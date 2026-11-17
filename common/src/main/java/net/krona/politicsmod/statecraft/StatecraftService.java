package net.krona.politicsmod.statecraft;

import dev.architectury.utils.GameInstance;
import net.krona.politicsmod.PoliticsManager;
import net.krona.politicsmod.config.PoliticsConfig;
import net.krona.politicsmod.politics.Country;
import net.krona.politicsmod.politics.CountryRole;
import net.krona.politicsmod.politics.NewsEntry;
import net.krona.politicsmod.politics.Notify;
import net.krona.politicsmod.realm.Realm;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * Runs events, decisions and goals: once per economy cycle events may fire, unanswered ones time out and goals are
 * checked; players answer events and take decisions through commands (the dashboard runs the same commands).
 */
public final class StatecraftService {

    private static final Random RANDOM = new Random();

    private StatecraftService() {
    }

    public static boolean enabled() {
        return PoliticsConfig.get().statecraftEnabled;
    }

    /** Text of a datapack field: '#'-prefixed so it is translated wherever it is shown. */
    public static String tr(String key) {
        return NewsEntry.TRANSLATE_PREFIX + key;
    }

    // ── The cycle ────────────────────────────────────────────────────────

    public static void cycle(PoliticsManager mgr) {
        if (!enabled()) return;
        long cycle = mgr.getCycleCount();
        for (Country c : new ArrayList<>(mgr.getCountries().values())) {
            CountryStatecraft st = c.statecraft;
            st.flags.entrySet().removeIf(e -> e.getValue() != 0 && e.getValue() <= cycle);

            // Unanswered events: their default option happens
            for (CountryStatecraft.Pending p : new ArrayList<>(st.pending)) {
                Statecraft.StateEvent ev = Statecraft.event(p.id());
                if (ev == null) st.pending.remove(p);
                else if (cycle >= p.expiresCycle()) resolve(mgr, c, p, ev, ev.defaultOption(), true, null);
            }
            checkGoals(mgr, c);
            maybeFire(mgr, c);
        }
        mgr.setDirty();
    }

    private static boolean memberOnline(Country c) {
        MinecraftServer server = GameInstance.getServer();
        if (server == null) return false;
        for (UUID id : c.getMembers().keySet()) if (server.getPlayerList().getPlayer(id) != null) return true;
        return false;
    }

    private static void maybeFire(PoliticsManager mgr, Country c) {
        PoliticsConfig cfg = PoliticsConfig.get();
        CountryStatecraft st = c.statecraft;
        long cycle = mgr.getCycleCount();
        if (st.pending.size() >= cfg.maxPendingEvents || cycle - st.lastEventCycle < cfg.eventMinGapCycles) return;
        if (cfg.eventsNeedOnlineMember && !memberOnline(c)) return;

        List<Statecraft.StateEvent> candidates = new ArrayList<>(Statecraft.events().values());
        Collections.shuffle(candidates, RANDOM);
        Scope scope = new Scope(mgr, c, null);
        for (Statecraft.StateEvent ev : candidates) {
            if (!available(ev, st, cycle)) continue;
            double chance = cfg.eventFrequencyPercent / 100.0 / ev.mtth();
            if (RANDOM.nextDouble() >= chance) continue;
            if (!ev.trigger().test(scope)) continue;
            String city = "";
            if (ev.city()) {
                List<String> fitting = new ArrayList<>();
                for (String name : c.cities) if (ev.cityCondition().test(scope.withCity(name))) fitting.add(name);
                if (fitting.isEmpty()) continue;
                city = fitting.get(RANDOM.nextInt(fitting.size()));
            }
            fire(mgr, c, ev, city);
            return;
        }
    }

    private static boolean available(Statecraft.StateEvent ev, CountryStatecraft st, long cycle) {
        Long last = st.lastFired.get(ev.id());
        if (last != null && (ev.once() || cycle - last < ev.cooldown())) return false;
        for (CountryStatecraft.Pending p : st.pending) if (p.id().equals(ev.id())) return false;
        return true;
    }

