package net.krona.politicsmod.client;

import net.minecraft.client.Camera;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import java.util.Map;

/**
 * Renders chunk borders. Called from the platform RenderHooksImpl
 * after entities are drawn (NeoForge: RenderLevelStageEvent, Fabric: WorldRenderEvents).
 */
public class BorderRenderer {

    public static void render(PoseStack poseStack, Camera camera) {
        if (poseStack == null || camera == null) return;
        if (!ClientPoliticsData.shouldRender()) return;

        var chunks = ClientPoliticsData.getChunks();
        if (chunks.isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        Vec3 cameraPos = camera.getPosition();
        poseStack.pushPose();
        poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);

        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.lineWidth(4.0f);
        RenderSystem.enableDepthTest();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        VertexConsumer builder = mc.renderBuffers().bufferSource().getBuffer(RenderType.lines());
        Matrix4f matrix = poseStack.last().pose();

        float minY = (float) mc.level.getMinBuildHeight();
        float maxY = (float) mc.level.getMaxBuildHeight();
        int style = ClientPoliticsData.borderStyle;

        for (Map.Entry<ChunkPos, Integer> entry : chunks.entrySet()) {
            ChunkPos pos = entry.getKey();
            int color = entry.getValue();

            checkAndDrawEdge(builder, matrix, chunks, pos, 0, -1, minY, maxY, color, style); // North
            checkAndDrawEdge(builder, matrix, chunks, pos, 0, 1, minY, maxY, color, style);  // South
            checkAndDrawEdge(builder, matrix, chunks, pos, -1, 0, minY, maxY, color, style); // West
            checkAndDrawEdge(builder, matrix, chunks, pos, 1, 0, minY, maxY, color, style);  // East
        }

        RenderSystem.disableBlend();
        poseStack.popPose();
    }

    private static void checkAndDrawEdge(VertexConsumer builder, Matrix4f matrix, Map<ChunkPos, Integer> chunks,
                                         ChunkPos current, int dx, int dz, float minY, float maxY, int currentColor, int style) {
        ChunkPos neighbor = new ChunkPos(current.x + dx, current.z + dz);
        int neighborColor = chunks.getOrDefault(neighbor, -1);

        if (currentColor != neighborColor) {
            float r = ((currentColor >> 16) & 0xFF) / 255.0f;
            float g = ((currentColor >> 8) & 0xFF) / 255.0f;
            float b = (currentColor & 0xFF) / 255.0f;

            if (style == 0) {
                drawEdgeLine(builder, matrix, current, dx, dz, minY, maxY, r, g, b, 1.0f);
            }
            else if (style == 1) {
                for(float y = minY; y < maxY; y += 10) {
                    drawEdgeLine(builder, matrix, current, dx, dz, y, y, r, g, b, 0.5f);
                }
                drawEdgeLine(builder, matrix, current, dx, dz, minY, maxY, r, g, b, 1.0f);
            }
            else if (style == 2) {
                float h = minY + 3;
                if (h > maxY) h = maxY;
                drawEdgeLine(builder, matrix, current, dx, dz, minY, h, r, g, b, 1.0f);
            }
        }
    }

    private static void drawEdgeLine(VertexConsumer builder, Matrix4f matrix, ChunkPos chunk, int dx, int dz, float y1, float y2, float r, float g, float b, float a) {
        float x1 = chunk.getMinBlockX(); float z1 = chunk.getMinBlockZ();
        float x2 = chunk.getMaxBlockX() + 1; float z2 = chunk.getMaxBlockZ() + 1;
        float dx1, dz1, dx2, dz2;

        if (dx == 0 && dz == -1) { dx1=x1; dz1=z1; dx2=x2; dz2=z1; }
        else if (dx == 0 && dz == 1) { dx1=x1; dz1=z2; dx2=x2; dz2=z2; }
        else if (dx == -1 && dz == 0) { dx1=x1; dz1=z1; dx2=x1; dz2=z2; }
        else { dx1=x2; dz1=z1; dx2=x2; dz2=z2; }

        builder.addVertex(matrix, dx1, y1, dz1).setColor(r, g, b, a).setNormal(0, 1, 0);
        builder.addVertex(matrix, dx2, y2, dz2).setColor(r, g, b, a).setNormal(0, 1, 0);
    }
}
