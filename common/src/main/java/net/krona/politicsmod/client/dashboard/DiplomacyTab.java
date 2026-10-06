package net.krona.politicsmod.client.dashboard;

import net.krona.politicsmod.client.CountryDashboardScreen;
import net.krona.politicsmod.client.ui.FlatButton;
import net.krona.politicsmod.client.ui.UiKit;
import net.krona.politicsmod.network.CountryDetailsPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

/** Relations with every country, incoming proposals, and sieges. */
public class DiplomacyTab extends DashboardTab {
    private static final int ROW_H = 13;

    private int selected = -1;
    private int scroll = 0;
    private int lw, rx, rw, listTop, listH, pendingTextW;
    private FlatButton btnAccept, btnReject, btnWar, btnAlliance, btnPeace, btnSiege;

    public DiplomacyTab(CountryDashboardScreen screen) {
        super(screen);
    }

    @Override
    public Component title() {
        return Component.translatable("gui.politicsmod.dashboard.tab.diplomacy");
    }

    @Override
    protected void addWidgets() {
        lw = w * 57 / 100;
        rx = x + lw + 6;
        rw = w - lw - 6;

        Component accept = Component.translatable("gui.politicsmod.diplomacy.accept");
        Component reject = Component.translatable("gui.politicsmod.diplomacy.reject");
        int rejW = FlatButton.widthFor(reject), accW = FlatButton.widthFor(accept);
        btnReject = add(new FlatButton(x + w - rejW - 3, y + 4, rejW, 14, reject,
                () -> screen.runCommand("politicsmod diplomacy reject")));
        btnAccept = add(new FlatButton(x + w - rejW - accW - 6, y + 4, accW, 14, accept,
                FlatButton.Style.PRIMARY, () -> screen.runCommand("politicsmod diplomacy accept")));
        pendingTextW = w - rejW - accW - 16;

        // War / alliance / peace: widths follow the labels and share the list's width
        int by = y + h - 15;
        // Measured with a wide price, so the real one always fits when the data arrives
        Component war = Component.translatable("gui.politicsmod.diplomacy.war_button", 99999);
        Component alliance = Component.translatable("gui.politicsmod.diplomacy.alliance_button");
        Component peace = Component.translatable("gui.politicsmod.diplomacy.peace_button");
        int ww = FlatButton.widthFor(war), aw = FlatButton.widthFor(alliance), pw = FlatButton.widthFor(peace);
        int spare = Math.max(0, lw - ww - aw - pw - 8) / 3;
        ww += spare;
        aw += spare;
        pw = lw - ww - aw - 8;
        btnWar = add(new FlatButton(x, by, ww, 15, war, FlatButton.Style.DANGER, () -> action("war")));
        btnAlliance = add(new FlatButton(x + ww + 4, by, aw, 15, alliance, () -> action("alliance")));
        btnPeace = add(new FlatButton(x + ww + aw + 8, by, pw, 15, peace, () -> action("peace")));

        btnSiege = add(new FlatButton(rx, by, rw, 15, Component.empty(), FlatButton.Style.DANGER,
                () -> screen.runCommand("politicsmod siege start")));

        listTop = y + 40;
        listH = by - 5 - listTop;
    }

    private CountryDetailsPayload.Relation relation() {
        CountryDetailsPayload d = details();
        if (d == null || selected < 0 || selected >= d.relations().size()) return null;
        return d.relations().get(selected);
    }

    private void action(String action) {
        CountryDetailsPayload.Relation r = relation();
        if (r != null) screen.runCommand("politicsmod diplomacy " + action + " " + r.country());
    }

