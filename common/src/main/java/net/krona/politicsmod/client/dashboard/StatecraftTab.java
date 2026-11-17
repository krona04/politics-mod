package net.krona.politicsmod.client.dashboard;

import net.krona.politicsmod.client.CountryDashboardScreen;
import net.krona.politicsmod.client.ui.FlatButton;
import net.krona.politicsmod.client.ui.UiKit;
import net.krona.politicsmod.network.CountryDetailsPayload;
import net.krona.politicsmod.statecraft.Line;
import net.krona.politicsmod.statecraft.StatecraftService;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;

/**
 * Events waiting for an answer, decisions the leader can take and the country's goals, on three pages.
 * Every button runs the same command as the chat, so the server checks everything again.
 */
public class StatecraftTab extends DashboardTab {
    private static final int PAGE_EVENTS = 0, PAGE_DECISIONS = 1, PAGE_GOALS = 2;
    private static final int MAX_OPTIONS = 4;
    private static final int BTN_H = 15;
    private static final int ROW_H = 13;

    private int page = 0;
    private int eventIndex = 0;
    private int selectedDecision = 0, selectedGoal = 0;
    private int listScroll = 0, detailScroll = 0;
    private int cityIndex = 0;

    private final FlatButton[] pageButtons = new FlatButton[3];
    private final FlatButton[] optionButtons = new FlatButton[MAX_OPTIONS];
    private FlatButton btnPrev, btnNext, btnTake, btnCity;
    private int top, listW, detailX, detailW;

    public StatecraftTab(CountryDashboardScreen screen) {
        super(screen);
    }

    @Override
    public Component title() {
        return Component.translatable("gui.politicsmod.dashboard.tab.statecraft");
    }

    @Override
    protected void addWidgets() {
        String[] keys = {"gui.politicsmod.statecraft.page.events", "gui.politicsmod.statecraft.page.decisions",
                "gui.politicsmod.statecraft.page.goals"};
        int bx = x;
        for (int i = 0; i < 3; i++) {
            final int p = i;
            Component label = Component.translatable(keys[i]);
            // The events page shows a count later: leave room for it
            int bw = FlatButton.widthFor(i == PAGE_EVENTS ? Component.translatable("gui.politicsmod.statecraft.page.events_count", 9) : label) + 6;
            pageButtons[i] = add(new FlatButton(bx, y, bw, 14, label, FlatButton.Style.TAB, () -> {
                page = p;
                listScroll = 0;
                detailScroll = 0;
            }));
            bx += bw + 3;
        }
        top = y + 19;
        listW = Math.max(110, w * 42 / 100);
        detailX = x + listW + 6;
        detailW = w - listW - 6;

        // Event options, from the bottom up
        for (int i = 0; i < MAX_OPTIONS; i++) {
            final int option = i;
            optionButtons[i] = add(new FlatButton(x + 4, 0, w - 8, BTN_H, Component.empty(), FlatButton.Style.NORMAL,
                    () -> answer(option)));
        }
        btnPrev = add(new FlatButton(x + w - 31, top + 3, 13, 12, Component.literal("◂"), () -> eventIndex--));
        btnNext = add(new FlatButton(x + w - 16, top + 3, 13, 12, Component.literal("▸"), () -> eventIndex++));

        // Decision: [city ▸][Take]
        Component take = Component.translatable("gui.politicsmod.statecraft.take");
        int tw = FlatButton.widthFor(take) + 8;
        btnTake = add(new FlatButton(detailX + detailW - tw, y + h - BTN_H, tw, BTN_H, take, FlatButton.Style.PRIMARY, this::take));
        btnCity = add(new FlatButton(detailX, y + h - BTN_H, detailW - tw - 4, BTN_H, Component.empty(), () -> cityIndex++)
                .tooltip(Component.translatable("gui.politicsmod.politics.next_city")));
    }

    private StatecraftService.View view() {
        CountryDetailsPayload d = details();
        return d == null ? null : d.statecraft();
    }

