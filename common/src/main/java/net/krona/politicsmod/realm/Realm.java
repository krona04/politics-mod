package net.krona.politicsmod.realm;

import net.krona.politicsmod.PoliticsManager;
import net.krona.politicsmod.config.PoliticsConfig;
import net.krona.politicsmod.politics.Country;
import net.krona.politicsmod.politics.CountryRole;
import net.krona.politicsmod.politics.Law;
import net.krona.politicsmod.politics.NewsEntry;
import net.krona.politicsmod.politics.Notify;
import net.krona.politicsmod.war.SiegeService;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The realm layer (0.5): what state a country is in beyond its money and land.
 *
 * - Stability (−3…+3) moves by progress points: every economy cycle peace, war, debt, overextension and unrest add or
 *   take points (the trend), and events (a lost war, a law change, a city leaving) give a one-off shock.
 * - Legitimacy (0–100) is how much the citizens accept the leader: it grows with time in power and with won
 *   elections, and falls with petitions, bankruptcy and disorder.
 * - Prestige (−100…100) comes from victories, new cities and annexed villages and fades over time.
 * - A city is a province: it has development (bought from a treasury, plus one level per two tax blocks) and unrest.
 * - Land a country takes is not its core at first: lower tax, higher unrest, until it has been held long enough.
 *
 * Every effect on money goes through modifiers, so each number in the menu can be explained part by part. The
 * whole layer is switched off with PoliticsConfig.realmEnabled; laws and stored modifiers keep working then.
 */
public final class Realm {

    // Ledger sample layout (PoliticsManager.history)
    public static final int H_DAY = 0, H_TREASURY = 1, H_NET = 2, H_CHUNKS = 3, H_CITIZENS = 4, H_STABILITY = 5,
            H_LEGITIMACY = 6, H_PRESTIGE = 7, H_DEVELOPMENT = 8, H_SIZE = 9;

    /** Development a city gets from its tax blocks on its own: one level per two blocks, up to this many. */
    public static final int MAX_PASSIVE_DEVELOPMENT = 5;
    /** Unrest above this lowers a city's tax income by one percent per point. */
    public static final int UNREST_TAX_START = 50;

    private Realm() {
    }

    public static boolean enabled() {
        return PoliticsConfig.get().realmEnabled;
    }

    /** "+2", "0", "−1" — the way levels are shown. */
    public static String signed(int value) {
        return value > 0 ? "+" + value : value < 0 ? "−" + (-value) : "0";
    }

    // ── Modifiers ────────────────────────────────────────────────────────

    /** Country-wide modifiers of one type: laws, stability, legitimacy, prestige and stored modifiers (percent). */
    public static Breakdown modifiers(Country c, ModifierType type) {
        PoliticsConfig cfg = PoliticsConfig.get();
        Breakdown.Builder b = Breakdown.builder();
        boolean on = enabled();
        switch (type) {
            case TAX_INCOME -> {
                if (c.hasLaw(Law.MARTIAL_LAW)) b.add(-cfg.martialLawIncomePenaltyPercent, "realm.politicsmod.source.law",
                        NewsEntry.TRANSLATE_PREFIX + Law.MARTIAL_LAW.nameKey());
                if (on) {
                    b.add(c.stability * cfg.stabilityTaxPercent, "realm.politicsmod.source.stability", signed(c.stability));
                    if (c.legitimacy >= 80) b.add(5, "realm.politicsmod.source.legitimacy_high");
                    else if (c.legitimacy < 30) b.add(-10, "realm.politicsmod.source.legitimacy_low");
                }
            }
            case EMBASSY_INCOME -> {
                if (c.hasLaw(Law.FREE_TRADE)) b.add(cfg.freeTradeEmbassyBonusPercent, "realm.politicsmod.source.law",
                        NewsEntry.TRANSLATE_PREFIX + Law.FREE_TRADE.nameKey());
                if (on) b.add(Math.round(c.prestige / 4f), "realm.politicsmod.source.prestige");
            }
            case LAND_PRICE -> {
                if (on) b.add(-Math.round(c.prestige / 10f), "realm.politicsmod.source.prestige");
            }
            case SIEGE_DEFENSE -> {
                if (c.hasLaw(Law.MARTIAL_LAW)) b.add((cfg.martialLawSiegeMultiplier - 1) * 100, "realm.politicsmod.source.law",
                        NewsEntry.TRANSLATE_PREFIX + Law.MARTIAL_LAW.nameKey());
            }
            case UPKEEP -> {
            }
        }
        long now = System.currentTimeMillis();
        for (TimedModifier m : c.modifiers) {
            if (m.type() == type && !m.expired(now)) b.add(m.value(), "realm.politicsmod.source.named", m.name());
        }
        return b.build();
    }

