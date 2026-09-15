package net.krona.politicsmod.client;

import net.krona.politicsmod.market.MarketService;
import net.krona.politicsmod.network.MarketActionPayload;
import net.krona.politicsmod.network.MarketDataPayload;
import net.krona.politicsmod.network.ModNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Trade Warehouse screen. Data comes from ClientPoliticsData.marketData, actions go through MarketActionPayload. */
public class MarketScreen extends Screen {
    private static final int TAB_MARKET = 0;
    private static final int TAB_MINE = 1;

    private static final int BG_W = 320;
    private static final int BG_H = 236;
    private static final int ROW_H = 22;
    private static final int PAGE_SIZE = 6;

    private int activeTab = TAB_MARKET;
    private int page = 0;
    private long selectedId = -1;

    private Button btnTabMarket, btnTabMine, btnPrev, btnNext, btnAction, btnSell;
    private EditBox priceInput;

    public MarketScreen() {
        super(Component.translatable("gui.politicsmod.market.title"));
    }

    private int left() { return width / 2 - 150; }
    private int top() { return height / 2 - BG_H / 2; }
    private int listTop() { return top() + 56; }

    @Override
    protected void init() {
        super.init();
        int cx = width / 2;
        int top = top();
        int left = left();

        btnTabMarket = addRenderableWidget(Button.builder(Component.translatable("gui.politicsmod.market.tab.buy"), b -> switchTab(TAB_MARKET))
                .bounds(cx - 102, top + 20, 100, 20).build());
        btnTabMine = addRenderableWidget(Button.builder(Component.translatable("gui.politicsmod.market.tab.mine"), b -> switchTab(TAB_MINE))
                .bounds(cx + 2, top + 20, 100, 20).build());

        int navY = listTop() + PAGE_SIZE * ROW_H + 4;
        btnPrev = addRenderableWidget(Button.builder(Component.literal("<"), b -> page--).bounds(left, navY, 20, 20).build());
        btnNext = addRenderableWidget(Button.builder(Component.literal(">"), b -> page++).bounds(left + 80, navY, 20, 20).build());
        btnAction = addRenderableWidget(Button.builder(Component.translatable("gui.politicsmod.market.buy"), b -> doAction())
                .bounds(left + 200, navY, 100, 20).build());

        int sellY = navY + 24;
        priceInput = addRenderableWidget(new EditBox(font, left + 150, sellY, 70, 20, Component.translatable("gui.politicsmod.market.price_hint")));
        priceInput.setMaxLength(7);
        priceInput.setFilter(s -> s.matches("\\d*"));
        priceInput.setHint(Component.translatable("gui.politicsmod.market.price_hint").withStyle(ChatFormatting.DARK_GRAY));
        btnSell = addRenderableWidget(Button.builder(Component.translatable("gui.politicsmod.market.sell"), b -> sellHeld())
                .bounds(left + 226, sellY, 74, 20).build());

        updateWidgets();
    }

    private void switchTab(int tab) {
        activeTab = tab;
        page = 0;
        selectedId = -1;
        updateWidgets();
    }

    /** Listings for the current tab: other countries' goods on Market, our own on Our Listings. */
    private List<MarketDataPayload.Entry> visibleEntries(MarketDataPayload data) {
        List<MarketDataPayload.Entry> result = new ArrayList<>();
        if (data == null) return result;
        for (MarketDataPayload.Entry e : data.entries()) {
            boolean mine = e.seller().equals(data.myCountry());
            if ((activeTab == TAB_MINE) == mine) result.add(e);
        }
        return result;
    }

    private MarketDataPayload.Entry selected(MarketDataPayload data) {
        for (MarketDataPayload.Entry e : visibleEntries(data)) {
            if (e.id() == selectedId) return e;
        }
        return null;
    }

    private boolean canSpendTreasury(MarketDataPayload data) {
        return data != null && ("LEADER".equals(data.myRole()) || "MAYOR".equals(data.myRole()));
    }

