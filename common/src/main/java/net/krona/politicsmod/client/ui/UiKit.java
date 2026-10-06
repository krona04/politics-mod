package net.krona.politicsmod.client.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;

/** Shared palette and drawing helpers, so every PoliticsMod screen looks the same. */
public final class UiKit {

    // ── Palette (ARGB) ───────────────────────────────────────────────────
    public static final int PANEL = 0xF01B1E25;
    public static final int PANEL_BORDER = 0xFF3B414D;
    public static final int SIDEBAR = 0xF0131519;
    public static final int CARD = 0xFF242833;
    public static final int CARD_HOVER = 0xFF2E3340;
    public static final int CARD_BORDER = 0xFF343A47;
    public static final int SELECTED = 0xFF35405A;
    public static final int ACCENT = 0xFFE0B040;
    public static final int ACCENT_DARK = 0xFF7A5E1E;
    public static final int TEXT = 0xFFE8E8E8;
    public static final int TEXT_DIM = 0xFF9AA0AA;
    public static final int TEXT_MUTED = 0xFF626873;
    public static final int GOOD = 0xFF5BD16B;
    public static final int BAD = 0xFFE0565B;
    public static final int WARN = 0xFFE8B84A;
    public static final int INFO = 0xFF5AB0E8;

    // Map colours
    public static final int MAP_BG = 0xFF14171C;
    public static final int MAP_GRID = 0x18FFFFFF;
    public static final int MAP_OWN = 0xFF4FB866;
    public static final int MAP_ALLY = 0xFF4E9BD8;
    public static final int MAP_ENEMY = 0xFFD9484F;
    public static final int MAP_NEUTRAL = 0xFF8C929C;

    private UiKit() {
    }

    public static void panel(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, PANEL);
        g.renderOutline(x, y, w, h, PANEL_BORDER);
    }

    public static void card(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, CARD);
        g.renderOutline(x, y, w, h, CARD_BORDER);
    }

    /** A section title with a short accent bar in front. */
    public static void section(GuiGraphics g, Font font, Component text, int x, int y) {
        g.fill(x, y, x + 2, y + 8, ACCENT);
        g.drawString(font, text, x + 5, y, ACCENT, false);
    }

    public static void row(GuiGraphics g, int x, int y, int w, int h, boolean selected, boolean hovered) {
        if (selected) g.fill(x, y, x + w, y + h, SELECTED);
        else if (hovered) g.fill(x, y, x + w, y + h, CARD_HOVER);
    }

    public static void progress(GuiGraphics g, int x, int y, int w, int h, float fraction, int color) {
        g.fill(x, y, x + w, y + h, 0xFF101216);
        int filled = Math.round(w * Math.max(0, Math.min(1, fraction)));
        if (filled > 0) g.fill(x, y, x + filled, y + h, color);
    }

    /** A small card with a dim label on top and a large value below. */
    public static void stat(GuiGraphics g, Font font, int x, int y, int w, int h, Component label, Component value, int color) {
        card(g, x, y, w, h);
        g.drawString(font, fit(font, label, w - 8), x + 5, y + 5, TEXT_DIM, false);
        g.drawString(font, fit(font, value, w - 8), x + 5, y + h - 13, color, false);
    }

    /** A rounded-looking tag, e.g. an active law. Returns its width. */
    public static int chip(GuiGraphics g, Font font, Component text, int x, int y, int color) {
        int w = font.width(text) + 8;
        g.fill(x, y, x + w, y + 11, (color & 0x00FFFFFF) | 0x40000000);
        g.renderOutline(x, y, w, 11, color);
        g.drawString(font, text, x + 4, y + 2, color, false);
        return w;
    }

    public static void text(GuiGraphics g, Font font, Component text, int x, int y, int maxWidth, int color) {
        g.drawString(font, fit(font, text, maxWidth), x, y, color, false);
    }

    public static void textRight(GuiGraphics g, Font font, Component text, int right, int y, int color) {
        g.drawString(font, text, right - font.width(text), y, color, false);
    }

    /**
     * Draws text wrapped to the width, at most maxLines lines (the last one gets an ellipsis
     * if text is left over). Returns the height used.
     */
    public static int wrap(GuiGraphics g, Font font, Component text, int x, int y, int maxWidth, int maxLines, int color) {
        java.util.List<FormattedCharSequence> lines = font.split(text, maxWidth);
        int shown = Math.min(lines.size(), Math.max(1, maxLines));
        for (int i = 0; i < shown; i++) {
            FormattedCharSequence line = lines.get(i);
            if (i == shown - 1 && lines.size() > shown) {
                // Rebuild the last visible line from the rest of the text so the ellipsis sits at its end
                StringBuilder rest = new StringBuilder();
                for (int j = i; j < lines.size(); j++) {
                    lines.get(j).accept((index, style, cp) -> {
                        rest.appendCodePoint(cp);
                        return true;
                    });
                    rest.append(' ');
                }
                line = fit(font, Component.literal(rest.toString().trim()), maxWidth);
            }
            g.drawString(font, line, x, y + i * 10, color, false);
        }
        return shown * 10;
    }

    /** Number of lines text takes when wrapped to the width. */
    public static int lines(Font font, Component text, int maxWidth) {
        return font.split(text, maxWidth).size();
    }

    /** Cuts text to the width and adds an ellipsis when it doesn't fit. */
    public static FormattedCharSequence fit(Font font, Component text, int maxWidth) {
        if (font.width(text) <= maxWidth) return text.getVisualOrderText();
        FormattedText cut = font.substrByWidth(text, Math.max(0, maxWidth - font.width("…")));
        return Language.getInstance().getVisualOrder(FormattedText.composite(cut, FormattedText.of("…")));
    }

    public static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    /** "m:ss" for a number of seconds. */
    public static String time(int seconds) {
        seconds = Math.max(0, seconds);
        return (seconds / 60) + ":" + String.format("%02d", seconds % 60);
    }

    public static int money(int amount) {
        return amount < 0 ? BAD : GOOD;
    }
}
