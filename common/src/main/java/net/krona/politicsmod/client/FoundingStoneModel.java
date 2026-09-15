package net.krona.politicsmod.client;

import net.krona.politicsmod.Politicsmod;
import net.krona.politicsmod.block.entity.FoundingStoneEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public class FoundingStoneModel extends GeoModel<FoundingStoneEntity> {
    @Override
    public ResourceLocation getModelResource(FoundingStoneEntity animatable) {
        return ResourceLocation.fromNamespaceAndPath(Politicsmod.MODID, "geo/founder_stone.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(FoundingStoneEntity animatable) {
        return ResourceLocation.fromNamespaceAndPath(Politicsmod.MODID, "textures/block/stone_texture.png");
    }

    @Override
    public ResourceLocation getAnimationResource(FoundingStoneEntity animatable) {
        return ResourceLocation.fromNamespaceAndPath(Politicsmod.MODID, "animations/stone.animation.json");
    }
}
