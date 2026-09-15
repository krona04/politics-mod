package net.krona.politicsmod.network;

import dev.architectury.networking.NetworkManager;
import io.netty.buffer.ByteBuf;
import net.krona.politicsmod.Politicsmod;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/** Client asks for fresh dashboard tab data (after a GUI action). */
public record RequestCountryDetailsPayload() implements CustomPacketPayload {

    public static final Type<RequestCountryDetailsPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Politicsmod.MODID, "request_country_details"));

    public static final StreamCodec<ByteBuf, RequestCountryDetailsPayload> CODEC =
            StreamCodec.unit(new RequestCountryDetailsPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(final RequestCountryDetailsPayload payload, final NetworkManager.PacketContext context) {
        context.queue(() -> {
            if (context.getPlayer() instanceof ServerPlayer player) {
                CountryDetailsPayload details = CountryDetailsPayload.build(player);
                if (details != null) ModNetworking.toPlayer(player, details);
            }
        });
    }
}
