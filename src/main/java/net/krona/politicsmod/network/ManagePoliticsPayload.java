package net.krona.politicsmod.network;

import net.krona.politicsmod.Politicsmod;
import net.krona.politicsmod.PoliticsManager;
// Импортируем наши классы ролей и стран (проверь пакет, если он отличается!)
import net.krona.politicsmod.politics.Country;
import net.krona.politicsmod.politics.CountryRole;

import io.netty.buffer.ByteBuf;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record ManagePoliticsPayload(int action, String arg1, String arg2) implements CustomPacketPayload {

    public static final Type<ManagePoliticsPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Politicsmod.MODID, "manage_politics"));

    public static final StreamCodec<ByteBuf, ManagePoliticsPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.INT, ManagePoliticsPayload::action,
            ByteBufCodecs.STRING_UTF8, ManagePoliticsPayload::arg1,
            ByteBufCodecs.STRING_UTF8, ManagePoliticsPayload::arg2,
            ManagePoliticsPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(final ManagePoliticsPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            var player = context.player();
            PoliticsManager manager = PoliticsManager.get(player.level());
            if (manager == null) return;

            // 1. Узнаем страну игрока
            String countryName = manager.getPlayerCountry(player.getUUID());
            if (countryName == null) {
                player.sendSystemMessage(Component.translatable("message.politicsmod.manage.no_state").withStyle(ChatFormatting.RED));
                return;
            }

            Country country = manager.getCountry(countryName);
            CountryRole role = country.getRole(player.getUUID());

            // 2. Проверяем права! Все эти действия может делать ТОЛЬКО Лидер.
            if (role != CountryRole.LEADER) {
                player.sendSystemMessage(Component.translatable("message.politicsmod.manage.no_permission").withStyle(ChatFormatting.RED));
                return; // Блокируем выполнение
            }

            // 3. Выполняем действия
            switch (payload.action) {
                case 0:
                    manager.setCapital(countryName, payload.arg1);
                    player.sendSystemMessage(Component.translatable("message.politicsmod.capital_set", payload.arg1));
                    manager.syncToPlayer((net.minecraft.server.level.ServerPlayer) player);
                    break;

                case 1:
                    if (manager.renameCountry(countryName, payload.arg1)) {
                        player.sendSystemMessage(Component.translatable("message.politicsmod.country_renamed", payload.arg1));
                    } else {
                        player.sendSystemMessage(Component.translatable("message.politicsmod.error.name_taken").withStyle(ChatFormatting.RED));
                    }
                    break;

                case 2:
                    if (manager.renameCity(countryName, payload.arg1, payload.arg2)) {
                        player.sendSystemMessage(Component.translatable("message.politicsmod.city_renamed"));
                    }
                    break;
            }
        });
    }
}