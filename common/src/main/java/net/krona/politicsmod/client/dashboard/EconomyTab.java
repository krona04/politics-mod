package net.krona.politicsmod.client.dashboard;

import net.krona.politicsmod.client.CountryDashboardScreen;
import net.krona.politicsmod.client.ui.FlatButton;
import net.krona.politicsmod.client.ui.UiKit;
import net.krona.politicsmod.network.CountryDetailsPayload;
import net.krona.politicsmod.politics.EconomySummary;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/** Treasury forecast per cycle, tax rates, loans and debt. */
public class EconomyTab extends DashboardTab {
    private static final int[] STEPS = {-5, -1, 1, 5};
    private static final int TAX_ROW = 28;

    private int lw, rx, rw, loansTop;
    private final List<FlatButton> taxButtons = new ArrayList<>();
    private FlatButton btnLoanAccept, btnLoanReject;

    public EconomyTab(CountryDashboardScreen screen) {
        super(screen);
    }

    @Override
    public Component title() {
        return Component.translatable("gui.politicsmod.dashboard.tab.economy");
    }

    @Override
    protected void addWidgets() {
        lw = w * 56 / 100;
        rx = x + lw + 6;
        rw = w - lw - 6;
        taxButtons.clear();
        for (int row = 0; row < 2; row++) {
            boolean federal = row == 0;
            int ty = y + 22 + row * TAX_ROW;
            int bw = (rw - 9) / 4;
            for (int i = 0; i < STEPS.length; i++) {
                int step = STEPS[i];
                taxButtons.add(add(new FlatButton(rx + i * (bw + 3), ty, bw, 13,
                        Component.literal((step > 0 ? "+" : "") + step), () -> changeTax(federal, step))));
            }
        }
        loansTop = y + 22 + 2 * TAX_ROW + 2;

        Component accept = Component.translatable("gui.politicsmod.diplomacy.accept");
        Component reject = Component.translatable("gui.politicsmod.diplomacy.reject");
        int by = y + h - 15;
        int aw = Math.max(FlatButton.widthFor(accept), (rw - 4) / 2);
        btnLoanAccept = add(new FlatButton(rx, by, aw, 15, accept, FlatButton.Style.PRIMARY, () -> screen.runCommand("politicsmod loan accept")));
        btnLoanReject = add(new FlatButton(rx + aw + 4, by, rw - aw - 4, 15, reject, () -> screen.runCommand("politicsmod loan reject")));
    }

    private void changeTax(boolean federal, int delta) {
        CountryDetailsPayload d = details();
        if (d == null) return;
        int current = federal ? d.economy().federalTaxRate() : d.economy().incomeTaxRate();
        int rate = Math.max(0, Math.min(federal ? 99 : 100, current + delta));
        if (rate != current) screen.runCommand("politicsmod tax " + (federal ? "federal " : "income ") + rate);
    }

    @Override
    public void update() {
        CountryDetailsPayload d = details();
        boolean leader = screen.isLeader() && d != null;
        for (FlatButton b : taxButtons) b.visible = leader;
        boolean offer = leader && !d.loanOffer().lender().isEmpty();
        btnLoanAccept.visible = offer;
        btnLoanReject.visible = offer;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        CountryDetailsPayload d = details();
        if (d == null) {
            loading(g);
            return;
        }
        renderForecast(g, d);
        renderTaxes(g, d);
        renderLoans(g, d);
    }

    private void renderForecast(GuiGraphics g, CountryDetailsPayload d) {
        EconomySummary e = d.economy();
        UiKit.section(g, font, Component.translatable("gui.politicsmod.economy.cycle_header"), x, y);
        UiKit.card(g, x, y + 11, lw, h - 11);
        g.enableScissor(x + 1, y + 12, x + lw - 1, y + h - 1);
        int ly = y + 15;

        ly = line(g, ly, Component.translatable("gui.politicsmod.overview.treasury"),
                Component.literal(e.treasury() + " / " + e.maxBalance() + "$"), UiKit.money(e.treasury()), UiKit.TEXT);
        UiKit.text(g, font, Component.translatable("gui.politicsmod.economy.chunks", e.chunks()), x + 5, ly, lw - 10, UiKit.TEXT_MUTED);
        ly += 10;
        UiKit.text(g, font, Component.translatable("gui.politicsmod.economy.vaults", e.vaults()), x + 5, ly, lw - 10, UiKit.TEXT_MUTED);
        ly += 13;

        ly = money(g, ly, "gui.politicsmod.economy.line_tax", e.directIncome(), true);
        ly = money(g, ly, "gui.politicsmod.economy.line_federal", e.federalTaxIncome(), true);
        ly = money(g, ly, "gui.politicsmod.economy.line_embassies", e.embassyIncome(), true);
        if (e.settlementIncome() > 0) ly = money(g, ly, "gui.politicsmod.economy.line_settlements", e.settlementIncome(), true);
        ly = money(g, ly, "gui.politicsmod.economy.line_upkeep", e.upkeep(), false);
        if (e.tributeAmount() > 0) ly = money(g, ly, "gui.politicsmod.economy.line_tribute", e.tributeAmount(), false);
        if (e.salaryCost() > 0) {
            ly = money(g, ly, "gui.politicsmod.economy.line_salaries", e.salaryCost(), false);
            ly = money(g, ly, "gui.politicsmod.economy.line_income_tax", e.incomeTaxReturn(), true);
        }
        if (e.loanPayment() > 0) ly = money(g, ly, "gui.politicsmod.economy.line_loan_payment", e.loanPayment(), false);
        if (e.loanIncome() > 0) ly = money(g, ly, "gui.politicsmod.economy.line_loan_income", e.loanIncome(), true);

        g.fill(x + 5, ly + 1, x + lw - 5, ly + 2, UiKit.CARD_BORDER);
        ly += 5;
        int net = e.net();
        ly = line(g, ly, Component.translatable("gui.politicsmod.overview.net"), Component.literal((net >= 0 ? "+" : "") + net + "$"),
                UiKit.money(net), UiKit.TEXT) + 3;

        if (e.treasury() < 0) {
            ly += UiKit.wrap(g, font, Component.translatable("gui.politicsmod.economy.bankrupt_warning"), x + 5, ly, lw - 10, 3, UiKit.BAD) + 2;
        }
        if (e.federalTaxRate() >= d.secedeThreshold()) {
            UiKit.wrap(g, font, Component.translatable("gui.politicsmod.economy.secede_warning", d.secedeThreshold()), x + 5, ly, lw - 10, 3, UiKit.WARN);
        }
        g.disableScissor();
    }

