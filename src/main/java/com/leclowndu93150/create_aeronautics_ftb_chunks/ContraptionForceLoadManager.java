package com.leclowndu93150.create_aeronautics_ftb_chunks;

import com.leclowndu93150.create_aeronautics_ftb_chunks.block.ContraptionClaimBlockEntity;
import dev.ftb.mods.ftbchunks.api.ClaimResult;
import dev.ftb.mods.ftbchunks.api.ClaimedChunk;
import dev.ftb.mods.ftbchunks.api.ClaimedChunkManager;
import dev.ftb.mods.ftbchunks.api.ChunkTeamData;
import dev.ftb.mods.ftbchunks.api.FTBChunksAPI;
import dev.ftb.mods.ftblibrary.math.ChunkDimPos;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.Team;
import dev.ryanhcode.sable.api.SubLevelHelper;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.ticket.SubLevelLoadingTicketType;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.plot.LevelPlot;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.Unit;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ContraptionForceLoadManager {

    private static final long CLAIM_BLOCK_REFRESH_INTERVAL = 100L;
    private static final int PHYSICAL_TICKET_RADIUS = 3;

    /*
     * Keep the original ID used by the development build so existing test worlds retain their tickets.
     * Sable serializes this type ID and uses it to restore the sub-level before players join.
     */
    private static final SubLevelLoadingTicketType<Unit> SABLE_FORCE_LOAD_TICKET = SubLevelLoadingTicketType.create(
            ResourceLocation.fromNamespaceAndPath(CreateAeronauticsFTBChunks.MODID, "plot_force_loaded"),
            Unit.CODEC
    );

    private static final Map<UUID, UUID> physicsForceLoaded = new ConcurrentHashMap<>();
    private static final Map<UUID, UUID> plotForceLoaded = new ConcurrentHashMap<>();
    private static final Map<UUID, PhysicalTicket> physicalTickets = new ConcurrentHashMap<>();
    private static long nextClaimBlockRefreshTick;

    private ContraptionForceLoadManager() {
    }

    /**
     * Forces class initialization during mod construction. Ticket types must be registered before Sable reads saved data.
     */
    public static void initialize() {
    }

    public static boolean enablePlotForceLoad(MinecraftServer server, UUID subLevelUUID, UUID ownerUUID) {
        if (!ModConfig.ALLOW_PLOT_CHUNK_FORCE_LOAD.get()) {
            return false;
        }

        Optional<Team> teamOpt = FTBTeamsAPI.api().getManager().getTeamForPlayerID(ownerUUID);
        if (teamOpt.isEmpty()) {
            return false;
        }

        Team team = teamOpt.get();
        ClaimedChunkManager manager = FTBChunksAPI.api().getManager();
        ChunkTeamData teamData = manager.getOrCreateData(team);
        if (!teamData.canDoOfflineForceLoading()) {
            return false;
        }

        ServerLevel level = server.overworld();
        ServerSubLevel subLevel = getSubLevel(level, subLevelUUID);
        if (subLevel == null) {
            return false;
        }

        LevelPlot plot = subLevel.getPlot();
        var source = server.createCommandSourceStack().withSuppressedOutput();
        int loaded = 0;
        int notOwned = 0;
        int quotaFull = 0;
        Map<String, Integer> failures = new HashMap<>();

        for (var holder : plot.getLoadedChunks()) {
            ChunkPos chunkPos = holder.getPos();
            ChunkDimPos dimPos = new ChunkDimPos(level.dimension(), chunkPos);
            ClaimedChunk existing = manager.getChunk(dimPos);

            if (existing == null || !existing.getTeamData().getTeam().getId().equals(team.getId())) {
                notOwned++;
                continue;
            }
            if (existing.isForceLoaded()) {
                loaded++;
                continue;
            }
            if (teamData.getForceLoadedChunks().size() >= teamData.getMaxForceLoadChunks()) {
                quotaFull++;
                continue;
            }

            ClaimResult result = teamData.forceLoad(source, dimPos, false);
            if (result.isSuccess()) {
                loaded++;
            } else {
                failures.merge(result.getResultId(), 1, Integer::sum);
            }
        }

        int total = plot.getLoadedChunks().size();
        boolean fullyLoaded = total > 0 && loaded == total;
        CreateAeronauticsFTBChunks.LOGGER.info(
                "[plot-forceload] sub={} loaded={}/{} not-owned={} quota-full={} failures={}",
                subLevelUUID, loaded, total, notOwned, quotaFull, failures
        );

        if (fullyLoaded) {
            plotForceLoaded.put(subLevelUUID, ownerUUID);
            enablePhysicsForceLoad(server, subLevelUUID, ownerUUID);
        }
        return fullyLoaded;
    }

    public static void disablePlotForceLoad(MinecraftServer server, UUID subLevelUUID) {
        disablePhysicsForceLoad(server, subLevelUUID);

        UUID ownerUUID = plotForceLoaded.remove(subLevelUUID);
        if (ownerUUID == null) {
            return;
        }

        Optional<Team> teamOpt = FTBTeamsAPI.api().getManager().getTeamForPlayerID(ownerUUID);
        ServerSubLevel subLevel = getSubLevel(server.overworld(), subLevelUUID);
        if (teamOpt.isEmpty() || subLevel == null) {
            return;
        }

        ChunkTeamData teamData = FTBChunksAPI.api().getManager().getOrCreateData(teamOpt.get());
        var source = server.createCommandSourceStack().withSuppressedOutput();
        for (var holder : subLevel.getPlot().getLoadedChunks()) {
            teamData.unForceLoad(source, new ChunkDimPos(server.overworld().dimension(), holder.getPos()), false);
        }
    }

    public static boolean enablePhysicsForceLoad(MinecraftServer server, UUID subLevelUUID, UUID ownerUUID) {
        if (!ModConfig.ALLOW_PLOT_CHUNK_FORCE_LOAD.get()) {
            return false;
        }

        ServerLevel level = server.overworld();
        ServerSubLevel subLevel = getSubLevel(level, subLevelUUID);
        if (subLevel == null) {
            return false;
        }

        Optional<Team> teamOpt = FTBTeamsAPI.api().getManager().getTeamForPlayerID(ownerUUID);
        if (teamOpt.isEmpty() || !isEntirePlotForceLoaded(level, subLevel.getPlot(), teamOpt.get().getId())) {
            return false;
        }
        plotForceLoaded.put(subLevelUUID, ownerUUID);

        SubLevelContainer container = SubLevelContainer.getContainer(level);
        if (!(container instanceof ServerSubLevelContainer serverContainer)) {
            return false;
        }

        addChainTickets(level, serverContainer, subLevel);
        if (!hasSableTicket(serverContainer, subLevelUUID)) {
            return false;
        }

        physicsForceLoaded.put(subLevelUUID, ownerUUID);
        updatePhysicalTickets(level, true);
        CreateAeronauticsFTBChunks.LOGGER.info("[physics-forceload] enabled Sable and physical tickets for sub={}", subLevelUUID);
        return true;
    }

    public static void disablePhysicsForceLoad(MinecraftServer server, UUID subLevelUUID) {
        physicsForceLoaded.remove(subLevelUUID);

        ServerLevel level = server.overworld();
        SubLevelContainer container = SubLevelContainer.getContainer(level);
        ServerSubLevel subLevel = getSubLevel(level, subLevelUUID);
        if (container instanceof ServerSubLevelContainer serverContainer && subLevel != null) {
            removeChainTickets(level, serverContainer, subLevel);
        }
        removePhysicalTicket(level, subLevelUUID);
        CreateAeronauticsFTBChunks.LOGGER.info("[physics-forceload] removed Sable and physical tickets for sub={}", subLevelUUID);
    }

    /**
     * Rebuilds the in-memory UI state after a server restart. Sable owns persistence and loading of physics tickets.
     */
    public static void refreshExistingClaimBlocks(MinecraftServer server) {
        ServerLevel level = server.overworld();
        long gameTime = level.getGameTime();
        if (gameTime < nextClaimBlockRefreshTick) {
            return;
        }
        nextClaimBlockRefreshTick = gameTime + CLAIM_BLOCK_REFRESH_INTERVAL;

        SubLevelContainer container = SubLevelContainer.getContainer(level);
        if (!(container instanceof ServerSubLevelContainer serverContainer)) {
            return;
        }

        for (ServerSubLevel subLevel : serverContainer.getAllSubLevels()) {
            if (subLevel.isRemoved()) {
                continue;
            }

            UUID subLevelUUID = subLevel.getUniqueId();
            UUID ownerUUID = findClaimBlockOwner(subLevel);
            boolean hasTicket = hasSableTicket(serverContainer, subLevelUUID);

            if (ownerUUID == null) {
                plotForceLoaded.remove(subLevelUUID);
                physicsForceLoaded.remove(subLevelUUID);
                if (hasTicket) {
                    serverContainer.removeForceLoadTicket(subLevel, SABLE_FORCE_LOAD_TICKET, Unit.INSTANCE);
                }
                removePhysicalTicket(level, subLevelUUID);
                continue;
            }

            Optional<Team> team = FTBTeamsAPI.api().getManager().getTeamForPlayerID(ownerUUID);
            boolean plotLoaded = team.isPresent()
                    && isEntirePlotForceLoaded(level, subLevel.getPlot(), team.get().getId());

            if (plotLoaded) {
                plotForceLoaded.put(subLevelUUID, ownerUUID);
                if (!hasTicket) {
                    addChainTickets(level, serverContainer, subLevel);
                    hasTicket = hasSableTicket(serverContainer, subLevelUUID);
                    if (hasTicket) {
                        CreateAeronauticsFTBChunks.LOGGER.info(
                                "[physics-forceload] restored Sable ticket for force-loaded sub={}",
                                subLevelUUID
                        );
                    }
                }

                if (hasTicket) {
                    physicsForceLoaded.put(subLevelUUID, ownerUUID);
                } else {
                    physicsForceLoaded.remove(subLevelUUID);
                }
            } else {
                plotForceLoaded.remove(subLevelUUID);
                physicsForceLoaded.remove(subLevelUUID);
                if (hasTicket) {
                    removeChainTickets(level, serverContainer, subLevel);
                }
                removePhysicalTicket(level, subLevelUUID);
            }
        }

        updatePhysicalTickets(level, gameTime % CLAIM_BLOCK_REFRESH_INTERVAL == 0L);
    }

    /**
     * Aero Reformation's physics anchor keeps a vanilla region ticket under the moving ship in addition
     * to Sable's sub-level ticket. This ensures the physical world remains entity-ticking even if Sable's
     * own inhabited-chunk ticket has not been established yet (notably directly after a join/load).
     */
    private static void updatePhysicalTickets(ServerLevel level, boolean renew) {
        SubLevelContainer container = SubLevelContainer.getContainer(level);
        if (!(container instanceof ServerSubLevelContainer serverContainer)) {
            return;
        }

        Set<UUID> wanted = ConcurrentHashMap.newKeySet();
        for (UUID rootId : physicsForceLoaded.keySet()) {
            ServerSubLevel root = getSubLevel(level, rootId);
            if (root == null || root.isRemoved()) {
                continue;
            }

            for (var connected : SubLevelHelper.getConnectedChain(root)) {
                if (!(connected instanceof ServerSubLevel subLevel) || subLevel.isRemoved()) {
                    continue;
                }
                UUID id = subLevel.getUniqueId();
                wanted.add(id);
                serverContainer.addForceLoadTicket(subLevel, SABLE_FORCE_LOAD_TICKET, Unit.INSTANCE);

                var position = subLevel.logicalPose().position();
                ChunkPos currentChunk = new ChunkPos(
                        (int) Math.floor(position.x() / 16.0),
                        (int) Math.floor(position.z() / 16.0)
                );
                PhysicalTicket previous = physicalTickets.get(id);
                if (previous == null || !previous.chunk().equals(currentChunk) || renew) {
                    if (previous != null) {
                        level.getChunkSource().removeRegionTicket(
                                TicketType.PORTAL, previous.chunk(), PHYSICAL_TICKET_RADIUS, previous.key()
                        );
                    }
                    BlockPos key = previous == null ? ticketKey(id) : previous.key();
                    level.getChunkSource().addRegionTicket(
                            TicketType.PORTAL, currentChunk, PHYSICAL_TICKET_RADIUS, key
                    );
                    physicalTickets.put(id, new PhysicalTicket(currentChunk, key));
                }
            }
        }

        for (UUID id : Set.copyOf(physicalTickets.keySet())) {
            if (!wanted.contains(id)) {
                removePhysicalTicket(level, id);
            }
        }
    }

    private static void addChainTickets(ServerLevel level, ServerSubLevelContainer container, ServerSubLevel root) {
        for (var connected : SubLevelHelper.getConnectedChain(root)) {
            if (connected instanceof ServerSubLevel subLevel && !subLevel.isRemoved()) {
                container.addForceLoadTicket(subLevel, SABLE_FORCE_LOAD_TICKET, Unit.INSTANCE);
            }
        }
    }

    private static void removeChainTickets(ServerLevel level, ServerSubLevelContainer container, ServerSubLevel root) {
        for (var connected : SubLevelHelper.getConnectedChain(root)) {
            if (connected instanceof ServerSubLevel subLevel) {
                UUID id = subLevel.getUniqueId();
                container.removeForceLoadTicket(subLevel, SABLE_FORCE_LOAD_TICKET, Unit.INSTANCE);
                removePhysicalTicket(level, id);
            }
        }
    }

    private static void removePhysicalTicket(ServerLevel level, UUID id) {
        PhysicalTicket ticket = physicalTickets.remove(id);
        if (ticket != null) {
            level.getChunkSource().removeRegionTicket(
                    TicketType.PORTAL, ticket.chunk(), PHYSICAL_TICKET_RADIUS, ticket.key()
            );
        }
    }

    private static BlockPos ticketKey(UUID id) {
        long mixed = id.getMostSignificantBits() ^ id.getLeastSignificantBits();
        int x = (int) Math.floorMod(mixed, 20_000_000L) - 10_000_000;
        int z = (int) Math.floorMod(Long.rotateLeft(mixed, 29), 20_000_000L) - 10_000_000;
        return new BlockPos(x, 0, z);
    }

    private static UUID findClaimBlockOwner(ServerSubLevel subLevel) {
        UUID subLevelUUID = subLevel.getUniqueId();
        for (var holder : subLevel.getPlot().getLoadedChunks()) {
            for (var blockEntity : holder.getChunk().getBlockEntities().values()) {
                if (blockEntity instanceof ContraptionClaimBlockEntity claimBlock) {
                    if (!subLevelUUID.equals(claimBlock.getSubLevelUUID())) {
                        claimBlock.setSubLevelUUID(subLevelUUID);
                    }
                    return claimBlock.getOwnerUUID();
                }
            }
        }
        return null;
    }

    private static boolean isEntirePlotForceLoaded(ServerLevel level, LevelPlot plot, UUID teamId) {
        ClaimedChunkManager manager = FTBChunksAPI.api().getManager();
        if (plot.getLoadedChunks().isEmpty()) {
            return false;
        }

        for (var holder : plot.getLoadedChunks()) {
            ClaimedChunk chunk = manager.getChunk(new ChunkDimPos(level.dimension(), holder.getPos()));
            if (chunk == null || !chunk.isForceLoaded()
                    || !chunk.getTeamData().getTeam().getId().equals(teamId)) {
                return false;
            }
        }
        return true;
    }

    private static boolean hasSableTicket(ServerSubLevelContainer container, UUID subLevelUUID) {
        var info = container.getAllTickets().get(subLevelUUID);
        return info != null && info.tickets().stream().anyMatch(ticket ->
                ticket.getType().equals(SABLE_FORCE_LOAD_TICKET) && Unit.INSTANCE.equals(ticket.getKey()));
    }

    private static ServerSubLevel getSubLevel(ServerLevel level, UUID subLevelUUID) {
        SubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            return null;
        }
        return container.getSubLevel(subLevelUUID) instanceof ServerSubLevel subLevel ? subLevel : null;
    }

    public static boolean isPlotForceLoaded(UUID subLevelUUID) {
        return plotForceLoaded.containsKey(subLevelUUID);
    }

    public static boolean isPhysicsForceLoaded(UUID subLevelUUID) {
        return physicsForceLoaded.containsKey(subLevelUUID);
    }

    public static Set<UUID> getPhysicsForceLoadedSubLevels() {
        return Collections.unmodifiableSet(physicsForceLoaded.keySet());
    }

    public static void cleanup(UUID subLevelUUID) {
        physicsForceLoaded.remove(subLevelUUID);
        plotForceLoaded.remove(subLevelUUID);
    }

    public static void cleanupAll() {
        physicalTickets.clear();
        physicsForceLoaded.clear();
        plotForceLoaded.clear();
        nextClaimBlockRefreshTick = 0L;
    }

    private record PhysicalTicket(ChunkPos chunk, BlockPos key) {
    }
}
