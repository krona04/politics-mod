package net.krona.politicsmod.item;

import net.krona.politicsmod.client.FoundingStoneItemRenderer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.item.BlockItem;
import net.minecraft.client.renderer.block.model.BlockModel;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.client.GeoRenderProvider;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.*;
import software.bernie.geckolib.util.GeckoLibUtil;
import java.util.function.Consumer;

public class FoundingStoneItem extends BlockItem implements GeoItem {
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public FoundingStoneItem(Block block, Properties properties) {
        super(block, properties);
    }

    // Подключаем рендерер предмета
    @Override
    public void createGeoRenderer(Consumer<GeoRenderProvider> consumer) {
        consumer.accept(new GeoRenderProvider() {
            private final FoundingStoneItemRenderer renderer = new FoundingStoneItemRenderer();

            @Override
            public software.bernie.geckolib.renderer.GeoItemRenderer<?> getGeoItemRenderer() {
                return renderer;
            }
        });
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "controller", 0, state ->
                state.setAndContinue(RawAnimation.begin().thenLoop("animation.sphere.idle"))
        ));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
