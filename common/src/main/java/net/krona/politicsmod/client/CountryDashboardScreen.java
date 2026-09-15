package net.krona.politicsmod.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.krona.politicsmod.network.ClaimLandPayload;
import net.krona.politicsmod.network.CountryDetailsPayload;
import net.krona.politicsmod.network.ManagePoliticsPayload;
import net.krona.politicsmod.network.ModNetworking;
import net.krona.politicsmod.network.RequestCountryDetailsPayload;
import net.krona.politicsmod.network.UpdateFlagPayload;
import net.krona.politicsmod.politics.EconomySummary;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

public class CountryDashboardScreen extends Screen {
    private static final int TAB_MAIN = 0;
    private static final int TAB_MAP = 1;
    private static final int TAB_CITIZENS = 2;
    private static final int TAB_DIPLOMACY = 3;
    private static final int TAB_ECONOMY = 4;

    private static final Pattern PLAYER_NAME = Pattern.compile("[A-Za-z0-9_]{3,16}");
    private static final int ROW_H = 12;
    private static final int CITIZEN_ROWS = 8;
    private static final int COUNTRY_ROWS = 7;
    /** Ticks to wait after a command before requesting fresh data (commands are not processed instantly). */
    private static final int REFRESH_DELAY_TICKS = 10;

    private final String countryName;
    private final int balance;
    private final List<String> cities;
    private String flagUrl;
    private final String playerRole;

    private int activeTab = TAB_MAIN;

    private final int bgW = 320;
    private final int bgH = 240;

    // Widgets per tab; visibility is toggled in updateVisibility()
    private final List<AbstractWidget> tabWidgets = new ArrayList<>();
    private final List<Integer> tabWidgetOwners = new ArrayList<>();
    private final List<Button> tabButtons = new ArrayList<>();

    // Overview
    private EditBox urlInput;
    private Button btnSaveUrl;

    // Map
    private Button btnCycleCity, btnSetCapital, btnAssignCity, btnAssignWild;
    private double mapOffsetX = 0;
    private double mapOffsetZ = 0;
    private boolean isDraggingMap = false;
    private final Set<ChunkPos> selectedChunks = new HashSet<>();
    private final int mapSize = 130;
    private final int scale = 10;
    private int selectedCityIndex = 0;

    // Citizens
    private int selectedMember = -1;
    private int citizenScroll = 0;
    private Button btnToggleRole, btnKick, btnInvite;
    private EditBox inviteInput;

    // Diplomacy
    private int selectedCountry = -1;
    private int countryScroll = 0;
    private Button btnAccept, btnReject, btnWar, btnAlliance, btnPeace;

    // Economy
    private Button btnTaxMinus5, btnTaxMinus1, btnTaxPlus1, btnTaxPlus5;

    private int refreshInTicks = -1;

    public CountryDashboardScreen(String countryName, int balance, List<String> cities, String flagUrl, String playerRole) {
        super(Component.literal(countryName));
        this.countryName = countryName;
        this.balance = balance;
        this.cities = cities;
        this.flagUrl = flagUrl;
        this.playerRole = playerRole;
    }

    private boolean isLeader() {
        return "LEADER".equals(playerRole);
    }

    private boolean canManageCitizens() {
        return "LEADER".equals(playerRole) || "MAYOR".equals(playerRole);
    }

    private <T extends AbstractWidget> T addTabWidget(int tab, T widget) {
        tabWidgets.add(widget);
        tabWidgetOwners.add(tab);
        return addRenderableWidget(widget);
    }

