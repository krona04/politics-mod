package net.krona.politicsmod.block;

import net.krona.politicsmod.PoliticsManager;
import net.krona.politicsmod.politics.Country;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.ChunkPos;
import org.jetbrains.annotations.Nullable;

public class RadarBlock extends Block {

    public RadarBlock(Properties properties) {
        super(properties);
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
                    player.displayClientMessage(Component.translatable("message.politicsmod.radar.not_on_territory").withStyle(ChatFormatting.RED), true);
                    level.destroyBlock(pos, true);
                } else {
                    Country country = manager.getCountry(countryName);
                    if (!country.radarBlocks.contains(pos.asLong())) {
                        country.radarBlocks.add(pos.asLong());
                        manager.setDirty();
                    }
                    player.displayClientMessage(
                        Component.translatable("message.politicsmod.radar.placed", PoliticsManager.RADAR_RANGE_CHUNKS)
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
                    country.radarBlocks.remove(pos.asLong());
                    manager.setDirty();
                }
            }
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }
}
