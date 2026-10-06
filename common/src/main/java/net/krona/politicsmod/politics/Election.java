package net.krona.politicsmod.politics;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** A running vote for the leader of a country or the mayor of one of its cities. */
public class Election {

    public enum Type { LEADER, MAYOR }

    public final Type type;
    /** The city whose mayor is elected; empty for a leader election. */
    public final String city;
    public final long endsAt;
    /** Candidate -> name, in the order they registered. */
    public final Map<UUID, String> candidates = new LinkedHashMap<>();
    /** Voter -> candidate. */
    public final Map<UUID, UUID> votes = new HashMap<>();

    public Election(Type type, String city, long endsAt) {
        this.type = type;
        this.city = city == null ? "" : city;
        this.endsAt = endsAt;
    }

    public int votesFor(UUID candidate) {
        int n = 0;
        for (UUID v : votes.values()) if (v.equals(candidate)) n++;
        return n;
    }

    /**
     * The candidate with the most votes. A tie keeps the current holder of the office if they ran,
     * otherwise the candidate who registered first wins. Null when nobody ran.
     */
    public UUID winner(UUID incumbent) {
        UUID best = null;
        int bestVotes = -1;
        for (UUID candidate : candidates.keySet()) {
            int n = votesFor(candidate);
            if (n > bestVotes || (n == bestVotes && candidate.equals(incumbent))) {
                best = candidate;
                bestVotes = n;
            }
        }
        return best;
    }

    public long remainingMs() {
        return Math.max(0, endsAt - System.currentTimeMillis());
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("type", type.name());
        tag.putString("city", city);
        tag.putLong("endsAt", endsAt);
        ListTag list = new ListTag();
        for (Map.Entry<UUID, String> e : candidates.entrySet()) {
            CompoundTag c = new CompoundTag();
            c.putUUID("id", e.getKey());
            c.putString("name", e.getValue());
            list.add(c);
        }
        tag.put("candidates", list);
        ListTag voteList = new ListTag();
        for (Map.Entry<UUID, UUID> e : votes.entrySet()) {
            CompoundTag v = new CompoundTag();
            v.putUUID("voter", e.getKey());
            v.putUUID("for", e.getValue());
            voteList.add(v);
        }
        tag.put("votes", voteList);
        return tag;
    }

    /** Returns null for data from an unknown election type. */
    public static Election load(CompoundTag tag) {
        Type type;
        try {
            type = Type.valueOf(tag.getString("type"));
        } catch (IllegalArgumentException e) {
            return null;
        }
        Election election = new Election(type, tag.getString("city"), tag.getLong("endsAt"));
        ListTag list = tag.getList("candidates", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag c = list.getCompound(i);
            if (c.hasUUID("id")) election.candidates.put(c.getUUID("id"), c.getString("name"));
        }
        ListTag voteList = tag.getList("votes", Tag.TAG_COMPOUND);
        for (int i = 0; i < voteList.size(); i++) {
            CompoundTag v = voteList.getCompound(i);
            if (v.hasUUID("voter") && v.hasUUID("for")) election.votes.put(v.getUUID("voter"), v.getUUID("for"));
        }
        return election;
    }
}
