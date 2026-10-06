package net.krona.politicsmod.client.dashboard;

import net.krona.politicsmod.client.ClientPoliticsData;
import net.krona.politicsmod.client.CountryDashboardScreen;
import net.krona.politicsmod.client.ProtectionScreen;
import net.krona.politicsmod.client.ui.FlatButton;
import net.krona.politicsmod.client.ui.UiKit;
import net.krona.politicsmod.network.ClaimLandPayload;
import net.krona.politicsmod.network.CountryDetailsPayload;
import net.krona.politicsmod.network.ManagePoliticsPayload;
import net.krona.politicsmod.network.ModNetworking;
import net.krona.politicsmod.politics.RoleAction;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.ChunkPos;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Territory map: own land, cities, allies, enemies and neutral countries around the player.
 * Click chunks to select them, drag to move the map, scroll to zoom.
 */
public class MapTab extends DashboardTab {
    private static final int MIN_SCALE = 4, MAX_SCALE = 20;

    private int scale = 10;
    private double offsetX = 0, offsetZ = 0;
    private final Set<ChunkPos> selected = new HashSet<>();
    private int cityIndex = 0;

    private boolean pressedOnMap = false;
    private boolean dragging = false;
    private double dragDistance = 0;

    private int mapX, mapY, mapSize, sideX, sideW;
    private FlatButton btnCity, btnAssign, btnClaim, btnCapital, btnProtection, btnClear;
    private static final int BTN_H = 15, BTN_STEP = 17;

    public MapTab(CountryDashboardScreen screen) {
        super(screen);
    }

    @Override
    public Component title() {
        return Component.translatable("gui.politicsmod.dashboard.tab.map");
    }

    @Override
    protected void addWidgets() {
        mapSize = Math.min(h, w - 116);
        mapX = x;
        mapY = y;
        sideX = x + mapSize + 6;
        sideW = w - mapSize - 6;

        add(new FlatButton(mapX + mapSize - 15, mapY + 2, 13, 13, Component.literal("⌖"), () -> {
            offsetX = 0;
            offsetZ = 0;
        }).tooltip(Component.translatable("gui.politicsmod.map.center")));

        int by = y + h - BTN_H;
        btnProtection = add(new FlatButton(sideX, by, sideW, BTN_H, Component.translatable("gui.politicsmod.protection.button"),
                () -> Minecraft.getInstance().setScreen(new ProtectionScreen(screen, cityNames()))));
        by -= BTN_STEP;
        btnCapital = add(new FlatButton(sideX, by, sideW, BTN_H, Component.translatable("gui.politicsmod.dashboard.make_capital"),
                this::makeCapital));
        by -= BTN_STEP;
        btnClaim = add(new FlatButton(sideX, by, sideW, BTN_H, Component.translatable("gui.politicsmod.map.claim_country"),
                FlatButton.Style.PRIMARY, this::claimForCountry)
                .tooltip(Component.translatable("gui.politicsmod.map.claim_country_hint")));
        by -= BTN_STEP;
        btnAssign = add(new FlatButton(sideX, by, sideW, BTN_H, Component.translatable("gui.politicsmod.dashboard.assign"),
                FlatButton.Style.PRIMARY, this::assignToCity)
                .tooltip(Component.translatable("gui.politicsmod.map.assign_hint")));
        by -= BTN_STEP;
        btnCity = add(new FlatButton(sideX, by, sideW, BTN_H, Component.empty(), () -> {
            List<String> cities = cityNames();
            if (!cities.isEmpty()) cityIndex = (cityIndex + 1) % cities.size();
        }).tooltip(Component.translatable("gui.politicsmod.politics.next_city")));
        btnClear = add(new FlatButton(sideX + sideW - 12, selectedLineY() - 2, 12, 12, Component.literal("✕"), selected::clear)
                .tooltip(Component.translatable("gui.politicsmod.map.clear")));
    }

    /** The legend takes 56 px and the hover card 36 px; the "selected" line comes right after. */
    private int selectedLineY() {
        return y + 56 + 38;
    }

