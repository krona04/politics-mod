package net.krona.politicsmod.block;

import net.krona.politicsmod.PoliticsManager;
import net.krona.politicsmod.politics.Country;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.ChunkPos;
import org.jetbrains.annotations.Nullable;

public class TaxBlock extends Block {

    public TaxBlock(Properties properties) {
        super(properties);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        if (!level.isClientSide() && placer instanceof Player player) {
            PoliticsManager manager = PoliticsManager.get(level);
            if (manager != null) {
                ChunkPos currentChunk = new ChunkPos(pos);
                String countryName = manager.getCountryNameAt(currentChunk);

                if (countryName != null) {
                    Country country = manager.getCountry(countryName);

                    int MAX_BLOCKS_PER_CHUNK = 4;
                    int blocksInThisChunk = 0;

                    for (Long savedPosLong : country.taxBlocks) {
                        BlockPos savedPos = BlockPos.of(savedPosLong);
                        ChunkPos savedChunk = new ChunkPos(savedPos);

                        if (savedChunk.equals(currentChunk)) {
                            blocksInThisChunk++;
                        }
                    }

                    if (blocksInThisChunk >= MAX_BLOCKS_PER_CHUNK) {
                        player.displayClientMessage(Component.translatable("message.politicsmod.tax.chunk_limit", MAX_BLOCKS_PER_CHUNK).withStyle(ChatFormatting.RED), true);
                        level.destroyBlock(pos, true);
                    } else {
                        if (!country.taxBlocks.contains(pos.asLong())) {
                            country.taxBlocks.add(pos.asLong());
                            manager.setDirty();
                            player.displayClientMessage(Component.translatable("message.politicsmod.tax.registered", blocksInThisChunk + 1, MAX_BLOCKS_PER_CHUNK).withStyle(ChatFormatting.GREEN), true);
                        }
                    }
                } else {
                    player.displayClientMessage(Component.translatable("message.politicsmod.tax.not_on_territory").withStyle(ChatFormatting.RED), true);
                    level.destroyBlock(pos, true);
                }
            }
        }
        super.setPlacedBy(level, pos, state, placer, stack);
    }
    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!level.isClientSide() && !state.is(newState.getBlock())) {
            PoliticsManager manager = PoliticsManager.get(level);
            if (manager != null) {
                String countryName = manager.getCountryNameAt(new ChunkPos(pos));
                if (countryName != null) {
                    Country country = manager.getCountry(countryName);
                    if (country.taxBlocks.remove(pos.asLong())) {
                        manager.setDirty();
                    }
                }
            }
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }
}
