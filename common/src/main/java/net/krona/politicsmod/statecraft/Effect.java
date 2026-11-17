package net.krona.politicsmod.statecraft;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import net.krona.politicsmod.PoliticsManager;
import net.krona.politicsmod.config.PoliticsConfig;
import net.krona.politicsmod.politics.Election;
import net.krona.politicsmod.politics.ElectionService;
import net.krona.politicsmod.politics.NewsEntry;
import net.krona.politicsmod.realm.Breakdown;
import net.krona.politicsmod.realm.ModifierType;
import net.krona.politicsmod.realm.Realm;
import net.krona.politicsmod.realm.TimedModifier;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * What an event option, a decision or a goal reward does, read from a datapack. Every effect can explain itself
 * ("Treasury: +500$"); flags and news are silent.
 *
 * JSON: {"type": "treasury", "amount": 500}, {"type": "modifier", "modifier": "tax_income", "value": 10, "cycles": 40},
 * {"type": "development", "amount": 1}. Effects without a city of their own act on the event's or decision's city,
 * else on the capital.
 */
public interface Effect {

    /** source = translation key of what caused it (the event or decision title), names stored modifiers. */
    void apply(Scope s, String source);

    void describe(Scope s, List<Line> out);

    static List<Effect> parseList(JsonElement json) {
        List<Effect> list = new ArrayList<>();
        if (json == null || json.isJsonNull()) return list;
        if (!json.isJsonArray()) throw new JsonParseException("Effects must be a list");
        for (JsonElement e : (JsonArray) json) list.add(parse(e.getAsJsonObject()));
        return list;
    }

    static void applyAll(List<Effect> effects, Scope s, String source) {
        for (Effect e : effects) {
            e.apply(s, source);
            s.invalidate();
        }
        s.mgr.setDirty();
    }

    static List<Line> describeAll(List<Effect> effects, Scope s) {
        List<Line> out = new ArrayList<>();
        for (Effect e : effects) e.describe(s, out);
        return out;
    }

    /** Minutes of game time a number of economy cycles lasts, for the explanations. */
    static int minutes(int cycles) {
        return Math.max(1, Math.round(cycles * PoliticsConfig.get().economyCycleTicks / 1200f));
    }

    /** "the chosen city" when a decision has not been taken yet, otherwise the city's name. */
    static String cityArg(Scope s) {
        String city = s.targetCity();
        return city == null ? NewsEntry.TRANSLATE_PREFIX + "statecraft.politicsmod.no_city" : city;
    }

    private static int intOf(JsonObject o, String key) {
        if (!o.has(key)) throw new JsonParseException("Missing '" + key + "'");
        return o.get(key).getAsInt();
    }

