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

    public static void setBorderStyle(int style) {
        borderStyle = style % 3; // Зацикливаем (0, 1, 2)
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
                // При создании страны красим всё в зеленый (0x00AA00)
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
    }

    public static Map<ChunkPos, Integer> getChunks() { return CHUNK_COLORS; }
    public static boolean shouldRender() { return renderBorders; }
    public static void toggleRender() { renderBorders = !renderBorders; }
}