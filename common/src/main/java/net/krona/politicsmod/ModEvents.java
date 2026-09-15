package net.krona.politicsmod;

import dev.architectury.event.events.common.BlockEvent;
import dev.architectury.event.events.common.CommandRegistrationEvent;
import dev.architectury.event.events.common.InteractionEvent;
import dev.architectury.event.events.common.PlayerEvent;
import dev.architectury.event.events.common.TickEvent;
import net.krona.politicsmod.events.ServerGameEvents;
import net.krona.politicsmod.market.MarketService;
import net.krona.politicsmod.network.ModNetworking;
import net.krona.politicsmod.network.SyncChunkPayload;

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
                manager.forEachClaim((pos, color) ->
                        ModNetworking.toPlayer(player, new SyncChunkPayload(pos, color)));
            }
        });

        // Drop the warehouse session so entries don't pile up for players who left
        PlayerEvent.PLAYER_QUIT.register(player -> MarketService.forget(player.getUUID()));

        CommandRegistrationEvent.EVENT.register(
                (dispatcher, registry, selection) -> ModCommands.register(dispatcher));

        BlockEvent.BREAK.register(
                (level, pos, state, player, xp) -> ServerGameEvents.onBlockBreak(level, pos, player));

        InteractionEvent.RIGHT_CLICK_BLOCK.register(
                (player, hand, pos, face) -> ServerGameEvents.onBlockInteract(player, pos));

        PlayerEvent.ATTACK_ENTITY.register(
                (player, level, entity, hand, hitResult) -> ServerGameEvents.onAttackEntity(player, entity));

        TickEvent.SERVER_POST.register(ServerGameEvents::onServerTick);
        TickEvent.SERVER_LEVEL_POST.register(PoliticsEvents::onLevelTick);
        TickEvent.PLAYER_POST.register(PoliticsEvents::onPlayerTick);
    }
}
