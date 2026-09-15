package net.krona.politicsmod.market;

import net.krona.politicsmod.PoliticsManager;
import net.krona.politicsmod.TradeWarehouseBlock;
import net.krona.politicsmod.config.PoliticsConfig;
import net.krona.politicsmod.network.MarketDataPayload;
import net.krona.politicsmod.network.ModNetworking;
import net.krona.politicsmod.politics.Country;
import net.krona.politicsmod.politics.CountryRole;
import net.krona.politicsmod.politics.MarketListing;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Server-side market logic. Only the listing id and price come from the client:
 * permissions, money, embargo and item presence are checked here on every action.
 */
public final class MarketService {

    public static final int ACTION_LIST = 0;
    public static final int ACTION_BUY = 1;
    public static final int ACTION_CANCEL = 2;

    /** Maximum distance to the warehouse for market actions (same as a chest). */
    private static final double MAX_DISTANCE_SQR = 8 * 8;

    /** Last warehouse opened by each player, so actions can't be sent from far away. */
    private static final Map<UUID, BlockPos> SESSIONS = new HashMap<>();

    private MarketService() {
    }

    /** Called from TradeWarehouseBlock on the server. */
    public static void open(ServerPlayer player, BlockPos warehousePos) {
        PoliticsManager mgr = PoliticsManager.get(player.level());
        if (mgr == null) return;

        String mine = mgr.getPlayerCountry(player.getUUID());
        if (mine == null) {
            fail(player, Component.translatable("message.politicsmod.no_state"));
            return;
        }
        if (!mine.equals(mgr.getCountryNameAt(new ChunkPos(warehousePos)))) {
            fail(player, Component.translatable("message.politicsmod.market.not_own_territory"));
            return;
        }

        SESSIONS.put(player.getUUID(), warehousePos.immutable());
        ModNetworking.toPlayer(player, MarketDataPayload.build(player, true));
    }

    public static void handleAction(ServerPlayer player, int action, long listingId, int price) {
        PoliticsManager mgr = PoliticsManager.get(player.level());
        if (mgr == null || !hasValidSession(player)) return;

        String mine = mgr.getPlayerCountry(player.getUUID());
        if (mine == null) {
            fail(player, Component.translatable("message.politicsmod.no_state"));
            return;
        }

        switch (action) {
            case ACTION_LIST -> list(player, mgr, mine, price);
            case ACTION_BUY -> buy(player, mgr, mine, listingId);
            case ACTION_CANCEL -> cancel(player, mgr, mine, listingId);
            default -> {
                return;
            }
        }
        ModNetworking.toPlayer(player, MarketDataPayload.build(player, false));
    }

    public static void forget(UUID player) {
        SESSIONS.remove(player);
    }

    private static boolean hasValidSession(ServerPlayer player) {
        BlockPos pos = SESSIONS.get(player.getUUID());
        if (pos == null) return false;
        boolean valid = player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= MAX_DISTANCE_SQR
                && player.level().getBlockState(pos).getBlock() instanceof TradeWarehouseBlock;
        if (!valid) SESSIONS.remove(player.getUUID());
        return valid;
    }

    // ── Actions ──────────────────────────────────────────────────────────

    private static void list(ServerPlayer player, PoliticsManager mgr, String mine, int price) {
        PoliticsConfig cfg = PoliticsConfig.get();
        if (price < 1 || price > cfg.maxListingPrice) {
            fail(player, Component.translatable("message.politicsmod.market.invalid_price", cfg.maxListingPrice));
            return;
        }
        if (mgr.countListings(mine) >= cfg.maxListingsPerCountry) {
            fail(player, Component.translatable("message.politicsmod.market.listing_limit", cfg.maxListingsPerCountry));
            return;
        }
        ItemStack held = player.getMainHandItem();
        if (held.isEmpty()) {
            fail(player, Component.translatable("message.politicsmod.market.empty_hand"));
            return;
        }

        // Take the item first, then create the listing, so it can't be duplicated
        ItemStack stack = held.copy();
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        mgr.addListing(mine, player.getUUID(), stack, price);

        player.sendSystemMessage(Component.translatable("message.politicsmod.market.listed",
                stack.getHoverName(), stack.getCount(), price).withStyle(ChatFormatting.GREEN));
    }