    public static int percent(Country c, ModifierType type) {
        return modifiers(c, type).total();
    }

    /** amount × (100 + percent)%; a modifier can bring a sum down to zero, never below. */
    public static int apply(int amount, int percent) {
        return (int) Math.max(0, (long) amount * (100 + percent) / 100);
    }

    // ── Cities as provinces ──────────────────────────────────────────────

    /** What a city is made of right now; unrest is explained part by part. */
    public record CityStats(int chunks, int nonCore, int taxBlocks, int bought, int development, boolean besieged,
                            Breakdown unrest) {
        public int unrestValue() {
            return Math.max(0, Math.min(100, unrest.total()));
        }
    }

    /** Every city of the country, in the country's order. */
    public static Map<String, CityStats> cities(PoliticsManager mgr, Country c) {
        String name = c.getName();
        Map<String, int[]> counts = new HashMap<>(); // chunks, nonCore, taxBlocks
        for (ChunkPos pos : mgr.getChunksOf(name)) {
            String city = mgr.getCityAt(pos);
            if (city == null) continue;
            int[] n = counts.computeIfAbsent(city, k -> new int[3]);
            n[0]++;
            if (!mgr.isCore(pos)) n[1]++;
        }
        for (Long posLong : c.taxBlocks) {
            String city = mgr.getCityAt(new ChunkPos(BlockPos.of(posLong)));
            if (city != null) counts.computeIfAbsent(city, k -> new int[3])[2]++;
        }
        java.util.Set<String> besieged = new java.util.HashSet<>();
        for (SiegeService.Siege s : SiegeService.of(name)) {
            if (s.defender.equals(name) && mgr.getCityAt(s.pos) != null) besieged.add(mgr.getCityAt(s.pos));
        }
        int wars = wars(mgr, name);

        Map<String, CityStats> result = new LinkedHashMap<>();
        for (String city : c.cities) {
            int[] n = counts.getOrDefault(city, new int[3]);
            int bought = c.cityDevelopment.getOrDefault(city, 0);
            int development = bought + Math.min(MAX_PASSIVE_DEVELOPMENT, n[2] / 2);
            Breakdown unrest = unrest(c, n[0], n[1], development, wars, besieged.contains(city));
            result.put(city, new CityStats(n[0], n[1], n[2], bought, development, besieged.contains(city), unrest));
        }
        return result;
    }

    private static Breakdown unrest(Country c, int chunks, int nonCore, int development, int wars, boolean besieged) {
        if (!enabled()) return Breakdown.EMPTY;
        Breakdown.Builder b = Breakdown.builder();
        if (c.federalTaxRate > 20) b.add((c.federalTaxRate - 20) / 2, "realm.politicsmod.unrest.federal_tax", String.valueOf(c.federalTaxRate));
        b.add(-c.stability * 5, "realm.politicsmod.unrest.stability", signed(c.stability));
        if (chunks > 0 && nonCore > 0) {
            int share = nonCore * 100 / chunks;
            b.add(Math.max(1, share * 30 / 100), "realm.politicsmod.unrest.non_core", String.valueOf(share));
        }
        b.add(-development * 2, "realm.politicsmod.unrest.development", String.valueOf(development));
        b.add(Math.min(2, wars) * 5, "realm.politicsmod.unrest.war");
        if (besieged) b.add(15, "realm.politicsmod.unrest.siege");
        if (c.isBankrupt()) b.add(15, "realm.politicsmod.unrest.bankrupt");
        if (c.legitimacy < 30) b.add(10, "realm.politicsmod.unrest.legitimacy");
        if (c.hasLaw(Law.MARTIAL_LAW)) b.add(-10, "realm.politicsmod.unrest.martial_law");
        return b.build();
    }

