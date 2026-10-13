package net.krona.politicsmod.integration.settlement;

import com.mojang.authlib.GameProfile;
import dev.architectury.platform.Platform;
import net.krona.politicsmod.PoliticsManager;
import net.krona.politicsmod.Politicsmod;
import net.krona.politicsmod.config.PoliticsConfig;
import net.krona.politicsmod.politics.Country;
import net.krona.politicsmod.politics.CountryRole;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Settlements of other mods inside countries: MineColonies colonies and Millénaire villages (NeoForge only).
 *
 * - A colony of a citizen and a village a citizen controls belong to the country. They pay tax each cycle
 *   (a colony more when its colonists are happy), their land is free for the country to claim and closed to others.
 * - A village where a citizen is a friend of the village can be annexed by the leader. It pays tribute and its land
 *   becomes the country's to claim; if the country's reputation there falls, the village breaks away.
 * - Colony ranks follow diplomacy: citizens and allies are friends of the colony, enemies at war are hostile,
 *   so the colony's guards fight them.
 *
 * Both mods are optional: their classes are touched only after Platform.isModLoaded says the mod is there.
 * If a newer version of a mod changes its API, that integration switches itself off instead of crashing.
 */
public final class Settlements {

    /** A colony as read from MineColonies. */
    public record ColonyInfo(String key, int id, String name, ChunkPos center, int population, double happiness,
                             UUID owner, String ownerName) {
    }

    /** A village as read from Millénaire; valid for the duration of one call. */
    public interface VillageInfo {
        String key();

        String name();

        ChunkPos center();

        int villagers();

        /** The player who controls the village in Millénaire, or null. */
        UUID controller();

        String controllerName();

        int reputation(UUID player);

        Set<ChunkPos> chunks();
    }

    /** Tax one settlement pays per cycle; the center decides whether it goes to a city or the nation. */
    public record Income(String name, ChunkPos center, int amount) {
    }

    /** Outcome of annexing or releasing a village. */
    public record Result(boolean ok, Component message) {
    }

    /** Millénaire's "public enemy" level: a village boycotts such a player and won't stay in their country. */
    private static final int PUBLIC_ENEMY = -1024;

    private static boolean colonyBroken, villageBroken;

    private Settlements() {
    }

    public static boolean minecolonies() {
        return !colonyBroken && PoliticsConfig.get().minecoloniesEnabled && Platform.isModLoaded("minecolonies");
    }

    public static boolean millenaire() {
        return !villageBroken && PoliticsConfig.get().millenaireEnabled && Platform.isModLoaded("millenaire");
    }

    public static boolean enabled() {
        return minecolonies() || millenaire();
    }

    /** Logged once at server start, so owners can see the integrations are on. */
    public static void announce() {
        if (minecolonies()) Politicsmod.LOGGER.info("MineColonies found: colonies of citizens belong to their country");
        if (millenaire()) Politicsmod.LOGGER.info("Millénaire found: villages can join countries");
    }

    // ── What a country has ───────────────────────────────────────────────

    /** The country's colonies and villages, and the villages it may annex. */
    public static List<Settlement> of(ServerLevel level, PoliticsManager mgr, String country) {
        if (level == null || !enabled()) return Collections.emptyList();
        Set<UUID> citizens = citizens(mgr, country);
        SettlementData data = mgr.settlementData();
        PoliticsConfig cfg = PoliticsConfig.get();
        List<Settlement> out = new ArrayList<>();

        for (ColonyInfo c : colonies(level)) {
            if (!citizens.contains(c.owner())) continue;
            out.add(new Settlement(c.key(), Settlement.Kind.COLONY, c.name(), c.center(), c.population(),
                    (int) Math.round(c.happiness()), c.ownerName(), Settlement.Status.COLONY, colonyTax(c)));
        }
        for (VillageInfo v : villages(level)) {
            int best = bestReputation(v, citizens);
            int full = v.villagers() * cfg.villageTaxPerVillager;
            UUID controller = v.controller();
            Settlement.Status status = null;
            int tax = 0;
            if (controller != null) {
                if (citizens.contains(controller)) {
                    status = Settlement.Status.CONTROLLED;
                    tax = full;
                }
            } else if (country.equals(data.annexed.get(v.key()))) {
                status = Settlement.Status.ANNEXED;
                tax = full * cfg.annexedVillageTributePercent / 100;
            } else if (!data.annexed.containsKey(v.key()) && best >= cfg.villageAnnexReputation) {
                status = Settlement.Status.ANNEXABLE;
            }
            if (status != null) {
                out.add(new Settlement(v.key(), Settlement.Kind.VILLAGE, v.name(), v.center(), v.villagers(), best,
                        controller != null ? v.controllerName() : "", status, tax));
            }
        }
        return out;
    }

