package net.krona.politicsmod.fabric.client;

import net.fabricmc.api.ClientModInitializer;
import net.krona.politicsmod.client.PoliticsmodClient;

/**
 * Fabric client entry point.
 * Declared in fabric.mod.json -> entrypoints.client
 */
public final class PoliticsmodFabricClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        PoliticsmodClient.init();
    }
}
