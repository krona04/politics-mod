package net.krona.politicsmod.network;

import net.krona.politicsmod.Politicsmod;
import net.krona.politicsmod.PoliticsManager;
// Убедись, что путь к классам Country и CountryRole правильный для твоего мода!
import net.krona.politicsmod.politics.Country;
import net.krona.politicsmod.politics.CountryRole;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
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
            Player player = context.player();
            PoliticsManager manager = PoliticsManager.get(player.level());

            if (manager != null) {
                // 1. Узнаем, в какой стране состоит игрок
                String playerCountry = manager.getPlayerCountry(player.getUUID());

                // 2. Проверяем, совпадает ли его страна с той, которую он пытается изменить
                if (playerCountry != null && playerCountry.equals(payload.countryName())) {

                    Country country = manager.getCountry(playerCountry);

                    // 3. Проверяем права (Только Лидер может ставить флаг)
                    if (country != null && country.getRole(player.getUUID()) == CountryRole.LEADER) {

                        // 4. Если всё отлично — меняем флаг
                        manager.setFlagUrl(payload.countryName(), payload.flagUrl());
                        player.sendSystemMessage(Component.translatable("message.politicsmod.flag.updated").withStyle(ChatFormatting.GREEN));

                    } else {
                        // Если это Мэр или Гражданин
                        player.sendSystemMessage(Component.translatable("message.politicsmod.flag.leader_only").withStyle(ChatFormatting.RED));
                    }
                } else {
                    // Если хакер пытается изменить чужой флаг
                    player.sendSystemMessage(Component.translatable("message.politicsmod.flag.not_your_country").withStyle(ChatFormatting.RED));
                }
            }
        });
    }
}