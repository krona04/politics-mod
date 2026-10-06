package net.krona.politicsmod.integration.webmap;

import com.flowpowered.math.vector.Vector2d;
import de.bluecolored.bluemap.api.BlueMapAPI;
import de.bluecolored.bluemap.api.BlueMapMap;
import de.bluecolored.bluemap.api.markers.MarkerSet;
import de.bluecolored.bluemap.api.markers.ShapeMarker;
import de.bluecolored.bluemap.api.math.Color;
import de.bluecolored.bluemap.api.math.Shape;
import net.krona.politicsmod.integration.ChunkOutline;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Countries as coloured areas in a "Countries" marker set on every overworld map. Loaded only with BlueMap. */
final class BlueMapLayer implements WebMaps.Layer {
    private static final String SET_ID = "politicsmod-countries";
    private static final float HEIGHT = 64f;

    private final MinecraftServer server;
    private final Consumer<BlueMapAPI> onEnable;
    private final Consumer<BlueMapAPI> onDisable;
    private volatile BlueMapAPI api;
    private List<WebMaps.Area> last = List.of();

    BlueMapLayer(MinecraftServer server) {
        this.server = server;
        this.onEnable = a -> {
            api = a;
            WebMaps.forceRedraw = true; // BlueMap starts later than the server; draw once it's there
        };
        this.onDisable = a -> api = null;
        BlueMapAPI.onEnable(onEnable);
        BlueMapAPI.onDisable(onDisable);
    }

    @Override
    public void update(List<WebMaps.Area> areas) {
        last = areas;
        BlueMapAPI a = api;
        if (a == null) return;

        MarkerSet set = MarkerSet.builder().label("Countries").toggleable(true).defaultHidden(false).build();
        for (WebMaps.Area area : areas) {
            int r = area.color() >> 16 & 0xFF, g = area.color() >> 8 & 0xFF, b = area.color() & 0xFF;
            int i = 0;
            for (ChunkOutline.Polygon polygon : area.polygons()) {
                List<Shape> holes = new ArrayList<>();
                for (List<ChunkOutline.Point> hole : polygon.holes()) holes.add(shape(hole));
                ShapeMarker marker = ShapeMarker.builder()
                        .label(area.name())
                        .detail(area.description())
                        .shape(shape(polygon.outer()), HEIGHT)
                        .holes(holes.toArray(new Shape[0]))
                        .lineColor(new Color(r, g, b, 1f))
                        .fillColor(new Color(r, g, b, 0.3f))
                        .lineWidth(2)
                        .depthTestEnabled(false)
                        .build();
                set.put(area.id() + "_" + i++, marker);
            }
        }
        a.getWorld(server.overworld()).ifPresent(world -> {
            for (BlueMapMap map : world.getMaps()) map.getMarkerSets().put(SET_ID, set);
        });
    }

    private static Shape shape(List<ChunkOutline.Point> points) {
        List<Vector2d> list = new ArrayList<>(points.size());
        for (ChunkOutline.Point p : points) list.add(new Vector2d(p.x(), p.z()));
        return new Shape(list);
    }

    @Override
    public void close() {
        BlueMapAPI a = api;
        if (a != null) {
            for (BlueMapMap map : a.getMaps()) map.getMarkerSets().remove(SET_ID);
        }
        BlueMapAPI.unregisterListener(onEnable);
        BlueMapAPI.unregisterListener(onDisable);
    }
}
