package net.krona.politicsmod.client;

import net.krona.politicsmod.network.FoundCityPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

public class CityFoundationScreen extends Screen {
    private final BlockPos pos;
    private EditBox nameInput;

    public CityFoundationScreen(BlockPos pos) {
        super(Component.translatable("gui.politicsmod.founding.title"));
        this.pos = pos;
    }

    @Override
    protected void init() {
        super.init();
        int centerX = this.width / 2;
        int centerY = this.height / 2;

        this.nameInput = new EditBox(this.font, centerX - 100, centerY - 20, 200, 20, Component.literal("Название города"));
        this.nameInput.setBordered(true);
        this.addRenderableWidget(this.nameInput);

        this.addRenderableWidget(Button.builder(Component.translatable("gui.politicsmod.founding.build"), b -> {
            String name = nameInput.getValue();
            if(!name.isEmpty()) {
                PacketDistributor.sendToServer(new FoundCityPayload(pos, name));
                this.onClose();
            }
        }).bounds(centerX - 60, centerY + 20, 120, 20).build());
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        this.renderBackground(g, mx, my, pt);

        int cx = width/2; int cy = height/2;
        g.fill(cx-120, cy-60, cx+120, cy+60, 0xCC000000);
        g.renderOutline(cx-120, cy-60, 240, 120, 0xFFFFFFFF);

        super.render(g, mx, my, pt);
        g.drawCenteredString(this.font, Component.translatable("gui.politicsmod.founding.header"), cx, cy - 45, 0xFFD700);
    }
}
