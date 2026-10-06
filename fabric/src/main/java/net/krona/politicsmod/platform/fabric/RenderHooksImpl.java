package net.krona.politicsmod.platform.fabric;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.krona.politicsmod.client.BorderRenderer;

/** Fabric equivalent of RenderLevelStageEvent(AFTER_TRANSLUCENT_BLOCKS): WorldRenderEvents.AFTER_TRANSLUCENT. */
public final class RenderHooksImpl {

    private RenderHooksImpl() {
    }

    public static void registerWorldRenderer() {
        WorldRenderEvents.AFTER_TRANSLUCENT.register(context ->
                BorderRenderer.render(context.matrixStack(), context.camera()));
    }
}
