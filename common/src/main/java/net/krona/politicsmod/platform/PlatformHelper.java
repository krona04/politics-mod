package net.krona.politicsmod.platform;

import dev.architectury.injectables.annotations.ExpectPlatform;

import java.nio.file.Path;

/**
 * Abstraction for what Architectury API does not provide.
 *
 * How @ExpectPlatform works: at build time Architectury replaces the call
 * with a static method from the Impl class in the platform subpackage:
 *   net.krona.politicsmod.platform.PlatformHelper
 *     -> net.krona.politicsmod.platform.fabric.PlatformHelperImpl
 *     -> net.krona.politicsmod.platform.neoforge.PlatformHelperImpl
 *
 * Package and class names must match EXACTLY, otherwise it crashes at runtime.
 * The method body in common never runs, hence the AssertionError.
 */
public final class PlatformHelper {

    private PlatformHelper() {
    }

    @ExpectPlatform
    public static Path getConfigDirectory() {
        throw new AssertionError("@ExpectPlatform was not replaced - check the Impl class package");
    }

    @ExpectPlatform
    public static boolean isModLoaded(String modId) {
        throw new AssertionError("@ExpectPlatform was not replaced - check the Impl class package");
    }

    @ExpectPlatform
    public static boolean isDevelopmentEnvironment() {
        throw new AssertionError("@ExpectPlatform was not replaced - check the Impl class package");
    }
}
