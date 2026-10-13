package net.krona.politicsmod.client.dashboard;

import net.krona.politicsmod.client.CountryDashboardScreen;
import net.krona.politicsmod.client.ui.FlatButton;
import net.krona.politicsmod.client.ui.UiKit;
import net.krona.politicsmod.integration.settlement.Settlement;
import net.krona.politicsmod.network.CountryDetailsPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * MineColonies colonies and Millénaire villages of the country, and the villages it may annex.
 * Shown only when one of those mods is installed on the server.
 */
public class SettlementsTab extends DashboardTab {
    private static final int ROW_H = 24;

    private int scroll = 0;
    private String selected = null;
    private FlatButton btnAnnex, btnRelease;

    public SettlementsTab(CountryDashboardScreen screen) {
        super(screen);
    }

    @Override
    public Component title() {
        return Component.translatable("gui.politicsmod.dashboard.tab.settlements");
    }

    @Override
    protected void addWidgets() {
        Component annex = Component.translatable("gui.politicsmod.settlements.annex");
        Component release = Component.translatable("gui.politicsmod.settlements.release");
        int by = y + h - 15;
        btnAnnex = add(new FlatButton(x + w - FlatButton.widthFor(annex), by, FlatButton.widthFor(annex), 15, annex,
                FlatButton.Style.PRIMARY, () -> act("annex")));
        btnRelease = add(new FlatButton(x + w - FlatButton.widthFor(release), by, FlatButton.widthFor(release), 15, release,
                FlatButton.Style.DANGER, () -> act("release")));
    }

    private void act(String action) {
        if (selected != null) screen.runCommand("politicsmod settlement " + action + " " + selected);
    }

    private CountryDetailsPayload.SettlementInfo selectedInfo() {
        CountryDetailsPayload d = details();
        if (d == null || selected == null) return null;
        for (CountryDetailsPayload.SettlementInfo s : d.settlements()) if (s.key().equals(selected)) return s;
        return null;
    }

    @Override
    public void update() {
        CountryDetailsPayload.SettlementInfo s = selectedInfo();
        boolean leader = screen.isLeader();
        btnAnnex.visible = leader && s != null && s.statusEnum() == Settlement.Status.ANNEXABLE;
        btnRelease.visible = leader && s != null && s.statusEnum() == Settlement.Status.ANNEXED;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        CountryDetailsPayload d = details();
        if (d == null) {
            loading(g);
            return;
        }
        List<CountryDetailsPayload.SettlementInfo> list = d.settlements();
        int income = 0;
        for (CountryDetailsPayload.SettlementInfo s : list) income += s.tax();

        UiKit.section(g, font, Component.translatable("gui.politicsmod.settlements.header", list.size()), x, y);
        if (income > 0) {
            UiKit.textRight(g, font, Component.translatable("gui.politicsmod.settlements.income", income), x + w, y, UiKit.GOOD);
        }

        boolean buttons = btnAnnex.visible || btnRelease.visible;
        int top = y + 13;
        int bottom = y + h - (buttons ? 19 : 0);
        UiKit.card(g, x, top, w, bottom - top);

        if (list.isEmpty()) {
            UiKit.wrap(g, font, Component.translatable("gui.politicsmod.settlements.empty", d.annexReputation()),
                    x + 6, top + 6, w - 12, 8, UiKit.TEXT_DIM);
            return;
        }

        int rows = Math.max(1, (bottom - top - 4) / ROW_H);
        scroll = clampScroll(scroll, list.size(), rows);
        g.enableScissor(x + 1, top + 1, x + w - 1, bottom - 1);
        int ry = top + 2;
        for (int i = scroll; i < list.size() && ry + ROW_H <= bottom; i++) {
            CountryDetailsPayload.SettlementInfo s = list.get(i);
            boolean hover = UiKit.inside(mx, my, x + 1, ry, w - 2, ROW_H - 1);
            UiKit.row(g, x + 1, ry, w - 2, ROW_H - 1, s.key().equals(selected), hover);
            renderRow(g, s, x + 5, ry + 2, w - 10);
            ry += ROW_H;
        }
        g.disableScissor();

        if (buttons) {
            CountryDetailsPayload.SettlementInfo s = selectedInfo();
            Component hint = s != null && s.statusEnum() == Settlement.Status.ANNEXABLE
                    ? Component.translatable("gui.politicsmod.settlements.annex_hint")
                    : Component.translatable("gui.politicsmod.settlements.release_hint");
            int bw = btnAnnex.visible ? btnAnnex.getWidth() : btnRelease.getWidth();
            UiKit.wrap(g, font, hint, x, y + h - 14, w - bw - 6, 2, UiKit.TEXT_MUTED);
        }
    }

    private void renderRow(GuiGraphics g, CountryDetailsPayload.SettlementInfo s, int rx, int ry, int rw) {
        Settlement.Status status = s.statusEnum();
        Component chip = Component.translatable("gui.politicsmod.settlements.status." + status.name().toLowerCase(java.util.Locale.ROOT));
        int chipColor = switch (status) {
            case COLONY, CONTROLLED -> UiKit.GOOD;
            case ANNEXED -> UiKit.INFO;
            case ANNEXABLE -> UiKit.WARN;
        };
        Component tax = s.tax() > 0 ? Component.literal("+" + s.tax() + "$") : Component.empty();
        int taxW = font.width(tax);
        int chipW = font.width(chip) + 8;
        UiKit.textRight(g, font, tax, rx + rw, ry + 1, UiKit.GOOD);
        UiKit.chip(g, font, chip, rx + rw - taxW - (taxW > 0 ? 6 : 0) - chipW, ry - 1, chipColor);
        UiKit.text(g, font, Component.literal(s.name()).withStyle(ChatFormatting.BOLD), rx, ry + 1,
                rw - taxW - chipW - 12, UiKit.TEXT);

        int bx = s.chunkX() * 16 + 8, bz = s.chunkZ() * 16 + 8;
        Component details = s.kindEnum() == Settlement.Kind.COLONY
                ? Component.translatable("gui.politicsmod.settlements.colony_line", s.population(), s.mood(), s.owner(), bx, bz)
                : Component.translatable("gui.politicsmod.settlements.village_line", s.population(), reputation(s.mood()), bx, bz);
        UiKit.text(g, font, details, rx, ry + 12, rw, UiKit.TEXT_MUTED);
    }

    /** 12345 → "12 345", so big Millénaire reputations stay readable. */
    private static String reputation(int value) {
        return String.format(java.util.Locale.ROOT, "%,d", value).replace(',', ' ');
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        CountryDetailsPayload d = details();
        if (d == null || d.settlements().isEmpty()) return false;
        int top = y + 13;
        int bottom = y + h - (btnAnnex.visible || btnRelease.visible ? 19 : 0);
        if (my < top + 2 || my >= bottom || mx < x || mx >= x + w) return false;
        int i = scroll + (int) ((my - top - 2) / ROW_H);
        if (i < 0 || i >= d.settlements().size()) return false;
        String key = d.settlements().get(i).key();
        selected = key.equals(selected) ? null : key;
        return true;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        scroll += delta > 0 ? -1 : 1;
        if (scroll < 0) scroll = 0;
        return true;
    }
}
