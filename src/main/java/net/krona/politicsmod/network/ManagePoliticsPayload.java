package net.krona.politicsmod.network;

import net.krona.politicsmod.Politicsmod;
import net.krona.politicsmod.PoliticsManager;
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

            String myCountry = manager.getCountryByOwner(player.getUUID());
            if (myCountry == null) return;

            switch (payload.action) {
                case 0:
                    manager.setCapital(myCountry, payload.arg1);
                    player.sendSystemMessage(Component.translatable("message.politicsmod.capital_set", payload.arg1));
                    manager.syncToPlayer((net.minecraft.server.level.ServerPlayer) player);
                    break;

                case 1:
                    if (manager.renameCountry(myCountry, payload.arg1)) {
                        player.sendSystemMessage(Component.translatable("message.politicsmod.country_renamed", payload.arg1));
                    } else {
                        player.sendSystemMessage(Component.translatable("message.politicsmod.error.name_taken").withStyle(ChatFormatting.RED));
                    }
                    break;

                case 2:
                    if (manager.renameCity(myCountry, payload.arg1, payload.arg2)) {
                        player.sendSystemMessage(Component.translatable("message.politicsmod.city_renamed"));
                    }
                    break;
            }
        });
    }
}
