package net.krona.politicsmod.platform.fabric;

import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Path;

/**
 * Fabric implementation of PlatformHelper.
 * NOTE: the package and class name must be exactly
 * <PlatformHelper package>.fabric.PlatformHelperImpl
 */
public final class PlatformHelperImpl {

    private PlatformHelperImpl() {
    }

    public static Path getConfigDirectory() {
        return FabricLoader.getInstance().getConfigDir();
    }

    public static boolean isModLoaded(String modId) {
        return FabricLoader.getInstance().isModLoaded(modId);
    }

    public static boolean isDevelopmentEnvironment() {
        return FabricLoader.getInstance().isDevelopmentEnvironment();
    }
}
