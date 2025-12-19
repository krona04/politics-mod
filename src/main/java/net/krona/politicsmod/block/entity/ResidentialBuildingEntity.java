package net.krona.politicsmod.block.entity;

import net.krona.politicsmod.Politicsmod;
import net.krona.politicsmod.PoliticsManager;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

// Убрали "implements GeoBlockEntity"
public class ResidentialBuildingEntity extends BlockEntity {

    // Размеры зоны
    private int rangeX = 5;
    private int rangeY = 5;
    private int rangeZ = 5;

    private int currentPopulation = 0;

    public ResidentialBuildingEntity(BlockPos pos, BlockState state) {
        super(Politicsmod.RESIDENTIAL_BUILDING_BE.get(), pos, state);
    }

    public void scanForPopulation() {
        if (level == null || level.isClientSide) return;

        int bedsFound = 0;
        int minX = worldPosition.getX() - rangeX;
        int minY = worldPosition.getY();
        int minZ = worldPosition.getZ() - rangeZ;
        int maxX = worldPosition.getX() + rangeX;
        int maxY = worldPosition.getY() + rangeY;
        int maxZ = worldPosition.getZ() + rangeZ;

        for (BlockPos pos : BlockPos.betweenClosed(minX, minY, minZ, maxX, maxY, maxZ)) {
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof BedBlock) {
                if (state.getValue(BedBlock.PART) == net.minecraft.world.level.block.state.properties.BedPart.HEAD) {
                    if (hasRoof(pos)) {
                        bedsFound += 2;
                    }
                }
            }
        }

        this.currentPopulation = bedsFound;
        setChanged();
        updatePoliticsManager();
    }

    private boolean hasRoof(BlockPos bedPos) {
        for (int i = 1; i <= 5; i++) {
            BlockPos checkPos = bedPos.above(i);
            BlockState state = level.getBlockState(checkPos);
            if (state.isSolidRender(level, checkPos)) {
                return true;
            }
        }
        return false;
    }

    private void updatePoliticsManager() {
        if (level instanceof ServerLevel) {
            PoliticsManager manager = PoliticsManager.get(level);
        }
    }

    public void setSize(int x, int y, int z) {
        this.rangeX = Math.min(Math.max(x, 1), 16);
        this.rangeY = Math.min(Math.max(y, 1), 32);
        this.rangeZ = Math.min(Math.max(z, 1), 16);
        setChanged();
        scanForPopulation();
    }

    public int getPop() { return currentPopulation; }
    public int getRX() { return rangeX; }
    public int getRY() { return rangeY; }
    public int getRZ() { return rangeZ; }

    @Override
    protected void saveAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider provider) {
        super.saveAdditional(tag, provider);
        tag.putInt("rx", rangeX);
        tag.putInt("ry", rangeY);
        tag.putInt("rz", rangeZ);
        tag.putInt("pop", currentPopulation);
    }

    @Override
    public void loadAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider provider) {
        super.loadAdditional(tag, provider);
        rangeX = tag.getInt("rx");
        rangeY = tag.getInt("ry");
        rangeZ = tag.getInt("rz");
        currentPopulation = tag.getInt("pop");
    }
}
