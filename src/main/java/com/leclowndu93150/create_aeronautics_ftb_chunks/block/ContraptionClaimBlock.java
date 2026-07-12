package com.leclowndu93150.create_aeronautics_ftb_chunks.block;

import com.leclowndu93150.create_aeronautics_ftb_chunks.ContraptionForceLoadManager;
import com.leclowndu93150.create_aeronautics_ftb_chunks.ModConfig;
import com.leclowndu93150.create_aeronautics_ftb_chunks.network.OpenContraptionScreenPacket;
import com.leclowndu93150.create_aeronautics_ftb_chunks.network.ServerHandler;
import com.mojang.serialization.MapCodec;
import dev.ftb.mods.ftbchunks.api.ClaimedChunk;
import dev.ftb.mods.ftbchunks.api.ClaimedChunkManager;
import dev.ftb.mods.ftbchunks.api.ChunkTeamData;
import dev.ftb.mods.ftbchunks.api.FTBChunksAPI;
import dev.ftb.mods.ftbchunks.api.FTBChunksProperties;
import dev.ftb.mods.ftblibrary.math.ChunkDimPos;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.Team;
import dev.ftb.mods.ftbteams.api.TeamRank;
import dev.ftb.mods.ftbteams.api.property.PrivacyMode;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.plot.LevelPlot;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class ContraptionClaimBlock extends BaseEntityBlock {

    public static final MapCodec<ContraptionClaimBlock> CODEC = simpleCodec(ContraptionClaimBlock::new);

    public ContraptionClaimBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ContraptionClaimBlockEntity(pos, state);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        if (level.isClientSide) return;

        SubLevelContainer container = SubLevelContainer.getContainer((ServerLevel) level);
        if (container != null && container.inBounds(pos)) {
            var plot = container.getPlot(new ChunkPos(pos));
            if (plot != null) {
                for (var holder : plot.getLoadedChunks()) {
                    var chunk = level.getChunk(holder.getPos().x, holder.getPos().z);
                    for (var entry : chunk.getBlockEntities().entrySet()) {
                        if (!entry.getKey().equals(pos) && entry.getValue() instanceof ContraptionClaimBlockEntity) {
                            level.destroyBlock(pos, true);
                            if (placer instanceof ServerPlayer sp)
                                sp.sendSystemMessage(Component.translatable("create_aeronautics_ftb_chunks.duplicate_claim"));
                            return;
                        }
                    }
                }
            }
        }

        if (placer instanceof Player player) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof ContraptionClaimBlockEntity entity) {
                entity.setOwnerUUID(player.getUUID());
            }
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (level.isClientSide) return InteractionResult.SUCCESS;

        ServerPlayer serverPlayer = (ServerPlayer) player;

        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof ContraptionClaimBlockEntity entity)) return InteractionResult.PASS;

        if (!canInteract(serverPlayer, entity.getOwnerUUID())) {
            serverPlayer.sendSystemMessage(Component.translatable("create_aeronautics_ftb_chunks.not_owner"));
            return InteractionResult.FAIL;
        }

        SubLevelContainer container = SubLevelContainer.getContainer((ServerLevel) level);
        if (container == null || !container.inBounds(pos)) {
            serverPlayer.sendSystemMessage(Component.translatable("create_aeronautics_ftb_chunks.not_on_contraption"));
            return InteractionResult.FAIL;
        }

        LevelPlot plot = container.getPlot(new ChunkPos(pos));
        if (plot == null) {
            serverPlayer.sendSystemMessage(Component.translatable("create_aeronautics_ftb_chunks.not_on_contraption"));
            return InteractionResult.FAIL;
        }

        SubLevel subLevel = plot.getSubLevel();
        String shipName = subLevel.getName() != null ? subLevel.getName() : "";

        List<Long> chunkLongs = new ArrayList<>();
        ClaimedChunkManager manager = FTBChunksAPI.api().getManager();
        boolean claimed = false;
        boolean forceLoaded = false;

        for (var holder : plot.getLoadedChunks()) {
            ChunkPos chunkPos = holder.getPos();
            chunkLongs.add(chunkPos.toLong());
            ClaimedChunk existing = manager.getChunk(new ChunkDimPos(level.dimension(), chunkPos));
            if (existing != null) {
                claimed = true;
                if (existing.isForceLoaded()) forceLoaded = true;
            }
        }

        UUID ownerUUID = entity.getOwnerUUID() != null ? entity.getOwnerUUID() : serverPlayer.getUUID();
        String ownerName = level.getServer().getProfileCache()
                .get(ownerUUID).map(p -> p.getName()).orElse(ownerUUID.toString().substring(0, 8));

        Optional<Team> teamOpt = FTBTeamsAPI.api().getManager().getTeamForPlayerID(ownerUUID);
        List<String> memberNames = new ArrayList<>();
        List<String> allyNames = new ArrayList<>();
        String accessMode = "private";

        if (teamOpt.isPresent()) {
            Team team = teamOpt.get();
            accessMode = team.getProperty(FTBChunksProperties.BLOCK_INTERACT_MODE).name;
            for (var entry : team.getPlayersByRank(TeamRank.ALLY).entrySet()) {
                String name = level.getServer().getProfileCache().get(entry.getKey()).map(p -> p.getName()).orElse(null);
                if (name == null) continue;
                if (entry.getValue().isAtLeast(TeamRank.MEMBER)) memberNames.add(name);
                else if (entry.getValue() == TeamRank.ALLY) allyNames.add(name);
            }
        }

        UUID subLevelUUID = subLevel.getUniqueId();
        entity.setSubLevelUUID(subLevelUUID);

        int usedClaims = 0, maxClaims = 0, usedForceLoads = 0, maxForceLoads = 0;
        boolean canPlotForceLoad = false;

        if (teamOpt.isPresent()) {
            ChunkTeamData td = FTBChunksAPI.api().getManager().getOrCreateData(teamOpt.get());
            usedClaims = td.getClaimedChunks().size();
            maxClaims = td.getMaxClaimChunks();
            usedForceLoads = td.getForceLoadedChunks().size();
            maxForceLoads = td.getMaxForceLoadChunks();
            canPlotForceLoad = ModConfig.ALLOW_PLOT_CHUNK_FORCE_LOAD.get()
                    && claimed
                    && td.canDoOfflineForceLoading()
                    && (forceLoaded || usedForceLoads < maxForceLoads);
        }

        ServerHandler.setPendingChunks(serverPlayer.getUUID(), chunkLongs);
        ServerHandler.setPendingSubLevel(serverPlayer.getUUID(), subLevelUUID);
        ServerHandler.setPendingDimension(serverPlayer.getUUID(), level.dimension());

        boolean isPartyTeam = teamOpt.isPresent() && teamOpt.get().isPartyTeam();

        PacketDistributor.sendToPlayer(serverPlayer, new OpenContraptionScreenPacket(
                subLevelUUID, shipName, chunkLongs.size(), claimed, forceLoaded,
                ContraptionForceLoadManager.isPlotForceLoaded(subLevelUUID),
                ContraptionForceLoadManager.isPhysicsForceLoaded(subLevelUUID),
                ModConfig.ALLOW_PLOT_CHUNK_FORCE_LOAD.get(),
                canPlotForceLoad,
                false,
                usedClaims, maxClaims, usedForceLoads, maxForceLoads,
                isPartyTeam, accessMode, ownerUUID, ownerName, memberNames, allyNames, chunkLongs
        ));
        return InteractionResult.SUCCESS;
    }

    private boolean canInteract(ServerPlayer player, @Nullable UUID ownerUUID) {
        if (ownerUUID == null) return true;
        if (player.getUUID().equals(ownerUUID)) return true;

        Optional<Team> ownerTeam = FTBTeamsAPI.api().getManager().getTeamForPlayerID(ownerUUID);
        Optional<Team> playerTeam = FTBTeamsAPI.api().getManager().getTeamForPlayer(player);

        if (ownerTeam.isPresent() && playerTeam.isPresent()
                && ownerTeam.get().getId().equals(playerTeam.get().getId())) {
            return playerTeam.get().getRankForPlayer(player.getUUID()).isOfficerOrBetter();
        }
        return false;
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        UUID ownerUUID = null;
        UUID subLevelUUID = null;
        LevelPlot plot = null;

        if (!level.isClientSide && !state.is(newState.getBlock())) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof ContraptionClaimBlockEntity entity) {
                SubLevelContainer container = SubLevelContainer.getContainer((ServerLevel) level);
                if (container != null && container.inBounds(pos)) {
                    plot = container.getPlot(new ChunkPos(pos));
                    if (plot != null) {
                        ownerUUID = entity.getOwnerUUID();
                        subLevelUUID = entity.getSubLevelUUID();
                    }
                }
            }
        }

        super.onRemove(state, level, pos, newState, movedByPiston);

        if (!level.isClientSide && !state.is(newState.getBlock())) {
            ServerLevel serverLevel = (ServerLevel) level;
            cleanupClaims(serverLevel, plot, ownerUUID, subLevelUUID);
            syncRemovedBlock(serverLevel, pos, state, newState);
        }
    }

    private void cleanupClaims(ServerLevel level, @Nullable LevelPlot plot, @Nullable UUID ownerUUID, @Nullable UUID subLevelUUID) {
        if (plot != null && ownerUUID != null) {
            Optional<Team> teamOpt = FTBTeamsAPI.api().getManager().getTeamForPlayerID(ownerUUID);
            if (teamOpt.isPresent()) {
                ClaimedChunkManager manager = FTBChunksAPI.api().getManager();
                ChunkTeamData teamData = manager.getOrCreateData(teamOpt.get());
                var source = level.getServer().createCommandSourceStack().withSuppressedOutput();
                for (var holder : plot.getLoadedChunks()) {
                    ChunkDimPos dimPos = new ChunkDimPos(level.dimension(), holder.getPos());
                    ClaimedChunk existing = manager.getChunk(dimPos);
                    if (existing != null && existing.getTeamData().getTeam().getId().equals(teamOpt.get().getId())) {
                        teamData.unForceLoad(source, dimPos, false);
                        teamData.unclaim(source, dimPos, false);
                    }
                }
            }
        }

        if (subLevelUUID != null) {
            ContraptionForceLoadManager.disablePhysicsForceLoad(level.getServer(), subLevelUUID);
            ContraptionForceLoadManager.disablePlotForceLoad(level.getServer(), subLevelUUID);
        }
    }

    private void syncRemovedBlock(ServerLevel level, BlockPos pos, BlockState oldState, BlockState newState) {
        level.sendBlockUpdated(pos, oldState, newState, Block.UPDATE_ALL_IMMEDIATE);

        SubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null || !container.inBounds(pos)) return;

        ClientboundBlockUpdatePacket packet = new ClientboundBlockUpdatePacket(pos, newState);
        for (ServerPlayer player : container.getPlayersTracking(new ChunkPos(pos))) {
            player.connection.send(packet);
        }
    }
}
