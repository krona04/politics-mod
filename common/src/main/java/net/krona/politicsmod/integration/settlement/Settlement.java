package net.krona.politicsmod.integration.settlement;

import net.minecraft.world.level.ChunkPos;

/**
 * A colony (MineColonies) or a village (Millénaire) as one country sees it.
 *
 * @param key        stable id: {@code colony:<id>} or {@code village:<uuid>}
 * @param mood       colony: happiness 0..10; village: the best reputation any citizen has there
 * @param owner      name of the colony owner or of the citizen controlling the village, "" if none
 * @param tax        what it pays the country each cycle (0 unless it belongs to the country)
 */
public record Settlement(String key, Kind kind, String name, ChunkPos center, int population, int mood,
                         String owner, Status status, int tax) {

    public enum Kind { COLONY, VILLAGE }

    public enum Status {
        /** A colony of a citizen. */
        COLONY,
        /** A Millénaire village a citizen controls. */
        CONTROLLED,
        /** A Millénaire village the country annexed thanks to its reputation there. */
        ANNEXED,
        /** A village where a citizen is liked enough that the leader may annex it. */
        ANNEXABLE;

        public boolean belongs() {
            return this != ANNEXABLE;
        }
    }
}
