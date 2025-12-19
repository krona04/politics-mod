package net.krona.politicsmod;

import net.krona.politicsmod.network.SyncChunkPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = Politicsmod.MODID)
public class ModEvents {

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {

            PoliticsManager manager = PoliticsManager.get(player.level());

            if (manager != null) {
                manager.forEachClaim((pos, isCity) -> {
                    PacketDistributor.sendToPlayer(
                            player,
                            new SyncChunkPayload(pos, isCity)
                    );
                });
            }
        }
    }
}
