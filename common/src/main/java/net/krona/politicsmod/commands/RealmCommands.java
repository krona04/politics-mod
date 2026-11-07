package net.krona.politicsmod.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.krona.politicsmod.PoliticsManager;
import net.krona.politicsmod.config.PoliticsConfig;
import net.krona.politicsmod.platform.Permissions;
import net.krona.politicsmod.politics.Country;
import net.krona.politicsmod.politics.CountryRole;
import net.krona.politicsmod.realm.Breakdown;
import net.krona.politicsmod.realm.ModifierType;
import net.krona.politicsmod.realm.Realm;
import net.krona.politicsmod.realm.TimedModifier;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/** The realm: /politicsmod realm, restoring order, developing cities and (operators) stored modifiers. */
public final class RealmCommands {

    private RealmCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("politicsmod")
            .then(Commands.literal("realm")
                .requires(src -> Permissions.command(src, "realm"))
                .executes(RealmCommands::status)
                .then(Commands.literal("stabilize").executes(RealmCommands::stabilize)))
            .then(Commands.literal("city")
                .then(Commands.literal("develop")
                    .then(Commands.argument("cityName", StringArgumentType.greedyString())
                        .suggests((ctx, b) -> {
                            ServerPlayer p = ctx.getSource().getPlayer();
                            PoliticsManager mgr = p == null ? null : PoliticsManager.get(p.level());
                            String c = mgr == null ? null : mgr.getPlayerCountry(p.getUUID());
                            return c == null ? b.buildFuture() : SharedSuggestionProvider.suggest(mgr.getCities(c), b);
                        })
                        .executes(ctx -> develop(ctx, StringArgumentType.getString(ctx, "cityName"))))))
            .then(Commands.literal("modifier")
                .requires(src -> Permissions.command(src, "admin"))
                // The country comes last and takes the rest of the line, so names with spaces or any letters need no quotes
                .then(Commands.literal("list")
                    .then(Commands.argument("country", StringArgumentType.greedyString()).suggests(RealmCommands::countries)
                        .executes(ctx -> listModifiers(ctx, StringArgumentType.getString(ctx, "country")))))
                .then(Commands.literal("add")
                    .then(Commands.argument("type", StringArgumentType.word())
                        .suggests((ctx, b) -> SharedSuggestionProvider.suggest(Arrays.stream(ModifierType.values()).map(ModifierType::key), b))
                        .then(Commands.argument("percent", IntegerArgumentType.integer(-100, 1000))
                            .then(Commands.argument("minutes", IntegerArgumentType.integer(0))
                                .then(Commands.argument("name", StringArgumentType.string())
                                    .then(Commands.argument("country", StringArgumentType.greedyString()).suggests(RealmCommands::countries)
                                        .executes(ctx -> addModifier(ctx,
                                                StringArgumentType.getString(ctx, "country"),
                                                StringArgumentType.getString(ctx, "type"),
                                                IntegerArgumentType.getInteger(ctx, "percent"),
                                                IntegerArgumentType.getInteger(ctx, "minutes"),
                                                StringArgumentType.getString(ctx, "name")))))))))
                .then(Commands.literal("remove")
                    .then(Commands.argument("id", StringArgumentType.word())
                        .then(Commands.argument("country", StringArgumentType.greedyString()).suggests(RealmCommands::countries)
                            .executes(ctx -> removeModifier(ctx, StringArgumentType.getString(ctx, "country"),
                                    StringArgumentType.getString(ctx, "id")))))))
        );
    }

    private static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> countries(
            CommandContext<CommandSourceStack> ctx, com.mojang.brigadier.suggestion.SuggestionsBuilder builder) {
        PoliticsManager mgr = PoliticsManager.get(ctx.getSource().getServer().overworld());
        return mgr == null ? builder.buildFuture() : SharedSuggestionProvider.suggest(mgr.getCountries().keySet(), builder);
    }

    // ── Status ───────────────────────────────────────────────────────────

    private static int status(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        PoliticsManager mgr = PoliticsManager.get(p.level());
        Country c = mgr == null ? null : country(p, mgr);
        if (c == null) return 0;
        if (!Realm.enabled()) {
            p.sendSystemMessage(Component.translatable("message.politicsmod.realm.disabled").withStyle(ChatFormatting.GRAY));
            return 1;
        }
        Map<String, Realm.CityStats> cities = Realm.cities(mgr, c);
        p.sendSystemMessage(Component.translatable("message.politicsmod.realm.header", c.getName()).withStyle(ChatFormatting.GOLD));

        Breakdown trend = Realm.stabilityTrend(mgr, c, cities);
        p.sendSystemMessage(hover(Component.translatable("message.politicsmod.realm.stability", Realm.signed(c.stability),
                Realm.signed(c.stabilityProgress), PoliticsConfig.get().stabilityProgressPerLevel, Breakdown.format(trend.total(), 1))
                .withStyle(c.stability < 0 ? ChatFormatting.RED : ChatFormatting.YELLOW), trend.lines("", 1, true)));

        Breakdown legit = Realm.legitimacyTrend(c);
        p.sendSystemMessage(hover(Component.translatable("message.politicsmod.realm.legitimacy", Math.round(c.legitimacy),
                Breakdown.format(legit.total(), 10)).withStyle(ChatFormatting.YELLOW), legit.lines("", 10, true)));
        p.sendSystemMessage(Component.translatable("message.politicsmod.realm.prestige", Realm.signed(Math.round(c.prestige)))
                .withStyle(ChatFormatting.YELLOW));

        int chunks = mgr.getChunksOf(c.getName()).size();
        int over = Realm.overextension(mgr, c.getName());
        p.sendSystemMessage(Component.translatable("message.politicsmod.realm.cores", over, chunks * over / 100, chunks,
                mgr.getLostCores(c.getName()).size()).withStyle(over > 25 ? ChatFormatting.RED : ChatFormatting.GRAY));

        for (ModifierType type : ModifierType.values()) {
            Breakdown b = Realm.modifiers(c, type);
            if (b.isEmpty()) continue;
            boolean goodIsPositive = type != ModifierType.UPKEEP && type != ModifierType.LAND_PRICE;
            p.sendSystemMessage(hover(Component.translatable("message.politicsmod.realm.modifier", Component.translatable(type.nameKey()),
                    Breakdown.format(b.total(), 1) + "%").withStyle(ChatFormatting.AQUA), b.lines("%", 1, goodIsPositive)));
        }
        for (Map.Entry<String, Realm.CityStats> e : cities.entrySet()) {
            Realm.CityStats s = e.getValue();
            p.sendSystemMessage(hover(Component.translatable("message.politicsmod.realm.city", e.getKey(), s.development(),
                    s.unrestValue()).withStyle(s.unrestValue() >= PoliticsConfig.get().unrestSecedeThreshold ? ChatFormatting.RED
                    : ChatFormatting.WHITE), s.unrest().lines("", 1, false)));
        }
        if (c.getRole(p.getUUID()) == CountryRole.LEADER && c.stability < 3) {
            p.sendSystemMessage(Component.translatable("message.politicsmod.realm.stabilize_hint", Realm.stabilizeCost(c),
                    PoliticsConfig.get().stabilizeProgress).withStyle(ChatFormatting.GRAY));
        }
        return 1;
    }

    /** The message with an "ⓘ" whose tooltip lists the parts. */
    private static Component hover(MutableComponent text, List<Component> lines) {
        if (lines.isEmpty()) return text;
        MutableComponent tip = Component.empty();
        for (int i = 0; i < lines.size(); i++) tip.append(i == 0 ? Component.empty() : Component.literal("\n")).append(lines.get(i));
        return text.append(Component.literal(" ⓘ").withStyle(ChatFormatting.GRAY))
                .withStyle(s -> s.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, tip)));
    }

    // ── Actions ──────────────────────────────────────────────────────────

    private static int stabilize(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        PoliticsManager mgr = PoliticsManager.get(p.level());
        Country c = mgr == null ? null : country(p, mgr);
        if (c == null) return 0;
        if (c.getRole(p.getUUID()) != CountryRole.LEADER) {
            p.sendSystemMessage(Component.translatable("message.politicsmod.diplomacy.leader_only").withStyle(ChatFormatting.RED));
            return 0;
        }
        Realm.Result result = Realm.stabilize(mgr, c);
        p.sendSystemMessage(result.message());
        return result.ok() ? 1 : 0;
    }

    private static int develop(CommandContext<CommandSourceStack> ctx, String city) throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        PoliticsManager mgr = PoliticsManager.get(p.level());
        Country c = mgr == null ? null : country(p, mgr);
        if (c == null) return 0;
        Realm.Result result = Realm.develop(mgr, p, c, city);
        p.sendSystemMessage(result.message());
        return result.ok() ? 1 : 0;
    }

    private static Country country(ServerPlayer p, PoliticsManager mgr) {
        String name = mgr.getPlayerCountry(p.getUUID());
        if (name == null) {
            p.sendSystemMessage(Component.translatable("message.politicsmod.no_state").withStyle(ChatFormatting.RED));
            return null;
        }
        return mgr.getCountry(name);
    }

    // ── Stored modifiers (operators) ─────────────────────────────────────

    private static Country target(CommandContext<CommandSourceStack> ctx, String name) {
        PoliticsManager mgr = PoliticsManager.get(ctx.getSource().getServer().overworld());
        Country c = mgr == null ? null : mgr.getCountry(name);
        if (c == null) ctx.getSource().sendFailure(Component.translatable("message.politicsmod.country_not_found", name));
        return c;
    }

    private static int listModifiers(CommandContext<CommandSourceStack> ctx, String name) {
        Country c = target(ctx, name);
        if (c == null) return 0;
        long now = System.currentTimeMillis();
        ctx.getSource().sendSuccess(() -> Component.translatable("message.politicsmod.modifier.header", c.getName())
                .withStyle(ChatFormatting.GOLD), false);
        if (c.modifiers.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.translatable("message.politicsmod.modifier.none").withStyle(ChatFormatting.GRAY), false);
        }
        for (TimedModifier m : c.modifiers) {
            String left = m.expiresAt() == 0 ? "∞" : ((Math.max(0, m.expiresAt() - now) / 60_000) + 1) + "m";
            ctx.getSource().sendSuccess(() -> Component.literal(" [" + m.id() + "] ")
                    .append(Component.translatable("realm.politicsmod.source.named", m.name()))
                    .append(" — ").append(Component.translatable(m.type().nameKey()))
                    .append(" " + Breakdown.format(m.value(), 1) + "%, " + left).withStyle(ChatFormatting.WHITE), false);
        }
        return 1;
    }

    private static int addModifier(CommandContext<CommandSourceStack> ctx, String name, String typeKey, int percent,
                                   int minutes, String label) {
        Country c = target(ctx, name);
        if (c == null) return 0;
        ModifierType type = ModifierType.byKey(typeKey);
        if (type == null) {
            ctx.getSource().sendFailure(Component.translatable("message.politicsmod.modifier.unknown_type", typeKey));
            return 0;
        }
        int next = 1;
        for (TimedModifier m : c.modifiers) {
            try { next = Math.max(next, Integer.parseInt(m.id()) + 1); } catch (NumberFormatException ignored) { }
        }
        long expires = minutes == 0 ? 0 : System.currentTimeMillis() + minutes * 60_000L;
        TimedModifier m = new TimedModifier(String.valueOf(next), label, type, percent, expires);
        c.modifiers.add(m);
        PoliticsManager.get(ctx.getSource().getServer().overworld()).setDirty();
        ctx.getSource().sendSuccess(() -> Component.translatable("message.politicsmod.modifier.added", m.id(), c.getName())
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int removeModifier(CommandContext<CommandSourceStack> ctx, String name, String id) {
        Country c = target(ctx, name);
        if (c == null) return 0;
        if (!c.modifiers.removeIf(m -> m.id().equals(id))) {
            ctx.getSource().sendFailure(Component.translatable("message.politicsmod.modifier.not_found", id));
            return 0;
        }
        PoliticsManager.get(ctx.getSource().getServer().overworld()).setDirty();
        ctx.getSource().sendSuccess(() -> Component.translatable("message.politicsmod.modifier.removed", id, c.getName())
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }
}
