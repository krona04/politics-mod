package net.krona.politicsmod.registry;

import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import net.krona.politicsmod.CityStoneBlock;
import net.krona.politicsmod.FoundingStoneBlock;
import net.krona.politicsmod.Politicsmod;
import net.krona.politicsmod.ResidentialBuildingBlock;
import net.krona.politicsmod.TradeWarehouseBlock;
import net.krona.politicsmod.block.EmbassyBlock;
import net.krona.politicsmod.block.RadarBlock;
import net.krona.politicsmod.block.TaxBlock;
import net.krona.politicsmod.block.VaultBlock;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

/**
 * Block registration (replaces NeoForge's DeferredRegister).
 * The API is nearly identical; differences:
 *   DeferredHolder<Block, Block>  ->  RegistrySupplier<Block>
 *   DeferredRegister.create(BuiltInRegistries.BLOCK, MODID)
 *       -> DeferredRegister.create(MODID, Registries.BLOCK)
 *   bus registration: BLOCKS.register(modEventBus) -> BLOCKS.register()
 */
public final class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(Politicsmod.MODID, Registries.BLOCK);

    public static final RegistrySupplier<Block> FOUNDING_STONE = BLOCKS.register("founding_stone",
            () -> new FoundingStoneBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.STONE).strength(3.0f).noOcclusion()));

    public static final RegistrySupplier<Block> CITY_STONE = BLOCKS.register("city_stone",
            () -> new CityStoneBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.STONE).strength(3.0f).noOcclusion()));

    public static final RegistrySupplier<Block> RESIDENTIAL_BUILDING = BLOCKS.register("residential_building",
            () -> new ResidentialBuildingBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD).strength(2.0f).noOcclusion()));

    public static final RegistrySupplier<Block> TRADE_WAREHOUSE = BLOCKS.register("trade_warehouse",
            () -> new TradeWarehouseBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD).strength(2.5f).sound(SoundType.WOOD).noOcclusion()));

    public static final RegistrySupplier<Block> TAX_BLOCK = BLOCKS.register("tax_block",
            () -> new TaxBlock(BlockBehaviour.Properties.of()
                    .strength(3.0f).requiresCorrectToolForDrops().noOcclusion()));

    public static final RegistrySupplier<Block> EMBASSY_BLOCK = BLOCKS.register("embassy_block",
            () -> new EmbassyBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.GOLD).strength(3.5f).noOcclusion()));

    public static final RegistrySupplier<Block> RADAR_BLOCK = BLOCKS.register("radar_block",
            () -> new RadarBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_RED).strength(3.5f).noOcclusion()));

    public static final RegistrySupplier<Block> VAULT_BLOCK = BLOCKS.register("vault_block",
            () -> new VaultBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_GRAY).strength(5.0f).noOcclusion().requiresCorrectToolForDrops()));

    private ModBlocks() {
    }

    public static void register() {
        BLOCKS.register();
    }
}
