package net.krona.politicsmod.client.dashboard;

import net.krona.politicsmod.client.CountryDashboardScreen;
import net.krona.politicsmod.client.ClientPoliticsData;
import net.krona.politicsmod.network.CountryDetailsPayload;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * One page of the country dashboard. Tabs keep their state (selection, scroll) while the screen
 * rebuilds its widgets, so init() may run many times.
 */
public abstract class DashboardTab {
    protected final CountryDashboardScreen screen;
    protected Font font;
    protected int x, y, w, h;

    protected DashboardTab(CountryDashboardScreen screen) {
        this.screen = screen;
    }

    public abstract Component title();

    /** Lays out the tab inside the content area and adds its widgets. */
    public final void init(Font font, int x, int y, int w, int h) {
        this.font = font;
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        addWidgets();
        update();
    }

    protected abstract void addWidgets();

    /** Refreshes which widgets are visible and active; called every tick. */
    public void update() {
    }

    /** Drawn under the widgets. */
    public abstract void render(GuiGraphics g, int mouseX, int mouseY, float partialTick);

    /** Drawn over the widgets (tooltips). */
    public void renderOverlay(GuiGraphics g, int mouseX, int mouseY) {
    }

    public boolean mouseClicked(double mx, double my, int button) {
        return false;
    }

    public boolean mouseReleased(double mx, double my, int button) {
        return false;
    }

    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        return false;
    }

    public boolean mouseScrolled(double mx, double my, double delta) {
        return false;
    }

    // ── Explanations on hover ────────────────────────────────────────────

    private record Hover(int x, int y, int w, int h, List<Component> lines) {
    }

    private static final int TOOLTIP_WIDTH = 230;

    /** Areas that explain themselves when hovered; collected while the tab renders, drawn over the widgets. */
    private final List<Hover> hovers = new ArrayList<>();

    /** Registers a tooltip for an area drawn this frame. */
    protected void hover(int x, int y, int w, int h, List<Component> lines) {
        if (lines != null && !lines.isEmpty()) hovers.add(new Hover(x, y, w, h, lines));
    }

    /** Called by the screen before the tab renders. */
    public final void beginRender() {
        hovers.clear();
    }

    /** Called by the screen after everything else: the tooltip of the area under the mouse, the last one drawn wins. */
    public final void renderHover(GuiGraphics g, int mx, int my) {
        for (int i = hovers.size() - 1; i >= 0; i--) {
            Hover h = hovers.get(i);
            if (mx >= h.x && mx < h.x + h.w && my >= h.y && my < h.y + h.h) {
                // Long explanations wrap instead of running off the screen
                List<net.minecraft.util.FormattedCharSequence> lines = new ArrayList<>();
                for (Component line : h.lines) lines.addAll(font.split(line, TOOLTIP_WIDTH));
                g.renderTooltip(font, lines, mx, my);
                return;
            }
        }
    }

    protected <T extends AbstractWidget> T add(T widget) {
        return screen.addTabWidget(widget);
    }

    protected CountryDetailsPayload details() {
        return ClientPoliticsData.countryDetails;
    }

    protected void loading(GuiGraphics g) {
        g.drawCenteredString(font, Component.translatable("gui.politicsmod.dashboard.loading"), x + w / 2, y + h / 2 - 4, 0xFF9AA0AA);
    }

    protected static int clampScroll(int scroll, int size, int rows) {
        return Math.max(0, Math.min(scroll, Math.max(0, size - rows)));
    }
}
