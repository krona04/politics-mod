package net.krona.politicsmod.war;

import net.krona.politicsmod.PoliticsManager;
import net.krona.politicsmod.config.PoliticsConfig;
import net.krona.politicsmod.politics.Country;
import net.krona.politicsmod.politics.CountryRole;
import net.krona.politicsmod.politics.Law;
import net.krona.politicsmod.politics.Notify;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * Sieges: during a war, attackers capture an enemy border chunk by holding it.
 *
 * Progress grows only while more attackers than defenders stand in the chunk; the siege fails
 * when no attacker has been there for the grace period. Sieges are not saved: a restart ends them.
 */
public final class SiegeService {

    private static final int STEP = 20; // ticks between updates

    public static final class Siege {
        public final String attacker;
        public final String defender;
        public final ChunkPos pos;
        public final ResourceKey<Level> dimension;
        public final int required;
        int progress;
        int grace;
        int attackersInside;
        int defendersInside;
        final ServerBossEvent bar;

        Siege(String attacker, String defender, ChunkPos pos, ResourceKey<Level> dimension, int required) {
            this.attacker = attacker;
            this.defender = defender;
            this.pos = pos;
            this.dimension = dimension;
            this.required = required;
            this.grace = PoliticsConfig.get().siegeGraceSeconds * 20;
            this.bar = new ServerBossEvent(Component.empty(), BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.NOTCHED_10);
        }

        public int percent() {
            return Math.min(100, progress * 100 / Math.max(1, required));
        }

        public int secondsLeft() {
            return Math.max(0, (required - progress) / 20);
        }
    }

    private static final List<Siege> SIEGES = new ArrayList<>();
    private static int timer = 0;

    private SiegeService() {
    }

    public static List<Siege> all() {
        return Collections.unmodifiableList(SIEGES);
    }

    public static List<Siege> of(String country) {
        List<Siege> list = new ArrayList<>();
        for (Siege s : SIEGES) if (s.attacker.equals(country) || s.defender.equals(country)) list.add(s);
        return list;
    }

    /** Attackers may break blocks only in chunks their country is besieging. */
    public static boolean isBesieging(String attacker, ChunkPos pos) {
        for (Siege s : SIEGES) if (s.attacker.equals(attacker) && s.pos.equals(pos)) return true;
        return false;
    }

    /** Starts a siege on the chunk the player stands in. Returns null on success, otherwise the reason. */
    public static Component start(ServerPlayer player, PoliticsManager mgr) {
        if (!PoliticsManager.hasTerritory(player.level())) return Component.translatable("message.politicsmod.overworld_only");
        String mine = mgr.getPlayerCountry(player.getUUID());
        if (mine == null) return Component.translatable("message.politicsmod.no_state");
        Country attacker = mgr.getCountry(mine);
        CountryRole role = attacker.getRole(player.getUUID());
        if (role != CountryRole.LEADER && role != CountryRole.MAYOR) {
            return Component.translatable("message.politicsmod.siege.no_permission");
        }
        if (attacker.isBankrupt()) return Component.translatable("message.politicsmod.debt.bankrupt_blocked");

        ChunkPos pos = player.chunkPosition();
        String defender = mgr.getCountryNameAt(pos);
        if (defender == null || defender.equals(mine)) return Component.translatable("message.politicsmod.siege.not_enemy_land");
        if (!mgr.isAtWar(mine, defender)) return Component.translatable("message.politicsmod.diplomacy.not_at_war", defender);
        if (!mgr.isBorderChunk(pos, defender)) return Component.translatable("message.politicsmod.siege.not_border");
        for (Siege s : SIEGES) {
            if (s.pos.equals(pos)) return Component.translatable("message.politicsmod.siege.already");
        }
        int running = 0;
        for (Siege s : SIEGES) if (s.attacker.equals(mine)) running++;
        if (running >= PoliticsConfig.get().maxSiegesPerCountry) {
            return Component.translatable("message.politicsmod.siege.too_many", PoliticsConfig.get().maxSiegesPerCountry);
        }
        int cost = PoliticsConfig.get().siegeCost;
        if (attacker.balance < cost) return Component.translatable("message.politicsmod.siege.no_funds", cost);

        attacker.balance -= cost;
        int required = PoliticsConfig.get().siegeHoldSeconds * 20;
        Country def = mgr.getCountry(defender);
        if (def.hasLaw(Law.MARTIAL_LAW)) required *= PoliticsConfig.get().martialLawSiegeMultiplier;
        SIEGES.add(new Siege(mine, defender, pos, player.level().dimension(), required));
        mgr.setDirty();

        mgr.postNews("news.politicsmod.siege_started", List.of(mine, defender), mine, defender,
                String.valueOf(pos.x), String.valueOf(pos.z));
        Component msg = Component.translatable("message.politicsmod.siege.started", mine, defender, pos.x, pos.z, required / 20)
                .withStyle(ChatFormatting.DARK_RED);
        Notify.members(attacker, msg);
        Notify.members(def, msg);
        return null;
    }

