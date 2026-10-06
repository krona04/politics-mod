package net.krona.politicsmod.client;

import net.krona.politicsmod.politics.RoleAction;
import net.krona.politicsmod.market.MarketService;
import net.krona.politicsmod.network.MarketActionPayload;
import net.krona.politicsmod.network.MarketDataPayload;
import net.krona.politicsmod.network.ModNetworking;
import net.krona.politicsmod.client.ui.FlatButton;
import net.krona.politicsmod.client.ui.UiKit;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Trade Warehouse screen. Data comes from ClientPoliticsData.marketData, actions go through MarketActionPayload. */
public class MarketScreen extends net.krona.politicsmod.client.ui.PoliticsScreen {
    private static final int TAB_MARKET = 0;
    private static final int TAB_MINE = 1;

    private static final int BG_W = 324;
    private static final int BG_H = 244;
    private static final int ROW_H = 22;
    private static final int PAGE_SIZE = 6;

    private int activeTab = TAB_MARKET;
    private int page = 0;
    private long selectedId = -1;

    private FlatButton btnTabMarket, btnTabMine, btnPrev, btnNext, btnAction, btnSell, btnMode;
    /** Market tab: pay from the treasury instead of the wallet. Our listings tab: sell on behalf of the country. */
    private boolean stateMode = false;
    private EditBox priceInput;

    public MarketScreen() {
        super(Component.translatable("gui.politicsmod.market.title"));
    }

    private int left() { return width / 2 - 150; }
    private int top() { return height / 2 - BG_H / 2; }
    private int listTop() { return top() + 58; }

    @Override
    protected void init() {
        super.init();
        int cx = width / 2;
        int top = top();
        int left = left();

        Component tabBuy = Component.translatable("gui.politicsmod.market.tab.buy");
        Component tabMine = Component.translatable("gui.politicsmod.market.tab.mine");
        int tb = FlatButton.widthFor(tabBuy) + 6, tm = FlatButton.widthFor(tabMine) + 6;
        btnTabMarket = addRenderableWidget(new FlatButton(left, top + 24, tb, 16, tabBuy, FlatButton.Style.TAB, () -> switchTab(TAB_MARKET)));
        btnTabMine = addRenderableWidget(new FlatButton(left + tb + 4, top + 24, tm, 16, tabMine, FlatButton.Style.TAB, () -> switchTab(TAB_MINE)));

        int navY = listTop() + PAGE_SIZE * ROW_H + 5;
        btnPrev = addRenderableWidget(new FlatButton(left, navY, 16, 16, Component.literal("<"), () -> page--));
        btnNext = addRenderableWidget(new FlatButton(left + 66, navY, 16, 16, Component.literal(">"), () -> page++));
        int actionW = Math.max(FlatButton.widthFor(Component.translatable("gui.politicsmod.market.buy")),
                FlatButton.widthFor(Component.translatable("gui.politicsmod.market.cancel")));
        btnAction = addRenderableWidget(new FlatButton(left + 300 - actionW, navY, actionW, 16, Component.translatable("gui.politicsmod.market.buy"),
                FlatButton.Style.PRIMARY, this::doAction));
        int modeW = 0;
        for (String k : new String[]{"pay_wallet", "pay_treasury", "sell_personal", "sell_state"}) {
            modeW = Math.max(modeW, FlatButton.widthFor(Component.translatable("gui.politicsmod.market." + k)));
        }
        modeW = Math.min(modeW, 300 - actionW - 4 - 86);
        btnMode = addRenderableWidget(new FlatButton(left + 300 - actionW - 4 - modeW, navY, modeW, 16, Component.empty(), () -> stateMode = !stateMode));

        int sellY = navY + 22;
        Component sellLabel = Component.translatable("gui.politicsmod.market.sell");
        int sellW = FlatButton.widthFor(sellLabel);
        priceInput = addRenderableWidget(new EditBox(font, left + 151, sellY + 1, Math.max(40, 300 - sellW - 4 - 151), 14,
                Component.translatable("gui.politicsmod.market.price_hint")));
        priceInput.setMaxLength(7);
        priceInput.setFilter(s -> s.matches("\\d*"));
        priceInput.setHint(Component.translatable("gui.politicsmod.market.price_hint").withStyle(ChatFormatting.DARK_GRAY));
        btnSell = addRenderableWidget(new FlatButton(left + 300 - sellW, sellY, sellW, 16, sellLabel, FlatButton.Style.PRIMARY, this::sellHeld));

        updateWidgets();
    }