    /** What the country's settlements pay this cycle. */
    public static List<Income> incomeOf(ServerLevel level, PoliticsManager mgr, String country) {
        List<Income> out = new ArrayList<>();
        for (Settlement s : of(level, mgr, country)) {
            if (s.status().belongs() && s.tax() > 0) out.add(new Income(s.name(), s.center(), s.tax()));
        }
        return out;
    }

    /** Happy colonies pay more: half the rate when nobody is happy, one and a half when everyone is. */
    private static int colonyTax(ColonyInfo c) {
        double mood = PoliticsConfig.get().colonyTaxFollowsHappiness ? 0.5 + Math.max(0, Math.min(10, c.happiness())) / 10.0 : 1.0;
        return (int) Math.round(c.population() * PoliticsConfig.get().colonyTaxPerCitizen * mood);
    }

    // ── Land ─────────────────────────────────────────────────────────────

    /**
     * Why the country may not take this chunk, or null if it may: the chunk belongs to a colony of a player
     * outside the country, or to a village that neither a citizen controls nor the country annexed.
     */
    public static Component claimBlocker(ServerLevel level, PoliticsManager mgr, String country, ChunkPos pos) {
        return claimBlocker(level, mgr, citizens(mgr, country), country, pos);
    }

    /** The same for land that a group of players without a country yet (a founder) is about to take. */
    public static Component claimBlocker(ServerLevel level, PoliticsManager mgr, Set<UUID> citizens, String country, ChunkPos pos) {
        if (level == null || !PoliticsConfig.get().settlementsBlockClaims) return null;
        ColonyInfo colony = colonyAt(level, pos);
        if (colony != null && !citizens.contains(colony.owner())) {
            return Component.translatable("message.politicsmod.claim.colony", colony.name());
        }
        VillageInfo village = villageAt(level, pos);
        if (village != null && !villageIsOurs(village, mgr, citizens, country)) {
            return Component.translatable("message.politicsmod.claim.village", village.name());
        }
        return null;
    }

    /** True when the chunk is land of one of the country's own settlements: the country takes it for free. */
    public static boolean ownLand(ServerLevel level, PoliticsManager mgr, String country, ChunkPos pos) {
        if (level == null || !enabled()) return false;
        Set<UUID> citizens = citizens(mgr, country);
        ColonyInfo colony = colonyAt(level, pos);
        if (colony != null && citizens.contains(colony.owner())) return true;
        VillageInfo village = villageAt(level, pos);
        return village != null && villageIsOurs(village, mgr, citizens, country);
    }

    private static boolean villageIsOurs(VillageInfo village, PoliticsManager mgr, Set<UUID> citizens, String country) {
        UUID controller = village.controller();
        if (controller != null) return citizens.contains(controller);
        return country != null && country.equals(mgr.settlementData().annexed.get(village.key()));
    }

    // ── Annexation ───────────────────────────────────────────────────────

    /** The leader annexes a village where a citizen is a friend of the village. */
    public static Result annex(ServerLevel level, PoliticsManager mgr, String country, String query) {
        if (!millenaire()) return fail("message.politicsmod.settlement.no_millenaire");
        VillageInfo v = findVillage(level, query);
        if (v == null) return fail("message.politicsmod.settlement.not_found", query);
        SettlementData data = mgr.settlementData();
        String owner = data.annexed.get(v.key());
        if (country.equals(owner)) return fail("message.politicsmod.settlement.already_ours", v.name());
        if (owner != null) return fail("message.politicsmod.settlement.taken", v.name(), owner);
        if (v.controller() != null) return fail("message.politicsmod.settlement.controlled", v.name(), v.controllerName());
        int need = PoliticsConfig.get().villageAnnexReputation;
        if (bestReputation(v, citizens(mgr, country)) < need) return fail("message.politicsmod.settlement.low_reputation", v.name(), need);
        data.annexed.put(v.key(), country);
        data.known.put(v.key(), country);
        mgr.postNews("news.politicsmod.settlement.annexed", List.of(country), country, v.name());
        mgr.setDirty();
        return new Result(true, Component.translatable("message.politicsmod.settlement.annexed", v.name()));
    }

