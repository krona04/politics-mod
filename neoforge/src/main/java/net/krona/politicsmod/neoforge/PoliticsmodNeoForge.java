package net.krona.politicsmod.neoforge;

import net.krona.politicsmod.Politicsmod;
import net.krona.politicsmod.client.PoliticsmodClient;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.krona.politicsmod.platform.neoforge.PermissionsImpl;

/**
 * NeoForge entry point. Architectury 13 finds the mod event bus itself,
 * so a manual EventBuses.registerModEventBus call is no longer needed.
 */
@Mod(Politicsmod.MODID)
public final class PoliticsmodNeoForge {

    public PoliticsmodNeoForge() {
        Politicsmod.init();
        NeoForge.EVENT_BUS.addListener(PermissionsImpl::onGatherNodes);

        if (FMLEnvironment.dist == Dist.CLIENT) {
            PoliticsmodClient.init();
        }
    }
}
