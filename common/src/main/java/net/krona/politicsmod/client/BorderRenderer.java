package net.krona.politicsmod.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.krona.politicsmod.client.ui.UiKit;
import net.krona.politicsmod.network.CountryDetailsPayload;
import net.krona.politicsmod.politics.Country;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Country and city borders in the world. Called from the platform RenderHooksImpl after translucent blocks
 * (water, glass) are drawn (NeoForge: RenderLevelStageEvent, Fabric: WorldRenderEvents).
 *
 * Borders lie on the ground instead of standing as columns from bedrock to the sky: a coloured strip
 * along the surface with a line on its edge, optionally a low glowing fence, drawn only near the player
 * and fading out with distance. Styles (/politicsmod borders style): 0 = strip, 1 = strip and fence, 2 = dashed.
 */
public final class BorderRenderer {
    private static final int RADIUS_CHUNKS = 8;
    private static final float FADE_START = 40f, FADE_END = 120f;
    /** Just above the ground so grass and snow don't hide it. */
    private static final float LIFT = 0.04f;
    /** Each side's border is drawn inside its own chunk, so two neighbouring countries both show their colour. */
    private static final float INSET = 0.06f;
    private static final float STRIP = 0.45f;
    private static final float FENCE_HEIGHT = 2.2f;

    private static final int[][] DIRS = {{0, -1}, {0, 1}, {-1, 0}, {1, 0}};

    /** The view transform of the frame being drawn. */
    private static PoseStack.Pose pose;

    private BorderRenderer() {
    }

    /** A quad drawn after the lines: four corners, one colour, alpha at the first two and the last two corners. */
    private record Quad(float[] xyz, int color, int alphaBottom, int alphaTop) {
    }

    public static void render(PoseStack ignored, Camera camera) {
        if (camera == null || !ClientPoliticsData.shouldRender()) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        // Countries own land only in the Overworld
        if (mc.player == null || level == null || level.dimension() != Level.OVERWORLD) return;
        Map<ChunkPos, ClientPoliticsData.ChunkInfo> chunks = ClientPoliticsData.getChunkInfo();
        if (chunks.isEmpty()) return;

        Vec3 cam = camera.getPosition();
        ChunkPos center = new ChunkPos(BlockPos.containing(cam));
        int radius = Math.min(RADIUS_CHUNKS, mc.options.getEffectiveRenderDistance());
        int style = ClientPoliticsData.borderStyle;
        Map<String, String> relations = relations();

        // Positions relative to the camera; the camera rotation is already in the model-view matrix at this
        // point of the frame. The buffers are flushed right away so the strip draws over the lines' state.
        PoseStack poseStack = new PoseStack();
        poseStack.translate(-cam.x, -cam.y, -cam.z);
        pose = poseStack.last();

        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        List<Quad> quads = new ArrayList<>();

        for (int cx = center.x - radius; cx <= center.x + radius; cx++) {
            for (int cz = center.z - radius; cz <= center.z + radius; cz++) {
                ChunkPos pos = new ChunkPos(cx, cz);
                ClientPoliticsData.ChunkInfo info = chunks.get(pos);
                if (info == null || !level.hasChunk(cx, cz)) continue;
                for (int[] d : DIRS) {
                    ClientPoliticsData.ChunkInfo other = chunks.get(new ChunkPos(cx + d[0], cz + d[1]));
                    if (other != null && other.country().equals(info.country()) && other.city().equals(info.city())) continue;
                    // Between two cities of one country the border is fainter and has no fence
                    boolean cityEdge = other != null && other.country().equals(info.country());
                    edge(lines, quads, level, cam, pos, d[0], d[1], colorOf(info, relations), cityEdge, style);
                }
            }
        }
        buffers.endBatch(RenderType.lines());

        if (!quads.isEmpty()) drawQuads(quads);
    }

