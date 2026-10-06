package net.krona.politicsmod.client;

import com.mojang.blaze3d.platform.NativeImage;
import net.krona.politicsmod.Politicsmod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Downloads flag pictures by URL and keeps them as textures. Accepts PNG, JPEG, GIF and BMP, and links to an
 * image page (Imgur, Wikimedia and most other hosts) by taking the picture from the page's og:image tag.
 */
public class FlagTextureManager {
    private static final Map<String, ResourceLocation> TEXTURE_CACHE = new HashMap<>();
    /** URL → time the download started; a failed download may be retried after RETRY_MS. */
    private static final Map<String, Long> LOADING = new HashMap<>();
    private static final long RETRY_MS = 30_000;
    private static final int MAX_BYTES = 4 * 1024 * 1024;
    /** Flags are small on screen; big photos are scaled down so they don't eat video memory. */
    private static final int MAX_SIZE = 512;
    private static final int MAX_REDIRECTS = 5;

    private static final Pattern META_IMAGE = Pattern.compile(
            "<meta[^>]+(?:property|name)\\s*=\\s*[\"'](?:og:image(?::secure_url)?|twitter:image(?::src)?)[\"'][^>]*>",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern CONTENT = Pattern.compile("content\\s*=\\s*[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);

    public static ResourceLocation getTexture(String url) {
        if (url == null || url.isEmpty()) return null;
        ResourceLocation cached = TEXTURE_CACHE.get(url);
        if (cached != null) return cached;

        Long started = LOADING.get(url);
        if (started != null && System.currentTimeMillis() - started < RETRY_MS) return null;
        LOADING.put(url, System.currentTimeMillis());
        startDownload(url);
        return null;
    }

    private static void startDownload(String urlString) {
        CompletableFuture.runAsync(() -> {
            try {
                byte[] data = fetch(urlString);
                if (looksLikeHtml(data)) {
                    // A link to a page with the picture, not to the picture itself
                    String image = pageImage(new String(data, StandardCharsets.UTF_8));
                    if (image == null) throw new IOException("the link is a web page without a picture");
                    data = fetch(URI.create(urlString).resolve(image).toString());
                }
                NativeImage image = decode(data);
                Minecraft.getInstance().execute(() -> {
                    ResourceLocation id = ResourceLocation.fromNamespaceAndPath("politicsmod",
                            "flags/" + Integer.toHexString(urlString.hashCode()));
                    Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(image));
                    TEXTURE_CACHE.put(urlString, id);
                });
            } catch (Exception e) {
                Politicsmod.LOGGER.warn("Could not load flag {}: {}", urlString, e.toString());
            }
        });
    }

    /** GET with redirects followed by hand: HttpURLConnection does not follow http → https on its own. */
    private static byte[] fetch(String urlString) throws IOException {
        String current = urlString;
        for (int i = 0; i <= MAX_REDIRECTS; i++) {
            URL url = URI.create(current).toURL();
            if (!"https".equals(url.getProtocol()) && !"http".equals(url.getProtocol())) {
                throw new IOException("only http(s) links are allowed");
            }
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setInstanceFollowRedirects(false);
            // Some hosts reject requests without a browser-like User-Agent
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) PoliticsMod");
            // Without this Java asks for text/html first, and Imgur then redirects a direct picture link to its web page
            connection.setRequestProperty("Accept", "image/png,image/jpeg,image/gif,image/bmp;q=0.9,*/*;q=0.1");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(10000);
            int code = connection.getResponseCode();
            if (code >= 300 && code < 400) {
                String location = connection.getHeaderField("Location");
                connection.disconnect();
                if (location == null) throw new IOException("redirect without a location");
                current = URI.create(current).resolve(location).toString();
                continue;
            }
            if (code != HttpURLConnection.HTTP_OK) {
                connection.disconnect();
                throw new IOException("HTTP " + code);
            }
            try (InputStream stream = connection.getInputStream()) {
                // Flags are small pictures; refuse anything big enough to hurt the client
                byte[] data = stream.readNBytes(MAX_BYTES + 1);
                if (data.length > MAX_BYTES) throw new IOException("the picture is larger than 4 MB");
                return data;
            }
        }
        throw new IOException("too many redirects");
    }

    private static boolean looksLikeHtml(byte[] data) {
        int i = 0;
        // Skip a UTF-8 byte order mark and leading whitespace
        if (data.length >= 3 && (data[0] & 0xFF) == 0xEF && (data[1] & 0xFF) == 0xBB && (data[2] & 0xFF) == 0xBF) i = 3;
        while (i < data.length && Character.isWhitespace(data[i])) i++;
        return i < data.length && data[i] == '<';
    }

    static String pageImage(String html) {
        Matcher tag = META_IMAGE.matcher(html);
        while (tag.find()) {
            Matcher content = CONTENT.matcher(tag.group());
            if (content.find()) return content.group(1).replace("&amp;", "&").trim();
        }
        return null;
    }

    private static NativeImage decode(byte[] data) throws IOException {
        BufferedImage picture = ImageIO.read(new ByteArrayInputStream(data));
        if (picture == null) {
            // Not something ImageIO knows (WebP, SVG...); PNG still has a second chance through stb
            try {
                return NativeImage.read(data);
            } catch (IOException e) {
                throw new IOException("unsupported picture format, use PNG or JPEG");
            }
        }
        int w = picture.getWidth(), h = picture.getHeight();
        float scale = Math.min(1f, (float) MAX_SIZE / Math.max(w, h));
        int tw = Math.max(1, Math.round(w * scale)), th = Math.max(1, Math.round(h * scale));
        NativeImage image = new NativeImage(tw, th, false);
        for (int y = 0; y < th; y++) {
            int sy = Math.min(h - 1, (int) (y / scale));
            for (int x = 0; x < tw; x++) {
                int argb = picture.getRGB(Math.min(w - 1, (int) (x / scale)), sy);
                // NativeImage stores ABGR
                int abgr = (argb & 0xFF00FF00) | (argb >> 16 & 0xFF) | (argb & 0xFF) << 16;
                image.setPixelRGBA(x, y, abgr);
            }
        }
        return image;
    }
}
