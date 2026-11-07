package net.krona.politicsmod.client.dashboard;

import com.mojang.blaze3d.systems.RenderSystem;
import net.krona.politicsmod.client.CountryDashboardScreen;
import net.krona.politicsmod.client.FlagTextureManager;
import net.krona.politicsmod.client.ui.FlatButton;
import net.krona.politicsmod.client.ui.UiKit;
import net.krona.politicsmod.network.CountryDetailsPayload;
import net.krona.politicsmod.network.ModNetworking;
import net.krona.politicsmod.network.UpdateFlagPayload;
import net.krona.politicsmod.politics.Law;
import net.krona.politicsmod.realm.Breakdown;
import net.krona.politicsmod.realm.ModifierType;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/** Country at a glance: flag, key numbers, the state of the realm, cities and the political situation. */
public class OverviewTab extends DashboardTab {
    private static final int FLAG_W = 48, FLAG_H = 32;
    private static final int CITY_ROW = 22;
    private static final int STATS_H = 27, REALM_H = 22;

    private EditBox urlInput;
    private FlatButton btnSaveUrl;
    private FlatButton btnStabilize;
    private int cityScroll = 0;

    public OverviewTab(CountryDashboardScreen screen) {
        super(screen);
    }

    @Override
    public Component title() {
        return Component.translatable("gui.politicsmod.dashboard.tab.main");
    }

    private int statsY() {
        return y + FLAG_H + 6;
    }

    private int realmY() {
        return statsY() + STATS_H + 4;
    }

    private int realmCardW() {
        return (w - 8) / 3;
    }

    private boolean realmShown() {
        CountryDetailsPayload d = details();
        return d != null && d.realm().enabled();
    }

    @Override
    protected void addWidgets() {
        // "Restore order": a small button in the stability card, for the leader
        int cw = realmCardW();
        btnStabilize = add(new FlatButton(x + cw - 16, realmY() + 8, 13, 12, Component.literal("+"),
                () -> screen.runCommand("politicsmod realm stabilize")));
        if (!screen.isLeader()) return;
        Component save = Component.translatable("gui.politicsmod.overview.save_flag");
        int bw = FlatButton.widthFor(save);
        int by = y + h - 15;
        urlInput = add(new EditBox(font, x + 1, by + 1, w - bw - 6, 13, Component.literal("URL")));
        urlInput.setMaxLength(256);
        urlInput.setValue(screen.flagUrl() == null ? "" : screen.flagUrl());
        urlInput.setHint(Component.translatable("gui.politicsmod.dashboard.paste_url").withStyle(ChatFormatting.DARK_GRAY));
        btnSaveUrl = add(new FlatButton(x + w - bw, by, bw, 15, save, FlatButton.Style.PRIMARY, () -> {
            screen.setFlagUrl(urlInput.getValue().trim());
            ModNetworking.toServer(new UpdateFlagPayload(screen.countryName(), screen.flagUrl()));
        }));
    }

