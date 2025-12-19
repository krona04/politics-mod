package net.krona.politicsmod;

import net.krona.politicsmod.network.SyncChunkPayload;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = Politicsmod.MODID, bus = EventBusSubscriber.Bus.GAME)
public class ModCommands {

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("politicsmod")
                        .then(Commands.literal("claim")
                                .executes(ctx -> claimSingleChunk(ctx, null))
                                .then(Commands.argument("cityName", StringArgumentType.greedyString())
                                        .executes(ctx -> claimSingleChunk(ctx, StringArgumentType.getString(ctx, "cityName")))
                                )
                        )
                        .then(Commands.literal("claim_radius")
                                .then(Commands.argument("radius", IntegerArgumentType.integer(1, 5))
                                        .executes(ctx -> claimRadius(ctx, IntegerArgumentType.getInteger(ctx, "radius"), null))
                                        .then(Commands.argument("cityName", StringArgumentType.greedyString())
                                                .executes(ctx -> claimRadius(ctx, IntegerArgumentType.getInteger(ctx, "radius"), StringArgumentType.getString(ctx, "cityName")))
                                        )
                                )
                        )
                        // КОМАНДА ПЕРЕИМЕНОВАНИЯ
                        .then(Commands.literal("rename")
                                .then(Commands.literal("country")
                                        .then(Commands.argument("newName", StringArgumentType.greedyString())
                                                .executes(ctx -> renameCountry(ctx, StringArgumentType.getString(ctx, "newName")))
                                        )
                                )
                        )
        );
    }

    private static int renameCountry(CommandContext<CommandSourceStack> ctx, String newName) {
        try {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            PoliticsManager manager = PoliticsManager.get(player.level());

            if (manager == null) return 0;
            String currentCountry = manager.getCountryByOwner(player.getUUID());

            if (currentCountry == null) {
                ctx.getSource().sendFailure(Component.translatable("message.politicscraft.no_state"));
                return 0;
            }

            if (manager.renameCountry(currentCountry, newName)) {
                ctx.getSource().sendSuccess(() -> Component.translatable("message.politicscraft.country_renamed", newName), true);
                return 1;
            } else {
                ctx.getSource().sendFailure(Component.translatable("message.politicscraft.error.name_taken"));
                return 0;
            }
        } catch (Exception e) {
            return 0;
        }
    }

    private static int claimSingleChunk(CommandContext<CommandSourceStack> ctx, String cityName) {
        try {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            return processClaim(ctx, player, player.chunkPosition(), cityName);
        } catch (Exception e) { return 0; }
    }

    private static int claimRadius(CommandContext<CommandSourceStack> ctx, int radius, String cityName) {
        try {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            ChunkPos center = player.chunkPosition();
            int count = 0;
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    ChunkPos targetPos = new ChunkPos(center.x + x, center.z + z);
                    if (processClaim(ctx, player, targetPos, cityName) == 1) count++;
                }
            }
            final int finalCount = count;
            ctx.getSource().sendSuccess(() -> Component.translatable("message.politicsmod.command.claimed", finalCount), true);
            return count;
        } catch (Exception e) { return 0; }
    }

    private static int processClaim(CommandContext<CommandSourceStack> ctx, ServerPlayer player, ChunkPos chunkPos, String cityName) {
        PoliticsManager manager = PoliticsManager.get(player.level());
        if (manager == null) return 0;

        String countryName = manager.getCountryByOwner(player.getUUID());
        if (countryName == null) {
            if (cityName == null) ctx.getSource().sendFailure(Component.translatable("message.politicsmod.no_state")); // Или message.politicscraft.command.no_country_arg
            return 0;
        }

        boolean isCityClaim = (cityName != null);
        if (isCityClaim && !manager.hasCity(countryName, cityName)) return 0;

        manager.claimChunk(chunkPos, 0, countryName, isCityClaim ? cityName : null);

        int color = manager.getColorForChunk(chunkPos);
        PacketDistributor.sendToPlayer(player, new SyncChunkPayload(chunkPos, color));
        return 1;
    }
}
