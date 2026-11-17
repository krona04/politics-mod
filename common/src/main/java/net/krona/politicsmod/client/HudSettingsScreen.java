package net.krona.politicsmod.client;

import net.krona.politicsmod.client.ui.FlatButton;
import net.krona.politicsmod.client.ui.PoliticsScreen;
import net.krona.politicsmod.client.ui.UiKit;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * Settings of the country info panel (key H): when it is shown, how it looks, how big it is and where it sits.
 * The world stays undimmed behind it, so the panel itself is the preview and changes at once.
 */
public class HudSettingsScreen extends PoliticsScreen {
    private static final int W = 210, H = 150, BTN = 15;

    private FlatButton btnMode, btnStyle, btnScale;
    private final FlatButton[] positions = new FlatButton[ClientHudSettings.Position.values().length];
    private int px, py;

    public HudSettingsScreen() {
        super(Component.translatable("gui.politicsmod.hud.settings.title"));
    }

    @Override
    protected void init() {
        super.init();
        px = (width - W) / 2;
        py = (height - H) / 2;
        int bx = px + 8, bw = W - 16;
        btnMode = addRenderableWidget(new FlatButton(bx, py + 20, bw, BTN, Component.empty(), () -> {
            ClientHudSettings.Mode[] all = ClientHudSettings.Mode.values();
            ClientHudSettings.set(all[(ClientHudSettings.mode().ordinal() + 1) % all.length], false);
        }));
        btnStyle = addRenderableWidget(new FlatButton(bx, py + 38, bw, BTN, Component.empty(), () -> {
            ClientHudSettings.Style[] all = ClientHudSettings.Style.values();
            ClientHudSettings.setStyle(all[(ClientHudSettings.style().ordinal() + 1) % all.length]);
        }));
        btnScale = addRenderableWidget(new FlatButton(bx, py + 56, bw, BTN, Component.empty(), () -> {
            int[] all = ClientHudSettings.SCALES;
            int next = all[0];
            for (int i = 0; i < all.length; i++) {
                if (all[i] == ClientHudSettings.scale()) next = all[(i + 1) % all.length];
            }
            ClientHudSettings.setScale(next);
        }));

        // Positions as they are on the screen: three on top, two in the middle, one bottom right
        int cell = 24, gap = 4;
        int gx = px + (W - (cell * 3 + gap * 2)) / 2;
        int gy = py + 88;
        ClientHudSettings.Position[] all = ClientHudSettings.Position.values();
        int[][] place = {{0, 0}, {1, 0}, {2, 0}, {0, 1}, {2, 1}, {2, 2}};
        for (int i = 0; i < all.length; i++) {
            ClientHudSettings.Position p = all[i];
            positions[i] = addRenderableWidget(new FlatButton(gx + place[i][0] * (cell + gap), gy + place[i][1] * (12 + 2), cell, 12,
                    Component.literal(p.arrow()), () -> ClientHudSettings.setPosition(p)));
        }

        addRenderableWidget(new FlatButton(px + W - 8 - 60, py + H - BTN - 6, 60, BTN, Component.translatable("gui.done"),
                FlatButton.Style.PRIMARY, this::onClose));
        update();
    }

    private void update() {
        btnMode.setMessage(Component.translatable("gui.politicsmod.hud.settings.mode", ClientHudSettings.mode().label()));
        btnStyle.setMessage(Component.translatable("gui.politicsmod.hud.settings.style", ClientHudSettings.style().label()));
        btnScale.setMessage(Component.translatable("gui.politicsmod.hud.settings.scale", ClientHudSettings.scale()));
        for (int i = 0; i < positions.length; i++) {
            positions[i].selected(ClientHudSettings.position() == ClientHudSettings.Position.values()[i]);
        }
    }

    @Override
    public void tick() {
        super.tick();
        update();
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // No dimming: the panel behind is the preview
        UiKit.panel(g, px, py, W, H);
        g.drawCenteredString(font, title.copy().withStyle(ChatFormatting.BOLD), px + W / 2, py + 7, UiKit.ACCENT);
        UiKit.section(g, font, Component.translatable("gui.politicsmod.hud.settings.position"), px + 8, py + 76);
    }
}
