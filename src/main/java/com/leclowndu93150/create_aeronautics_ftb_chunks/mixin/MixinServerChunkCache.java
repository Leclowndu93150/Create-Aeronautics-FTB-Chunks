package com.leclowndu93150.create_aeronautics_ftb_chunks.mixin;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerChunkCache.class)
public abstract class MixinServerChunkCache {

    @Shadow
    @Final
    private ServerLevel level;

    @Inject(
            method = {
                    "addRegionTicket(Lnet/minecraft/server/level/TicketType;Lnet/minecraft/world/level/ChunkPos;ILjava/lang/Object;Z)V",
                    "removeRegionTicket(Lnet/minecraft/server/level/TicketType;Lnet/minecraft/world/level/ChunkPos;ILjava/lang/Object;Z)V"
            },
            at = @At("HEAD"),
            cancellable = true,
            require = 2,
            remap = false
    )
    private <T> void keepPlotChunksOutOfDistanceManager(TicketType<T> type, ChunkPos pos, int distance,
                                                       T value, boolean forceTicks, CallbackInfo ci) {
        SubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container != null && container.inBounds(pos)) {
            ci.cancel();
        }
    }
}
