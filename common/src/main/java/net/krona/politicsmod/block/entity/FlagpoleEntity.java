package net.krona.politicsmod.block.entity;

import net.krona.politicsmod.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Holds no data: it exists so the flag can be drawn by a block entity renderer. */
public class FlagpoleEntity extends BlockEntity {

    public FlagpoleEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.FLAGPOLE_BE.get(), pos, state);
    }
}
