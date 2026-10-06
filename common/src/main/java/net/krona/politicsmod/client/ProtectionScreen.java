package net.krona.politicsmod.client;

import net.krona.politicsmod.client.ui.FlatButton;
import net.krona.politicsmod.client.ui.UiKit;
import net.krona.politicsmod.network.CountryDetailsPayload;
import net.krona.politicsmod.network.ModNetworking;
import net.krona.politicsmod.network.RequestCountryDetailsPayload;
import net.krona.politicsmod.protection.ProtectionFlag;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Protection flags for the country or one of its cities. Opened from the dashboard's Map tab.
 * Each button cycles: inherit -> on -> off -> inherit, through the same /politicsmod flag commands.
 */
public class ProtectionScreen extends net.krona.politicsmod.client.ui.PoliticsScreen {
    private static final int REFRESH_DELAY_TICKS = 10;
    private static final int PW = 280, ROW = 24;

    private final Screen parent;
    private final List<String> cities;
    private int target = 0; // 0 = country, 1.. = cities.get(target - 1)
    private FlatButton btnTarget;
    private final List<FlatButton> flagButtons = new ArrayList<>();
    private int refreshInTicks = -1;
    private int px, py, ph;

    public ProtectionScreen(Screen parent, List<String> cities) {
        super(Component.translatable("gui.politicsmod.protection.title"));
        this.parent = parent;
        this.cities = cities;
    }

    @Override
    protected void init() {
        super.init();
        flagButtons.clear();
        ProtectionFlag[] flags = ProtectionFlag.values();
        ph = 58 + flags.length * ROW + 28;
        px = (width - PW) / 2;
        py = (height - ph) / 2;

        btnTarget = addRenderableWidget(new FlatButton(px + 10, py + 28, PW - 20, 16, Component.empty(), () -> {
            target = (target + 1) % (cities.size() + 1);
            updateButtons();
        }));

        for (int i = 0; i < flags.length; i++) {
            ProtectionFlag flag = flags[i];
            FlatButton b = new FlatButton(px + PW - 80, py + 54 + i * ROW + 3, 70, 16, Component.empty(), () -> cycle(flag))
                    .tooltip(Component.translatable("gui.politicsmod.flag." + flag.key() + ".hint"));
            flagButtons.add(addRenderableWidget(b));
        }

        addRenderableWidget(new FlatButton(px + PW / 2 - 45, py + ph - 24, 90, 16, Component.translatable("gui.done"),
                FlatButton.Style.PRIMARY, this::onClose));
        updateButtons();
    }

    private String targetCity() {
        return target == 0 ? "" : cities.get(target - 1);
    }

    /** The explicit value at the current target, or null if it inherits. */
    private Boolean override(CountryDetailsPayload details, String city, ProtectionFlag flag) {
        if (details == null) return null;
        for (CountryDetailsPayload.FlagOverride o : details.flags()) {
            if (o.target().equals(city) && o.flag().equals(flag.key())) return o.value();
        }
        return null;
    }

    /** What the flag is when the current target doesn't override it. */
    private boolean inherited(CountryDetailsPayload details, String city, ProtectionFlag flag) {
        if (!city.isEmpty()) {
            Boolean countryValue = override(details, "", flag);
            if (countryValue != null) return countryValue;
        }
        return details != null && (details.flagDefaults() & (1 << flag.ordinal())) != 0;
    }

    private void updateButtons() {
        CountryDetailsPayload details = ClientPoliticsData.countryDetails;
        String city = targetCity();
        btnTarget.setMessage(city.isEmpty()
                ? Component.translatable("gui.politicsmod.protection.target_country")
                : Component.translatable("gui.politicsmod.protection.target_city", city));

        ProtectionFlag[] flags = ProtectionFlag.values();
        for (int i = 0; i < flags.length; i++) {
            ProtectionFlag flag = flags[i];
            Boolean value = override(details, city, flag);
            boolean effective = value != null ? value : inherited(details, city, flag);
            Component state = Component.translatable(effective ? "gui.politicsmod.flag.on" : "gui.politicsmod.flag.off")
                    .withStyle(effective ? ChatFormatting.GREEN : ChatFormatting.RED);
            flagButtons.get(i).setMessage(state);
            flagButtons.get(i).active = details != null;
        }
    }

    private void cycle(ProtectionFlag flag) {
        String city = targetCity();
        Boolean value = override(ClientPoliticsData.countryDetails, city, flag);
        String next = value == null ? "on" : value ? "off" : "default";
        String command = city.isEmpty()
                ? "politicsmod flag set " + flag.key() + " " + next
                : "politicsmod flag city " + CountryDashboardScreen.quote(city) + " " + flag.key() + " " + next;
        if (minecraft != null && minecraft.player != null) {
            minecraft.player.connection.sendCommand(command);
            refreshInTicks = REFRESH_DELAY_TICKS;
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (refreshInTicks > 0 && --refreshInTicks == 0) {
            ModNetworking.toServer(new RequestCountryDetailsPayload());
        }
        updateButtons();
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
        super.renderBackground(g, mx, my, pt);
        UiKit.panel(g, px, py, PW, ph);
        g.drawString(font, title.copy().withStyle(ChatFormatting.BOLD), px + 10, py + 10, UiKit.TEXT, false);
        g.fill(px + 10, py + 22, px + PW - 10, py + 23, UiKit.PANEL_BORDER);

        CountryDetailsPayload details = ClientPoliticsData.countryDetails;
        String city = targetCity();
        ProtectionFlag[] flags = ProtectionFlag.values();
        for (int i = 0; i < flags.length; i++) {
            ProtectionFlag flag = flags[i];
            int ry = py + 54 + i * ROW;
            UiKit.card(g, px + 10, ry, PW - 20, ROW - 2);
            UiKit.text(g, font, Component.translatable("gui.politicsmod.flag." + flag.key()), px + 16, ry + 4, PW - 110, UiKit.TEXT);
            boolean inherits = override(details, city, flag) == null;
            UiKit.text(g, font, Component.translatable(inherits ? "gui.politicsmod.protection.state_inherited" : "gui.politicsmod.protection.state_set"),
                    px + 16, ry + 13, PW - 110, inherits ? UiKit.TEXT_MUTED : UiKit.ACCENT);
        }
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.setScreen(parent);
    }
}