    @Override
    public void update() {
        if (btnSaveUrl != null) btnSaveUrl.active = !urlInput.getValue().trim().equals(screen.flagUrl());
        CountryDetailsPayload d = details();
        boolean show = screen.isLeader() && realmShown() && d.realm().stability() < 3;
        btnStabilize.visible = show;
        if (show) {
            CountryDetailsPayload.RealmInfo r = d.realm();
            btnStabilize.active = r.stabilizeCooldown() == 0 && d.economy().treasury() >= r.stabilizeCost();
            Component tip = Component.translatable("gui.politicsmod.realm.stabilize_tip", r.stabilizeCost());
            if (r.stabilizeCooldown() > 0) {
                tip = tip.copy().append("\n").append(Component.translatable("gui.politicsmod.realm.stabilize_wait",
                        UiKit.time(r.stabilizeCooldown())));
            }
            btnStabilize.tooltip(tip);
        }
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        CountryDetailsPayload d = details();

        // ── Flag and title
        String url = screen.flagUrl();
        ResourceLocation tex = url == null || url.isEmpty() ? null : FlagTextureManager.getTexture(url);
        UiKit.card(g, x, y, FLAG_W + 2, FLAG_H + 2);
        if (tex != null) {
            RenderSystem.setShaderTexture(0, tex);
            g.blit(tex, x + 1, y + 1, 0, 0, FLAG_W, FLAG_H, FLAG_W, FLAG_H);
        } else {
            g.drawCenteredString(font, "⚑", x + 1 + FLAG_W / 2, y + FLAG_H / 2 - 3, UiKit.TEXT_MUTED);
        }
        int tx = x + FLAG_W + 8, tw = w - FLAG_W - 8;
        UiKit.text(g, font, Component.literal(screen.countryName()).withStyle(ChatFormatting.BOLD), tx, y + 1, tw, UiKit.ACCENT);
        String capital = d == null || d.capital().isEmpty() ? "—" : d.capital();
        UiKit.text(g, font, Component.translatable("gui.politicsmod.overview.capital", capital), tx, y + 12, tw, UiKit.TEXT_DIM);
        UiKit.text(g, font, Component.translatable("gui.politicsmod.info.role", CountryDashboardScreen.roleName(screen.playerRole())),
                tx, y + 23, tw, UiKit.TEXT_DIM);
        if (d == null) {
            loading(g);
            return;
        }

        // ── Numbers
        int sy = statsY(), sh = STATS_H, gap = 4, sw = (w - gap * 3) / 4;
        int treasury = d.economy().treasury();
        UiKit.stat(g, font, x, sy, sw, sh, Component.translatable("gui.politicsmod.overview.treasury"),
                Component.literal(treasury + "$"), UiKit.money(treasury));
        int online = 0;
        for (CountryDetailsPayload.Member m : d.members()) if (m.online()) online++;
        int cx = x + sw + gap;
        UiKit.stat(g, font, cx, sy, sw, sh, Component.translatable("gui.politicsmod.overview.citizens"),
                Component.literal(String.valueOf(d.members().size())), UiKit.TEXT);
        Component onlineMark = Component.literal("● " + online);
        g.drawString(font, onlineMark, cx + sw - 5 - font.width(onlineMark), sy + sh - 12, UiKit.GOOD, false);
        UiKit.stat(g, font, x + (sw + gap) * 2, sy, sw, sh, Component.translatable("gui.politicsmod.overview.territory"),
                Component.translatable("gui.politicsmod.overview.chunks_value", d.economy().chunks()), UiKit.TEXT);
        int net = d.economy().net();
        UiKit.stat(g, font, x + (sw + gap) * 3, sy, sw, sh, Component.translatable("gui.politicsmod.overview.net"),
                Component.literal((net >= 0 ? "+" : "") + net + "$"), UiKit.money(net));

        // ── The realm: stability, legitimacy, prestige
        int ly = sy + sh + 6;
        if (d.realm().enabled()) {
            renderRealm(g, d.realm(), realmY());
            ly = realmY() + REALM_H + 4;
        }

        // ── Cities (left) and situation (right)
        int bottom = y + h - (screen.isLeader() ? 19 : 0);
        int lw = (w - 6) / 2;
        renderCities(g, mx, my, d, x, ly, lw, bottom - ly);
        renderSituation(g, d, x + lw + 6, ly, w - lw - 6, bottom - ly);
    }

    // ── Realm cards ──────────────────────────────────────────────────────

