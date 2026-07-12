package com.leclowndu93150.create_aeronautics_ftb_chunks.client.map;

import com.leclowndu93150.create_aeronautics_ftb_chunks.network.ShipMapDataPacket;
import dev.ftb.mods.ftbchunks.api.client.event.MapIconEvent;
import dev.ftb.mods.ftbchunks.client.gui.LargeMapScreen;
import dev.ftb.mods.ftblibrary.ui.ScreenWrapper;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class ClientShipMapManager {

    private static final Map<UUID, ShipMapIcon> icons = new HashMap<>();
    private static boolean initialized;

    private ClientShipMapManager() {
    }

    public static void initialize() {
        if (initialized) {
            return;
        }
        initialized = true;
        MapIconEvent.MINIMAP.register(ClientShipMapManager::addIcons);
        MapIconEvent.LARGE_MAP.register(ClientShipMapManager::addIcons);
    }

    public static void accept(ShipMapDataPacket packet) {
        Set<UUID> previous = Set.copyOf(icons.keySet());
        Set<UUID> received = new HashSet<>();
        for (ShipMapDataPacket.ShipData ship : packet.ships()) {
            received.add(ship.subLevelId());
            icons.compute(ship.subLevelId(), (id, existing) -> {
                if (existing == null) {
                    return new ShipMapIcon(ship);
                }
                existing.update(ship);
                return existing;
            });
        }
        icons.keySet().removeIf(id -> !received.contains(id));

        if (!previous.equals(received)
                && Minecraft.getInstance().screen instanceof ScreenWrapper wrapper
                && wrapper.getGui() instanceof LargeMapScreen largeMap) {
            largeMap.refreshWidgets();
        }
    }

    public static void clear() {
        icons.clear();
    }

    private static void addIcons(MapIconEvent event) {
        ResourceLocation dimension = event.getDimension().location();
        for (ShipMapIcon icon : icons.values()) {
            if (icon.data().dimension().equals(dimension)) {
                event.add(icon);
            }
        }
    }
}