    /** Starts an event for a country; also used by the operator command. */
    public static void fire(PoliticsManager mgr, Country c, Statecraft.StateEvent ev, String city) {
        long cycle = mgr.getCycleCount();
        int timeout = ev.timeout() > 0 ? ev.timeout() : PoliticsConfig.get().eventTimeoutCycles;
        CountryStatecraft st = c.statecraft;
        st.pending.add(new CountryStatecraft.Pending(ev.id(), city == null ? "" : city, cycle, cycle + timeout));
        st.lastFired.put(ev.id(), cycle);
        st.lastEventCycle = cycle;
        mgr.setDirty();
        Notify.members(c, eventLine("message.politicsmod.event.fired", c, ev, city));
        notice(c, ev, city, null);
    }

    /** "★ <title> — the ruler must decide. [Open]" with a click that lists the events. */
    private static MutableComponent eventLine(String key, Country c, Statecraft.StateEvent ev, String city) {
        MutableComponent msg = Component.translatable(key,
                Component.translatable(ev.title(), c.getName(), city == null ? "" : city).withStyle(ChatFormatting.WHITE))
                .withStyle(ChatFormatting.GOLD);
        msg.append(" ").append(Component.translatable("message.politicsmod.event.open").withStyle(style -> style
                .withColor(ChatFormatting.AQUA).withUnderlined(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/politicsmod event list"))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.translatable("message.politicsmod.event.open_hint")))));
        return msg;
    }

    /** A toast with a bell for the online members (only == one player, for a reminder on joining). */
    private static void notice(Country c, Statecraft.StateEvent ev, String city, ServerPlayer only) {
        net.krona.politicsmod.network.EventNoticePayload payload = new net.krona.politicsmod.network.EventNoticePayload(
                ev.title(), c.getName(), city == null ? "" : city, c.statecraft.pending.size());
        if (only != null) {
            net.krona.politicsmod.network.ModNetworking.toPlayer(only, payload);
            return;
        }
        MinecraftServer server = GameInstance.getServer();
        if (server == null) return;
        for (UUID id : c.getMembers().keySet()) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p != null) net.krona.politicsmod.network.ModNetworking.toPlayer(p, payload);
        }
    }

    /** On joining: events that came while the player was away, in chat and as a toast. */
    public static void remind(PoliticsManager mgr, ServerPlayer player) {
        if (!enabled()) return;
        String name = mgr.getPlayerCountry(player.getUUID());
        Country c = name == null ? null : mgr.getCountry(name);
        if (c == null || c.statecraft.pending.isEmpty()) return;
        Statecraft.StateEvent first = null;
        String firstCity = "";
        for (CountryStatecraft.Pending p : c.statecraft.pending) {
            Statecraft.StateEvent ev = Statecraft.event(p.id());
            if (ev == null) continue;
            player.sendSystemMessage(eventLine("message.politicsmod.event.reminder", c, ev, p.city()));
            if (first == null) {
                first = ev;
                firstCity = p.city();
            }
        }
        if (first != null) notice(c, first, firstCity, player);
    }

    /** Applies an option and records the outcome; by = who chose it, null when time ran out. */
    private static void resolve(PoliticsManager mgr, Country c, CountryStatecraft.Pending p, Statecraft.StateEvent ev,
                                int option, boolean timedOut, String by) {
        Scope s = new Scope(mgr, c, c.cities.contains(p.city()) ? p.city() : null);
        Statecraft.Option opt = ev.options().get(option);
        c.statecraft.pending.remove(p);
        Effect.applyAll(opt.effects(), s, ev.title());
        c.statecraft.remember(new CountryStatecraft.Answered(ev.id(), p.city(), option, timedOut, mgr.getCycleCount()));
        mgr.postNews(timedOut ? "news.politicsmod.event_timeout" : "news.politicsmod.event_answered", List.of(c.getName()),
                c.getName(), tr(ev.title()), tr(opt.text()));
        Component title = Component.translatable(ev.title(), c.getName(), p.city());
        Component choice = Component.translatable(opt.text(), c.getName(), p.city());
        Notify.members(c, timedOut
                ? Component.translatable("message.politicsmod.event.timed_out", title, choice).withStyle(ChatFormatting.YELLOW)
                : Component.translatable("message.politicsmod.event.answered", by, title, choice).withStyle(ChatFormatting.GREEN));
        mgr.setDirty();
    }

    private static void checkGoals(PoliticsManager mgr, Country c) {
        CountryStatecraft st = c.statecraft;
        Scope s = new Scope(mgr, c, null);
        for (Statecraft.Goal g : Statecraft.goals()) {
            if (st.goalsDone.containsKey(g.id())) continue;
            if (!st.goalsActive.contains(g.id())) {
                if (!g.start().test(s)) continue;
                st.goalsActive.add(g.id());
            }
            if (!g.complete().test(s)) continue;
            st.goalsActive.remove(g.id());
            st.goalsDone.put(g.id(), mgr.getCycleCount());
            Effect.applyAll(g.reward(), s, g.title());
            mgr.postNews("news.politicsmod.goal_reached", List.of(c.getName()), c.getName(), tr(g.title()));
            Notify.members(c, Component.translatable("message.politicsmod.goal.reached",
                    Component.translatable(g.title()).withStyle(ChatFormatting.WHITE)).withStyle(ChatFormatting.GOLD));
        }
    }

    // ── Player actions ───────────────────────────────────────────────────

    /** The leader answers; while the leader is offline a mayor may answer as regent. */
    public static boolean mayAnswer(Country c, UUID player) {
        CountryRole role = c.getRole(player);
        if (role == CountryRole.LEADER) return true;
        if (role != CountryRole.MAYOR) return false;
        MinecraftServer server = GameInstance.getServer();
        UUID leader = c.getLeader();
        return server != null && (leader == null || server.getPlayerList().getPlayer(leader) == null);
    }

    public static Realm.Result answer(PoliticsManager mgr, ServerPlayer player, Country c, String eventId, int option) {
        if (!enabled()) return error("message.politicsmod.statecraft.disabled");
        CountryStatecraft.Pending pending = null;
        for (CountryStatecraft.Pending p : c.statecraft.pending) if (p.id().equals(Statecraft.id(eventId))) pending = p;
        if (pending == null) return error("message.politicsmod.event.not_pending");
        if (!mayAnswer(c, player.getUUID())) return error("message.politicsmod.event.not_allowed");
        Statecraft.StateEvent ev = Statecraft.event(pending.id());
        if (ev == null) return error("message.politicsmod.event.not_pending");
        if (option < 1 || option > ev.options().size()) return error("message.politicsmod.event.bad_option", ev.options().size());
        Statecraft.Option opt = ev.options().get(option - 1);
        Scope s = new Scope(mgr, c, c.cities.contains(pending.city()) ? pending.city() : null);
        if (!opt.allow().test(s)) return error("message.politicsmod.event.option_locked");
        resolve(mgr, c, pending, ev, option - 1, false, player.getGameProfile().getName());
        return new Realm.Result(true, Component.empty());
    }

    /** Cycles until a decision may be taken again; 0 when it may. */
    public static long cooldownLeft(Country c, Statecraft.Decision d, long cycle) {
        Long last = c.statecraft.decisions.get(d.id());
        if (last == null || d.cooldown() <= 0) return 0;
        return Math.max(0, last + d.cooldown() - cycle);
    }

    public static Realm.Result take(PoliticsManager mgr, ServerPlayer player, Country c, String id, String city) {
        if (!enabled()) return error("message.politicsmod.statecraft.disabled");
        if (c.getRole(player.getUUID()) != CountryRole.LEADER) return error("message.politicsmod.decision.leader_only");
        Statecraft.Decision d = Statecraft.decision(id);
        if (d == null) return error("message.politicsmod.decision.unknown", id);
        Scope s = new Scope(mgr, c, null);
        if (!d.potential().test(s)) return error("message.politicsmod.decision.not_available");
        if (d.once() && c.statecraft.decisions.containsKey(d.id())) return error("message.politicsmod.decision.already_taken");
        long cd = cooldownLeft(c, d, mgr.getCycleCount());
        if (cd > 0) return error("message.politicsmod.decision.cooldown", Effect.minutes((int) cd));
        if (d.target()) {
            if (city == null || !c.cities.contains(city)) return error("message.politicsmod.decision.pick_city");
            s = s.withCity(city);
            if (!d.cityCondition().test(s)) return error("message.politicsmod.decision.city_unfit", city);
        }
        if (!d.allow().test(s)) return error("message.politicsmod.decision.requirements");
        if (c.balance < d.cost()) return error("message.politicsmod.realm.no_funds", d.cost());
        c.balance -= d.cost();
        c.statecraft.decisions.put(d.id(), mgr.getCycleCount());
        Effect.applyAll(d.effects(), s, d.title());
        mgr.postNews("news.politicsmod.decision_taken", List.of(c.getName()), c.getName(), tr(d.title()));
        Notify.members(c, Component.translatable("message.politicsmod.decision.taken", player.getGameProfile().getName(),
                Component.translatable(d.title()).withStyle(ChatFormatting.WHITE)).withStyle(ChatFormatting.GREEN));
        return new Realm.Result(true, Component.empty());
    }

    private static Realm.Result error(String key, Object... args) {
        return new Realm.Result(false, Component.translatable(key, args).withStyle(ChatFormatting.RED));
    }

    // ── What the dashboard shows ─────────────────────────────────────────

    public record OptionView(String text, boolean allowed, List<Line> effects) {
    }

    public record EventView(String id, String title, String desc, String city, int secondsLeft, List<OptionView> options) {
    }

    public record RecentView(String title, String option, String city, boolean timedOut) {
    }

    /** state: AVAILABLE, LOCKED (requirements), TAKEN (once), COOLDOWN, HIDDEN_CITY... see constants. */
    public record DecisionView(String id, String title, String desc, int cost, int state, int cooldownMinutes, boolean target,
                               List<String> cities, List<Line> requirements, List<Line> effects) {
    }

    public record GoalView(String id, String title, String desc, boolean done, int progress, List<Line> requirements,
                           List<Line> reward) {
    }

    public record View(boolean enabled, int rank, boolean canAnswer, boolean canDecide, List<EventView> events,
                       List<RecentView> recent, List<DecisionView> decisions, List<GoalView> goals) {
    }

    public static final int AVAILABLE = 0, LOCKED = 1, TAKEN = 2, COOLDOWN = 3;

    public static View view(PoliticsManager mgr, Country c, UUID viewer) {
        long cycle = mgr.getCycleCount();
        int cycleSeconds = PoliticsConfig.get().economyCycleTicks / 20;
        Scope base = new Scope(mgr, c, null);
        CountryStatecraft st = c.statecraft;

        List<EventView> events = new ArrayList<>();
        for (CountryStatecraft.Pending p : st.pending) {
            Statecraft.StateEvent ev = Statecraft.event(p.id());
            if (ev == null) continue;
            Scope s = base.withCity(c.cities.contains(p.city()) ? p.city() : null);
            List<OptionView> options = new ArrayList<>();
            for (Statecraft.Option o : ev.options()) {
                options.add(new OptionView(o.text(), o.allow().test(s), Effect.describeAll(o.effects(), s)));
            }
            int left = (int) Math.max(0, (p.expiresCycle() - cycle) * cycleSeconds);
            events.add(new EventView(ev.id(), ev.title(), ev.desc(), p.city(), left, options));
        }

        List<RecentView> recent = new ArrayList<>();
        for (int i = st.recent.size() - 1; i >= 0; i--) {
            CountryStatecraft.Answered a = st.recent.get(i);
            Statecraft.StateEvent ev = Statecraft.event(a.id());
            if (ev == null || a.option() < 0 || a.option() >= ev.options().size()) continue;
            recent.add(new RecentView(ev.title(), ev.options().get(a.option()).text(), a.city(), a.timedOut()));
        }

        List<DecisionView> decisions = new ArrayList<>();
        for (Statecraft.Decision d : Statecraft.decisions()) {
            if (!d.potential().test(base)) continue;
            boolean taken = d.once() && st.decisions.containsKey(d.id());
            List<String> cities = new ArrayList<>();
            if (d.target()) for (String city : c.cities) if (d.cityCondition().test(base.withCity(city))) cities.add(city);
            List<Line> req = new ArrayList<>();
            d.allow().describe(base, req, 0);
            if (d.cost() > 0) req.add(Line.of("statecraft.politicsmod.need.cost", c.balance >= d.cost() ? Line.MET : Line.UNMET,
                    String.valueOf(d.cost())));
            if (d.target()) req.add(Line.of("statecraft.politicsmod.need.target_city", cities.isEmpty() ? Line.UNMET : Line.MET));
            long cd = cooldownLeft(c, d, cycle);
            int state = taken ? TAKEN : cd > 0 ? COOLDOWN
                    : d.allow().test(base) && c.balance >= d.cost() && (!d.target() || !cities.isEmpty()) ? AVAILABLE : LOCKED;
            decisions.add(new DecisionView(d.id(), d.title(), d.desc(), d.cost(), state, cd > 0 ? Effect.minutes((int) cd) : 0,
                    d.target(), cities, req, Effect.describeAll(d.effects(), base)));
        }

        List<GoalView> goals = new ArrayList<>();
        for (Statecraft.Goal g : Statecraft.goals()) {
            boolean done = st.goalsDone.containsKey(g.id());
            if (!done && !st.goalsActive.contains(g.id())) continue;
            List<Line> req = new ArrayList<>();
            g.complete().describe(base, req, 0);
            int progress = done ? 100 : Math.round(g.complete().progress(base) * 100);
            goals.add(new GoalView(g.id(), g.title(), g.desc(), done, progress, req, Effect.describeAll(g.reward(), base)));
        }
        // Open goals first, reached ones after
        goals.sort((a, b) -> Boolean.compare(a.done(), b.done()));

        return new View(enabled(), st.rank, mayAnswer(c, viewer), c.getRole(viewer) == CountryRole.LEADER,
                events, recent, decisions, goals);
    }

    // ── Serialization ────────────────────────────────────────────────────

    public static void write(net.minecraft.network.FriendlyByteBuf buf, View v) {
        buf.writeBoolean(v.enabled());
        buf.writeVarInt(v.rank());
        buf.writeBoolean(v.canAnswer());
        buf.writeBoolean(v.canDecide());
        buf.writeCollection(v.events(), (b, e) -> {
            b.writeUtf(e.id());
            b.writeUtf(e.title());
            b.writeUtf(e.desc());
            b.writeUtf(e.city());
            b.writeVarInt(e.secondsLeft());
            b.writeCollection(e.options(), (bb, o) -> {
                bb.writeUtf(o.text());
                bb.writeBoolean(o.allowed());
                bb.writeCollection(o.effects(), Line::write);
            });
        });
        buf.writeCollection(v.recent(), (b, r) -> {
            b.writeUtf(r.title());
            b.writeUtf(r.option());
            b.writeUtf(r.city());
            b.writeBoolean(r.timedOut());
        });
        buf.writeCollection(v.decisions(), (b, d) -> {
            b.writeUtf(d.id());
            b.writeUtf(d.title());
            b.writeUtf(d.desc());
            b.writeVarInt(d.cost());
            b.writeVarInt(d.state());
            b.writeVarInt(d.cooldownMinutes());
            b.writeBoolean(d.target());
            b.writeCollection(d.cities(), net.minecraft.network.FriendlyByteBuf::writeUtf);
            b.writeCollection(d.requirements(), Line::write);
            b.writeCollection(d.effects(), Line::write);
        });
        buf.writeCollection(v.goals(), (b, g) -> {
            b.writeUtf(g.id());
            b.writeUtf(g.title());
            b.writeUtf(g.desc());
            b.writeBoolean(g.done());
            b.writeVarInt(g.progress());
            b.writeCollection(g.requirements(), Line::write);
            b.writeCollection(g.reward(), Line::write);
        });
    }

    public static View read(net.minecraft.network.FriendlyByteBuf buf) {
        boolean enabled = buf.readBoolean();
        int rank = buf.readVarInt();
        boolean canAnswer = buf.readBoolean();
        boolean canDecide = buf.readBoolean();
        List<EventView> events = buf.readList(b -> new EventView(b.readUtf(), b.readUtf(), b.readUtf(), b.readUtf(), b.readVarInt(),
                b.readList(bb -> new OptionView(bb.readUtf(), bb.readBoolean(), bb.readList(Line::read)))));
        List<RecentView> recent = buf.readList(b -> new RecentView(b.readUtf(), b.readUtf(), b.readUtf(), b.readBoolean()));
        List<DecisionView> decisions = buf.readList(b -> new DecisionView(b.readUtf(), b.readUtf(), b.readUtf(), b.readVarInt(),
                b.readVarInt(), b.readVarInt(), b.readBoolean(), b.readList(net.minecraft.network.FriendlyByteBuf::readUtf),
                b.readList(Line::read), b.readList(Line::read)));
        List<GoalView> goals = buf.readList(b -> new GoalView(b.readUtf(), b.readUtf(), b.readUtf(), b.readBoolean(), b.readVarInt(),
                b.readList(Line::read), b.readList(Line::read)));
        return new View(enabled, rank, canAnswer, canDecide, events, recent, decisions, goals);
    }
}
