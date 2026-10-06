package net.krona.politicsmod.protection;

import dev.architectury.event.EventResult;
import net.krona.politicsmod.PoliticsManager;
import net.krona.politicsmod.config.PoliticsConfig;
import net.krona.politicsmod.politics.Country;
import net.krona.politicsmod.politics.CountryRole;
import net.krona.politicsmod.politics.Plot;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Territory protection: who may build, open containers, enter, fight and pick things up,
 * and what explosions and fire may destroy. Handlers are registered in ModEvents.
 */
public final class Protection {

    private record SafeSpot(ResourceKey<Level> dimension, Vec3 pos) {
    }

    private static final Map<UUID, SafeSpot> LAST_ALLOWED = new HashMap<>();

    private Protection() {
    }

    /** Operators always ignore protection; creative players only if the config allows it. */
    public static boolean bypasses(Player player) {
        return player.hasPermissions(2) || (PoliticsConfig.get().creativeBypassesProtection && player.isCreative());
    }

    // ── Building and interaction ─────────────────────────────────────────

    /**
     * Returns null if the player may break or place blocks at pos, otherwise the reason to show.
     * warBreak = allow enemies to break blocks in a chunk their country is besieging.
     */
    public static Component buildDenied(PoliticsManager mgr, Player player, BlockPos pos, boolean warBreak) {
        if (bypasses(player)) return null;
        ChunkPos chunk = new ChunkPos(pos);
        Country country = mgr.getCountryAt(chunk);
        if (country == null) return null;

        UUID id = player.getUUID();
        Plot plot = mgr.getPlot(chunk);
        if (plot != null && plot.isOwned()) {
            if (plot.canBuild(id)) return null;
        } else if (country.getRole(id).canBuild()) {
            return null;
        }

        if (warBreak) {
            String attacker = mgr.getPlayerCountry(id);
            if (attacker != null && mgr.isAtWar(attacker, country.getName())
                    && net.krona.politicsmod.war.SiegeService.isBesieging(attacker, chunk)) {
                return null;
            }
        }
        if (plot != null && plot.isOwned()) {
            return Component.translatable("message.politicsmod.plot.no_permission", plot.ownerName);
        }
        return Component.translatable("message.politicsmod.no_build_permission", country.getName());
    }

    /** Returns null if the player may use doors, containers and other blocks at pos. */
    public static Component interactDenied(PoliticsManager mgr, Player player, BlockPos pos) {
        if (bypasses(player)) return null;
        ChunkPos chunk = new ChunkPos(pos);
        Country country = mgr.getCountryAt(chunk);
        if (country == null) return null;

        UUID id = player.getUUID();
        Plot plot = mgr.getPlot(chunk);
        if (plot != null && plot.isOwned()) {
            return plot.canAccess(id) ? null : Component.translatable("message.politicsmod.plot.no_permission", plot.ownerName);
        }
        // Enemies can't open containers even in border chunks: looting only by breaking blocks
        return country.getRole(id).canBuild() ? null : Component.translatable("message.politicsmod.no_interact_permission");
    }

    public static EventResult onPlace(Level level, BlockPos pos, net.minecraft.world.level.block.state.BlockState state, Entity entity) {
        if (!(entity instanceof ServerPlayer player) || !(level instanceof ServerLevel serverLevel)) return EventResult.pass();
        PoliticsManager mgr = PoliticsManager.at(serverLevel);
        if (mgr == null) return EventResult.pass();
        return deny(player, buildDenied(mgr, player, pos, false));
    }

    // ── Combat ───────────────────────────────────────────────────────────

    /** Covers melee and projectiles: allies never hurt each other, and PvP follows the flag unless at war. */
    public static EventResult onLivingHurt(LivingEntity victim, DamageSource source, float amount) {
        if (!(victim instanceof ServerPlayer target) || !(source.getEntity() instanceof ServerPlayer attacker)) {
            return EventResult.pass();
        }
        if (attacker == target || bypasses(attacker)) return EventResult.pass();
        PoliticsManager mgr = PoliticsManager.get(target.serverLevel());
        if (mgr == null) return EventResult.pass();

        String a = mgr.getPlayerCountry(attacker.getUUID());
        String v = mgr.getPlayerCountry(target.getUUID());
        if (a != null && v != null && !a.equals(v)) {
            if (mgr.isAtWar(a, v)) return EventResult.pass();
            if (mgr.isAllied(a, v)) {
                return deny(attacker, Component.translatable("message.politicsmod.no_attack_ally", v));
            }
        }
        if (PoliticsManager.hasTerritory(target.level()) && !mgr.isAllowed(target.chunkPosition(), ProtectionFlag.PVP)) {
            return deny(attacker, Component.translatable("message.politicsmod.protection.pvp_denied"));
        }
        return EventResult.pass();
    }

    // ── World damage ─────────────────────────────────────────────────────

    public static void onExplosion(Level level, Explosion explosion, List<Entity> affected) {
        if (!(level instanceof ServerLevel serverLevel)) return;
        PoliticsManager mgr = PoliticsManager.at(serverLevel);
        if (mgr == null) return;
        boolean byMob = explosion.getIndirectSourceEntity() instanceof Mob || explosion.getDirectSourceEntity() instanceof Mob;
        ProtectionFlag flag = byMob ? ProtectionFlag.MOB_EXPLOSIONS : ProtectionFlag.EXPLOSIONS;
        explosion.getToBlow().removeIf(pos -> !mgr.isAllowed(new ChunkPos(pos), flag));
    }

