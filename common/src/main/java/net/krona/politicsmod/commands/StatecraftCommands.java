package net.krona.politicsmod.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.krona.politicsmod.PoliticsManager;
import net.krona.politicsmod.network.BorderSettingsPayload;
import net.krona.politicsmod.network.ModNetworking;
import net.krona.politicsmod.platform.Permissions;
import net.krona.politicsmod.politics.Country;
import net.krona.politicsmod.realm.Realm;
import net.krona.politicsmod.statecraft.CountryStatecraft;
import net.krona.politicsmod.statecraft.Line;
import net.krona.politicsmod.statecraft.Statecraft;
import net.krona.politicsmod.statecraft.StatecraftService;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/** Events, decisions and goals in chat (0.5.1), and the HUD mode. The dashboard runs the same commands. */
public final class StatecraftCommands {

    private static final String[] HUD_MODES = {"always", "territory", "banner", "off"};

    private StatecraftCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("politicsmod")
            .then(Commands.literal("event")
                .requires(src -> Permissions.command(src, "statecraft"))
                .executes(StatecraftCommands::listEvents)
                .then(Commands.literal("list").executes(StatecraftCommands::listEvents))
                .then(Commands.literal("answer")
                    .then(Commands.argument("id", ResourceLocationArgument.id()).suggests(StatecraftCommands::pendingIds)
                        .then(Commands.argument("option", IntegerArgumentType.integer(1, 9))
                            .executes(ctx -> answer(ctx, ResourceLocationArgument.getId(ctx, "id").toString(),
                                    IntegerArgumentType.getInteger(ctx, "option"))))))
                .then(Commands.literal("fire")
                    .requires(src -> Permissions.command(src, "admin"))
                    .then(Commands.argument("id", ResourceLocationArgument.id())
                        .suggests((ctx, b) -> SharedSuggestionProvider.suggest(Statecraft.events().keySet(), b))
                        .then(Commands.argument("country", StringArgumentType.greedyString()).suggests(StatecraftCommands::countries)
                            .executes(ctx -> fire(ctx, ResourceLocationArgument.getId(ctx, "id").toString(),
                                    StringArgumentType.getString(ctx, "country")))))))
            .then(Commands.literal("decision")
                .requires(src -> Permissions.command(src, "statecraft"))
                .executes(StatecraftCommands::listDecisions)
                .then(Commands.literal("list").executes(StatecraftCommands::listDecisions))
                .then(Commands.literal("take")
                    .then(Commands.argument("id", ResourceLocationArgument.id())
                        .suggests((ctx, b) -> SharedSuggestionProvider.suggest(Statecraft.decisions().stream().map(Statecraft.Decision::id), b))
                        .executes(ctx -> take(ctx, ResourceLocationArgument.getId(ctx, "id").toString(), null))
                        .then(Commands.argument("city", StringArgumentType.greedyString()).suggests(StatecraftCommands::cities)
                            .executes(ctx -> take(ctx, ResourceLocationArgument.getId(ctx, "id").toString(),
                                    StringArgumentType.getString(ctx, "city")))))))
            .then(Commands.literal("goals")
                .requires(src -> Permissions.command(src, "statecraft"))
                .executes(StatecraftCommands::listGoals))
            .then(Commands.literal("hud")
                .requires(src -> Permissions.command(src, "hud"))
                .executes(ctx -> hud(ctx, BorderSettingsPayload.HUD_CYCLE))
                .then(Commands.argument("mode", StringArgumentType.word())
                    .suggests((ctx, b) -> SharedSuggestionProvider.suggest(HUD_MODES, b))
                    .executes(ctx -> {
                        String mode = StringArgumentType.getString(ctx, "mode");
                        for (int i = 0; i < HUD_MODES.length; i++) {
                            if (HUD_MODES[i].equalsIgnoreCase(mode)) return hud(ctx, BorderSettingsPayload.HUD_MODE_FIRST + i);
                        }
                        ctx.getSource().sendFailure(Component.translatable("message.politicsmod.hud.unknown_mode", mode));
                        return 0;
                    }))));
    }

    private static int hud(CommandContext<CommandSourceStack> ctx, int action) throws CommandSyntaxException {
        ModNetworking.toPlayer(ctx.getSource().getPlayerOrException(), new BorderSettingsPayload(action));
        return 1;
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private record Mine(ServerPlayer player, PoliticsManager mgr, Country country) {
    }

    /** The player's country, or null after telling them they have none. */
    private static Mine mine(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        PoliticsManager mgr = PoliticsManager.get(p.level());
        String name = mgr == null ? null : mgr.getPlayerCountry(p.getUUID());
        if (name == null) {
            p.sendSystemMessage(Component.translatable("message.politicsmod.no_country").withStyle(ChatFormatting.RED));
            return null;
        }
        if (!StatecraftService.enabled()) {
            p.sendSystemMessage(Component.translatable("message.politicsmod.statecraft.disabled").withStyle(ChatFormatting.RED));
            return null;
        }
        return new Mine(p, mgr, mgr.getCountry(name));
    }

    private static CompletableFuture<Suggestions> countries(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder b) {
        PoliticsManager mgr = PoliticsManager.get(ctx.getSource().getServer().overworld());
        return mgr == null ? b.buildFuture() : SharedSuggestionProvider.suggest(mgr.getCountries().keySet(), b);
    }

    private static CompletableFuture<Suggestions> cities(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder b) {
        ServerPlayer p = ctx.getSource().getPlayer();
        PoliticsManager mgr = p == null ? null : PoliticsManager.get(p.level());
        String c = mgr == null ? null : mgr.getPlayerCountry(p.getUUID());
        return c == null ? b.buildFuture() : SharedSuggestionProvider.suggest(mgr.getCities(c), b);
    }

    private static CompletableFuture<Suggestions> pendingIds(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder b) {
        ServerPlayer p = ctx.getSource().getPlayer();
        PoliticsManager mgr = p == null ? null : PoliticsManager.get(p.level());
        String c = mgr == null ? null : mgr.getPlayerCountry(p.getUUID());
        if (c == null) return b.buildFuture();
        List<String> ids = new ArrayList<>();
        for (CountryStatecraft.Pending pe : mgr.getCountry(c).statecraft.pending) ids.add(pe.id());
        return SharedSuggestionProvider.suggest(ids, b);
    }

    private static Component hoverLines(MutableComponent text, List<Line> lines, Component first) {
        MutableComponent tip = Component.empty();
        boolean any = false;
        if (first != null) {
            tip.append(first);
            any = true;
        }
        for (Line l : lines) {
            if (any) tip.append("\n");
            tip.append(l.styled());
            any = true;
        }
        if (!any) return text;
        Component tooltip = tip;
        return text.withStyle(s -> s.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, tooltip)));
    }

    // ── Events ───────────────────────────────────────────────────────────

    private static int listEvents(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Mine m = mine(ctx);
        if (m == null) return 0;
        StatecraftService.View v = StatecraftService.view(m.mgr, m.country, m.player.getUUID());
        if (v.events().isEmpty()) {
            m.player.sendSystemMessage(Component.translatable("gui.politicsmod.statecraft.no_events").withStyle(ChatFormatting.GRAY));
            return 1;
        }
        String country = m.country.getName();
        for (StatecraftService.EventView e : v.events()) {
            m.player.sendSystemMessage(Component.literal("★ ").withStyle(ChatFormatting.GOLD)
                    .append(Component.translatable(e.title(), country, e.city()).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD))
                    .append(Component.literal("  ⌛ " + (e.secondsLeft() / 60) + ":" + String.format("%02d", e.secondsLeft() % 60))
                            .withStyle(ChatFormatting.GRAY)));
            m.player.sendSystemMessage(Component.translatable(e.desc(), country, e.city()).withStyle(ChatFormatting.WHITE));
            for (int i = 0; i < e.options().size(); i++) {
                StatecraftService.OptionView o = e.options().get(i);
                String command = "/politicsmod event answer " + e.id() + " " + (i + 1);
                MutableComponent button = Component.literal(" [" + (i + 1) + "] ").withStyle(ChatFormatting.AQUA)
                        .append(Component.translatable(o.text(), country, e.city()).withStyle(o.allowed() && v.canAnswer()
                                ? ChatFormatting.YELLOW : ChatFormatting.DARK_GRAY));
                if (v.canAnswer() && o.allowed()) {
                    button = button.withStyle(s -> s.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command)));
                }
                m.player.sendSystemMessage(hoverLines(button, o.effects(), null));
            }
        }
        if (!v.canAnswer()) {
            m.player.sendSystemMessage(Component.translatable("gui.politicsmod.statecraft.leader_answers").withStyle(ChatFormatting.GRAY));
        }
        return 1;
    }

    private static int answer(CommandContext<CommandSourceStack> ctx, String id, int option) throws CommandSyntaxException {
        Mine m = mine(ctx);
        if (m == null) return 0;
        Realm.Result r = StatecraftService.answer(m.mgr, m.player, m.country, id, option);
        if (!r.ok()) m.player.sendSystemMessage(r.message());
        return r.ok() ? 1 : 0;
    }

    private static int fire(CommandContext<CommandSourceStack> ctx, String id, String countryName) {
        PoliticsManager mgr = PoliticsManager.get(ctx.getSource().getServer().overworld());
        Country c = mgr == null ? null : mgr.getCountry(countryName);
        if (c == null) {
            ctx.getSource().sendFailure(Component.translatable("message.politicsmod.country_not_found", countryName));
            return 0;
        }
        Statecraft.StateEvent ev = Statecraft.event(id);
        if (ev == null) {
            ctx.getSource().sendFailure(Component.translatable("message.politicsmod.event.unknown", id));
            return 0;
        }
        String city = "";
        if (ev.city()) {
            if (c.cities.isEmpty()) {
                ctx.getSource().sendFailure(Component.translatable("message.politicsmod.event.needs_city"));
                return 0;
            }
            city = c.capital != null && c.cities.contains(c.capital) ? c.capital : c.cities.get(0);
        }
        StatecraftService.fire(mgr, c, ev, city);
        ctx.getSource().sendSuccess(() -> Component.translatable("message.politicsmod.event.fired_by_admin", id, c.getName())
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    // ── Decisions ────────────────────────────────────────────────────────

    private static int listDecisions(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Mine m = mine(ctx);
        if (m == null) return 0;
        StatecraftService.View v = StatecraftService.view(m.mgr, m.country, m.player.getUUID());
        if (v.decisions().isEmpty()) {
            m.player.sendSystemMessage(Component.translatable("gui.politicsmod.statecraft.no_decisions").withStyle(ChatFormatting.GRAY));
            return 1;
        }
        for (StatecraftService.DecisionView d : v.decisions()) {
            ChatFormatting color = switch (d.state()) {
                case StatecraftService.AVAILABLE -> ChatFormatting.GREEN;
                case StatecraftService.TAKEN -> ChatFormatting.AQUA;
                case StatecraftService.COOLDOWN -> ChatFormatting.GOLD;
                default -> ChatFormatting.GRAY;
            };
            MutableComponent line = Component.literal(d.state() == StatecraftService.TAKEN ? "✔ " : "• ").withStyle(color)
                    .append(Component.translatable(d.title()).withStyle(color));
            if (d.cost() > 0) line.append(Component.literal("  " + d.cost() + "$").withStyle(ChatFormatting.GRAY));
            List<Line> all = new ArrayList<>(d.requirements());
            all.addAll(d.effects());
            Component hover = hoverLines(line, all, Component.translatable(d.desc(), m.country.getName(), "").withStyle(ChatFormatting.WHITE));
            MutableComponent row = Component.empty().append(hover);
            if (d.state() == StatecraftService.AVAILABLE && v.canDecide()) {
                String cmd = "/politicsmod decision take " + d.id() + (d.target() ? " " : "");
                ClickEvent click = d.target() ? new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, cmd)
                        : new ClickEvent(ClickEvent.Action.RUN_COMMAND, cmd);
                row.append(" ").append(Component.translatable("gui.politicsmod.statecraft.take_chat")
                        .withStyle(s -> s.withColor(ChatFormatting.AQUA).withUnderlined(true).withClickEvent(click)));
            }
            m.player.sendSystemMessage(row);
        }
        return 1;
    }

    private static int take(CommandContext<CommandSourceStack> ctx, String id, String city) throws CommandSyntaxException {
        Mine m = mine(ctx);
        if (m == null) return 0;
        Realm.Result r = StatecraftService.take(m.mgr, m.player, m.country, id, city);
        if (!r.ok()) m.player.sendSystemMessage(r.message());
        return r.ok() ? 1 : 0;
    }

    // ── Goals ────────────────────────────────────────────────────────────

    private static int listGoals(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Mine m = mine(ctx);
        if (m == null) return 0;
        StatecraftService.View v = StatecraftService.view(m.mgr, m.country, m.player.getUUID());
        if (v.goals().isEmpty()) {
            m.player.sendSystemMessage(Component.translatable("gui.politicsmod.statecraft.no_goals").withStyle(ChatFormatting.GRAY));
            return 1;
        }
        for (StatecraftService.GoalView g : v.goals()) {
            MutableComponent line = g.done()
                    ? Component.literal("✔ ").withStyle(ChatFormatting.GREEN).append(Component.translatable(g.title()).withStyle(ChatFormatting.GRAY))
                    : Component.literal("◇ ").withStyle(ChatFormatting.GOLD).append(Component.translatable(g.title()).withStyle(ChatFormatting.WHITE))
                    .append(Component.literal("  " + g.progress() + "%").withStyle(ChatFormatting.GRAY));
            List<Line> all = new ArrayList<>(g.requirements());
            all.addAll(g.reward());
            m.player.sendSystemMessage(hoverLines(line, all, Component.translatable(g.desc(), m.country.getName(), "")
                    .withStyle(ChatFormatting.WHITE)));
        }
        return 1;
    }
}
