package net.krona.politicsmod.block;

import net.krona.politicsmod.PoliticsManager;
import net.krona.politicsmod.block.entity.EmbassyEntity;
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

public class EmbassyBlock extends Block implements EntityBlock {

    public EmbassyBlock(Properties properties) {
        super(properties);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new EmbassyEntity(pos, state);
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
                    String linked = country.embassyLinks.get(pos.asLong());
                    if (linked != null) {
                        boolean atWar = manager.isAtWar(countryName, linked);
                        int income = atWar ? 0 : PoliticsManager.EMBASSY_INCOME_BONUS;
                        player.sendSystemMessage(Component.translatable(
                                "gui.politicsmod.embassy.status_linked", linked, income)
                                .withStyle(atWar ? ChatFormatting.RED : ChatFormatting.GOLD));
                    } else {
                        player.sendSystemMessage(Component.translatable("gui.politicsmod.embassy.status_unlinked")
                                .withStyle(ChatFormatting.GRAY));
                    }
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
                    player.displayClientMessage(Component.translatable("message.politicsmod.embassy.not_on_territory").withStyle(ChatFormatting.RED), true);
                    level.destroyBlock(pos, true);
                } else {
                    Country country = manager.getCountry(countryName);
                    if (!country.embassyBlocks.contains(pos.asLong())) {
                        country.embassyBlocks.add(pos.asLong());
                        manager.setDirty();
                    }
                    player.displayClientMessage(Component.translatable("message.politicsmod.embassy.placed").withStyle(ChatFormatting.GREEN), true);
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
                    long posLong = pos.asLong();
                    if (country.embassyBlocks.remove(posLong)) {
                        country.embassyLinks.remove(posLong);
                        manager.setDirty();
                    }
                }
            }
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }
}