    private List<String> cityNames() {
        CountryDetailsPayload d = details();
        if (d == null) return screen.cities();
        List<String> names = new ArrayList<>();
        for (CountryDetailsPayload.City c : d.cities()) names.add(c.name());
        return names;
    }

    private String targetCity() {
        List<String> cities = cityNames();
        if (cities.isEmpty()) return null;
        if (cityIndex >= cities.size()) cityIndex = 0;
        return cities.get(cityIndex);
    }

    @Override
    public void update() {
        String city = targetCity();
        btnCity.setMessage(city == null ? Component.translatable("gui.politicsmod.dashboard.no_cities")
                : Component.translatable("gui.politicsmod.dashboard.target", city));
        btnCity.active = city != null;

        int own = 0, free = 0;
        for (ChunkPos p : selected) {
            ClientPoliticsData.ChunkInfo info = ClientPoliticsData.getChunkInfo(p);
            if (info == null) free++;
            else if (info.country().equals(screen.countryName()) && info.city().isEmpty()) own++;
        }
        boolean cityRights = screen.can(RoleAction.CLAIM_CITY);
        btnCity.visible = cityRights;
        btnAssign.visible = cityRights;
        btnAssign.active = city != null && own > 0;
        btnAssign.setMessage(Component.translatable("gui.politicsmod.map.assign_n", own));
        btnClaim.visible = screen.isLeader();
        btnClaim.active = free > 0;
        btnClaim.setMessage(Component.translatable("gui.politicsmod.map.claim_country_n", free));
        btnCapital.visible = screen.isLeader();
        btnCapital.active = city != null;
        btnProtection.visible = screen.isLeader() || "MAYOR".equals(screen.playerRole());
        btnClear.visible = !selected.isEmpty();
    }

    // ── Actions ──────────────────────────────────────────────────────────

    private void assignToCity() {
        String city = targetCity();
        if (city == null) return;
        for (ChunkPos p : selected) {
            ClientPoliticsData.ChunkInfo info = ClientPoliticsData.getChunkInfo(p);
            if (info != null && info.country().equals(screen.countryName()) && info.city().isEmpty()) {
                ModNetworking.toServer(new ClaimLandPayload(p.getWorldPosition(), screen.countryName(), city, true));
            }
        }
        selected.clear();
        screen.refreshSoon();
    }

    private void claimForCountry() {
        for (ChunkPos p : selected) {
            if (ClientPoliticsData.getChunkInfo(p) == null) {
                ModNetworking.toServer(new ClaimLandPayload(p.getWorldPosition(), screen.countryName(), "", false));
            }
        }
        selected.clear();
        screen.refreshSoon();
    }

    private void makeCapital() {
        String city = targetCity();
        if (city == null) return;
        ModNetworking.toServer(new ManagePoliticsPayload(0, city, ""));
        screen.requestDetails();
    }

    // ── Geometry ─────────────────────────────────────────────────────────

    private ChunkPos center() {
        var player = Minecraft.getInstance().player;
        return player == null ? new ChunkPos(0, 0) : player.chunkPosition();
    }

    private ChunkPos chunkAt(double mx, double my) {
        ChunkPos c = center();
        double dx = (mx - (mapX + mapSize / 2.0)) / scale - offsetX;
        double dz = (my - (mapY + mapSize / 2.0)) / scale - offsetZ;
        return new ChunkPos(c.x + (int) Math.floor(dx + 0.5), c.z + (int) Math.floor(dz + 0.5));
    }

    /** Screen position of the top-left corner of a chunk cell. */
    private int cellX(ChunkPos c, ChunkPos center) {
        return (int) Math.floor(mapX + mapSize / 2.0 + (c.x - center.x + offsetX - 0.5) * scale);
    }

    private int cellZ(ChunkPos c, ChunkPos center) {
        return (int) Math.floor(mapY + mapSize / 2.0 + (c.z - center.z + offsetZ - 0.5) * scale);
    }

    private Map<String, String> relations() {
        Map<String, String> map = new HashMap<>();
        CountryDetailsPayload d = details();
        if (d != null) for (CountryDetailsPayload.Relation r : d.relations()) map.put(r.country(), r.status());
        return map;
    }

