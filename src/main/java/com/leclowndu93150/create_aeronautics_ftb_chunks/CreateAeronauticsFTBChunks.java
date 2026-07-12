package com.leclowndu93150.create_aeronautics_ftb_chunks;

import com.leclowndu93150.create_aeronautics_ftb_chunks.block.ContraptionClaimBlock;
import com.leclowndu93150.create_aeronautics_ftb_chunks.block.ContraptionClaimBlockEntity;
import com.leclowndu93150.create_aeronautics_ftb_chunks.client.ClientHandler;
import com.leclowndu93150.create_aeronautics_ftb_chunks.event.SubLevelClaimHandler;
import com.leclowndu93150.create_aeronautics_ftb_chunks.network.ContraptionAllyPacket;
import com.leclowndu93150.create_aeronautics_ftb_chunks.network.ContraptionClaimActionPacket;
import com.leclowndu93150.create_aeronautics_ftb_chunks.network.OpenContraptionScreenPacket;
import com.leclowndu93150.create_aeronautics_ftb_chunks.network.ShipMapDataPacket;
import com.leclowndu93150.create_aeronautics_ftb_chunks.network.ServerHandler;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.api.distmarker.Dist;
import net.minecraft.tags.BlockTags;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(CreateAeronauticsFTBChunks.MODID)
public class CreateAeronauticsFTBChunks {

    public static final String MODID = "create_aeronautics_ftb_chunks";
    public static final Logger LOGGER = LoggerFactory.getLogger(MODID);

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MODID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    public static final DeferredBlock<Block> CONTRAPTION_CLAIM_BLOCK = BLOCKS.register(
            "contraption_claim_block",
            () -> new ContraptionClaimBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.5f)
                    .sound(SoundType.METAL)
            )
    );

    public static final DeferredItem<BlockItem> CONTRAPTION_CLAIM_BLOCK_ITEM = ITEMS.registerSimpleBlockItem(
            "contraption_claim_block",
            CONTRAPTION_CLAIM_BLOCK,
            new Item.Properties()
    );

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> CREATIVE_TAB = CREATIVE_TABS.register("main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.create_aeronautics_ftb_chunks.main"))
                    .icon(() -> CONTRAPTION_CLAIM_BLOCK_ITEM.get().getDefaultInstance())
                    .displayItems((params, output) -> output.accept(CONTRAPTION_CLAIM_BLOCK_ITEM.get()))
                    .build()
    );

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ContraptionClaimBlockEntity>> CONTRAPTION_CLAIM_BLOCK_ENTITY =
            BLOCK_ENTITIES.register("contraption_claim_block_entity",
                    () -> BlockEntityType.Builder.of(ContraptionClaimBlockEntity::new, CONTRAPTION_CLAIM_BLOCK.get()).build(null)
            );

    public CreateAeronauticsFTBChunks(IEventBus modEventBus, ModContainer modContainer) {
        ContraptionForceLoadManager.initialize();
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        BLOCK_ENTITIES.register(modEventBus);
        CREATIVE_TABS.register(modEventBus);
        modEventBus.addListener(this::registerPayloads);
        modContainer.registerConfig(ModConfig.Type.SERVER, com.leclowndu93150.create_aeronautics_ftb_chunks.ModConfig.SPEC);
        NeoForge.EVENT_BUS.register(this);
        if (FMLEnvironment.dist == Dist.CLIENT) {
            ClientHandler.initialize();
        }
    }

    private void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("2");
        registrar.playToClient(
                OpenContraptionScreenPacket.TYPE,
                OpenContraptionScreenPacket.STREAM_CODEC,
                ClientHandler::handleOpenContraptionScreen
        );
        registrar.playToClient(
                ShipMapDataPacket.TYPE,
                ShipMapDataPacket.STREAM_CODEC,
                ClientHandler::handleShipMapData
        );
        registrar.playToServer(
                ContraptionClaimActionPacket.TYPE,
                ContraptionClaimActionPacket.STREAM_CODEC,
                ServerHandler::handleContraptionClaimAction
        );
        registrar.playToServer(
                ContraptionAllyPacket.TYPE,
                ContraptionAllyPacket.STREAM_CODEC,
                ServerHandler::handleContraptionAlly
        );
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        var overworld = event.getServer().overworld();
        SubLevelContainer container = SubLevelContainer.getContainer(overworld);
        if (container != null) {
            container.addObserver(new SubLevelClaimHandler(event.getServer()));
        }
    }

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        ContraptionForceLoadManager.refreshExistingClaimBlocks(event.getServer());
        ShipMapSyncManager.tick(event.getServer());
    }

    @SubscribeEvent
    public void onServerStopped(ServerStoppedEvent event) {
        ContraptionForceLoadManager.cleanupAll();
        ShipMapSyncManager.reset();
    }
}
