package net.krona.politicsmod.politics;

import net.krona.politicsmod.PoliticsManager;
import net.krona.politicsmod.config.PoliticsConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.UUID;

/** Starting and finishing elections. Checked once a second from the server tick. */
public final class ElectionService {

    private ElectionService() {
    }

    /** Milliseconds until the country may hold another election; 0 when it may now. */
    public static long cooldownLeft(Country country) {
        return Math.max(0, country.lastElectionAt + PoliticsConfig.get().electionCooldownMs() - System.currentTimeMillis());
    }

    /** Signatures needed for the petition to start a leader election. */
    public static int petitionNeeded(Country country) {
        int citizens = Math.max(1, country.getMembers().size() - 1); // the leader doesn't sign against themselves
        return Math.max(1, (int) Math.ceil(citizens * PoliticsConfig.get().petitionPercent / 100.0));
    }

    public static void start(PoliticsManager mgr, Country country, Election.Type type, String city) {
        country.election = new Election(type, city, System.currentTimeMillis() + PoliticsConfig.get().electionDurationMs());
        if (type == Election.Type.LEADER) country.petition.clear();
        mgr.setDirty();
        int minutes = PoliticsConfig.get().electionDurationMinutes;
        if (type == Election.Type.LEADER) {
            mgr.postNews("news.politicsmod.election_leader_started", List.of(country.getName()), country.getName());
            Notify.members(country, Component.translatable("message.politicsmod.election.started_leader", minutes)
                    .withStyle(ChatFormatting.GOLD));
        } else {
            mgr.postNews("news.politicsmod.election_mayor_started", List.of(country.getName()), city, country.getName());
            Notify.members(country, Component.translatable("message.politicsmod.election.started_mayor", city, minutes)
                    .withStyle(ChatFormatting.GOLD));
            net.krona.politicsmod.villagers.VillagerCitizens.nominate(mgr, country, country.election);
        }
    }

    /** Ends elections whose time is up and starts leader elections that a petition asked for. */
    public static void tick(PoliticsManager mgr) {
        long now = System.currentTimeMillis();
        for (Country country : mgr.getCountries().values()) {
            Election e = country.election;
            if (e != null) {
                if (now >= e.endsAt) finish(mgr, country);
            } else if (!country.petition.isEmpty() && country.petition.size() >= petitionNeeded(country)
                    && cooldownLeft(country) == 0) {
                start(mgr, country, Election.Type.LEADER, "");
            }
        }
    }

    private static void finish(PoliticsManager mgr, Country country) {
        Election e = country.election;
        country.election = null;
        country.lastElectionAt = System.currentTimeMillis();
        mgr.setDirty();

        // Candidates who left the country can't win; nor villagers who no longer live in the city
        e.candidates.keySet().removeIf(id -> e.programs.containsKey(id)
                ? !country.villagers.containsKey(id) || !country.villagers.get(id).city.equals(e.city)
                : country.getRole(id) == CountryRole.GUEST);
        e.votes.entrySet().removeIf(v -> country.getRole(v.getKey()) == CountryRole.GUEST || !e.candidates.containsKey(v.getValue()));
        if (e.type == Election.Type.MAYOR && !country.cities.contains(e.city)) return;

        net.krona.politicsmod.villagers.VillagerMayor villagerIncumbent = country.villagerMayors.get(e.city);
        UUID incumbent = e.type == Election.Type.LEADER ? country.getLeader()
                : villagerIncumbent != null ? villagerIncumbent.id() : country.cityMayors.get(e.city);
        UUID winner = e.winner(incumbent);
        if (winner == null) {
            mgr.postNews("news.politicsmod.election_no_candidates", List.of(country.getName()), country.getName());
            if (e.type == Election.Type.LEADER) net.krona.politicsmod.realm.Realm.onElectionWithoutCandidates(country);
            Notify.members(country, Component.translatable("message.politicsmod.election.no_candidates").withStyle(ChatFormatting.YELLOW));
            return;
        }
        String winnerName = e.candidates.get(winner);
        String votes = String.valueOf(e.votesFor(winner));

        if (e.type == Election.Type.LEADER) {
            // Legitimacy follows the share of the vote; a re-elected leader keeps what they had and gains a little
            net.krona.politicsmod.realm.Realm.onLeaderElected(country, e.votesFor(winner) / (float) Math.max(1, e.votes.size()),
                    winner.equals(incumbent));
            if (!winner.equals(incumbent)) {
                if (incumbent != null) {
                    boolean mayor = country.cityMayors.containsValue(incumbent);
                    country.setRole(incumbent, mayor ? CountryRole.MAYOR : CountryRole.CITIZEN);
                }
                country.setRole(winner, CountryRole.LEADER);
            }
            mgr.postNews("news.politicsmod.election_leader_won", List.of(country.getName()), winnerName, country.getName(), votes);
            Notify.members(country, Component.translatable("message.politicsmod.election.leader_won", winnerName, votes)
                    .withStyle(ChatFormatting.GREEN));
        } else if (e.programs.containsKey(winner)) {
            // A villager wins: the player mayor, if any, steps down
            UUID playerMayor = country.cityMayors.remove(e.city);
            if (playerMayor != null && country.getRole(playerMayor) == CountryRole.MAYOR && !country.cityMayors.containsValue(playerMayor)) {
                country.setRole(playerMayor, CountryRole.CITIZEN);
            }
            net.krona.politicsmod.villagers.MayorProgram program = net.krona.politicsmod.villagers.VillagerCitizens.programOf(e, winner);
            country.villagerMayors.put(e.city, new net.krona.politicsmod.villagers.VillagerMayor(winner, winnerName, program,
                    mgr.getCycleCount()));
            mgr.postNews("news.politicsmod.villager_mayor_won", List.of(country.getName()), winnerName, e.city,
                    net.krona.politicsmod.villagers.VillagerCitizens.programArg(program), votes);
            Notify.members(country, Component.translatable("message.politicsmod.election.villager_won", winnerName, e.city,
                    Component.translatable(program.nameKey()), votes).withStyle(ChatFormatting.GREEN));
        } else {
            mgr.setCityMayor(country.getName(), e.city, winner);
            if (country.getRole(winner) == CountryRole.CITIZEN) country.setRole(winner, CountryRole.MAYOR);
            if (incumbent != null && !incumbent.equals(winner) && country.getRole(incumbent) == CountryRole.MAYOR
                    && !country.cityMayors.containsValue(incumbent)) {
                country.setRole(incumbent, CountryRole.CITIZEN);
            }
            mgr.postNews("news.politicsmod.election_mayor_won", List.of(country.getName()), winnerName, e.city, votes);
            Notify.members(country, Component.translatable("message.politicsmod.election.mayor_won", winnerName, e.city, votes)
                    .withStyle(ChatFormatting.GREEN));
        }
        mgr.setDirty();
    }
}
