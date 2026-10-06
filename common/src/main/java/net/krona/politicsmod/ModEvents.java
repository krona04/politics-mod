package net.krona.politicsmod;

import dev.architectury.event.events.common.BlockEvent;
import dev.architectury.event.events.common.CommandRegistrationEvent;
import dev.architectury.event.events.common.InteractionEvent;
import dev.architectury.event.events.common.PlayerEvent;
import dev.architectury.event.events.common.TickEvent;
import net.krona.politicsmod.events.ServerGameEvents;
import net.krona.politicsmod.market.MarketService;
import net.krona.politicsmod.protection.Protection;
import net.krona.politicsmod.config.PoliticsConfig;
import net.krona.politicsmod.item.GuideBookItem;
import dev.architectury.event.events.common.EntityEvent;
import dev.architectury.event.events.common.ExplosionEvent;
import net.krona.politicsmod.network.ModNetworking;
import dev.architectury.event.events.common.LifecycleEvent;

/**
 * Server-side event registration.
 *
 * NeoForge -> Architectury mapping:
 *   PlayerEvent.PlayerLoggedInEvent     -> PlayerEvent.PLAYER_JOIN
 *   RegisterCommandsEvent               -> CommandRegistrationEvent.EVENT
 *   BlockEvent.BreakEvent               -> BlockEvent.BREAK
 *   PlayerInteractEvent.RightClickBlock -> InteractionEvent.RIGHT_CLICK_BLOCK
 *   AttackEntityEvent                   -> PlayerEvent.ATTACK_ENTITY
 *   ServerTickEvent.Post                -> TickEvent.SERVER_POST
 *   LevelTickEvent.Post                 -> TickEvent.SERVER_LEVEL_POST
 *   PlayerTickEvent.Post                -> TickEvent.PLAYER_POST
 *
 * To cancel, return EventResult.interruptFalse() instead of event.setCanceled(true);
 * return EventResult.pass() to let the event continue.
 */
public final class ModEvents {

    private ModEvents() {
    }

    public static void register() {
        // Sync all claimed chunks to the joining player
        PlayerEvent.PLAYER_JOIN.register(player -> {
            PoliticsManager manager = PoliticsManager.get(player.level());
            if (manager != null) {
                if (PoliticsConfig.get().giveGuideOnFirstJoin && manager.markGuideGiven(player.getUUID())) {
                    GuideBookItem.give(player);
                }
                manager.syncToPlayer(player);
                ModNetworking.toPlayer(player, net.krona.politicsmod.network.SyncFlagsPayload.of(manager));
            }
        });

        // Drop the warehouse session so entries don't pile up for players who left
        PlayerEvent.PLAYER_QUIT.register(player -> {
            MarketService.forget(player.getUUID());
            Protection.forget(player.getUUID());
        });

        // Territory protection beyond breaking and interacting
        BlockEvent.PLACE.register(Protection::onPlace);
        EntityEvent.LIVING_HURT.register(Protection::onLivingHurt);
        ExplosionEvent.DETONATE.register(Protection::onExplosion);
        PlayerEvent.PICKUP_ITEM_PRE.register(Protection::onPickup);

        CommandRegistrationEvent.EVENT.register(
                (dispatcher, registry, selection) -> {
                    ModCommands.register(dispatcher);
                    net.krona.politicsmod.commands.PlotCommands.register(dispatcher);
                    net.krona.politicsmod.commands.FlagCommands.register(dispatcher);
                    net.krona.politicsmod.commands.GeneralCommands.register(dispatcher);
                    net.krona.politicsmod.commands.StateCommands.register(dispatcher);
                });

        BlockEvent.BREAK.register(
                (level, pos, state, player, xp) -> ServerGameEvents.onBlockBreak(level, pos, player));

        InteractionEvent.RIGHT_CLICK_BLOCK.register(
                (player, hand, pos, face) -> ServerGameEvents.onBlockInteract(player, pos));

        PlayerEvent.ATTACK_ENTITY.register(
                (player, level, entity, hand, hitResult) -> ServerGameEvents.onAttackEntity(player, entity));

        TickEvent.SERVER_POST.register(ServerGameEvents::onServerTick);
        TickEvent.SERVER_LEVEL_POST.register(PoliticsEvents::onLevelTick);
        TickEvent.PLAYER_POST.register(PoliticsEvents::onPlayerTick);

        LifecycleEvent.SERVER_STARTED.register(net.krona.politicsmod.integration.webmap.WebMaps::start);
        LifecycleEvent.SERVER_STOPPING.register(server -> {
            net.krona.politicsmod.war.SiegeService.clear();
            net.krona.politicsmod.integration.webmap.WebMaps.stop();
        });
    }
}
