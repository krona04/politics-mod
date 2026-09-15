package net.krona.politicsmod.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

public class ModKeyMappings {

    public static final KeyMapping OPEN_COUNTRY_MENU = new KeyMapping(
            "key.politicsmod.open_menu",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_K,
            "key.categories.politicsmod"
    );
}