    private StatecraftService.EventView event() {
        StatecraftService.View v = view();
        if (v == null || v.events().isEmpty()) return null;
        eventIndex = Math.floorMod(eventIndex, v.events().size());
        return v.events().get(eventIndex);
    }

    private StatecraftService.DecisionView decision() {
        StatecraftService.View v = view();
        if (v == null || v.decisions().isEmpty()) return null;
        selectedDecision = Math.max(0, Math.min(selectedDecision, v.decisions().size() - 1));
        return v.decisions().get(selectedDecision);
    }

    private StatecraftService.GoalView goal() {
        StatecraftService.View v = view();
        if (v == null || v.goals().isEmpty()) return null;
        selectedGoal = Math.max(0, Math.min(selectedGoal, v.goals().size() - 1));
        return v.goals().get(selectedGoal);
    }

    private String city(StatecraftService.DecisionView d) {
        if (d == null || !d.target() || d.cities().isEmpty()) return null;
        cityIndex = Math.floorMod(cityIndex, d.cities().size());
        return d.cities().get(cityIndex);
    }

    private void answer(int option) {
        StatecraftService.EventView e = event();
        if (e != null) screen.runCommand("politicsmod event answer " + e.id() + " " + (option + 1));
    }

    private void take() {
        StatecraftService.DecisionView d = decision();
        if (d == null) return;
        String city = city(d);
        screen.runCommand("politicsmod decision take " + d.id() + (city != null ? " " + city : ""));
    }

    @Override
    public void update() {
        StatecraftService.View v = view();
        for (int i = 0; i < 3; i++) pageButtons[i].selected(i == page);
        if (v != null) {
            pageButtons[PAGE_EVENTS].setMessage(v.events().isEmpty() ? Component.translatable("gui.politicsmod.statecraft.page.events")
                    : Component.translatable("gui.politicsmod.statecraft.page.events_count", v.events().size()));
        }

        StatecraftService.EventView e = page == PAGE_EVENTS ? event() : null;
        int options = e == null ? 0 : Math.min(MAX_OPTIONS, e.options().size());
        for (int i = 0; i < MAX_OPTIONS; i++) {
            FlatButton b = optionButtons[i];
            b.visible = i < options;
            if (!b.visible) continue;
            StatecraftService.OptionView o = e.options().get(i);
            b.setY(y + h - (options - i) * (BTN_H + 3) + 3);
            b.setMessage(Component.translatable(o.text(), screen.countryName(), e.city()));
            b.active = v.canAnswer() && o.allowed();
        }
        boolean several = e != null && v.events().size() > 1;
        btnPrev.visible = several;
        btnNext.visible = several;

        StatecraftService.DecisionView d = page == PAGE_DECISIONS ? decision() : null;
        btnTake.visible = d != null && v.canDecide();
        btnTake.active = d != null && d.state() == StatecraftService.AVAILABLE;
        String city = city(d);
        btnCity.visible = btnTake.visible && d.target();
        btnCity.active = d != null && d.cities().size() > 1;
        btnCity.setMessage(city == null ? Component.translatable("gui.politicsmod.dashboard.no_cities")
                : Component.translatable("gui.politicsmod.statecraft.city", city));
    }

    // ── Rendering ────────────────────────────────────────────────────────

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        StatecraftService.View v = view();
        if (v == null) {
            loading(g);
            return;
        }
        Component rank = Component.translatable("statecraft.politicsmod.rank." + switch (v.rank()) {
            case 1 -> "kingdom";
            case 2 -> "empire";
            default -> "principality";
        });
        int rw = font.width(rank) + 8;
        UiKit.chip(g, font, rank, x + w - rw, y + 1, UiKit.ACCENT);
        hover(x + w - rw, y + 1, rw, 11, List.of(Component.translatable("gui.politicsmod.statecraft.rank_hint")
                .withStyle(ChatFormatting.GRAY)));

