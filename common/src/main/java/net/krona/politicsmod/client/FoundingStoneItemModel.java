package net.krona.politicsmod.client;

import net.krona.politicsmod.Politicsmod;
import net.krona.politicsmod.item.FoundingStoneItem;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public class FoundingStoneItemModel extends GeoModel<FoundingStoneItem> {
    @Override
    public ResourceLocation getModelResource(FoundingStoneItem animatable) {
        return ResourceLocation.fromNamespaceAndPath(Politicsmod.MODID, "geo/founder_stone.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(FoundingStoneItem animatable) {
        return ResourceLocation.fromNamespaceAndPath(Politicsmod.MODID, "textures/block/stone_texture.png");
    }

    @Override
    public ResourceLocation getAnimationResource(FoundingStoneItem animatable) {
        return ResourceLocation.fromNamespaceAndPath(Politicsmod.MODID, "animations/stone.animation.json");
    }
}
