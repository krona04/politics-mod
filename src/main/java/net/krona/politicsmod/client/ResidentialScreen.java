package net.krona.politicsmod.client;

import net.krona.politicsmod.block.entity.ResidentialBuildingEntity;
import net.krona.politicsmod.network.UpdateBuildingPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

public class ResidentialScreen extends Screen {
    private final BlockPos pos;
    private final int currentPop;
    private EditBox inputX, inputY, inputZ;

    public ResidentialScreen(BlockPos pos, int currentPop) {
        super(Component.translatable("gui.politicsmod.residential.title"));
        this.pos = pos;
        this.currentPop = currentPop;
    }

    @Override
    protected void init() {
        int cx = width / 2;
        int cy = height / 2;

        this.inputX = new EditBox(font, cx - 50, cy - 40, 100, 20, Component.translatable("gui.politicsmod.residential.radius_x"));
        this.inputY = new EditBox(font, cx - 50, cy - 10, 100, 20, Component.translatable("gui.politicsmod.residential.height_y"));
        this.inputZ = new EditBox(font, cx - 50, cy + 20, 100, 20, Component.translatable("gui.politicsmod.residential.radius_z"));

        this.inputX.setValue("5");
        this.inputY.setValue("5");
        this.inputZ.setValue("5");

        this.addRenderableWidget(inputX);
        this.addRenderableWidget(inputY);
        this.addRenderableWidget(inputZ);

        this.addRenderableWidget(Button.builder(Component.translatable("gui.politicsmod.residential.recalc"), b -> sendUpdate())
                .bounds(cx - 60, cy + 50, 120, 20).build());
    }

    private void sendUpdate() {
        try {
            int x = Integer.parseInt(inputX.getValue());
            int y = Integer.parseInt(inputY.getValue());
            int z = Integer.parseInt(inputZ.getValue());
            PacketDistributor.sendToServer(new UpdateBuildingPayload(pos, x, y, z));
            this.onClose();
        } catch (NumberFormatException e) {
        }
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        this.renderBackground(g, mx, my, pt);
        super.render(g, mx, my, pt);
        g.drawCenteredString(font, Component.translatable("gui.politicsmod.residential.config_title"), width/2, height/2 - 80, 0xFFFFFF);
        String popText = Component.translatable("gui.politicsmod.residential.pop", currentPop).getString();
        g.drawCenteredString(font, popText, width/2, height/2 - 65, 0x00FF00);

        g.drawString(font, "X:", width/2 - 65, height/2 - 35, 0xAAAAAA);
        g.drawString(font, "Y:", width/2 - 65, height/2 - 5, 0xAAAAAA);
        g.drawString(font, "Z:", width/2 - 65, height/2 + 25, 0xAAAAAA);
    }
}