    private static void buy(ServerPlayer player, PoliticsManager mgr, String mine, long listingId) {
        Country buyer = mgr.getCountry(mine);
        CountryRole role = buyer.getRole(player.getUUID());
        if (role != CountryRole.LEADER && role != CountryRole.MAYOR) {
            fail(player, Component.translatable("message.politicsmod.market.buy_role"));
            return;
        }

        MarketListing listing = mgr.findListing(listingId);
        if (listing == null) {
            fail(player, Component.translatable("message.politicsmod.market.not_found"));
            return;
        }
        if (listing.seller.equals(mine)) {
            fail(player, Component.translatable("message.politicsmod.market.own_listing"));
            return;
        }
        Country seller = mgr.getCountry(listing.seller);
        if (seller == null) {
            mgr.removeListing(listing.id);
            fail(player, Component.translatable("message.politicsmod.market.not_found"));
            return;
        }
        if (mgr.isAtWar(mine, listing.seller)) {
            fail(player, Component.translatable("message.politicsmod.market.embargo", listing.seller));
            return;
        }
        if (buyer.balance < listing.price) {
            fail(player, Component.translatable("message.politicsmod.market.no_funds", listing.price));
            return;
        }

        int fee = feeFor(mgr, mine, listing.seller, listing.price);

        // Order matters: remove listing -> money -> item, all within one server tick
        mgr.removeListing(listing.id);
        buyer.balance -= listing.price;
        seller.balance += listing.price - fee;
        mgr.setDirty();
        give(player, listing.stack.copy());

        player.sendSystemMessage(Component.translatable("message.politicsmod.market.bought",
                listing.stack.getHoverName(), listing.stack.getCount(), listing.seller, listing.price)
                .withStyle(ChatFormatting.GREEN));
        notifyCountry(player.getServer(), mgr, listing.seller,
                Component.translatable("message.politicsmod.market.sold_notify",
                        mine, listing.stack.getHoverName(), listing.stack.getCount(), listing.price - fee)
                        .withStyle(ChatFormatting.GOLD));
    }

    private static void cancel(ServerPlayer player, PoliticsManager mgr, String mine, long listingId) {
        MarketListing listing = mgr.findListing(listingId);
        if (listing == null || !listing.seller.equals(mine)) {
            fail(player, Component.translatable("message.politicsmod.market.not_found"));
            return;
        }
        CountryRole role = mgr.getCountry(mine).getRole(player.getUUID());
        boolean allowed = listing.listedBy.equals(player.getUUID())
                || role == CountryRole.LEADER || role == CountryRole.MAYOR;
        if (!allowed) {
            fail(player, Component.translatable("message.politicsmod.market.cancel_role"));
            return;
        }

        mgr.removeListing(listing.id);
        give(player, listing.stack.copy());
        player.sendSystemMessage(Component.translatable("message.politicsmod.market.cancelled").withStyle(ChatFormatting.YELLOW));
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    /** Fee taken from the seller; allies trade without it. */
    public static int feeFor(PoliticsManager mgr, String buyer, String seller, int price) {
        if (mgr.isAllied(buyer, seller)) return 0;
        return price * PoliticsConfig.get().marketFeePercent / 100;
    }

    /** Puts the item into the inventory and drops whatever doesn't fit, so nothing is lost. */
    private static void give(ServerPlayer player, ItemStack stack) {
        player.getInventory().add(stack); // shrinks the stack by the amount that fit
        if (!stack.isEmpty()) {
            player.drop(stack, false);
        }
    }

    private static void notifyCountry(MinecraftServer server, PoliticsManager mgr, String country, Component message) {
        if (server == null) return;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (country.equals(mgr.getPlayerCountry(p.getUUID()))) p.sendSystemMessage(message);
        }
    }

    private static void fail(ServerPlayer player, Component message) {
        player.sendSystemMessage(message.copy().withStyle(ChatFormatting.RED));
    }
}