    /** Cancels every siege the country is running. Returns how many were cancelled. */
    public static int cancel(String attacker) {
        int n = 0;
        for (Iterator<Siege> it = SIEGES.iterator(); it.hasNext(); ) {
            Siege s = it.next();
            if (s.attacker.equals(attacker)) {
                s.bar.removeAllPlayers();
                it.remove();
                n++;
            }
        }
        return n;
    }

    public static void tick(MinecraftServer server) {
        if (SIEGES.isEmpty() || ++timer < STEP) return;
        timer = 0;
        PoliticsManager mgr = PoliticsManager.get(server.overworld());
        if (mgr == null) return;

        for (Iterator<Siege> it = SIEGES.iterator(); it.hasNext(); ) {
            Siege s = it.next();
            Country attacker = mgr.getCountry(s.attacker);
            Country defender = mgr.getCountry(s.defender);
            // Peace, a dissolved country or a lost chunk ends the siege quietly
            if (attacker == null || defender == null || !mgr.isAtWar(s.attacker, s.defender)
                    || !s.defender.equals(mgr.getCountryNameAt(s.pos))) {
                s.bar.removeAllPlayers();
                it.remove();
                continue;
            }

            ServerLevel level = server.getLevel(s.dimension);
            s.attackersInside = 0;
            s.defendersInside = 0;
            if (level != null) {
                for (ServerPlayer p : level.players()) {
                    if (!p.isAlive() || p.isSpectator() || !p.chunkPosition().equals(s.pos)) continue;
                    String c = mgr.getPlayerCountry(p.getUUID());
                    if (c == null) continue;
                    if (c.equals(s.attacker)) s.attackersInside++;
                    else if (c.equals(s.defender) || mgr.isAllied(c, s.defender)) s.defendersInside++;
                }
            }

            if (s.attackersInside > 0) {
                s.grace = PoliticsConfig.get().siegeGraceSeconds * 20;
                if (s.attackersInside > s.defendersInside) s.progress += STEP;
            } else {
                s.grace -= STEP;
            }

            if (s.progress >= s.required) {
                s.bar.removeAllPlayers();
                it.remove();
                mgr.transferChunk(s.pos, s.attacker);
                mgr.postNews("news.politicsmod.siege_won", List.of(s.attacker, s.defender), s.attacker,
                        String.valueOf(s.pos.x), String.valueOf(s.pos.z), s.defender);
                Component msg = Component.translatable("message.politicsmod.siege.won", s.attacker, s.pos.x, s.pos.z, s.defender)
                        .withStyle(ChatFormatting.GOLD);
                Notify.members(attacker, msg);
                Notify.members(defender, msg);
                continue;
            }
            if (s.grace <= 0) {
                s.bar.removeAllPlayers();
                it.remove();
                mgr.postNews("news.politicsmod.siege_failed", List.of(s.attacker, s.defender), s.attacker,
                        String.valueOf(s.pos.x), String.valueOf(s.pos.z));
                Component msg = Component.translatable("message.politicsmod.siege.failed", s.attacker, s.pos.x, s.pos.z)
                        .withStyle(ChatFormatting.YELLOW);
                Notify.members(attacker, msg);
                Notify.members(defender, msg);
                continue;
            }
            updateBar(server, mgr, s);
        }
    }

    /** The bar is shown to both countries and to anyone within two chunks of the siege. */
    private static void updateBar(MinecraftServer server, PoliticsManager mgr, Siege s) {
        String state = s.attackersInside == 0 ? "gui.politicsmod.siege.bar_empty"
                : s.attackersInside > s.defendersInside ? "gui.politicsmod.siege.bar_advancing"
                : "gui.politicsmod.siege.bar_contested";
        s.bar.setName(Component.translatable("gui.politicsmod.siege.bar", s.attacker, s.defender, s.pos.x, s.pos.z,
                Component.translatable(state), s.attackersInside, s.defendersInside));
        s.bar.setProgress(Math.min(1f, s.progress / (float) s.required));
        s.bar.setColor(s.attackersInside > s.defendersInside ? BossEvent.BossBarColor.RED : BossEvent.BossBarColor.YELLOW);

        Set<ServerPlayer> viewers = new HashSet<>();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            String c = mgr.getPlayerCountry(p.getUUID());
            boolean involved = s.attacker.equals(c) || s.defender.equals(c);
            boolean near = p.level().dimension().equals(s.dimension)
                    && Math.abs(p.chunkPosition().x - s.pos.x) <= 2 && Math.abs(p.chunkPosition().z - s.pos.z) <= 2;
            if (involved || near) viewers.add(p);
        }
        for (ServerPlayer p : new ArrayList<>(s.bar.getPlayers())) {
            if (!viewers.contains(p)) s.bar.removePlayer(p);
        }
        for (ServerPlayer p : viewers) s.bar.addPlayer(p);
    }

    /** Called when the server stops, so bars don't leak into the next world. */
    public static void clear() {
        for (Siege s : SIEGES) s.bar.removeAllPlayers();
        SIEGES.clear();
        timer = 0;
    }
}
