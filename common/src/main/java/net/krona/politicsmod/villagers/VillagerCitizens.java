package net.krona.politicsmod.villagers;

import net.krona.politicsmod.PoliticsManager;
import net.krona.politicsmod.config.PoliticsConfig;
import net.krona.politicsmod.politics.Country;
import net.krona.politicsmod.politics.Election;
import net.krona.politicsmod.politics.NewsEntry;
import net.krona.politicsmod.politics.Notify;
import net.krona.politicsmod.realm.Realm;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Vanilla villagers as citizens of the city they live in (0.5.1): counted in the population, paying a small tax to
 * the city, and running for mayor with a program. Villagers are found in the loaded chunks of each city once per
 * economy cycle; the ones in unloaded chunks are remembered as they were.
 */
public final class VillagerCitizens {

    private static final Random RANDOM = new Random();

    /** Names for villagers without a name tag; picked from the villager's id, so a villager keeps its name. */
    private static final String[] NAMES = {
            "Abel", "Ada", "Agnes", "Albin", "Alma", "Ansel", "Arno", "Astrid", "Basil", "Beatrix", "Bertram", "Bruno",
            "Cecil", "Clara", "Conrad", "Dora", "Edgar", "Edith", "Elias", "Elma", "Emil", "Erna", "Felix", "Frida",
            "Gerald", "Greta", "Gustav", "Hanna", "Hector", "Hilda", "Ida", "Igor", "Ingrid", "Ivo", "Jasper", "Jonas",
            "Klara", "Kurt", "Lena", "Leopold", "Linus", "Lotte", "Ludo", "Magda", "Marek", "Martha", "Milo", "Mira",
            "Nils", "Nora", "Olaf", "Olga", "Oskar", "Otto", "Paula", "Petra", "Quentin", "Rasmus", "Rosa", "Rufus",
            "Selma", "Silas", "Sven", "Tilda", "Tobias", "Ulla", "Ursula", "Valter", "Vera", "Viktor", "Wanda", "Wilma"};

    private VillagerCitizens() {
    }

    public static boolean enabled() {
        return PoliticsConfig.get().villagerCitizensEnabled;
    }

    public static String nameFor(UUID id) {
        return NAMES[Math.floorMod(id.hashCode(), NAMES.length)];
    }

    private static String nameOf(Villager v) {
        if (v.hasCustomName() && v.getCustomName() != null) {
            String custom = v.getCustomName().getString().strip();
            if (!custom.isEmpty()) return custom.length() > 24 ? custom.substring(0, 24) : custom;
        }
        return nameFor(v.getUUID());
    }

    private static String professionOf(Villager v) {
        var key = BuiltInRegistries.VILLAGER_PROFESSION.getKey(v.getVillagerData().getProfession());
        return key == null ? "none" : key.getPath();
    }

    // ── Census ───────────────────────────────────────────────────────────

    /** Finds the villagers living in every city and forgets the ones that are gone. Run once per economy cycle. */
    public static void census(PoliticsManager mgr, ServerLevel level) {
        if (!enabled()) {
            for (Country c : mgr.getCountries().values()) {
                c.villagers.clear();
                c.villagerMayors.clear();
            }
            return;
        }
        long cycle = mgr.getCycleCount();
        for (Country c : mgr.getCountries().values()) {
            Map<UUID, Villager> found = new HashMap<>();
            Map<UUID, String> cityOf = new HashMap<>();
            Set<Long> loaded = new HashSet<>();
            for (ChunkPos pos : mgr.getChunksOf(c.getName())) {
                String city = mgr.getCityAt(pos);
                if (city == null || !c.cities.contains(city) || !level.areEntitiesLoaded(pos.toLong())) continue;
                loaded.add(pos.toLong());
                AABB box = new AABB(pos.getMinBlockX(), level.getMinBuildHeight(), pos.getMinBlockZ(),
                        pos.getMaxBlockX() + 1, level.getMaxBuildHeight(), pos.getMaxBlockZ() + 1);
                for (Villager v : level.getEntitiesOfClass(Villager.class, box, v -> v.isAlive() && !v.isBaby())) {
                    found.put(v.getUUID(), v);
                    cityOf.put(v.getUUID(), city);
                }
            }
            // Villagers seen now: new citizens or moved within the country
            for (Map.Entry<UUID, Villager> e : found.entrySet()) {
                Villager v = e.getValue();
                String city = cityOf.get(e.getKey());
                long chunk = new ChunkPos(v.blockPosition()).toLong();
                VillagerCitizen known = c.villagers.get(e.getKey());
                if (known == null) {
                    c.villagers.put(e.getKey(), new VillagerCitizen(e.getKey(), nameOf(v), city, professionOf(v), chunk, cycle));
                } else {
                    if (v.hasCustomName()) known.name = nameOf(v);
                    known.city = city;
                    known.profession = professionOf(v);
                    known.chunk = chunk;
                    known.seenCycle = cycle;
                }
            }
            // Gone: their chunk (with its entities) is loaded but they are in no city of the country, or their city is no more
            c.villagers.values().removeIf(v -> !c.cities.contains(v.city)
                    || (!found.containsKey(v.id) && (loaded.contains(v.chunk) || level.areEntitiesLoaded(v.chunk))));
            // A villager mayor who left ends their term
            for (Map.Entry<String, VillagerMayor> e : new ArrayList<>(c.villagerMayors.entrySet())) {
                if (!c.cities.contains(e.getKey())) {
                    c.villagerMayors.remove(e.getKey());
                } else if (!c.villagers.containsKey(e.getValue().id())) {
                    c.villagerMayors.remove(e.getKey());
                    mgr.postNews("news.politicsmod.villager_mayor_gone", List.of(c.getName()), e.getValue().name(), e.getKey());
                }
            }
        }
        mgr.setDirty();
    }

