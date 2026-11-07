package net.krona.politicsmod.realm;

import net.krona.politicsmod.ModCommands;
import net.krona.politicsmod.PoliticsManager;
import net.krona.politicsmod.commands.StateCommands;
import net.krona.politicsmod.config.PoliticsConfig;
import net.krona.politicsmod.politics.Country;
import net.krona.politicsmod.politics.CountryRole;
import net.krona.politicsmod.politics.EconomySummary;
import net.krona.politicsmod.politics.ElectionService;
import net.krona.politicsmod.war.SiegeService;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * "Needs attention" icons in the dashboard header: what is going wrong, what is waiting for an answer.
 * Each one says which tab to open. Built on the server, so they match what the commands would say.
 */
public final class Alerts {

    public static final int INFO = 0, WARN = 1, DANGER = 2;

    // Dashboard tabs, in CountryDashboardScreen order
    public static final int TAB_MAIN = 0, TAB_MAP = 1, TAB_POLITICS = 3, TAB_DIPLOMACY = 4, TAB_ECONOMY = 5;

    /** How long after the law cooldown ends the leader is told laws can change again. */
    private static final long LAW_READY_SHOWN_MS = 30 * 60_000L;

    public record Alert(String key, List<String> args, int level, int tab) {
    }

    private Alerts() {
    }

    public static List<Alert> of(PoliticsManager mgr, Country c, UUID viewer, EconomySummary economy,
                                 Map<String, Realm.CityStats> cities, Breakdown trend) {
        List<Alert> list = new ArrayList<>();
        boolean leader = c.getRole(viewer) == CountryRole.LEADER;
        PoliticsConfig cfg = PoliticsConfig.get();

        // Money
        if (economy.treasury() < 0) {
            list.add(alert("alert.politicsmod.bankrupt", DANGER, TAB_ECONOMY));
        } else if (economy.net() < 0 && economy.treasury() + economy.net() * 10L < 0) {
            list.add(alert("alert.politicsmod.treasury_falling", WARN, TAB_ECONOMY,
                    String.valueOf(economy.treasury() / -economy.net())));
        }

        // War
        int sieges = 0;
        for (SiegeService.Siege s : SiegeService.of(c.getName())) if (s.defender.equals(c.getName())) sieges++;
        if (sieges > 0) list.add(alert("alert.politicsmod.siege", DANGER, TAB_DIPLOMACY, String.valueOf(sieges)));

        // Realm
        if (Realm.enabled()) {
            if (c.stability <= -2) {
                list.add(alert("alert.politicsmod.stability_low", DANGER, TAB_MAIN, Realm.signed(c.stability)));
            } else if (trend.total() < 0 && c.stability <= 0) {
                list.add(alert("alert.politicsmod.stability_falling", WARN, TAB_MAIN, Breakdown.format(trend.total(), 1)));
            }
            int over = Realm.overextension(mgr, c.getName());
            if (over >= 25) list.add(alert("alert.politicsmod.overextension", WARN, TAB_MAP, String.valueOf(over)));
            for (Map.Entry<String, Realm.CityStats> e : cities.entrySet()) {
                int unrest = e.getValue().unrestValue();
                if (unrest >= cfg.unrestSecedeThreshold) {
                    list.add(alert("alert.politicsmod.city_revolt", DANGER, TAB_MAIN, e.getKey(), String.valueOf(unrest)));
                } else if (unrest >= Realm.UNREST_TAX_START) {
                    list.add(alert("alert.politicsmod.city_unrest", WARN, TAB_MAIN, e.getKey(), String.valueOf(unrest)));
                }
            }
            if (leader && c.stability < 0 && Realm.stabilizeCooldown(c) == 0 && c.balance >= Realm.stabilizeCost(c)) {
                list.add(alert("alert.politicsmod.can_stabilize", INFO, TAB_MAIN, String.valueOf(Realm.stabilizeCost(c))));
            }
        }

        // Politics
        if (c.election != null && !c.election.votes.containsKey(viewer)) {
            list.add(alert("alert.politicsmod.vote", INFO, TAB_POLITICS));
        }
        if (leader && !c.petition.isEmpty()) {
            list.add(alert("alert.politicsmod.petition", WARN, TAB_POLITICS, String.valueOf(c.petition.size()),
                    String.valueOf(ElectionService.petitionNeeded(c))));
        }
        if (leader && c.lawChangedAt > 0) {
            long readyAt = c.lawChangedAt + cfg.lawCooldownMs();
            long now = System.currentTimeMillis();
            if (now >= readyAt && now - readyAt < LAW_READY_SHOWN_MS) list.add(alert("alert.politicsmod.laws_ready", INFO, TAB_POLITICS));
        }

        // Waiting for an answer
        if (leader && ModCommands.getPendingDiplomacy(c.getName()) != null) {
            list.add(alert("alert.politicsmod.proposal", INFO, TAB_DIPLOMACY, ModCommands.getPendingDiplomacy(c.getName())[0]));
        }
        if (leader && StateCommands.getLoanOffer(c.getName()) != null) {
            list.add(alert("alert.politicsmod.loan_offer", INFO, TAB_ECONOMY, StateCommands.getLoanOffer(c.getName()).lender()));
        }
        return list;
    }

    private static Alert alert(String key, int level, int tab, String... args) {
        return new Alert(key, List.of(args), level, tab);
    }
}
