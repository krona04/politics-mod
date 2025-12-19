package net.krona.politicsmod.network;

import net.krona.politicsmod.Politicsmod;
import net.krona.politicsmod.PoliticsManager;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record CreateCountryPayload(BlockPos pos, String countryName) implements CustomPacketPayload {

    public static final Type<CreateCountryPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Politicsmod.MODID, "create_country"));

    public static final StreamCodec<ByteBuf, CreateCountryPayload> CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, CreateCountryPayload::pos,
            ByteBufCodecs.STRING_UTF8, CreateCountryPayload::countryName,
            CreateCountryPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(final CreateCountryPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            // Вызываем метод создания ТОЛЬКО страны
            PoliticsManager.createCountry(
                    context.player().level(),
                    payload.pos,
                    context.player(),
                    payload.countryName
            );
        });
    }
}
