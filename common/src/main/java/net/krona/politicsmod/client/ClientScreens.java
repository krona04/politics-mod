package net.krona.politicsmod.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

import java.util.List;

/**
 * Opens mod screens on behalf of blocks and packets.
 *
 * Kept out of block classes on purpose: blocks are loaded on dedicated servers too,
 * and a direct reference to Minecraft/Screen there crashes the server
 * ("Cannot load class ...Screen in environment type SERVER").
 * Call only when level.isClientSide is true.
 */
public final class ClientScreens {

    private ClientScreens() {
    }

    public static void openFoundingStone(BlockPos pos) {
        Minecraft.getInstance().setScreen(new FoundingStoneScreen(pos));
    }

    public static void openCityFoundation(BlockPos pos) {
        Minecraft.getInstance().setScreen(new CityFoundationScreen(pos));
    }

    public static void openResidential(BlockPos pos, int population) {
        Minecraft.getInstance().setScreen(new ResidentialScreen(pos, population));
    }

    public static void openMarket() {
        Minecraft.getInstance().setScreen(new MarketScreen());
    }

    public static void openCountryDashboard(String countryName, int balance, List<String> cities,
                                            String flagUrl, String playerRole) {
        Minecraft.getInstance().setScreen(
                new CountryDashboardScreen(countryName, balance, cities, flagUrl, playerRole));
    }
}