    /**
     * The strip and the fence are see-through, so they must not write depth: otherwise water, rain and particles
     * behind them are cut out and the sea floor shows through a hole in the water.
     */
    private static void drawQuads(List<Quad> quads) {
        BufferBuilder vc = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (Quad q : quads) {
            int r = q.color >> 16 & 0xFF, g = q.color >> 8 & 0xFF, b = q.color & 0xFF;
            float[] p = q.xyz;
            vc.addVertex(pose, p[0], p[1], p[2]).setColor(r, g, b, q.alphaBottom);
            vc.addVertex(pose, p[3], p[4], p[5]).setColor(r, g, b, q.alphaBottom);
            vc.addVertex(pose, p[6], p[7], p[8]).setColor(r, g, b, q.alphaTop);
            vc.addVertex(pose, p[9], p[10], p[11]).setColor(r, g, b, q.alphaTop);
        }
        MeshData mesh = vc.build();
        if (mesh == null) return;
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        BufferUploader.drawWithShader(mesh);
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    // ── Geometry ─────────────────────────────────────────────────────────

    /** Start corner of the border on the side facing (dx, dz), the step along it, and the inward direction. */
    private static float[] side(ChunkPos c, int dx, int dz) {
        float x0 = c.getMinBlockX(), z0 = c.getMinBlockZ();
        if (dz == -1) return new float[]{x0, z0 + INSET, 1, 0, 0, 1};
        if (dz == 1) return new float[]{x0, z0 + 16 - INSET, 1, 0, 0, -1};
        if (dx == -1) return new float[]{x0 + INSET, z0, 0, 1, 1, 0};
        return new float[]{x0 + 16 - INSET, z0, 0, 1, -1, 0};
    }

    private static void edge(VertexConsumer lines, List<Quad> quads, ClientLevel level, Vec3 cam, ChunkPos c,
                             int dx, int dz, int color, boolean cityEdge, int style) {
        float[] s = side(c, dx, dz);
        float r = (color >> 16 & 0xFF) / 255f, g = (color >> 8 & 0xFF) / 255f, b = (color & 0xFF) / 255f;
        float lineAlpha = cityEdge ? 0.55f : 0.95f;
        float stripAlpha = cityEdge ? 0.3f : 0.55f;
        // The strip is the country colour lightened towards white, so it stands out on grass of any shade
        int light = lighten(color, 0.35f);
        boolean dashed = style == 2;
        float prev = Float.NaN;
        for (int i = 0; i < 16; i++) {
            float x1 = s[0] + s[2] * i, z1 = s[1] + s[3] * i;
            float x2 = x1 + s[2], z2 = z1 + s[3];
            float fade = fade(cam, (x1 + x2) / 2, (z1 + z2) / 2);
            float y = ground(level, c, dx, dz, i) + LIFT;
            if (fade > 0.02f && (!dashed || i % 2 == 0)) {
                line(lines, x1, y, z1, x2, y, z2, r, g, b, lineAlpha * fade);
                // A flat strip on the inner side of the line, fading towards the inside
                float ix = s[4] * STRIP, iz = s[5] * STRIP;
                int a = (int) (stripAlpha * fade * 255);
                quads.add(new Quad(new float[]{x1, y, z1, x2, y, z2, x2 + ix, y, z2 + iz, x1 + ix, y, z1 + iz}, light, a, a / 4));
                if (style == 1 && !cityEdge) {
                    int fa = (int) (0.45f * fade * 255);
                    quads.add(new Quad(new float[]{x1, y, z1, x2, y, z2, x2, y + FENCE_HEIGHT, z2, x1, y + FENCE_HEIGHT, z1}, color, fa, 0));
                }
            }
            // Steps up and down the terrain keep the line connected
            if (!dashed && fade > 0.02f && !Float.isNaN(prev) && prev != y) {
                line(lines, x1, prev, z1, x1, y, z1, r, g, b, lineAlpha * fade);
            }
            prev = y;
        }
    }

    private static int lighten(int color, float amount) {
        int r = color >> 16 & 0xFF, g = color >> 8 & 0xFF, b = color & 0xFF;
        r += (int) ((255 - r) * amount);
        g += (int) ((255 - g) * amount);
        b += (int) ((255 - b) * amount);
        return r << 16 | g << 8 | b;
    }

    /** Height of the ground on the border at block i: the higher of the two sides, so it is never buried. */
    private static float ground(ClientLevel level, ChunkPos c, int dx, int dz, int i) {
        int bx, bz;
        if (dz != 0) {
            bx = c.getMinBlockX() + i;
            bz = dz < 0 ? c.getMinBlockZ() : c.getMaxBlockZ();
        } else {
            bz = c.getMinBlockZ() + i;
            bx = dx < 0 ? c.getMinBlockX() : c.getMaxBlockX();
        }
        int inside = surface(level, bx, bz);
        int ox = bx + dx, oz = bz + dz;
        int outside = level.hasChunk(ox >> 4, oz >> 4) ? surface(level, ox, oz) : inside;
        return Math.max(inside, outside);
    }

    private static final Map<Long, Integer> SURFACE = new HashMap<>();
    private static long surfaceEpoch = -1;

    /**
     * Top of the ground at a column, under leaves and tree trunks. The client has no "no leaves" heightmap
     * (it stays empty), so this starts from MOTION_BLOCKING and walks down. Cached for two seconds.
     */
    private static int surface(ClientLevel level, int x, int z) {
        long epoch = level.getGameTime() / 40;
        if (epoch != surfaceEpoch) {
            SURFACE.clear();
            surfaceEpoch = epoch;
        }
        long key = ((long) x << 32) | (z & 0xFFFFFFFFL);
        Integer cached = SURFACE.get(key);
        if (cached != null) return cached;
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
        int min = level.getMinBuildHeight();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int steps = 0; steps < 48 && y > min; steps++) {
            BlockState state = level.getBlockState(pos.set(x, y - 1, z));
            boolean passThrough = state.is(BlockTags.LEAVES) || state.is(BlockTags.LOGS)
                    || (state.getCollisionShape(level, pos).isEmpty() && state.getFluidState().isEmpty());
            if (!passThrough) break;
            y--;
        }
        SURFACE.put(key, y);
        return y;
    }

