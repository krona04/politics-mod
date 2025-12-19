package net.krona.politicsmod;

import net.krona.politicsmod.block.entity.FoundingStoneEntity;
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

public class FoundingStoneBlock extends Block implements EntityBlock {

    public FoundingStoneBlock(Properties properties) {
        super(properties);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new FoundingStoneEntity(pos, state);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!player.isCreative()) {
            player.displayClientMessage(Component.translatable("message.politicsmod.only_creative").withStyle(ChatFormatting.RED), true);
            return InteractionResult.FAIL;
        }

        if (!level.isClientSide) {
            net.krona.politicsmod.PoliticsManager manager = net.krona.politicsmod.PoliticsManager.get(level);
            String existingCountry = manager.getCountryByOwner(player.getUUID());

            if (existingCountry != null) {
                return InteractionResult.FAIL;
            } else {
                player.sendSystemMessage(Component.translatable("message.politicsmod.founding_stone_hint").withStyle(ChatFormatting.GRAY));
                return InteractionResult.SUCCESS;
            }
        }

        if (level.isClientSide) {
            net.minecraft.client.Minecraft.getInstance().setScreen(new net.krona.politicsmod.client.FoundingStoneScreen(pos));
        }

        return InteractionResult.SUCCESS;
    }
}