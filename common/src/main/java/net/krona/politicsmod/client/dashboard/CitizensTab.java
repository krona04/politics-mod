package net.krona.politicsmod.client.dashboard;

import net.krona.politicsmod.client.CountryDashboardScreen;
import net.krona.politicsmod.client.ui.FlatButton;
import net.krona.politicsmod.client.ui.UiKit;
import net.krona.politicsmod.network.CountryDetailsPayload;
import net.krona.politicsmod.politics.RoleAction;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.regex.Pattern;

/** Members with roles, online status and salaries; invite, kick, promote and pay them. */
public class CitizensTab extends DashboardTab {
    private static final Pattern PLAYER_NAME = Pattern.compile("[A-Za-z0-9_]{3,16}");
    private static final int ROW_H = 13;

    private int selected = -1;
    private int scroll = 0;
    private int listH;
    private FlatButton btnRole, btnKick, btnInvite, btnSalary;
    private EditBox inviteInput, salaryInput;

    public CitizensTab(CountryDashboardScreen screen) {
        super(screen);
    }

    @Override
    public Component title() {
        return Component.translatable("gui.politicsmod.dashboard.tab.citizens");
    }

    @Override
    protected void addWidgets() {
        // Two rows under the list: [invite name][Invite] and [salary][Set salary]; buttons are as wide as their labels
        int by = y + h - 15;
        Component salaryLabel = Component.translatable("gui.politicsmod.citizens.set_salary");
        int sw = FlatButton.widthFor(salaryLabel);
        salaryInput = add(new EditBox(font, x + 1, by + 1, w - sw - 6, 13, Component.translatable("gui.politicsmod.citizens.salary_hint")));
        salaryInput.setMaxLength(7);
        salaryInput.setFilter(v -> v.matches("\\d*"));
        salaryInput.setHint(Component.translatable("gui.politicsmod.citizens.salary_hint").withStyle(ChatFormatting.DARK_GRAY));
        btnSalary = add(new FlatButton(x + w - sw, by, sw, 15, salaryLabel, this::setSalary));

        by -= 18;
        Component inviteLabel = Component.translatable("gui.politicsmod.citizens.invite");
        int iw = FlatButton.widthFor(inviteLabel);
        inviteInput = add(new EditBox(font, x + 1, by + 1, w - iw - 6, 13, Component.translatable("gui.politicsmod.citizens.invite_hint")));
        inviteInput.setMaxLength(16);
        inviteInput.setHint(Component.translatable("gui.politicsmod.citizens.invite_hint").withStyle(ChatFormatting.DARK_GRAY));
        btnInvite = add(new FlatButton(x + w - iw, by, iw, 15, inviteLabel, FlatButton.Style.PRIMARY, this::invite));

        by -= 18;
        int half = (w - 4) / 2;
        btnRole = add(new FlatButton(x, by, half, 15, Component.translatable("gui.politicsmod.citizens.make_mayor"), this::toggleRole));
        btnKick = add(new FlatButton(x + half + 4, by, w - half - 4, 15, Component.translatable("gui.politicsmod.citizens.kick"),
                FlatButton.Style.DANGER, this::kick));
        listH = by - 5 - (y + 13);
    }

    private CountryDetailsPayload.Member member() {
        CountryDetailsPayload d = details();
        if (d == null || selected < 0 || selected >= d.members().size()) return null;
        return d.members().get(selected);
    }