    @Override
    public void update() {
        CountryDetailsPayload d = details();
        boolean leader = screen.isLeader();
        boolean pending = d != null && !d.pendingFrom().isEmpty();
        btnAccept.visible = leader && pending;
        btnReject.visible = leader && pending;

        CountryDetailsPayload.Relation r = relation();
        String status = r != null ? r.status() : "";
        btnWar.visible = leader;
        btnAlliance.visible = leader;
        btnPeace.visible = leader;
        btnWar.active = r != null && !"WAR".equals(status);
        btnWar.setMessage(Component.translatable("gui.politicsmod.diplomacy.war_button", d != null ? d.warCost() : 0));
        btnAlliance.active = r != null && "NEUTRAL".equals(status);
        btnPeace.active = r != null && "WAR".equals(status);

        boolean atWar = false;
        if (d != null) for (CountryDetailsPayload.Relation rel : d.relations()) atWar |= "WAR".equals(rel.status());
        btnSiege.visible = leader || "MAYOR".equals(screen.playerRole());
        btnSiege.active = atWar;
        btnSiege.setMessage(Component.translatable("gui.politicsmod.siege.start_here", d != null ? d.siegeCost() : 0));
        btnSiege.tooltip(Component.translatable("gui.politicsmod.siege.start_hint"));
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        CountryDetailsPayload d = details();
        if (d == null) {
            loading(g);
            return;
        }

        // Incoming proposal
        UiKit.card(g, x, y, w, 22);
        if (d.pendingFrom().isEmpty()) {
            UiKit.text(g, font, Component.translatable("gui.politicsmod.diplomacy.no_pending"), x + 6, y + 7, w - 12, UiKit.TEXT_MUTED);
        } else {
            g.fill(x, y, x + 2, y + 22, UiKit.ACCENT);
            String key = "ALLIANCE".equals(d.pendingType()) ? "gui.politicsmod.diplomacy.pending_alliance" : "gui.politicsmod.diplomacy.pending_peace";
            UiKit.text(g, font, Component.translatable(key, d.pendingFrom()), x + 6, y + 7, screen.isLeader() ? pendingTextW : w - 12, UiKit.ACCENT);
        }

        // Relations
        List<CountryDetailsPayload.Relation> relations = d.relations();
        UiKit.section(g, font, Component.translatable("gui.politicsmod.diplomacy.relations_header"), x, y + 28);
        UiKit.card(g, x, listTop, lw, listH);
        if (relations.isEmpty()) {
            g.drawCenteredString(font, Component.translatable("gui.politicsmod.diplomacy.no_countries"), x + lw / 2, listTop + 8, UiKit.TEXT_MUTED);
        }
        int rows = listH / ROW_H;
        scroll = clampScroll(scroll, relations.size(), rows);
        for (int r = 0; r < rows && scroll + r < relations.size(); r++) {
            int index = scroll + r;
            CountryDetailsPayload.Relation rel = relations.get(index);
            int ry = listTop + 1 + r * ROW_H;
            UiKit.row(g, x + 1, ry, lw - 2, ROW_H - 1, index == selected, UiKit.inside(mx, my, x, ry, lw, ROW_H));
            Component status = Component.translatable("gui.politicsmod.diplomacy.status." + rel.status().toLowerCase());
            UiKit.text(g, font, Component.literal(rel.country()), x + 6, ry + 2, lw - font.width(status) - 16, UiKit.TEXT);
            UiKit.textRight(g, font, status, x + lw - 6, ry + 2, statusColor(rel.status()));
        }

        // Sieges
        UiKit.section(g, font, Component.translatable("gui.politicsmod.siege.header"), rx, y + 28);
        int sy = listTop;
        if (d.sieges().isEmpty()) {
            UiKit.card(g, rx, sy, rw, listH);
            UiKit.wrap(g, font, Component.translatable("gui.politicsmod.siege.none_hint"), rx + 5, sy + 4, rw - 10, (listH - 6) / 10, UiKit.TEXT_MUTED);
        }
        for (CountryDetailsPayload.SiegeInfo s : d.sieges()) {
            if (sy + 32 > listTop + listH) break;
            boolean attacking = s.attacker().equals(screen.countryName());
            UiKit.card(g, rx, sy, rw, 32);
            g.fill(rx, sy, rx + 2, sy + 32, attacking ? UiKit.WARN : UiKit.BAD);
            Component head = Component.translatable(attacking ? "gui.politicsmod.siege.attacking" : "gui.politicsmod.siege.defending",
                    attacking ? s.defender() : s.attacker());
            UiKit.text(g, font, head, rx + 6, sy + 4, rw - 10, UiKit.TEXT);
            UiKit.progress(g, rx + 6, sy + 16, rw - 12, 5, s.percent() / 100f, attacking ? UiKit.WARN : UiKit.BAD);
            // "50% · ⌛ 2:30" on the left, the chunk on the right: no words, so it fits in every language
            Component coords = Component.literal("[" + s.x() + ", " + s.z() + "]");
            UiKit.text(g, font, Component.literal(s.percent() + "% · ⌛ " + UiKit.time(s.secondsLeft())), rx + 6, sy + 23,
                    rw - font.width(coords) - 16, UiKit.TEXT_DIM);
            UiKit.textRight(g, font, coords, rx + rw - 5, sy + 23, UiKit.TEXT_MUTED);
            sy += 36;
        }
    }

    private static int statusColor(String status) {
        return switch (status) {
            case "WAR" -> UiKit.BAD;
            case "ALLIANCE" -> UiKit.GOOD;
            default -> UiKit.TEXT_DIM;
        };
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        CountryDetailsPayload d = details();
        if (d == null || !UiKit.inside(mx, my, x, listTop, lw, listH)) return false;
        int index = scroll + (int) ((my - listTop - 1) / ROW_H);
        selected = index < d.relations().size() ? index : -1;
        update();
        return true;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        scroll += delta > 0 ? -1 : 1;
        return true;
    }
}
