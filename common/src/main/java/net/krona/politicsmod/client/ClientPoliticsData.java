package net.krona.politicsmod.client;

import net.minecraft.world.level.ChunkPos;
import java.util.HashMap;
import java.util.Map;

public class ClientPoliticsData {
    private static final Map<ChunkPos, Integer> CHUNK_COLORS = new HashMap<>();
    private static boolean renderBorders = true;

    public static String currentCountry = "";
    public static String currentCity = "";
    public static int myBalance = 0;
    public static int borderStyle = 0;

    /** Latest data for the dashboard tabs; null until the server sends it. */
    public static net.krona.politicsmod.network.CountryDetailsPayload countryDetails = null;

    public static void setCountryDetails(net.krona.politicsmod.network.CountryDetailsPayload details) {
        countryDetails = details;
    }

    /** Latest market state; the warehouse screen reads it while rendering. */
    public static net.krona.politicsmod.network.MarketDataPayload marketData = null;

    public static void setMarketData(net.krona.politicsmod.network.MarketDataPayload data) {
        marketData = data;
        if (data.open()) ClientScreens.openMarket();
    }

    public static String radarAlert = "";
    private static long radarAlertExpiry = 0L;

    public static void setRadarAlert(String detected) {
        radarAlert = detected;
        radarAlertExpiry = System.currentTimeMillis() + 5000L;
    }

    public static boolean hasActiveRadarAlert() {
        return !radarAlert.isEmpty() && System.currentTimeMillis() < radarAlertExpiry;
    }

    public static void setBorderStyle(int style) {
        borderStyle = style % 3; // wraps around: 0, 1, 2
    }

    public static void handleChunkSync(ChunkPos pos, int color) {
        if (color == 0) {
            CHUNK_COLORS.remove(pos);
        } else {
            CHUNK_COLORS.put(pos, color);
        }
    }

    public static void updateHud(String country, String city, int balance) {
        currentCountry = (country == null || country.isEmpty()) ?
                net.minecraft.network.chat.Component.translatable("gui.politicsmod.hud.wilderness").getString() :
                country;

        currentCity = (city == null) ? "" : city;
        myBalance = balance;
    }

    public static void addChunk(ChunkPos pos, int color) {
        CHUNK_COLORS.put(pos, color);
        renderBorders = true;
    }

    public static void addInitialTerritory(ChunkPos center) {
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                ChunkPos p = new ChunkPos(center.x + x, center.z + z);
                // A new country starts fully green (0x00AA00)
                CHUNK_COLORS.put(p, 0x00AA00);
            }
        }
        renderBorders = true;
    }

    public static void clear() {
        CHUNK_COLORS.clear();
        currentCountry = "";
        currentCity = "";
        myBalance = 0;
        radarAlert = "";
        radarAlertExpiry = 0L;
        countryDetails = null;
        marketData = null;
    }

    public static Map<ChunkPos, Integer> getChunks() { return CHUNK_COLORS; }
    public static boolean shouldRender() { return renderBorders; }
    public static void toggleRender() { renderBorders = !renderBorders; }
}