    @Override
    public void update() {
        CountryDetailsPayload.Member m = member();
        boolean actionable = m != null && m.online() && !"LEADER".equals(m.role());
        btnRole.visible = screen.isLeader();
        btnRole.active = actionable;
        btnRole.setMessage(Component.translatable(m != null && "MAYOR".equals(m.role())
                ? "gui.politicsmod.citizens.make_citizen" : "gui.politicsmod.citizens.make_mayor"));
        btnKick.visible = screen.can(RoleAction.KICK);
        btnKick.active = actionable;
        Component hint = m != null && !m.online() ? Component.translatable("gui.politicsmod.citizens.must_be_online") : null;
        btnRole.tooltip(hint);
        btnKick.tooltip(hint);

        boolean invite = screen.can(RoleAction.INVITE);
        inviteInput.visible = invite;
        btnInvite.visible = invite;
        btnInvite.active = PLAYER_NAME.matcher(inviteInput.getValue()).matches();

        salaryInput.visible = screen.isLeader();
        btnSalary.visible = screen.isLeader();
        btnSalary.active = m != null && !salaryInput.getValue().isEmpty();
    }

    private void toggleRole() {
        CountryDetailsPayload.Member m = member();
        if (m != null) screen.runCommand("politicsmod setrole " + m.name() + " " + ("MAYOR".equals(m.role()) ? "citizen" : "mayor"));
    }

    private void kick() {
        CountryDetailsPayload.Member m = member();
        if (m == null) return;
        screen.runCommand("politicsmod kick " + m.name());
        selected = -1;
    }

    private void invite() {
        String name = inviteInput.getValue().trim();
        if (!PLAYER_NAME.matcher(name).matches()) return;
        screen.runCommand("politicsmod invite " + name);
        inviteInput.setValue("");
    }

    private void setSalary() {
        CountryDetailsPayload.Member m = member();
        if (m == null || salaryInput.getValue().isEmpty()) return;
        screen.runCommand("politicsmod salary set " + m.name() + " " + Integer.parseInt(salaryInput.getValue()));
        salaryInput.setValue("");
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        CountryDetailsPayload d = details();
        if (d == null) {
            loading(g);
            return;
        }
        List<CountryDetailsPayload.Member> members = d.members();
        UiKit.section(g, font, Component.translatable("gui.politicsmod.citizens.header", members.size()), x, y);
        int top = y + 13;
        UiKit.card(g, x, top, w, listH);
        int rows = listH / ROW_H;
        scroll = clampScroll(scroll, members.size(), rows);
        for (int r = 0; r < rows && scroll + r < members.size(); r++) {
            int index = scroll + r;
            CountryDetailsPayload.Member m = members.get(index);
            int ry = top + 1 + r * ROW_H;
            UiKit.row(g, x + 1, ry, w - 2, ROW_H - 1, index == selected, UiKit.inside(mx, my, x, ry, w, ROW_H));
            g.drawString(font, "●", x + 5, ry + 2, m.online() ? UiKit.GOOD : UiKit.TEXT_MUTED, false);
            UiKit.text(g, font, Component.literal(m.name()), x + 15, ry + 2, w * 45 / 100 - 18, m.online() ? UiKit.TEXT : UiKit.TEXT_DIM);
            if (m.salary() > 0) {
                g.drawString(font, Component.translatable("gui.politicsmod.citizens.salary", m.salary()), x + w * 47 / 100, ry + 2, UiKit.WARN, false);
            }
            UiKit.textRight(g, font, CountryDashboardScreen.roleName(m.role()), x + w - 6, ry + 2, roleColor(m.role()));
        }
        if (members.size() > rows) {
            UiKit.textRight(g, font, Component.literal((scroll + 1) + "–" + Math.min(members.size(), scroll + rows) + "/" + members.size()),
                    x + w, y, UiKit.TEXT_MUTED);
        }
    }

    private static int roleColor(String role) {
        return switch (role) {
            case "LEADER" -> UiKit.ACCENT;
            case "MAYOR" -> UiKit.INFO;
            default -> UiKit.TEXT_DIM;
        };
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        CountryDetailsPayload d = details();
        int top = y + 13;
        if (d == null || !UiKit.inside(mx, my, x, top, w, listH)) return false;
        int index = scroll + (int) ((my - top - 1) / ROW_H);
        selected = index < d.members().size() ? index : -1;
        update();
        return true;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        scroll += delta > 0 ? -1 : 1;
        return true;
    }
}
