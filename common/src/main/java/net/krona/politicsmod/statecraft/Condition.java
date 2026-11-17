package net.krona.politicsmod.statecraft;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import net.krona.politicsmod.politics.DiplomacyStatus;
import net.krona.politicsmod.politics.ElectionService;
import net.krona.politicsmod.politics.Law;
import net.krona.politicsmod.politics.NewsEntry;
import net.krona.politicsmod.realm.Realm;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.ToIntFunction;

/**
 * A requirement of an event, decision or goal, read from a datapack. Every condition can explain itself as lines
 * ("Cities: at least 3 (now 2)") and say how close the country is (for goal progress bars).
 *
 * JSON: {"type": "cities", "min": 3}, {"type": "law", "law": "martial_law"}, {"type": "any", "conditions": [...]};
 * an array means "all of these". "hidden": true keeps a condition out of the explanations.
 */
public interface Condition {

    boolean test(Scope s);

    /** Adds the lines that explain this condition; hidden conditions add none. */
    void describe(Scope s, List<Line> out, int indent);

    /** 0–1, how close the country is. */
    default float progress(Scope s) {
        return test(s) ? 1f : 0f;
    }

    Condition ALWAYS = new Condition() {
        @Override
        public boolean test(Scope s) {
            return true;
        }

        @Override
        public void describe(Scope s, List<Line> out, int indent) {
        }
    };

    // ── Values that numeric conditions compare ───────────────────────────

    /** Name -> how to read it from a scope. Rank and stability are shown specially. */
    Map<String, ToIntFunction<Scope>> VALUES = Map.ofEntries(
            Map.entry("stability", s -> s.country.stability),
            Map.entry("legitimacy", s -> Math.round(s.country.legitimacy)),
            Map.entry("prestige", s -> Math.round(s.country.prestige)),
            Map.entry("treasury", s -> s.country.balance),
            Map.entry("debt", s -> s.mgr.getDebt(s.country.getName())),
            Map.entry("chunks", s -> s.mgr.getChunksOf(s.country.getName()).size()),
            Map.entry("cities", s -> s.country.cities.size()),
            Map.entry("citizens", s -> s.country.getMembers().size()),
            Map.entry("villagers", s -> s.country.villagers.size()),
            Map.entry("population", s -> s.country.getMembers().size() + s.country.villagers.size()),
            Map.entry("development", Scope::totalDevelopment),
            Map.entry("max_unrest", Scope::maxUnrest),
            Map.entry("wars", s -> s.count(DiplomacyStatus.WAR)),
            Map.entry("allies", s -> s.count(DiplomacyStatus.ALLIANCE)),
            Map.entry("embassies", Scope::activeEmbassies),
            Map.entry("overextension", s -> Realm.overextension(s.mgr, s.country.getName())),
            Map.entry("tax_blocks", s -> s.country.taxBlocks.size()),
            Map.entry("federal_tax", s -> s.country.federalTaxRate),
            Map.entry("rank", s -> s.country.statecraft.rank),
            Map.entry("petition", s -> s.country.petition.size()),
            Map.entry("villager_mayors", s -> s.country.villagerMayors.size()),
            Map.entry("country_age", Scope::countryAge),
            Map.entry("city_unrest", s -> s.cityStats() == null ? 0 : s.cityStats().unrestValue()),
            Map.entry("city_development", s -> s.cityStats() == null ? 0 : s.cityStats().development()),
            Map.entry("city_villagers", s -> s.cityStats() == null ? 0 : s.cityStats().villagers()),
            Map.entry("city_chunks", s -> s.cityStats() == null ? 0 : s.cityStats().chunks()));

    /** How a value is shown in the explanations. */
    static String show(String name, int value) {
        return switch (name) {
            case "stability" -> Realm.signed(value);
            case "rank" -> Realm.rankKey(value);
            case "treasury", "debt" -> value + "$";
            case "overextension", "federal_tax" -> value + "%";
            default -> String.valueOf(value);
        };
    }

    // ── Parsing ──────────────────────────────────────────────────────────

