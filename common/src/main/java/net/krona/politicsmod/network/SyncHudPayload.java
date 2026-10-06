package net.krona.politicsmod.network;

import dev.architectury.networking.NetworkManager;
import net.krona.politicsmod.Politicsmod;
import net.krona.politicsmod.client.ClientPoliticsData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * HUD state for one player (server -> client), sent every second.
 *
 * @param treasury  treasury of the player's own country (may be negative: debt), or NO_COUNTRY
 * @param wallet    the player's personal balance
 * @param plot      true if the player stands on a plot
 * @param plotOwner owner of that plot, "" if it is free
 * @param plotSale  sale price of a free plot (0 = not for sale)
 * @param plotRent  rent of a free plot per cycle (0 = not for rent)
 * @param myCountry the player's own country, "" if none (the world borders colour it apart from the others)
 */
public record SyncHudPayload(String country, String city, int treasury, int wallet,
                             boolean plot, String plotOwner, int plotSale, int plotRent, String myCountry) implements CustomPacketPayload {

    /** Treasury value meaning "the player has no country"; any real balance, debt included, differs from it. */
    public static final int NO_COUNTRY = Integer.MIN_VALUE;

    public static final Type<SyncHudPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Politicsmod.MODID, "sync_hud"));

    public static final StreamCodec<FriendlyByteBuf, SyncHudPayload> CODEC = StreamCodec.of(SyncHudPayload::write, SyncHudPayload::read);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(final SyncHudPayload payload, final NetworkManager.PacketContext context) {
        context.queue(() -> ClientPoliticsData.updateHud(payload));
    }

    private static void write(FriendlyByteBuf buf, SyncHudPayload p) {
        buf.writeUtf(p.country);
        buf.writeUtf(p.city);
        buf.writeInt(p.treasury);
        buf.writeInt(p.wallet);
        buf.writeBoolean(p.plot);
        buf.writeUtf(p.plotOwner);
        buf.writeVarInt(p.plotSale);
        buf.writeVarInt(p.plotRent);
        buf.writeUtf(p.myCountry);
    }

    private static SyncHudPayload read(FriendlyByteBuf buf) {
        return new SyncHudPayload(buf.readUtf(), buf.readUtf(), buf.readInt(), buf.readInt(),
                buf.readBoolean(), buf.readUtf(), buf.readVarInt(), buf.readVarInt(), buf.readUtf());
    }
}
