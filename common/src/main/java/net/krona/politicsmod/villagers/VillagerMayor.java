package net.krona.politicsmod.villagers;

import net.minecraft.nbt.CompoundTag;

import java.util.UUID;

/** A villager elected mayor of a city, with the program it ran on. */
public record VillagerMayor(UUID id, String name, MayorProgram program, long sinceCycle) {

    public CompoundTag save(String city) {
        CompoundTag t = new CompoundTag();
        t.putString("city", city);
        t.putUUID("id", id);
        t.putString("name", name);
        t.putString("program", program.key());
        t.putLong("since", sinceCycle);
        return t;
    }

    /** Null for broken data or an unknown program. */
    public static VillagerMayor load(CompoundTag t) {
        MayorProgram program = MayorProgram.byKey(t.getString("program"));
        if (!t.hasUUID("id") || program == null) return null;
        return new VillagerMayor(t.getUUID("id"), t.getString("name"), program, t.getLong("since"));
    }
}