    static Condition parse(JsonElement json) {
        if (json == null || json.isJsonNull()) return ALWAYS;
        if (json.isJsonArray()) return new All(parseList(json.getAsJsonArray()), false);
        if (!json.isJsonObject()) throw new JsonParseException("A condition must be an object or an array");
        JsonObject o = json.getAsJsonObject();
        String type = string(o, "type");
        boolean hidden = o.has("hidden") && o.get("hidden").getAsBoolean();
        if (VALUES.containsKey(type)) {
            Integer min = o.has("min") ? o.get("min").getAsInt() : null;
            Integer max = o.has("max") ? o.get("max").getAsInt() : null;
            if (o.has("value")) min = max = o.get("value").getAsInt();
            if (min == null && max == null) throw new JsonParseException("'" + type + "' needs min, max or value");
            return new Numeric(type, VALUES.get(type), min, max, hidden || type.equals("country_age"));
        }
        boolean want = !o.has("value") || o.get("value").getAsBoolean();
        return switch (type) {
            case "all" -> new All(parseList(o.getAsJsonArray("conditions")), hidden);
            case "any" -> new Any(parseList(o.getAsJsonArray("conditions")), hidden);
            case "not" -> new Not(parseList(o.getAsJsonArray("conditions")), hidden);
            case "law" -> {
                Law law = Law.byKey(string(o, "law"));
                if (law == null) throw new JsonParseException("Unknown law " + o.get("law"));
                yield new Flagged(s -> s.country.hasLaw(law) == want, hidden, want ? "statecraft.politicsmod.need.law"
                        : "statecraft.politicsmod.need.no_law", NewsEntry.TRANSLATE_PREFIX + law.nameKey());
            }
            case "flag" -> {
                String flag = string(o, "flag");
                yield new Flagged(s -> s.country.statecraft.hasFlag(flag, s.cycle()) == want, true, "", flag);
            }
            case "bankrupt" -> new Flagged(s -> s.country.isBankrupt() == want, hidden,
                    want ? "statecraft.politicsmod.need.bankrupt" : "statecraft.politicsmod.need.not_bankrupt");
            case "goal_done" -> {
                String goal = Statecraft.id(string(o, "goal"));
                yield new Flagged(s -> s.country.statecraft.goalsDone.containsKey(goal) == want, hidden,
                        want ? "statecraft.politicsmod.need.goal" : "statecraft.politicsmod.need.no_goal",
                        NewsEntry.TRANSLATE_PREFIX + "goal." + Statecraft.namespaced(goal) + ".title");
            }
            case "decision_taken" -> {
                String decision = Statecraft.id(string(o, "decision"));
                yield new Flagged(s -> s.country.statecraft.decisions.containsKey(decision) == want, hidden,
                        want ? "statecraft.politicsmod.need.decision" : "statecraft.politicsmod.need.no_decision",
                        NewsEntry.TRANSLATE_PREFIX + "decision." + Statecraft.namespaced(decision) + ".title");
            }
            case "is_capital" -> new Flagged(s -> s.targetCity() != null && s.targetCity().equals(s.country.capital) == want,
                    hidden, want ? "statecraft.politicsmod.need.capital" : "statecraft.politicsmod.need.not_capital");
            case "villager_mayor" -> new Flagged(s -> s.targetCity() != null
                    && s.country.villagerMayors.containsKey(s.targetCity()) == want, hidden,
                    want ? "statecraft.politicsmod.need.villager_mayor" : "statecraft.politicsmod.need.no_villager_mayor");
            case "election" -> new Flagged(s -> (s.country.election != null) == want, hidden,
                    want ? "statecraft.politicsmod.need.election" : "statecraft.politicsmod.need.no_election");
            case "election_ready" -> new Flagged(s -> (s.country.election == null && ElectionService.cooldownLeft(s.country) == 0) == want,
                    true, "");
            default -> throw new JsonParseException("Unknown condition type '" + type + "'");
        };
    }

    private static List<Condition> parseList(JsonArray array) {
        if (array == null) throw new JsonParseException("'conditions' must be a list");
        List<Condition> list = new ArrayList<>();
        for (JsonElement e : array) list.add(parse(e));
        return list;
    }

    private static String string(JsonObject o, String key) {
        if (!o.has(key)) throw new JsonParseException("Missing '" + key + "'");
        return o.get(key).getAsString();
    }

