package net.krona.politicsmod.statecraft;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import dev.architectury.registry.ReloadListenerRegistry;
import net.krona.politicsmod.Politicsmod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Events, decisions and goals, loaded from datapacks:
 * data/&lt;namespace&gt;/politicsmod/events/*.json, .../decisions/*.json, .../goals/*.json.
 * The id is "namespace:path". Texts are translation keys; by default "event.&lt;namespace&gt;.&lt;path&gt;.title" and so on,
 * a datapack may give its own keys or plain text instead.
 */
public final class Statecraft {

    private Statecraft() {
    }

    // ── Content ──────────────────────────────────────────────────────────

    public record Option(String text, Condition allow, List<Effect> effects) {
    }

    /**
     * @param mtth    mean time to happen, in economy cycles, while the conditions hold
     * @param city    the event happens in one city that passes cityCondition; texts get it as %2$s
     * @param timeout cycles the leader has to answer before defaultOption happens; 0 = PoliticsConfig.eventTimeoutCycles
     */
    public record StateEvent(String id, String title, String desc, Condition trigger, boolean city, Condition cityCondition,
                             int mtth, int cooldown, boolean once, int timeout, int defaultOption, List<Option> options) {
    }

    /** target = the leader picks one of the country's cities (passing cityCondition) when taking it. */
    public record Decision(String id, String title, String desc, int order, Condition potential, Condition allow, int cost,
                           boolean target, Condition cityCondition, boolean once, int cooldown, List<Effect> effects) {
    }

    /** A goal starts when start holds once and is reached when complete holds; then reward happens. */
    public record Goal(String id, String title, String desc, int order, Condition start, Condition complete,
                       List<Effect> reward) {
    }

    private static Map<String, StateEvent> events = Map.of();
    private static Map<String, Decision> decisions = Map.of();
    private static Map<String, Goal> goals = Map.of();

    public static Map<String, StateEvent> events() {
        return events;
    }

    public static List<Decision> decisions() {
        List<Decision> list = new ArrayList<>(decisions.values());
        list.sort(Comparator.comparingInt(Decision::order).thenComparing(Decision::id));
        return list;
    }

    public static Decision decision(String id) {
        return decisions.get(id(id));
    }

    public static List<Goal> goals() {
        List<Goal> list = new ArrayList<>(goals.values());
        list.sort(Comparator.comparingInt(Goal::order).thenComparing(Goal::id));
        return list;
    }

    public static Goal goal(String id) {
        return goals.get(id(id));
    }

    public static StateEvent event(String id) {
        return events.get(id(id));
    }

    /** "found_city" -> "politicsmod:found_city"; ids that already have a namespace stay as they are. */
    public static String id(String raw) {
        return raw.contains(":") ? raw : Politicsmod.MODID + ":" + raw;
    }

    /** "politicsmod:found_city" -> "politicsmod.found_city", for translation keys. */
    public static String namespaced(String id) {
        return id(id).replace(':', '.').replace('/', '.');
    }

    // ── Loading ──────────────────────────────────────────────────────────

    public static void register() {
        ReloadListenerRegistry.register(PackType.SERVER_DATA, new Loader("politicsmod/events", Kind.EVENT),
                ResourceLocation.fromNamespaceAndPath(Politicsmod.MODID, "events"));
        ReloadListenerRegistry.register(PackType.SERVER_DATA, new Loader("politicsmod/decisions", Kind.DECISION),
                ResourceLocation.fromNamespaceAndPath(Politicsmod.MODID, "decisions"));
        ReloadListenerRegistry.register(PackType.SERVER_DATA, new Loader("politicsmod/goals", Kind.GOAL),
                ResourceLocation.fromNamespaceAndPath(Politicsmod.MODID, "goals"));
    }

    private enum Kind { EVENT, DECISION, GOAL }

    private static final class Loader extends SimpleJsonResourceReloadListener {
        private final Kind kind;

        Loader(String directory, Kind kind) {
            super(new Gson(), directory);
            this.kind = kind;
        }

