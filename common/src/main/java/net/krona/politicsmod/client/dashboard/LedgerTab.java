package net.krona.politicsmod.client.dashboard;

import net.krona.politicsmod.client.CountryDashboardScreen;
import net.krona.politicsmod.client.ui.FlatButton;
import net.krona.politicsmod.client.ui.UiKit;
import net.krona.politicsmod.network.CountryDetailsPayload;
import net.krona.politicsmod.realm.Realm;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * The ledger: every country side by side, and a graph of the last days for the own country and one other.
 * Click a column to sort, click a country to compare it on the graph.
 */
public class LedgerTab extends DashboardTab {
    private static final int ROW = 11, NUM_COL = 40;

    private enum Column {
        COUNTRY, LAND, CITIZENS, DEVELOPMENT, STABILITY, PRESTIGE;

        String key() {
            return "gui.politicsmod.ledger.col." + name().toLowerCase(Locale.ROOT);
        }

        int value(CountryDetailsPayload.LedgerRow r) {
            return switch (this) {
                case COUNTRY -> 0;
                case LAND -> r.chunks();
                case CITIZENS -> r.citizens();
                case DEVELOPMENT -> r.development();
                case STABILITY -> r.stability();
                case PRESTIGE -> r.prestige();
            };
        }
    }

    /** What the graph shows; money only for the own country. */
    private enum Metric {
        LAND(Realm.H_CHUNKS, false), CITIZENS(Realm.H_CITIZENS, false), DEVELOPMENT(Realm.H_DEVELOPMENT, false),
        STABILITY(Realm.H_STABILITY, false), LEGITIMACY(Realm.H_LEGITIMACY, false), PRESTIGE(Realm.H_PRESTIGE, false),
        TREASURY(Realm.H_TREASURY, true), NET(Realm.H_NET, true);

        final int index;
        final boolean ownOnly;

        Metric(int index, boolean ownOnly) {
            this.index = index;
            this.ownOnly = ownOnly;
        }

        String key() {
            return "gui.politicsmod.ledger.metric." + name().toLowerCase(Locale.ROOT);
        }
    }

    private static Column sort = Column.LAND;
    private static boolean descending = true;
    private static Metric metric = Metric.LAND;
    private String compare = null;
    private int scroll = 0;

    private int tableH, chartTop;
    private FlatButton btnMetric;

    public LedgerTab(CountryDashboardScreen screen) {
        super(screen);
    }

    @Override
    public Component title() {
        return Component.translatable("gui.politicsmod.dashboard.tab.ledger");
    }

    @Override
    protected void addWidgets() {
        tableH = Math.max(ROW * 4, (h * 46 / 100) / ROW * ROW + 1);
        chartTop = y + tableH + 6;
        Component label = Component.translatable("gui.politicsmod.ledger.metric", Component.translatable(metric.key()));
        int bw = Math.min(150, w / 2);
        btnMetric = add(new FlatButton(x + w - bw, chartTop - 1, bw, 12, label, () -> {
            metric = Metric.values()[(metric.ordinal() + 1) % Metric.values().length];
        }).tooltip(Component.translatable("gui.politicsmod.ledger.metric_hint")));
    }

    @Override
    public void update() {
        btnMetric.setMessage(Component.translatable("gui.politicsmod.ledger.metric", Component.translatable(metric.key())));
    }

    private List<CountryDetailsPayload.LedgerRow> sortedRows(CountryDetailsPayload d) {
        List<CountryDetailsPayload.LedgerRow> rows = new ArrayList<>(d.ledger());
        Comparator<CountryDetailsPayload.LedgerRow> cmp = sort == Column.COUNTRY
                ? Comparator.comparing(CountryDetailsPayload.LedgerRow::country, String.CASE_INSENSITIVE_ORDER)
                : Comparator.comparingInt(sort::value);
        if (descending) cmp = cmp.reversed();
        rows.sort(cmp.thenComparing(CountryDetailsPayload.LedgerRow::country, String.CASE_INSENSITIVE_ORDER));
        return rows;
    }

    private int nameW() {
        return w - NUM_COL * 5 - 6;
    }

    private int columnX(Column c) {
        return c == Column.COUNTRY ? x + 4 : x + nameW() + NUM_COL * (c.ordinal() - 1);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        CountryDetailsPayload d = details();
        if (d == null) {
            loading(g);
            return;
        }
        renderTable(g, mx, my, d);
        renderChart(g, mx, my, d);
    }

    // ── Table ────────────────────────────────────────────────────────────

