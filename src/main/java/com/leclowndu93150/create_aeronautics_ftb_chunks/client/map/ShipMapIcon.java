package com.leclowndu93150.create_aeronautics_ftb_chunks.client.map;

import com.leclowndu93150.create_aeronautics_ftb_chunks.network.ShipMapDataPacket;
import com.mojang.math.Axis;
import dev.ftb.mods.ftbchunks.api.client.icon.MapIcon;
import dev.ftb.mods.ftbchunks.api.client.icon.MapType;
import dev.ftb.mods.ftblibrary.ui.BaseScreen;
import dev.ftb.mods.ftblibrary.ui.input.Key;
import dev.ftb.mods.ftblibrary.ui.input.MouseButton;
import dev.ftb.mods.ftblibrary.util.TooltipList;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public final class ShipMapIcon implements MapIcon {

    private static final long INTERPOLATION_MILLIS = 250L;

    private ShipMapDataPacket.ShipData data;
    private Vec3 from;
    private Vec3 to;
    private float fromHeading;
    private float toHeading;
    private long updateTime;

    public ShipMapIcon(ShipMapDataPacket.ShipData data) {
        this.data = data;
        this.from = positionOf(data);
        this.to = this.from;
        this.fromHeading = data.heading();
        this.toHeading = data.heading();
        this.updateTime = System.currentTimeMillis();
    }

    public void update(ShipMapDataPacket.ShipData next) {
        double progress = progress();
        this.from = this.from.lerp(this.to, progress);
        this.fromHeading = interpolatedHeading(progress);
        this.to = positionOf(next);
        this.toHeading = next.heading();
        this.data = next;
        this.updateTime = System.currentTimeMillis();
    }

    public ShipMapDataPacket.ShipData data() {
        return data;
    }

    @Override
    public Vec3 getPos(float partialTick) {
        return from.lerp(to, progress());
    }

    @Override
    public boolean isVisible(MapType mapType, double distance, boolean outsideMap) {
        // FTB Chunks passes outsideMap once an icon is beyond the minimap viewport. Keeping an
        // ordinary icon visible there makes its widget stretch/clamp at the edge and appear huge.
        return !mapType.isWorldIcon() && (!mapType.isMinimap() || !outsideMap);
    }

    @Override
    public double getIconScale(MapType mapType) {
        return mapType.isMinimap() ? 0.9 : 1.15;
    }

    @Override
    public int getPriority() {
        return 80;
    }

    @Override
    public void addTooltip(TooltipList tooltip) {
        tooltip.add(Component.literal(data.name()).withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));
        tooltip.add(Component.translatable("create_aeronautics_ftb_chunks.map.owner", data.ownerName())
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable(
                "create_aeronautics_ftb_chunks.map.plot_chunks",
                data.forceLoadedChunks(), data.plotChunks()
        ).withStyle(data.forceLoadedChunks() == data.plotChunks() ? ChatFormatting.GREEN : ChatFormatting.YELLOW));
        tooltip.add(Component.translatable(
                "create_aeronautics_ftb_chunks.map.physics",
                status(data.physicsForceLoaded())
        ).withStyle(data.physicsForceLoaded() ? ChatFormatting.GREEN : ChatFormatting.GRAY));
        tooltip.add(Component.translatable(
                "create_aeronautics_ftb_chunks.map.physical_chunk",
                status(data.physicalChunkLoaded())
        ).withStyle(data.physicalChunkLoaded() ? ChatFormatting.GREEN : ChatFormatting.RED));
        tooltip.add(Component.translatable("create_aeronautics_ftb_chunks.map.altitude", Mth.floor(data.y()))
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("create_aeronautics_ftb_chunks.map.speed", String.format("%.1f", data.speed()))
                .withStyle(ChatFormatting.GRAY));
    }

    @Override
    public boolean onMousePressed(BaseScreen screen, MouseButton button) {
        return false;
    }

    @Override
    public boolean onKeyPressed(BaseScreen screen, Key key) {
        return false;
    }

    @Override
    public void draw(MapType mapType, GuiGraphics graphics, int x, int y, int width, int height, boolean hovered, int alpha) {
        int color = iconColor(hovered, alpha);
        var pose = graphics.pose();
        pose.pushPose();
        pose.translate(x + width / 2.0f, y + height / 2.0f, 20.0f);
        pose.mulPose(Axis.ZP.rotationDegrees(interpolatedHeading(progress())));
        float scale = Math.max(0.55f, Math.min(width, height) / 16.0f);
        pose.scale(scale, scale, 1.0f);

        graphics.fill(-3, -7, 3, 7, 0xDD101820);
        graphics.fill(-2, -9, 2, -6, 0xDD101820);
        graphics.fill(-7, -2, 7, 3, 0xDD101820);
        graphics.fill(-5, 4, 5, 7, 0xDD101820);

        graphics.fill(-2, -7, 2, 6, color);
        graphics.fill(-1, -8, 1, -6, color);
        graphics.fill(-6, -1, 6, 2, color);
        graphics.fill(-4, 4, 4, 6, color);
        graphics.fill(-1, -5, 1, -2, 0xFFDAF4FF);
        pose.popPose();
    }

    private int iconColor(boolean hovered, int alpha) {
        int rgb;
        if (!data.physicalChunkLoaded()) {
            rgb = 0xFF5555;
        } else if (data.physicsForceLoaded()) {
            rgb = 0x55FF88;
        } else if (data.forceLoadedChunks() == data.plotChunks()) {
            rgb = 0xFFD65A;
        } else {
            rgb = 0x55DFFF;
        }
        if (hovered) {
            rgb = lighten(rgb);
        }
        return Mth.clamp(alpha, 0, 255) << 24 | rgb;
    }

    private static int lighten(int rgb) {
        int r = Math.min(255, (rgb >> 16 & 0xFF) + 35);
        int g = Math.min(255, (rgb >> 8 & 0xFF) + 35);
        int b = Math.min(255, (rgb & 0xFF) + 35);
        return r << 16 | g << 8 | b;
    }

    private double progress() {
        return Mth.clamp((System.currentTimeMillis() - updateTime) / (double) INTERPOLATION_MILLIS, 0.0, 1.0);
    }

    private float interpolatedHeading(double progress) {
        float difference = Mth.wrapDegrees(toHeading - fromHeading);
        return fromHeading + difference * (float) progress;
    }

    private static Vec3 positionOf(ShipMapDataPacket.ShipData data) {
        return new Vec3(data.x(), data.y(), data.z());
    }

    private static Component status(boolean enabled) {
        return Component.translatable(enabled
                ? "create_aeronautics_ftb_chunks.map.active"
                : "create_aeronautics_ftb_chunks.map.inactive");
    }
}
