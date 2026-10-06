package net.krona.politicsmod.client;

import net.krona.politicsmod.client.ui.FlatButton;
import net.krona.politicsmod.client.ui.PoliticsScreen;
import net.krona.politicsmod.client.ui.UiKit;
import net.krona.politicsmod.network.ModNetworking;
import net.krona.politicsmod.network.UpdateBuildingPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/** Size of the zone a Residential Building counts beds in. */
public class ResidentialScreen extends PoliticsScreen {
    private static final int PW = 250, PH = 150;

    private final BlockPos pos;
    private final int currentPop;
    private EditBox inputX, inputY, inputZ;
    private int px, py;

    public ResidentialScreen(BlockPos pos, int currentPop) {
        super(Component.translatable("gui.politicsmod.residential.title"));
        this.pos = pos;
        this.currentPop = currentPop;
    }

    @Override
    protected void init() {
        px = (width - PW) / 2;
        py = (height - PH) / 2;
        int fx = px + PW - 70;
        inputX = addRenderableWidget(field(fx, py + 42, Component.translatable("gui.politicsmod.residential.radius_x")));
        inputY = addRenderableWidget(field(fx, py + 62, Component.translatable("gui.politicsmod.residential.height_y")));
        inputZ = addRenderableWidget(field(fx, py + 82, Component.translatable("gui.politicsmod.residential.radius_z")));
        Component recalc = Component.translatable("gui.politicsmod.residential.recalc");
        int bw = Math.max(100, FlatButton.widthFor(recalc));
        addRenderableWidget(new FlatButton(px + (PW - bw) / 2, py + PH - 25, bw, 16, recalc, FlatButton.Style.PRIMARY, this::sendUpdate));
    }

    private EditBox field(int x, int y, Component name) {
        EditBox box = new EditBox(font, x, y, 56, 14, name);
        box.setMaxLength(2);
        box.setFilter(v -> v.matches("\\d*"));
        box.setValue("5");
        return box;
    }

    private void sendUpdate() {
        try {
            int x = Integer.parseInt(inputX.getValue());
            int y = Integer.parseInt(inputY.getValue());
            int z = Integer.parseInt(inputZ.getValue());
            ModNetworking.toServer(new UpdateBuildingPayload(pos, x, y, z));
            onClose();
        } catch (NumberFormatException ignored) {
            // an empty field: leave the screen open
        }
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
        super.renderBackground(g, mx, my, pt);
        UiKit.panel(g, px, py, PW, PH);
        g.drawCenteredString(font, Component.translatable("gui.politicsmod.residential.config_title").withStyle(ChatFormatting.BOLD),
                px + PW / 2, py + 10, UiKit.ACCENT);
        g.fill(px + 12, py + 22, px + PW - 12, py + 23, UiKit.PANEL_BORDER);
        UiKit.text(g, font, Component.translatable("gui.politicsmod.residential.pop", currentPop), px + 13, py + 28, PW - 26, UiKit.GOOD);
        int lw = PW - 70 - 13 - 6;
        UiKit.text(g, font, Component.translatable("gui.politicsmod.residential.radius_x"), px + 13, py + 45, lw, UiKit.TEXT_DIM);
        UiKit.text(g, font, Component.translatable("gui.politicsmod.residential.height_y"), px + 13, py + 65, lw, UiKit.TEXT_DIM);
        UiKit.text(g, font, Component.translatable("gui.politicsmod.residential.radius_z"), px + 13, py + 85, lw, UiKit.TEXT_DIM);
    }
}
