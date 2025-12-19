package net.krona.politicsmod.client;

import net.krona.politicsmod.block.entity.FoundingStoneEntity;
import software.bernie.geckolib.renderer.GeoBlockRenderer;

public class FoundingStoneRenderer extends GeoBlockRenderer<FoundingStoneEntity> {
    public FoundingStoneRenderer() {
        super(new FoundingStoneModel());
    }
}