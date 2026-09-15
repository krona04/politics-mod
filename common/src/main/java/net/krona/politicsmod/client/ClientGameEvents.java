package net.krona.politicsmod.client;

import net.krona.politicsmod.network.ModNetworking;
import net.krona.politicsmod.network.RequestOpenMenuPayload;
import net.minecraft.client.Minecraft;

public class ClientGameEvents {

    // Poll the key every client tick (works on both loaders)
    public static void onClientTick(Minecraft minecraft) {
        while (ModKeyMappings.OPEN_COUNTRY_MENU.consumeClick()) {
            if (minecraft.player != null) {
                ModNetworking.toServer(new RequestOpenMenuPayload());
            }
        }
    }
}
