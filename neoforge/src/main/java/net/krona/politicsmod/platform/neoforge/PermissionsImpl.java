package net.krona.politicsmod.platform.neoforge;

import net.krona.politicsmod.Politicsmod;
import net.krona.politicsmod.platform.Permissions;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.server.permission.PermissionAPI;
import net.neoforged.neoforge.server.permission.events.PermissionGatherEvent;
import net.neoforged.neoforge.server.permission.nodes.PermissionNode;
import net.neoforged.neoforge.server.permission.nodes.PermissionTypes;

import java.util.HashMap;
import java.util.Map;

/**
 * NeoForge implementation of Permissions: nodes are registered with PermissionAPI so that
 * LuckPerms (or any other handler) can override them. The default resolver uses the operator level.
 */
public final class PermissionsImpl {

    private static final Map<String, PermissionNode<Boolean>> NODES = new HashMap<>();

    private PermissionsImpl() {
    }

    /** Registered on NeoForge.EVENT_BUS from PoliticsmodNeoForge. */
    public static void onGatherNodes(PermissionGatherEvent.Nodes event) {
        for (Map.Entry<String, Integer> e : Permissions.NODES.entrySet()) {
            int level = e.getValue();
            PermissionNode<Boolean> node = new PermissionNode<>(Politicsmod.MODID, "command." + e.getKey(), PermissionTypes.BOOLEAN,
                    (player, uuid, context) -> level == 0 || (player != null && player.hasPermissions(level)));
            NODES.put(Permissions.node(e.getKey()), node);
            event.addNodes(node);
        }
    }

    public static boolean check(ServerPlayer player, String node, int defaultLevel) {
        PermissionNode<Boolean> registered = NODES.get(node);
        if (registered == null) return player.hasPermissions(defaultLevel);
        return PermissionAPI.getPermission(player, registered);
    }
}