    private void updateWidgets() {
        MarketDataPayload data = ClientPoliticsData.marketData;
        List<MarketDataPayload.Entry> entries = visibleEntries(data);
        int pages = Math.max(1, (entries.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        page = Math.max(0, Math.min(page, pages - 1));

        btnTabMarket.active = activeTab != TAB_MARKET;
        btnTabMine.active = activeTab != TAB_MINE;
        btnPrev.active = page > 0;
        btnNext.active = page < pages - 1;

        MarketDataPayload.Entry sel = selected(data);
        if (activeTab == TAB_MARKET) {
            btnAction.setMessage(Component.translatable("gui.politicsmod.market.buy"));
            Component reason = null;
            if (sel != null) {
                if (!canSpendTreasury(data)) reason = Component.translatable("message.politicsmod.market.buy_role");
                else if ("WAR".equals(sel.relation())) reason = Component.translatable("message.politicsmod.market.embargo", sel.seller());
                else if (sel.price() > data.treasury()) reason = Component.translatable("message.politicsmod.market.no_funds", sel.price());
            }
            btnAction.active = sel != null && reason == null;
            btnAction.setTooltip(reason != null ? Tooltip.create(reason) : null);
        } else {
            btnAction.setMessage(Component.translatable("gui.politicsmod.market.cancel"));
            btnAction.active = sel != null;
            btnAction.setTooltip(null);
        }

        boolean mineTab = activeTab == TAB_MINE;
        priceInput.visible = mineTab;
        btnSell.visible = mineTab;
        ItemStack held = heldItem();
        int price = parsePrice();
        btnSell.active = !held.isEmpty() && data != null && price >= 1 && price <= data.maxPrice();
    }

    private ItemStack heldItem() {
        return minecraft != null && minecraft.player != null ? minecraft.player.getMainHandItem() : ItemStack.EMPTY;
    }

    private int parsePrice() {
        try {
            return priceInput.getValue().isEmpty() ? 0 : Integer.parseInt(priceInput.getValue());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    @Override
    public void tick() {
        super.tick();
        updateWidgets();
    }

    // ── Actions ──────────────────────────────────────────────────────────

    private void doAction() {
        MarketDataPayload.Entry sel = selected(ClientPoliticsData.marketData);
        if (sel == null) return;
        int action = activeTab == TAB_MARKET ? MarketService.ACTION_BUY : MarketService.ACTION_CANCEL;
        ModNetworking.toServer(new MarketActionPayload(action, sel.id(), 0));
        selectedId = -1;
    }

    private void sellHeld() {
        int price = parsePrice();
        if (price < 1 || heldItem().isEmpty()) return;
        ModNetworking.toServer(new MarketActionPayload(MarketService.ACTION_LIST, -1, price));
        priceInput.setValue("");
    }

    // ── Rendering ────────────────────────────────────────────────────────

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g, mx, my, pt);
        int cx = width / 2;
        int top = top();
        int left = left();

        g.fill(left - 8, top, left + 308, top + BG_H, 0xCC101010);
        g.renderOutline(left - 8, top, 316, BG_H, 0xFF8B6B3D);

        super.render(g, mx, my, pt);

        g.drawCenteredString(font, title.copy().withStyle(ChatFormatting.BOLD), cx, top + 7, 0xFFD700);

        MarketDataPayload data = ClientPoliticsData.marketData;
        if (data == null) return;

        g.drawString(font, Component.translatable("gui.politicsmod.market.treasury", data.treasury()), left, top + 45, 0x55FF55);
        Component fee = Component.translatable("gui.politicsmod.market.fee", data.feePercent());
        g.drawString(font, fee, left + 300 - font.width(fee), top + 45, 0xAAAAAA);

        List<MarketDataPayload.Entry> entries = visibleEntries(data);
        int listTop = listTop();
        g.fill(left, listTop, left + 300, listTop + PAGE_SIZE * ROW_H, 0x66000000);
        g.renderOutline(left - 1, listTop - 1, 302, PAGE_SIZE * ROW_H + 2, 0xFF555555);

        if (entries.isEmpty()) {
            String key = activeTab == TAB_MARKET ? "gui.politicsmod.market.empty" : "gui.politicsmod.market.mine_empty";
            g.drawCenteredString(font, Component.translatable(key), cx, listTop + PAGE_SIZE * ROW_H / 2 - 4, 0x777777);
        }

        ItemStack hovered = ItemStack.EMPTY;
        int from = page * PAGE_SIZE;
        for (int row = 0; row < PAGE_SIZE && from + row < entries.size(); row++) {
            MarketDataPayload.Entry e = entries.get(from + row);
            int y = listTop + row * ROW_H;
            boolean hover = mx >= left && mx < left + 300 && my >= y && my < y + ROW_H;

            if (e.id() == selectedId) g.fill(left, y, left + 300, y + ROW_H, 0x8855AAFF);
            else if (hover) g.fill(left, y, left + 300, y + ROW_H, 0x33FFFFFF);

            g.renderItem(e.stack(), left + 3, y + 3);
            g.renderItemDecorations(font, e.stack(), left + 3, y + 3);
            if (mx >= left + 3 && mx < left + 19 && my >= y + 3 && my < y + 19) hovered = e.stack();

            String name = font.plainSubstrByWidth(e.stack().getHoverName().getString(), 170);
            g.drawString(font, name, left + 24, y + 2, 0xFFFFFF);

            if (activeTab == TAB_MARKET) {
                boolean war = "WAR".equals(e.relation());
                Component sub = war
                        ? Component.translatable("gui.politicsmod.market.embargo_row", e.seller())
                        : Component.translatable("gui.politicsmod.market.seller", e.seller());
                g.drawString(font, font.plainSubstrByWidth(sub.getString(), 190), left + 24, y + 12,
                        war ? 0xFF5555 : "ALLIANCE".equals(e.relation()) ? 0x55FF55 : 0x999999);
            }

            String price = e.price() + "$";
            g.drawString(font, price, left + 296 - font.width(price), y + 7, 0xFFD700);
        }

        int pages = Math.max(1, (entries.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int navY = listTop + PAGE_SIZE * ROW_H + 4;
        g.drawCenteredString(font, Component.translatable("gui.politicsmod.market.page", page + 1, pages), left + 50, navY + 6, 0xFFFFFF);

        if (activeTab == TAB_MINE) {
            int sellY = navY + 24;
            g.drawString(font, Component.translatable("gui.politicsmod.market.sell_header"), left, sellY + 6, 0xFFFFFF);
            ItemStack held = heldItem();
            int iconX = left + 126;
            g.fill(iconX - 1, sellY + 1, iconX + 17, sellY + 19, 0x66000000);
            if (!held.isEmpty()) {
                g.renderItem(held, iconX, sellY + 2);
                g.renderItemDecorations(font, held, iconX, sellY + 2);
                if (mx >= iconX && mx < iconX + 16 && my >= sellY + 2 && my < sellY + 18) hovered = held;
            }
        }

        if (!hovered.isEmpty()) {
            g.renderTooltip(font, hovered, mx, my);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        int left = left();
        int listTop = listTop();
        if (button == 0 && mouseX >= left && mouseX < left + 300
                && mouseY >= listTop && mouseY < listTop + PAGE_SIZE * ROW_H) {
            List<MarketDataPayload.Entry> entries = visibleEntries(ClientPoliticsData.marketData);
            int index = page * PAGE_SIZE + (int) ((mouseY - listTop) / ROW_H);
            selectedId = index < entries.size() ? entries.get(index).id() : -1;
            updateWidgets();
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        page += scrollY > 0 ? -1 : 1;
        updateWidgets();
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        // The market is live, so singleplayer should not pause
        return false;
    }
}