    /** Extra tax percent of a city's tax blocks: development raises it, high unrest lowers it. */
    public static int cityTaxPercent(CityStats s) {
        if (!enabled()) return 0;
        return s.development() * PoliticsConfig.get().developmentTaxPercent - Math.max(0, s.unrestValue() - UNREST_TAX_START);
    }

    /** Price of the next development level a city can buy, or -1 at the limit. */
    public static int developmentCost(Country c, String city) {
        int bought = c.cityDevelopment.getOrDefault(city, 0);
        if (bought >= PoliticsConfig.get().maxDevelopment) return -1;
        return PoliticsConfig.get().developmentCost * (bought + 1);
    }

    /** A mayor may also leave when the country falls apart (stability −3) or the city boils over. */
    public static boolean maySecede(PoliticsManager mgr, Country c, String city) {
        if (!enabled()) return false;
        if (c.stability <= -3) return true;
        CityStats s = cities(mgr, c).get(city);
        return s != null && s.unrestValue() >= PoliticsConfig.get().unrestSecedeThreshold;
    }

    // ── Tax income ───────────────────────────────────────────────────────

    /** Tax percentages of a country for one cycle: the country-wide total and each city's extra. */
    public record TaxRates(int country, Map<String, Integer> city) {
    }

    public static TaxRates taxRates(Country c, Map<String, CityStats> cities) {
        Map<String, Integer> city = new HashMap<>();
        for (Map.Entry<String, CityStats> e : cities.entrySet()) city.put(e.getKey(), cityTaxPercent(e.getValue()));
        return new TaxRates(percent(c, ModifierType.TAX_INCOME), city);
    }

    /** Income of one tax block in a chunk, after modifiers and the non-core cut. */
    public static int taxBlockIncome(PoliticsManager mgr, ChunkPos pos, TaxRates rates) {
        return taxBlockIncome(mgr, pos, rates, true);
    }

    /** coreRule = false gives what the block would bring on core land (to show the loss). */
    public static int taxBlockIncome(PoliticsManager mgr, ChunkPos pos, TaxRates rates, boolean coreRule) {
        int pct = rates.country();
        String city = mgr.getCityAt(pos);
        if (city != null) pct += rates.city().getOrDefault(city, 0);
        int income = apply(PoliticsConfig.get().incomePerTaxBlock, pct);
        if (coreRule && enabled() && !mgr.isCore(pos)) income = income * PoliticsConfig.get().nonCoreTaxPercent / 100;
        return income;
    }

    // ── Country-wide numbers ─────────────────────────────────────────────

    public static int wars(PoliticsManager mgr, String country) {
        int n = 0;
        for (String other : mgr.getCountries().keySet()) {
            if (!other.equals(country) && mgr.isAtWar(country, other)) n++;
        }
        return n;
    }

    /** Share of the country's land that is not yet core, %. */
    public static int overextension(PoliticsManager mgr, String country) {
        int total = 0, nonCore = 0;
        for (ChunkPos pos : mgr.getChunksOf(country)) {
            total++;
            if (!mgr.isCore(pos)) nonCore++;
        }
        return total == 0 ? 0 : nonCore * 100 / total;
    }

    private static int activeEmbassies(PoliticsManager mgr, Country c) {
        int active = 0;
        for (String partner : c.embassyLinks.values()) {
            if (mgr.getCountry(partner) != null && !mgr.isAtWar(c.getName(), partner)) active++;
        }
        return active;
    }

