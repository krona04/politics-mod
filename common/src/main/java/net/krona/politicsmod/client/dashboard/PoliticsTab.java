package net.krona.politicsmod.client.dashboard;

import net.krona.politicsmod.client.CountryDashboardScreen;
import net.krona.politicsmod.client.ui.FlatButton;
import net.krona.politicsmod.client.ui.UiKit;
import net.krona.politicsmod.network.CountryDetailsPayload;
import net.krona.politicsmod.politics.Law;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.List;

/** Laws on the left, elections and the petition on the right. */
public class PoliticsTab extends DashboardTab {
    private static final int ROW_H = 12;
    private static final int BTN_H = 15;

    private final FlatButton[] lawButtons = new FlatButton[Law.values().length];
    private FlatButton btnRun, btnVote, btnStartLeader, btnStartMayor, btnCity, btnSign;
    private int selectedCandidate = -1;
    private int cityIndex = 0;

    private int lw, rx, rw, lawH, electionTop, electionH, petitionTop;

    public PoliticsTab(CountryDashboardScreen screen) {
        super(screen);
    }

    @Override
    public Component title() {
        return Component.translatable("gui.politicsmod.dashboard.tab.politics");
    }

    @Override
    protected void addWidgets() {
        lw = (w - 6) / 2;
        rx = x + lw + 6;
        rw = w - lw - 6;
        lawH = (h - 12 - 6) / 3;

        // Law buttons: as wide as the longer of "Enact" and "Repeal", so they don't jump when toggled
        int lawBtnW = Math.max(FlatButton.widthFor(Component.translatable("gui.politicsmod.politics.enact")),
                FlatButton.widthFor(Component.translatable("gui.politicsmod.politics.repeal")));
        Law[] laws = Law.values();
        for (int i = 0; i < laws.length; i++) {
            Law law = laws[i];
            int cy = y + 12 + i * (lawH + 3);
            lawButtons[i] = add(new FlatButton(x + lw - lawBtnW - 3, cy + lawH - BTN_H - 3, lawBtnW, BTN_H, Component.empty(), () -> {
                CountryDetailsPayload d = details();
                if (d == null) return;
                screen.runCommand("politicsmod law " + (d.hasLaw(law) ? "repeal " : "enact ") + law.key());
            }));
        }

        // Right column, from the bottom up: action buttons, petition, election card
        int by = y + h - BTN_H;
        Component run = Component.translatable("gui.politicsmod.politics.run");
        Component vote = Component.translatable("gui.politicsmod.politics.vote");
        int runW = FlatButton.widthFor(run), voteW = FlatButton.widthFor(vote);
        if (runW + voteW + 4 <= rw) {
            int half = Math.max(runW, (rw - 4) / 2);
            btnRun = add(new FlatButton(rx, by, half, BTN_H, run, () -> screen.runCommand("politicsmod election run")));
            btnVote = add(new FlatButton(rx + half + 4, by, rw - half - 4, BTN_H, vote, FlatButton.Style.PRIMARY, this::vote));
        } else {
            btnVote = add(new FlatButton(rx, by, rw, BTN_H, vote, FlatButton.Style.PRIMARY, this::vote));
            btnRun = add(new FlatButton(rx, by - BTN_H - 2, rw, BTN_H, run, () -> screen.runCommand("politicsmod election run")));
        }
        btnStartLeader = add(new FlatButton(rx, by, rw, BTN_H, Component.translatable("gui.politicsmod.politics.start_leader"),
                FlatButton.Style.PRIMARY, () -> screen.runCommand("politicsmod election start leader")));
        int by2 = by - BTN_H - 2;
        btnStartMayor = add(new FlatButton(rx, by2, rw - BTN_H - 2, BTN_H, Component.empty(), () -> {
            String city = city();
            if (city != null) screen.runCommand("politicsmod election start mayor " + city);
        }));
        btnCity = add(new FlatButton(rx + rw - BTN_H, by2, BTN_H, BTN_H, Component.literal("▸"), () -> {
            CountryDetailsPayload d = details();
            if (d != null && !d.cities().isEmpty()) cityIndex = (cityIndex + 1) % d.cities().size();
        }).tooltip(Component.translatable("gui.politicsmod.politics.next_city")));

        petitionTop = by2 - 4 - 34;
        Component sign = Component.translatable("gui.politicsmod.politics.sign");
        int signW = FlatButton.widthFor(sign);
        btnSign = add(new FlatButton(rx + rw - signW, petitionTop + 11, signW, 13, sign,
                () -> screen.runCommand("politicsmod election petition"))
                .tooltip(Component.translatable("gui.politicsmod.politics.sign_hint")));

        electionTop = y + 11;
        electionH = petitionTop - 4 - electionTop;
    }

