package net.krona.politicsmod.client;

import net.minecraft.world.level.ChunkPos;
import java.util.HashMap;
import java.util.Map;

public class ClientPoliticsData {
    /** Owner of a claimed chunk as the server last reported it. */
    public record ChunkInfo(String country, String city) {
    }

    private static final Map<ChunkPos, Integer> CHUNK_COLORS = new HashMap<>();
    private static final Map<ChunkPos, ChunkInfo> CHUNK_INFO = new HashMap<>();
    private static final Map<String, String> FLAGS = new HashMap<>();
    /** Bumped on every territory change; minimap overlays redraw when it moves. */
    private static int territoryVersion = 0;
    private static boolean renderBorders = true;

    public static String currentCountry = "";
    /** The player's own country, "" if none. */
    public static String myCountry = "";
    public static String currentCity = "";
    public static int myTreasury = net.krona.politicsmod.network.SyncHudPayload.NO_COUNTRY;
    public static int myWallet = 0;
    public static boolean plotHere = false;
    public static String plotOwner = "";
    public static int plotSale = 0;
    public static int plotRent = 0;
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

    public static void handleChunkSync(ChunkPos pos, int color, String country, String city) {
        if (country.isEmpty()) {
            CHUNK_COLORS.remove(pos);
            CHUNK_INFO.remove(pos);
        } else {
            CHUNK_COLORS.put(pos, color);
            CHUNK_INFO.put(pos, new ChunkInfo(country, city));
        }
        territoryVersion++;
    }

    public static ChunkInfo getChunkInfo(ChunkPos pos) {
        return CHUNK_INFO.get(pos);
    }

    public static Map<ChunkPos, ChunkInfo> getChunkInfo() {
        return CHUNK_INFO;
    }

    public static int getTerritoryVersion() {
        return territoryVersion;
    }

    public static void setFlags(Map<String, String> flags) {
        FLAGS.clear();
        FLAGS.putAll(flags);
    }

    /** Flag image URL of a country, or "" when it has none. */
    public static String getFlagUrl(String country) {
        return country == null ? "" : FLAGS.getOrDefault(country, "");
    }

    public static void updateHud(net.krona.politicsmod.network.SyncHudPayload hud) {
        currentCountry = hud.country().isEmpty()
                ? net.minecraft.network.chat.Component.translatable("gui.politicsmod.hud.wilderness").getString()
                : hud.country();
        currentCity = hud.city();
        myTreasury = hud.treasury();
        myWallet = hud.wallet();
        plotHere = hud.plot();
        plotOwner = hud.plotOwner();
        plotSale = hud.plotSale();
        plotRent = hud.plotRent();
        myCountry = hud.myCountry();
    }

    public static void addChunk(ChunkPos pos, int color) {
        CHUNK_COLORS.put(pos, color);
        renderBorders = true;
    }

    public static void clear() {
        CHUNK_COLORS.clear();
        CHUNK_INFO.clear();
        FLAGS.clear();
        territoryVersion++;
        currentCountry = "";
        currentCity = "";
        myCountry = "";
        myTreasury = net.krona.politicsmod.network.SyncHudPayload.NO_COUNTRY;
        myWallet = 0;
        plotHere = false;
        plotOwner = "";
        radarAlert = "";
        radarAlertExpiry = 0L;
        countryDetails = null;
        marketData = null;
    }

    public static Map<ChunkPos, Integer> getChunks() { return CHUNK_COLORS; }
    public static boolean shouldRender() { return renderBorders; }
    public static void toggleRender() { renderBorders = !renderBorders; }
}