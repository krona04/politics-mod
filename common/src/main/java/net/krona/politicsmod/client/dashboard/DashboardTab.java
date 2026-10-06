package net.krona.politicsmod.client.dashboard;

import net.krona.politicsmod.client.CountryDashboardScreen;
import net.krona.politicsmod.client.ClientPoliticsData;
import net.krona.politicsmod.network.CountryDetailsPayload;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;

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