    private void renderRealm(GuiGraphics g, CountryDetailsPayload.RealmInfo r, int ry) {
        int cw = realmCardW();
        int barEnd = screen.isLeader() && r.stability() < 3 ? 19 : 5;

        // Stability: level, progress toward the next one, trend per cycle
        int sx = x;
        UiKit.card(g, sx, ry, cw, REALM_H);
        UiKit.text(g, font, Component.translatable("gui.politicsmod.realm.stability"), sx + 5, ry + 3, cw - 30, UiKit.TEXT_DIM);
        trend(g, sx + cw - 5, ry + 3, r.trend().total(), 1);
        String level = UiKit.signed(r.stability());
        g.drawString(font, level, sx + 5, ry + 12, UiKit.stability(r.stability()), false);
        UiKit.centeredBar(g, sx + 22, ry + 15, cw - 22 - barEnd, 3, r.progress() / (float) r.perLevel());
        hover(sx, ry, cw, REALM_H, stabilityTooltip(r));

        // Legitimacy: 0–100
        int lx = x + cw + 4;
        UiKit.card(g, lx, ry, cw, REALM_H);
        UiKit.text(g, font, Component.translatable("gui.politicsmod.realm.legitimacy"), lx + 5, ry + 3, cw - 30, UiKit.TEXT_DIM);
        trend(g, lx + cw - 5, ry + 3, r.legitimacyTrend().total(), 10);
        int legit = Math.round(r.legitimacy10() / 10f);
        int legitColor = UiKit.heat(1 - legit / 100f);
        g.drawString(font, String.valueOf(legit), lx + 5, ry + 12, legitColor, false);
        UiKit.progress(g, lx + 22, ry + 15, cw - 27, 3, legit / 100f, legitColor);
        hover(lx, ry, cw, REALM_H, legitimacyTooltip(r, legit));

        // Prestige: −100…100
        int px = x + (cw + 4) * 2;
        int pw = w - (cw + 4) * 2;
        UiKit.card(g, px, ry, pw, REALM_H);
        UiKit.text(g, font, Component.translatable("gui.politicsmod.realm.prestige"), px + 5, ry + 3, pw - 10, UiKit.TEXT_DIM);
        int prestige = Math.round(r.prestige10() / 10f);
        g.drawString(font, UiKit.signed(prestige), px + 5, ry + 12, prestige < 0 ? UiKit.BAD : UiKit.ACCENT, false);
        UiKit.centeredBar(g, px + 28, ry + 15, pw - 33, 3, prestige / 100f);
        hover(px, ry, pw, REALM_H, prestigeTooltip(r, prestige));
    }

    /** "▲2" or "▼1" in the card's top-right corner. */
    private void trend(GuiGraphics g, int right, int ty, int value, int divisor) {
        if (value == 0) return;
        Component t = Component.literal((value > 0 ? "▲" : "▼") + Breakdown.format(Math.abs(value), divisor).substring(1));
        UiKit.textRight(g, font, t, right, ty, value > 0 ? UiKit.GOOD : UiKit.BAD);
    }

