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
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/** Country at a glance: flag, key numbers, cities and the political situation. */
public class OverviewTab extends DashboardTab {
    private static final int FLAG_W = 48, FLAG_H = 32;
    private static final int CITY_ROW = 22;

    private EditBox urlInput;
    private FlatButton btnSaveUrl;
    private int cityScroll = 0;

    public OverviewTab(CountryDashboardScreen screen) {
        super(screen);
    }

    @Override
    public Component title() {
        return Component.translatable("gui.politicsmod.dashboard.tab.main");
    }

    @Override
    protected void addWidgets() {
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
        int sy = y + FLAG_H + 6, sh = 27, gap = 4, sw = (w - gap * 3) / 4;
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

        // ── Cities (left) and situation (right)
        int ly = sy + sh + 6;
        int bottom = y + h - (screen.isLeader() ? 19 : 0);
        int lw = (w - 6) / 2;
        renderCities(g, mx, my, d, x, ly, lw, bottom - ly);
        renderSituation(g, d, x + lw + 6, ly, w - lw - 6, bottom - ly);
    }

    private void renderCities(GuiGraphics g, int mx, int my, CountryDetailsPayload d, int cx, int cy, int cw, int ch) {
        UiKit.section(g, font, Component.translatable("gui.politicsmod.overview.cities", d.cities().size()), cx, cy);
        int top = cy + 11;
        UiKit.card(g, cx, top, cw, ch - 11);
        List<CountryDetailsPayload.City> cities = d.cities();
        if (cities.isEmpty()) {
            UiKit.text(g, font, Component.translatable("gui.politicsmod.dashboard.no_cities"), cx + 5, top + 5, cw - 10, UiKit.TEXT_MUTED);
            return;
        }
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
            Component chunks = Component.translatable("gui.politicsmod.overview.chunks_value", c.chunks());
            int kw = font.width(chunks);
            Component mayor = Component.literal("♛ " + (c.mayor().isEmpty() ? "—" : c.mayor()));
            UiKit.text(g, font, mayor, cx + 5, ry + 12, cw - kw - 14, UiKit.TEXT_DIM);
            UiKit.textRight(g, font, chunks, cx + cw - 5, ry + 12, UiKit.TEXT_MUTED);
        }
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
