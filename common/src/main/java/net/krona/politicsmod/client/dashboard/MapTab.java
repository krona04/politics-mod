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
 * Territory map: own land, cities, allies, enemies and neutral countries around the player, and map modes for
 * income, stability, unrest, cores and settlements. Click chunks to select them, drag to move the map, scroll to zoom.
 */
public class MapTab extends DashboardTab {
    private static final int MIN_SCALE = 4, MAX_SCALE = 20;
    /** Land the current mode says nothing about. */
    private static final int MAP_EMPTY = 0xFF3A4048;

    /** What the colours show. Realm modes need the realm layer, Settlements needs MineColonies or Millénaire. */
    private enum Mode {
        RELATIONS, POLITICAL, INCOME, STABILITY, UNREST, CORES, SETTLEMENTS;

        String key() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }

        boolean available(CountryDetailsPayload d) {
            if (d == null) return this == RELATIONS;
            return switch (this) {
                case STABILITY, UNREST, CORES -> d.realm().enabled();
                case SETTLEMENTS -> d.settlementsEnabled();
                default -> true;
            };
        }
    }

    /** Kept while the game runs, so the map reopens in the mode the player left it in. */
    private static Mode mode = Mode.RELATIONS;

    /** Per-frame lookups for the current mode. */
    private record ModeData(Map<Long, Integer> income, int maxIncome, Map<Long, Integer> nonCore, Set<Long> lostCores,
                            Map<String, Integer> stability, Map<String, Integer> unrest) {
    }

    private int scale = 10;
    private double offsetX = 0, offsetZ = 0;
    private final Set<ChunkPos> selected = new HashSet<>();
    private int cityIndex = 0;

    private boolean pressedOnMap = false;
    private boolean dragging = false;
    private double dragDistance = 0;

    private int mapX, mapY, mapSize, sideX, sideW;
    private FlatButton btnCity, btnAssign, btnClaim, btnCapital, btnProtection, btnClear, btnMode, btnDevelop;
    /** Cost of the selection last shown in the claim tooltip, to rebuild the tooltip only when it changes. */
    private int shownCost = -1;
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

        add(new FlatButton(mapX + mapSize - 17, mapY + 2, 15, 13, Component.literal("⌖"), () -> {
            offsetX = 0;
            offsetZ = 0;
        }).tooltip(Component.translatable("gui.politicsmod.map.center")));

        btnMode = add(new FlatButton(sideX, y, sideW, 13, Component.empty(), this::nextMode));

        int by = y + h - BTN_H;
        btnProtection = add(new FlatButton(sideX, by, sideW, BTN_H, Component.translatable("gui.politicsmod.protection.button"),
                () -> Minecraft.getInstance().setScreen(new ProtectionScreen(screen, cityNames()))));
        by -= BTN_STEP;
        btnClaim = add(new FlatButton(sideX, by, sideW, BTN_H, Component.translatable("gui.politicsmod.map.claim_country"),
                FlatButton.Style.PRIMARY, this::claimForCountry)
                .tooltip(Component.translatable("gui.politicsmod.map.claim_country_hint")));
        by -= BTN_STEP;
        btnCapital = add(new FlatButton(sideX, by, sideW, BTN_H, Component.translatable("gui.politicsmod.dashboard.make_capital"),
                this::makeCapital));
        by -= BTN_STEP;
        btnDevelop = add(new FlatButton(sideX, by, sideW, BTN_H, Component.empty(), this::developCity));
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

    /** The mode button and legend take 60 px and the hover card 34 px; the "selected" line comes right after. */
    private int selectedLineY() {
        return y + 60 + 36;
    }

    private void nextMode() {
        CountryDetailsPayload d = details();
        Mode[] all = Mode.values();
        Mode next = mode;
        for (int i = 0; i < all.length; i++) {
            next = all[(next.ordinal() + 1) % all.length];
            if (next.available(d)) break;
        }
        mode = next;
    }

    private CountryDetailsPayload.City targetCityInfo() {
        CountryDetailsPayload d = details();
        String city = targetCity();
        if (d == null || city == null) return null;
        for (CountryDetailsPayload.City c : d.cities()) if (c.name().equals(city)) return c;
        return null;
    }

    private void developCity() {
        String city = targetCity();
        if (city != null) screen.runCommand("politicsmod city develop " + city);
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
        updateClaimCost();
        btnCapital.visible = screen.isLeader();
        btnCapital.active = city != null;
        btnProtection.visible = screen.isLeader() || "MAYOR".equals(screen.playerRole());
        btnClear.visible = !selected.isEmpty();

        CountryDetailsPayload d = details();
        if (!mode.available(d)) mode = Mode.RELATIONS;
        btnMode.setMessage(Component.translatable("gui.politicsmod.map.mode",
                Component.translatable("gui.politicsmod.map.mode." + mode.key())));
        btnMode.tooltip(Component.translatable("gui.politicsmod.map.mode_hint"));
        updateDevelop(d, city);
    }

    /** The leader, or the mayor of the city, buys its next development level. */
    private void updateDevelop(CountryDetailsPayload d, String city) {
        CountryDetailsPayload.City info = targetCityInfo();
        boolean mayorHere = city != null && "MAYOR".equals(screen.playerRole()) && d != null
                && info != null && net.minecraft.client.Minecraft.getInstance().player != null
                && info.mayor().equals(net.minecraft.client.Minecraft.getInstance().player.getGameProfile().getName());
        btnDevelop.visible = d != null && d.realm().enabled() && info != null && (screen.isLeader() || mayorHere);
        if (!btnDevelop.visible) return;
        int cost = info.nextDevelopmentCost();
        btnDevelop.active = cost >= 0;
        btnDevelop.setMessage(cost >= 0 ? Component.translatable("gui.politicsmod.map.develop", cost)
                : Component.translatable("gui.politicsmod.map.develop_max"));
        btnDevelop.tooltip(Component.translatable("gui.politicsmod.map.develop_hint", city, info.development(),
                d.realm().developmentTaxPercent(), mayorHere && !screen.isLeader()
                        ? Component.translatable("gui.politicsmod.map.develop_city_pays")
                        : Component.translatable("gui.politicsmod.map.develop_nation_pays")));
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
        // In the order the cost was estimated in, so the server charges what the tooltip said
        for (ChunkPos p : claimOrder(new ArrayList<>())) {
            ModNetworking.toServer(new ClaimLandPayload(p.getWorldPosition(), screen.countryName(), "", false));
        }
        selected.clear();
        screen.refreshSoon();
    }

    /**
     * Free selected chunks in buying order: chunks that touch the country's land (or chunks bought before them)
     * come first, so a connected selection is bought without outposts. Fills {@code touching} alongside.
     */
    private List<ChunkPos> claimOrder(List<Boolean> touching) {
        List<ChunkPos> left = new ArrayList<>();
        for (ChunkPos p : selected) if (ClientPoliticsData.getChunkInfo(p) == null) left.add(p);
        left.sort((a, b) -> a.x != b.x ? Integer.compare(a.x, b.x) : Integer.compare(a.z, b.z));
        Set<ChunkPos> bought = new HashSet<>();
        List<ChunkPos> order = new ArrayList<>();
        while (!left.isEmpty()) {
            int pick = -1;
            for (int i = 0; i < left.size() && pick < 0; i++) if (touchesOwn(left.get(i), bought)) pick = i;
            touching.add(pick >= 0);
            ChunkPos p = left.remove(Math.max(pick, 0));
            bought.add(p);
            order.add(p);
        }
        return order;
    }

    private boolean touchesOwn(ChunkPos p, Set<ChunkPos> bought) {
        for (ChunkPos n : new ChunkPos[]{new ChunkPos(p.x + 1, p.z), new ChunkPos(p.x - 1, p.z),
                new ChunkPos(p.x, p.z + 1), new ChunkPos(p.x, p.z - 1)}) {
            if (bought.contains(n)) return true;
            ClientPoliticsData.ChunkInfo info = ClientPoliticsData.getChunkInfo(n);
            if (info != null && info.country().equals(screen.countryName())) return true;
        }
        return false;
    }

    /** Shows what the selected free chunks will cost, with the same rules the server uses. */
    private void updateClaimCost() {
        CountryDetailsPayload d = details();
        List<Boolean> touching = new ArrayList<>();
        List<ChunkPos> order = d == null ? List.of() : claimOrder(touching);
        int cost = 0, outposts = 0;
        for (int i = 0; i < order.size(); i++) {
            cost += d.landPrices().price(d.economy().chunks() + i, touching.get(i));
            if (!touching.get(i)) outposts++;
        }
        int key = order.isEmpty() ? 0 : cost * 31 + outposts + 1;
        if (key == shownCost) return;
        shownCost = key;
        Component tip = Component.translatable("gui.politicsmod.map.claim_country_hint");
        if (!order.isEmpty()) {
            tip = tip.copy().append("\n").append(Component.translatable("gui.politicsmod.map.claim_cost", cost));
            if (outposts > 0) {
                tip = tip.copy().append("\n").append(Component.translatable("gui.politicsmod.map.claim_outposts",
                        outposts, d.landPrices().outpostMultiplier()));
            }
        }
        btnClaim.tooltip(tip);
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

    private CountryDetailsPayload.SettlementInfo settlementAt(ChunkPos pos) {
        CountryDetailsPayload d = details();
        if (d == null) return null;
        for (CountryDetailsPayload.SettlementInfo s : d.settlements()) {
            if (s.chunkX() == pos.x && s.chunkZ() == pos.z) return s;
        }
        return null;
    }

    private Map<String, String> relations() {
        Map<String, String> map = new HashMap<>();
        CountryDetailsPayload d = details();
        if (d != null) for (CountryDetailsPayload.Relation r : d.relations()) map.put(r.country(), r.status());
        return map;
    }

    private ModeData modeData() {
        CountryDetailsPayload d = details();
        Map<Long, Integer> income = new HashMap<>();
        Map<Long, Integer> nonCore = new HashMap<>();
        Set<Long> lost = new HashSet<>();
        Map<String, Integer> stability = new HashMap<>();
        Map<String, Integer> unrest = new HashMap<>();
        int max = 1;
        if (d != null) {
            for (CountryDetailsPayload.ChunkValue v : d.chunkIncome()) {
                income.put(v.pos(), v.value());
                max = Math.max(max, v.value());
            }
            for (CountryDetailsPayload.ChunkValue v : d.nonCore()) nonCore.put(v.pos(), v.value());
            lost.addAll(d.lostCores());
            for (CountryDetailsPayload.LedgerRow r : d.ledger()) stability.put(r.country(), r.stability());
            for (CountryDetailsPayload.City c : d.cities()) unrest.put(c.name(), c.unrestValue());
        }
        return new ModeData(income, max, nonCore, lost, stability, unrest);
    }

    /** Fill colour of a claimed chunk in the current mode; the alpha says how strongly it is shown. */
    private int fillOf(ChunkPos p, ClientPoliticsData.ChunkInfo info, Map<String, String> relations, ModeData md) {
        boolean own = info.country().equals(screen.countryName());
        int faint = (UiKit.MAP_NEUTRAL & 0xFFFFFF) | 0x30000000;
        return switch (mode) {
            case RELATIONS -> (colorOf(info, relations) & 0xFFFFFF) | 0x88000000;
            case POLITICAL -> net.krona.politicsmod.politics.Country.mapColor(info.country()) | 0x99000000;
            case INCOME -> {
                if (!own) yield faint;
                Integer income = md.income().get(p.toLong());
                if (income == null || income == 0) yield (MAP_EMPTY & 0xFFFFFF) | 0x99000000;
                yield (blend(0xFF2E5E3A, 0xFF7CF08F, income / (float) md.maxIncome()) & 0xFFFFFF) | 0xAA000000;
            }
            case STABILITY -> {
                Integer level = md.stability().get(info.country());
                yield level == null ? faint : (UiKit.stability(level) & 0xFFFFFF) | 0x99000000;
            }
            case UNREST -> {
                if (!own) yield faint;
                Integer unrest = info.city().isEmpty() ? null : md.unrest().get(info.city());
                if (unrest == null) yield (MAP_EMPTY & 0xFFFFFF) | 0x99000000;
                CountryDetailsPayload d = details();
                int secede = d == null ? 75 : Math.max(1, d.realm().unrestSecede());
                yield (UiKit.heat(unrest / (float) secede) & 0xFFFFFF) | 0xAA000000;
            }
            case CORES -> {
                if (!own) yield faint;
                yield md.nonCore().containsKey(p.toLong()) ? (UiKit.WARN & 0xFFFFFF) | 0x99000000 : (UiKit.MAP_OWN & 0xFFFFFF) | 0x88000000;
            }
            case SETTLEMENTS -> faint;
        };
    }

    private static int blend(int from, int to, float t) {
        t = Math.max(0, Math.min(1, t));
        int r = (int) (((from >> 16) & 0xFF) + (((to >> 16) & 0xFF) - ((from >> 16) & 0xFF)) * t);
        int g = (int) (((from >> 8) & 0xFF) + (((to >> 8) & 0xFF) - ((from >> 8) & 0xFF)) * t);
        int b = (int) ((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
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
        ModeData md = modeData();
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
                int fill = fillOf(p, info, relations, md);
                g.fill(sx, sz, sx + scale, sz + scale, fill);
                // Solid edges where the owner or city changes
                int edge = (mode == Mode.RELATIONS ? colorOf(info, relations) : fill) | 0xFF000000;
                if (!sameArea(info, ClientPoliticsData.getChunkInfo(new ChunkPos(ix, iz - 1)))) g.fill(sx, sz, sx + scale, sz + 1, edge);
                if (!sameArea(info, ClientPoliticsData.getChunkInfo(new ChunkPos(ix, iz + 1)))) g.fill(sx, sz + scale - 1, sx + scale, sz + scale, edge);
                if (!sameArea(info, ClientPoliticsData.getChunkInfo(new ChunkPos(ix - 1, iz)))) g.fill(sx, sz, sx + 1, sz + scale, edge);
                if (!sameArea(info, ClientPoliticsData.getChunkInfo(new ChunkPos(ix + 1, iz)))) g.fill(sx + scale - 1, sz, sx + scale, sz + scale, edge);
            }
        }
        // Lost cores: a red frame, whoever holds them now
        if (mode == Mode.CORES) {
            for (long l : md.lostCores()) {
                ChunkPos p = new ChunkPos(l);
                int sx = cellX(p, center), sz = cellZ(p, center);
                g.renderOutline(sx + 1, sz + 1, scale - 2, scale - 2, UiKit.BAD);
            }
        }
        for (ChunkPos p : selected) {
            int sx = cellX(p, center), sz = cellZ(p, center);
            g.fill(sx + 1, sz + 1, sx + scale - 1, sz + scale - 1, 0x66FFFFFF);
            g.renderOutline(sx, sz, scale, scale, 0xFFFFFFFF);
        }

        // Colonies and villages: a marker on the chunk with their center
        CountryDetailsPayload d = details();
        if (d != null) {
            for (CountryDetailsPayload.SettlementInfo s : d.settlements()) {
                ChunkPos p = new ChunkPos(s.chunkX(), s.chunkZ());
                int mxc = cellX(p, center) + scale / 2, mzc = cellZ(p, center) + scale / 2;
                int color = s.statusEnum().belongs() ? UiKit.ACCENT : UiKit.WARN;
                int r = mode == Mode.SETTLEMENTS ? 5 : 3;
                g.fill(mxc - r, mzc - r, mxc + r + 1, mzc + r + 1, 0xFF000000);
                g.fill(mxc - r + 1, mzc - r + 1, mxc + r, mzc + r, color);
            }
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

        renderSide(g, relations, md, hovered);
    }

    private void renderSide(GuiGraphics g, Map<String, String> relations, ModeData md, ChunkPos hovered) {
        int ly = y + 15;
        switch (mode) {
            case RELATIONS -> {
                legend(g, ly, UiKit.MAP_OWN, "gui.politicsmod.map.legend_own");
                legend(g, ly + 9, UiKit.ACCENT, "gui.politicsmod.map.legend_city");
                legend(g, ly + 18, UiKit.MAP_ALLY, "gui.politicsmod.map.legend_ally");
                legend(g, ly + 27, UiKit.MAP_ENEMY, "gui.politicsmod.map.legend_enemy");
                legend(g, ly + 36, UiKit.MAP_NEUTRAL, "gui.politicsmod.map.legend_neutral");
            }
            case POLITICAL -> {
                legend(g, ly, net.krona.politicsmod.politics.Country.mapColor(screen.countryName()) | 0xFF000000,
                        "gui.politicsmod.map.legend_own");
                UiKit.wrap(g, font, Component.translatable("gui.politicsmod.map.legend_political"), sideX, ly + 10, sideW, 3, UiKit.TEXT_MUTED);
            }
            case INCOME -> {
                legend(g, ly, 0xFF7CF08F, "gui.politicsmod.map.legend_income_high");
                legend(g, ly + 9, 0xFF2E5E3A, "gui.politicsmod.map.legend_income_low");
                legend(g, ly + 18, MAP_EMPTY, "gui.politicsmod.map.legend_income_none");
            }
            case STABILITY -> {
                legend(g, ly, UiKit.stability(3), "gui.politicsmod.map.legend_stability_high");
                legend(g, ly + 9, UiKit.stability(0), "gui.politicsmod.map.legend_stability_zero");
                legend(g, ly + 18, UiKit.stability(-3), "gui.politicsmod.map.legend_stability_low");
            }
            case UNREST -> {
                legend(g, ly, UiKit.heat(0), "gui.politicsmod.map.legend_unrest_calm");
                legend(g, ly + 9, UiKit.heat(0.5f), "gui.politicsmod.map.legend_unrest_some");
                legend(g, ly + 18, UiKit.heat(1), "gui.politicsmod.map.legend_unrest_revolt");
                legend(g, ly + 27, MAP_EMPTY, "gui.politicsmod.map.legend_no_city");
            }
            case CORES -> {
                legend(g, ly, UiKit.MAP_OWN, "gui.politicsmod.map.legend_core");
                legend(g, ly + 9, UiKit.WARN, "gui.politicsmod.map.legend_non_core");
                legend(g, ly + 18, UiKit.BAD, "gui.politicsmod.map.legend_lost_core");
            }
            case SETTLEMENTS -> {
                legend(g, ly, UiKit.ACCENT, "gui.politicsmod.map.legend_settlement_own");
                legend(g, ly + 9, UiKit.WARN, "gui.politicsmod.map.legend_settlement_annexable");
            }
        }
        ly = y + 60;

        UiKit.card(g, sideX, ly, sideW, 34);
        if (hovered == null) {
            // Hints wrap inside the card; two short lines in most languages
            Component hint = Component.translatable("gui.politicsmod.map.hover_hint").append(" ")
                    .append(Component.translatable("gui.politicsmod.map.zoom_hint"));
            UiKit.wrap(g, font, hint, sideX + 4, ly + 2, sideW - 8, 3, UiKit.TEXT_MUTED);
        } else {
            ClientPoliticsData.ChunkInfo info = ClientPoliticsData.getChunkInfo(hovered);
            UiKit.text(g, font, Component.translatable("gui.politicsmod.map.chunk", hovered.x, hovered.z), sideX + 4, ly + 2, sideW - 8, UiKit.TEXT_DIM);
            Component modeInfo = modeInfo(hovered, info, md);
            if (info == null) {
                UiKit.text(g, font, Component.translatable("gui.politicsmod.hud.wilderness"), sideX + 4, ly + 12, sideW - 8, UiKit.TEXT);
            } else {
                UiKit.text(g, font, Component.literal(info.country()), sideX + 4, ly + 12, sideW - 8, colorOf(info, relations));
            }
            if (modeInfo != null) {
                UiKit.text(g, font, modeInfo, sideX + 4, ly + 23, sideW - 8, UiKit.ACCENT);
            } else if (info != null) {
                Component sub = info.city().isEmpty() ? Component.translatable("gui.politicsmod.map.country")
                        : Component.translatable("gui.politicsmod.map.city_named", info.city());
                UiKit.text(g, font, sub, sideX + 4, ly + 23, sideW - 8, UiKit.TEXT_DIM);
            }
            // A colony or village centered here: its name over the last line
            CountryDetailsPayload.SettlementInfo s = settlementAt(hovered);
            if (s != null) {
                g.fill(sideX + 1, ly + 22, sideX + sideW - 1, ly + 33, UiKit.CARD);
                UiKit.text(g, font, Component.literal("⌂ " + s.name()), sideX + 4, ly + 23, sideW - 8, UiKit.ACCENT);
            }
        }
        UiKit.text(g, font, Component.translatable("gui.politicsmod.map.selected", selected.size()), sideX, selectedLineY(),
                sideW - 16, UiKit.TEXT_DIM);
    }

    /** What the current mode says about the hovered chunk, or null to show its city. */
    private Component modeInfo(ChunkPos p, ClientPoliticsData.ChunkInfo info, ModeData md) {
        boolean own = info != null && info.country().equals(screen.countryName());
        switch (mode) {
            case INCOME -> {
                if (own) return Component.translatable("gui.politicsmod.map.info_income", md.income().getOrDefault(p.toLong(), 0));
            }
            case STABILITY -> {
                Integer level = info == null ? null : md.stability().get(info.country());
                if (level != null) return Component.translatable("gui.politicsmod.map.info_stability", UiKit.signed(level));
            }
            case UNREST -> {
                Integer unrest = own && !info.city().isEmpty() ? md.unrest().get(info.city()) : null;
                if (unrest != null) return Component.translatable("gui.politicsmod.map.info_unrest", unrest);
            }
            case CORES -> {
                if (md.lostCores().contains(p.toLong())) return Component.translatable("gui.politicsmod.map.info_lost_core");
                if (own) {
                    Integer left = md.nonCore().get(p.toLong());
                    return left == null ? Component.translatable("gui.politicsmod.map.info_core")
                            : Component.translatable("gui.politicsmod.map.info_non_core", left);
                }
            }
            default -> {
            }
        }
        return null;
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