    private int money(GuiGraphics g, int ly, String key, int amount, boolean income) {
        return line(g, ly, Component.translatable(key), Component.literal((income ? "+" : "−") + amount + "$"),
                income ? UiKit.GOOD : UiKit.BAD, UiKit.TEXT_DIM);
    }

    /** A label on the left and a value on the right; the label gets whatever room the value leaves. */
    private int line(GuiGraphics g, int ly, Component label, Component value, int valueColor, int labelColor) {
        int vw = font.width(value);
        UiKit.text(g, font, label, x + 5, ly, lw - vw - 16, labelColor);
        UiKit.textRight(g, font, value, x + lw - 5, ly, valueColor);
        return ly + 10;
    }

    private void renderTaxes(GuiGraphics g, CountryDetailsPayload d) {
        EconomySummary e = d.economy();
        UiKit.section(g, font, Component.translatable("gui.politicsmod.economy.taxes"), rx, y);
        Component federal = Component.translatable("gui.politicsmod.economy.federal_tax", e.federalTaxRate());
        UiKit.text(g, font, federal, rx, y + 12, rw, e.federalTaxRate() >= d.secedeThreshold() ? UiKit.BAD : UiKit.TEXT);
        Component income = Component.translatable("gui.politicsmod.economy.income_tax", e.incomeTaxRate());
        UiKit.text(g, font, income, rx, y + 12 + TAX_ROW, rw, UiKit.TEXT);
    }

    private void renderLoans(GuiGraphics g, CountryDetailsPayload d) {
        UiKit.section(g, font, Component.translatable("gui.politicsmod.economy.loans"), rx, loansTop);
        int ly = loansTop + 12;
        CountryDetailsPayload.LoanOfferInfo offer = d.loanOffer();
        int bottom = y + h - (offer.lender().isEmpty() ? 0 : 18);

        if (d.loans().isEmpty() && offer.lender().isEmpty()) {
            // No loans: explain how to get one, in as many lines as fit
            UiKit.wrap(g, font, Component.translatable("gui.politicsmod.economy.loan_hint", d.maxLoanInterest(), d.maxLoanCycles()),
                    rx, ly, rw, (bottom - ly) / 10, UiKit.TEXT_MUTED);
            return;
        }
        int offerH = offer.lender().isEmpty() ? 0 : 34;
        for (CountryDetailsPayload.LoanInfo l : d.loans()) {
            if (ly + 21 > bottom - offerH) break;
            UiKit.card(g, rx, ly, rw, 21);
            g.fill(rx, ly, rx + 2, ly + 21, l.owe() ? UiKit.BAD : UiKit.GOOD);
            UiKit.text(g, font, Component.translatable(l.owe() ? "gui.politicsmod.economy.loan_owe" : "gui.politicsmod.economy.loan_owed", l.other()),
                    rx + 5, ly + 2, rw - 8, UiKit.TEXT);
            UiKit.text(g, font, Component.translatable("gui.politicsmod.economy.loan_line", l.remaining(), l.installment()),
                    rx + 5, ly + 11, rw - 8, UiKit.TEXT_DIM);
            ly += 23;
        }
        if (!offer.lender().isEmpty()) {
            int oy = bottom - offerH;
            UiKit.card(g, rx, oy, rw, offerH - 2);
            g.fill(rx, oy, rx + 2, oy + offerH - 2, UiKit.ACCENT);
            UiKit.text(g, font, Component.translatable("gui.politicsmod.economy.loan_offer", offer.lender()), rx + 5, oy + 3, rw - 8, UiKit.ACCENT);
            UiKit.text(g, font, Component.translatable("gui.politicsmod.economy.loan_offer_terms", offer.amount(), offer.interest(), offer.cycles()),
                    rx + 5, oy + 12, rw - 8, UiKit.TEXT_DIM);
            UiKit.text(g, font, Component.translatable("gui.politicsmod.economy.loan_offer_allies"), rx + 5, oy + 21, rw - 8, UiKit.TEXT_MUTED);
        }
    }
}
