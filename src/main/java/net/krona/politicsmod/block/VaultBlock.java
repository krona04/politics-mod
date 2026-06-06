package net.krona.politicsmod.block;

import net.krona.politicsmod.PoliticsManager;
import net.krona.politicsmod.block.entity.VaultEntity;
import net.krona.politicsmod.politics.Country;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

public class VaultBlock extends Block implements EntityBlock {

    public VaultBlock(Properties properties) {
        super(properties);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new VaultEntity(pos, state);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide) {
            PoliticsManager manager = PoliticsManager.get(level);
            if (manager != null) {
                String countryName = manager.getCountryNameAt(new ChunkPos(pos));
                if (countryName != null) {
                    Country country = manager.getCountry(countryName);
                    int capBonus = PoliticsManager.VAULT_BALANCE_BONUS;
                    int totalLimit = PoliticsManager.BASE_MAX_BALANCE
                            + country.vaultBlocks.size() * capBonus;
                    int reserve = 0;
                    if (level.getBlockEntity(pos) instanceof VaultEntity be) {
                        reserve = be.getReserve();
                    }
                    player.sendSystemMessage(Component.translatable(
                            "gui.politicsmod.vault.status", reserve, capBonus, totalLimit)
                            .withStyle(ChatFormatting.AQUA));
                }
            }
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state,
                            @Nullable LivingEntity placer, ItemStack stack) {
        if (!level.isClientSide() && placer instanceof Player player) {
            PoliticsManager manager = PoliticsManager.get(level);
            if (manager != null) {
                ChunkPos chunk = new ChunkPos(pos);
                String countryName = manager.getCountryNameAt(chunk);
                if (countryName == null) {
                    player.displayClientMessage(Component.translatable("message.politicsmod.vault.not_on_territory").withStyle(ChatFormatting.RED), true);
                    level.destroyBlock(pos, true);
                } else {
                    Country country = manager.getCountry(countryName);
                    if (!country.vaultBlocks.contains(pos.asLong())) {
                        country.vaultBlocks.add(pos.asLong());
                        manager.setDirty();
                    }
                    int newLimit = PoliticsManager.BASE_MAX_BALANCE
                            + country.vaultBlocks.size() * PoliticsManager.VAULT_BALANCE_BONUS;
                    player.displayClientMessage(
                        Component.translatable("message.politicsmod.vault.placed", newLimit)
                            .withStyle(ChatFormatting.GREEN), true);
                }
            }
        }
        super.setPlacedBy(level, pos, state, placer, stack);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos,
                         BlockState newState, boolean isMoving) {
        if (!level.isClientSide() && !state.is(newState.getBlock())) {
            PoliticsManager manager = PoliticsManager.get(level);
            if (manager != null) {
                String countryName = manager.getCountryNameAt(new ChunkPos(pos));
                if (countryName != null) {
                    Country country = manager.getCountry(countryName);
                    country.vaultBlocks.remove(pos.asLong());
                    manager.setDirty();
                }
            }
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }
}
