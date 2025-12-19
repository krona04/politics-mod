package net.krona.politicsmod.network;

import net.krona.politicsmod.Politicsmod;
import net.krona.politicsmod.PoliticsManager;
import io.netty.buffer.ByteBuf;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.PacketDistributor;

public record ClaimLandPayload(BlockPos center, String countryName, String cityName, boolean isCityClaim) implements CustomPacketPayload {

    public static final Type<ClaimLandPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Politicsmod.MODID, "claim_land"));

    public static final StreamCodec<ByteBuf, ClaimLandPayload> CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, ClaimLandPayload::center,
            ByteBufCodecs.STRING_UTF8, ClaimLandPayload::countryName,
            ByteBufCodecs.STRING_UTF8, ClaimLandPayload::cityName,
            ByteBufCodecs.BOOL, ClaimLandPayload::isCityClaim,
            ClaimLandPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(final ClaimLandPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) context.player();
            PoliticsManager manager = PoliticsManager.get(player.level());

            if (manager != null) {
                if (payload.isCityClaim) {
                    if (!manager.hasCity(payload.countryName, payload.cityName)) {
                        player.sendSystemMessage(Component.translatable("message.politicsmod.error.city_not_found", payload.cityName).withStyle(ChatFormatting.RED));
                        return;
                    }
                }

                String targetCity = payload.isCityClaim ? payload.cityName : "";

                net.minecraft.world.level.ChunkPos chunkPos = new net.minecraft.world.level.ChunkPos(payload.center);

                manager.claimChunk(chunkPos, 0, payload.countryName, targetCity);

                int newColor = manager.getColorForChunk(chunkPos);
                PacketDistributor.sendToPlayer(player, new SyncChunkPayload(chunkPos, newColor));
            }
        });
    }
}