    /** Stability progress the country gains or loses each cycle, and why. */
    public static Breakdown stabilityTrend(PoliticsManager mgr, Country c, Map<String, CityStats> cities) {
        if (!enabled()) return Breakdown.EMPTY;
        PoliticsConfig cfg = PoliticsConfig.get();
        Breakdown.Builder b = Breakdown.builder();
        int wars = wars(mgr, c.getName());
        if (wars == 0) b.add(1, "realm.politicsmod.trend.peace");
        else b.add(-3 * Math.min(2, wars), "realm.politicsmod.trend.war", String.valueOf(wars));
        if (c.isBankrupt()) b.add(-5, "realm.politicsmod.trend.bankrupt");
        else if (mgr.getDebt(c.getName()) == 0) b.add(1, "realm.politicsmod.trend.solvent");
        int over = overextension(mgr, c.getName());
        b.add(-over / 15, "realm.politicsmod.trend.overextension", String.valueOf(over));
        if (c.legitimacy >= 75) b.add(1, "realm.politicsmod.trend.legitimacy_high");
        else if (c.legitimacy < 30) b.add(-2, "realm.politicsmod.trend.legitimacy_low");
        if (c.federalTaxRate >= cfg.secedeThresholdPercent) b.add(-3, "realm.politicsmod.trend.federal_tax", String.valueOf(c.federalTaxRate));
        else if (c.federalTaxRate >= 50) b.add(-1, "realm.politicsmod.trend.federal_tax", String.valueOf(c.federalTaxRate));
        if (!cities.isEmpty()) {
            int sum = 0;
            for (CityStats s : cities.values()) sum += s.unrestValue();
            int avg = sum / cities.size();
            if (avg >= 50) b.add(-2, "realm.politicsmod.trend.unrest", String.valueOf(avg));
            else if (avg >= 30) b.add(-1, "realm.politicsmod.trend.unrest", String.valueOf(avg));
        }
        if (activeEmbassies(mgr, c) >= 2) b.add(1, "realm.politicsmod.trend.embassies");
        return b.build();
    }

    /** Legitimacy change per cycle in tenths of a point, and why. */
    public static Breakdown legitimacyTrend(Country c) {
        if (!enabled()) return Breakdown.EMPTY;
        Breakdown.Builder b = Breakdown.builder();
        if (c.legitimacy < 100) b.add(3, "realm.politicsmod.legitimacy.time");
        if (!c.petition.isEmpty()) {
            int needed = net.krona.politicsmod.politics.ElectionService.petitionNeeded(c);
            b.add(-Math.max(1, 10 * c.petition.size() / needed), "realm.politicsmod.legitimacy.petition",
                    String.valueOf(c.petition.size()), String.valueOf(needed));
        }
        if (c.isBankrupt()) b.add(-5, "realm.politicsmod.legitimacy.bankrupt");
        if (c.stability <= -2) b.add(-5, "realm.politicsmod.legitimacy.disorder");
        return b.build();
    }

    // ── The cycle ────────────────────────────────────────────────────────

    /** Run at the end of every economy cycle. */
    public static void cycle(PoliticsManager mgr) {
        mgr.advanceCycle();
        long now = System.currentTimeMillis();
        for (Country c : mgr.getCountries().values()) {
            c.modifiers.removeIf(m -> m.expired(now));
            if (!enabled()) continue;
            Map<String, CityStats> cities = cities(mgr, c);
            c.stabilityProgress += stabilityTrend(mgr, c, cities).total();
            settleStability(mgr, c);
            c.legitimacy = clamp(c.legitimacy + legitimacyTrend(c).total() / 10f, 0, 100);
            c.prestige -= c.prestige * PoliticsConfig.get().prestigeDecayPerMille / 1000f;
            if (Math.abs(c.prestige) < 0.05f) c.prestige = 0f;

            // A city about to boil over makes the news once, until it calms down
            for (Map.Entry<String, CityStats> e : cities.entrySet()) {
                boolean restless = e.getValue().unrestValue() >= PoliticsConfig.get().unrestSecedeThreshold;
                if (restless && c.restlessCities.add(e.getKey())) {
                    mgr.postNews("news.politicsmod.city_unrest", List.of(c.getName()), e.getKey(), c.getName());
                } else if (!restless) {
                    c.restlessCities.remove(e.getKey());
                }
            }
        }
        mgr.setDirty();
    }

