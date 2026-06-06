package net.krona.politicsmod.network;

import net.krona.politicsmod.Politicsmod;
import net.krona.politicsmod.PoliticsManager;
// Импортируем класс Страны
import net.krona.politicsmod.politics.Country;

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

                    // 1. ИСПОЛЬЗУЕМ НОВЫЙ МЕТОД (Ищет страну для любого жителя)
                    String countryName = manager.getPlayerCountry(player.getUUID());

                    if (countryName != null) {
                        Country country = manager.getCountry(countryName);

                        int balance = country.balance;
                        var cities = manager.getCities(countryName);
                        String flagUrl = manager.getFlagUrl(countryName);

                        // 2. Узнаем роль игрока и превращаем её в строку ("LEADER", "MAYOR", "CITIZEN")
                        String roleName = country.getRole(player.getUUID()).name();

                        // 3. Отправляем всё это в клиентское меню (добавили roleName в конце)
                        PacketDistributor.sendToPlayer(player, new OpenCountryMenuPayload(countryName, balance, cities, flagUrl, roleName));
                    } else {
                        player.sendSystemMessage(Component.translatable("message.politicsmod.no_state").withStyle(ChatFormatting.RED));
                    }
                }
            }
        });
    }
}