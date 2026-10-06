package net.krona.politicsmod.network;

import dev.architectury.networking.NetworkManager;
import net.krona.politicsmod.PoliticsManager;
import net.krona.politicsmod.Politicsmod;
import net.krona.politicsmod.politics.Country;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;

/**
 * Flag image of every country (server -> client), for flagpoles and maps.
 * Sent on join and whenever a country appears, disappears or changes its flag.
 */
public record SyncFlagsPayload(Map<String, String> flags) implements CustomPacketPayload {

    public static final Type<SyncFlagsPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Politicsmod.MODID, "sync_flags"));

    public static final StreamCodec<ByteBuf, SyncFlagsPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.map(HashMap::new, ByteBufCodecs.STRING_UTF8, ByteBufCodecs.STRING_UTF8), SyncFlagsPayload::flags,
            SyncFlagsPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static SyncFlagsPayload of(PoliticsManager mgr) {
        Map<String, String> flags = new HashMap<>();
        for (Country c : mgr.getCountries().values()) flags.put(c.getName(), c.flagUrl == null ? "" : c.flagUrl);
        return new SyncFlagsPayload(flags);
    }

    public static void broadcast(PoliticsManager mgr) {
        ModNetworking.toAll(of(mgr));
    }

    public static void handle(final SyncFlagsPayload payload, final NetworkManager.PacketContext context) {
        context.queue(() -> net.krona.politicsmod.client.ClientPoliticsData.setFlags(payload.flags()));
    }
}