    /** Turns progress into stability levels and announces a change. */
    private static void settleStability(PoliticsManager mgr, Country c) {
        int per = PoliticsConfig.get().stabilityProgressPerLevel;
        int before = c.stability;
        while (c.stabilityProgress >= per && c.stability < 3) {
            c.stability++;
            c.stabilityProgress -= per;
        }
        while (c.stabilityProgress <= -per && c.stability > -3) {
            c.stability--;
            c.stabilityProgress += per;
        }
        // Nothing to gain above +3 or to lose below −3
        if (c.stability >= 3) c.stabilityProgress = Math.min(0, c.stabilityProgress);
        if (c.stability <= -3) c.stabilityProgress = Math.max(0, c.stabilityProgress);
        c.stabilityProgress = Math.max(-per + 1, Math.min(per - 1, c.stabilityProgress));
        if (c.stability != before) {
            boolean up = c.stability > before;
            mgr.postNews(up ? "news.politicsmod.stability_up" : "news.politicsmod.stability_down", List.of(c.getName()),
                    c.getName(), signed(c.stability));
            Notify.members(c, Component.translatable(up ? "message.politicsmod.realm.stability_up" : "message.politicsmod.realm.stability_down",
                    signed(c.stability)).withStyle(up ? ChatFormatting.GREEN : ChatFormatting.RED));
        }
    }

    /** A one-off change of stability progress (an event); may move the level at once. */
    public static void shock(PoliticsManager mgr, Country c, int points) {
        if (!enabled() || c == null || points == 0) return;
        c.stabilityProgress += points;
        settleStability(mgr, c);
        mgr.setDirty();
    }

    public static void addPrestige(Country c, float amount) {
        if (!enabled() || c == null) return;
        c.prestige = clamp(c.prestige + amount, -100, 100);
    }

