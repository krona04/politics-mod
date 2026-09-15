package net.krona.politicsmod.network;

import net.krona.politicsmod.Politicsmod;
import net.krona.politicsmod.block.entity.ResidentialBuildingEntity;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import dev.architectury.networking.NetworkManager;

public record UpdateBuildingPayload(BlockPos pos, int x, int y, int z) implements CustomPacketPayload {
    public static final Type<UpdateBuildingPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Politicsmod.MODID, "update_building"));

    public static final StreamCodec<ByteBuf, UpdateBuildingPayload> CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, UpdateBuildingPayload::pos,
            ByteBufCodecs.INT, UpdateBuildingPayload::x,
            ByteBufCodecs.INT, UpdateBuildingPayload::y,
            ByteBufCodecs.INT, UpdateBuildingPayload::z,
            UpdateBuildingPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(final UpdateBuildingPayload payload, final NetworkManager.PacketContext context) {
        context.queue(() -> {
            var level = context.getPlayer().level();
            if (level.getBlockEntity(payload.pos) instanceof ResidentialBuildingEntity entity) {
                entity.setSize(payload.x, payload.y, payload.z);
                context.getPlayer().sendSystemMessage(Component.translatable("message.politicsmod.zone_updated", entity.getPop()));
            }
        });
    }
}