package net.krona.politicsmod.block.entity;

import net.krona.politicsmod.Politicsmod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class EmbassyEntity extends BlockEntity {

    private String linkedCountry = "";

    public EmbassyEntity(BlockPos pos, BlockState state) {
        super(Politicsmod.EMBASSY_BE.get(), pos, state);
    }

    public String getLinkedCountry() { return linkedCountry; }

    public void setLinkedCountry(String country) {
        this.linkedCountry = (country == null) ? "" : country;
        setChanged();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.saveAdditional(tag, provider);
        tag.putString("linkedCountry", linkedCountry);
    }

    @Override
    public void loadAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.loadAdditional(tag, provider);
        linkedCountry = tag.contains("linkedCountry") ? tag.getString("linkedCountry") : "";
    }
}