    private List<Component> stabilityTooltip(CountryDetailsPayload.RealmInfo r) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("gui.politicsmod.realm.stability_title", UiKit.signed(r.stability()), UiKit.signed(r.progress()), r.perLevel())
                .withStyle(ChatFormatting.GOLD));
        lines.add(Component.translatable("gui.politicsmod.realm.per_cycle", Breakdown.format(r.trend().total(), 1))
                .withStyle(ChatFormatting.WHITE));
        lines.addAll(r.trend().lines("", 1, true));
        lines.add(Component.translatable("gui.politicsmod.realm.stability_effects").withStyle(ChatFormatting.DARK_GRAY));
        return lines;
    }

    private List<Component> legitimacyTooltip(CountryDetailsPayload.RealmInfo r, int legit) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("gui.politicsmod.realm.legitimacy_title", legit).withStyle(ChatFormatting.GOLD));
        lines.add(Component.translatable("gui.politicsmod.realm.per_cycle", Breakdown.format(r.legitimacyTrend().total(), 10))
                .withStyle(ChatFormatting.WHITE));
        lines.addAll(r.legitimacyTrend().lines("", 10, true));
        lines.add(Component.translatable("gui.politicsmod.realm.legitimacy_effects").withStyle(ChatFormatting.DARK_GRAY));
        return lines;
    }

    private List<Component> prestigeTooltip(CountryDetailsPayload.RealmInfo r, int prestige) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("gui.politicsmod.realm.prestige_title", UiKit.signed(prestige)).withStyle(ChatFormatting.GOLD));
        for (ModifierType type : new ModifierType[]{ModifierType.EMBASSY_INCOME, ModifierType.LAND_PRICE}) {
            for (Breakdown.Part p : r.modifier(type).parts()) {
                if (!p.key().equals("realm.politicsmod.source.prestige")) continue;
                boolean good = (p.value() > 0) == (type == ModifierType.EMBASSY_INCOME);
                lines.add(Component.translatable(type.nameKey()).withStyle(ChatFormatting.GRAY).append(": ")
                        .append(Component.literal(Breakdown.format(p.value(), 1) + "%")
                                .withStyle(good ? ChatFormatting.GREEN : ChatFormatting.RED)));
            }
        }
        lines.add(Component.translatable("gui.politicsmod.realm.prestige_effects").withStyle(ChatFormatting.DARK_GRAY));
        return lines;
    }

    // ── Cities ───────────────────────────────────────────────────────────

    private void renderCities(GuiGraphics g, int mx, int my, CountryDetailsPayload d, int cx, int cy, int cw, int ch) {
        UiKit.section(g, font, Component.translatable("gui.politicsmod.overview.cities", d.cities().size()), cx, cy);
        int top = cy + 11;
        UiKit.card(g, cx, top, cw, ch - 11);
        List<CountryDetailsPayload.City> cities = d.cities();
        if (cities.isEmpty()) {
            UiKit.text(g, font, Component.translatable("gui.politicsmod.dashboard.no_cities"), cx + 5, top + 5, cw - 10, UiKit.TEXT_MUTED);
            return;
        }
        boolean realm = d.realm().enabled();
        int rows = Math.max(1, (ch - 13) / CITY_ROW);
        cityScroll = clampScroll(cityScroll, cities.size(), rows);
        for (int r = 0; r < rows && cityScroll + r < cities.size(); r++) {
            CountryDetailsPayload.City c = cities.get(cityScroll + r);
            int ry = top + 1 + r * CITY_ROW;
            UiKit.row(g, cx + 1, ry, cw - 2, CITY_ROW - 1, false, UiKit.inside(mx, my, cx, ry, cw, CITY_ROW));
            boolean capital = c.name().equals(d.capital());
            Component balance = Component.literal(c.balance() + "$");
            int bw = font.width(balance);
            UiKit.text(g, font, Component.literal((capital ? "★ " : "") + c.name()), cx + 5, ry + 2, cw - bw - 14, capital ? UiKit.ACCENT : UiKit.TEXT);
            UiKit.textRight(g, font, balance, cx + cw - 5, ry + 2, UiKit.money(c.balance()));
            // Development as a number, unrest as a small bar that turns red toward revolt
            int barW = realm ? 20 : 0;
            Component right = realm
                    ? Component.literal("◆" + c.development())
                    : Component.translatable("gui.politicsmod.overview.chunks_value", c.chunks());
            int kw = font.width(right) + (realm ? barW + 4 : 0);
            Component mayor = Component.literal("♛ " + (c.mayor().isEmpty() ? "—" : c.mayor()));
            UiKit.text(g, font, mayor, cx + 5, ry + 12, cw - kw - 14, UiKit.TEXT_DIM);
            if (realm) {
                float t = c.unrestValue() / (float) Math.max(1, d.realm().unrestSecede());
                UiKit.progress(g, cx + cw - 5 - barW, ry + 14, barW, 4, Math.max(0.05f, c.unrestValue() / 100f), UiKit.heat(t));
                UiKit.textRight(g, font, right, cx + cw - 9 - barW, ry + 12, UiKit.ACCENT);
            } else {
                UiKit.textRight(g, font, right, cx + cw - 5, ry + 12, UiKit.TEXT_MUTED);
            }
            if (realm) hover(cx, ry, cw, CITY_ROW, cityTooltip(d, c));
        }
    }

    private List<Component> cityTooltip(CountryDetailsPayload d, CountryDetailsPayload.City c) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal(c.name()).withStyle(ChatFormatting.GOLD));
        lines.add(Component.translatable("gui.politicsmod.realm.city_chunks", c.chunks(), c.nonCore()).withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("gui.politicsmod.realm.city_development", c.development(), c.boughtDevelopment(),
                c.development() - c.boughtDevelopment()).withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("gui.politicsmod.realm.city_tax", Breakdown.format(c.taxPercent(), 1) + "%")
                .withStyle(c.taxPercent() >= 0 ? ChatFormatting.GREEN : ChatFormatting.RED));
        lines.add(Component.translatable("gui.politicsmod.realm.city_unrest", c.unrestValue()).withStyle(ChatFormatting.WHITE));
        lines.addAll(c.unrest().lines("", 1, false));
        if (c.unrestValue() >= d.realm().unrestSecede()) {
            lines.add(Component.translatable("gui.politicsmod.realm.city_may_secede").withStyle(ChatFormatting.RED));
        }
        if (c.nextDevelopmentCost() >= 0) {
            lines.add(Component.translatable("gui.politicsmod.realm.city_develop_hint", c.nextDevelopmentCost()).withStyle(ChatFormatting.DARK_GRAY));
        }
        return lines;
    }

    private void renderSituation(GuiGraphics g, CountryDetailsPayload d, int sx, int sy, int sw, int sh) {
        UiKit.section(g, font, Component.translatable("gui.politicsmod.overview.situation"), sx, sy);
        int top = sy + 11;
        UiKit.card(g, sx, top, sw, sh - 11);
        g.enableScissor(sx + 1, top + 1, sx + sw - 1, top + sh - 12);
        int ly = top + 4;
        int tw = sw - 10;

        // Laws in force, as chips that wrap onto new lines
        int cx = sx + 5;
        boolean anyLaw = false;
        for (Law law : Law.values()) {
            if (!d.hasLaw(law)) continue;
            Component name = Component.translatable(law.nameKey());
            if (anyLaw && cx + font.width(name) + 8 > sx + sw - 4) {
                cx = sx + 5;
                ly += 13;
            }
            cx += UiKit.chip(g, font, name, cx, ly, UiKit.INFO) + 3;
            anyLaw = true;
        }
        if (anyLaw) ly += 15;
        else ly += UiKit.wrap(g, font, Component.translatable("gui.politicsmod.overview.no_laws"), sx + 5, ly, tw, 2, UiKit.TEXT_MUTED) + 3;

        CountryDetailsPayload.ElectionInfo e = d.election();
        Component election = e.type().isEmpty()
                ? Component.translatable("gui.politicsmod.overview.no_election")
                : Component.translatable("gui.politicsmod.overview.election", UiKit.time(e.secondsLeft()));
        ly += UiKit.wrap(g, font, election, sx + 5, ly, tw, 2, e.type().isEmpty() ? UiKit.TEXT_DIM : UiKit.WARN) + 2;

        int wars = 0, allies = 0;
        for (CountryDetailsPayload.Relation r : d.relations()) {
            if ("WAR".equals(r.status())) wars++;
            if ("ALLIANCE".equals(r.status())) allies++;
        }
        ly += UiKit.wrap(g, font, Component.translatable("gui.politicsmod.overview.relations", wars, allies), sx + 5, ly, tw, 2,
                wars > 0 ? UiKit.BAD : UiKit.TEXT_DIM) + 2;
        if (d.realm().enabled() && d.realm().overextension() > 0) {
            ly += UiKit.wrap(g, font, Component.translatable("gui.politicsmod.overview.overextension", d.realm().overextension()),
                    sx + 5, ly, tw, 2, d.realm().overextension() >= 25 ? UiKit.WARN : UiKit.TEXT_DIM) + 2;
        }
        if (!d.sieges().isEmpty()) {
            ly += UiKit.wrap(g, font, Component.translatable("gui.politicsmod.overview.sieges", d.sieges().size()), sx + 5, ly, tw, 2, UiKit.BAD) + 2;
        }
        if (d.economy().treasury() < 0) {
            ly += UiKit.wrap(g, font, Component.translatable("gui.politicsmod.overview.bankrupt"), sx + 5, ly, tw, 2, UiKit.BAD) + 2;
        }
        if (d.economy().debt() > 0) {
            UiKit.wrap(g, font, Component.translatable("gui.politicsmod.overview.debt", d.economy().debt()), sx + 5, ly, tw, 2, UiKit.WARN);
        }
        g.disableScissor();
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        cityScroll += delta > 0 ? -1 : 1;
        return true;
    }
}