    /** The leader lets an annexed village go. */
    public static Result release(ServerLevel level, PoliticsManager mgr, String country, String query) {
        SettlementData data = mgr.settlementData();
        VillageInfo v = millenaire() ? findVillage(level, query) : null;
        String key = v != null ? v.key() : query;
        String name = v != null ? v.name() : query;
        if (!country.equals(data.annexed.get(key))) return fail("message.politicsmod.settlement.not_annexed", name);
        data.annexed.remove(key);
        data.known.remove(key);
        mgr.postNews("news.politicsmod.settlement.released", List.of(country), country, name);
        mgr.setDirty();
        return new Result(true, Component.translatable("message.politicsmod.settlement.released", name));
    }

    private static VillageInfo findVillage(ServerLevel level, String query) {
        String q = query.trim().toLowerCase(Locale.ROOT);
        for (VillageInfo v : villages(level)) {
            if (v.key().equalsIgnoreCase(q) || v.name().toLowerCase(Locale.ROOT).equals(q)) return v;
        }
        return null;
    }

    // ── Periodic sync: break-aways, news, colony ranks ───────────────────

    /** Runs every few seconds on the server. */
    public static void sync(MinecraftServer server, PoliticsManager mgr) {
        if (!enabled()) return;
        ServerLevel level = server.overworld();
        SettlementData data = mgr.settlementData();
        boolean dirty = false;

        // Annexed villages that are gone, taken over by a player, or that the country fell out with
        if (millenaire()) {
            Map<String, VillageInfo> byKey = new HashMap<>();
            for (VillageInfo v : villages(level)) byKey.put(v.key(), v);
            for (Map.Entry<String, String> e : new ArrayList<>(data.annexed.entrySet())) {
                VillageInfo v = byKey.get(e.getKey());
                String country = e.getValue();
                if (v == null || v.controller() != null || mgr.getCountry(country) == null) {
                    data.annexed.remove(e.getKey());
                    dirty = true;
                    continue;
                }
                Set<UUID> citizens = citizens(mgr, country);
                if (bestReputation(v, citizens) < PoliticsConfig.get().villageKeepReputation || worstReputation(v, citizens) <= PUBLIC_ENEMY) {
                    data.annexed.remove(e.getKey());
                    data.known.remove(e.getKey());
                    mgr.postNews("news.politicsmod.settlement.broke_away", List.of(country), v.name(), country);
                    dirty = true;
                }
            }
        }

        // News when a settlement joins or leaves a country
        Map<String, String> belongs = new HashMap<>();
        Map<String, Settlement> settlements = new HashMap<>();
        for (Country c : mgr.getCountries().values()) {
            for (Settlement s : of(level, mgr, c.getName())) {
                if (!s.status().belongs()) continue;
                belongs.put(s.key(), c.getName());
                settlements.put(s.key(), s);
            }
        }
        for (Map.Entry<String, String> e : belongs.entrySet()) {
            String before = data.known.get(e.getKey());
            if (e.getValue().equals(before)) continue;
            Settlement s = settlements.get(e.getKey());
            String key = s.kind() == Settlement.Kind.COLONY ? "news.politicsmod.settlement.colony_joined" : "news.politicsmod.settlement.village_joined";
            mgr.postNews(key, List.of(e.getValue()), s.name(), e.getValue());
            data.known.put(e.getKey(), e.getValue());
            dirty = true;
        }
        for (Map.Entry<String, String> e : new ArrayList<>(data.known.entrySet())) {
            if (belongs.containsKey(e.getKey())) continue;
            data.known.remove(e.getKey());
            dirty = true;
        }

        if (minecolonies() && PoliticsConfig.get().colonyRanksFollowDiplomacy) {
            dirty |= syncColonyRanks(server, level, mgr, data);
        }
        if (dirty) mgr.setDirty();
    }