    private void renderTable(GuiGraphics g, int mx, int my, CountryDetailsPayload d) {
        UiKit.card(g, x, y, w, tableH);
        // Header: click to sort; the sorted column is underlined, the direction is in its tooltip
        for (Column c : Column.values()) {
            Component label = Component.translatable(c.key());
            int color = c == sort ? UiKit.ACCENT : UiKit.TEXT_DIM;
            int lx, lw;
            if (c == Column.COUNTRY) {
                lw = Math.min(font.width(label), nameW() - 8);
                lx = columnX(c);
                UiKit.text(g, font, label, lx, y + 3, nameW() - 8, color);
            } else {
                var fitted = UiKit.fit(font, label, NUM_COL - 4);
                lw = font.width(fitted);
                lx = columnX(c) + NUM_COL - 4 - lw;
                g.drawString(font, fitted, lx, y + 3, color, false);
            }
            if (c == sort) g.fill(lx, y + 12, lx + lw, y + 13, UiKit.ACCENT);
            hover(c == Column.COUNTRY ? x : columnX(c), y, c == Column.COUNTRY ? nameW() : NUM_COL, ROW + 1,
                    List.of(Component.translatable(c.key()).withStyle(ChatFormatting.GOLD),
                            Component.translatable(c == sort && descending ? "gui.politicsmod.ledger.sorted_desc"
                                    : c == sort ? "gui.politicsmod.ledger.sorted_asc" : "gui.politicsmod.ledger.sort_hint")
                                    .withStyle(ChatFormatting.GRAY)));
        }
        g.fill(x + 1, y + ROW + 1, x + w - 1, y + ROW + 2, UiKit.CARD_BORDER);

        List<CountryDetailsPayload.LedgerRow> rows = sortedRows(d);
        int visible = (tableH - ROW - 4) / ROW;
        scroll = clampScroll(scroll, rows.size(), visible);
        for (int i = 0; i < visible && scroll + i < rows.size(); i++) {
            CountryDetailsPayload.LedgerRow r = rows.get(scroll + i);
            int ry = y + ROW + 3 + i * ROW;
            boolean own = r.country().equals(screen.countryName());
            boolean hovered = UiKit.inside(mx, my, x, ry, w, ROW);
            UiKit.row(g, x + 1, ry - 1, w - 2, ROW, r.country().equals(compare), hovered);
            int text = own ? UiKit.ACCENT : UiKit.TEXT;
            g.fill(columnX(Column.COUNTRY), ry + 2, columnX(Column.COUNTRY) + 5, ry + 7,
                    net.krona.politicsmod.politics.Country.mapColor(r.country()) | 0xFF000000);
            UiKit.text(g, font, Component.literal(r.country()), columnX(Column.COUNTRY) + 8, ry + 1, nameW() - 16, text);
            number(g, Column.LAND, ry, String.valueOf(r.chunks()), text);
            number(g, Column.CITIZENS, ry, String.valueOf(r.citizens()), text);
            number(g, Column.DEVELOPMENT, ry, String.valueOf(r.development()), text);
            number(g, Column.STABILITY, ry, UiKit.signed(r.stability()), UiKit.stability(r.stability()));
            number(g, Column.PRESTIGE, ry, UiKit.signed(r.prestige()), r.prestige() < 0 ? UiKit.BAD : text);
            List<Component> tip = new ArrayList<>();
            tip.add(Component.literal(r.country()).withStyle(ChatFormatting.GOLD));
            tip.add(Component.translatable("gui.politicsmod.ledger.row_cities", r.cities()).withStyle(ChatFormatting.GRAY));
            tip.add(Component.translatable("gui.politicsmod.ledger.row_legitimacy", r.legitimacy()).withStyle(ChatFormatting.GRAY));
            if (!own) tip.add(Component.translatable("gui.politicsmod.ledger.row_compare").withStyle(ChatFormatting.DARK_GRAY));
            hover(x, ry, w, ROW, tip);
        }
    }

    private void number(GuiGraphics g, Column c, int ry, String value, int color) {
        UiKit.textRight(g, font, Component.literal(value), columnX(c) + NUM_COL - 4, ry + 1, color);
    }

    // ── Chart ────────────────────────────────────────────────────────────

    private List<int[]> historyOf(CountryDetailsPayload d, String country) {
        if (country == null) return List.of();
        for (CountryDetailsPayload.History h : d.history()) if (h.country().equals(country)) return h.samples();
        return List.of();
    }

