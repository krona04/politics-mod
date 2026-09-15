package net.krona.politicsmod;

import net.krona.politicsmod.market.MarketService;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

public class TradeWarehouseBlock extends Block {

    public TradeWarehouseBlock(Properties properties) {
        super(properties);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.isClientSide() || !(placer instanceof Player player)) return;

        PoliticsManager manager = PoliticsManager.get(level);
        if (manager == null) return;

        String owner = manager.getCountryNameAt(new ChunkPos(pos));
        String mine = manager.getPlayerCountry(player.getUUID());
        if (owner == null || !owner.equals(mine)) {
            player.displayClientMessage(Component.translatable("message.politicsmod.market.not_on_territory")
                    .withStyle(ChatFormatting.RED), true);
            level.destroyBlock(pos, !player.isCreative());
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            MarketService.open(serverPlayer, pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
