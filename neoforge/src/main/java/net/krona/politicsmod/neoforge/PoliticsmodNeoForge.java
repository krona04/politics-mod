package net.krona.politicsmod.neoforge;

import net.krona.politicsmod.Politicsmod;
import net.krona.politicsmod.client.PoliticsmodClient;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;

/**
 * NeoForge entry point. Architectury 13 finds the mod event bus itself,
 * so a manual EventBuses.registerModEventBus call is no longer needed.
 */
@Mod(Politicsmod.MODID)
public final class PoliticsmodNeoForge {

    public PoliticsmodNeoForge() {
        Politicsmod.init();

        if (FMLEnvironment.dist == Dist.CLIENT) {
            PoliticsmodClient.init();
        }
    }
}