    private String city() {
        CountryDetailsPayload d = details();
        if (d == null || d.cities().isEmpty()) return null;
        if (cityIndex >= d.cities().size()) cityIndex = 0;
        return d.cities().get(cityIndex).name();
    }

    private void vote() {
        CountryDetailsPayload d = details();
        if (d == null || selectedCandidate < 0 || selectedCandidate >= d.election().candidates().size()) return;
        screen.runCommand("politicsmod election vote " + d.election().candidates().get(selectedCandidate).name());
    }

    @Override
    public void update() {
        CountryDetailsPayload d = details();
        boolean leader = screen.isLeader();
        Law[] laws = Law.values();
        for (int i = 0; i < laws.length; i++) {
            lawButtons[i].visible = leader && d != null;
            if (d != null) {
                boolean on = d.hasLaw(laws[i]);
                lawButtons[i].setMessage(Component.translatable(on ? "gui.politicsmod.politics.repeal" : "gui.politicsmod.politics.enact"));
                lawButtons[i].active = d.lawCooldown() == 0;
            }
        }

        boolean running = d != null && !d.election().type().isEmpty();
        btnRun.visible = running;
        btnRun.active = running && !d.election().candidate();
        btnVote.visible = running;
        btnVote.active = running && selectedCandidate >= 0 && selectedCandidate < d.election().candidates().size();

        boolean canStart = d != null && !running && leader;
        boolean cooled = d != null && d.election().cooldown() == 0;
        btnStartLeader.visible = canStart;
        btnStartLeader.active = cooled;
        String city = city();
        btnStartMayor.visible = canStart;
        btnStartMayor.active = cooled && city != null;
        btnStartMayor.setMessage(city == null ? Component.translatable("gui.politicsmod.dashboard.no_cities")
                : Component.translatable("gui.politicsmod.politics.start_mayor", city));
        btnCity.visible = canStart;
        btnCity.active = d != null && d.cities().size() > 1;

        btnSign.visible = d != null && !leader && !running && !d.election().signed();
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        CountryDetailsPayload d = details();
        if (d == null) {
            loading(g);
            return;
        }
        renderLaws(g, d);
        renderElection(g, mx, my, d);
    }

    private void renderLaws(GuiGraphics g, CountryDetailsPayload d) {
        UiKit.section(g, font, Component.translatable("gui.politicsmod.politics.laws"), x, y);
        if (d.lawCooldown() > 0) {
            UiKit.textRight(g, font, Component.literal("⌛ " + UiKit.time(d.lawCooldown())), x + lw, y, UiKit.TEXT_MUTED);
        }
        Law[] laws = Law.values();
        for (int i = 0; i < laws.length; i++) {
            Law law = laws[i];
            boolean on = d.hasLaw(law);
            int cy = y + 12 + i * (lawH + 3);
            UiKit.card(g, x, cy, lw, lawH);
            if (on) g.fill(x, cy, x + 2, cy + lawH, UiKit.GOOD);
            UiKit.text(g, font, Component.translatable(law.nameKey()).withStyle(ChatFormatting.BOLD), x + 6, cy + 4, lw - 10, UiKit.TEXT);
            // Up to two lines of description; the whole text is in the card's tooltip
            int descLines = Math.max(1, (lawH - 4 - 12 - BTN_H - 3) / 10);
            UiKit.wrap(g, font, Component.translatable("law.politicsmod." + law.key() + ".desc"), x + 6, cy + 15, lw - 10, descLines, UiKit.TEXT_DIM);
            Component state = Component.translatable(on ? "gui.politicsmod.flag.on" : "gui.politicsmod.flag.off");
            UiKit.chip(g, font, state, x + 6, cy + lawH - 15, on ? UiKit.GOOD : UiKit.TEXT_MUTED);
        }
    }

    @Override
    public void renderOverlay(GuiGraphics g, int mx, int my) {
        Law[] laws = Law.values();
        for (int i = 0; i < laws.length; i++) {
            int cy = y + 12 + i * (lawH + 3);
            if (UiKit.inside(mx, my, x, cy, lw, lawH) && !lawButtons[i].isHovered()) {
                g.renderTooltip(font, font.split(Component.translatable("law.politicsmod." + laws[i].key() + ".desc"), 200), mx, my);
            }
        }
    }

