package net.krona.politicsmod.client;

import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/**
 * The country info panel (look, place and size set in HudSettingsScreen) and the name of the land the player enters,
 * shown big for a moment.
 * Hidden with F1 like the rest of the HUD, and while the F3 screen is open; ClientHudSettings picks what is shown.
 */
public class PoliticsHudOverlay implements LayeredDraw.Layer {

    public static final ResourceLocation HUD_ID = ResourceLocation.fromNamespaceAndPath("politicsmod", "hud");

    /** How long the entry banner stays, ms, with its fade in and out. */
    private static final long BANNER_MS = 3000, FADE_IN_MS = 300, FADE_OUT_MS = 800;

    @Override
    public void render(GuiGraphics g, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.player == null) return;
        ClientHudSettings.Mode mode = ClientHudSettings.mode();
        if (mode == ClientHudSettings.Mode.OFF) return;

        renderBanner(g, mc);
        if (mc.getDebugOverlay().showDebugScreen()) return;

        boolean panel = mode == ClientHudSettings.Mode.ALWAYS
                || (mode == ClientHudSettings.Mode.TERRITORY && ClientPoliticsData.inTerritory);
        renderPanel(g, mc.font, panel);
    }

    // ── Corner panel ─────────────────────────────────────────────────────

    private static final int PAD = 4, ROW = 11, ICON = 8;
    private static final int BG = 0xD8151820, BORDER = 0x40FFFFFF;
    private static final int OWN = 0x5BD16B, ALLY = 0x5AB0E8, ENEMY = 0xE0565B, NEUTRAL = 0xE8E8E8, WILD = 0x9AA0AA;

    /** One line of the panel: an item icon (or none), text and colour. */
    private record Row(ItemStack icon, Component text, int color) {
    }

    private static final ItemStack CITY = new ItemStack(Items.BELL);
    private static final ItemStack PLOT = new ItemStack(Items.OAK_SIGN);
    private static final ItemStack TREASURY = new ItemStack(Items.GOLD_BLOCK);
    private static final ItemStack WALLET = new ItemStack(Items.GOLD_NUGGET);
    private static final ItemStack RADAR = new ItemStack(Items.SPYGLASS);
    private static final ItemStack EVENT = new ItemStack(Items.WRITABLE_BOOK);

    /**
     * The panel in the look, place and size the player picked (ClientHudSettings): the flag and the name of the land
     * in the colour of the relation (own, ally, enemy, neutral, wilderness), then the city, the plot and the money with
     * item icons instead of words. The radar warning and a waiting event are shown even when the rest is hidden.
     */
    private static void renderPanel(GuiGraphics g, Font font, boolean full) {
        List<Row> rows = new ArrayList<>();
        if (full) {
            if (!ClientPoliticsData.currentCity.isEmpty()) {
                rows.add(new Row(CITY, Component.literal(ClientPoliticsData.currentCity), 0xFFD700));
            }
            if (ClientPoliticsData.plotHere) {
                Component plot;
                if (!ClientPoliticsData.plotOwner.isEmpty()) {
                    plot = Component.translatable("gui.politicsmod.hud.plot_owner", ClientPoliticsData.plotOwner);
                } else if (ClientPoliticsData.plotSale > 0) {
                    plot = Component.translatable("gui.politicsmod.hud.plot_sale", ClientPoliticsData.plotSale);
                } else if (ClientPoliticsData.plotRent > 0) {
                    plot = Component.translatable("gui.politicsmod.hud.plot_rent", ClientPoliticsData.plotRent);
                } else {
                    plot = Component.translatable("gui.politicsmod.hud.plot_free");
                }
                rows.add(new Row(PLOT, plot, 0xAAAAFF));
            }
            if (ClientPoliticsData.myTreasury != net.krona.politicsmod.network.SyncHudPayload.NO_COUNTRY) {
                rows.add(new Row(TREASURY, Component.literal(money(ClientPoliticsData.myTreasury)),
                        ClientPoliticsData.myTreasury < 0 ? 0xFF5555 : 0x55FF55));
            }
            rows.add(new Row(WALLET, Component.literal(money(ClientPoliticsData.myWallet)), 0xFFD700));
        }
        if (ClientPoliticsData.hasActiveRadarAlert()) {
            rows.add(new Row(RADAR, Component.translatable("gui.politicsmod.hud.radar_alert", ClientPoliticsData.radarAlert), 0xFF5555));
        }
        if (ClientPoliticsData.pendingEvents > 0) {
            // Short on purpose: the panel must stay small
            Component line = ClientPoliticsData.canAnswerEvents
                    ? Component.translatable("gui.politicsmod.hud.event_short", ClientPoliticsData.pendingEvents,
                            ModKeyMappings.OPEN_COUNTRY_MENU.getTranslatedKeyMessage())
                    : Component.translatable("gui.politicsmod.hud.event_short_leader", ClientPoliticsData.pendingEvents);
            int color = 0xFFD700;
            if (ClientPoliticsData.canAnswerEvents) {
                // Pulses from gold to almost white for whoever can answer
                float pulse = (float) (0.5 + 0.5 * Math.sin(System.currentTimeMillis() / 300.0));
                color = (0xFF << 16) | ((0xC0 + Math.round(pulse * 0x3F)) << 8) | (0x40 + Math.round(pulse * 0xBF));
            }
            rows.add(new Row(EVENT, line, color));
        }
        if (!full && rows.isEmpty()) return;

        int relation = relationColor();
        Component name = Component.literal(ClientPoliticsData.currentCountry).withStyle(ChatFormatting.BOLD);
        ClientHudSettings.Style style = ClientHudSettings.style();

        // Size first, then the place on the screen
        int w, h;
        switch (style) {
            case COMPACT -> {
                w = full ? 18 + font.width(name) : 0;
                for (Row r : rows) w += (w > 0 ? GAP : 0) + ICON + 3 + font.width(r.text);
                w += PAD * 2 + 2;
                h = 14;
            }
            case TEXT -> {
                w = full ? 18 + font.width(name) : 0;
                for (Row r : rows) w = Math.max(w, ICON + 4 + font.width(r.text));
                h = (full ? 13 : 0) + rows.size() * ROW;
            }
            default -> {
                w = full ? 18 + font.width(name) : 0;
                for (Row r : rows) w = Math.max(w, ICON + 4 + font.width(r.text));
                w += PAD * 2 + 2;
                h = PAD * 2 + (full ? 13 : 0) + rows.size() * ROW - (rows.isEmpty() ? 2 : 0);
            }
        }
        float scale = ClientHudSettings.scale() / 100f;
        int sw = Math.round(w * scale), sh = Math.round(h * scale);
        int gw = g.guiWidth(), gh = g.guiHeight();
        int x = switch (ClientHudSettings.position()) {
            case TOP_LEFT, MIDDLE_LEFT -> MARGIN;
            case TOP_CENTER -> (gw - sw) / 2;
            default -> gw - sw - MARGIN;
        };
        int y = switch (ClientHudSettings.position()) {
            case MIDDLE_LEFT, MIDDLE_RIGHT -> (gh - sh) / 2;
            case BOTTOM_RIGHT -> gh - sh - MARGIN;
            default -> MARGIN;
        };

        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(scale, scale, 1f);
        switch (style) {
            case COMPACT -> drawCompact(g, font, full, name, relation, rows, w, h);
            case TEXT -> drawLines(g, font, full, name, relation, rows, 0, 0);
            default -> drawCard(g, font, full, name, relation, rows, w, h);
        }
        g.pose().popPose();
    }

    private static final int MARGIN = 6, GAP = 8;

    private static void drawCard(GuiGraphics g, Font font, boolean full, Component name, int relation, List<Row> rows, int w, int h) {
        g.fill(0, 0, w, h, BG);
        g.renderOutline(0, 0, w, h, BORDER);
        g.fill(0, 0, 2, h, 0xFF000000 | (full ? relation : 0xFFD700));
        if (full && !rows.isEmpty()) g.fill(PAD + 2, PAD + 10, w - PAD, PAD + 11, BORDER);
        drawLines(g, font, full, name, relation, rows, PAD + 2, PAD);
    }

    /** Flag and name, then one row per line: used by the card and by the text-only look. */
    private static void drawLines(GuiGraphics g, Font font, boolean full, Component name, int relation, List<Row> rows, int x, int y) {
        if (full) {
            renderFlag(g, font, x, y, relation);
            g.drawString(font, name, x + 18, y + 1, relation, true);
            y += 13;
        }
        for (Row r : rows) {
            icon(g, r.icon, x, y);
            g.drawString(font, r.text, x + ICON + 4, y, r.color, true);
            y += ROW;
        }
    }

    /** Everything on one line in a thin strip. */
    private static void drawCompact(GuiGraphics g, Font font, boolean full, Component name, int relation, List<Row> rows, int w, int h) {
        g.fill(0, 0, w, h, BG);
        g.renderOutline(0, 0, w, h, BORDER);
        g.fill(0, 0, 2, h, 0xFF000000 | (full ? relation : 0xFFD700));
        int x = PAD + 2;
        int y = (h - 8) / 2;
        boolean first = true;
        if (full) {
            renderFlag(g, font, x, y - 1, relation);
            g.drawString(font, name, x + 18, y, relation, true);
            x += 18 + font.width(name);
            first = false;
        }
        for (Row r : rows) {
            if (!first) {
                g.fill(x + GAP / 2, 3, x + GAP / 2 + 1, h - 3, BORDER);
                x += GAP;
            }
            first = false;
            icon(g, r.icon, x, y);
            g.drawString(font, r.text, x + ICON + 3, y, r.color, true);
            x += ICON + 3 + font.width(r.text);
        }
    }

    /** An item drawn at half size, 8x8. */
    private static void icon(GuiGraphics g, ItemStack icon, int x, int y) {
        if (icon == null) return;
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(0.5f, 0.5f, 1f);
        g.renderItem(icon, 0, 0);
        g.pose().popPose();
    }

    /** The country's flag, 14x10; a coloured square when it has none or it is still loading. */
    private static void renderFlag(GuiGraphics g, Font font, int x, int y, int relation) {
        ResourceLocation flag = null;
        if (ClientPoliticsData.inTerritory) {
            String url = ClientPoliticsData.getFlagUrl(ClientPoliticsData.currentCountry);
            if (!url.isEmpty()) flag = FlagTextureManager.getTexture(url);
        }
        if (flag != null) {
            g.blit(flag, x, y, 14, 10, 0, 0, 22, 15, 22, 15);
        } else {
            g.fill(x, y, x + 14, y + 10, 0xFF000000 | (relation & 0xFFFFFF));
            g.fill(x + 1, y + 1, x + 13, y + 9, 0xFF2A2F39);
            g.drawString(font, "⚑", x + 4, y + 1, relation, false);
        }
        g.renderOutline(x - 1, y - 1, 16, 12, BORDER);
    }

    /** Colour of the land the player stands on, as the world borders show it. */
    private static int relationColor() {
        if (!ClientPoliticsData.inTerritory) return WILD;
        String here = ClientPoliticsData.currentCountry;
        if (here.equals(ClientPoliticsData.myCountry)) return OWN;
        net.krona.politicsmod.network.CountryDetailsPayload d = ClientPoliticsData.countryDetails;
        if (d != null) {
            for (net.krona.politicsmod.network.CountryDetailsPayload.Relation r : d.relations()) {
                if (!r.country().equals(here)) continue;
                if ("WAR".equals(r.status())) return ENEMY;
                if ("ALLIANCE".equals(r.status())) return ALLY;
            }
        }
        return NEUTRAL;
    }

    /** "7 108$" with a space between thousands, "−50$" for debt. */
    private static String money(int amount) {
        String digits = String.valueOf(Math.abs((long) amount));
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < digits.length(); i++) {
            if (i > 0 && (digits.length() - i) % 3 == 0) out.append(' ');
            out.append(digits.charAt(i));
        }
        return (amount < 0 ? "−" : "") + out + "$";
    }

    private static void renderBanner(GuiGraphics g, Minecraft mc) {
        long age = System.currentTimeMillis() - ClientPoliticsData.bannerShownAt;
        if (ClientPoliticsData.bannerTitle.isEmpty() || age < 0 || age > BANNER_MS) return;
        float alpha = age < FADE_IN_MS ? age / (float) FADE_IN_MS
                : age > BANNER_MS - FADE_OUT_MS ? (BANNER_MS - age) / (float) FADE_OUT_MS : 1f;
        int a = Math.max(4, Math.min(255, Math.round(alpha * 255)));
        if (a <= 4) return;
        Font font = mc.font;
        int cx = g.guiWidth() / 2;
        int cy = g.guiHeight() / 4;
        Component title = Component.literal(ClientPoliticsData.bannerTitle);
        g.pose().pushPose();
        g.pose().translate(cx, cy, 0);
        g.pose().scale(2f, 2f, 1f);
        g.drawString(font, title, -font.width(title) / 2, 0, (a << 24) | (ClientPoliticsData.bannerColor & 0xFFFFFF), true);
        g.pose().popPose();
        if (!ClientPoliticsData.bannerSubtitle.isEmpty()) {
            Component sub = Component.literal(ClientPoliticsData.bannerSubtitle);
            g.drawString(font, sub, cx - font.width(sub) / 2, cy + 20, (a << 24) | 0xFFD700, true);
        }
    }
}
