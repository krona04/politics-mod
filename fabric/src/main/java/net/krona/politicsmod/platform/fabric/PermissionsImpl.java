package net.krona.politicsmod.platform.fabric;

import net.minecraft.server.level.ServerPlayer;

/**
 * Fabric implementation of Permissions: fabric-permissions-api, which LuckPerms and other
 * permission mods implement. Without such a mod it falls back to the operator level.
 */
public final class PermissionsImpl {

    private PermissionsImpl() {
    }

    public static boolean check(ServerPlayer player, String node, int defaultLevel) {
        return me.lucko.fabric.api.permissions.v0.Permissions.check(player, node, defaultLevel);
    }
}