    @Override
    protected void init() {
        super.init();
        tabWidgets.clear();
        tabWidgetOwners.clear();
        tabButtons.clear();

        int cx = width / 2;
        int cy = height / 2;
        int bgX = cx - bgW / 2;
        int bgY = cy - bgH / 2;

        // ── Tabs ─────────────────────────────────────────────────────────
        String[] tabKeys = {"main", "map", "citizens", "diplomacy", "economy"};
        int tabW = 60, tabGap = 2;
        int tabsX = cx - (tabKeys.length * tabW + (tabKeys.length - 1) * tabGap) / 2;
        int tabsY = bgY + 25;
        for (int i = 0; i < tabKeys.length; i++) {
            final int tab = i;
            Button b = Button.builder(Component.translatable("gui.politicsmod.dashboard.tab." + tabKeys[i]), btn -> switchTab(tab))
                    .bounds(tabsX + i * (tabW + tabGap), tabsY, tabW, 20).build();
            tabButtons.add(addRenderableWidget(b));
        }

        int startY = bgY + 50;
        int left = cx - 150;

        // ── Overview ─────────────────────────────────────────────────────
        urlInput = addTabWidget(TAB_MAIN, new EditBox(font, cx - 100, startY + 92, 150, 20, Component.literal("URL")));
        urlInput.setMaxLength(256);
        urlInput.setValue(flagUrl == null ? "" : flagUrl);
        btnSaveUrl = addTabWidget(TAB_MAIN, Button.builder(Component.literal("OK"), b -> {
            flagUrl = urlInput.getValue();
            ModNetworking.toServer(new UpdateFlagPayload(countryName, flagUrl));
        }).bounds(cx + 55, startY + 92, 40, 20).build());

        // ── Map ──────────────────────────────────────────────────────────
        int controlsY = startY + mapSize + 7;
        btnCycleCity = addTabWidget(TAB_MAP, Button.builder(Component.translatable("gui.politicsmod.dashboard.target", "..."), b -> cycleTargetCity())
                .bounds(cx - 115, controlsY, 110, 20).build());
        btnSetCapital = addTabWidget(TAB_MAP, Button.builder(Component.translatable("gui.politicsmod.dashboard.make_capital"), b -> setCapital())
                .bounds(cx + 5, controlsY, 110, 20).build());
        btnAssignCity = addTabWidget(TAB_MAP, Button.builder(Component.translatable("gui.politicsmod.dashboard.assign"), b -> assignToSelectedCity())
                .bounds(cx - 115, controlsY + 25, 230, 20).build());
        btnAssignWild = addTabWidget(TAB_MAP, Button.builder(Component.translatable("gui.politicsmod.dashboard.wilderness"), b -> assignToWilderness())
                .bounds(cx - 115, controlsY + 50, 230, 20).build());

        // ── Citizens ─────────────────────────────────────────────────────
        int citizenButtonsY = startY + 14 + CITIZEN_ROWS * ROW_H + 6;
        btnToggleRole = addTabWidget(TAB_CITIZENS, Button.builder(Component.translatable("gui.politicsmod.citizens.make_mayor"), b -> toggleSelectedRole())
                .bounds(left, citizenButtonsY, 145, 20).build());
        btnKick = addTabWidget(TAB_CITIZENS, Button.builder(Component.translatable("gui.politicsmod.citizens.kick"), b -> kickSelected())
                .bounds(left + 155, citizenButtonsY, 145, 20).build());
        inviteInput = addTabWidget(TAB_CITIZENS, new EditBox(font, left, citizenButtonsY + 26, 205, 20, Component.translatable("gui.politicsmod.citizens.invite_hint")));
        inviteInput.setMaxLength(16);
        inviteInput.setHint(Component.translatable("gui.politicsmod.citizens.invite_hint").withStyle(ChatFormatting.DARK_GRAY));
        btnInvite = addTabWidget(TAB_CITIZENS, Button.builder(Component.translatable("gui.politicsmod.citizens.invite"), b -> invite())
                .bounds(left + 210, citizenButtonsY + 26, 90, 20).build());

        // ── Diplomacy ────────────────────────────────────────────────────
        btnAccept = addTabWidget(TAB_DIPLOMACY, Button.builder(Component.translatable("gui.politicsmod.diplomacy.accept"),
                b -> runCommand("politicsmod diplomacy accept")).bounds(left + 150, startY + 12, 72, 20).build());
        btnReject = addTabWidget(TAB_DIPLOMACY, Button.builder(Component.translatable("gui.politicsmod.diplomacy.reject"),
                b -> runCommand("politicsmod diplomacy reject")).bounds(left + 228, startY + 12, 72, 20).build());
        int dipButtonsY = startY + 50 + COUNTRY_ROWS * ROW_H + 6;
        btnWar = addTabWidget(TAB_DIPLOMACY, Button.builder(Component.translatable("gui.politicsmod.diplomacy.war_button", 0),
                b -> diplomacyAction("war")).bounds(left, dipButtonsY, 96, 20).build());
        btnAlliance = addTabWidget(TAB_DIPLOMACY, Button.builder(Component.translatable("gui.politicsmod.diplomacy.alliance_button"),
                b -> diplomacyAction("alliance")).bounds(left + 102, dipButtonsY, 96, 20).build());
        btnPeace = addTabWidget(TAB_DIPLOMACY, Button.builder(Component.translatable("gui.politicsmod.diplomacy.peace_button"),
                b -> diplomacyAction("peace")).bounds(left + 204, dipButtonsY, 96, 20).build());

        // ── Economy ──────────────────────────────────────────────────────
        int taxY = startY + 150;
        btnTaxMinus5 = addTabWidget(TAB_ECONOMY, Button.builder(Component.literal("-5"), b -> changeTax(-5)).bounds(cx - 150, taxY, 30, 20).build());
        btnTaxMinus1 = addTabWidget(TAB_ECONOMY, Button.builder(Component.literal("-1"), b -> changeTax(-1)).bounds(cx - 116, taxY, 30, 20).build());
        btnTaxPlus1 = addTabWidget(TAB_ECONOMY, Button.builder(Component.literal("+1"), b -> changeTax(1)).bounds(cx + 86, taxY, 30, 20).build());
        btnTaxPlus5 = addTabWidget(TAB_ECONOMY, Button.builder(Component.literal("+5"), b -> changeTax(5)).bounds(cx + 120, taxY, 30, 20).build());

        updateCityButtonText();
        updateVisibility();
    }

