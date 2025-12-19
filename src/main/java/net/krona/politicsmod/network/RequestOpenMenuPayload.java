package net.krona.politicsmod.network;

import net.krona.politicsmod.Politicsmod;
import net.krona.politicsmod.PoliticsManager;
import io.netty.buffer.ByteBuf;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record RequestOpenMenuPayload() implements CustomPacketPayload {
    public static final Type<RequestOpenMenuPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Politicsmod.MODID, "request_open_menu"));

    public static final StreamCodec<ByteBuf, RequestOpenMenuPayload> CODEC = StreamCodec.unit(new RequestOpenMenuPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(final RequestOpenMenuPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                PoliticsManager manager = PoliticsManager.get(player.level());
                if (manager != null) {
                    String country = manager.getCountryByOwner(player.getUUID());
                    if (country != null) {
                        int balance = manager.getBalance(country);
                        var cities = manager.getCities(country);

                        String flagUrl = manager.getFlagUrl(country);

                        PacketDistributor.sendToPlayer(player, new OpenCountryMenuPayload(country, balance, cities, flagUrl));
                    } else {
                        player.sendSystemMessage(Component.translatable("message.politicsmod.no_state").withStyle(ChatFormatting.RED));
                    }
                }
            }
        });
    }
}