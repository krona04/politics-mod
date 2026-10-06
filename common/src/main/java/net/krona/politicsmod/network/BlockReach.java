package net.krona.politicsmod.network;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;

/** Server-side check that a packet refers to a real block the player can reach. */
public final class BlockReach {

    private static final double MAX_DISTANCE_SQR = 8 * 8;

    private BlockReach() {
    }

    public static boolean isAt(Player player, BlockPos pos, Block expected) {
        if (player == null || pos == null) return false;
        if (!player.level().isLoaded(pos)) return false;
        return player.level().getBlockState(pos).is(expected)
                && player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= MAX_DISTANCE_SQR;
    }
}
