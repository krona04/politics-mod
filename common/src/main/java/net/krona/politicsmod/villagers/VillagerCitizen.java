package net.krona.politicsmod.villagers;

import net.minecraft.nbt.CompoundTag;

import java.util.UUID;

/**
 * A vanilla villager living inside a city of a country. Kept while its chunk is unloaded; dropped when its chunk is
 * loaded and the villager is no longer in any city of the country (it died or wandered off).
 */
public final class VillagerCitizen {
    public final UUID id;
    public String name;
    public String city;
    /** Profession id path ("farmer", "none"). */
    public String profession;
    /** Chunk (ChunkPos.toLong) where it was last seen. */
    public long chunk;
    /** Economy cycle it was last seen in. */
    public long seenCycle;

    public VillagerCitizen(UUID id, String name, String city, String profession, long chunk, long seenCycle) {
        this.id = id;
        this.name = name;
        this.city = city;
        this.profession = profession;
        this.chunk = chunk;
        this.seenCycle = seenCycle;
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putUUID("id", id);
        t.putString("name", name);
        t.putString("city", city);
        t.putString("profession", profession);
        t.putLong("chunk", chunk);
        t.putLong("seen", seenCycle);
        return t;
    }

    public static VillagerCitizen load(CompoundTag t) {
        if (!t.hasUUID("id")) return null;
        return new VillagerCitizen(t.getUUID("id"), t.getString("name"), t.getString("city"), t.getString("profession"),
                t.getLong("chunk"), t.getLong("seen"));
    }
}
