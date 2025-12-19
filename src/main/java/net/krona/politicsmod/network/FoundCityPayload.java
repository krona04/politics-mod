package net.krona.politicsmod.network;

import net.krona.politicsmod.Politicsmod;
import net.krona.politicsmod.PoliticsManager;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record FoundCityPayload(BlockPos pos, String cityName) implements CustomPacketPayload {
    public static final Type<FoundCityPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Politicsmod.MODID, "found_city"));

    public static final StreamCodec<ByteBuf, FoundCityPayload> CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, FoundCityPayload::pos,
            ByteBufCodecs.STRING_UTF8, FoundCityPayload::cityName,
            FoundCityPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(final FoundCityPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            PoliticsManager manager = PoliticsManager.get(context.player().level());
            if (manager != null) {
                if (context.player() instanceof ServerPlayer sp) {
                    manager.foundNewCity(payload.pos, sp, payload.cityName);
                }
            }
        });
    }
}