    /** Called from FireBlockMixin before fire burns or spreads to a block. */
    public static boolean fireAllowed(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel serverLevel)) return true;
        PoliticsManager mgr = PoliticsManager.at(serverLevel);
        return mgr == null || mgr.isAllowed(new ChunkPos(pos), ProtectionFlag.FIRE_SPREAD);
    }

    // ── Outsiders ────────────────────────────────────────────────────────

    public static EventResult onPickup(Player player, ItemEntity item, net.minecraft.world.item.ItemStack stack) {
        if (!(player instanceof ServerPlayer sp) || bypasses(sp)) return EventResult.pass();
        PoliticsManager mgr = PoliticsManager.at(sp.serverLevel());
        if (mgr == null) return EventResult.pass();
        ChunkPos chunk = new ChunkPos(item.blockPosition());
        Country country = mgr.getCountryAt(chunk);
        if (country == null || isWelcome(mgr, sp, country)) return EventResult.pass();
        return mgr.isAllowed(chunk, ProtectionFlag.ITEM_PICKUP) ? EventResult.pass() : EventResult.interruptFalse();
    }

    /**
     * Keeps players out of land they may not enter (the ENTRY flag and locked plots) by sending
     * them back to the last place they were allowed to stand. Called a few times per second.
     */
    public static void checkEntry(ServerPlayer player) {
        if (bypasses(player)) {
            remember(player);
            return;
        }
        PoliticsManager mgr = PoliticsManager.at(player.serverLevel());
        Component reason = mgr != null ? entryDenied(mgr, player, player.chunkPosition()) : null;
        if (reason == null) {
            remember(player);
            return;
        }
        // Back to the last allowed spot, but only if it is still allowed: when a law or flag changes while
        // the player stands inside, that spot is inside too, and sending them there would loop forever
        SafeSpot spot = LAST_ALLOWED.get(player.getUUID());
        if (spot != null && spot.dimension().equals(player.level().dimension())
                && entryDenied(mgr, player, new ChunkPos(net.minecraft.core.BlockPos.containing(spot.pos()))) == null) {
            player.teleportTo(spot.pos().x, spot.pos().y, spot.pos().z);
        } else {
            Vec3 out = nearestAllowed(mgr, player);
            if (out != null) {
                player.teleportTo(out.x, out.y, out.z);
                remember(player);
            }
        }
        player.displayClientMessage(reason.copy().withStyle(ChatFormatting.RED), true);
    }

    /** Surface of the nearest chunk the player may stand in, searched ring by ring; null if none is close. */
    private static Vec3 nearestAllowed(PoliticsManager mgr, ServerPlayer player) {
        ChunkPos here = player.chunkPosition();
        for (int r = 1; r <= 32; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.abs(dx) != r && Math.abs(dz) != r) continue; // ring only
                    ChunkPos c = new ChunkPos(here.x + dx, here.z + dz);
                    if (entryDenied(mgr, player, c) != null) continue;
                    int bx = c.getMiddleBlockX(), bz = c.getMiddleBlockZ();
                    int by = player.serverLevel().getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz);
                    return new Vec3(bx + 0.5, by, bz + 0.5);
                }
            }
        }
        return null;
    }

    public static void forget(UUID player) {
        LAST_ALLOWED.remove(player);
    }

    private static void remember(ServerPlayer player) {
        LAST_ALLOWED.put(player.getUUID(), new SafeSpot(player.level().dimension(), player.position()));
    }

    private static Component entryDenied(PoliticsManager mgr, ServerPlayer player, ChunkPos chunk) {
        Country country = mgr.getCountryAt(chunk);
        if (country == null) return null;
        UUID id = player.getUUID();

        Plot plot = mgr.getPlot(chunk);
        if (plot != null && plot.isOwned() && plot.closed && !plot.canAccess(id)) {
            CountryRole role = country.getRole(id);
            boolean mayor = id.equals(country.cityMayors.get(plot.city));
            if (role != CountryRole.LEADER && !mayor) {
                return Component.translatable("message.politicsmod.plot.closed", plot.ownerName);
            }
        }
        String mine = mgr.getPlayerCountry(id);
        if (mine != null && mgr.isAtWar(mine, country.getName())) return null;
        // Closed borders keep out everyone but citizens, allies included
        if (country.hasLaw(net.krona.politicsmod.politics.Law.CLOSED_BORDERS) && country.getRole(id) == CountryRole.GUEST) {
            return Component.translatable("message.politicsmod.law.borders_closed", country.getName());
        }
        if (isWelcome(mgr, player, country)) return null;
        return mgr.isAllowed(chunk, ProtectionFlag.ENTRY) ? null
                : Component.translatable("message.politicsmod.protection.entry_denied", country.getName());
    }

    /** Members and allies of a country are never treated as outsiders there. */
    private static boolean isWelcome(PoliticsManager mgr, Player player, Country country) {
        if (country.getRole(player.getUUID()) != CountryRole.GUEST) return true;
        String mine = mgr.getPlayerCountry(player.getUUID());
        return mine != null && mgr.isAllied(mine, country.getName());
    }

    private static EventResult deny(Player player, Component reason) {
        if (reason == null) return EventResult.pass();
        player.displayClientMessage(reason.copy().withStyle(ChatFormatting.RED), true);
        return EventResult.interruptFalse();
    }
}