    private static float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }

    // ── Events ───────────────────────────────────────────────────────────

    public static void onSiegeWon(PoliticsManager mgr, Country attacker, Country defender) {
        addPrestige(attacker, 3);
        addPrestige(defender, -2);
        shock(mgr, defender, -10);
    }

    public static void onSurrender(PoliticsManager mgr, Country loser, Country winner) {
        addPrestige(winner, 15);
        addPrestige(loser, -15);
        if (enabled() && loser != null) loser.legitimacy = clamp(loser.legitimacy - 10, 0, 100);
        shock(mgr, loser, -40);
    }

    public static void onLawChanged(PoliticsManager mgr, Country c) {
        shock(mgr, c, -15);
    }

    public static void onSecession(PoliticsManager mgr, Country old) {
        addPrestige(old, -10);
        shock(mgr, old, -30);
    }

    public static void onBankrupt(PoliticsManager mgr, Country c) {
        shock(mgr, c, -30);
    }

    public static void onCityFounded(Country c) {
        addPrestige(c, 2);
    }

    public static void onSettlementAnnexed(Country c) {
        addPrestige(c, 3);
    }

    /** share = the winner's part of the votes (0–1). */
    public static void onLeaderElected(Country c, float share, boolean incumbent) {
        if (!enabled()) return;
        float fromVote = 30 + 70 * share;
        c.legitimacy = clamp(incumbent ? Math.max(c.legitimacy, fromVote) + 5 : fromVote, 0, 100);
    }

    public static void onElectionWithoutCandidates(Country c) {
        if (enabled()) c.legitimacy = clamp(c.legitimacy - 5, 0, 100);
    }

    // ── Actions ──────────────────────────────────────────────────────────

    /** What an action did: ok, and the message for the player. */
    public record Result(boolean ok, Component message) {
    }

    public static int stabilizeCost(Country c) {
        return PoliticsConfig.get().stabilizeBaseCost * (c.stability + 4);
    }

    public static long stabilizeCooldown(Country c) {
        return Math.max(0, c.stabilizedAt + PoliticsConfig.get().stabilizeCooldownMs() - System.currentTimeMillis());
    }

    /** The leader spends treasury money to calm the country. Returns the message for the player. */
    public static Result stabilize(PoliticsManager mgr, Country c) {
        if (!enabled()) return error("message.politicsmod.realm.disabled");
        if (c.stability >= 3) return error("message.politicsmod.realm.stability_max");
        long cd = stabilizeCooldown(c);
        if (cd > 0) return error("message.politicsmod.realm.stabilize_cooldown", (cd / 60_000) + 1);
        int cost = stabilizeCost(c);
        if (c.balance < cost) return error("message.politicsmod.realm.no_funds", cost);
        c.balance -= cost;
        c.stabilizedAt = System.currentTimeMillis();
        shock(mgr, c, PoliticsConfig.get().stabilizeProgress);
        mgr.postNews("news.politicsmod.stabilized", List.of(c.getName()), c.getName());
        return new Result(true, Component.translatable("message.politicsmod.realm.stabilized", cost,
                PoliticsConfig.get().stabilizeProgress).withStyle(ChatFormatting.GREEN));
    }

    /** Buys a development level: the city's own mayor pays from the city treasury, the leader from the nation's. */
    public static Result develop(PoliticsManager mgr, ServerPlayer player, Country c, String city) {
        if (!enabled()) return error("message.politicsmod.realm.disabled");
        if (!c.cities.contains(city)) return error("message.politicsmod.city.not_found", city);
        boolean mayor = city.equals(mgr.getMayorCity(c.getName(), player.getUUID()));
        boolean leader = c.getRole(player.getUUID()) == CountryRole.LEADER;
        if (!mayor && !leader) return error("message.politicsmod.realm.develop_role");
        int cost = developmentCost(c, city);
        if (cost < 0) return error("message.politicsmod.realm.develop_max", PoliticsConfig.get().maxDevelopment);
        if (mayor && !leader) {
            int cityBalance = c.cityBalances.getOrDefault(city, 0);
            if (cityBalance < cost) return error("message.politicsmod.city.no_city_funds", cost);
            c.cityBalances.put(city, cityBalance - cost);
        } else {
            if (c.balance < cost) return error("message.politicsmod.realm.no_funds", cost);
            c.balance -= cost;
        }
        int level = c.cityDevelopment.merge(city, 1, Integer::sum);
        mgr.setDirty();
        mgr.postNews("news.politicsmod.city_developed", List.of(c.getName()), city, String.valueOf(level));
        return new Result(true, Component.translatable("message.politicsmod.realm.developed", city, level, cost)
                .withStyle(ChatFormatting.GREEN));
    }

    private static Result error(String key, Object... args) {
        return new Result(false, Component.translatable(key, args).withStyle(ChatFormatting.RED));
    }

    // ── Ledger ───────────────────────────────────────────────────────────

    public static int totalDevelopment(PoliticsManager mgr, Country c) {
        int sum = 0;
        for (CityStats s : cities(mgr, c).values()) sum += s.development();
        return sum;
    }

    /** One sample per country at dawn; net = what the treasury gained over the day. */
    public static void snapshot(PoliticsManager mgr, long day, Map<String, Long> netByCountry) {
        for (Country c : new ArrayList<>(mgr.getCountries().values())) {
            int[] s = new int[H_SIZE];
            s[H_DAY] = (int) day;
            s[H_TREASURY] = c.balance;
            s[H_NET] = (int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, netByCountry.getOrDefault(c.getName(), 0L)));
            s[H_CHUNKS] = mgr.getChunksOf(c.getName()).size();
            s[H_CITIZENS] = c.getMembers().size();
            s[H_STABILITY] = c.stability;
            s[H_LEGITIMACY] = Math.round(c.legitimacy);
            s[H_PRESTIGE] = Math.round(c.prestige);
            s[H_DEVELOPMENT] = totalDevelopment(mgr, c);
            mgr.addHistory(c.getName(), s);
        }
    }
}
