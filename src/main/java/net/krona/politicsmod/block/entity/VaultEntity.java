package net.krona.politicsmod.block.entity;

import net.krona.politicsmod.Politicsmod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class VaultEntity extends BlockEntity {

    private int reserve = 0;

    public VaultEntity(BlockPos pos, BlockState state) {
        super(Politicsmod.VAULT_BE.get(), pos, state);
    }

    public int getReserve() { return reserve; }

    public void deposit(int amount) {
        reserve += amount;
        setChanged();
    }

    public int withdraw(int amount) {
        int actual = Math.min(amount, reserve);
        reserve -= actual;
        setChanged();
        return actual;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.saveAdditional(tag, provider);
        tag.putInt("reserve", reserve);
    }

    @Override
    public void loadAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.loadAdditional(tag, provider);
        reserve = tag.contains("reserve") ? tag.getInt("reserve") : 0;
    }
}
