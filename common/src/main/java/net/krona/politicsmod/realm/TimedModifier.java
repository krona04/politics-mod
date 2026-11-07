package net.krona.politicsmod.realm;

import net.minecraft.nbt.CompoundTag;

/**
 * A modifier stored with a country for a while: after a lost war, a disaster an operator announced, a future event.
 * The name is shown as it is unless it starts with '#', then it is a translation key.
 *
 * @param expiresAt milliseconds since the epoch; 0 = until removed
 */
public record TimedModifier(String id, String name, ModifierType type, int value, long expiresAt) {

    public boolean expired(long now) {
        return expiresAt > 0 && now >= expiresAt;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("id", id);
        tag.putString("name", name);
        tag.putString("type", type.key());
        tag.putInt("value", value);
        tag.putLong("expiresAt", expiresAt);
        return tag;
    }

    /** Null when the type is unknown (a newer version's modifier). */
    public static TimedModifier load(CompoundTag tag) {
        ModifierType type = ModifierType.byKey(tag.getString("type"));
        if (type == null) return null;
        return new TimedModifier(tag.getString("id"), tag.getString("name"), type, tag.getInt("value"), tag.getLong("expiresAt"));
    }
}
