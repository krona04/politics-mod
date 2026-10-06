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
            if (player.tickCount % 5 == 0) {
                net.krona.politicsmod.protection.Protection.checkEntry(player);
            }
            if (player.tickCount % 20 == 0) {
                updatePlayerHud(player);
            }
        }
    }

    private static void updatePlayerHud(ServerPlayer player) {
        PoliticsManager manager = PoliticsManager.get(player.level());
        if (manager == null) return;

        ChunkPos pos = player.chunkPosition();
        boolean land = PoliticsManager.hasTerritory(player.level());
        String currentCountry = land ? manager.getCountryNameAt(pos) : null;
        String currentCity = land ? manager.getCityAt(pos) : null;

        String myCountry = manager.getPlayerCountry(player.getUUID());
        int treasury = myCountry != null ? manager.getBalance(myCountry) : SyncHudPayload.NO_COUNTRY;

        net.krona.politicsmod.politics.Plot plot = land ? manager.getPlot(pos) : null;
        ModNetworking.toPlayer(player, new SyncHudPayload(
                currentCountry == null ? "" : currentCountry,
                currentCity == null ? "" : currentCity,
                treasury,
                manager.getWallet(player.getUUID()).balance(),
                plot != null,
                plot != null && plot.isOwned() ? plot.ownerName : "",
                plot != null ? plot.salePrice : 0,
                plot != null ? plot.rentPrice : 0,
                myCountry == null ? "" : myCountry
        ));
    }
}
