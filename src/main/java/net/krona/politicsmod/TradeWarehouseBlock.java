package net.krona.politicsmod;

import net.krona.politicsmod.block.entity.TradeWarehouseEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

public class TradeWarehouseBlock extends Block implements EntityBlock {

    public TradeWarehouseBlock(Properties properties) {
        super(properties);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TradeWarehouseEntity(pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!player.isCreative()) {
            player.displayClientMessage(Component.translatable("message.politicsmod.only_creative").withStyle(ChatFormatting.RED), true);
            return InteractionResult.FAIL;
        }

        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof TradeWarehouseEntity warehouse) {
                serverPlayer.openMenu(warehouse, pos);
            }
        }
        return InteractionResult.SUCCESS;
    }
}
