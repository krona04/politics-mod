package net.krona.politicsmod.client;

import net.krona.politicsmod.network.CreateCountryPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

public class FoundingStoneScreen extends Screen {
    private final BlockPos blockPos;
    private EditBox nameInput;

    public FoundingStoneScreen(BlockPos pos) {
        super(Component.translatable("gui.politicsmod.founding_state.title"));
        this.blockPos = pos;
    }

    @Override
    protected void init() {
        super.init();
        int centerX = this.width / 2;
        int centerY = this.height / 2;

        int inputY = centerY - 10;

        this.nameInput = new EditBox(this.font, centerX - 100, inputY, 200, 20, Component.translatable("gui.politicsmod.founding_state.name_hint"));
        this.nameInput.setBordered(true);
        this.nameInput.setMaxLength(32);
        this.addRenderableWidget(this.nameInput);

        this.addRenderableWidget(Button.builder(Component.translatable("gui.politicsmod.founding_state.create"), (button) -> {
            onPressCreate();
        }).bounds(centerX - 60, inputY + 35, 120, 20).build());
    }

    private void onPressCreate() {
        String country = this.nameInput.getValue();
        if (country.isEmpty()) return;
        PacketDistributor.sendToServer(new CreateCountryPayload(this.blockPos, country));
        ClientPoliticsData.addInitialTerritory(new net.minecraft.world.level.ChunkPos(this.blockPos));
        this.onClose();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g, mouseX, mouseY, partialTick);

        int cx = this.width / 2;
        int cy = this.height / 2;

        int boxW = 240;
        int boxH = 130;
        g.fill(cx - boxW/2, cy - boxH/2, cx + boxW/2, cy + boxH/2, 0xDD000000); // Темный фон
        g.renderOutline(cx - boxW/2, cy - boxH/2, boxW, boxH, 0xFFFFFFFF); // Белая рамка

        super.render(g, mouseX, mouseY, partialTick);

        g.drawCenteredString(this.font,
                Component.translatable("gui.politicsmod.founding_state.header").withStyle(ChatFormatting.BOLD),
                cx, cy - 50, 0xFFD700);

        g.drawString(this.font,
                Component.translatable("gui.politicsmod.founding_state.name_label"),
                cx - 100, cy - 22, 0xAAAAAA);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}