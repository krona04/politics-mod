package net.krona.politicsmod.network;

import net.krona.politicsmod.Politicsmod;
import net.krona.politicsmod.PoliticsManager;
import net.krona.politicsmod.politics.Country;
import net.krona.politicsmod.politics.CountryRole;

import io.netty.buffer.ByteBuf;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
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
            if (manager == null) return;

            String playerCountryName = manager.getPlayerCountry(player.getUUID());
            if (playerCountryName == null || !playerCountryName.equals(payload.countryName())) {
                player.sendSystemMessage(Component.translatable("message.politicsmod.claim.not_your_country").withStyle(ChatFormatting.RED));
                return;
            }

            Country country = manager.getCountry(playerCountryName);
            CountryRole role = country.getRole(player.getUUID());
            ChunkPos chunkPos = new ChunkPos(payload.center());
            String currentOwner = manager.getCountryNameAt(chunkPos);

            if (payload.isCityClaim()) {
                if (role != CountryRole.LEADER && role != CountryRole.MAYOR) {
                    player.sendSystemMessage(Component.translatable("message.politicsmod.claim.leader_mayor_only").withStyle(ChatFormatting.RED));
                    return;
                }
                if (!manager.hasCity(playerCountryName, payload.cityName())) {
                    player.sendSystemMessage(Component.translatable("message.politicsmod.error.city_not_found", payload.cityName()).withStyle(ChatFormatting.RED));
                    return;
                }
                if (currentOwner == null || !currentOwner.equals(playerCountryName)) {
                    player.sendSystemMessage(Component.translatable("message.politicsmod.claim.city_on_claimed_only").withStyle(ChatFormatting.RED));
                    return;
                }
                if (manager.getCityAt(chunkPos) != null) {
                    player.sendSystemMessage(Component.translatable("message.politicsmod.claim.chunk_other_city").withStyle(ChatFormatting.RED));
                    return;
                }

                int CITY_CLAIM_PRICE = 100;
                if (country.balance < CITY_CLAIM_PRICE) {
                    player.sendSystemMessage(Component.translatable("message.politicsmod.claim.no_funds_city", CITY_CLAIM_PRICE).withStyle(ChatFormatting.RED));
                    return;
                }

                country.balance -= CITY_CLAIM_PRICE;
                manager.claimChunk(chunkPos, 0, playerCountryName, payload.cityName());
                player.sendSystemMessage(Component.translatable("message.politicsmod.claim.city_expanded", CITY_CLAIM_PRICE).withStyle(ChatFormatting.GREEN));

            } else {
                if (role != CountryRole.LEADER) {
                    player.sendSystemMessage(Component.translatable("message.politicsmod.claim.leader_only").withStyle(ChatFormatting.RED));
                    return;
                }
                if (currentOwner != null) {
                    player.sendSystemMessage(Component.translatable("message.politicsmod.claim.chunk_taken").withStyle(ChatFormatting.RED));
                    return;
                }

                int CLAIM_PRICE = 500;
                if (country.balance < CLAIM_PRICE) {
                    player.sendSystemMessage(Component.translatable("message.politicsmod.claim.no_funds_country", CLAIM_PRICE).withStyle(ChatFormatting.RED));
                    return;
                }

                country.balance -= CLAIM_PRICE;
                manager.claimChunk(chunkPos, 0, playerCountryName, null);
                player.sendSystemMessage(Component.translatable("message.politicsmod.claim.country_expanded", CLAIM_PRICE).withStyle(ChatFormatting.GREEN));
            }

            int newColor = manager.getColorForChunk(chunkPos);
            PacketDistributor.sendToPlayer(player, new SyncChunkPayload(chunkPos, newColor));
        });
    }
}
