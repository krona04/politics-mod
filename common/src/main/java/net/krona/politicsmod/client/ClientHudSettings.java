package net.krona.politicsmod.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.krona.politicsmod.Politicsmod;
import net.krona.politicsmod.platform.PlatformHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * How the country info panel is shown (0.5.1): when, where, how it looks and how big; kept per player in
 * config/politicsmod-client.json. Changed in HudSettingsScreen (the HUD key) or with /politicsmod hud.
 */
public final class ClientHudSettings {

    public enum Mode {
        /** Always, as before 0.5.1. */
        ALWAYS,
        /** Only on the land of a country; in the wilderness only the radar warning. */
        TERRITORY,
        /** No corner panel, only the name of a country or city shown big when entering it. */
        BANNER,
        /** Nothing. */
        OFF;

        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }

        public Component label() {
            return Component.translatable("gui.politicsmod.hud.mode." + key());
        }
    }

    /** Where the panel sits on the screen. */
    public enum Position {
        TOP_LEFT, TOP_CENTER, TOP_RIGHT, MIDDLE_LEFT, MIDDLE_RIGHT, BOTTOM_RIGHT;

        /** Arrow shown on its button in the settings screen. */
        public String arrow() {
            return switch (this) {
                case TOP_LEFT -> "↖";
                case TOP_CENTER -> "↑";
                case TOP_RIGHT -> "↗";
                case MIDDLE_LEFT -> "←";
                case MIDDLE_RIGHT -> "→";
                case BOTTOM_RIGHT -> "↘";
            };
        }
    }

    /** How the panel looks. */
    public enum Style {
        /** A dark card with the flag, the name and a line per value. */
        CARD,
        /** Everything on one line. */
        COMPACT,
        /** Lines of text with icons, no background. */
        TEXT;

        public Component label() {
            return Component.translatable("gui.politicsmod.hud.style." + name().toLowerCase(Locale.ROOT));
        }
    }

    /** Panel sizes the settings offer, %. */
    public static final int[] SCALES = {75, 100, 125, 150};

    private static final String FILE_NAME = Politicsmod.MODID + "-client.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static Mode mode = Mode.ALWAYS;
    private static Position position = Position.TOP_LEFT;
    private static Style style = Style.CARD;
    private static int scale = 100;
    private static boolean loaded = false;

    private ClientHudSettings() {
    }

    public static Mode mode() {
        if (!loaded) load();
        return mode;
    }

    public static void set(Mode next, boolean announce) {
        if (!loaded) load(); // so the other settings are not lost when this one is saved
        mode = next;
        save();
        if (announce) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                mc.player.displayClientMessage(Component.translatable("gui.politicsmod.hud.mode_set",
                        next.label().copy().withStyle(ChatFormatting.YELLOW)), true);
            }
        }
    }

    public static void cycle() {
        Mode[] all = Mode.values();
        set(all[(mode().ordinal() + 1) % all.length], true);
    }

    /** Opens the settings screen; /politicsmod hud calls it through BorderSettingsPayload. */
    public static void openSettings() {
        Minecraft.getInstance().setScreen(new HudSettingsScreen());
    }

    public static Position position() {
        if (!loaded) load();
        return position;
    }

    public static Style style() {
        if (!loaded) load();
        return style;
    }

    /** Panel size, %. */
    public static int scale() {
        if (!loaded) load();
        return scale;
    }

    public static void setPosition(Position p) {
        if (!loaded) load(); // so the other settings are not lost when this one is saved
        position = p;
        save();
    }

    public static void setStyle(Style s) {
        if (!loaded) load(); // so the other settings are not lost when this one is saved
        style = s;
        save();
    }

    public static void setScale(int percent) {
        if (!loaded) load(); // so the other settings are not lost when this one is saved
        scale = Math.max(50, Math.min(200, percent));
        save();
    }

    private static Path path() {
        return PlatformHelper.getConfigDirectory().resolve(FILE_NAME);
    }

    private static void load() {
        loaded = true;
        Path path = path();
        if (!Files.exists(path)) return;
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
            if (json.has("hudMode")) mode = Mode.valueOf(json.get("hudMode").getAsString().toUpperCase(Locale.ROOT));
            if (json.has("hudPosition")) position = Position.valueOf(json.get("hudPosition").getAsString().toUpperCase(Locale.ROOT));
            if (json.has("hudStyle")) style = Style.valueOf(json.get("hudStyle").getAsString().toUpperCase(Locale.ROOT));
            if (json.has("hudScale")) scale = Math.max(50, Math.min(200, json.get("hudScale").getAsInt()));
        } catch (Exception e) {
            Politicsmod.LOGGER.warn("Could not read {}: {}", FILE_NAME, e.getMessage());
        }
    }

    private static void save() {
        JsonObject json = new JsonObject();
        json.addProperty("hudMode", mode.key());
        json.addProperty("hudPosition", position.name().toLowerCase(Locale.ROOT));
        json.addProperty("hudStyle", style.name().toLowerCase(Locale.ROOT));
        json.addProperty("hudScale", scale);
        try {
            Files.createDirectories(path().getParent());
            try (Writer writer = Files.newBufferedWriter(path(), StandardCharsets.UTF_8)) {
                GSON.toJson(json, writer);
            }
        } catch (Exception e) {
            Politicsmod.LOGGER.warn("Could not write {}: {}", FILE_NAME, e.getMessage());
        }
    }
}
