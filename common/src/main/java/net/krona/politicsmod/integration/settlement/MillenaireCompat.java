package net.krona.politicsmod.integration.settlement;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import org.millenaire.village.Village;
import org.millenaire.village.VillageSavedData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Millénaire API calls. Loaded only when Millénaire is installed (see Settlements). */
final class MillenaireCompat {

    /** Village land changes only when a village grows, so it is recomputed at most once a minute. */
    private static final long CHUNKS_TTL_MS = 60_000;
    private static final Map<Object, Set<ChunkPos>> CHUNKS = new HashMap<>();
    private static long chunksAt;

    private MillenaireCompat() {
    }

    /** All villages of this world; lone buildings (a single farm or tower) are not settlements. */
    static List<Settlements.VillageInfo> villages(ServerLevel level) {
        List<Settlements.VillageInfo> list = new ArrayList<>();
        VillageSavedData data = VillageSavedData.get(level);
        if (data == null || data.getVillageManager() == null) return list;
        for (Village village : data.getVillageManager().getAllVillages()) {
            if (village.isLoneBuilding() || village.getCenter() == null) continue;
            list.add(new Info(level, village));
        }
        return list;
    }

    /** The village whose land this chunk is, or null. */
    static Settlements.VillageInfo villageAt(ServerLevel level, ChunkPos pos) {
        for (Settlements.VillageInfo village : villages(level)) {
            if (village.chunks().contains(pos)) return village;
        }
        return null;
    }

    private record Info(ServerLevel level, Village village) implements Settlements.VillageInfo {
        @Override
        public String key() {
            return "village:" + village.getId().uuid();
        }

        @Override
        public String name() {
            String name = village.getVillageName();
            return name == null || name.isBlank() ? "Millénaire" : name;
        }

        @Override
        public ChunkPos center() {
            BlockPos c = village.getCenter();
            return new ChunkPos(c);
        }

        @Override
        public int villagers() {
            return village.getVillagerUuids().size();
        }

        @Override
        public UUID controller() {
            return village.isPlayerControlled() ? village.getOwnerUUID() : null;
        }

        @Override
        public String controllerName() {
            String name = village.getOwnerName();
            return name == null ? "" : name;
        }

        @Override
        public int reputation(UUID player) {
            return village.getCombinedReputation(level, player);
        }

        @Override
        public Set<ChunkPos> chunks() {
            long now = System.currentTimeMillis();
            if (now - chunksAt > CHUNKS_TTL_MS) {
                CHUNKS.clear();
                chunksAt = now;
            }
            return CHUNKS.computeIfAbsent(village.getId(), id -> {
                Set<ChunkPos> chunks = village.computeVillageChunks();
                return chunks == null ? Set.of() : Set.copyOf(chunks);
            });
        }
    }
}
