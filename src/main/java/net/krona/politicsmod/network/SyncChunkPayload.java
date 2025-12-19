package net.krona.politicsmod.network;

import net.krona.politicsmod.Politicsmod;
import net.krona.politicsmod.client.ClientPoliticsData;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SyncChunkPayload(ChunkPos pos, int color) implements CustomPacketPayload {

    public static final Type<SyncChunkPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Politicsmod.MODID, "sync_chunk"));

    private static final StreamCodec<ByteBuf, ChunkPos> CHUNK_POS_CODEC = ByteBufCodecs.VAR_LONG.map(ChunkPos::new, ChunkPos::toLong);

    public static final StreamCodec<ByteBuf, SyncChunkPayload> CODEC = StreamCodec.composite(
            CHUNK_POS_CODEC, SyncChunkPayload::pos,
            ByteBufCodecs.INT, SyncChunkPayload::color,
            SyncChunkPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(final SyncChunkPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            net.krona.politicsmod.client.ClientPoliticsData.handleChunkSync(payload.pos(), payload.color());
        });
    }
}