    private void renderElection(GuiGraphics g, int mx, int my, CountryDetailsPayload d) {
        CountryDetailsPayload.ElectionInfo e = d.election();
        UiKit.section(g, font, Component.translatable("gui.politicsmod.politics.election"), rx, y);
        UiKit.card(g, rx, electionTop, rw, electionH);

        if (e.type().isEmpty()) {
            int ly = electionTop + 5;
            UiKit.text(g, font, Component.translatable("gui.politicsmod.overview.no_election"), rx + 5, ly, rw - 10, UiKit.TEXT);
            ly += 12;
            int maxLines = (electionH - 20 - (e.cooldown() > 0 ? 11 : 0)) / 10;
            UiKit.wrap(g, font, Component.translatable(screen.isLeader()
                    ? "gui.politicsmod.politics.leader_hint" : "gui.politicsmod.politics.citizen_hint"), rx + 5, ly, rw - 10, maxLines, UiKit.TEXT_DIM);
            if (e.cooldown() > 0) {
                UiKit.text(g, font, Component.translatable("gui.politicsmod.politics.cooldown", UiKit.time(e.cooldown())),
                        rx + 5, electionTop + electionH - 11, rw - 10, UiKit.TEXT_MUTED);
            }
        } else {
            Component office = "LEADER".equals(e.type()) ? Component.translatable("gui.politicsmod.politics.office_leader")
                    : Component.translatable("gui.politicsmod.politics.office_mayor", e.city());
            Component timer = Component.literal("⌛ " + UiKit.time(e.secondsLeft()));
            UiKit.text(g, font, office, rx + 5, electionTop + 4, rw - font.width(timer) - 14, UiKit.WARN);
            UiKit.textRight(g, font, timer, rx + rw - 5, electionTop + 4, UiKit.TEXT_DIM);

            List<CountryDetailsPayload.Candidate> cands = e.candidates();
            int total = 0;
            for (CountryDetailsPayload.Candidate c : cands) total += c.votes();
            int top = electionTop + 16;
            if (cands.isEmpty()) {
                UiKit.wrap(g, font, Component.translatable("message.politicsmod.election.no_candidates_yet"), rx + 5, top + 2, rw - 10, 2, UiKit.TEXT_MUTED);
            }
            int rows = (electionH - 18) / (ROW_H + 1);
            for (int i = 0; i < Math.min(rows, cands.size()); i++) {
                CountryDetailsPayload.Candidate c = cands.get(i);
                int ry = top + i * (ROW_H + 1);
                UiKit.row(g, rx + 1, ry, rw - 2, ROW_H, i == selectedCandidate, UiKit.inside(mx, my, rx, ry, rw, ROW_H));
                boolean mine = c.name().equals(e.myVote());
                UiKit.text(g, font, Component.literal((mine ? "✔ " : "") + c.name()), rx + 5, ry + 2, rw - 62, mine ? UiKit.GOOD : UiKit.TEXT);
                UiKit.progress(g, rx + rw - 52, ry + 3, 28, 5, total == 0 ? 0 : c.votes() / (float) total, UiKit.ACCENT);
                UiKit.textRight(g, font, Component.literal(String.valueOf(c.votes())), rx + rw - 5, ry + 2, UiKit.TEXT_DIM);
            }
        }

        // Petition for a new leader
        UiKit.section(g, font, Component.translatable("gui.politicsmod.politics.petition"), rx, petitionTop);
        float fraction = e.petitionNeeded() == 0 ? 0 : e.petition() / (float) e.petitionNeeded();
        int barW = rw - (btnSign.visible ? btnSign.getWidth() + 4 : 0);
        UiKit.progress(g, rx, petitionTop + 15, barW, 5, fraction, UiKit.WARN);
        UiKit.text(g, font, Component.translatable(e.signed() ? "gui.politicsmod.politics.petition_signed" : "gui.politicsmod.politics.petition_count",
                e.petition(), e.petitionNeeded()), rx, petitionTop + 25, rw, UiKit.TEXT_DIM);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        CountryDetailsPayload d = details();
        if (d == null || d.election().type().isEmpty()) return false;
        int top = electionTop + 16;
        if (!UiKit.inside(mx, my, rx, top, rw, electionH - 18)) return false;
        int index = (int) ((my - top) / (ROW_H + 1));
        selectedCandidate = index < d.election().candidates().size() ? index : -1;
        update();
        return true;
    }
}