    private static float fade(Vec3 cam, float x, float z) {
        double dist = Math.sqrt((x - cam.x) * (x - cam.x) + (z - cam.z) * (z - cam.z));
        if (dist <= FADE_START) return 1f;
        return (float) Math.max(0, 1 - (dist - FADE_START) / (FADE_END - FADE_START));
    }

    private static void line(VertexConsumer lines, float x1, float y1, float z1, float x2, float y2, float z2,
                             float r, float g, float b, float a) {
        // The lines shader widens each segment sideways from its direction, passed as the normal
        float nx = x2 - x1, ny = y2 - y1, nz = z2 - z1;
        float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (len < 1e-4f) return;
        nx /= len;
        ny /= len;
        nz /= len;
        lines.addVertex(pose, x1, y1, z1).setColor(r, g, b, a).setNormal(pose, nx, ny, nz);
        lines.addVertex(pose, x2, y2, z2).setColor(r, g, b, a).setNormal(pose, nx, ny, nz);
    }

    // ── Colours: the same as on the dashboard map ───────────────────────

    private static Map<String, String> relations() {
        Map<String, String> map = new HashMap<>();
        CountryDetailsPayload d = ClientPoliticsData.countryDetails;
        if (d != null) for (CountryDetailsPayload.Relation r : d.relations()) map.put(r.country(), r.status());
        return map;
    }

    private static int colorOf(ClientPoliticsData.ChunkInfo info, Map<String, String> relations) {
        if (info.country().equals(ClientPoliticsData.myCountry)) {
            return (info.city().isEmpty() ? UiKit.MAP_OWN : UiKit.ACCENT) & 0xFFFFFF;
        }
        return switch (relations.getOrDefault(info.country(), "")) {
            case "WAR" -> UiKit.MAP_ENEMY & 0xFFFFFF;
            case "ALLIANCE" -> UiKit.MAP_ALLY & 0xFFFFFF;
            default -> Country.mapColor(info.country());
        };
    }
}