    private int colorOf(ClientPoliticsData.ChunkInfo info, Map<String, String> relations) {
        if (info.country().equals(screen.countryName())) return info.city().isEmpty() ? UiKit.MAP_OWN : UiKit.ACCENT;
        return switch (relations.getOrDefault(info.country(), "NEUTRAL")) {
            case "WAR" -> UiKit.MAP_ENEMY;
            case "ALLIANCE" -> UiKit.MAP_ALLY;
            default -> UiKit.MAP_NEUTRAL;
        };
    }

    private static boolean sameArea(ClientPoliticsData.ChunkInfo a, ClientPoliticsData.ChunkInfo b) {
        if (a == null || b == null) return a == b;
        return a.country().equals(b.country()) && a.city().equals(b.city());
    }

    // ── Rendering ────────────────────────────────────────────────────────

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        Map<String, String> relations = relations();
        g.fill(mapX, mapY, mapX + mapSize, mapY + mapSize, UiKit.MAP_BG);
        g.enableScissor(mapX, mapY, mapX + mapSize, mapY + mapSize);

        ChunkPos center = center();
        int radius = mapSize / scale / 2 + 2;
        int cx = center.x - (int) Math.round(offsetX), cz = center.z - (int) Math.round(offsetZ);
        for (int ix = cx - radius; ix <= cx + radius; ix++) {
            for (int iz = cz - radius; iz <= cz + radius; iz++) {
                ChunkPos p = new ChunkPos(ix, iz);
                int sx = cellX(p, center), sz = cellZ(p, center);
                if (scale >= 8) g.renderOutline(sx, sz, scale + 1, scale + 1, UiKit.MAP_GRID);
                ClientPoliticsData.ChunkInfo info = ClientPoliticsData.getChunkInfo(p);
                if (info == null) continue;
                int color = colorOf(info, relations);
                g.fill(sx, sz, sx + scale, sz + scale, (color & 0xFFFFFF) | 0x88000000);
                // Solid edges where the owner or city changes
                int edge = color | 0xFF000000;
                if (!sameArea(info, ClientPoliticsData.getChunkInfo(new ChunkPos(ix, iz - 1)))) g.fill(sx, sz, sx + scale, sz + 1, edge);
                if (!sameArea(info, ClientPoliticsData.getChunkInfo(new ChunkPos(ix, iz + 1)))) g.fill(sx, sz + scale - 1, sx + scale, sz + scale, edge);
                if (!sameArea(info, ClientPoliticsData.getChunkInfo(new ChunkPos(ix - 1, iz)))) g.fill(sx, sz, sx + 1, sz + scale, edge);
                if (!sameArea(info, ClientPoliticsData.getChunkInfo(new ChunkPos(ix + 1, iz)))) g.fill(sx + scale - 1, sz, sx + scale, sz + scale, edge);
            }
        }
        for (ChunkPos p : selected) {
            int sx = cellX(p, center), sz = cellZ(p, center);
            g.fill(sx + 1, sz + 1, sx + scale - 1, sz + scale - 1, 0x66FFFFFF);
            g.renderOutline(sx, sz, scale, scale, 0xFFFFFFFF);
        }

        // Player position inside their chunk
        var player = Minecraft.getInstance().player;
        if (player != null) {
            double fx = player.getX() / 16.0 - center.x - 0.5 + offsetX;
            double fz = player.getZ() / 16.0 - center.z - 0.5 + offsetZ;
            int px = (int) (mapX + mapSize / 2.0 + fx * scale);
            int pz = (int) (mapY + mapSize / 2.0 + fz * scale);
            g.fill(px - 3, pz - 3, px + 4, pz + 4, 0xFF000000);
            g.fill(px - 2, pz - 2, px + 3, pz + 3, 0xFFFFFFFF);
        }

        // Hovered cell
        boolean hoverMap = UiKit.inside(mx, my, mapX, mapY, mapSize, mapSize);
        ChunkPos hovered = hoverMap ? chunkAt(mx, my) : null;
        if (hovered != null) {
            int sx = cellX(hovered, center), sz = cellZ(hovered, center);
            g.renderOutline(sx, sz, scale, scale, 0xAAFFFFFF);
        }
        g.disableScissor();
        g.renderOutline(mapX - 1, mapY - 1, mapSize + 2, mapSize + 2, UiKit.CARD_BORDER);

