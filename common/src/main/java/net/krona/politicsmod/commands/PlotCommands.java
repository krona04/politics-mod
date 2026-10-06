package net.krona.politicsmod.commands;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.krona.politicsmod.PoliticsManager;
import net.krona.politicsmod.economy.PlotService;
import net.krona.politicsmod.platform.Permissions;
import net.krona.politicsmod.politics.Country;
import net.krona.politicsmod.politics.CountryRole;
import net.krona.politicsmod.politics.Plot;
import net.krona.politicsmod.politics.RoleAction;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;

import java.util.Map;
import java.util.UUID;

/** /politicsmod plot ... — every command works on the chunk the player is standing in. */
public final class PlotCommands {

    private PlotCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("politicsmod")
            .then(Commands.literal("plot")
                .requires(src -> Permissions.command(src, "plot"))
                .executes(PlotCommands::info)
                .then(Commands.literal("info").executes(PlotCommands::info))
                .then(Commands.literal("list").executes(PlotCommands::list))
                // Management: leader, or a role allowed to MANAGE_PLOTS (a mayor only in their own city)
                .then(Commands.literal("create").executes(PlotCommands::create))
                .then(Commands.literal("delete").executes(PlotCommands::delete))
                .then(Commands.literal("evict").executes(PlotCommands::evict))
                .then(Commands.literal("price")
                    .then(Commands.literal("sale")
                        .then(Commands.argument("amount", IntegerArgumentType.integer(0))
                            .executes(ctx -> setPrice(ctx, false, IntegerArgumentType.getInteger(ctx, "amount")))))
                    .then(Commands.literal("rent")
                        .then(Commands.argument("amount", IntegerArgumentType.integer(0))
                            .executes(ctx -> setPrice(ctx, true, IntegerArgumentType.getInteger(ctx, "amount"))))))
                // Citizens
                .then(Commands.literal("buy").executes(ctx -> acquire(ctx, false)))
                .then(Commands.literal("rent").executes(ctx -> acquire(ctx, true)))
                // Owner
                .then(Commands.literal("leave").executes(PlotCommands::leave))
                .then(Commands.literal("lock").executes(ctx -> setClosed(ctx, true)))
                .then(Commands.literal("unlock").executes(ctx -> setClosed(ctx, false)))
                .then(Commands.literal("trust")
                    .then(Commands.argument("player", GameProfileArgument.gameProfile())
                        .executes(ctx -> trust(ctx, Plot.Trust.ACCESS))
                        .then(Commands.literal("access").executes(ctx -> trust(ctx, Plot.Trust.ACCESS)))
                        .then(Commands.literal("build").executes(ctx -> trust(ctx, Plot.Trust.BUILD)))))
                .then(Commands.literal("untrust")
                    .then(Commands.argument("player", GameProfileArgument.gameProfile())
                        .executes(PlotCommands::untrust))))
        );
    }

    // ── Info ─────────────────────────────────────────────────────────────

    private static int info(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        PoliticsManager mgr = PoliticsManager.here(player);
        if (mgr == null) return 0;
        Plot plot = mgr.getPlot(player.chunkPosition());
        if (plot == null) {
            return fail(player, "message.politicsmod.plot.none_here");
        }
        player.sendSystemMessage(Component.translatable("gui.politicsmod.plot.header", plot.city, plot.country).withStyle(ChatFormatting.GOLD));
        if (plot.isOwned()) {
            player.sendSystemMessage(Component.translatable(plot.rented ? "gui.politicsmod.plot.tenant" : "gui.politicsmod.plot.owner",
                    plot.ownerName).withStyle(ChatFormatting.YELLOW));
        } else {
            player.sendSystemMessage(Component.translatable("gui.politicsmod.plot.free").withStyle(ChatFormatting.GREEN));
        }
        if (plot.salePrice > 0) player.sendSystemMessage(Component.translatable("gui.politicsmod.plot.sale_price", plot.salePrice).withStyle(ChatFormatting.WHITE));
        if (plot.rentPrice > 0) player.sendSystemMessage(Component.translatable("gui.politicsmod.plot.rent_price", plot.rentPrice).withStyle(ChatFormatting.WHITE));
        if (plot.closed) player.sendSystemMessage(Component.translatable("gui.politicsmod.plot.locked").withStyle(ChatFormatting.RED));
        for (Map.Entry<UUID, Plot.Trust> e : plot.trusted.entrySet()) {
            player.sendSystemMessage(Component.translatable("gui.politicsmod.plot.trusted_entry", plot.trustedNames.getOrDefault(e.getKey(), "?"),
                    Component.translatable("gui.politicsmod.plot.trust." + e.getValue().name().toLowerCase())).withStyle(ChatFormatting.GRAY));
        }
        return 1;
    }

    private static int list(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        PoliticsManager mgr = PoliticsManager.get(player.level());
        if (mgr == null) return 0;
        String mine = mgr.getPlayerCountry(player.getUUID());
        if (mine == null) return fail(player, "message.politicsmod.no_state");

        int shown = 0;
        player.sendSystemMessage(Component.translatable("gui.politicsmod.plot.list_header", mine).withStyle(ChatFormatting.GOLD));
        for (Plot listed : mgr.getPlots()) {
            Plot plot = mgr.getPlot(listed.pos);
            if (plot == null || !plot.country.equals(mine)) continue;
            Component status = plot.isOwned()
                    ? Component.literal(plot.ownerName)
                    : Component.translatable("gui.politicsmod.plot.free_short", plot.salePrice, plot.rentPrice);
            player.sendSystemMessage(Component.translatable("gui.politicsmod.plot.list_entry", plot.city,
                    plot.pos.x, plot.pos.z, status).withStyle(ChatFormatting.WHITE));
            if (++shown >= 30) break;
        }
        if (shown == 0) player.sendSystemMessage(Component.translatable("gui.politicsmod.plot.list_empty").withStyle(ChatFormatting.GRAY));
        return 1;
    }

    // ── Management ───────────────────────────────────────────────────────

    private static int create(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        PoliticsManager mgr = PoliticsManager.here(player);
        if (mgr == null) return 0;
        ChunkPos chunk = player.chunkPosition();
        String owner = mgr.getCountryNameAt(chunk);
        String city = mgr.getCityAt(chunk);
        if (owner == null || city == null) return fail(player, "message.politicsmod.plot.not_city_land");
        if (!canManage(mgr.getCountry(owner), city, player.getUUID())) return fail(player, "message.politicsmod.plot.no_manage");
        if (mgr.getPlot(chunk) != null) return fail(player, "message.politicsmod.plot.already_plot");
        mgr.createPlot(chunk);
        return ok(player, Component.translatable("message.politicsmod.plot.created", city));
    }

    private static int delete(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        PoliticsManager mgr = PoliticsManager.here(player);
        if (mgr == null) return 0;
        Plot plot = managedPlot(mgr, player);
        if (plot == null) return 0;
        notifyOwner(player, plot, Component.translatable("message.politicsmod.plot.removed_notify", plot.city));
        mgr.removePlot(plot.pos);
        return ok(player, Component.translatable("message.politicsmod.plot.deleted"));
    }

    private static int evict(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        PoliticsManager mgr = PoliticsManager.here(player);
        if (mgr == null) return 0;
        Plot plot = managedPlot(mgr, player);
        if (plot == null) return 0;
        if (!plot.isOwned()) return fail(player, "message.politicsmod.plot.not_owned");
        notifyOwner(player, plot, Component.translatable("message.politicsmod.plot.removed_notify", plot.city));
        String name = plot.ownerName;
        plot.release();
        mgr.setDirty();
        return ok(player, Component.translatable("message.politicsmod.plot.evicted", name));
    }

    private static int setPrice(CommandContext<CommandSourceStack> ctx, boolean rent, int amount) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        PoliticsManager mgr = PoliticsManager.here(player);
        if (mgr == null) return 0;
        Plot plot = managedPlot(mgr, player);
        if (plot == null) return 0;
        if (rent) plot.rentPrice = amount;
        else plot.salePrice = amount;
        mgr.setDirty();
        String key = amount == 0
                ? (rent ? "message.politicsmod.plot.rent_off" : "message.politicsmod.plot.sale_off")
                : (rent ? "message.politicsmod.plot.rent_set" : "message.politicsmod.plot.sale_set");
        return ok(player, Component.translatable(key, amount));
    }

    // ── Citizens ─────────────────────────────────────────────────────────

    private static int acquire(CommandContext<CommandSourceStack> ctx, boolean rent) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        PoliticsManager mgr = PoliticsManager.here(player);
        if (mgr == null) return 0;
        Plot plot = mgr.getPlot(player.chunkPosition());
        if (plot == null) return fail(player, "message.politicsmod.plot.none_here");

        PlotService.Result result = PlotService.acquire(mgr, plot, player.getUUID(), player.getGameProfile().getName(), rent);
        return switch (result) {
            case OK -> ok(player, Component.translatable(rent ? "message.politicsmod.plot.rented" : "message.politicsmod.plot.bought",
                    plot.city, rent ? plot.rentPrice : plot.salePrice));
            case TAKEN -> fail(player, "message.politicsmod.plot.taken");
            case NOT_CITIZEN -> fail(player, "message.politicsmod.plot.not_citizen");
            case NOT_FOR_SALE -> fail(player, "message.politicsmod.plot.not_for_sale");
            case NOT_FOR_RENT -> fail(player, "message.politicsmod.plot.not_for_rent");
            case LIMIT -> {
                player.sendSystemMessage(Component.translatable("message.politicsmod.plot.limit",
                        net.krona.politicsmod.config.PoliticsConfig.get().maxPlotsPerPlayer).withStyle(ChatFormatting.RED));
                yield 0;
            }
            case NO_FUNDS -> {
                player.sendSystemMessage(Component.translatable("message.politicsmod.wallet.no_funds",
                        rent ? plot.rentPrice : plot.salePrice).withStyle(ChatFormatting.RED));
                yield 0;
            }
        };
    }

    // ── Owner ────────────────────────────────────────────────────────────

    private static int leave(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        PoliticsManager mgr = PoliticsManager.here(player);
        if (mgr == null) return 0;
        Plot plot = ownedPlot(mgr, player);
        if (plot == null) return 0;
        plot.release();
        mgr.setDirty();
        return ok(player, Component.translatable("message.politicsmod.plot.left", plot.city));
    }

    private static int setClosed(CommandContext<CommandSourceStack> ctx, boolean closed) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        PoliticsManager mgr = PoliticsManager.here(player);
        if (mgr == null) return 0;
        Plot plot = ownedPlot(mgr, player);
        if (plot == null) return 0;
        plot.closed = closed;
        mgr.setDirty();
        return ok(player, Component.translatable(closed ? "message.politicsmod.plot.locked" : "message.politicsmod.plot.unlocked"));
    }

    private static int trust(CommandContext<CommandSourceStack> ctx, Plot.Trust level) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        PoliticsManager mgr = PoliticsManager.here(player);
        if (mgr == null) return 0;
        Plot plot = ownedPlot(mgr, player);
        if (plot == null) return 0;
        GameProfile target = single(ctx);
        if (target == null) return 0;
        if (target.getId().equals(player.getUUID())) return fail(player, "message.politicsmod.wallet.self");
        plot.trusted.put(target.getId(), level);
        plot.trustedNames.put(target.getId(), target.getName());
        mgr.setDirty();
        return ok(player, Component.translatable("message.politicsmod.plot.trusted", target.getName(),
                Component.translatable("gui.politicsmod.plot.trust." + level.name().toLowerCase())));
    }

    private static int untrust(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        PoliticsManager mgr = PoliticsManager.here(player);
        if (mgr == null) return 0;
        Plot plot = ownedPlot(mgr, player);
        if (plot == null) return 0;
        GameProfile target = single(ctx);
        if (target == null) return 0;
        plot.trusted.remove(target.getId());
        plot.trustedNames.remove(target.getId());
        mgr.setDirty();
        return ok(player, Component.translatable("message.politicsmod.plot.untrusted", target.getName()));
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    /** Leader of the country, or a role allowed to MANAGE_PLOTS; a mayor only in their own city (or a city without a mayor). */
    public static boolean canManage(Country country, String city, UUID player) {
        if (country == null) return false;
        CountryRole role = country.getRole(player);
        if (role == CountryRole.LEADER) return true;
        if (!RoleAction.MANAGE_PLOTS.allowed(role)) return false;
        UUID cityMayor = country.cityMayors.get(city);
        return cityMayor == null || cityMayor.equals(player);
    }

    private static Plot managedPlot(PoliticsManager mgr, ServerPlayer player) {
        Plot plot = mgr.getPlot(player.chunkPosition());
        if (plot == null) {
            fail(player, "message.politicsmod.plot.none_here");
            return null;
        }
        if (!canManage(mgr.getCountry(plot.country), plot.city, player.getUUID())) {
            fail(player, "message.politicsmod.plot.no_manage");
            return null;
        }
        return plot;
    }

    private static Plot ownedPlot(PoliticsManager mgr, ServerPlayer player) {
        Plot plot = mgr.getPlot(player.chunkPosition());
        if (plot == null) {
            fail(player, "message.politicsmod.plot.none_here");
            return null;
        }
        if (!player.getUUID().equals(plot.owner)) {
            fail(player, "message.politicsmod.plot.not_yours");
            return null;
        }
        return plot;
    }

    private static GameProfile single(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        var profiles = GameProfileArgument.getGameProfiles(ctx, "player");
        if (profiles.size() != 1) {
            ctx.getSource().sendFailure(Component.translatable("message.politicsmod.wallet.one_player").withStyle(ChatFormatting.RED));
            return null;
        }
        return profiles.iterator().next();
    }

    private static void notifyOwner(ServerPlayer actor, Plot plot, Component message) {
        if (plot.owner == null || plot.owner.equals(actor.getUUID())) return;
        ServerPlayer owner = actor.getServer().getPlayerList().getPlayer(plot.owner);
        if (owner != null) owner.sendSystemMessage(message.copy().withStyle(ChatFormatting.RED));
    }

    private static int ok(ServerPlayer player, Component message) {
        player.sendSystemMessage(message.copy().withStyle(ChatFormatting.GREEN));
        return 1;
    }

    private static int fail(ServerPlayer player, String key) {
        player.sendSystemMessage(Component.translatable(key).withStyle(ChatFormatting.RED));
        return 0;
    }
}
