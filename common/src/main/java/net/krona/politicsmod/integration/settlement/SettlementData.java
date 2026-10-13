package net.krona.politicsmod.integration.settlement;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * What PoliticsMod remembers about settlements, saved with the political data:
 * which villages each country annexed, which settlements were already announced as part of a country,
 * and which colony ranks PoliticsMod set itself (so it only ever takes back its own changes).
 */
public final class SettlementData {
    /** Village key → country that annexed it. */
    public final Map<String, String> annexed = new HashMap<>();
    /** Settlement key → country it was last announced in, for the news feed. */
    public final Map<String, String> known = new HashMap<>();
    /** Colony key → player → rank PoliticsMod gave them ("friend" or "hostile"). */
    public final Map<String, Map<UUID, String>> managedRanks = new HashMap<>();

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.put("annexed", saveMap(annexed));
        tag.put("known", saveMap(known));
        ListTag ranks = new ListTag();
        managedRanks.forEach((colony, players) -> players.forEach((player, rank) -> {
            CompoundTag r = new CompoundTag();
            r.putString("colony", colony);
            r.putUUID("player", player);
            r.putString("rank", rank);
            ranks.add(r);
        }));
        tag.put("ranks", ranks);
        return tag;
    }

    public static SettlementData load(CompoundTag tag) {
        SettlementData d = new SettlementData();
        loadMap(tag.getList("annexed", Tag.TAG_COMPOUND), d.annexed);
        loadMap(tag.getList("known", Tag.TAG_COMPOUND), d.known);
        for (Tag t : tag.getList("ranks", Tag.TAG_COMPOUND)) {
            CompoundTag r = (CompoundTag) t;
            if (!r.hasUUID("player")) continue;
            d.managedRanks.computeIfAbsent(r.getString("colony"), k -> new HashMap<>()).put(r.getUUID("player"), r.getString("rank"));
        }
        return d;
    }

    /** A country is gone: forget its annexations; its colonies' ranks are restored by the next sync. */
    public void forgetCountry(String country) {
        annexed.values().removeIf(country::equals);
        known.values().removeIf(country::equals);
    }

    private static ListTag saveMap(Map<String, String> map) {
        ListTag list = new ListTag();
        map.forEach((k, v) -> {
            CompoundTag e = new CompoundTag();
            e.putString("key", k);
            e.putString("value", v);
            list.add(e);
        });
        return list;
    }

    private static void loadMap(ListTag list, Map<String, String> map) {
        for (Tag t : list) {
            CompoundTag e = (CompoundTag) t;
            map.put(e.getString("key"), e.getString("value"));
        }
    }
}
