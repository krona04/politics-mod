package net.krona.politicsmod.network;

import dev.architectury.networking.NetworkManager;
import net.krona.politicsmod.Politicsmod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server -> client (0.5.1): an event of the player's country waits for an answer. The client shows a toast with a
 * bell, so nobody misses it in a busy chat. Sent when the event fires and when a member joins while one waits.
 *
 * @param title   translation key (or plain text) of the event title
 * @param country the country, for %1$s
 * @param city    the event's city, "" for country-wide events
 * @param count   events waiting in total
 */
public record EventNoticePayload(String title, String country, String city, int count) implements CustomPacketPayload {

    public static final Type<EventNoticePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Politicsmod.MODID, "event_notice"));

    public static final StreamCodec<FriendlyByteBuf, EventNoticePayload> CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeUtf(p.title);
                buf.writeUtf(p.country);
                buf.writeUtf(p.city);
                buf.writeVarInt(p.count);
            },
            buf -> new EventNoticePayload(buf.readUtf(), buf.readUtf(), buf.readUtf(), buf.readVarInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(final EventNoticePayload payload, final NetworkManager.PacketContext context) {
        context.queue(() -> net.krona.politicsmod.client.ClientEventNotice.show(payload));
    }
}