    // ── Kinds ────────────────────────────────────────────────────────────

    record Numeric(String name, ToIntFunction<Scope> value, Integer min, Integer max, boolean hidden) implements Condition {
        @Override
        public boolean test(Scope s) {
            int v = value.applyAsInt(s);
            return (min == null || v >= min) && (max == null || v <= max);
        }

        @Override
        public void describe(Scope s, List<Line> out, int indent) {
            if (hidden) return;
            int v = value.applyAsInt(s);
            String label = NewsEntry.TRANSLATE_PREFIX + "statecraft.politicsmod.value." + name;
            int state = test(s) ? Line.MET : Line.UNMET;
            Line line;
            if (min != null && max != null && min.equals(max)) {
                line = Line.of("statecraft.politicsmod.need.exactly", state, label, show(name, min), show(name, v));
            } else if (min != null && max != null) {
                line = Line.of("statecraft.politicsmod.need.between", state, label, show(name, min), show(name, max), show(name, v));
            } else if (min != null) {
                line = Line.of("statecraft.politicsmod.need.at_least", state, label, show(name, min), show(name, v));
            } else {
                line = Line.of("statecraft.politicsmod.need.at_most", state, label, show(name, max), show(name, v));
            }
            out.add(line.indented(indent));
        }

        @Override
        public float progress(Scope s) {
            if (test(s)) return 1f;
            int v = value.applyAsInt(s);
            if (min != null && v < min && min > 0) return Math.max(0f, Math.min(1f, v / (float) min));
            return 0f;
        }
    }

    record Flagged(java.util.function.Predicate<Scope> check, boolean hidden, String key, String... args) implements Condition {
        @Override
        public boolean test(Scope s) {
            return check.test(s);
        }

        @Override
        public void describe(Scope s, List<Line> out, int indent) {
            if (hidden || key.isEmpty()) return;
            out.add(Line.of(key, test(s) ? Line.MET : Line.UNMET, args).indented(indent));
        }
    }

    record All(List<Condition> list, boolean hidden) implements Condition {
        @Override
        public boolean test(Scope s) {
            for (Condition c : list) if (!c.test(s)) return false;
            return true;
        }

        @Override
        public void describe(Scope s, List<Line> out, int indent) {
            if (hidden) return;
            for (Condition c : list) c.describe(s, out, indent);
        }

        @Override
        public float progress(Scope s) {
            if (list.isEmpty()) return 1f;
            float sum = 0;
            for (Condition c : list) sum += c.progress(s);
            return sum / list.size();
        }
    }

    record Any(List<Condition> list, boolean hidden) implements Condition {
        @Override
        public boolean test(Scope s) {
            for (Condition c : list) if (c.test(s)) return true;
            return list.isEmpty();
        }

        @Override
        public void describe(Scope s, List<Line> out, int indent) {
            if (hidden) return;
            out.add(Line.of("statecraft.politicsmod.need.any", test(s) ? Line.MET : Line.UNMET).indented(indent));
            for (Condition c : list) c.describe(s, out, indent + 1);
        }

        @Override
        public float progress(Scope s) {
            float best = list.isEmpty() ? 1f : 0f;
            for (Condition c : list) best = Math.max(best, c.progress(s));
            return best;
        }
    }

    record Not(List<Condition> list, boolean hidden) implements Condition {
        @Override
        public boolean test(Scope s) {
            for (Condition c : list) if (c.test(s)) return false;
            return true;
        }

        @Override
        public void describe(Scope s, List<Line> out, int indent) {
            if (hidden) return;
            out.add(Line.of("statecraft.politicsmod.need.none", test(s) ? Line.MET : Line.UNMET).indented(indent));
            List<Line> inner = new ArrayList<>();
            for (Condition c : list) c.describe(s, inner, indent + 1);
            // Inside "none of" a line that holds is the bad case: swap the marks
            for (Line l : inner) {
                int st = l.state() == Line.MET ? Line.UNMET : l.state() == Line.UNMET ? Line.MET : l.state();
                out.add(new Line(l.key(), l.args(), st, l.indent()));
            }
        }
    }
}
