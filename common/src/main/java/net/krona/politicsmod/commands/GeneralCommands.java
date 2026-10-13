package net.krona.politicsmod.commands;

import com.mojang.brigadier.CommandDispatcher;
import net.krona.politicsmod.item.GuideBookItem;
import net.krona.politicsmod.platform.Permissions;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

/** /politicsmod help and /politicsmod guide. */
public final class GeneralCommands {

    /** Help lines, in the order shown: gui.politicsmod.help.&lt;key&gt;. */
    private static final String[] HELP = {"dashboard", "info", "wallet", "salary", "claim", "plot", "flag",
            "diplomacy", "election", "law", "siege", "loan", "news", "settlement", "market", "exchange", "delete", "guide"};

    private GeneralCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("politicsmod")
            .then(Commands.literal("help")
                .requires(src -> Permissions.command(src, "help"))
                .executes(ctx -> {
                    var src = ctx.getSource();
                    src.sendSuccess(() -> Component.translatable("command.politicsmod.help.header").withStyle(ChatFormatting.GOLD), false);
                    for (String key : HELP) {
                        src.sendSuccess(() -> Component.translatable("gui.politicsmod.help." + key).withStyle(ChatFormatting.YELLOW), false);
                    }
                    return 1;
                }))
            .then(Commands.literal("guide")
                .requires(src -> Permissions.command(src, "guide"))
                .executes(ctx -> {
                    GuideBookItem.give(ctx.getSource().getPlayerOrException());
                    return 1;
                }))
        );
    }
}
