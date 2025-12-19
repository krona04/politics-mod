package net.krona.politicsmod.client;

import net.krona.politicsmod.Politicsmod;
import net.krona.politicsmod.network.RequestOpenMenuPayload;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = Politicsmod.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.GAME)
public class ClientGameEvents {

    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {
        if (ModKeyMappings.OPEN_COUNTRY_MENU.consumeClick()) {
            PacketDistributor.sendToServer(new RequestOpenMenuPayload());
        }
    }

    @SubscribeEvent
    public static void onLogOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientPoliticsData.clear();
    }

    @SubscribeEvent
    public static void onLogIn(ClientPlayerNetworkEvent.LoggingIn event) {
        ClientPoliticsData.clear();
    }
}