    // ── Tab switching and widget state ───────────────────────────────────

    private void switchTab(int tab) {
        activeTab = tab;
        if (tab >= TAB_CITIZENS) requestDetails();
        updateVisibility();
    }

    private void updateVisibility() {
        for (int i = 0; i < tabButtons.size(); i++) {
            tabButtons.get(i).active = i != activeTab;
        }
        for (int i = 0; i < tabWidgets.size(); i++) {
            tabWidgets.get(i).visible = tabWidgetOwners.get(i) == activeTab;
        }

        CountryDetailsPayload details = ClientPoliticsData.countryDetails;

        // Overview: only the leader can change the flag
        urlInput.visible &= isLeader();
        btnSaveUrl.visible &= isLeader();

        // Map
        btnCycleCity.visible &= canManageCitizens();
        btnSetCapital.visible &= isLeader();
        btnAssignCity.visible &= canManageCitizens();
        btnAssignWild.visible &= isLeader();

        // Citizens
        btnToggleRole.visible &= isLeader();
        btnKick.visible &= canManageCitizens();
        inviteInput.visible &= canManageCitizens();
        btnInvite.visible &= canManageCitizens();

        CountryDetailsPayload.Member member = selectedMember(details);
        boolean actionable = member != null && member.online() && !"LEADER".equals(member.role());
        btnKick.active = actionable;
        btnToggleRole.active = actionable;
        btnToggleRole.setMessage(Component.translatable(member != null && "MAYOR".equals(member.role())
                ? "gui.politicsmod.citizens.make_citizen" : "gui.politicsmod.citizens.make_mayor"));
        Tooltip onlineHint = member != null && !member.online()
                ? Tooltip.create(Component.translatable("gui.politicsmod.citizens.must_be_online")) : null;
        btnKick.setTooltip(onlineHint);
        btnToggleRole.setTooltip(onlineHint);
        btnInvite.active = PLAYER_NAME.matcher(inviteInput.getValue()).matches();

        // Diplomacy: every decision is made by the leader
        boolean hasPending = details != null && !details.pendingFrom().isEmpty();
        btnAccept.visible &= isLeader() && hasPending;
        btnReject.visible &= isLeader() && hasPending;
        btnWar.visible &= isLeader();
        btnAlliance.visible &= isLeader();
        btnPeace.visible &= isLeader();

        CountryDetailsPayload.Relation relation = selectedRelation(details);
        String status = relation != null ? relation.status() : "";
        btnWar.active = relation != null && !"WAR".equals(status);
        btnAlliance.active = relation != null && "NEUTRAL".equals(status);
        btnPeace.active = relation != null && "WAR".equals(status);
        if (details != null) {
            btnWar.setMessage(Component.translatable("gui.politicsmod.diplomacy.war_button", details.warCost()));
        }

        // Economy
        boolean taxVisible = isLeader() && details != null;
        btnTaxMinus5.visible &= taxVisible;
        btnTaxMinus1.visible &= taxVisible;
        btnTaxPlus1.visible &= taxVisible;
        btnTaxPlus5.visible &= taxVisible;
    }