        @Override
        protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
            Map<String, Object> loaded = new LinkedHashMap<>();
            for (Map.Entry<ResourceLocation, JsonElement> e : files.entrySet()) {
                String id = e.getKey().toString();
                try {
                    JsonObject o = e.getValue().getAsJsonObject();
                    if (o.has("enabled") && !o.get("enabled").getAsBoolean()) continue;
                    loaded.put(id, switch (kind) {
                        case EVENT -> parseEvent(id, o);
                        case DECISION -> parseDecision(id, o);
                        case GOAL -> parseGoal(id, o);
                    });
                } catch (RuntimeException ex) {
                    Politicsmod.LOGGER.error("Skipping {} {}: {}", kind.name().toLowerCase(java.util.Locale.ROOT), id, ex.getMessage());
                }
            }
            switch (kind) {
                case EVENT -> events = Collections.unmodifiableMap(cast(loaded));
                case DECISION -> decisions = Collections.unmodifiableMap(cast(loaded));
                case GOAL -> goals = Collections.unmodifiableMap(cast(loaded));
            }
            Politicsmod.LOGGER.info("Loaded {} {}s", loaded.size(), kind.name().toLowerCase(java.util.Locale.ROOT));
        }

        @SuppressWarnings("unchecked")
        private static <T> Map<String, T> cast(Map<String, Object> map) {
            Map<String, T> out = new LinkedHashMap<>();
            for (Map.Entry<String, Object> e : map.entrySet()) out.put(e.getKey(), (T) e.getValue());
            return out;
        }
    }

    private static String text(JsonObject o, String key, String fallback) {
        return o.has(key) ? o.get(key).getAsString() : fallback;
    }

    private static int number(JsonObject o, String key, int fallback) {
        return o.has(key) ? o.get(key).getAsInt() : fallback;
    }

    private static boolean flag(JsonObject o, String key, boolean fallback) {
        return o.has(key) ? o.get(key).getAsBoolean() : fallback;
    }

    static StateEvent parseEvent(String id, JsonObject o) {
        String base = "event." + namespaced(id);
        JsonArray opts = o.getAsJsonArray("options");
        if (opts == null || opts.isEmpty()) throw new JsonParseException("An event needs options");
        List<Option> options = new ArrayList<>();
        for (int i = 0; i < opts.size(); i++) {
            JsonObject oo = opts.get(i).getAsJsonObject();
            options.add(new Option(text(oo, "text", base + ".option." + (i + 1)), Condition.parse(oo.get("conditions")),
                    Effect.parseList(oo.get("effects"))));
        }
        int def = Math.max(0, Math.min(options.size() - 1, number(o, "default_option", options.size()) - 1));
        return new StateEvent(id, text(o, "title", base + ".title"), text(o, "description", base + ".desc"),
                Condition.parse(o.get("conditions")), flag(o, "city", false), Condition.parse(o.get("city_conditions")),
                Math.max(1, number(o, "mean_time", 60)), Math.max(0, number(o, "cooldown", 120)), flag(o, "once", false),
                Math.max(0, number(o, "timeout", 0)), def, options);
    }

    static Decision parseDecision(String id, JsonObject o) {
        String base = "decision." + namespaced(id);
        return new Decision(id, text(o, "title", base + ".title"), text(o, "description", base + ".desc"),
                number(o, "order", 100), Condition.parse(o.get("potential")), Condition.parse(o.get("conditions")),
                Math.max(0, number(o, "cost", 0)), flag(o, "target_city", false), Condition.parse(o.get("city_conditions")),
                flag(o, "once", false), Math.max(0, number(o, "cooldown", 0)), Effect.parseList(o.get("effects")));
    }

    static Goal parseGoal(String id, JsonObject o) {
        String base = "goal." + namespaced(id);
        return new Goal(id, text(o, "title", base + ".title"), text(o, "description", base + ".desc"),
                number(o, "order", 100), Condition.parse(o.get("start")), Condition.parse(o.get("conditions")),
                Effect.parseList(o.get("reward")));
    }
}
