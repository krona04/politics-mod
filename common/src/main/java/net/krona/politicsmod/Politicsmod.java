package net.krona.politicsmod;

import net.krona.politicsmod.config.PoliticsConfig;
import net.krona.politicsmod.network.ModNetworking;
import net.krona.politicsmod.registry.ModBlockEntities;
import net.krona.politicsmod.registry.ModBlocks;
import net.krona.politicsmod.registry.ModCreativeTabs;
import net.krona.politicsmod.registry.ModItems;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Politicsmod {
    public static final String MODID = "politicsmod";
    public static final Logger LOGGER = LoggerFactory.getLogger(MODID);

    private Politicsmod() {
    }

    public static void init() {
        PoliticsConfig.load();

        // Order matters: blocks before items and block entities, those before the creative tab
        ModBlocks.register();
        ModItems.register();
        ModBlockEntities.register();
        ModCreativeTabs.register();

        ModNetworking.register();
        ModEvents.register();

        LOGGER.info("PoliticsMod initialised");
    }
}
