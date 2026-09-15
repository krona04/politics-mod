package net.krona.politicsmod.platform;

import dev.architectury.injectables.annotations.ExpectPlatform;

/**
 * Chunk border rendering in the world (BorderRenderer).
 *
 * This is the only part of the mod with no Architectury API equivalent:
 *   NeoForge: RenderLevelStageEvent (Stage.AFTER_ENTITIES)
 *   Fabric:   WorldRenderEvents.AFTER_ENTITIES (Fabric API)
 *
 * So registration lives in the platform Impl classes.
 */
public final class RenderHooks {

    private RenderHooks() {
    }

    @ExpectPlatform
    public static void registerWorldRenderer() {
        throw new AssertionError("@ExpectPlatform was not replaced - check the Impl class package");
    }
}