    private void renderChart(GuiGraphics g, int mx, int my, CountryDetailsPayload d) {
        UiKit.section(g, font, Component.translatable("gui.politicsmod.ledger.chart"), x, chartTop);
        int top = chartTop + 14, bottom = y + h - 12;
        int left = x + 32, right = x + w - 4;
        UiKit.card(g, x, top - 2, w, bottom - top + 4);

        List<int[]> mine = historyOf(d, screen.countryName());
        List<int[]> other = metric.ownOnly ? List.of() : historyOf(d, compare);
        if (mine.size() < 2 && other.size() < 2) {
            UiKit.wrap(g, font, Component.translatable("gui.politicsmod.ledger.no_history"), x + 6, top + 4, w - 12, 4, UiKit.TEXT_MUTED);
            return;
        }

        // Ranges over both lines
        int minDay = Integer.MAX_VALUE, maxDay = Integer.MIN_VALUE, min = Integer.MAX_VALUE, max = Integer.MIN_VALUE;
        for (List<int[]> series : List.of(mine, other)) {
            for (int[] s : series) {
                minDay = Math.min(minDay, s[Realm.H_DAY]);
                maxDay = Math.max(maxDay, s[Realm.H_DAY]);
                min = Math.min(min, s[metric.index]);
                max = Math.max(max, s[metric.index]);
            }
        }
        if (max == min) {
            max++;
            min--;
        }
        int dayRange = Math.max(1, maxDay - minDay);

        // Axis labels and the zero line
        g.drawString(font, String.valueOf(max), x + 3, top, UiKit.TEXT_MUTED, false);
        g.drawString(font, String.valueOf(min), x + 3, bottom - 8, UiKit.TEXT_MUTED, false);
        if (min < 0 && max > 0) {
            int zy = valueY(0, min, max, top, bottom);
            g.fill(left, zy, right, zy + 1, 0x30FFFFFF);
        }

        drawSeries(g, mine, minDay, dayRange, min, max, left, right, top, bottom, UiKit.ACCENT);
        drawSeries(g, other, minDay, dayRange, min, max, left, right, top, bottom, UiKit.INFO);

        // Legend
        int lx = x + 4;
        lx = legend(g, lx, bottom + 3, UiKit.ACCENT, screen.countryName());
        if (!other.isEmpty()) legend(g, lx + 8, bottom + 3, UiKit.INFO, compare);
        else if (metric.ownOnly) UiKit.text(g, font, Component.translatable("gui.politicsmod.ledger.own_only"), lx + 8, bottom + 3,
                x + w - lx - 12, UiKit.TEXT_MUTED);

        // Hover: the nearest day and its values
        if (UiKit.inside(mx, my, left, top, right - left, bottom - top)) {
            int day = minDay + Math.round((mx - left) / (float) (right - left) * dayRange);
            int gx = left + (int) ((long) (day - minDay) * (right - left) / dayRange);
            g.fill(gx, top, gx + 1, bottom, 0x60FFFFFF);
            List<Component> tip = new ArrayList<>();
            tip.add(Component.translatable("gui.politicsmod.ledger.day", day + 1).withStyle(ChatFormatting.GOLD));
            addValue(tip, mine, day, screen.countryName(), ChatFormatting.YELLOW);
            addValue(tip, other, day, compare, ChatFormatting.AQUA);
            hover(left, top, right - left, bottom - top, tip);
        }
    }

    private void addValue(List<Component> tip, List<int[]> series, int day, String name, ChatFormatting color) {
        for (int[] s : series) {
            if (s[Realm.H_DAY] == day) tip.add(Component.literal(name + ": " + s[metric.index]).withStyle(color));
        }
    }

    private int legend(GuiGraphics g, int lx, int ly, int color, String name) {
        g.fill(lx, ly + 2, lx + 6, ly + 6, color);
        Component n = Component.literal(name);
        int tw = Math.min(font.width(n), 120);
        UiKit.text(g, font, n, lx + 9, ly, tw, UiKit.TEXT_DIM);
        return lx + 9 + tw;
    }

    private static int valueY(int value, int min, int max, int top, int bottom) {
        return bottom - 1 - (int) ((long) (value - min) * (bottom - top - 2) / (max - min));
    }

    private void drawSeries(GuiGraphics g, List<int[]> series, int minDay, int dayRange, int min, int max,
                            int left, int right, int top, int bottom, int color) {
        int px = -1, py = -1;
        for (int[] s : series) {
            int sx = left + (int) ((long) (s[Realm.H_DAY] - minDay) * (right - left) / dayRange);
            int sy = valueY(s[metric.index], min, max, top, bottom);
            if (px >= 0) line(g, px, py, sx, sy, color);
            g.fill(sx - 1, sy - 1, sx + 2, sy + 2, color);
            px = sx;
            py = sy;
        }
    }

    /** A 1-pixel line made of tiny fills; enough for a few dozen points. */
    private static void line(GuiGraphics g, int x0, int y0, int x1, int y1, int color) {
        int steps = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0));
        for (int i = 0; i <= steps; i++) {
            int lx = x0 + (steps == 0 ? 0 : (x1 - x0) * i / steps);
            int ly = y0 + (steps == 0 ? 0 : (y1 - y0) * i / steps);
            g.fill(lx, ly, lx + 1, ly + 1, color);
        }
    }

    // ── Input ────────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        CountryDetailsPayload d = details();
        if (d == null || button != 0 || !UiKit.inside(mx, my, x, y, w, tableH)) return false;
        if (my < y + ROW + 2) {
            for (Column c : Column.values()) {
                int cx = columnX(c);
                int cw = c == Column.COUNTRY ? nameW() : NUM_COL;
                if (mx >= cx && mx < cx + cw) {
                    if (sort == c) descending = !descending;
                    else {
                        sort = c;
                        descending = c != Column.COUNTRY;
                    }
                    return true;
                }
            }
            return true;
        }
        int index = scroll + (int) ((my - (y + ROW + 3)) / ROW);
        List<CountryDetailsPayload.LedgerRow> rows = sortedRows(d);
        if (index >= 0 && index < rows.size()) {
            String name = rows.get(index).country();
            if (!name.equals(screen.countryName())) compare = name.equals(compare) ? null : name;
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (!UiKit.inside(mx, my, x, y, w, tableH)) return false;
        scroll += delta > 0 ? -1 : 1;
        return true;
    }
}
