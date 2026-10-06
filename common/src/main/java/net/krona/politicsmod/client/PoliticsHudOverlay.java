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


        if (ClientPoliticsData.plotHere) {
            y += 10;
            Component plotLine;
            if (!ClientPoliticsData.plotOwner.isEmpty()) {
                plotLine = Component.translatable("gui.politicsmod.hud.plot_owner", ClientPoliticsData.plotOwner);
            } else if (ClientPoliticsData.plotSale > 0) {
                plotLine = Component.translatable("gui.politicsmod.hud.plot_sale", ClientPoliticsData.plotSale);
            } else if (ClientPoliticsData.plotRent > 0) {
                plotLine = Component.translatable("gui.politicsmod.hud.plot_rent", ClientPoliticsData.plotRent);
            } else {
                plotLine = Component.translatable("gui.politicsmod.hud.plot_free");
            }
            guiGraphics.drawString(font, plotLine, x, y, 0xAAAAFF);
        }

        if (ClientPoliticsData.myTreasury != net.krona.politicsmod.network.SyncHudPayload.NO_COUNTRY) {
            y += 10;
            guiGraphics.drawString(font,
                    Component.translatable("gui.politicsmod.hud.treasury", ClientPoliticsData.myTreasury),
                    x, y, ClientPoliticsData.myTreasury < 0 ? 0xFF5555 : colorBalance);
        }

        y += 10;
        guiGraphics.drawString(font,
                Component.translatable("gui.politicsmod.hud.wallet", ClientPoliticsData.myWallet),
                x, y, 0xFFD700);

        if (ClientPoliticsData.hasActiveRadarAlert()) {
            y += 10;
            guiGraphics.drawString(font,
                    Component.translatable("gui.politicsmod.hud.radar_alert", ClientPoliticsData.radarAlert),
                    x, y, 0xFF5555);
        }
    }
}
