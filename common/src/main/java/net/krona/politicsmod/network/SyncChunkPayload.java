package net.krona.politicsmod.network;

import net.krona.politicsmod.Politicsmod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import dev.architectury.networking.NetworkManager;

/** Owner of one chunk (server -> client). An empty country means the chunk is wilderness again. */
public record SyncChunkPayload(ChunkPos pos, int color, String country, String city) implements CustomPacketPayload {

    public static final Type<SyncChunkPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Politicsmod.MODID, "sync_chunk"));

    private static final StreamCodec<ByteBuf, ChunkPos> CHUNK_POS_CODEC = ByteBufCodecs.VAR_LONG.map(ChunkPos::new, ChunkPos::toLong);

    public static final StreamCodec<ByteBuf, SyncChunkPayload> CODEC = StreamCodec.composite(
            CHUNK_POS_CODEC, SyncChunkPayload::pos,
            ByteBufCodecs.INT, SyncChunkPayload::color,
            ByteBufCodecs.STRING_UTF8, SyncChunkPayload::country,
            ByteBufCodecs.STRING_UTF8, SyncChunkPayload::city,
            SyncChunkPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(final SyncChunkPayload payload, final NetworkManager.PacketContext context) {
        context.queue(() -> net.krona.politicsmod.client.ClientPoliticsData.handleChunkSync(
                payload.pos(), payload.color(), payload.country(), payload.city()));
    }
}
