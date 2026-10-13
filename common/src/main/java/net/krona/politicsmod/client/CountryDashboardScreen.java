package net.krona.politicsmod.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.krona.politicsmod.client.dashboard.CitizensTab;
import net.krona.politicsmod.client.dashboard.DashboardTab;
import net.krona.politicsmod.client.dashboard.DiplomacyTab;
import net.krona.politicsmod.client.dashboard.EconomyTab;
import net.krona.politicsmod.client.dashboard.MapTab;
import net.krona.politicsmod.client.dashboard.NewsTab;
import net.krona.politicsmod.client.dashboard.SettlementsTab;
import net.krona.politicsmod.client.dashboard.OverviewTab;
import net.krona.politicsmod.client.dashboard.PoliticsTab;
import net.krona.politicsmod.client.ui.FlatButton;
import net.krona.politicsmod.client.ui.PoliticsScreen;
import net.krona.politicsmod.client.ui.UiKit;
import net.krona.politicsmod.network.CountryDetailsPayload;
import net.krona.politicsmod.network.ModNetworking;
import net.krona.politicsmod.network.RequestCountryDetailsPayload;
import net.krona.politicsmod.politics.RoleAction;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * The country dashboard (K): a sidebar with tabs on the left and the selected tab on the right.
 * Each tab lives in client/dashboard; this class draws the frame and switches between them.
 */
public class CountryDashboardScreen extends PoliticsScreen {

    private static final String[] TAB_KEYS = {"main", "map", "citizens", "politics", "diplomacy", "economy", "news", "settlements"};
    private static final int HEADER = 24;
    /** Ticks to wait after a command before requesting fresh data (commands are not processed instantly). */
    private static final int REFRESH_DELAY_TICKS = 10;
    /** Timers and sieges change on their own, so data is refreshed now and then while the screen is open. */
    private static final int PERIODIC_REFRESH_TICKS = 100;

    /** The tab that was open last time, so the dashboard reopens where the player left it. */
    private static int lastTab = 0;

    private final String countryName;
    private final int balance;
    private final List<String> cities;
    private String flagUrl;
    private final String playerRole;

    private final DashboardTab[] tabs;
    private int activeTab;
    private int px, py, pw, ph, sidebar;
    private int refreshInTicks = -1;
    private static final int SETTLEMENTS_TAB = 7;
    private boolean settlementsShown;
    private int periodic = 0;

    public CountryDashboardScreen(String countryName, int balance, List<String> cities, String flagUrl, String playerRole) {
        super(Component.literal(countryName));
        this.countryName = countryName;
        this.balance = balance;
        this.cities = cities;
        this.flagUrl = flagUrl;
        this.playerRole = playerRole;
        this.tabs = new DashboardTab[]{
                new OverviewTab(this), new MapTab(this), new CitizensTab(this), new PoliticsTab(this),
                new DiplomacyTab(this), new EconomyTab(this), new NewsTab(this), new SettlementsTab(this)};
        this.activeTab = Math.min(lastTab, tabs.length - 1);
    }

    // ── Accessors for tabs ───────────────────────────────────────────────

    public String countryName() { return countryName; }

    private static boolean settlementsAvailable() {
        return ClientPoliticsData.countryDetails != null && ClientPoliticsData.countryDetails.settlementsEnabled();
    }
    public String playerRole() { return playerRole; }
    public List<String> cities() { return cities; }
    public String flagUrl() { return flagUrl; }
    public void setFlagUrl(String url) { this.flagUrl = url; }

    public boolean isLeader() {
        return "LEADER".equals(playerRole);
    }

    /** Uses the server's role rules once the details have arrived; falls back to the default rules before that. */
    public boolean can(RoleAction action) {
        CountryDetailsPayload details = ClientPoliticsData.countryDetails;
        if (details != null) return action.in(details.permissions());
        return "LEADER".equals(playerRole) || "MAYOR".equals(playerRole);
    }

    public int treasury() {
        CountryDetailsPayload details = ClientPoliticsData.countryDetails;
        return details != null ? details.economy().treasury() : balance;
    }

    public <T extends AbstractWidget> T addTabWidget(T widget) {
        return addRenderableWidget(widget);
    }

    /**
     * GUI actions run the same commands as chat: the server checks permissions and prices
     * and sends the same messages, so the GUI never allows more than the command does.
     */
    public void runCommand(String command) {
        if (minecraft == null || minecraft.player == null) return;
        minecraft.player.connection.sendCommand(command);
        refreshInTicks = REFRESH_DELAY_TICKS;
    }

    /** Asks for fresh data shortly, after the server has handled a packet. */
    public void refreshSoon() {
        refreshInTicks = REFRESH_DELAY_TICKS;
    }

    public void requestDetails() {
        refreshInTicks = -1;
        periodic = 0;
        ModNetworking.toServer(new RequestCountryDetailsPayload());
    }