    @Override
    public void tick() {
        super.tick();
        if (refreshInTicks > 0 && --refreshInTicks == 0) {
            requestDetails();
        }
        updateVisibility();
    }

    private void requestDetails() {
        refreshInTicks = -1;
        ModNetworking.toServer(new RequestCountryDetailsPayload());
    }

    /**
     * GUI actions run the same commands as chat: the server checks permissions and prices
     * and sends the same messages, so the GUI never allows more than the command does.
     */
    private void runCommand(String command) {
        if (minecraft == null || minecraft.player == null) return;
        minecraft.player.connection.sendCommand(command);
        refreshInTicks = REFRESH_DELAY_TICKS;
    }

    private CountryDetailsPayload.Member selectedMember(CountryDetailsPayload details) {
        if (details == null || selectedMember < 0 || selectedMember >= details.members().size()) return null;
        return details.members().get(selectedMember);
    }

    private CountryDetailsPayload.Relation selectedRelation(CountryDetailsPayload details) {
        if (details == null || selectedCountry < 0 || selectedCountry >= details.relations().size()) return null;
        return details.relations().get(selectedCountry);
    }

    // ── Actions ──────────────────────────────────────────────────────────

    private void toggleSelectedRole() {
        CountryDetailsPayload.Member m = selectedMember(ClientPoliticsData.countryDetails);
        if (m == null) return;
        String newRole = "MAYOR".equals(m.role()) ? "citizen" : "mayor";
        runCommand("politicsmod setrole " + m.name() + " " + newRole);
    }

    private void kickSelected() {
        CountryDetailsPayload.Member m = selectedMember(ClientPoliticsData.countryDetails);
        if (m == null) return;
        runCommand("politicsmod kick " + m.name());
        selectedMember = -1;
    }

    private void invite() {
        String name = inviteInput.getValue().trim();
        if (!PLAYER_NAME.matcher(name).matches()) return;
        runCommand("politicsmod invite " + name);
        inviteInput.setValue("");
    }

    private void diplomacyAction(String action) {
        CountryDetailsPayload.Relation r = selectedRelation(ClientPoliticsData.countryDetails);
        if (r == null) return;
        runCommand("politicsmod diplomacy " + action + " " + r.country());
    }

    private void changeTax(int delta) {
        CountryDetailsPayload details = ClientPoliticsData.countryDetails;
        if (details == null) return;
        int rate = Math.max(0, Math.min(99, details.economy().federalTaxRate() + delta));
        if (rate != details.economy().federalTaxRate()) {
            runCommand("politicsmod tax federal " + rate);
        }
    }

    // ── Map ──────────────────────────────────────────────────────────────

    private void cycleTargetCity() { if (!cities.isEmpty()) { selectedCityIndex = (selectedCityIndex + 1) % cities.size(); updateCityButtonText(); } }
    private void setCapital() { if (!cities.isEmpty()) { ModNetworking.toServer(new ManagePoliticsPayload(0, cities.get(selectedCityIndex), "")); onClose(); } }
    private void updateCityButtonText() {
        if (cities.isEmpty()) {
            btnCycleCity.setMessage(Component.translatable("gui.politicsmod.dashboard.no_cities")); btnCycleCity.active = false; btnAssignCity.active = false; btnSetCapital.active = false;
        } else {
            String city = cities.get(selectedCityIndex);
            if (city.length() > 10) city = city.substring(0, 10) + "..";
            btnCycleCity.setMessage(Component.translatable("gui.politicsmod.dashboard.target", city)); btnCycleCity.active = true; btnAssignCity.active = true; btnSetCapital.active = true;
        }
    }
    private void assignToSelectedCity() {
        if (selectedChunks.isEmpty() || cities.isEmpty()) return;
        String target = cities.get(selectedCityIndex);
        for (ChunkPos pos : selectedChunks) ModNetworking.toServer(new ClaimLandPayload(pos.getWorldPosition(), countryName, target, true));
        selectedChunks.clear();
    }
    private void assignToWilderness() {
        if (selectedChunks.isEmpty()) return;
        for (ChunkPos pos : selectedChunks) ModNetworking.toServer(new ClaimLandPayload(pos.getWorldPosition(), countryName, "", false));
        selectedChunks.clear();
    }

