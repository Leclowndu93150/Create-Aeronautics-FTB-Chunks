package com.leclowndu93150.create_aeronautics_ftb_chunks.client;

import com.leclowndu93150.create_aeronautics_ftb_chunks.client.gui.ContraptionClaimScreen;
import com.leclowndu93150.create_aeronautics_ftb_chunks.client.map.ClientShipMapManager;
import com.leclowndu93150.create_aeronautics_ftb_chunks.network.OpenContraptionScreenPacket;
import com.leclowndu93150.create_aeronautics_ftb_chunks.network.ShipMapDataPacket;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class ClientHandler {

    public static void initialize() {
        ClientShipMapManager.initialize();
        NeoForge.EVENT_BUS.addListener(ClientHandler::onLoggingOut);
    }

    public static void handleOpenContraptionScreen(OpenContraptionScreenPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> ContraptionClaimScreen.open(packet));
    }

    public static void handleShipMapData(ShipMapDataPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> ClientShipMapManager.accept(packet));
    }

    private static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientShipMapManager.clear();
    }
}
