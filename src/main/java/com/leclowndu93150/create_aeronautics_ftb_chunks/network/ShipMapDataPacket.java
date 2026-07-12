package com.leclowndu93150.create_aeronautics_ftb_chunks.network;

import com.leclowndu93150.create_aeronautics_ftb_chunks.CreateAeronauticsFTBChunks;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.UUID;

public record ShipMapDataPacket(List<ShipData> ships) implements CustomPacketPayload {

    public static final Type<ShipMapDataPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateAeronauticsFTBChunks.MODID, "ship_map_data")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, ShipMapDataPacket> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> buf.writeCollection(packet.ships(), ShipData::write),
            buf -> new ShipMapDataPacket(buf.readList(ShipData::read))
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public record ShipData(
            UUID subLevelId,
            ResourceLocation dimension,
            String name,
            String ownerName,
            double x,
            double y,
            double z,
            float heading,
            double speed,
            int plotChunks,
            int forceLoadedChunks,
            boolean physicsForceLoaded,
            boolean physicalChunkLoaded
    ) {
        private static void write(FriendlyByteBuf buf, ShipData data) {
            buf.writeUUID(data.subLevelId());
            buf.writeResourceLocation(data.dimension());
            buf.writeUtf(data.name(), 128);
            buf.writeUtf(data.ownerName(), 64);
            buf.writeDouble(data.x());
            buf.writeDouble(data.y());
            buf.writeDouble(data.z());
            buf.writeFloat(data.heading());
            buf.writeDouble(data.speed());
            buf.writeVarInt(data.plotChunks());
            buf.writeVarInt(data.forceLoadedChunks());
            buf.writeBoolean(data.physicsForceLoaded());
            buf.writeBoolean(data.physicalChunkLoaded());
        }

        private static ShipData read(FriendlyByteBuf buf) {
            return new ShipData(
                    buf.readUUID(),
                    buf.readResourceLocation(),
                    buf.readUtf(128),
                    buf.readUtf(64),
                    buf.readDouble(),
                    buf.readDouble(),
                    buf.readDouble(),
                    buf.readFloat(),
                    buf.readDouble(),
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readBoolean(),
                    buf.readBoolean()
            );
        }
    }
}
