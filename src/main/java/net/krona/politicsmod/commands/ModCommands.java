package net.krona.politicsmod.commands;

import net.krona.politicsmod.Politicsmod;
import net.krona.politicsmod.PoliticsManager;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ModCommands {

    private static final Map<UUID, Long> deleteRequests = new HashMap<>();

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("politicsmod")

                .then(Commands.literal("help")
                        .executes(context -> {
                            context.getSource().sendSuccess(() -> Component.translatable("command.politicsmod.help.header").withStyle(ChatFormatting.GOLD), false);
                            context.getSource().sendSuccess(() -> Component.translatable("command.politicsmod.help.delete").withStyle(ChatFormatting.YELLOW), false);
                            return 1;
                        })
                )

                .then(Commands.literal("delete")
                        .executes(ModCommands::requestDelete)
                )

                .then(Commands.literal("confirm")
                        .executes(ModCommands::confirmDelete)
                )
        );
    }

    private static int requestDelete(CommandContext<CommandSourceStack> context) {
        try {
            ServerPlayer player = context.getSource().getPlayerOrException();
            PoliticsManager manager = PoliticsManager.get(player.level());
            String country = manager.getCountryByOwner(player.getUUID());

            if (country == null) {
                context.getSource().sendFailure(Component.translatable("message.politicsmod.no_state"));
                return 0;
            }

            deleteRequests.put(player.getUUID(), System.currentTimeMillis());

            player.sendSystemMessage(Component.translatable("command.politicsmod.delete.warning").withStyle(ChatFormatting.RED));
            player.sendSystemMessage(Component.translatable("command.politicsmod.delete.confirm_hint").withStyle(ChatFormatting.GRAY));

        } catch (Exception e) {
            e.printStackTrace();
        }
        return 1;
    }

    private static int confirmDelete(CommandContext<CommandSourceStack> context) {
        try {
            ServerPlayer player = context.getSource().getPlayerOrException();
            UUID uuid = player.getUUID();

            if (!deleteRequests.containsKey(uuid)) {
                context.getSource().sendFailure(Component.translatable("command.politicsmod.delete.no_request"));
                return 0;
            }

            long requestTime = deleteRequests.get(uuid);
            if (System.currentTimeMillis() - requestTime > 30000) {
                deleteRequests.remove(uuid);
                context.getSource().sendFailure(Component.translatable("command.politicsmod.delete.timeout"));
                return 0;
            }

            PoliticsManager manager = PoliticsManager.get(player.level());
            String country = manager.getCountryByOwner(uuid);

            if (country != null) {
                manager.deleteCountry(country);
                player.sendSystemMessage(Component.translatable("command.politicsmod.delete.success", country).withStyle(ChatFormatting.GREEN));
            }

            deleteRequests.remove(uuid);

        } catch (Exception e) {
            e.printStackTrace();
        }
        return 1;
    }
}
