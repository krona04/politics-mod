package net.krona.politicsmod;

import net.krona.politicsmod.network.SyncHudPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.krona.politicsmod.network.ModNetworking;
import net.minecraft.world.entity.player.Player;

public class PoliticsEvents {

    public static void onLevelTick(ServerLevel serverLevel) {
        if (serverLevel.dimension() == net.minecraft.world.level.Level.OVERWORLD) {
            long time = serverLevel.getDayTime();
            if (time % 24000 == 0) {
                PoliticsManager manager = PoliticsManager.get(serverLevel);
                if (manager != null) manager.collectTaxes(serverLevel);
            }
        }
    }

    public static void onPlayerTick(Player entity) {
        if (entity instanceof ServerPlayer player) {
            if (player.tickCount % 20 == 0) {
                updatePlayerHud(player);
            }
        }
    }

    private static void updatePlayerHud(ServerPlayer player) {
        PoliticsManager manager = PoliticsManager.get(player.level());
        if (manager == null) return;

        ChunkPos pos = player.chunkPosition();
        String currentCountry = manager.getCountryNameAt(pos);
        String currentCity = manager.getCityAt(pos);

        String ownedCountry = manager.getCountryByOwner(player.getUUID());
        int balance = 0;
        if (ownedCountry != null) {
            balance = manager.getBalance(ownedCountry);
        }

        ModNetworking.toPlayer(player, new SyncHudPayload(
                currentCountry == null ? "" : currentCountry,
                currentCity == null ? "" : currentCity,
                balance
        ));
    }
}
