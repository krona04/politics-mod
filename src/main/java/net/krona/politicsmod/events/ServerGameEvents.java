package net.krona.politicsmod.events;

import net.krona.politicsmod.Politicsmod;
import net.krona.politicsmod.PoliticsManager;
import net.krona.politicsmod.politics.Country;
import net.krona.politicsmod.politics.CountryRole;
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
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.krona.politicsmod.politics.Country;
import net.minecraft.core.BlockPos;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import net.krona.politicsmod.network.SyncRadarPayload;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = Politicsmod.MODID, bus = EventBusSubscriber.Bus.GAME)
public class ServerGameEvents {

    private static int economyTimer = 0;
    private static int radarTimer    = 0;
    private static final int ECONOMY_CYCLE_TICKS = 10000;
    private static final int RADAR_CHECK_TICKS   = 100; // ~5 сек

    // 1. Защита от разрушения блоков
    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.getLevel().isClientSide() || event.getPlayer().isCreative()) return;
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) return;

        PoliticsManager manager = PoliticsManager.get(serverLevel);
        if (manager == null) return;

        ChunkPos chunkPos = new ChunkPos(event.getPos());
        Country country = manager.getCountryAt(chunkPos);
        if (country == null) return;

        CountryRole role = country.getRole(event.getPlayer().getUUID());
        if (role.canBuild()) return;

        // Проверка военного исключения: враг может ломать блоки на приграничных чанках
        String attackerCountry = manager.getPlayerCountry(event.getPlayer().getUUID());
        String territoryCountry = manager.getCountryNameAt(chunkPos);
        if (attackerCountry != null
                && manager.isAtWar(attackerCountry, territoryCountry)
                && manager.isBorderChunk(chunkPos, territoryCountry)) {
            // Война — ломать приграничные блоки разрешено
            return;
        }

        event.getPlayer().displayClientMessage(
                Component.translatable("message.politicsmod.no_build_permission", country.getName())
                        .withStyle(ChatFormatting.RED), true
        );
        event.setCanceled(true);
    }

    // 2. Защита от взаимодействия (сундуки, двери и т.д.)
    @SubscribeEvent
    public static void onBlockInteract(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide() || event.getEntity().isCreative()) return;
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) return;

        PoliticsManager manager = PoliticsManager.get(serverLevel);
        if (manager == null) return;

        ChunkPos chunkPos = new ChunkPos(event.getPos());
        Country country = manager.getCountryAt(chunkPos);
        if (country == null) return;

        CountryRole role = country.getRole(event.getEntity().getUUID());
        if (role.canBuild()) return;

        // Войска врага не могут открывать сундуки даже на приграничных чанках
        // (грабёж только через разрушение блоков)
        event.getEntity().displayClientMessage(
                Component.translatable("message.politicsmod.no_interact_permission")
                        .withStyle(ChatFormatting.RED), true
        );
        event.setCanceled(true);
    }

    // 3. Защита союзников от PvP
    @SubscribeEvent
    public static void onAttackEntity(AttackEntityEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer attacker)) return;
        if (!(event.getTarget() instanceof ServerPlayer victim)) return;
        if (event.getEntity().level().isClientSide()) return;
        if (!(event.getEntity().level() instanceof ServerLevel serverLevel)) return;

        PoliticsManager manager = PoliticsManager.get(serverLevel);
        if (manager == null) return;

        String attackerCountry = manager.getPlayerCountry(attacker.getUUID());
        String victimCountry = manager.getPlayerCountry(victim.getUUID());

        if (attackerCountry != null && victimCountry != null
                && manager.isAllied(attackerCountry, victimCountry)) {
            attacker.displayClientMessage(
                    Component.translatable("message.politicsmod.no_attack_ally", victimCountry)
                            .withStyle(ChatFormatting.YELLOW), true
            );
            event.setCanceled(true);
        }
    }

    // 4. Экономический цикл
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        economyTimer++;
        radarTimer++;

        if (radarTimer >= RADAR_CHECK_TICKS) {
            radarTimer = 0;
            MinecraftServer srv = ServerLifecycleHooks.getCurrentServer();
            if (srv != null) {
                ServerLevel ow = srv.overworld();
                checkRadars(srv, ow, PoliticsManager.get(ow));
            }
        }

        if (economyTimer >= ECONOMY_CYCLE_TICKS) {
            economyTimer = 0;

            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            if (server != null) {
                ServerLevel overworld = server.overworld();
                PoliticsManager manager = PoliticsManager.get(overworld);

                if (manager != null) {
                    manager.processEconomyCycle(overworld);

                    server.getPlayerList().broadcastSystemMessage(
                            Component.translatable("message.politicsmod.economy.cycle_complete")
                                    .withStyle(ChatFormatting.AQUA),
                            false
                    );
                }
            }
        }
    }

    private static void checkRadars(MinecraftServer server, ServerLevel level, PoliticsManager manager) {
        if (manager == null) return;

        for (Map.Entry<String, Country> entry : manager.getCountries().entrySet()) {
            String countryName = entry.getKey();
            Country country    = entry.getValue();
            if (country.radarBlocks.isEmpty()) continue;

            // Собираем врагов (игроков из стран в состоянии войны)
            List<ServerPlayer> enemies = new ArrayList<>();
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                String pCountry = manager.getPlayerCountry(p.getUUID());
                if (pCountry != null && manager.isAtWar(countryName, pCountry)) {
                    enemies.add(p);
                }
            }
            if (enemies.isEmpty()) continue;

            // Проверяем попадание в радиус
            Set<String> detected = new HashSet<>();
            for (Long radarPosLong : country.radarBlocks) {
                net.minecraft.world.level.ChunkPos radarChunk =
                    new net.minecraft.world.level.ChunkPos(BlockPos.of(radarPosLong));
                for (ServerPlayer enemy : enemies) {
                    net.minecraft.world.level.ChunkPos ec = enemy.chunkPosition();
                    if (Math.abs(ec.x - radarChunk.x) <= PoliticsManager.RADAR_RANGE_CHUNKS &&
                        Math.abs(ec.z - radarChunk.z) <= PoliticsManager.RADAR_RANGE_CHUNKS) {
                        detected.add(enemy.getName().getString());
                    }
                }
            }

            if (!detected.isEmpty()) {
                SyncRadarPayload packet = new SyncRadarPayload(String.join(", ", detected));
                for (ServerPlayer member : server.getPlayerList().getPlayers()) {
                    if (countryName.equals(manager.getPlayerCountry(member.getUUID()))) {
                        PacketDistributor.sendToPlayer(member, packet);
                    }
                }
            }
        }
    }
}
