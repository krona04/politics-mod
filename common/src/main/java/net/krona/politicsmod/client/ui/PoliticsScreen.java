package net.krona.politicsmod.client.ui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Base for all PoliticsMod screens. Since 1.21 vanilla blurs the world (and the HUD with it)
 * behind every screen; our screens only darken it, so the world stays sharp and readable.
 */
public abstract class PoliticsScreen extends Screen {

    protected PoliticsScreen(Component title) {
        super(title);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (minecraft != null && minecraft.level != null) {
            renderTransparentBackground(g);
        } else {
            super.renderBackground(g, mouseX, mouseY, partialTick);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