        if (!v.enabled()) {
            UiKit.card(g, x, top, w, h - (top - y));
            UiKit.wrap(g, font, Component.translatable("message.politicsmod.statecraft.disabled"), x + 6, top + 6, w - 12, 3, UiKit.TEXT_DIM);
            return;
        }
        switch (page) {
            case PAGE_EVENTS -> renderEvents(g, mx, my, v);
            case PAGE_DECISIONS -> renderDecisions(g, mx, my, v);
            default -> renderGoals(g, mx, my, v);
        }
    }

    private void renderEvents(GuiGraphics g, int mx, int my, StatecraftService.View v) {
        StatecraftService.EventView e = event();
        int bottom = y + h;
        if (e == null) {
            UiKit.card(g, x, top, w, 26);
            UiKit.text(g, font, Component.translatable("gui.politicsmod.statecraft.no_events"), x + 6, top + 4, w - 12, UiKit.TEXT);
            UiKit.text(g, font, Component.translatable("gui.politicsmod.statecraft.no_events_hint"), x + 6, top + 15, w - 12, UiKit.TEXT_MUTED);
            int ry = top + 32;
            UiKit.section(g, font, Component.translatable("gui.politicsmod.statecraft.recent"), x, ry);
            ry += 12;
            if (v.recent().isEmpty()) {
                UiKit.text(g, font, Component.translatable("gui.politicsmod.statecraft.recent_none"), x + 4, ry, w - 8, UiKit.TEXT_MUTED);
            }
            for (StatecraftService.RecentView r : v.recent()) {
                if (ry + 11 > bottom) break;
                Component title = Component.translatable(r.title(), screen.countryName(), r.city()).withStyle(ChatFormatting.WHITE);
                Component choice = Component.translatable(r.option(), screen.countryName(), r.city());
                Component line = Component.translatable(r.timedOut() ? "gui.politicsmod.statecraft.recent_timeout"
                        : "gui.politicsmod.statecraft.recent_line", title, choice);
                UiKit.text(g, font, line, x + 4, ry, w - 8, UiKit.TEXT_DIM);
                ry += 11;
            }
            return;
        }

        int options = Math.min(MAX_OPTIONS, e.options().size());
        int cardBottom = bottom - options * (BTN_H + 3);
        UiKit.card(g, x, top, w, cardBottom - top);
        int tx = x + 6;
        int right = x + w - 6 - (btnPrev.visible ? 34 : 0);
        Component timer = Component.literal("⌛ " + UiKit.time(e.secondsLeft()));
        UiKit.textRight(g, font, timer, right, top + 5, e.secondsLeft() < 120 ? UiKit.BAD : UiKit.TEXT_DIM);
        right -= font.width(timer) + 6;
        if (btnPrev.visible) {
            // "1/2" sits in the title row next to the timer: below it the description runs under the arrows
            Component count = Component.literal((eventIndex + 1) + "/" + v.events().size());
            UiKit.textRight(g, font, count, right, top + 5, UiKit.TEXT_MUTED);
            right -= font.width(count) + 6;
        }
        if (!e.city().isEmpty()) {
            Component cityChip = Component.literal(e.city());
            int cw = font.width(cityChip) + 8;
            if (right - cw > tx + 40) {
                UiKit.chip(g, font, cityChip, right - cw, top + 3, UiKit.INFO);
                right -= cw + 4;
            }
        }
        UiKit.text(g, font, Component.translatable(e.title(), screen.countryName(), e.city()).withStyle(ChatFormatting.BOLD),
                tx, top + 5, right - tx, UiKit.ACCENT);
        int descTop = top + 18;
        int maxLines = Math.max(1, (cardBottom - 6 - descTop) / 10);
        UiKit.wrap(g, font, Component.translatable(e.desc(), screen.countryName(), e.city()), tx, descTop, w - 12, maxLines, UiKit.TEXT);
        hover(x, top, w, cardBottom - top, List.of(Component.translatable(e.desc(), screen.countryName(), e.city())));

        if (!v.canAnswer()) {
            UiKit.textRight(g, font, Component.translatable("gui.politicsmod.statecraft.leader_answers"), x + w - 6, cardBottom - 11, UiKit.TEXT_MUTED);
        }
        // What each option does, on hover
        for (int i = 0; i < options; i++) {
            FlatButton b = optionButtons[i];
            StatecraftService.OptionView o = e.options().get(i);
            List<Component> tip = new ArrayList<>();
            tip.add(Component.translatable(o.text(), screen.countryName(), e.city()).withStyle(ChatFormatting.WHITE));
            if (o.effects().isEmpty()) tip.add(Component.translatable("gui.politicsmod.statecraft.no_effect").withStyle(ChatFormatting.GRAY));
            for (Line l : o.effects()) tip.add(l.styled());
            if (!o.allowed()) tip.add(Component.translatable("message.politicsmod.event.option_locked").withStyle(ChatFormatting.RED));
            hover(b.getX(), b.getY(), b.getWidth(), b.getHeight(), tip);
        }
    }

    private void renderDecisions(GuiGraphics g, int mx, int my, StatecraftService.View v) {
        List<StatecraftService.DecisionView> list = v.decisions();
        int listH = y + h - top;
        UiKit.card(g, x, top, listW, listH);
        if (list.isEmpty()) {
            UiKit.wrap(g, font, Component.translatable("gui.politicsmod.statecraft.no_decisions"), x + 5, top + 5, listW - 10, 4, UiKit.TEXT_MUTED);
        }
        int rows = (listH - 2) / ROW_H;
        listScroll = clampScroll(listScroll, list.size(), rows);
        for (int r = 0; r < rows && listScroll + r < list.size(); r++) {
            int i = listScroll + r;
            StatecraftService.DecisionView d = list.get(i);
            int ry = top + 1 + r * ROW_H;
            UiKit.row(g, x + 1, ry, listW - 2, ROW_H - 1, i == selectedDecision, UiKit.inside(mx, my, x, ry, listW, ROW_H));
            int color = switch (d.state()) {
                case StatecraftService.AVAILABLE -> UiKit.GOOD;
                case StatecraftService.TAKEN -> UiKit.INFO;
                case StatecraftService.COOLDOWN -> UiKit.WARN;
                default -> UiKit.TEXT_MUTED;
            };
            g.drawString(font, d.state() == StatecraftService.TAKEN ? "✔" : "●", x + 4, ry + 2, color, false);
            UiKit.text(g, font, Component.translatable(d.title()), x + 13, ry + 2, listW - 17,
                    d.state() == StatecraftService.LOCKED ? UiKit.TEXT_DIM : UiKit.TEXT);
        }

        StatecraftService.DecisionView d = decision();
        int panelBottom = btnTake.visible ? y + h - BTN_H - 4 : y + h;
        UiKit.card(g, detailX, top, detailW, panelBottom - top);
        if (d == null) return;
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable(d.desc(), screen.countryName(), "").withStyle(ChatFormatting.GRAY));
        if (d.state() == StatecraftService.TAKEN) {
            lines.add(Component.translatable("gui.politicsmod.statecraft.taken").withStyle(ChatFormatting.AQUA));
        } else if (d.state() == StatecraftService.COOLDOWN) {
            lines.add(Component.translatable("gui.politicsmod.statecraft.cooldown", d.cooldownMinutes()).withStyle(ChatFormatting.GOLD));
        }
        if (!d.requirements().isEmpty()) {
            lines.add(Component.translatable("gui.politicsmod.statecraft.requirements").withStyle(ChatFormatting.GOLD));
            for (Line l : d.requirements()) lines.add(l.styled());
        }
        lines.add(Component.translatable("gui.politicsmod.statecraft.effects").withStyle(ChatFormatting.GOLD));
        for (Line l : d.effects()) lines.add(l.styled());
        detail(g, Component.translatable(d.title()), lines, top, panelBottom);
    }

    private void renderGoals(GuiGraphics g, int mx, int my, StatecraftService.View v) {
        List<StatecraftService.GoalView> list = v.goals();
        int listH = y + h - top;
        UiKit.card(g, x, top, listW, listH);
        if (list.isEmpty()) {
            UiKit.wrap(g, font, Component.translatable("gui.politicsmod.statecraft.no_goals"), x + 5, top + 5, listW - 10, 4, UiKit.TEXT_MUTED);
        }
        int rows = (listH - 2) / ROW_H;
        listScroll = clampScroll(listScroll, list.size(), rows);
        for (int r = 0; r < rows && listScroll + r < list.size(); r++) {
            int i = listScroll + r;
            StatecraftService.GoalView goal = list.get(i);
            int ry = top + 1 + r * ROW_H;
            UiKit.row(g, x + 1, ry, listW - 2, ROW_H - 1, i == selectedGoal, UiKit.inside(mx, my, x, ry, listW, ROW_H));
            if (goal.done()) {
                g.drawString(font, "✔", x + 4, ry + 2, UiKit.GOOD, false);
                UiKit.text(g, font, Component.translatable(goal.title()), x + 13, ry + 2, listW - 17, UiKit.TEXT_DIM);
            } else {
                UiKit.text(g, font, Component.translatable(goal.title()), x + 5, ry + 2, listW - 40, UiKit.TEXT);
                UiKit.progress(g, x + listW - 32, ry + 4, 27, 4, goal.progress() / 100f, UiKit.ACCENT);
            }
        }

        StatecraftService.GoalView goal = goal();
        UiKit.card(g, detailX, top, detailW, y + h - top);
        if (goal == null) return;
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable(goal.desc(), screen.countryName(), "").withStyle(ChatFormatting.GRAY));
        lines.add(goal.done() ? Component.translatable("gui.politicsmod.statecraft.goal_done").withStyle(ChatFormatting.GREEN)
                : Component.translatable("gui.politicsmod.statecraft.progress", goal.progress()).withStyle(ChatFormatting.GOLD));
        if (!goal.done() && !goal.requirements().isEmpty()) {
            lines.add(Component.translatable("gui.politicsmod.statecraft.requirements").withStyle(ChatFormatting.GOLD));
            for (Line l : goal.requirements()) lines.add(l.styled());
        }
        lines.add(Component.translatable("gui.politicsmod.statecraft.reward").withStyle(ChatFormatting.GOLD));
        for (Line l : goal.reward()) lines.add(l.styled());
        detail(g, Component.translatable(goal.title()), lines, top, y + h);
    }

    /** A title and wrapped lines in the detail panel; the wheel scrolls them when they don't fit. */
    private void detail(GuiGraphics g, Component title, List<Component> lines, int from, int to) {
        int tx = detailX + 5, tw = detailW - 10;
        UiKit.text(g, font, title.copy().withStyle(ChatFormatting.BOLD), tx, from + 5, tw, UiKit.ACCENT);
        List<FormattedCharSequence> wrapped = new ArrayList<>();
        for (Component c : lines) wrapped.addAll(font.split(c, tw));
        int bodyTop = from + 17;
        int visible = Math.max(1, (to - 4 - bodyTop) / 10);
        detailScroll = clampScroll(detailScroll, wrapped.size(), visible);
        for (int i = 0; i < visible && detailScroll + i < wrapped.size(); i++) {
            g.drawString(font, wrapped.get(detailScroll + i), tx, bodyTop + i * 10, UiKit.TEXT, false);
        }
        if (wrapped.size() > visible) {
            UiKit.textRight(g, font, Component.literal(detailScroll + visible < wrapped.size() ? "▾" : "▴"), detailX + detailW - 4,
                    to - 12, UiKit.TEXT_MUTED);
        }
    }

    // ── Input ────────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        StatecraftService.View v = view();
        if (v == null || page == PAGE_EVENTS) return false;
        int listH = y + h - top;
        if (!UiKit.inside(mx, my, x, top, listW, listH)) return false;
        int index = listScroll + (int) ((my - top - 1) / ROW_H);
        if (page == PAGE_DECISIONS && index < v.decisions().size()) {
            selectedDecision = index;
            cityIndex = 0;
        } else if (page == PAGE_GOALS && index < v.goals().size()) {
            selectedGoal = index;
        }
        detailScroll = 0;
        update();
        return true;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (page == PAGE_EVENTS) return false;
        if (mx < detailX) listScroll += delta > 0 ? -1 : 1;
        else detailScroll += delta > 0 ? -1 : 1;
        return true;
    }
}