    // ── Economy ──────────────────────────────────────────────────────────

    /** Tax of the villagers of one city per cycle, before the federal tax; pct = the city's tax modifiers. */
    public static int cityTax(Country c, String city, int pct) {
        if (!enabled()) return 0;
        int villagers = c.villagersIn(city);
        return villagers == 0 ? 0 : Realm.apply(villagers * PoliticsConfig.get().villagerTaxPerCycle, pct);
    }

    /** Builder mayors buy development from their city's treasury when it has enough. */
    public static void builders(PoliticsManager mgr, Country c) {
        for (Map.Entry<String, VillagerMayor> e : c.villagerMayors.entrySet()) {
            if (e.getValue().program() != MayorProgram.BUILDER || !Realm.enabled()) continue;
            String city = e.getKey();
            int cost = Realm.developmentCost(c, city);
            int balance = c.cityBalances.getOrDefault(city, 0);
            if (cost <= 0 || balance < cost) continue;
            c.cityBalances.put(city, balance - cost);
            int level = c.cityDevelopment.merge(city, 1, Integer::sum);
            mgr.postNews("news.politicsmod.villager_mayor_built", List.of(c.getName()), e.getValue().name(), city, String.valueOf(level));
        }
    }

    // ── Elections ────────────────────────────────────────────────────────

    /** A villager joins a mayor election: the villager mayor runs again, otherwise one of the city's villagers. */
    public static void nominate(PoliticsManager mgr, Country c, Election e) {
        PoliticsConfig cfg = PoliticsConfig.get();
        if (!enabled() || !cfg.villagerCandidates || e.type != Election.Type.MAYOR) return;
        List<VillagerCitizen> locals = new ArrayList<>();
        for (VillagerCitizen v : c.villagers.values()) if (v.city.equals(e.city)) locals.add(v);
        if (locals.size() < cfg.villagerCandidateMinVillagers) return;

        VillagerMayor incumbent = c.villagerMayors.get(e.city);
        VillagerCitizen runner = null;
        MayorProgram program;
        if (incumbent != null && c.villagers.containsKey(incumbent.id())) {
            runner = c.villagers.get(incumbent.id());
            program = incumbent.program();
        } else {
            runner = locals.get(RANDOM.nextInt(locals.size()));
            program = MayorProgram.values()[RANDOM.nextInt(MayorProgram.values().length)];
        }
        String name = runner.name;
        for (String taken : e.candidates.values()) if (taken.equalsIgnoreCase(name)) name = name + " " + (1 + RANDOM.nextInt(9));
        e.candidates.put(runner.id, name);
        e.programs.put(runner.id, program.key());
        mgr.setDirty();
        Notify.members(c, Component.translatable("message.politicsmod.election.villager_runs", name, e.city,
                Component.translatable(program.nameKey())).withStyle(ChatFormatting.YELLOW));
    }

    /** The program a candidate runs on, or null for players. */
    public static MayorProgram programOf(Election e, UUID candidate) {
        String key = e.programs.get(candidate);
        return key == null ? null : MayorProgram.byKey(key);
    }

    /** '#'-key of a program for news and payloads. */
    public static String programArg(MayorProgram p) {
        return NewsEntry.TRANSLATE_PREFIX + p.nameKey();
    }
}
