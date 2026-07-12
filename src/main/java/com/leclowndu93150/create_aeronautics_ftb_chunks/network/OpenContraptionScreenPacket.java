package com.leclowndu93150.create_aeronautics_ftb_chunks.network;

import com.leclowndu93150.create_aeronautics_ftb_chunks.CreateAeronauticsFTBChunks;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.UUID;

public record OpenContraptionScreenPacket(
        UUID subLevelUUID,
        String shipName,
        int chunkCount,
        boolean claimed,
        boolean forceLoaded,
        boolean plotForceLoaded,
        boolean physicsForceLoaded,
        boolean canForceLoad,
        boolean canPlotForceLoad,
        boolean canPhysicsForceLoad,
        int usedClaims,
        int maxClaims,
        int usedForceLoads,
        int maxForceLoads,
        boolean isPartyTeam,
        String accessMode,
        UUID ownerUUID,
        String ownerName,
        List<String> memberNames,
        List<String> allyNames,
        List<Long> chunkLongs
) implements CustomPacketPayload {

    public static final Type<OpenContraptionScreenPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateAeronauticsFTBChunks.MODID, "open_contraption_screen")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenContraptionScreenPacket> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeUUID(p.subLevelUUID());
                buf.writeUtf(p.shipName());
                buf.writeInt(p.chunkCount());
                buf.writeBoolean(p.claimed());
                buf.writeBoolean(p.forceLoaded());
                buf.writeBoolean(p.plotForceLoaded());
                buf.writeBoolean(p.physicsForceLoaded());
                buf.writeBoolean(p.canForceLoad());
                buf.writeBoolean(p.canPlotForceLoad());
                buf.writeBoolean(p.canPhysicsForceLoad());
                buf.writeInt(p.usedClaims());
                buf.writeInt(p.maxClaims());
                buf.writeInt(p.usedForceLoads());
                buf.writeInt(p.maxForceLoads());
                buf.writeBoolean(p.isPartyTeam());
                buf.writeUtf(p.accessMode());
                buf.writeUUID(p.ownerUUID());
                buf.writeUtf(p.ownerName());
                buf.writeCollection(p.memberNames(), (b, s) -> b.writeUtf(s));
                buf.writeCollection(p.allyNames(), (b, s) -> b.writeUtf(s));
                buf.writeCollection(p.chunkLongs(), (b, l) -> b.writeLong(l));
            },
            buf -> new OpenContraptionScreenPacket(
                    buf.readUUID(),
                    buf.readUtf(),
                    buf.readInt(),
                    buf.readBoolean(),
                    buf.readBoolean(),
                    buf.readBoolean(),
                    buf.readBoolean(),
                    buf.readBoolean(),
                    buf.readBoolean(),
                    buf.readBoolean(),
                    buf.readInt(),
                    buf.readInt(),
                    buf.readInt(),
                    buf.readInt(),
                    buf.readBoolean(),
                    buf.readUtf(),
                    buf.readUUID(),
                    buf.readUtf(),
                    buf.readList(b -> b.readUtf()),
                    buf.readList(b -> b.readUtf()),
                    buf.readList(b -> b.readLong())
            )
    );

    public OpenContraptionScreenPacket withClaimed(boolean claimed) {
        int claimsDelta = claimed == this.claimed ? 0 : claimed ? chunkCount : -chunkCount;
        int updatedClaims = Math.clamp(usedClaims + claimsDelta, 0, maxClaims);
        boolean updatedForceLoaded = claimed && forceLoaded;
        int forceLoadsDelta = !claimed && forceLoaded ? -chunkCount : 0;
        int updatedForceLoads = Math.clamp(usedForceLoads + forceLoadsDelta, 0, maxForceLoads);
        return new OpenContraptionScreenPacket(subLevelUUID, shipName, chunkCount, claimed, updatedForceLoaded, claimed && plotForceLoaded, claimed && physicsForceLoaded, canForceLoad, canPlotForceLoad, canPhysicsForceLoad, updatedClaims, maxClaims, updatedForceLoads, maxForceLoads, isPartyTeam, accessMode, ownerUUID, ownerName, memberNames, allyNames, chunkLongs);
    }

    public OpenContraptionScreenPacket withForceLoaded(boolean forceLoaded, int usedForceLoadsDelta) {
        int transitionDelta = forceLoaded == this.forceLoaded ? 0 : forceLoaded ? chunkCount : -chunkCount;
        int updatedForceLoads = Math.clamp(usedForceLoads + transitionDelta, 0, maxForceLoads);
        return new OpenContraptionScreenPacket(subLevelUUID, shipName, chunkCount, claimed, forceLoaded, forceLoaded && plotForceLoaded, forceLoaded && physicsForceLoaded, canForceLoad, canPlotForceLoad, canPhysicsForceLoad, usedClaims, maxClaims, updatedForceLoads, maxForceLoads, isPartyTeam, accessMode, ownerUUID, ownerName, memberNames, allyNames, chunkLongs);
    }

    public OpenContraptionScreenPacket withPlotForceLoaded(boolean plotForceLoaded) {
        return new OpenContraptionScreenPacket(subLevelUUID, shipName, chunkCount, claimed, forceLoaded, plotForceLoaded, physicsForceLoaded, canForceLoad, canPlotForceLoad, canPhysicsForceLoad, usedClaims, maxClaims, usedForceLoads, maxForceLoads, isPartyTeam, accessMode, ownerUUID, ownerName, memberNames, allyNames, chunkLongs);
    }

    public OpenContraptionScreenPacket withPhysicsForceLoaded(boolean physicsForceLoaded) {
        return new OpenContraptionScreenPacket(subLevelUUID, shipName, chunkCount, claimed, forceLoaded, plotForceLoaded, physicsForceLoaded, canForceLoad, canPlotForceLoad, canPhysicsForceLoad, usedClaims, maxClaims, usedForceLoads, maxForceLoads, isPartyTeam, accessMode, ownerUUID, ownerName, memberNames, allyNames, chunkLongs);
    }

    public OpenContraptionScreenPacket withAccessMode(String accessMode) {
        return new OpenContraptionScreenPacket(subLevelUUID, shipName, chunkCount, claimed, forceLoaded, plotForceLoaded, physicsForceLoaded, canForceLoad, canPlotForceLoad, canPhysicsForceLoad, usedClaims, maxClaims, usedForceLoads, maxForceLoads, isPartyTeam, accessMode, ownerUUID, ownerName, memberNames, allyNames, chunkLongs);
    }

    public OpenContraptionScreenPacket withAllyNames(List<String> allyNames) {
        return new OpenContraptionScreenPacket(subLevelUUID, shipName, chunkCount, claimed, forceLoaded, plotForceLoaded, physicsForceLoaded, canForceLoad, canPlotForceLoad, canPhysicsForceLoad, usedClaims, maxClaims, usedForceLoads, maxForceLoads, isPartyTeam, accessMode, ownerUUID, ownerName, memberNames, allyNames, chunkLongs);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