    private void switchTab(int tab) {
        activeTab = tab;
        stateMode = false;
        page = 0;
        selectedId = -1;
        updateWidgets();
    }

    /** Listings for the current tab: other countries' goods on Market, our own on Our Listings. */
    private List<MarketDataPayload.Entry> visibleEntries(MarketDataPayload data) {
        List<MarketDataPayload.Entry> result = new ArrayList<>();
        if (data == null) return result;
        for (MarketDataPayload.Entry e : data.entries()) {
            if ((activeTab == TAB_MINE) == e.ownedByMe()) result.add(e);
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
        return data != null && RoleAction.SPEND_TREASURY.in(data.permissions());
    }

    private void updateWidgets() {
        MarketDataPayload data = ClientPoliticsData.marketData;
        List<MarketDataPayload.Entry> entries = visibleEntries(data);
        int pages = Math.max(1, (entries.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        page = Math.max(0, Math.min(page, pages - 1));

        btnTabMarket.selected(activeTab == TAB_MARKET);
        btnTabMine.selected(activeTab == TAB_MINE);
        btnPrev.active = page > 0;
        btnNext.active = page < pages - 1;

        MarketDataPayload.Entry sel = selected(data);
        if (activeTab == TAB_MARKET) {
            if (stateMode && !canSpendTreasury(data)) stateMode = false;
            btnMode.active = canSpendTreasury(data);
            btnMode.setMessage(Component.translatable(stateMode ? "gui.politicsmod.market.pay_treasury" : "gui.politicsmod.market.pay_wallet"));
            btnMode.setTooltip(Tooltip.create(Component.translatable("gui.politicsmod.market.pay_hint")));

            btnAction.setMessage(Component.translatable("gui.politicsmod.market.buy"));
            Component reason = null;
            if (sel != null) {
                int funds = stateMode ? data.treasury() : data.wallet();
                if ("WAR".equals(sel.relation())) reason = Component.translatable("message.politicsmod.market.embargo", sel.seller());
                else if (sel.price() > funds) reason = Component.translatable(
                        stateMode ? "message.politicsmod.market.no_funds" : "message.politicsmod.wallet.no_funds", sel.price());
            }
            btnAction.active = sel != null && reason == null;
            btnAction.setTooltip(reason != null ? Tooltip.create(reason) : null);
        } else {
            boolean canSellForState = data != null && RoleAction.SELL_FOR_STATE.in(data.permissions());
            if (stateMode && !canSellForState) stateMode = false;
            btnMode.active = canSellForState;
            btnMode.setMessage(Component.translatable(stateMode ? "gui.politicsmod.market.sell_state" : "gui.politicsmod.market.sell_personal"));
            btnMode.setTooltip(Tooltip.create(Component.translatable("gui.politicsmod.market.sell_mode_hint")));
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
        ModNetworking.toServer(new MarketActionPayload(action, sel.id(), 0, stateMode));
        selectedId = -1;
    }

    private void sellHeld() {
        int price = parsePrice();
        if (price < 1 || heldItem().isEmpty()) return;
        ModNetworking.toServer(new MarketActionPayload(MarketService.ACTION_LIST, -1, price, stateMode));
        priceInput.setValue("");
    }

    // ── Rendering ────────────────────────────────────────────────────────

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
        super.renderBackground(g, mx, my, pt);
        int top = top();
        int left = left();
        UiKit.panel(g, left - 12, top, BG_W, BG_H);
        g.drawString(font, title.copy().withStyle(ChatFormatting.BOLD), left, top + 9, UiKit.TEXT, false);
        g.fill(left, top + 20, left + 300, top + 21, UiKit.PANEL_BORDER);
        UiKit.card(g, left, listTop(), 300, PAGE_SIZE * ROW_H);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        int cx = width / 2;
        int top = top();
        int left = left();

        MarketDataPayload data = ClientPoliticsData.marketData;
        if (data == null) return;

        Component fee = Component.translatable("gui.politicsmod.market.fee", data.feePercent());
        UiKit.textRight(g, font, fee, left + 300, top + 9, UiKit.TEXT_DIM);
        int chipX = left + 300;
        Component wallet = Component.translatable("gui.politicsmod.dashboard.wallet_chip", data.wallet());
        chipX -= font.width(wallet) + 8;
        UiKit.chip(g, font, wallet, chipX, top + 45 - 2, UiKit.INFO);
        if (!data.myCountry().isEmpty()) {
            Component treasury = Component.translatable("gui.politicsmod.dashboard.treasury_chip", data.treasury());
            chipX -= font.width(treasury) + 12;
            UiKit.chip(g, font, treasury, chipX, top + 45 - 2, UiKit.money(data.treasury()));
        }

        List<MarketDataPayload.Entry> entries = visibleEntries(data);
        int listTop = listTop();

        if (entries.isEmpty()) {
            String key = activeTab == TAB_MARKET ? "gui.politicsmod.market.empty" : "gui.politicsmod.market.mine_empty";
            g.drawCenteredString(font, Component.translatable(key), cx, listTop + PAGE_SIZE * ROW_H / 2 - 4, UiKit.TEXT_MUTED);
        }

        ItemStack hovered = ItemStack.EMPTY;
        int from = page * PAGE_SIZE;
        for (int row = 0; row < PAGE_SIZE && from + row < entries.size(); row++) {
            MarketDataPayload.Entry e = entries.get(from + row);
            int y = listTop + row * ROW_H;
            boolean hover = mx >= left && mx < left + 300 && my >= y && my < y + ROW_H;

            UiKit.row(g, left + 1, y, 298, ROW_H, e.id() == selectedId, hover);

            g.renderItem(e.stack(), left + 3, y + 3);
            g.renderItemDecorations(font, e.stack(), left + 3, y + 3);
            if (mx >= left + 3 && mx < left + 19 && my >= y + 3 && my < y + 19) hovered = e.stack();

            String name = font.plainSubstrByWidth(e.stack().getHoverName().getString(), 170);
            g.drawString(font, name, left + 24, y + 3, UiKit.TEXT, false);

            boolean war = "WAR".equals(e.relation());
            String sellerLabel = e.personal() ? e.sellerName() + " (" + e.seller() + ")" : e.seller();
            Component sub;
            if (activeTab == TAB_MINE) {
                sub = Component.translatable(e.personal() ? "gui.politicsmod.market.row_personal" : "gui.politicsmod.market.row_state");
            } else if (war) {
                sub = Component.translatable("gui.politicsmod.market.embargo_row", sellerLabel);
            } else {
                sub = Component.translatable(e.personal() ? "gui.politicsmod.market.seller_player" : "gui.politicsmod.market.seller", sellerLabel);
            }
            g.drawString(font, font.plainSubstrByWidth(sub.getString(), 190), left + 24, y + 12,
                    war ? UiKit.BAD : "ALLIANCE".equals(e.relation()) ? UiKit.GOOD : UiKit.TEXT_DIM, false);

            String price = e.price() + "$";
            g.drawString(font, price, left + 294 - font.width(price), y + 7, UiKit.ACCENT, false);
        }

        int pages = Math.max(1, (entries.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int navY = listTop + PAGE_SIZE * ROW_H + 5;
        g.drawCenteredString(font, Component.translatable("gui.politicsmod.market.page", page + 1, pages), left + 41, navY + 4, UiKit.TEXT_DIM);

        if (activeTab == TAB_MINE) {
            int sellY = navY + 22;
            UiKit.text(g, font, Component.translatable("gui.politicsmod.market.sell_header"), left, sellY + 4, 122, UiKit.TEXT);
            ItemStack held = heldItem();
            int iconX = left + 127;
            UiKit.card(g, iconX - 1, sellY - 1, 18, 18);
            if (!held.isEmpty()) {
                g.renderItem(held, iconX, sellY);
                g.renderItemDecorations(font, held, iconX, sellY);
                if (mx >= iconX && mx < iconX + 16 && my >= sellY && my < sellY + 16) hovered = held;
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
