package net.krona.politicsmod.politics;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.ChunkPos;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * A plot is one city chunk that a player can buy or rent. The owner decides who else may
 * use it; other citizens (and even the leader) can't build or open containers there.
 */
public final class Plot {

    public enum Trust { ACCESS, BUILD }

    public final ChunkPos pos;
    public final String country;
    public final String city;

    public UUID owner;            // null = free plot
    public String ownerName = "";
    public int salePrice;         // > 0 = can be bought
    public int rentPrice;         // > 0 = can be rented, charged every economy cycle
    public boolean rented;        // the current owner is a tenant
    public boolean closed;        // only the owner, trusted players, the leader and the city's mayor may enter
    public final Map<UUID, Trust> trusted = new LinkedHashMap<>();
    public final Map<UUID, String> trustedNames = new LinkedHashMap<>();

    public Plot(ChunkPos pos, String country, String city) {
        this.pos = pos;
        this.country = country;
        this.city = city;
    }

    public boolean isOwned() {
        return owner != null;
    }

    public boolean canBuild(UUID player) {
        return player.equals(owner) || trusted.get(player) == Trust.BUILD;
    }

    public boolean canAccess(UUID player) {
        return player.equals(owner) || trusted.containsKey(player);
    }

    /** Frees the plot: no owner, no guests, no lock. Prices stay so it can be sold again. */
    public void release() {
        owner = null;
        ownerName = "";
        rented = false;
        closed = false;
        trusted.clear();
        trustedNames.clear();
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putLong("pos", pos.toLong());
        tag.putString("country", country);
        tag.putString("city", city);
        if (owner != null) tag.putUUID("owner", owner);
        tag.putString("ownerName", ownerName);
        tag.putInt("salePrice", salePrice);
        tag.putInt("rentPrice", rentPrice);
        tag.putBoolean("rented", rented);
        tag.putBoolean("closed", closed);
        ListTag list = new ListTag();
        for (Map.Entry<UUID, Trust> e : trusted.entrySet()) {
            CompoundTag t = new CompoundTag();
            t.putUUID("player", e.getKey());
            t.putString("name", trustedNames.getOrDefault(e.getKey(), ""));
            t.putString("level", e.getValue().name());
            list.add(t);
        }
        tag.put("trusted", list);
        return tag;
    }

    public static Plot load(CompoundTag tag) {
        Plot plot = new Plot(new ChunkPos(tag.getLong("pos")), tag.getString("country"), tag.getString("city"));
        if (tag.hasUUID("owner")) plot.owner = tag.getUUID("owner");
        plot.ownerName = tag.getString("ownerName");
        plot.salePrice = tag.getInt("salePrice");
        plot.rentPrice = tag.getInt("rentPrice");
        plot.rented = tag.getBoolean("rented");
        plot.closed = tag.getBoolean("closed");
        ListTag list = tag.getList("trusted", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag t = list.getCompound(i);
            if (!t.hasUUID("player")) continue;
            Trust level;
            try {
                level = Trust.valueOf(t.getString("level"));
            } catch (IllegalArgumentException e) {
                level = Trust.ACCESS;
            }
            plot.trusted.put(t.getUUID("player"), level);
            plot.trustedNames.put(t.getUUID("player"), t.getString("name"));
        }
        return plot;
    }
}