    // ── Rendering ────────────────────────────────────────────────────────

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        this.renderBackground(g, mx, my, pt);

        int cx = width / 2;
        int cy = height / 2;
        int bgY = cy - bgH / 2;
        int startY = bgY + 50;

        super.render(g, mx, my, pt);

        g.drawCenteredString(this.font, "§l" + countryName, cx, bgY + 10, 0xFFD700);

        switch (activeTab) {
            case TAB_MAIN -> renderMainTab(g, cx, startY);
            case TAB_MAP -> renderMapTab(g, mx, my, cx, startY);
            default -> {
                CountryDetailsPayload details = ClientPoliticsData.countryDetails;
                if (details == null) {
                    g.drawCenteredString(font, Component.translatable("gui.politicsmod.dashboard.loading"), cx, startY + 40, 0xAAAAAA);
                } else if (activeTab == TAB_CITIZENS) {
                    renderCitizensTab(g, mx, my, cx, startY, details);
                } else if (activeTab == TAB_DIPLOMACY) {
                    renderDiplomacyTab(g, mx, my, cx, startY, details);
                } else {
                    renderEconomyTab(g, cx, startY, details);
                }
            }
        }
    }

    private void renderMainTab(GuiGraphics g, int cx, int startY) {
        String roleKey = switch (playerRole) {
            case "LEADER" -> "gui.politicsmod.role.leader";
            case "MAYOR" -> "gui.politicsmod.role.mayor";
            case "GUEST" -> "gui.politicsmod.role.guest";
            default -> "gui.politicsmod.role.citizen";
        };
        g.drawCenteredString(font, Component.translatable("gui.politicsmod.info.role",
                Component.translatable(roleKey).withStyle(ChatFormatting.AQUA)), cx, startY - 2, 0xFFFFFF);

        g.drawCenteredString(font, Component.translatable("gui.politicsmod.dashboard.balance", "§a" + balance).getString(), cx, startY + 10, 0xFFFFFF);

        if (flagUrl != null && !flagUrl.isEmpty()) {
            ResourceLocation texture = FlagTextureManager.getTexture(flagUrl);
            int size = 64;
            if (texture != null) {
                RenderSystem.setShaderTexture(0, texture);
                g.blit(texture, cx - size / 2, startY + 24, 0, 0, size, size, size, size);
            } else {
                g.fill(cx - size / 2, startY + 24, cx + size / 2, startY + 24 + size, 0xFF555555);
                g.drawCenteredString(font, "...", cx, startY + 24 + size / 2 - 4, 0xFFFFFF);
            }
        } else {
            g.drawCenteredString(font, Component.translatable("gui.politicsmod.dashboard.no_flag"), cx, startY + 50, 0xAAAAAA);
        }

        if (isLeader()) {
            g.drawCenteredString(font, Component.translatable("gui.politicsmod.dashboard.paste_url"), cx, startY + 116, 0xAAAAAA);
        }

        int listY = startY + 132;
        g.drawString(font, Component.translatable("gui.politicsmod.dashboard.cities_list"), cx - 110, listY, 0xFFFFFF);
        if (cities.isEmpty()) {
            g.drawString(font, "-", cx - 100, listY + 12, 0xAAAAAA);
        } else {
            for (int i = 0; i < Math.min(cities.size(), 4); i++) {
                g.drawString(font, "• " + cities.get(i), cx - 100, listY + 12 + (i * 11), 0xAAAAAA);
            }
        }
    }

    private void renderCitizensTab(GuiGraphics g, int mx, int my, int cx, int startY, CountryDetailsPayload details) {
        int left = cx - 150;
        List<CountryDetailsPayload.Member> members = details.members();
        g.drawString(font, Component.translatable("gui.politicsmod.citizens.header", members.size()), left, startY, 0xFFD700);

        int listY = startY + 14;
        drawListFrame(g, left, listY, CITIZEN_ROWS);
        citizenScroll = clampScroll(citizenScroll, members.size(), CITIZEN_ROWS);
        for (int row = 0; row < CITIZEN_ROWS; row++) {
            int index = citizenScroll + row;
            if (index >= members.size()) break;
            CountryDetailsPayload.Member m = members.get(index);
            int y = listY + row * ROW_H;
            drawRowBackground(g, left, y, index == selectedMember, isInside(mx, my, left, y, 300, ROW_H));

            g.drawString(font, "●", left + 4, y + 2, m.online() ? 0x55FF55 : 0x555555);
            g.drawString(font, font.plainSubstrByWidth(m.name(), 180), left + 14, y + 2, m.online() ? 0xFFFFFF : 0xAAAAAA);

            Component role = Component.translatable("gui.politicsmod.role." + m.role().toLowerCase());
            g.drawString(font, role, left + 296 - font.width(role), y + 2, roleColor(m.role()));
        }
    }

    private void renderDiplomacyTab(GuiGraphics g, int mx, int my, int cx, int startY, CountryDetailsPayload details) {
        int left = cx - 150;

        // Incoming proposal
        if (details.pendingFrom().isEmpty()) {
            g.drawString(font, Component.translatable("gui.politicsmod.diplomacy.no_pending"), left, startY + 18, 0x777777);
        } else {
            String key = "ALLIANCE".equals(details.pendingType())
                    ? "gui.politicsmod.diplomacy.pending_alliance" : "gui.politicsmod.diplomacy.pending_peace";
            g.drawString(font, Component.translatable(key, details.pendingFrom()), left, startY + 18, 0xFFD700);
        }

        List<CountryDetailsPayload.Relation> relations = details.relations();
        g.drawString(font, Component.translatable("gui.politicsmod.diplomacy.relations_header"), left, startY + 38, 0xFFD700);

        int listY = startY + 50;
        drawListFrame(g, left, listY, COUNTRY_ROWS);
        if (relations.isEmpty()) {
            g.drawCenteredString(font, Component.translatable("gui.politicsmod.diplomacy.no_countries"), cx, listY + 30, 0x777777);
            return;
        }
        countryScroll = clampScroll(countryScroll, relations.size(), COUNTRY_ROWS);
        for (int row = 0; row < COUNTRY_ROWS; row++) {
            int index = countryScroll + row;
            if (index >= relations.size()) break;
            CountryDetailsPayload.Relation r = relations.get(index);
            int y = listY + row * ROW_H;
            drawRowBackground(g, left, y, index == selectedCountry, isInside(mx, my, left, y, 300, ROW_H));

            g.drawString(font, font.plainSubstrByWidth(r.country(), 200), left + 4, y + 2, 0xFFFFFF);
            Component status = Component.translatable("gui.politicsmod.diplomacy.status." + r.status().toLowerCase());
            g.drawString(font, status, left + 296 - font.width(status), y + 2, statusColor(r.status()));
        }
    }

    private void renderEconomyTab(GuiGraphics g, int cx, int startY, CountryDetailsPayload details) {
        EconomySummary e = details.economy();
        int left = cx - 140;
        int y = startY;

        g.drawString(font, Component.translatable("gui.politicsmod.economy.treasury", e.treasury(), e.maxBalance()), left, y, 0x55FF55);
        y += 11;
        g.drawString(font, Component.translatable("gui.politicsmod.info.vaults", e.vaults(), e.maxBalance()), left, y, 0xAAAAAA);
        y += 11;
        g.drawString(font, Component.translatable("gui.politicsmod.economy.chunks", e.chunks()), left, y, 0xAAAAAA);

        y += 16;
        g.drawString(font, Component.translatable("gui.politicsmod.economy.cycle_header"), left, y, 0xFFD700);
        y += 12;
        g.drawString(font, Component.translatable("gui.politicsmod.economy.tax_income", e.directIncome()), left + 8, y, 0x55FF55);
        y += 11;
        g.drawString(font, Component.translatable("gui.politicsmod.economy.federal_income", e.federalTaxIncome()), left + 8, y, 0x55FF55);
        y += 11;
        g.drawString(font, Component.translatable("gui.politicsmod.economy.embassy_income",
                e.embassyIncome(), e.activeEmbassies(), e.embassies()), left + 8, y, 0x55FF55);
        y += 11;
        g.drawString(font, Component.translatable("gui.politicsmod.info.upkeep", e.upkeep()), left + 8, y, 0xFF5555);
        if (e.tributeAmount() > 0) {
            y += 11;
            g.drawString(font, Component.translatable("gui.politicsmod.info.tribute_to", e.tributeTo(), e.tributeAmount()), left + 8, y, 0xAA0000);
        }
        y += 13;
        int net = e.net();
        g.drawString(font, Component.translatable("gui.politicsmod.info.net", (net >= 0 ? "+" : "") + net), left, y, net >= 0 ? 0x55FF55 : 0xFF5555);

        // Federal tax (the ± buttons are visible to the leader only)
        int taxY = startY + 150;
        Component tax = Component.translatable("gui.politicsmod.economy.federal_tax", e.federalTaxRate());
        g.drawCenteredString(font, tax, cx, taxY + 6, e.federalTaxRate() >= details.secedeThreshold() ? 0xFF5555 : 0xFFFFFF);
        if (e.federalTaxRate() >= details.secedeThreshold()) {
            g.drawCenteredString(font, Component.translatable("gui.politicsmod.economy.secede_warning", details.secedeThreshold()),
                    cx, taxY + 26, 0xFF5555);
        }
    }

    // ── Rendering helpers ────────────────────────────────────────────────

    private void drawListFrame(GuiGraphics g, int left, int top, int rows) {
        g.fill(left, top, left + 300, top + rows * ROW_H, 0x66000000);
        g.renderOutline(left - 1, top - 1, 302, rows * ROW_H + 2, 0xFF555555);
    }

    private void drawRowBackground(GuiGraphics g, int left, int y, boolean selected, boolean hovered) {
        if (selected) g.fill(left, y, left + 300, y + ROW_H, 0x8855AAFF);
        else if (hovered) g.fill(left, y, left + 300, y + ROW_H, 0x33FFFFFF);
    }

    private static boolean isInside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private static int clampScroll(int scroll, int size, int rows) {
        return Math.max(0, Math.min(scroll, Math.max(0, size - rows)));
    }

    private static int roleColor(String role) {
        return switch (role) {
            case "LEADER" -> 0xFFD700;
            case "MAYOR" -> 0x55FFFF;
            default -> 0xAAAAAA;
        };
    }

    private static int statusColor(String status) {
        return switch (status) {
            case "WAR" -> 0xFF5555;
            case "ALLIANCE" -> 0x55FF55;
            default -> 0xAAAAAA;
        };
    }

    private void renderMapTab(GuiGraphics g, int mouseX, int mouseY, int cx, int startY) {
        int mapX = cx - (mapSize / 2);
        int mapY = startY;
        g.fill(mapX - 2, mapY - 2, mapX + mapSize + 2, mapY + mapSize + 2, 0xFF5A4530);
        g.fill(mapX, mapY, mapX + mapSize, mapY + mapSize, 0xFFE8DCCA);
        g.enableScissor(mapX, mapY, mapX + mapSize, mapY + mapSize);
        var chunks = ClientPoliticsData.getChunks();
        if (this.minecraft.player != null) {
            ChunkPos playerChunk = this.minecraft.player.chunkPosition();
            int centerScreenX = mapX + (mapSize / 2);
            int centerScreenY = mapY + (mapSize / 2);
            for (var entry : chunks.entrySet()) {
                int color = entry.getValue();
                int renderColor = 0xAA000000 | (color & 0xFFFFFF);
                drawChunkOnMap(g, playerChunk, centerScreenX, centerScreenY, entry.getKey(), renderColor);
            }
            for (ChunkPos selected : selectedChunks) drawChunkOnMap(g, playerChunk, centerScreenX, centerScreenY, selected, 0x99FFFFFF);
            g.fill(centerScreenX - 2, centerScreenY - 2, centerScreenX + 3, centerScreenY + 3, 0xFFFF0000);
        }
        g.disableScissor();
        if (mouseX >= mapX && mouseX < mapX + mapSize && mouseY >= mapY && mouseY < mapY + mapSize && this.minecraft.player != null) {
            ChunkPos playerChunk = this.minecraft.player.chunkPosition();
            int centerScreenX = mapX + (mapSize / 2);
            int centerScreenY = mapY + (mapSize / 2);
            double relativeX = mouseX - centerScreenX - (mapOffsetX * scale);
            double relativeY = mouseY - centerScreenY - (mapOffsetZ * scale);
            int chunkDx = (int) Math.floor(relativeX / scale);
            int chunkDz = (int) Math.floor(relativeY / scale);
            ChunkPos hoveredChunk = new ChunkPos(playerChunk.x + chunkDx, playerChunk.z + chunkDz);
            if (chunks.containsKey(hoveredChunk)) {
                int col = chunks.get(hoveredChunk);
                Component tooltipText = (col == 0x00AA00) ?
                        Component.translatable("gui.politicsmod.map.country") :
                        Component.translatable("gui.politicsmod.map.city");
                g.renderTooltip(this.font, tooltipText, mouseX, mouseY);
            }
        }
    }

    private void drawChunkOnMap(GuiGraphics g, ChunkPos playerPos, int centerX, int centerY, ChunkPos targetPos, int color) {
        double dx = (targetPos.x - playerPos.x) + mapOffsetX;
        double dz = (targetPos.z - playerPos.z) + mapOffsetZ;
        int boxX = (int) (centerX + (dx * scale));
        int boxY = (int) (centerY + (dz * scale));
        if (boxX > centerX - mapSize && boxX < centerX + mapSize && boxY > centerY - mapSize && boxY < centerY + mapSize) {
            g.fill(boxX + 1, boxY + 1, boxX + scale - 1, boxY + scale - 1, color);
        }
    }

    // ── Input ────────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        if (button != 0) return false;

        int cx = width / 2;
        int cy = height / 2;
        int bgY = cy - bgH / 2;
        int startY = bgY + 50;
        int left = cx - 150;
        CountryDetailsPayload details = ClientPoliticsData.countryDetails;

        if (activeTab == TAB_MAP) {
            int mapX = cx - (mapSize / 2);
            int mapY = startY;
            if (isInside(mouseX, mouseY, mapX, mapY, mapSize, mapSize) && this.minecraft.player != null) {
                ChunkPos playerChunk = this.minecraft.player.chunkPosition();
                int centerScreenX = mapX + (mapSize / 2);
                int centerScreenY = mapY + (mapSize / 2);
                double relativeX = mouseX - centerScreenX - (mapOffsetX * scale);
                double relativeY = mouseY - centerScreenY - (mapOffsetZ * scale);
                int chunkDx = (int) Math.floor(relativeX / scale);
                int chunkDz = (int) Math.floor(relativeY / scale);
                ChunkPos clickedChunk = new ChunkPos(playerChunk.x + chunkDx, playerChunk.z + chunkDz);
                if (selectedChunks.contains(clickedChunk)) selectedChunks.remove(clickedChunk);
                else selectedChunks.add(clickedChunk);
                isDraggingMap = true;
                return true;
            }
        } else if (activeTab == TAB_CITIZENS && details != null) {
            int listY = startY + 14;
            if (isInside(mouseX, mouseY, left, listY, 300, CITIZEN_ROWS * ROW_H)) {
                int index = citizenScroll + (int) ((mouseY - listY) / ROW_H);
                selectedMember = index < details.members().size() ? index : -1;
                updateVisibility();
                return true;
            }
        } else if (activeTab == TAB_DIPLOMACY && details != null) {
            int listY = startY + 50;
            if (isInside(mouseX, mouseY, left, listY, 300, COUNTRY_ROWS * ROW_H)) {
                int index = countryScroll + (int) ((mouseY - listY) / ROW_H);
                selectedCountry = index < details.relations().size() ? index : -1;
                updateVisibility();
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int step = scrollY > 0 ? -1 : 1;
        if (activeTab == TAB_CITIZENS) {
            citizenScroll += step;
            return true;
        }
        if (activeTab == TAB_DIPLOMACY) {
            countryScroll += step;
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        isDraggingMap = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (activeTab == TAB_MAP && isDraggingMap) {
            mapOffsetX += dragX / scale;
            mapOffsetZ += dragY / scale;
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }
}
