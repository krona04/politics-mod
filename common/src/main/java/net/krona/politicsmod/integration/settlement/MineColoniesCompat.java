package net.krona.politicsmod.integration.settlement;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.permissions.IPermissions;
import com.minecolonies.api.colony.permissions.Rank;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/** MineColonies API calls. Loaded only when MineColonies is installed (see Settlements). */
final class MineColoniesCompat {

    static final String FRIEND = "friend", HOSTILE = "hostile";

    private MineColoniesCompat() {
    }

    static List<Settlements.ColonyInfo> colonies(ServerLevel level) {
        List<Settlements.ColonyInfo> list = new ArrayList<>();
        for (IColony colony : IColonyManager.getInstance().getColonies(level)) {
            Settlements.ColonyInfo info = info(colony);
            if (info != null) list.add(info);
        }
        return list;
    }

    /** The colony that owns this chunk, or null. */
    static Settlements.ColonyInfo colonyAt(ServerLevel level, ChunkPos pos) {
        return info(IColonyManager.getInstance().getColonyByPosFromWorld(level, pos.getMiddleBlockPosition(level.getSeaLevel())));
    }

    private static Settlements.ColonyInfo info(IColony colony) {
        if (colony == null || colony.getCenter() == null) return null;
        IPermissions perms = colony.getPermissions();
        return new Settlements.ColonyInfo("colony:" + colony.getID(), colony.getID(), colony.getName(), new ChunkPos(colony.getCenter()),
                colony.getCitizenManager().getCurrentCitizenCount(), colony.getOverallHappiness(), perms.getOwner(), perms.getOwnerName());
    }

    /**
     * Gives players the colony ranks their country's diplomacy asks for: {@code desired} maps a player to
     * "friend" or "hostile". Only players with the neutral rank, or a rank PoliticsMod gave them before, are
     * touched; ranks the owner set by hand stay. {@code managed} remembers what PoliticsMod set and is updated.
     * Returns true when something changed.
     */
    static boolean syncRanks(ServerLevel level, int colonyId, Map<UUID, String> desired, Map<UUID, String> managed,
                             Function<UUID, String> names) {
        IColony colony = IColonyManager.getInstance().getColonyByWorld(colonyId, level);
        if (colony == null) return false;
        IPermissions perms = colony.getPermissions();
        UUID owner = perms.getOwner();
        boolean changed = false;

        for (Map.Entry<UUID, String> e : desired.entrySet()) {
            UUID player = e.getKey();
            if (player.equals(owner)) continue;
            Rank target = HOSTILE.equals(e.getValue()) ? perms.getRankHostile() : perms.getRankFriend();
            Rank current = perms.getRank(player);
            String ours = managed.get(player);
            boolean free = current == null || current.equals(perms.getRankNeutral()) || (ours != null && current.equals(rankOf(perms, ours)));
            if (!free) continue;
            if (!target.equals(current)) {
                String name = names.apply(player);
                boolean done = perms.getPlayers().containsKey(player) ? perms.setPlayerRank(player, target, level)
                        : name != null && perms.addPlayer(player, name, target);
                if (!done) continue;
                changed = true;
            }
            managed.put(player, e.getValue());
        }

        // Take back what diplomacy no longer asks for: peace made, player left the country
        for (UUID player : new HashSet<>(managed.keySet())) {
            if (desired.containsKey(player)) continue;
            Rank current = perms.getRank(player);
            if (current != null && current.equals(rankOf(perms, managed.get(player)))) {
                perms.removePlayer(player);
                changed = true;
            }
            managed.remove(player);
        }
        if (changed) colony.markDirty();
        return changed;
    }

    /** Ids of all colonies in this world, to drop remembered ranks of colonies that are gone. */
    static Set<String> keys(ServerLevel level) {
        Set<String> keys = new HashSet<>();
        for (IColony colony : IColonyManager.getInstance().getColonies(level)) keys.add("colony:" + colony.getID());
        return keys;
    }

    private static Rank rankOf(IPermissions perms, String name) {
        return HOSTILE.equals(name) ? perms.getRankHostile() : perms.getRankFriend();
    }
}
