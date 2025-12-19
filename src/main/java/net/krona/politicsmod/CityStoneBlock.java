package net.krona.politicsmod;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public class CityStoneBlock extends Block {
    public CityStoneBlock(Properties properties) { super(properties); }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!player.isCreative()) {
            player.displayClientMessage(Component.translatable("message.politicsmod.only_creative").withStyle(ChatFormatting.RED), true);
            return InteractionResult.FAIL;
        }

        if (level.isClientSide) {
            net.minecraft.client.Minecraft.getInstance().setScreen(new net.krona.politicsmod.client.CityFoundationScreen(pos));
        }
        return InteractionResult.SUCCESS;
    }
}