        renderSide(g, relations, hovered);
    }

    private void renderSide(GuiGraphics g, Map<String, String> relations, ChunkPos hovered) {
        int ly = y;
        UiKit.section(g, font, Component.translatable("gui.politicsmod.map.legend"), sideX, ly);
        ly += 11;
        legend(g, ly, UiKit.MAP_OWN, "gui.politicsmod.map.legend_own");
        legend(g, ly + 9, UiKit.ACCENT, "gui.politicsmod.map.legend_city");
        legend(g, ly + 18, UiKit.MAP_ALLY, "gui.politicsmod.map.legend_ally");
        legend(g, ly + 27, UiKit.MAP_ENEMY, "gui.politicsmod.map.legend_enemy");
        legend(g, ly + 36, UiKit.MAP_NEUTRAL, "gui.politicsmod.map.legend_neutral");
        ly = y + 56;

        UiKit.card(g, sideX, ly, sideW, 36);
        if (hovered == null) {
            // Hints wrap inside the card; two short lines in most languages
            Component hint = Component.translatable("gui.politicsmod.map.hover_hint").append(" ")
                    .append(Component.translatable("gui.politicsmod.map.zoom_hint"));
            UiKit.wrap(g, font, hint, sideX + 4, ly + 3, sideW - 8, 3, UiKit.TEXT_MUTED);
        } else {
            ClientPoliticsData.ChunkInfo info = ClientPoliticsData.getChunkInfo(hovered);
            UiKit.text(g, font, Component.translatable("gui.politicsmod.map.chunk", hovered.x, hovered.z), sideX + 4, ly + 3, sideW - 8, UiKit.TEXT_DIM);
            if (info == null) {
                UiKit.text(g, font, Component.translatable("gui.politicsmod.hud.wilderness"), sideX + 4, ly + 14, sideW - 8, UiKit.TEXT);
            } else {
                UiKit.text(g, font, Component.literal(info.country()), sideX + 4, ly + 14, sideW - 8, colorOf(info, relations));
                Component sub = info.city().isEmpty() ? Component.translatable("gui.politicsmod.map.country")
                        : Component.translatable("gui.politicsmod.map.city_named", info.city());
                UiKit.text(g, font, sub, sideX + 4, ly + 25, sideW - 8, UiKit.TEXT_DIM);
            }
        }
        UiKit.text(g, font, Component.translatable("gui.politicsmod.map.selected", selected.size()), sideX, selectedLineY(),
                sideW - 16, UiKit.TEXT_DIM);
    }

    private void legend(GuiGraphics g, int ly, int color, String key) {
        g.fill(sideX, ly, sideX + 7, ly + 7, color);
        UiKit.text(g, font, Component.translatable(key), sideX + 11, ly, sideW - 11, UiKit.TEXT_DIM);
    }

    // ── Input ────────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0 || !UiKit.inside(mx, my, mapX, mapY, mapSize, mapSize)) return false;
        pressedOnMap = true;
        dragging = false;
        dragDistance = 0;
        return true;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (!pressedOnMap) return false;
        dragDistance += Math.abs(dx) + Math.abs(dy);
        if (dragDistance > 3) dragging = true;
        if (dragging) {
            offsetX += dx / scale;
            offsetZ += dy / scale;
        }
        return true;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (!pressedOnMap) return false;
        pressedOnMap = false;
        if (!dragging && UiKit.inside(mx, my, mapX, mapY, mapSize, mapSize)) {
            ChunkPos c = chunkAt(mx, my);
            if (!selected.remove(c)) selected.add(c);
        }
        dragging = false;
        return true;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (!UiKit.inside(mx, my, mapX, mapY, mapSize, mapSize)) return false;
        scale = Math.max(MIN_SCALE, Math.min(MAX_SCALE, scale + (delta > 0 ? 2 : -2)));
        return true;
    }
}
