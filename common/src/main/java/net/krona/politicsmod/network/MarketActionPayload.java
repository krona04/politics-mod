package net.krona.politicsmod.network;

import dev.architectury.networking.NetworkManager;
import io.netty.buffer.ByteBuf;
import net.krona.politicsmod.Politicsmod;
import net.krona.politicsmod.market.MarketService;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * Market action (client -> server). All checks happen in MarketService.
 *
 * @param action    MarketService.ACTION_LIST / ACTION_BUY / ACTION_CANCEL
 * @param listingId listing id (buy and remove)
 * @param price     price (listing the held item)
 */
public record MarketActionPayload(int action, long listingId, int price) implements CustomPacketPayload {

    public static final Type<MarketActionPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Politicsmod.MODID, "market_action"));

    public static final StreamCodec<ByteBuf, MarketActionPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, MarketActionPayload::action,
            ByteBufCodecs.VAR_LONG, MarketActionPayload::listingId,
            ByteBufCodecs.VAR_INT, MarketActionPayload::price,
            MarketActionPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(final MarketActionPayload payload, final NetworkManager.PacketContext context) {
        context.queue(() -> {
            if (context.getPlayer() instanceof ServerPlayer player) {
                MarketService.handleAction(player, payload.action(), payload.listingId(), payload.price());
            }
        });
    }
}
