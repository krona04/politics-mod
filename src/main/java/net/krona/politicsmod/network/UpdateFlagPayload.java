package net.krona.politicsmod.network;

import net.krona.politicsmod.Politicsmod;
import net.krona.politicsmod.PoliticsManager;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record UpdateFlagPayload(String countryName, String flagUrl) implements CustomPacketPayload {

    public static final Type<UpdateFlagPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Politicsmod.MODID, "update_flag"));

    public static final StreamCodec<ByteBuf, UpdateFlagPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, UpdateFlagPayload::countryName,
            ByteBufCodecs.STRING_UTF8, UpdateFlagPayload::flagUrl,
            UpdateFlagPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(final UpdateFlagPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            PoliticsManager manager = PoliticsManager.get(context.player().level());
            if (manager != null) {
                manager.setFlagUrl(payload.countryName, payload.flagUrl);
            }
        });
    }
}
