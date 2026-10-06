package net.krona.politicsmod.economy;

import net.krona.politicsmod.PoliticsManager;
import net.krona.politicsmod.config.PoliticsConfig;
import net.krona.politicsmod.politics.Country;
import net.krona.politicsmod.politics.CountryRole;
import net.krona.politicsmod.politics.Plot;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/**
 * Buying, renting and rent collection for plots. Sale money and rent go to the city's treasury,
 * because plots are city land.
 */
public final class PlotService {

    public enum Result { OK, NOT_FOR_SALE, NOT_FOR_RENT, TAKEN, NOT_CITIZEN, NO_FUNDS, LIMIT }

    private PlotService() {
    }

    /** Buys (rent = false) or starts renting (rent = true) a free plot; the first rent is paid immediately. */
    public static Result acquire(PoliticsManager mgr, Plot plot, UUID player, String playerName, boolean rent) {
        if (plot.isOwned()) return Result.TAKEN;
        Country country = mgr.getCountry(plot.country);
        if (country == null || country.getRole(player) == CountryRole.GUEST) return Result.NOT_CITIZEN;
        if (mgr.countOwnedPlots(player) >= PoliticsConfig.get().maxPlotsPerPlayer) return Result.LIMIT;

        int price = rent ? plot.rentPrice : plot.salePrice;
        if (price <= 0) return rent ? Result.NOT_FOR_RENT : Result.NOT_FOR_SALE;
        Wallet wallet = mgr.getWallet(player);
        if (wallet.balance() < price) return Result.NO_FUNDS;

        wallet.apply(-price, rent ? WalletTransaction.TYPE_RENT : WalletTransaction.TYPE_PLOT, plot.city,
                PoliticsConfig.get().walletHistorySize);
        country.cityBalances.merge(plot.city, price, Integer::sum);
        plot.release();
        plot.owner = player;
        plot.ownerName = playerName;
        plot.rented = rent;
        mgr.setDirty();
        return Result.OK;
    }

    /** Called from the economy cycle: collects rent and frees plots of players who left the country. */
    public static void processCycle(PoliticsManager mgr, MinecraftServer server) {
        int historyLimit = PoliticsConfig.get().walletHistorySize;
        for (Plot listed : mgr.getPlots()) {
            Plot plot = mgr.getPlot(listed.pos); // drops plots whose land changed hands
            if (plot == null || !plot.isOwned()) continue;

            Country country = mgr.getCountry(plot.country);
            UUID owner = plot.owner;
            if (country == null || country.getRole(owner) == CountryRole.GUEST) {
                plot.release();
                notify(server, owner, Component.translatable("message.politicsmod.plot.lost_citizenship", plot.city)
                        .withStyle(ChatFormatting.RED));
                continue;
            }
            if (!plot.rented || plot.rentPrice <= 0) continue;

            Wallet wallet = mgr.getWallet(owner);
            if (wallet.balance() < plot.rentPrice) {
                plot.release();
                notify(server, owner, Component.translatable("message.politicsmod.plot.evicted_rent", plot.city, plot.rentPrice)
                        .withStyle(ChatFormatting.RED));
                continue;
            }
            wallet.apply(-plot.rentPrice, WalletTransaction.TYPE_RENT, plot.city, historyLimit);
            country.cityBalances.merge(plot.city, plot.rentPrice, Integer::sum);
        }
        mgr.setDirty();
    }

    private static void notify(MinecraftServer server, UUID playerId, Component message) {
        if (server == null) return;
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        if (player != null) player.sendSystemMessage(message);
    }
}
