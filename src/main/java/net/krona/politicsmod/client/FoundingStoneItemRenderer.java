package net.krona.politicsmod.client;

import net.krona.politicsmod.item.FoundingStoneItem;
import software.bernie.geckolib.renderer.GeoItemRenderer;

public class FoundingStoneItemRenderer extends GeoItemRenderer<FoundingStoneItem> {
    public FoundingStoneItemRenderer() {
        super(new FoundingStoneItemModel());
    }
}
