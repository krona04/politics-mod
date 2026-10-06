package net.krona.politicsmod.integration.webmap;

import dev.architectury.platform.Platform;
import net.krona.politicsmod.PoliticsManager;
import net.krona.politicsmod.Politicsmod;
import net.krona.politicsmod.config.PoliticsConfig;
import net.krona.politicsmod.integration.ChunkOutline;
import net.krona.politicsmod.politics.Country;
import net.krona.politicsmod.politics.Notify;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.ChunkPos;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Country borders on BlueMap and Dynmap. Both are optional: their classes are touched only
 * after Platform.isModLoaded says the mod is there, so the mod runs fine without them.
 */
public final class WebMaps {

    /** One country, ready to draw. */
    public record Area(String id, String name, String description, int color, List<ChunkOutline.Polygon> polygons) {
    }

    interface Layer {
        void update(List<Area> areas);

        void close();
    }

    /** Redraw at least this often even without border changes, so descriptions stay current. */
    private static final int FULL_REFRESH_SECONDS = 300;

    private static final List<Layer> LAYERS = new ArrayList<>();
    private static MinecraftServer server;
    private static int drawnVersion = -1;
    private static int seconds = 0;
    private static int sinceFull = 0;
    static volatile boolean forceRedraw = false;

    private WebMaps() {
    }

    public static void start(MinecraftServer srv) {
        server = srv;
        drawnVersion = -1;
        if (!PoliticsConfig.get().webMapsEnabled) return;
        if (Platform.isModLoaded("bluemap")) {
            try {
                LAYERS.add(new BlueMapLayer(srv));
                Politicsmod.LOGGER.info("BlueMap found: country borders will be shown on the web map");
            } catch (Throwable t) {
                Politicsmod.LOGGER.warn("Could not set up the BlueMap layer", t);
            }
        }
        if (Platform.isModLoaded("dynmap")) {
            try {
                LAYERS.add(new DynmapLayer(srv));
                Politicsmod.LOGGER.info("Dynmap found: country borders will be shown on the web map");
            } catch (Throwable t) {
                Politicsmod.LOGGER.warn("Could not set up the Dynmap layer", t);
            }
        }
    }

    public static void stop() {
        for (Layer layer : LAYERS) {
            try {
                layer.close();
            } catch (Throwable t) {
                Politicsmod.LOGGER.warn("Could not close a web map layer", t);
            }
        }
        LAYERS.clear();
        server = null;
    }

    /** Called once a second. */
    public static void tick(PoliticsManager mgr) {
        if (LAYERS.isEmpty() || server == null) return;
        sinceFull++;
        if (++seconds < PoliticsConfig.get().webMapUpdateSeconds && !forceRedraw) return;
        seconds = 0;
        boolean changed = mgr.getTerritoryVersion() != drawnVersion;
        if (!changed && !forceRedraw && sinceFull < FULL_REFRESH_SECONDS) return;
        forceRedraw = false;
        sinceFull = 0;
        drawnVersion = mgr.getTerritoryVersion();

        List<Area> areas = build(mgr);
        for (Layer layer : LAYERS) {
            try {
                layer.update(areas);
            } catch (Throwable t) {
                Politicsmod.LOGGER.warn("Could not update a web map layer", t);
            }
        }
    }

    private static List<Area> build(PoliticsManager mgr) {
        Map<String, List<int[]>> byCountry = new HashMap<>();
        for (Map.Entry<ChunkPos, String> e : mgr.getClaims().entrySet()) {
            byCountry.computeIfAbsent(e.getValue(), k -> new ArrayList<>()).add(new int[]{e.getKey().x, e.getKey().z});
        }
        List<Area> areas = new ArrayList<>();
        for (Map.Entry<String, List<int[]>> e : byCountry.entrySet()) {
            Country c = mgr.getCountry(e.getKey());
            if (c == null) continue;
            areas.add(new Area(id(c.getName()), c.getName(), describe(c), Country.mapColor(c.getName()), ChunkOutline.trace(e.getValue())));
        }
        return areas;
    }

    private static String describe(Country c) {
        String leader = c.getLeader() == null ? "—" : Notify.nameOf(c.getLeader());
        return "<b>" + html(c.getName()) + "</b><br>"
                + "Leader: " + html(leader) + "<br>"
                + "Capital: " + html(c.capital == null || c.capital.isEmpty() ? "—" : c.capital) + "<br>"
                + "Cities: " + c.cities.size() + "<br>"
                + "Citizens: " + c.getMembers().size();
    }

    /** Country names are shown on a web page, so they are escaped. */
    static String html(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    /** Marker ids only allow simple characters. */
    private static String id(String name) {
        return "politicsmod_" + Integer.toHexString(name.hashCode()) + "_" + name.replaceAll("[^A-Za-z0-9]", "");
    }
}
