package net.krona.politicsmod.registry;

import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import net.krona.politicsmod.Politicsmod;
import net.krona.politicsmod.item.FoundingStoneItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;

public final class ModItems {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(Politicsmod.MODID, Registries.ITEM);

    public static final RegistrySupplier<Item> FOUNDING_STONE = ITEMS.register("founding_stone",
            () -> new FoundingStoneItem(ModBlocks.FOUNDING_STONE.get(), new Item.Properties()));

    public static final RegistrySupplier<Item> CITY_STONE = ITEMS.register("city_stone",
            () -> new BlockItem(ModBlocks.CITY_STONE.get(), new Item.Properties()));

    public static final RegistrySupplier<Item> RESIDENTIAL_BUILDING = ITEMS.register("residential_building",
            () -> new BlockItem(ModBlocks.RESIDENTIAL_BUILDING.get(), new Item.Properties()));

    public static final RegistrySupplier<Item> TRADE_WAREHOUSE = ITEMS.register("trade_warehouse",
            () -> new BlockItem(ModBlocks.TRADE_WAREHOUSE.get(), new Item.Properties()));

    public static final RegistrySupplier<Item> TAX_BLOCK = ITEMS.register("tax_block",
            () -> new BlockItem(ModBlocks.TAX_BLOCK.get(), new Item.Properties()));

    public static final RegistrySupplier<Item> EMBASSY_BLOCK = ITEMS.register("embassy_block",
            () -> new BlockItem(ModBlocks.EMBASSY_BLOCK.get(), new Item.Properties()));

    public static final RegistrySupplier<Item> RADAR_BLOCK = ITEMS.register("radar_block",
            () -> new BlockItem(ModBlocks.RADAR_BLOCK.get(), new Item.Properties()));

    public static final RegistrySupplier<Item> VAULT_BLOCK = ITEMS.register("vault_block",
            () -> new BlockItem(ModBlocks.VAULT_BLOCK.get(), new Item.Properties()));

    private ModItems() {
    }

    public static void register() {
        ITEMS.register();
    }
}
