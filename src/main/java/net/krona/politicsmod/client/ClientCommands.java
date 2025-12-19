package net.krona.politicsmod.client;

import net.krona.politicsmod.Politicsmod;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

@EventBusSubscriber(modid = Politicsmod.MODID, value = Dist.CLIENT)
public class ClientCommands {

    @SubscribeEvent
    public static void onRegisterCommands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("politicsmod")
                        .then(Commands.literal("borders")
                                .executes(context -> {
                                    ClientPoliticsData.toggleRender();
                                    boolean state = ClientPoliticsData.shouldRender();

                                    Component stateText = Component.translatable(state ? "command.politicsmod.borders.on" : "command.politicsmod.borders.off")
                                            .withStyle(state ? net.minecraft.ChatFormatting.GREEN : net.minecraft.ChatFormatting.RED);

                                    context.getSource().sendSystemMessage(Component.translatable("command.politicsmod.borders.toggle", stateText));
                                    return 1;
                                })
                                .then(Commands.literal("style")
                                        .executes(context -> {
                                            int current = ClientPoliticsData.borderStyle;
                                            ClientPoliticsData.setBorderStyle(current + 1);

                                            String styleKey = switch (ClientPoliticsData.borderStyle) {
                                                case 0 -> "command.politicsmod.borders.style.lines";
                                                case 1 -> "command.politicsmod.borders.style.walls";
                                                case 2 -> "command.politicsmod.borders.style.corners";
                                                default -> "command.politicsmod.borders.style.lines";
                                            };

                                            Component styleName = Component.translatable(styleKey).withStyle(net.minecraft.ChatFormatting.YELLOW);
                                            context.getSource().sendSystemMessage(Component.translatable("command.politicsmod.borders.style_msg", styleName));
                                            return 1;
                                        })
                                )
                        )
        );
    }
}
