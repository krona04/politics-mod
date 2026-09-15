package net.krona.politicsmod.client;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** Applies /politicsmod borders on the client; triggered by BorderSettingsPayload. */
public final class ClientBorderSettings {

    private ClientBorderSettings() {
    }

    public static void toggle() {
        ClientPoliticsData.toggleRender();
        boolean state = ClientPoliticsData.shouldRender();
        Component stateText = Component.translatable(state ? "command.politicsmod.borders.on" : "command.politicsmod.borders.off")
                .withStyle(state ? ChatFormatting.GREEN : ChatFormatting.RED);
        show(Component.translatable("command.politicsmod.borders.toggle", stateText));
    }

    public static void cycleStyle() {
        ClientPoliticsData.setBorderStyle(ClientPoliticsData.borderStyle + 1);
        String styleKey = switch (ClientPoliticsData.borderStyle) {
            case 1 -> "command.politicsmod.borders.style.walls";
            case 2 -> "command.politicsmod.borders.style.corners";
            default -> "command.politicsmod.borders.style.lines";
        };
        Component styleName = Component.translatable(styleKey).withStyle(ChatFormatting.YELLOW);
        show(Component.translatable("command.politicsmod.borders.style_msg", styleName));
    }

    private static void show(Component message) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            minecraft.player.displayClientMessage(message, false);
        }
    }
}
