package net.krona.politicsmod.registry;

import dev.architectury.registry.CreativeTabRegistry;
import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import net.krona.politicsmod.Politicsmod;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;

public final class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Politicsmod.MODID, Registries.CREATIVE_MODE_TAB);

    // Contents are set in displayItems, which runs lazily once registries are filled.
    // Calling ModItems.X.get() directly in register() crashes NeoForge, where items
    // are registered after the mod constructor runs.
    public static final RegistrySupplier<CreativeModeTab> POLITICS_TAB = TABS.register("politics_tab",
            () -> CreativeTabRegistry.create(builder -> builder
                    .title(Component.translatable("creativetab.politicsmod.tab"))
                    .icon(() -> ModItems.RESIDENTIAL_BUILDING.get().getDefaultInstance())
                    .displayItems((parameters, output) -> {
                        output.accept(ModItems.FOUNDING_STONE.get());
                        output.accept(ModItems.CITY_STONE.get());
                        output.accept(ModItems.RESIDENTIAL_BUILDING.get());
                        output.accept(ModItems.TAX_BLOCK.get());
                        output.accept(ModItems.EMBASSY_BLOCK.get());
                        output.accept(ModItems.RADAR_BLOCK.get());
                        output.accept(ModItems.VAULT_BLOCK.get());
                        output.accept(ModItems.TRADE_WAREHOUSE.get());
                    })));

    private ModCreativeTabs() {
    }

    public static void register() {
        TABS.register();
    }
}