    /** Citizens and allies are friends of a citizen's colony, enemies at war are hostile to it. */
    private static boolean syncColonyRanks(MinecraftServer server, ServerLevel level, PoliticsManager mgr, SettlementData data) {
        try {
            boolean changed = false;
            Set<String> alive = MineColoniesCompat.keys(level);
            changed |= data.managedRanks.keySet().removeIf(k -> !alive.contains(k));
            for (ColonyInfo colony : colonies(level)) {
                Map<UUID, String> desired = new HashMap<>();
                String country = mgr.getPlayerCountry(colony.owner());
                Country c = country == null ? null : mgr.getCountry(country);
                if (c != null && c.getRole(colony.owner()) != CountryRole.GUEST) {
                    for (Country other : mgr.getCountries().values()) {
                        String rank = other == c || mgr.isAllied(country, other.getName()) ? MineColoniesCompat.FRIEND
                                : mgr.isAtWar(country, other.getName()) ? MineColoniesCompat.HOSTILE : null;
                        if (rank == null) continue;
                        other.getMembers().forEach((id, role) -> {
                            if (role != CountryRole.GUEST) desired.put(id, rank);
                        });
                    }
                }
                Map<UUID, String> managed = data.managedRanks.computeIfAbsent(colony.key(), k -> new HashMap<>());
                if (desired.isEmpty() && managed.isEmpty()) {
                    data.managedRanks.remove(colony.key());
                    continue;
                }
                changed |= MineColoniesCompat.syncRanks(level, colony.id(), desired, managed, id -> name(server, id));
                if (managed.isEmpty()) data.managedRanks.remove(colony.key());
            }
            return changed;
        } catch (Throwable t) {
            colonyBroken = broken("MineColonies", t);
            return false;
        }
    }

    // ── Reading the mods safely ──────────────────────────────────────────

    private static List<ColonyInfo> colonies(ServerLevel level) {
        if (!minecolonies()) return Collections.emptyList();
        try {
            return MineColoniesCompat.colonies(level);
        } catch (Throwable t) {
            colonyBroken = broken("MineColonies", t);
            return Collections.emptyList();
        }
    }

    private static ColonyInfo colonyAt(ServerLevel level, ChunkPos pos) {
        if (!minecolonies()) return null;
        try {
            return MineColoniesCompat.colonyAt(level, pos);
        } catch (Throwable t) {
            colonyBroken = broken("MineColonies", t);
            return null;
        }
    }

    private static List<VillageInfo> villages(ServerLevel level) {
        if (!millenaire()) return Collections.emptyList();
        try {
            return MillenaireCompat.villages(level);
        } catch (Throwable t) {
            villageBroken = broken("Millénaire", t);
            return Collections.emptyList();
        }
    }

    private static VillageInfo villageAt(ServerLevel level, ChunkPos pos) {
        if (!millenaire()) return null;
        try {
            return MillenaireCompat.villageAt(level, pos);
        } catch (Throwable t) {
            villageBroken = broken("Millénaire", t);
            return null;
        }
    }

    private static int bestReputation(VillageInfo v, Set<UUID> citizens) {
        int best = Integer.MIN_VALUE;
        for (UUID id : citizens) best = Math.max(best, v.reputation(id));
        return best == Integer.MIN_VALUE ? 0 : best;
    }

    private static int worstReputation(VillageInfo v, Set<UUID> citizens) {
        int worst = Integer.MAX_VALUE;
        for (UUID id : citizens) worst = Math.min(worst, v.reputation(id));
        return worst == Integer.MAX_VALUE ? 0 : worst;
    }

    static Set<UUID> citizens(PoliticsManager mgr, String country) {
        Set<UUID> set = new HashSet<>();
        Country c = country == null ? null : mgr.getCountry(country);
        if (c != null) c.getMembers().forEach((id, role) -> {
            if (role != CountryRole.GUEST) set.add(id);
        });
        return set;
    }

    private static String name(MinecraftServer server, UUID id) {
        if (server.getProfileCache() == null) return null;
        return server.getProfileCache().get(id).map(GameProfile::getName).orElse(null);
    }

    private static Result fail(String key, Object... args) {
        return new Result(false, Component.translatable(key, args));
    }

    private static boolean broken(String mod, Throwable t) {
        Politicsmod.LOGGER.warn("{} integration switched off: this version of {} is not supported", mod, mod, t);
        return true;
    }
}
