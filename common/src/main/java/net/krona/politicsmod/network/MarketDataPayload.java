package net.krona.politicsmod.network;

import dev.architectury.networking.NetworkManager;
import net.krona.politicsmod.PoliticsManager;
import net.krona.politicsmod.Politicsmod;
import net.krona.politicsmod.client.ClientPoliticsData;
import net.krona.politicsmod.config.PoliticsConfig;
import net.krona.politicsmod.market.MarketService;
import net.krona.politicsmod.politics.Country;
import net.krona.politicsmod.politics.MarketListing;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Market state for the warehouse screen (server -> client).
 * open=true opens the screen; open=false only refreshes an open screen after an action.
 */
public record MarketDataPayload(
        boolean open,
        String myCountry,
        String myRole,
        int treasury,
        int feePercent,
        int maxPrice,
        List<Entry> entries
) implements CustomPacketPayload {

    /**
     * @param relation relation of the player's country to the seller (ALLIANCE/NEUTRAL/WAR)
     * @param fee      fee deducted from the seller if the player's country buys
     */
    public record Entry(long id, String seller, ItemStack stack, int price, String relation, int fee) {
    }

    public static final Type<MarketDataPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Politicsmod.MODID, "market_data"));

    public static final StreamCodec<RegistryFriendlyByteBuf, MarketDataPayload> CODEC =
            StreamCodec.of(MarketDataPayload::write, MarketDataPayload::read);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(final MarketDataPayload payload, final NetworkManager.PacketContext context) {
        context.queue(() -> ClientPoliticsData.setMarketData(payload));
    }

    public static MarketDataPayload build(ServerPlayer player, boolean open) {
        PoliticsManager mgr = PoliticsManager.get(player.level());
        String mine = mgr != null ? mgr.getPlayerCountry(player.getUUID()) : null;
        Country country = mine != null ? mgr.getCountry(mine) : null;
        PoliticsConfig cfg = PoliticsConfig.get();

        List<Entry> entries = new ArrayList<>();
        if (country != null) {
            List<MarketListing> listings = new ArrayList<>(mgr.getListings());
            listings.sort(Comparator.comparingLong((MarketListing l) -> l.createdAt).reversed());
            for (MarketListing l : listings) {
                if (mgr.getCountry(l.seller) == null) continue; // seller country was dissolved
                entries.add(new Entry(l.id, l.seller, l.stack, l.price,
                        mgr.getDiplomacy(mine, l.seller).name(),
                        MarketService.feeFor(mgr, mine, l.seller, l.price)));
            }
        }

        return new MarketDataPayload(
                open,
                mine != null ? mine : "",
                country != null ? country.getRole(player.getUUID()).name() : "GUEST",
                country != null ? country.balance : 0,
                cfg.marketFeePercent,
                cfg.maxListingPrice,
                entries);
    }

    private static void write(RegistryFriendlyByteBuf buf, MarketDataPayload p) {
        buf.writeBoolean(p.open);
        buf.writeUtf(p.myCountry);
        buf.writeUtf(p.myRole);
        buf.writeInt(p.treasury);
        buf.writeVarInt(p.feePercent);
        buf.writeVarInt(p.maxPrice);
        buf.writeVarInt(p.entries.size());
        for (Entry e : p.entries) {
            buf.writeVarLong(e.id);
            buf.writeUtf(e.seller);
            ItemStack.STREAM_CODEC.encode(buf, e.stack);
            buf.writeVarInt(e.price);
            buf.writeUtf(e.relation);
            buf.writeVarInt(e.fee);
        }
    }

    private static MarketDataPayload read(RegistryFriendlyByteBuf buf) {
        boolean open = buf.readBoolean();
        String myCountry = buf.readUtf();
        String myRole = buf.readUtf();
        int treasury = buf.readInt();
        int feePercent = buf.readVarInt();
        int maxPrice = buf.readVarInt();
        int count = buf.readVarInt();
        List<Entry> entries = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            entries.add(new Entry(buf.readVarLong(), buf.readUtf(), ItemStack.STREAM_CODEC.decode(buf),
                    buf.readVarInt(), buf.readUtf(), buf.readVarInt()));
        }
        return new MarketDataPayload(open, myCountry, myRole, treasury, feePercent, maxPrice, entries);
    }
}
