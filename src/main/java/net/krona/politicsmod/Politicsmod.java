package net.krona.politicsmod;

import net.krona.politicsmod.network.*;
import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;

@Mod(Politicsmod.MODID)
public class Politicsmod {
    public static final String MODID = "politicsmod";
    public static final Logger LOGGER = LogUtils.getLogger();

    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(BuiltInRegistries.BLOCK, MODID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(BuiltInRegistries.ITEM, MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(BuiltInRegistries.BLOCK_ENTITY_TYPE, MODID);

    public static final DeferredHolder<Block, Block> FOUNDING_STONE_BLOCK = BLOCKS.register("founding_stone",
            () -> new FoundingStoneBlock(BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(3.0f).noOcclusion()));

    public static final DeferredHolder<Block, Block> CITY_STONE = BLOCKS.register("city_stone",
            () -> new CityStoneBlock(BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(3.0f).noOcclusion()));

    public static final DeferredHolder<Block, Block> RESIDENTIAL_BUILDING_BLOCK = BLOCKS.register("residential_building",
            () -> new ResidentialBuildingBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2.0f).noOcclusion()));

    public static final DeferredHolder<Item, Item> FOUNDING_STONE_ITEM = ITEMS.register("founding_stone",
            () -> new net.krona.politicsmod.item.FoundingStoneItem(FOUNDING_STONE_BLOCK.get(), new Item.Properties()));

    public static final DeferredHolder<Item, Item> CITY_STONE_ITEM = ITEMS.register("city_stone",
            () -> new BlockItem(CITY_STONE.get(), new Item.Properties()));

    public static final DeferredHolder<Item, Item> RESIDENTIAL_BUILDING_ITEM = ITEMS.register("residential_building",
            () -> new BlockItem(RESIDENTIAL_BUILDING_BLOCK.get(), new Item.Properties()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<net.krona.politicsmod.block.entity.FoundingStoneEntity>> FOUNDING_STONE_BE = BLOCK_ENTITIES.register("founding_stone",
            () -> BlockEntityType.Builder.of(net.krona.politicsmod.block.entity.FoundingStoneEntity::new, FOUNDING_STONE_BLOCK.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<net.krona.politicsmod.block.entity.ResidentialBuildingEntity>> RESIDENTIAL_BUILDING_BE = BLOCK_ENTITIES.register("residential_building",
            () -> BlockEntityType.Builder.of(net.krona.politicsmod.block.entity.ResidentialBuildingEntity::new, RESIDENTIAL_BUILDING_BLOCK.get()).build(null));

    public static final DeferredHolder<Block, Block> TRADE_WAREHOUSE_BLOCK = BLOCKS.register("trade_warehouse",
            () -> new net.krona.politicsmod.TradeWarehouseBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2.5f)));

    public static final DeferredHolder<Item, Item> TRADE_WAREHOUSE_ITEM = ITEMS.register("trade_warehouse",
            () -> new BlockItem(TRADE_WAREHOUSE_BLOCK.get(), new Item.Properties()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<net.krona.politicsmod.block.entity.TradeWarehouseEntity>> TRADE_WAREHOUSE_BE = BLOCK_ENTITIES.register("trade_warehouse",
            () -> BlockEntityType.Builder.of(net.krona.politicsmod.block.entity.TradeWarehouseEntity::new, TRADE_WAREHOUSE_BLOCK.get()).build(null));

    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> POLITICS_TAB = CREATIVE_MODE_TABS.register("politics_tab",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("creativetab.politicsmod.tab"))
                    .icon(() -> RESIDENTIAL_BUILDING_ITEM.get().getDefaultInstance())
                    .displayItems((parameters, output) -> {
                        output.accept(FOUNDING_STONE_ITEM.get());
                        output.accept(CITY_STONE_ITEM.get());
                        output.accept(RESIDENTIAL_BUILDING_ITEM.get());
                        // output.accept(TRADE_WAREHOUSE_ITEM.get());
                    }).build());

    public Politicsmod(IEventBus modEventBus, ModContainer modContainer) {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        BLOCK_ENTITIES.register(modEventBus);

        CREATIVE_MODE_TABS.register(modEventBus);

        modEventBus.addListener(this::addCreative);
        modEventBus.addListener(this::registerNetwork);

        // NeoForge.EVENT_BUS.register(this);
    }

    private void addCreative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.BUILDING_BLOCKS) {
            event.accept(FOUNDING_STONE_ITEM.get());
            event.accept(CITY_STONE_ITEM.get());
            event.accept(RESIDENTIAL_BUILDING_ITEM.get());
        }
    }

    private void registerNetwork(final RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar("1");

        NeoForge.EVENT_BUS.addListener(ModCommands::onRegisterCommands);

        registrar.playToClient(SyncChunkPayload.TYPE, SyncChunkPayload.CODEC, SyncChunkPayload::handle);
        registrar.playToClient(OpenCountryMenuPayload.TYPE, OpenCountryMenuPayload.CODEC, OpenCountryMenuPayload::handle);
        registrar.playToClient(SyncHudPayload.TYPE, SyncHudPayload.CODEC, SyncHudPayload::handle);

        registrar.playToServer(CreateCountryPayload.TYPE, CreateCountryPayload.CODEC, CreateCountryPayload::handle);
        registrar.playToServer(CreateCityPayload.TYPE, CreateCityPayload.CODEC, CreateCityPayload::handle);
        registrar.playToServer(ClaimLandPayload.TYPE, ClaimLandPayload.CODEC, ClaimLandPayload::handle);
        registrar.playToServer(FoundCityPayload.TYPE, FoundCityPayload.CODEC, FoundCityPayload::handle);
        registrar.playToServer(RequestOpenMenuPayload.TYPE, RequestOpenMenuPayload.CODEC, RequestOpenMenuPayload::handle);
        registrar.playToServer(UpdateFlagPayload.TYPE, UpdateFlagPayload.CODEC, UpdateFlagPayload::handle);
        registrar.playToServer(ManagePoliticsPayload.TYPE, ManagePoliticsPayload.CODEC, ManagePoliticsPayload::handle);

        registrar.playToServer(UpdateBuildingPayload.TYPE, UpdateBuildingPayload.CODEC, UpdateBuildingPayload::handle);
    }
}