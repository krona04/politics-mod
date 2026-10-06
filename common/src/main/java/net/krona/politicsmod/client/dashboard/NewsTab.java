package net.krona.politicsmod.client.dashboard;

import net.krona.politicsmod.client.CountryDashboardScreen;
import net.krona.politicsmod.client.ui.FlatButton;
import net.krona.politicsmod.client.ui.UiKit;
import net.krona.politicsmod.network.CountryDetailsPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;

/** The server's news feed, newest first; optionally only the news about this country. */
public class NewsTab extends DashboardTab {
    private int scroll = 0;
    private boolean onlyOurs = false;
    private FlatButton btnFilter;

    public NewsTab(CountryDashboardScreen screen) {
        super(screen);
    }

    @Override
    public Component title() {
        return Component.translatable("gui.politicsmod.dashboard.tab.news");
    }

    @Override
    protected void addWidgets() {
        int bw = Math.max(FlatButton.widthFor(Component.translatable("gui.politicsmod.news.filter_all")),
                FlatButton.widthFor(Component.translatable("gui.politicsmod.news.filter_ours")));
        btnFilter = add(new FlatButton(x + w - bw, y - 2, bw, 13, Component.empty(), () -> {
            onlyOurs = !onlyOurs;
            scroll = 0;
        }));
    }

    @Override
    public void update() {
        btnFilter.setMessage(Component.translatable(onlyOurs ? "gui.politicsmod.news.filter_ours" : "gui.politicsmod.news.filter_all"));
    }

    private List<CountryDetailsPayload.News> entries(CountryDetailsPayload d) {
        List<CountryDetailsPayload.News> list = new ArrayList<>();
        for (int i = d.news().size() - 1; i >= 0; i--) {
            CountryDetailsPayload.News n = d.news().get(i);
            if (!onlyOurs || n.args().contains(screen.countryName())) list.add(n);
        }
        return list;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        CountryDetailsPayload d = details();
        if (d == null) {
            loading(g);
            return;
        }
        UiKit.text(g, font, Component.translatable("gui.politicsmod.news.header"), x + 5, y, w - btnFilter.getWidth() - 12, UiKit.ACCENT);
        g.fill(x, y, x + 2, y + 8, UiKit.ACCENT);
        int top = y + 15;
        UiKit.card(g, x, top, w, h - 15);
        List<CountryDetailsPayload.News> list = entries(d);
        if (list.isEmpty()) {
            g.drawCenteredString(font, Component.translatable("message.politicsmod.news.empty"), x + w / 2, top + 10, UiKit.TEXT_MUTED);
            return;
        }
        scroll = Math.max(0, Math.min(scroll, list.size() - 1));
        long now = System.currentTimeMillis();
        int ly = top + 5;
        g.enableScissor(x + 1, top + 1, x + w - 1, y + h - 1);
        for (int i = scroll; i < list.size() && ly < y + h - 4; i++) {
            CountryDetailsPayload.News n = list.get(i);
            boolean ours = n.args().contains(screen.countryName());
            // "[5 min ago] text" as one paragraph, so long time labels in any language just wrap
            Component line = Component.empty()
                    .append(ago(now - n.time()).copy().withColor(UiKit.TEXT_MUTED & 0xFFFFFF))
                    .append(" ")
                    .append(n.entry().text().copy().withColor((ours ? UiKit.TEXT : UiKit.TEXT_DIM) & 0xFFFFFF));
            List<FormattedCharSequence> lines = font.split(line, w - 14);
            if (ours) g.fill(x + 3, ly, x + 5, ly + lines.size() * 10 - 2, UiKit.ACCENT);
            for (FormattedCharSequence l : lines) {
                g.drawString(font, l, x + 8, ly, UiKit.TEXT, false);
                ly += 10;
            }
            ly += 4;
        }
        g.disableScissor();
    }

    private static Component ago(long ms) {
        long minutes = Math.max(0, ms / 60_000);
        if (minutes < 1) return Component.translatable("gui.politicsmod.news.now");
        if (minutes < 60) return Component.translatable("gui.politicsmod.news.minutes", minutes);
        long hours = minutes / 60;
        if (hours < 48) return Component.translatable("gui.politicsmod.news.hours", hours);
        return Component.translatable("gui.politicsmod.news.days", hours / 24);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        scroll += delta > 0 ? -1 : 1;
        if (scroll < 0) scroll = 0;
        return true;
    }
}
