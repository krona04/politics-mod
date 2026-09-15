package net.krona.politicsmod.client;

import dev.architectury.event.events.client.ClientGuiEvent;
import dev.architectury.event.events.client.ClientPlayerEvent;
import dev.architectury.event.events.client.ClientTickEvent;
import dev.architectury.registry.client.keymappings.KeyMappingRegistry;
import dev.architectury.registry.client.rendering.BlockEntityRendererRegistry;
import net.krona.politicsmod.platform.RenderHooks;
import net.krona.politicsmod.registry.ModBlockEntities;

/**
 * Common client entry point. Called from PoliticsmodFabricClient
 * and from PoliticsmodNeoForge (on Dist.CLIENT only).
 *
 * NeoForge -> Architectury mapping:
 *   RegisterKeyMappingsEvent            -> KeyMappingRegistry.register(...)
 *   RegisterGuiLayersEvent (HUD)        -> ClientGuiEvent.RENDER_HUD
 *   InputEvent.Key                      -> ClientTickEvent.CLIENT_POST + consumeClick()
 *   ClientPlayerNetworkEvent.LoggingIn  -> ClientPlayerEvent.CLIENT_PLAYER_JOIN
 *   ClientPlayerNetworkEvent.LoggingOut -> ClientPlayerEvent.CLIENT_PLAYER_QUIT
 *   RegisterClientCommandsEvent         -> none: /politicsmod borders is a server command (see BorderSettingsPayload)
 *   EntityRenderersEvent.RegisterRenderers -> BlockEntityRendererRegistry.register(...)
 *   RenderLevelStageEvent               -> no equivalent, see RenderHooks (@ExpectPlatform)
 */
public final class PoliticsmodClient {

    private static final PoliticsHudOverlay HUD = new PoliticsHudOverlay();

    private PoliticsmodClient() {
    }

    public static void init() {
        KeyMappingRegistry.register(ModKeyMappings.OPEN_COUNTRY_MENU);

        ClientGuiEvent.RENDER_HUD.register(HUD::render);

        ClientTickEvent.CLIENT_POST.register(ClientGameEvents::onClientTick);

        ClientPlayerEvent.CLIENT_PLAYER_JOIN.register(player -> ClientPoliticsData.clear());
        ClientPlayerEvent.CLIENT_PLAYER_QUIT.register(player -> ClientPoliticsData.clear());

        // On NeoForge the block entity type is registered later, so wait for it via listen()
        ModBlockEntities.FOUNDING_STONE_BE.listen(type ->
                BlockEntityRendererRegistry.register(type, context -> new FoundingStoneRenderer()));

        RenderHooks.registerWorldRenderer();
    }
}
