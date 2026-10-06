package net.krona.politicsmod.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

/** A flat button in the PoliticsMod style. */
public class FlatButton extends AbstractButton {

    public enum Style {
        NORMAL, PRIMARY, DANGER, TAB
    }

    private final Runnable action;
    private final Style style;
    private boolean selected;

    public FlatButton(int x, int y, int w, int h, Component message, Style style, Runnable action) {
        super(x, y, w, h, message);
        this.style = style;
        this.action = action;
    }

    public FlatButton(int x, int y, int w, int h, Component message, Runnable action) {
        this(x, y, w, h, message, Style.NORMAL, action);
    }

    /** Width a button needs to show its label without cutting it. */
    public static int widthFor(Component label) {
        return Minecraft.getInstance().font.width(label) + 10;
    }

    public FlatButton selected(boolean selected) {
        this.selected = selected;
        return this;
    }

    public FlatButton tooltip(Component text) {
        setTooltip(text == null ? null : Tooltip.create(text));
        return this;
    }

    @Override
    public void onPress() {
        action.run();
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        Font font = Minecraft.getInstance().font;
        boolean hover = active && isHoveredOrFocused();
        int x = getX(), y = getY(), w = getWidth(), h = getHeight();
        int textColor;

        if (style == Style.TAB) {
            if (selected) {
                g.fill(x, y, x + w, y + h, UiKit.CARD);
                g.fill(x, y, x + 2, y + h, UiKit.ACCENT);
                textColor = UiKit.ACCENT;
            } else {
                if (hover) g.fill(x, y, x + w, y + h, UiKit.CARD_HOVER);
                textColor = hover ? UiKit.TEXT : UiKit.TEXT_DIM;
            }
            g.drawString(font, UiKit.fit(font, getMessage(), w - 14), x + 9, y + (h - 8) / 2, textColor, false);
            return;
        }

        int bg, border;
        switch (style) {
            case PRIMARY -> {
                bg = hover ? 0xFF8C6D22 : UiKit.ACCENT_DARK;
                border = UiKit.ACCENT;
                textColor = 0xFFFFF2D0;
            }
            case DANGER -> {
                bg = hover ? 0xFF7A2E33 : 0xFF5A2328;
                border = UiKit.BAD;
                textColor = 0xFFFFD8DA;
            }
            default -> {
                bg = hover ? UiKit.CARD_HOVER : UiKit.CARD;
                border = hover ? 0xFF4C5361 : UiKit.CARD_BORDER;
                textColor = UiKit.TEXT;
            }
        }
        if (!active) {
            bg = 0xFF1C1F26;
            border = 0xFF2A2F39;
            textColor = UiKit.TEXT_MUTED;
        }
        g.fill(x, y, x + w, y + h, bg);
        g.renderOutline(x, y, w, h, border);
        var text = UiKit.fit(font, getMessage(), w - 6);
        g.drawString(font, text, x + (w - font.width(text)) / 2, y + (h - 8) / 2, textColor, false);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
