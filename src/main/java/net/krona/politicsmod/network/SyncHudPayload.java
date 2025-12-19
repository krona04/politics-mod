package net.krona.politicsmod.network;

import net.krona.politicsmod.Politicsmod;
import net.krona.politicsmod.client.ClientPoliticsData;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SyncHudPayload(String country, String city, int balance) implements CustomPacketPayload {

    public static final Type<SyncHudPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Politicsmod.MODID, "sync_hud"));

    public static final StreamCodec<ByteBuf, SyncHudPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, SyncHudPayload::country,
            ByteBufCodecs.STRING_UTF8, SyncHudPayload::city,
            ByteBufCodecs.INT, SyncHudPayload::balance,
            SyncHudPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(final SyncHudPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            ClientPoliticsData.updateHud(payload.country, payload.city, payload.balance);
        });
    }
}
