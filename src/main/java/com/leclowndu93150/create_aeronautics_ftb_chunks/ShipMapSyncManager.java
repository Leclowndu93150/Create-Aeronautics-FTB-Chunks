package com.leclowndu93150.create_aeronautics_ftb_chunks;

import com.leclowndu93150.create_aeronautics_ftb_chunks.block.ContraptionClaimBlockEntity;
import com.leclowndu93150.create_aeronautics_ftb_chunks.network.ShipMapDataPacket;
import dev.ftb.mods.ftbchunks.api.ClaimedChunk;
import dev.ftb.mods.ftbchunks.api.ClaimedChunkManager;
import dev.ftb.mods.ftbchunks.api.FTBChunksAPI;
import dev.ftb.mods.ftblibrary.math.ChunkDimPos;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.Team;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class ShipMapSyncManager {

    private static final int SYNC_INTERVAL_TICKS = 5;
    private static final double SECONDS_PER_SYNC = SYNC_INTERVAL_TICKS / 20.0;
    private static final Map<UUID, Vector3d> previousPositions = new HashMap<>();

    private ShipMapSyncManager() {
    }

    public static void tick(MinecraftServer server) {
        if (server.overworld().getGameTime() % SYNC_INTERVAL_TICKS != 0L) {
            return;
        }

        List<VisibleShip> ships = collectShips(server);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Optional<Team> playerTeam = FTBTeamsAPI.api().getManager().getTeamForPlayer(player);
            List<ShipMapDataPacket.ShipData> visible = ships.stream()
                    .filter(ship -> ship.ownerId().equals(player.getUUID())
                            || playerTeam.isPresent() && playerTeam.get().getId().equals(ship.teamId()))
                    .map(VisibleShip::data)
                    .toList();
            PacketDistributor.sendToPlayer(player, new ShipMapDataPacket(visible));
        }
    }

    public static void reset() {
        previousPositions.clear();
    }

    private static List<VisibleShip> collectShips(MinecraftServer server) {
        List<VisibleShip> result = new ArrayList<>();
        Set<UUID> seen = new HashSet<>();

        for (ServerLevel level : server.getAllLevels()) {
            SubLevelContainer container = SubLevelContainer.getContainer(level);
            if (container == null) {
                continue;
            }

            for (var rawSubLevel : container.getAllSubLevels()) {
                if (!(rawSubLevel instanceof ServerSubLevel subLevel) || subLevel.isRemoved()) {
                    continue;
                }

                Owner owner = findOwner(server, subLevel);
                if (owner == null) {
                    continue;
                }

                Optional<Team> team = FTBTeamsAPI.api().getManager().getTeamForPlayerID(owner.id());
                if (team.isEmpty()) {
                    continue;
                }

                var position = subLevel.logicalPose().position();
                UUID id = subLevel.getUniqueId();
                seen.add(id);
                Vector3d current = new Vector3d(position);
                Vector3d previous = previousPositions.put(id, current);
                double speed = previous == null ? 0.0 : previous.distance(current) / SECONDS_PER_SYNC;

                Vector3d forward = subLevel.logicalPose().orientation().transform(new Vector3d(0.0, 0.0, -1.0));
                float heading = (float) Math.toDegrees(Math.atan2(forward.x, -forward.z));
                int totalChunks = subLevel.getPlot().getLoadedChunks().size();
                int forceLoadedChunks = countForceLoadedChunks(level, subLevel);
                ChunkPos physicalChunk = new ChunkPos((int) Math.floor(position.x()) >> 4, (int) Math.floor(position.z()) >> 4);

                String name = subLevel.getName();
                if (name == null || name.isBlank()) {
                    name = "Contraption";
                }

                ShipMapDataPacket.ShipData data = new ShipMapDataPacket.ShipData(
                        id,
                        level.dimension().location(),
                        name,
                        owner.name(),
                        position.x(),
                        position.y(),
                        position.z(),
                        heading,
                        speed,
                        totalChunks,
                        forceLoadedChunks,
                        ContraptionForceLoadManager.isPhysicsForceLoaded(id),
                        level.hasChunk(physicalChunk.x, physicalChunk.z)
                );
                result.add(new VisibleShip(owner.id(), team.get().getId(), data));
            }
        }

        previousPositions.keySet().removeIf(id -> !seen.contains(id));
        return result;
    }

    private static Owner findOwner(MinecraftServer server, ServerSubLevel subLevel) {
        for (var holder : subLevel.getPlot().getLoadedChunks()) {
            for (var blockEntity : holder.getChunk().getBlockEntities().values()) {
                if (blockEntity instanceof ContraptionClaimBlockEntity claimBlock && claimBlock.getOwnerUUID() != null) {
                    UUID ownerId = claimBlock.getOwnerUUID();
                    String ownerName = server.getProfileCache().get(ownerId)
                            .map(profile -> profile.getName())
                            .orElse(ownerId.toString().substring(0, 8));
                    return new Owner(ownerId, ownerName);
                }
            }
        }
        return null;
    }

    private static int countForceLoadedChunks(ServerLevel level, ServerSubLevel subLevel) {
        ClaimedChunkManager manager = FTBChunksAPI.api().getManager();
        int count = 0;
        for (var holder : subLevel.getPlot().getLoadedChunks()) {
            ClaimedChunk chunk = manager.getChunk(new ChunkDimPos(level.dimension(), holder.getPos()));
            if (chunk != null && chunk.isForceLoaded()) {
                count++;
            }
        }
        return count;
    }

    private record Owner(UUID id, String name) {
    }

    private record VisibleShip(UUID ownerId, UUID teamId, ShipMapDataPacket.ShipData data) {
    }
}
