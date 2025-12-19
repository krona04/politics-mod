package net.krona.politicsmod.client;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class FlagTextureManager {
    private static final Map<String, ResourceLocation> TEXTURE_CACHE = new HashMap<>();
    private static final Map<String, Boolean> LOADING_STATUS = new HashMap<>();

    public static ResourceLocation getTexture(String url) {
        if (url == null || url.isEmpty()) return null;

        if (TEXTURE_CACHE.containsKey(url)) {
            return TEXTURE_CACHE.get(url);
        }

        if (LOADING_STATUS.containsKey(url)) {
            return null;
        }

        startDownload(url);
        return null;
    }

    private static void startDownload(String urlString) {
        LOADING_STATUS.put(urlString, true);

        CompletableFuture.runAsync(() -> {
            try {
                URL url = new URL(urlString);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestProperty("User-Agent", "Mozilla/5.0"); // Чтобы сайты не блокировали
                connection.connect();

                try (InputStream stream = connection.getInputStream()) {
                    NativeImage image = NativeImage.read(stream);

                    Minecraft.getInstance().execute(() -> {
                        DynamicTexture texture = new DynamicTexture(image);
                        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("politicsmod", "flags/" + urlString.hashCode());

                        Minecraft.getInstance().getTextureManager().register(id, texture);
                        TEXTURE_CACHE.put(urlString, id);
                    });
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }
}
