package net.krona.politicsmod.network;

import net.krona.politicsmod.Politicsmod;
import net.krona.politicsmod.PoliticsManager;
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
import dev.architectury.networking.NetworkManager;

public record UpdateFlagPayload(String countryName, String flagUrl) implements CustomPacketPayload {

    public static final Type<UpdateFlagPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Politicsmod.MODID, "update_flag"));

    public static final StreamCodec<ByteBuf, UpdateFlagPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, UpdateFlagPayload::countryName,
            ByteBufCodecs.STRING_UTF8, UpdateFlagPayload::flagUrl,
            UpdateFlagPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(final UpdateFlagPayload payload, final NetworkManager.PacketContext context) {
        context.queue(() -> {
            Player player = context.getPlayer();
            PoliticsManager manager = PoliticsManager.get(player.level());

            if (manager != null) {
                String playerCountry = manager.getPlayerCountry(player.getUUID());

                // A player may only change their own country's flag
                if (playerCountry != null && playerCountry.equals(payload.countryName())) {

                    Country country = manager.getCountry(playerCountry);

                    // Only the leader can set the flag
                    if (country != null && country.getRole(player.getUUID()) == CountryRole.LEADER) {

                        manager.setFlagUrl(payload.countryName(), payload.flagUrl());
                        player.sendSystemMessage(Component.translatable("message.politicsmod.flag.updated").withStyle(ChatFormatting.GREEN));

                    } else {
                        player.sendSystemMessage(Component.translatable("message.politicsmod.flag.leader_only").withStyle(ChatFormatting.RED));
                    }
                } else {
                    player.sendSystemMessage(Component.translatable("message.politicsmod.flag.not_your_country").withStyle(ChatFormatting.RED));
                }
            }
        });
    }
}