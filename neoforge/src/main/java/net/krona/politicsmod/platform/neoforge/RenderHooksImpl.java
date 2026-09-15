package net.krona.politicsmod.platform.neoforge;

import net.krona.politicsmod.client.BorderRenderer;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;

/** NeoForge implementation: the same RenderLevelStageEvent the original BorderRenderer used. */
public final class RenderHooksImpl {

    private RenderHooksImpl() {
    }

    public static void registerWorldRenderer() {
        NeoForge.EVENT_BUS.addListener(RenderHooksImpl::onRenderLevel);
    }

    private static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            BorderRenderer.render(event.getPoseStack(), event.getCamera());
        }
    }
}
