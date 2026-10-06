package net.krona.politicsmod.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.krona.politicsmod.PoliticsManager;
import net.krona.politicsmod.platform.Permissions;
import net.krona.politicsmod.politics.Country;
import net.krona.politicsmod.politics.CountryRole;
import net.krona.politicsmod.protection.ProtectionFlag;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

/**
 * /politicsmod flag ... — protection flags for the whole country (leader) or one city (leader or its mayor).
 * Values: on, off, default (inherit from the country, or from the server config).
 */
public final class FlagCommands {

    private static final SuggestionProvider<CommandSourceStack> FLAGS = (ctx, builder) ->
            SharedSuggestionProvider.suggest(Arrays.stream(ProtectionFlag.values()).map(ProtectionFlag::key), builder);
    private static final SuggestionProvider<CommandSourceStack> VALUES = (ctx, builder) ->
            SharedSuggestionProvider.suggest(new String[]{"on", "off", "default"}, builder);

    private FlagCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("politicsmod")
            .then(Commands.literal("flag")
                .requires(src -> Permissions.command(src, "flag"))
                .executes(FlagCommands::showHere)
                .then(Commands.literal("set")
                    .then(Commands.argument("flag", StringArgumentType.word()).suggests(FLAGS)
                        .then(Commands.argument("value", StringArgumentType.word()).suggests(VALUES)
                            .executes(ctx -> set(ctx, null)))))
                .then(Commands.literal("city")
                    .then(Commands.argument("city", StringArgumentType.string())
                        .then(Commands.argument("flag", StringArgumentType.word()).suggests(FLAGS)
                            .then(Commands.argument("value", StringArgumentType.word()).suggests(VALUES)
                                .executes(ctx -> set(ctx, StringArgumentType.getString(ctx, "city"))))))))
        );
    }

    private static int showHere(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        PoliticsManager mgr = PoliticsManager.here(player);
        if (mgr == null) return 0;
        ChunkPos chunk = player.chunkPosition();
        String owner = mgr.getCountryNameAt(chunk);
        if (owner == null) {
            player.sendSystemMessage(Component.translatable("message.politicsmod.protection.wilderness").withStyle(ChatFormatting.GRAY));
            return 1;
        }
        String city = mgr.getCityAt(chunk);
        player.sendSystemMessage(Component.translatable("gui.politicsmod.flag.header", city != null ? city + ", " + owner : owner)
                .withStyle(ChatFormatting.GOLD));
        for (ProtectionFlag flag : ProtectionFlag.values()) {
            boolean value = mgr.isAllowed(chunk, flag);
            player.sendSystemMessage(Component.translatable("gui.politicsmod.flag.entry",
                    Component.translatable("gui.politicsmod.flag." + flag.key()),
                    Component.translatable(value ? "gui.politicsmod.flag.on" : "gui.politicsmod.flag.off")
                            .withStyle(value ? ChatFormatting.GREEN : ChatFormatting.RED)));
        }
        return 1;
    }

    private static int set(CommandContext<CommandSourceStack> ctx, String city) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        PoliticsManager mgr = PoliticsManager.get(player.level());
        if (mgr == null) return 0;
        String mine = mgr.getPlayerCountry(player.getUUID());
        if (mine == null) return fail(player, "message.politicsmod.no_state");
        Country country = mgr.getCountry(mine);

        ProtectionFlag flag = ProtectionFlag.byKey(StringArgumentType.getString(ctx, "flag"));
        if (flag == null) return fail(player, "message.politicsmod.protection.unknown");
        String raw = StringArgumentType.getString(ctx, "value");
        Boolean value = switch (raw) {
            case "on" -> Boolean.TRUE;
            case "off" -> Boolean.FALSE;
            case "default" -> null;
            default -> Boolean.TRUE;
        };
        if (!raw.equals("on") && !raw.equals("off") && !raw.equals("default")) return fail(player, "message.politicsmod.protection.bad_value");

        UUID id = player.getUUID();
        Map<ProtectionFlag, Boolean> target;
        if (city == null) {
            if (country.getRole(id) != CountryRole.LEADER) return fail(player, "message.politicsmod.protection.leader_only");
            target = country.flags;
        } else {
            if (!country.cities.contains(city)) {
                player.sendSystemMessage(Component.translatable("message.politicsmod.city.not_found", city).withStyle(ChatFormatting.RED));
                return 0;
            }
            boolean mayor = id.equals(country.cityMayors.get(city));
            if (country.getRole(id) != CountryRole.LEADER && !mayor) return fail(player, "message.politicsmod.protection.city_role");
            target = country.cityFlags.computeIfAbsent(city, c -> new EnumMap<>(ProtectionFlag.class));
        }

        if (value == null) target.remove(flag);
        else target.put(flag, value);
        mgr.setDirty();

        Component flagName = Component.translatable("gui.politicsmod.flag." + flag.key());
        Component valueName = Component.translatable(value == null ? "gui.politicsmod.flag.default"
                : value ? "gui.politicsmod.flag.on" : "gui.politicsmod.flag.off");
        player.sendSystemMessage(Component.translatable("message.politicsmod.protection.set", flagName, city != null ? city : mine, valueName)
                .withStyle(ChatFormatting.GREEN));
        return 1;
    }

    private static int fail(ServerPlayer player, String key) {
        player.sendSystemMessage(Component.translatable(key).withStyle(ChatFormatting.RED));
        return 0;
    }
}
