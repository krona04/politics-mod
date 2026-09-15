package net.krona.politicsmod.network;

import dev.architectury.networking.NetworkManager;
import dev.architectury.platform.Platform;
import dev.architectury.utils.Env;
import dev.architectury.utils.GameInstance;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Network registration (replaces NeoForge's PayloadRegistrar).
 *
 * Mapping:
 *   registrar.playToClient(TYPE, CODEC, X::handle)
 *       -> NetworkManager.registerReceiver(NetworkManager.Side.S2C, TYPE, CODEC, X::handle)
 *   registrar.playToServer(TYPE, CODEC, X::handle)
 *       -> NetworkManager.registerReceiver(NetworkManager.Side.C2S, TYPE, CODEC, X::handle)
 *
 * handle() signature:
 *   (T payload, IPayloadContext ctx)              // NeoForge
 *   (T payload, NetworkManager.PacketContext ctx) // Architectury
 * Inside: ctx.enqueueWork(...) -> ctx.queue(...), ctx.player() -> ctx.getPlayer().
 */
public final class ModNetworking {

    private ModNetworking() {
    }

    public static void register() {
        // ---- Server -> Client ----
        s2c(SyncChunkPayload.TYPE, SyncChunkPayload.CODEC, SyncChunkPayload::handle);
        s2c(OpenCountryMenuPayload.TYPE, OpenCountryMenuPayload.CODEC, OpenCountryMenuPayload::handle);
        s2c(SyncHudPayload.TYPE, SyncHudPayload.CODEC, SyncHudPayload::handle);
        s2c(SyncRadarPayload.TYPE, SyncRadarPayload.CODEC, SyncRadarPayload::handle);
        s2c(CountryDetailsPayload.TYPE, CountryDetailsPayload.CODEC, CountryDetailsPayload::handle);
        s2c(MarketDataPayload.TYPE, MarketDataPayload.CODEC, MarketDataPayload::handle);
        s2c(BorderSettingsPayload.TYPE, BorderSettingsPayload.CODEC, BorderSettingsPayload::handle);

        // ---- Client -> Server ----
        NetworkManager.registerReceiver(NetworkManager.Side.C2S,
                CreateCountryPayload.TYPE, CreateCountryPayload.CODEC, CreateCountryPayload::handle);
        NetworkManager.registerReceiver(NetworkManager.Side.C2S,
                CreateCityPayload.TYPE, CreateCityPayload.CODEC, CreateCityPayload::handle);
        NetworkManager.registerReceiver(NetworkManager.Side.C2S,
                ClaimLandPayload.TYPE, ClaimLandPayload.CODEC, ClaimLandPayload::handle);
        NetworkManager.registerReceiver(NetworkManager.Side.C2S,
                FoundCityPayload.TYPE, FoundCityPayload.CODEC, FoundCityPayload::handle);
        NetworkManager.registerReceiver(NetworkManager.Side.C2S,
                RequestOpenMenuPayload.TYPE, RequestOpenMenuPayload.CODEC, RequestOpenMenuPayload::handle);
        NetworkManager.registerReceiver(NetworkManager.Side.C2S,
                UpdateFlagPayload.TYPE, UpdateFlagPayload.CODEC, UpdateFlagPayload::handle);
        NetworkManager.registerReceiver(NetworkManager.Side.C2S,
                ManagePoliticsPayload.TYPE, ManagePoliticsPayload.CODEC, ManagePoliticsPayload::handle);
        NetworkManager.registerReceiver(NetworkManager.Side.C2S,
                UpdateBuildingPayload.TYPE, UpdateBuildingPayload.CODEC, UpdateBuildingPayload::handle);
        NetworkManager.registerReceiver(NetworkManager.Side.C2S,
                RequestCountryDetailsPayload.TYPE, RequestCountryDetailsPayload.CODEC, RequestCountryDetailsPayload::handle);
        NetworkManager.registerReceiver(NetworkManager.Side.C2S,
                MarketActionPayload.TYPE, MarketActionPayload.CODEC, MarketActionPayload::handle);
    }

    /**
     * On the client we register the handler; on a dedicated server only the payload type,
     * because S2C handlers touch client-only classes (Minecraft, Screen).
     */
    private static <T extends CustomPacketPayload> void s2c(
            CustomPacketPayload.Type<T> type,
            StreamCodec<? super RegistryFriendlyByteBuf, T> codec,
            NetworkManager.NetworkReceiver<T> handler) {
        if (Platform.getEnvironment() == Env.CLIENT) {
            NetworkManager.registerReceiver(NetworkManager.Side.S2C, type, codec, handler);
        } else {
            NetworkManager.registerS2CPayloadType(type, codec);
        }
    }

    // ---- Send helpers ----
    // Replaces PacketDistributor.sendToPlayer(player, payload)
    public static void toPlayer(ServerPlayer player, CustomPacketPayload payload) {
        NetworkManager.sendToPlayer(player, payload);
    }

    // Replaces PacketDistributor.sendToServer(payload)
    public static void toServer(CustomPacketPayload payload) {
        NetworkManager.sendToServer(payload);
    }

    // Replaces PacketDistributor.sendToAllPlayers(payload)
    public static void toAll(CustomPacketPayload payload) {
        MinecraftServer server = GameInstance.getServer();
        if (server != null) {
            NetworkManager.sendToPlayers(server.getPlayerList().getPlayers(), payload);
        }
    }
}
