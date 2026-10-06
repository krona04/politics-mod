package net.krona.politicsmod.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.krona.politicsmod.block.FlagpoleBlock;
import net.krona.politicsmod.block.entity.FlagpoleEntity;
import net.krona.politicsmod.politics.Country;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;

/**
 * Draws a waving flag on a flagpole: the image of the country that owns the chunk,
 * or plain cloth in the country's map colour while it has no image. Nothing on unclaimed land.
 */
public class FlagpoleRenderer implements BlockEntityRenderer<FlagpoleEntity> {
    private static final ResourceLocation CLOTH = ResourceLocation.withDefaultNamespace("textures/block/white_wool.png");
    private static final int SEGMENTS = 12;
    private static final float WIDTH = 1.2f, TOP = 0.92f, BOTTOM = 0.28f, POLE = 0.07f; // just under the finial, touching the pole

    public FlagpoleRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(FlagpoleEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        ClientPoliticsData.ChunkInfo info = ClientPoliticsData.getChunkInfo(new ChunkPos(be.getBlockPos()));
        if (info == null || be.getLevel() == null) return;

        String url = ClientPoliticsData.getFlagUrl(info.country());
        ResourceLocation texture = url.isEmpty() ? null : FlagTextureManager.getTexture(url);
        int color = 0xFFFFFF;
        if (texture == null) {
            texture = CLOTH;
            color = Country.mapColor(info.country());
        }
        float r = (color >> 16 & 0xFF) / 255f, g = (color >> 8 & 0xFF) / 255f, b = (color & 0xFF) / 255f;

        Direction facing = be.getBlockState().getValue(FlagpoleBlock.FACING);
        float time = (be.getLevel().getGameTime() + partialTick) * 0.12f + (be.getBlockPos().asLong() % 100);

        pose.pushPose();
        pose.translate(0.5, 0, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(-facing.toYRot()));
        PoseStack.Pose p = pose.last();
        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(texture));

        for (int i = 0; i < SEGMENTS; i++) {
            float u0 = i / (float) SEGMENTS, u1 = (i + 1) / (float) SEGMENTS;
            float x0 = POLE + u0 * WIDTH, x1 = POLE + u1 * WIDTH;
            // The wave grows towards the free end of the cloth
            float z0 = Mth.sin(time + u0 * 6f) * 0.07f * u0;
            float z1 = Mth.sin(time + u1 * 6f) * 0.07f * u1;
            float nx = -(z1 - z0), nz = x1 - x0;
            float len = Mth.sqrt(nx * nx + nz * nz);
            nx /= len;
            nz /= len;
            vertex(vc, p, x0, TOP, z0, u0, 0, r, g, b, light, overlay, nx, nz);
            vertex(vc, p, x0, BOTTOM, z0, u0, 1, r, g, b, light, overlay, nx, nz);
            vertex(vc, p, x1, BOTTOM, z1, u1, 1, r, g, b, light, overlay, nx, nz);
            vertex(vc, p, x1, TOP, z1, u1, 0, r, g, b, light, overlay, nx, nz);
        }
        pose.popPose();
    }

    private static void vertex(VertexConsumer vc, PoseStack.Pose p, float x, float y, float z, float u, float v,
                               float r, float g, float b, int light, int overlay, float nx, float nz) {
        vc.addVertex(p, x, y, z).setColor(r, g, b, 1f).setUv(u, v).setOverlay(overlay).setLight(light).setNormal(p, nx, 0, nz);
    }

    @Override
    public boolean shouldRenderOffScreen(FlagpoleEntity be) {
        return true; // the cloth reaches past the block
    }
}
