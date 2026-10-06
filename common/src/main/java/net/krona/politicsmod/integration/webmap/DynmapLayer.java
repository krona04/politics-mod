package net.krona.politicsmod.integration.webmap;

import net.krona.politicsmod.integration.ChunkOutline;
import net.minecraft.server.MinecraftServer;
import org.dynmap.DynmapCommonAPI;
import org.dynmap.DynmapCommonAPIListener;
import org.dynmap.markers.AreaMarker;
import org.dynmap.markers.MarkerAPI;
import org.dynmap.markers.MarkerSet;

import java.util.ArrayList;
import java.util.List;

/**
 * Countries as area markers in a "Countries" layer. Loaded only with Dynmap.
 * Dynmap areas have no holes, so unclaimed gaps inside a country are drawn filled.
 */
final class DynmapLayer implements WebMaps.Layer {
    private static final String SET_ID = "politicsmod.countries";

    private final MinecraftServer server;
    private final DynmapCommonAPIListener listener;
    private volatile MarkerAPI markers;

    DynmapLayer(MinecraftServer server) {
        this.server = server;
        this.listener = new DynmapCommonAPIListener() {
            @Override
            public void apiEnabled(DynmapCommonAPI api) {
                markers = api.getMarkerAPI();
                WebMaps.forceRedraw = true;
            }
        };
        DynmapCommonAPIListener.register(listener);
    }

    /** Dynmap names the overworld after the world folder; the Nether and the End have fixed names. */
    private String worldName() {
        return server.getWorldData().getLevelName();
    }

    @Override
    public void update(List<WebMaps.Area> areas) {
        MarkerAPI api = markers;
        if (api == null) return;
        MarkerSet set = api.getMarkerSet(SET_ID);
        if (set == null) set = api.createMarkerSet(SET_ID, "Countries", null, false);
        if (set == null) return;
        for (AreaMarker old : new ArrayList<>(set.getAreaMarkers())) old.deleteMarker();

        String world = worldName();
        for (WebMaps.Area area : areas) {
            int i = 0;
            for (ChunkOutline.Polygon polygon : area.polygons()) {
                List<ChunkOutline.Point> pts = polygon.outer();
                double[] xs = new double[pts.size()], zs = new double[pts.size()];
                for (int k = 0; k < pts.size(); k++) {
                    xs[k] = pts.get(k).x();
                    zs[k] = pts.get(k).z();
                }
                AreaMarker marker = set.createAreaMarker(area.id() + "_" + i++, WebMaps.html(area.name()), true, world, xs, zs, false);
                if (marker == null) continue;
                marker.setLineStyle(2, 0.9, area.color());
                marker.setFillStyle(0.3, area.color());
                marker.setDescription(area.description());
            }
        }
    }

    @Override
    public void close() {
        MarkerAPI api = markers;
        if (api != null) {
            MarkerSet set = api.getMarkerSet(SET_ID);
            if (set != null) set.deleteMarkerSet();
        }
        DynmapCommonAPIListener.unregister(listener);
    }
}
