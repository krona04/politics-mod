package net.krona.politicsmod;

import net.krona.politicsmod.network.SyncHudPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = Politicsmod.MODID, bus = EventBusSubscriber.Bus.GAME)
public class PoliticsEvents {

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel serverLevel && serverLevel.dimension() == net.minecraft.world.level.Level.OVERWORLD) {
            long time = serverLevel.getDayTime();
            if (time % 24000 == 0) {
                PoliticsManager manager = PoliticsManager.get(serverLevel);
                if (manager != null) manager.collectTaxes(serverLevel);
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            if (player.tickCount % 20 == 0) {
                updatePlayerHud(player);
            }
        }
    }

    private static void updatePlayerHud(ServerPlayer player) {
        PoliticsManager manager = PoliticsManager.get(player.level());
        if (manager == null) return;

        ChunkPos pos = player.chunkPosition();
        String currentCountry = manager.getCountryAt(pos);
        String currentCity = manager.getCityAt(pos);

        String ownedCountry = manager.getCountryByOwner(player.getUUID());
        int balance = 0;
        if (ownedCountry != null) {
            balance = manager.getBalance(ownedCountry);
        }

        PacketDistributor.sendToPlayer(player, new SyncHudPayload(
                currentCountry == null ? "" : currentCountry,
                currentCity == null ? "" : currentCity,
                balance
        ));
    }
}
