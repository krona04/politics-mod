package net.krona.politicsmod.politics;

import dev.architectury.utils.GameInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.UUID;

/** Chat messages to the online members of a country. */
public final class Notify {

    private Notify() {
    }

    public static void members(Country country, Component message) {
        MinecraftServer server = GameInstance.getServer();
        if (server == null || country == null) return;
        for (Map.Entry<UUID, CountryRole> e : country.getMembers().entrySet()) {
            ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
            if (p != null) p.sendSystemMessage(message);
        }
    }

    public static void leader(Country country, Component message) {
        MinecraftServer server = GameInstance.getServer();
        if (server == null || country == null || country.getLeader() == null) return;
        ServerPlayer p = server.getPlayerList().getPlayer(country.getLeader());
        if (p != null) p.sendSystemMessage(message);
    }

    public static void everyone(Component message) {
        MinecraftServer server = GameInstance.getServer();
        if (server != null) server.getPlayerList().broadcastSystemMessage(message, false);
    }

    /** Name of a player, online or from the profile cache. */
    public static String nameOf(UUID id) {
        MinecraftServer server = GameInstance.getServer();
        if (server != null) {
            ServerPlayer online = server.getPlayerList().getPlayer(id);
            if (online != null) return online.getGameProfile().getName();
            if (server.getProfileCache() != null) {
                var profile = server.getProfileCache().get(id);
                if (profile.isPresent()) return profile.get().getName();
            }
        }
        return id.toString().substring(0, 8);
    }
}
