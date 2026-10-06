package net.krona.politicsmod.neoforge.mixin;

import net.krona.politicsmod.protection.Protection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.FireBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Fire has no event on either loader. checkBurnOut is where fire destroys a neighbouring block
 * and spreads into it, so cancelling it stops fire from eating protected land.
 * NeoForge patches checkBurnOut with an extra Direction parameter, so this differs from the Fabric mixin.
 */
@Mixin(FireBlock.class)
public abstract class FireBlockMixin {

    @Inject(method = "checkBurnOut", at = @At("HEAD"), cancellable = true)
    private void politicsmod$protectFromFire(Level level, BlockPos pos, int chance, RandomSource random, int age, Direction face, CallbackInfo ci) {
        if (!Protection.fireAllowed(level, pos)) ci.cancel();
    }
}
