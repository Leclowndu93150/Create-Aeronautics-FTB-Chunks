package com.leclowndu93150.create_aeronautics_ftb_chunks.network;

import com.leclowndu93150.create_aeronautics_ftb_chunks.ContraptionForceLoadManager;
import com.leclowndu93150.create_aeronautics_ftb_chunks.mixin.PartyTeamAccessor;
import com.mojang.authlib.GameProfile;
import dev.ftb.mods.ftbchunks.api.ClaimedChunk;
import dev.ftb.mods.ftbchunks.api.ClaimedChunkManager;
import dev.ftb.mods.ftbchunks.api.ChunkTeamData;
import dev.ftb.mods.ftbchunks.api.FTBChunksAPI;
import dev.ftb.mods.ftbchunks.api.FTBChunksProperties;
import dev.ftb.mods.ftblibrary.math.ChunkDimPos;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.Team;
import dev.ftb.mods.ftbteams.api.property.PrivacyMode;
import dev.ftb.mods.ftbteams.data.PartyTeam;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ServerHandler {

    private static final Map<UUID, List<Long>> pendingChunks = new ConcurrentHashMap<>();
    private static final Map<UUID, UUID> pendingSubLevels = new ConcurrentHashMap<>();
    private static final Map<UUID, ResourceKey<Level>> pendingDimensions = new ConcurrentHashMap<>();

    public static void setPendingChunks(UUID playerUUID, List<Long> chunks) {
        pendingChunks.put(playerUUID, chunks);
    }

    public static void setPendingSubLevel(UUID playerUUID, UUID subLevelUUID) {
        pendingSubLevels.put(playerUUID, subLevelUUID);
    }

    public static void setPendingDimension(UUID playerUUID, ResourceKey<Level> dimension) {
        pendingDimensions.put(playerUUID, dimension);
    }

    public static void handleContraptionClaimAction(ContraptionClaimActionPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) context.player();
            List<Long> chunks = pendingChunks.get(player.getUUID());
            if (chunks == null || chunks.isEmpty()) return;

            Optional<Team> teamOpt = FTBTeamsAPI.api().getManager().getTeamForPlayer(player);
            if (teamOpt.isEmpty()) return;

            Team team = teamOpt.get();
            UUID subLevelUUID = pendingSubLevels.get(player.getUUID());

            switch (packet.action()) {
                case PHYSICS_FORCE_LOAD -> {
                    if (subLevelUUID != null)
                        ContraptionForceLoadManager.enablePhysicsForceLoad(player.server, subLevelUUID, player.getUUID());
                    return;
                }
                case PHYSICS_UNFORCE_LOAD -> {
                    if (subLevelUUID != null)
                        ContraptionForceLoadManager.disablePhysicsForceLoad(player.server, subLevelUUID);
                    return;
                }
                case ACCESS_PRIVATE, ACCESS_ALLIES, ACCESS_PUBLIC -> {
                    PrivacyMode mode = switch (packet.action()) {
                        case ACCESS_PRIVATE -> PrivacyMode.PRIVATE;
                        case ACCESS_ALLIES  -> PrivacyMode.ALLIES;
                        default             -> PrivacyMode.PUBLIC;
                    };
                    team.setProperty(FTBChunksProperties.BLOCK_INTERACT_MODE, mode);
                    team.setProperty(FTBChunksProperties.BLOCK_EDIT_MODE, mode);
                    team.syncOnePropertyToAll(player.server, FTBChunksProperties.BLOCK_INTERACT_MODE, mode);
                    team.syncOnePropertyToAll(player.server, FTBChunksProperties.BLOCK_EDIT_MODE, mode);
                    return;
                }
                default -> {}
            }

            ClaimedChunkManager manager = FTBChunksAPI.api().getManager();
            ChunkTeamData teamData = manager.getOrCreateData(team);
            var source = player.createCommandSourceStack().withSuppressedOutput();
            ResourceKey<Level> dimension = pendingDimensions.getOrDefault(player.getUUID(), player.server.overworld().dimension());

            for (long chunkLong : chunks) {
                ChunkPos chunkPos = new ChunkPos(chunkLong);
                ChunkDimPos dimPos = new ChunkDimPos(dimension, chunkPos);
                ClaimedChunk existing = manager.getChunk(dimPos);

                switch (packet.action()) {
                    case CLAIM -> teamData.claim(source, dimPos, false);
                    case UNCLAIM -> {
                        if (existing != null && existing.getTeamData().getTeam().getId().equals(team.getId())) {
                            teamData.unForceLoad(source, dimPos, false);
                            teamData.unclaim(source, dimPos, false);
                        }
                    }
                    case FORCE_LOAD -> teamData.forceLoad(source, dimPos, false);
                    case UNFORCE_LOAD -> teamData.unForceLoad(source, dimPos, false);
                    default -> {}
                }
            }

            if (packet.action() == ContraptionClaimActionPacket.Action.FORCE_LOAD && subLevelUUID != null)
                ContraptionForceLoadManager.enablePlotForceLoad(player.server, subLevelUUID, player.getUUID());
            if (packet.action() == ContraptionClaimActionPacket.Action.UNFORCE_LOAD && subLevelUUID != null) {
                ContraptionForceLoadManager.disablePhysicsForceLoad(player.server, subLevelUUID);
                ContraptionForceLoadManager.disablePlotForceLoad(player.server, subLevelUUID);
            }
            if (packet.action() == ContraptionClaimActionPacket.Action.UNCLAIM && subLevelUUID != null) {
                ContraptionForceLoadManager.disablePhysicsForceLoad(player.server, subLevelUUID);
                ContraptionForceLoadManager.disablePlotForceLoad(player.server, subLevelUUID);
            }
        });
    }

    public static void handleContraptionAlly(ContraptionAllyPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) context.player();
            Optional<Team> teamOpt = FTBTeamsAPI.api().getManager().getTeamForPlayer(player);
            if (teamOpt.isEmpty()) return;
            if (!(teamOpt.get() instanceof PartyTeam partyTeam)) {
                player.sendSystemMessage(Component.literal("You must be in a party team to manage allies."));
                return;
            }

            var profileOpt = player.server.getProfileCache().get(packet.playerName());
            if (profileOpt.isEmpty()) {
                player.sendSystemMessage(Component.literal("Player '" + packet.playerName() + "' not found."));
                return;
            }

            var gameProfile = new GameProfile(profileOpt.get().getId(), profileOpt.get().getName());
            var source = player.createCommandSourceStack();

            try {
                if (packet.remove()) {
                    ((PartyTeamAccessor) partyTeam).invokeRemoveAlly(source, List.of(gameProfile));
                } else {
                    ((PartyTeamAccessor) partyTeam).invokeAddAlly(source, List.of(gameProfile));
                }
            } catch (Exception ignored) {}
        });
    }
}
