package net.krona.politicsmod.network;

import net.krona.politicsmod.Politicsmod;
import net.krona.politicsmod.client.ClientPoliticsData;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SyncRadarPayload(String detected) implements CustomPacketPayload {

    public static final Type<SyncRadarPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Politicsmod.MODID, "sync_radar"));

    public static final StreamCodec<ByteBuf, SyncRadarPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, SyncRadarPayload::detected,
            SyncRadarPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(final SyncRadarPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> ClientPoliticsData.setRadarAlert(payload.detected()));
    }
}
