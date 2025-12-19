package net.krona.politicsmod.network;

import net.krona.politicsmod.Politicsmod;
import net.krona.politicsmod.PoliticsManager;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record CreateCityPayload(String countryName, String cityName) implements CustomPacketPayload {
    public static final Type<CreateCityPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Politicsmod.MODID, "create_city"));

    public static final StreamCodec<ByteBuf, CreateCityPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, CreateCityPayload::countryName,
            ByteBufCodecs.STRING_UTF8, CreateCityPayload::cityName,
            CreateCityPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(final CreateCityPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            PoliticsManager manager = PoliticsManager.get(context.player().level());
            if (manager != null) {
                manager.createCity(payload.countryName, payload.cityName);
            }
        });
    }
}
