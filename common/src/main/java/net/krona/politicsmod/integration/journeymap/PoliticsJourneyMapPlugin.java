package net.krona.politicsmod.integration.journeymap;

import dev.architectury.event.events.client.ClientTickEvent;
import journeymap.api.v2.client.IClientAPI;
import journeymap.api.v2.client.IClientPlugin;
import journeymap.api.v2.common.JourneyMapPlugin;
import journeymap.api.v2.client.display.PolygonOverlay;
import journeymap.api.v2.client.model.MapPolygon;
import journeymap.api.v2.client.model.ShapeProperties;
import net.krona.politicsmod.Politicsmod;
import net.krona.politicsmod.client.ClientPoliticsData;
import net.krona.politicsmod.integration.ChunkOutline;
import net.krona.politicsmod.politics.Country;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Country borders on the JourneyMap minimap and full-screen map. JourneyMap finds this class by
 * itself (the "journeymap" entrypoint on Fabric, the annotation on NeoForge); without JourneyMap
 * it is never loaded.
 */
@JourneyMapPlugin(apiVersion = "2.0.0")
public class PoliticsJourneyMapPlugin implements IClientPlugin {
    private static final int CHECK_TICKS = 20;

    private IClientAPI api;
    private int drawnVersion = -1;
    private int timer = 0;

    @Override
    public String getModId() {
        return Politicsmod.MODID;
    }

    @Override
    public void initialize(IClientAPI api) {
        this.api = api;
        ClientTickEvent.CLIENT_POST.register(mc -> {
            if (++timer < CHECK_TICKS) return;
            timer = 0;
            int version = ClientPoliticsData.getTerritoryVersion();
            if (version != drawnVersion) {
                drawnVersion = version;
                redraw();
            }
        });
    }

    private void redraw() {
        api.removeAll(Politicsmod.MODID);
        Map<String, List<int[]>> byCountry = new HashMap<>();
        for (Map.Entry<ChunkPos, ClientPoliticsData.ChunkInfo> e : ClientPoliticsData.getChunkInfo().entrySet()) {
            byCountry.computeIfAbsent(e.getValue().country(), k -> new ArrayList<>()).add(new int[]{e.getKey().x, e.getKey().z});
        }
        for (Map.Entry<String, List<int[]>> e : byCountry.entrySet()) {
            int color = Country.mapColor(e.getKey());
            for (ChunkOutline.Polygon polygon : ChunkOutline.trace(e.getValue())) {
                ShapeProperties props = new ShapeProperties()
                        .setStrokeColor(color).setStrokeOpacity(0.9f).setStrokeWidth(2f)
                        .setFillColor(color).setFillOpacity(0.25f);
                List<MapPolygon> holes = new ArrayList<>();
                for (List<ChunkOutline.Point> hole : polygon.holes()) holes.add(polygon(hole));
                PolygonOverlay overlay = new PolygonOverlay(Politicsmod.MODID, Level.OVERWORLD, props, polygon(polygon.outer()), holes);
                overlay.setTitle(e.getKey());
                overlay.setOverlayGroupName("Countries");
                try {
                    api.show(overlay);
                } catch (Exception ex) {
                    Politicsmod.LOGGER.warn("JourneyMap refused a border overlay", ex);
                }
            }
        }
    }

    private static MapPolygon polygon(List<ChunkOutline.Point> points) {
        List<BlockPos> list = new ArrayList<>(points.size());
        for (ChunkOutline.Point p : points) list.add(new BlockPos(p.x(), 64, p.z()));
        return new MapPolygon(list);
    }
}
