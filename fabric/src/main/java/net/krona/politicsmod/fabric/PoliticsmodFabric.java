package net.krona.politicsmod.fabric;

import net.fabricmc.api.ModInitializer;
import net.krona.politicsmod.Politicsmod;

/**
 * Fabric entry point (the equivalent of the @Mod class on NeoForge).
 * Declared in fabric.mod.json -> entrypoints.main
 */
public final class PoliticsmodFabric implements ModInitializer {

    @Override
    public void onInitialize() {
        Politicsmod.init();
    }
}
