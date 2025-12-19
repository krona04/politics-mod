package net.krona.politicsmod.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public class PoliticsHudOverlay implements LayeredDraw.Layer {

    public static final ResourceLocation HUD_ID = ResourceLocation.fromNamespaceAndPath("politicsmod", "hud");

    @Override
    public void render(GuiGraphics guiGraphics, DeltaTracker deltaTracker) {
        int x = 10;
        int y = 10;
        int colorCountry = 0x00FF00;
        int colorCity = 0xFFD700;
        int colorBalance = 0x55FF55;

        var font = net.minecraft.client.Minecraft.getInstance().font;


        guiGraphics.drawString(font,
                Component.translatable("gui.politicsmod.hud.country", ClientPoliticsData.currentCountry),
                x, y, colorCountry);


        if (!ClientPoliticsData.currentCity.isEmpty()) {
            y += 10;
            guiGraphics.drawString(font,
                    Component.translatable("gui.politicsmod.hud.city", ClientPoliticsData.currentCity),
                    x, y, colorCity);
        }


        y += 10;
        guiGraphics.drawString(font,
                Component.translatable("gui.politicsmod.hud.balance", ClientPoliticsData.myBalance),
                x, y, colorBalance);
    }
}