    static Effect parse(JsonObject o) {
        if (!o.has("type")) throw new JsonParseException("An effect needs a 'type'");
        String type = o.get("type").getAsString();
        return switch (type) {
            case "treasury" -> {
                int amount = o.has("amount") ? o.get("amount").getAsInt() : 0;
                int percent = o.has("percent") ? o.get("percent").getAsInt() : 0;
                yield new Effect() {
                    @Override
                    public void apply(Scope s, String source) {
                        int delta = amount + (int) ((long) Math.max(0, s.country.balance) * percent / 100);
                        if (delta >= 0) s.country.balance += delta;
                        else PoliticsManager.charge(s.country, -delta);
                    }

                    @Override
                    public void describe(Scope s, List<Line> out) {
                        if (amount != 0) out.add(Line.of("statecraft.politicsmod.effect.treasury", amount > 0 ? Line.GOOD : Line.BAD,
                                Breakdown.format(amount, 1)));
                        if (percent != 0) out.add(Line.of("statecraft.politicsmod.effect.treasury_percent",
                                percent > 0 ? Line.GOOD : Line.BAD, Breakdown.format(percent, 1)));
                    }
                };
            }
            case "city_treasury" -> {
                int amount = intOf(o, "amount");
                yield new Effect() {
                    @Override
                    public void apply(Scope s, String source) {
                        String city = s.targetCity();
                        if (city != null) s.country.cityBalances.merge(city, amount, (a, b) -> Math.max(0, a + b));
                    }

                    @Override
                    public void describe(Scope s, List<Line> out) {
                        out.add(Line.of("statecraft.politicsmod.effect.city_treasury", amount > 0 ? Line.GOOD : Line.BAD,
                                cityArg(s), Breakdown.format(amount, 1)));
                    }
                };
            }
            case "stability" -> {
                int amount = intOf(o, "amount");
                yield new Effect() {
                    @Override
                    public void apply(Scope s, String source) {
                        Realm.shock(s.mgr, s.country, amount);
                    }

                    @Override
                    public void describe(Scope s, List<Line> out) {
                        if (Realm.enabled()) out.add(Line.of("statecraft.politicsmod.effect.stability",
                                amount > 0 ? Line.GOOD : Line.BAD, Breakdown.format(amount, 1)));
                    }
                };
            }
            case "legitimacy" -> {
                int amount = intOf(o, "amount");
                yield new Effect() {
                    @Override
                    public void apply(Scope s, String source) {
                        if (Realm.enabled()) s.country.legitimacy = Math.max(0, Math.min(100, s.country.legitimacy + amount));
                    }

                    @Override
                    public void describe(Scope s, List<Line> out) {
                        if (Realm.enabled()) out.add(Line.of("statecraft.politicsmod.effect.legitimacy",
                                amount > 0 ? Line.GOOD : Line.BAD, Breakdown.format(amount, 1)));
                    }
                };
            }
            case "prestige" -> {
                int amount = intOf(o, "amount");
                yield new Effect() {
                    @Override
                    public void apply(Scope s, String source) {
                        Realm.addPrestige(s.country, amount);
                    }

                    @Override
                    public void describe(Scope s, List<Line> out) {
                        if (Realm.enabled()) out.add(Line.of("statecraft.politicsmod.effect.prestige",
                                amount > 0 ? Line.GOOD : Line.BAD, Breakdown.format(amount, 1)));
                    }
                };
            }
            case "modifier", "city_modifier" -> {
                boolean city = type.equals("city_modifier");
                ModifierType mod = ModifierType.byKey(o.has("modifier") ? o.get("modifier").getAsString() : "");
                if (mod == null) throw new JsonParseException("Unknown modifier " + o.get("modifier"));
                if (city && mod != ModifierType.UNREST && mod != ModifierType.TAX_INCOME) {
                    throw new JsonParseException("A city modifier is unrest or tax_income");
                }
                int value = intOf(o, "value");
                int cycles = o.has("cycles") ? o.get("cycles").getAsInt() : 0;
                // Lower unrest, upkeep and land prices are good for the country
                boolean good = (value > 0) == (mod != ModifierType.UNREST && mod != ModifierType.UPKEEP && mod != ModifierType.LAND_PRICE);
                yield new Effect() {
                    @Override
                    public void apply(Scope s, String source) {
                        long expires = cycles <= 0 ? 0 : System.currentTimeMillis()
                                + cycles * (long) PoliticsConfig.get().economyCycleTicks * 50L;
                        TimedModifier m = new TimedModifier("sc-" + UUID.randomUUID().toString().substring(0, 8),
                                NewsEntry.TRANSLATE_PREFIX + source, mod, value, expires);
                        if (!city) {
                            s.country.modifiers.add(m);
                        } else {
                            String target = s.targetCity();
                            if (target != null) s.country.cityModifiers.computeIfAbsent(target, k -> new ArrayList<>()).add(m);
                        }
                    }

                    @Override
                    public void describe(Scope s, List<Line> out) {
                        String unit = mod == ModifierType.UNREST ? "" : "%";
                        String name = NewsEntry.TRANSLATE_PREFIX + mod.nameKey();
                        String v = Breakdown.format(value, 1) + unit;
                        int state = good ? Line.GOOD : Line.BAD;
                        if (city) {
                            out.add(cycles > 0 ? Line.of("statecraft.politicsmod.effect.city_modifier", state, name, cityArg(s), v,
                                    String.valueOf(minutes(cycles)))
                                    : Line.of("statecraft.politicsmod.effect.city_modifier_forever", state, name, cityArg(s), v));
                        } else {
                            out.add(cycles > 0 ? Line.of("statecraft.politicsmod.effect.modifier", state, name, v,
                                    String.valueOf(minutes(cycles)))
                                    : Line.of("statecraft.politicsmod.effect.modifier_forever", state, name, v));
                        }
                    }
                };
            }
            case "development" -> {
                int amount = intOf(o, "amount");
                yield new Effect() {
                    @Override
                    public void apply(Scope s, String source) {
                        String city = s.targetCity();
                        if (city == null) return;
                        int max = PoliticsConfig.get().maxDevelopment;
                        int level = Math.max(0, Math.min(max, s.country.cityDevelopment.getOrDefault(city, 0) + amount));
                        s.country.cityDevelopment.put(city, level);
                    }

                    @Override
                    public void describe(Scope s, List<Line> out) {
                        if (Realm.enabled()) out.add(Line.of("statecraft.politicsmod.effect.development",
                                amount > 0 ? Line.GOOD : Line.BAD, cityArg(s), Breakdown.format(amount, 1)));
                    }
                };
            }
            case "wallets" -> {
                int amount = intOf(o, "amount");
                yield new Effect() {
                    @Override
                    public void apply(Scope s, String source) {
                        for (UUID member : s.country.getMembers().keySet()) {
                            net.krona.politicsmod.economy.WalletService.stateGrant(s.mgr, member, amount, s.country.getName());
                        }
                    }

                    @Override
                    public void describe(Scope s, List<Line> out) {
                        out.add(Line.of("statecraft.politicsmod.effect.wallets", amount > 0 ? Line.GOOD : Line.BAD,
                                Breakdown.format(amount, 1)));
                    }
                };
            }
            case "rank" -> {
                int rank = Math.max(0, Math.min(2, intOf(o, "rank")));
                yield new Effect() {
                    @Override
                    public void apply(Scope s, String source) {
                        if (s.country.statecraft.rank == rank) return;
                        s.country.statecraft.rank = rank;
                        s.mgr.postNews("news.politicsmod.rank", List.of(s.country.getName()), s.country.getName(), Realm.rankKey(rank));
                    }

                    @Override
                    public void describe(Scope s, List<Line> out) {
                        out.add(Line.of("statecraft.politicsmod.effect.rank", Line.GOOD, Realm.rankKey(rank)));
                    }
                };
            }
            case "capital" -> new Effect() {
                @Override
                public void apply(Scope s, String source) {
                    if (s.city == null || !s.country.cities.contains(s.city)) return;
                    s.mgr.setCapital(s.country.getName(), s.city);
                    s.mgr.postNews("news.politicsmod.capital_moved", List.of(s.country.getName()), s.country.getName(), s.city);
                }

                @Override
                public void describe(Scope s, List<Line> out) {
                    out.add(Line.of("statecraft.politicsmod.effect.capital", Line.NEUTRAL,
                            s.city == null ? NewsEntry.TRANSLATE_PREFIX + "statecraft.politicsmod.chosen_city" : s.city));
                }
            };
            case "election" -> new Effect() {
                @Override
                public void apply(Scope s, String source) {
                    if (s.country.election == null) ElectionService.start(s.mgr, s.country, Election.Type.LEADER, "");
                }

                @Override
                public void describe(Scope s, List<Line> out) {
                    out.add(Line.of("statecraft.politicsmod.effect.election", Line.NEUTRAL));
                }
            };
            case "set_flag", "clear_flag" -> {
                boolean set = type.equals("set_flag");
                String flag = o.has("flag") ? o.get("flag").getAsString() : "";
                if (flag.isEmpty()) throw new JsonParseException("A flag effect needs 'flag'");
                int cycles = o.has("cycles") ? o.get("cycles").getAsInt() : 0;
                yield new Effect() {
                    @Override
                    public void apply(Scope s, String source) {
                        if (set) s.country.statecraft.flags.put(flag, cycles <= 0 ? 0L : s.cycle() + cycles);
                        else s.country.statecraft.flags.remove(flag);
                    }

                    @Override
                    public void describe(Scope s, List<Line> out) {
                    }
                };
            }
            case "news" -> {
                String key = o.has("key") ? o.get("key").getAsString() : "";
                yield new Effect() {
                    @Override
                    public void apply(Scope s, String source) {
                        String city = s.targetCity();
                        s.mgr.postNews(key, List.of(s.country.getName()), s.country.getName(), city == null ? "" : city);
                    }

                    @Override
                    public void describe(Scope s, List<Line> out) {
                    }
                };
            }
            default -> throw new JsonParseException("Unknown effect type '" + type + "'");
        };
    }

}
