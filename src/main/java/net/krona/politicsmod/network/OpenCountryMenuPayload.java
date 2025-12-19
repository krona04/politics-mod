package net.krona.politicsmod.network;

import net.krona.politicsmod.Politicsmod;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import java.util.ArrayList;
import java.util.List;

public record OpenCountryMenuPayload(String countryName, int balance, List<String> cities, String flagUrl) implements CustomPacketPayload {

    public static final Type<OpenCountryMenuPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Politicsmod.MODID, "open_menu"));

    public static final StreamCodec<ByteBuf, OpenCountryMenuPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, OpenCountryMenuPayload::countryName,
            ByteBufCodecs.INT, OpenCountryMenuPayload::balance,
            ByteBufCodecs.collection(ArrayList::new, ByteBufCodecs.STRING_UTF8), OpenCountryMenuPayload::cities,
            ByteBufCodecs.STRING_UTF8, OpenCountryMenuPayload::flagUrl,
            OpenCountryMenuPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(final OpenCountryMenuPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            Minecraft.getInstance().setScreen(new net.krona.politicsmod.client.CountryDashboardScreen(
                    payload.countryName, payload.balance, payload.cities, payload.flagUrl
            ));
        });
    }
}