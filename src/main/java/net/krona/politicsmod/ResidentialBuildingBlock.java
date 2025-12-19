package net.krona.politicsmod;

import net.krona.politicsmod.block.entity.ResidentialBuildingEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

public class ResidentialBuildingBlock extends Block implements EntityBlock {

    public ResidentialBuildingBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!player.isCreative()) {
            player.displayClientMessage(Component.translatable("message.politicsmod.only_creative").withStyle(ChatFormatting.RED), true);
            return InteractionResult.FAIL;
        }

        if (level.isClientSide) {
            int pop = 0;
            if (level.getBlockEntity(pos) instanceof ResidentialBuildingEntity be) {
                pop = be.getPop();
            }
            net.minecraft.client.Minecraft.getInstance().setScreen(
                    new net.krona.politicsmod.client.ResidentialScreen(pos, pop)
            );
        }
        return InteractionResult.SUCCESS;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ResidentialBuildingEntity(pos, state);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }
}