    /** Commands take names in quotes when they contain spaces. */
    public static String quote(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    // ── Layout ───────────────────────────────────────────────────────────

    @Override
    protected void init() {
        super.init();
        // The panel fills small screens (GUI scale 3 on 1366x768 leaves 455x256) and stops growing on big ones
        pw = Math.min(470, width - 12);
        ph = Math.min(272, height - 12);
        px = (width - pw) / 2;
        py = (height - ph) / 2;

        // The sidebar is as wide as the longest tab name in the current language needs
        int longest = 0;
        for (String key : TAB_KEYS) {
            longest = Math.max(longest, font.width(Component.translatable("gui.politicsmod.dashboard.tab." + key)));
        }
        sidebar = Math.max(84, Math.min(112, longest + 26));

        // Colonies and villages only when MineColonies or Millénaire is on the server; that tab is the last one
        settlementsShown = settlementsAvailable();
        if (activeTab == SETTLEMENTS_TAB && !settlementsShown) activeTab = 0;
        for (int i = 0; i < tabs.length; i++) {
            if (i == SETTLEMENTS_TAB && !settlementsShown) continue;
            final int tab = i;
            addRenderableWidget(new FlatButton(px + 4, py + 40 + i * 18, sidebar - 8, 17,
                    Component.translatable("gui.politicsmod.dashboard.tab." + TAB_KEYS[i]), FlatButton.Style.TAB,
                    () -> switchTab(tab)).selected(i == activeTab));
        }
        addRenderableWidget(new FlatButton(px + 6, py + ph - 21, sidebar - 12, 16,
                Component.translatable("gui.done"), this::onClose));

        int cx = px + sidebar + 8;
        tabs[activeTab].init(font, cx, py + HEADER + 6, pw - sidebar - 16, ph - HEADER - 12);
    }

    private void switchTab(int tab) {
        if (tab == activeTab) return;
        activeTab = tab;
        lastTab = tab;
        requestDetails();
        rebuildWidgets();
    }

    @Override
    public void tick() {
        super.tick();
        if (refreshInTicks > 0 && --refreshInTicks == 0) requestDetails();
        if (++periodic >= PERIODIC_REFRESH_TICKS) requestDetails();
        if (settlementsShown != settlementsAvailable()) rebuildWidgets();
        tabs[activeTab].update();
    }

    // ── Rendering ────────────────────────────────────────────────────────

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(g, mouseX, mouseY, partialTick);
        UiKit.panel(g, px, py, pw, ph);
        g.fill(px + 1, py + 1, px + sidebar, py + ph - 1, UiKit.SIDEBAR);
        g.fill(px + sidebar, py + 1, px + sidebar + 1, py + ph - 1, UiKit.PANEL_BORDER);

        // Sidebar header: flag, name and role
        ResourceLocation flag = flagUrl == null || flagUrl.isEmpty() ? null : FlagTextureManager.getTexture(flagUrl);
        if (flag != null) {
            RenderSystem.setShaderTexture(0, flag);
            g.blit(flag, px + 7, py + 8, 0, 0, 22, 15, 22, 15);
        } else {
            g.fill(px + 7, py + 8, px + 29, py + 23, 0xFF2A2F39);
            g.drawCenteredString(font, "⚑", px + 18, py + 12, UiKit.TEXT_MUTED);
        }
        g.renderOutline(px + 6, py + 7, 24, 17, UiKit.CARD_BORDER);
        UiKit.text(g, font, Component.literal(countryName).withStyle(ChatFormatting.BOLD), px + 34, py + 11, sidebar - 38, UiKit.ACCENT);
        UiKit.text(g, font, roleName(playerRole), px + 7, py + 26, sidebar - 14, UiKit.TEXT_DIM);
        g.fill(px + 6, py + 36, px + sidebar - 6, py + 37, UiKit.PANEL_BORDER);

        // Content header: tab title and money; the chips give way when the title needs the room
        int cx = px + sidebar + 8;
        Component title = tabs[activeTab].title().copy().withStyle(ChatFormatting.BOLD);
        g.drawString(font, title, cx, py + 8, UiKit.TEXT, false);
        int right = px + pw - 8;
        int left = cx + font.width(title) + 8;
        Component wallet = Component.translatable("gui.politicsmod.dashboard.wallet_chip", ClientPoliticsData.myWallet);
        if (right - font.width(wallet) - 8 >= left) {
            right -= UiKit.chip(g, font, wallet, right - font.width(wallet) - 8, py + 6, UiKit.INFO) + 4;
        }
        int t = treasury();
        Component treasuryChip = Component.translatable("gui.politicsmod.dashboard.treasury_chip", t);
        if (right - font.width(treasuryChip) - 8 >= left) {
            UiKit.chip(g, font, treasuryChip, right - font.width(treasuryChip) - 8, py + 6, t < 0 ? UiKit.BAD : UiKit.GOOD);
        }
        g.fill(cx, py + HEADER - 2, px + pw - 8, py + HEADER - 1, UiKit.PANEL_BORDER);

        tabs[activeTab].render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        tabs[activeTab].renderOverlay(g, mouseX, mouseY);
    }

    public static Component roleName(String role) {
        return Component.translatable(switch (role) {
            case "LEADER" -> "gui.politicsmod.role.leader";
            case "MAYOR" -> "gui.politicsmod.role.mayor";
            case "GUEST" -> "gui.politicsmod.role.guest";
            default -> "gui.politicsmod.role.citizen";
        });
    }

    // ── Input ────────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        return tabs[activeTab].mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        boolean handled = tabs[activeTab].mouseReleased(mx, my, button);
        return super.mouseReleased(mx, my, button) || handled;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (tabs[activeTab].mouseDragged(mx, my, button, dx, dy)) return true;
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        if (tabs[activeTab].mouseScrolled(mx, my, scrollY)) return true;
        return super.mouseScrolled(mx, my, scrollX, scrollY);
    }

}
