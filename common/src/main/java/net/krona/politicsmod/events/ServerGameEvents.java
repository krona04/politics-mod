package net.krona.politicsmod.events;

import net.krona.politicsmod.config.PoliticsConfig;

import net.krona.politicsmod.Politicsmod;
import net.krona.politicsmod.PoliticsManager;
import net.krona.politicsmod.politics.Country;
import net.krona.politicsmod.politics.CountryRole;
import net.krona.politicsmod.protection.Protection;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import dev.architectury.event.EventResult;
import net.krona.politicsmod.network.ModNetworking;
import net.krona.politicsmod.network.SyncRadarPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/** Handlers are registered in ModEvents. EventResult.interruptFalse() cancels the event. */
public class ServerGameEvents {

    private static int economyTimer = 0;
    private static int radarTimer    = 0;
    private static int settlementSeconds = 0;
    private static int secondTimer   = 0;

    // 1. Block break protection (plots, roles, sieges)
    public static EventResult onBlockBreak(Level level, BlockPos pos, Player player) {
        if (level.isClientSide() || !(level instanceof ServerLevel serverLevel)) return EventResult.pass();
        PoliticsManager manager = PoliticsManager.at(serverLevel);
        if (manager == null) return EventResult.pass();
        return deny(player, Protection.buildDenied(manager, player, pos, true));
    }

    // 2. Interaction protection (chests, doors, etc.)
    public static EventResult onBlockInteract(Player player, BlockPos pos) {
        Level level = player.level();
        if (level.isClientSide() || !(level instanceof ServerLevel serverLevel)) return EventResult.pass();
        PoliticsManager manager = PoliticsManager.at(serverLevel);
        if (manager == null) return EventResult.pass();
        return deny(player, Protection.interactDenied(manager, player, pos));
    }

    private static EventResult deny(Player player, Component reason) {
        if (reason == null) return EventResult.pass();
        player.displayClientMessage(reason.copy().withStyle(ChatFormatting.RED), true);
        return EventResult.interruptFalse();
    }

    // 3. No PvP between allies
    public static EventResult onAttackEntity(Player player, Entity target) {
        if (!(player instanceof ServerPlayer attacker)) return EventResult.pass();
        if (!(target instanceof ServerPlayer victim)) return EventResult.pass();
        if (!(attacker.level() instanceof ServerLevel serverLevel)) return EventResult.pass();

        PoliticsManager manager = PoliticsManager.get(serverLevel);
        if (manager == null) return EventResult.pass();

        String attackerCountry = manager.getPlayerCountry(attacker.getUUID());
        String victimCountry = manager.getPlayerCountry(victim.getUUID());

        if (attackerCountry != null && victimCountry != null
                && manager.isAllied(attackerCountry, victimCountry)) {
            attacker.displayClientMessage(
                    Component.translatable("message.politicsmod.no_attack_ally", victimCountry)
                            .withStyle(ChatFormatting.YELLOW), true
            );
            return EventResult.interruptFalse();
        }
        return EventResult.pass();
    }

    // 4. Economy cycle
    public static void onServerTick(MinecraftServer server) {
        economyTimer++;
        radarTimer++;

        net.krona.politicsmod.war.SiegeService.tick(server);
        if (++secondTimer >= 20) {
            secondTimer = 0;
            PoliticsManager mgr = PoliticsManager.get(server.overworld());
            if (mgr != null) {
                net.krona.politicsmod.politics.ElectionService.tick(mgr);
                net.krona.politicsmod.integration.webmap.WebMaps.tick(mgr);
                // Colonies and villages: break-aways, news and colony ranks, every 10 seconds
                if (++settlementSeconds >= 10) {
                    settlementSeconds = 0;
                    net.krona.politicsmod.integration.settlement.Settlements.sync(server, mgr);
                }
            }
        }

        if (radarTimer >= PoliticsConfig.get().radarCheckTicks) {
            radarTimer = 0;
            ServerLevel ow = server.overworld();
            checkRadars(server, ow, PoliticsManager.get(ow));
        }

        if (economyTimer >= PoliticsConfig.get().economyCycleTicks) {
            economyTimer = 0;

            {
                ServerLevel overworld = server.overworld();
                PoliticsManager manager = PoliticsManager.get(overworld);

                if (manager != null) {
                    manager.processEconomyCycle(overworld);
                }
            }
        }
        net.krona.politicsmod.economy.DailyReport.tick(server);
    }

    private static void checkRadars(MinecraftServer server, ServerLevel level, PoliticsManager manager) {
        if (manager == null) return;

        for (Map.Entry<String, Country> entry : manager.getCountries().entrySet()) {
            String countryName = entry.getKey();
            Country country    = entry.getValue();
            if (country.radarBlocks.isEmpty()) continue;

            // Collect enemies (players from countries at war)
            List<ServerPlayer> enemies = new ArrayList<>();
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                String pCountry = manager.getPlayerCountry(p.getUUID());
                if (pCountry != null && manager.isAtWar(countryName, pCountry) && PoliticsManager.hasTerritory(p.level())) {
                    enemies.add(p);
                }
            }
            if (enemies.isEmpty()) continue;

            // Check which enemies are within radar range
            Set<String> detected = new HashSet<>();
            for (Long radarPosLong : country.radarBlocks) {
                net.minecraft.world.level.ChunkPos radarChunk =
                    new net.minecraft.world.level.ChunkPos(BlockPos.of(radarPosLong));
                for (ServerPlayer enemy : enemies) {
                    net.minecraft.world.level.ChunkPos ec = enemy.chunkPosition();
                    if (Math.abs(ec.x - radarChunk.x) <= PoliticsConfig.get().radarRangeChunks &&
                        Math.abs(ec.z - radarChunk.z) <= PoliticsConfig.get().radarRangeChunks) {
                        detected.add(enemy.getName().getString());
                    }
                }
            }

            if (!detected.isEmpty()) {
                SyncRadarPayload packet = new SyncRadarPayload(String.join(", ", detected));
                for (ServerPlayer member : server.getPlayerList().getPlayers()) {
                    if (countryName.equals(manager.getPlayerCountry(member.getUUID()))) {
                        ModNetworking.toPlayer(member, packet);
                    }
                }
            }
        }
    }
}
