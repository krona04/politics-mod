package net.krona.politicsmod.platform;

import dev.architectury.injectables.annotations.ExpectPlatform;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Permission nodes for permission plugins such as LuckPerms: politicsmod.command.&lt;group&gt;.
 * Without a plugin every node falls back to its vanilla operator level.
 *
 * Fabric checks go through fabric-permissions-api, NeoForge checks through PermissionAPI
 * (nodes are registered there from NODES).
 */
public final class Permissions {

    /** Command group -> operator level required when no permission plugin decides. */
    public static final Map<String, Integer> NODES = new LinkedHashMap<>();

    static {
        for (String group : new String[]{"info", "members", "claim", "rename", "pay", "tax", "city", "territory",
                "embassy", "diplomacy", "wallet", "salary", "plot", "flag", "borders", "delete", "guide", "help",
                "election", "law", "loan", "siege", "news"}) {
            NODES.put(group, 0);
        }
        NODES.put("reload", 2);
        NODES.put("admin", 2);
    }

    private Permissions() {
    }

    public static String node(String group) {
        return "politicsmod.command." + group;
    }

    /** For Brigadier .requires(...): the console and command blocks use the operator level. */
    public static boolean command(CommandSourceStack source, String group) {
        int level = NODES.getOrDefault(group, 0);
        ServerPlayer player = source.getPlayer();
        if (player == null) return source.hasPermission(level);
        return check(player, node(group), level);
    }

    @ExpectPlatform
    public static boolean check(ServerPlayer player, String node, int defaultLevel) {
        throw new AssertionError("@ExpectPlatform was not replaced - check the Impl class package");
    }
}
