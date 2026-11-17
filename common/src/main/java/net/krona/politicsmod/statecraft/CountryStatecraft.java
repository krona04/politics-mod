package net.krona.politicsmod.statecraft;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What a country has been through: its rank, events waiting for an answer and answered ones, decisions taken, goals
 * started and reached, and flags that events and decisions set. Cycles are PoliticsManager economy cycles.
 */
public final class CountryStatecraft {

    /** How many answered events are remembered for the "recent" list. */
    public static final int RECENT_KEPT = 8;

    /** An event waiting for the leader; city is "" for country-wide events. */
    public record Pending(String id, String city, long firedCycle, long expiresCycle) {
    }

    /** An answered (or timed out) event. option -1 = nobody answered, the default happened. */
    public record Answered(String id, String city, int option, boolean timedOut, long cycle) {
    }

    /** 0 = principality, 1 = kingdom, 2 = empire. */
    public int rank = 0;
    public final List<Pending> pending = new ArrayList<>();
    public final List<Answered> recent = new ArrayList<>();
    /** Event id -> cycle it last fired. */
    public final Map<String, Long> lastFired = new HashMap<>();
    /** Decision id -> cycle it was last taken. */
    public final Map<String, Long> decisions = new HashMap<>();
    /** Goals started (their start conditions held once) and not yet reached, in the order they started. */
    public final Set<String> goalsActive = new LinkedHashSet<>();
    /** Goal id -> cycle it was reached. */
    public final Map<String, Long> goalsDone = new HashMap<>();
    /** Flag -> cycle it ends (0 = until cleared). */
    public final Map<String, Long> flags = new HashMap<>();
    /** Cycle of the last event that fired, to space them out. */
    public long lastEventCycle = -1_000_000L;

    public boolean hasFlag(String flag, long cycle) {
        Long end = flags.get(flag);
        return end != null && (end == 0 || end > cycle);
    }

    public void remember(Answered a) {
        recent.add(a);
        while (recent.size() > RECENT_KEPT) recent.remove(0);
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putInt("rank", rank);
        t.putLong("lastEventCycle", lastEventCycle);
        ListTag p = new ListTag();
        for (Pending e : pending) {
            CompoundTag c = new CompoundTag();
            c.putString("id", e.id());
            c.putString("city", e.city());
            c.putLong("fired", e.firedCycle());
            c.putLong("expires", e.expiresCycle());
            p.add(c);
        }
        t.put("pending", p);
        ListTag r = new ListTag();
        for (Answered a : recent) {
            CompoundTag c = new CompoundTag();
            c.putString("id", a.id());
            c.putString("city", a.city());
            c.putInt("option", a.option());
            c.putBoolean("timedOut", a.timedOut());
            c.putLong("cycle", a.cycle());
            r.add(c);
        }
        t.put("recent", r);
        t.put("lastFired", longMap(lastFired));
        t.put("decisions", longMap(decisions));
        t.put("goalsDone", longMap(goalsDone));
        t.put("flags", longMap(flags));
        ListTag active = new ListTag();
        for (String g : goalsActive) active.add(StringTag.valueOf(g));
        t.put("goalsActive", active);
        return t;
    }

    public static CountryStatecraft load(CompoundTag t) {
        CountryStatecraft s = new CountryStatecraft();
        if (t.isEmpty()) return s;
        s.rank = Math.max(0, Math.min(2, t.getInt("rank")));
        s.lastEventCycle = t.contains("lastEventCycle") ? t.getLong("lastEventCycle") : s.lastEventCycle;
        ListTag p = t.getList("pending", Tag.TAG_COMPOUND);
        for (int i = 0; i < p.size(); i++) {
            CompoundTag c = p.getCompound(i);
            s.pending.add(new Pending(c.getString("id"), c.getString("city"), c.getLong("fired"), c.getLong("expires")));
        }
        ListTag r = t.getList("recent", Tag.TAG_COMPOUND);
        for (int i = 0; i < r.size(); i++) {
            CompoundTag c = r.getCompound(i);
            s.recent.add(new Answered(c.getString("id"), c.getString("city"), c.getInt("option"), c.getBoolean("timedOut"),
                    c.getLong("cycle")));
        }
        readLongMap(t.getCompound("lastFired"), s.lastFired);
        readLongMap(t.getCompound("decisions"), s.decisions);
        readLongMap(t.getCompound("goalsDone"), s.goalsDone);
        readLongMap(t.getCompound("flags"), s.flags);
        ListTag active = t.getList("goalsActive", Tag.TAG_STRING);
        for (int i = 0; i < active.size(); i++) s.goalsActive.add(active.getString(i));
        return s;
    }

    private static CompoundTag longMap(Map<String, Long> map) {
        CompoundTag t = new CompoundTag();
        for (Map.Entry<String, Long> e : map.entrySet()) t.putLong(e.getKey(), e.getValue());
        return t;
    }

    private static void readLongMap(CompoundTag t, Map<String, Long> into) {
        for (String key : t.getAllKeys()) into.put(key, t.getLong(key));
    }
}
