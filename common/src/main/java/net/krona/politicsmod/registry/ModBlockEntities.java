package net.krona.politicsmod.registry;

import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import net.krona.politicsmod.Politicsmod;
import net.krona.politicsmod.block.entity.EmbassyEntity;
import net.krona.politicsmod.block.entity.FoundingStoneEntity;
import net.krona.politicsmod.block.entity.ResidentialBuildingEntity;
import net.krona.politicsmod.block.entity.VaultEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;

public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Politicsmod.MODID, Registries.BLOCK_ENTITY_TYPE);

    public static final RegistrySupplier<BlockEntityType<FoundingStoneEntity>> FOUNDING_STONE_BE =
            BLOCK_ENTITIES.register("founding_stone", () -> BlockEntityType.Builder
                    .of(FoundingStoneEntity::new, ModBlocks.FOUNDING_STONE.get()).build(null));

    public static final RegistrySupplier<BlockEntityType<ResidentialBuildingEntity>> RESIDENTIAL_BUILDING_BE =
            BLOCK_ENTITIES.register("residential_building", () -> BlockEntityType.Builder
                    .of(ResidentialBuildingEntity::new, ModBlocks.RESIDENTIAL_BUILDING.get()).build(null));

    public static final RegistrySupplier<BlockEntityType<EmbassyEntity>> EMBASSY_BE =
            BLOCK_ENTITIES.register("embassy_block", () -> BlockEntityType.Builder
                    .of(EmbassyEntity::new, ModBlocks.EMBASSY_BLOCK.get()).build(null));

    public static final RegistrySupplier<BlockEntityType<VaultEntity>> VAULT_BE =
            BLOCK_ENTITIES.register("vault_block", () -> BlockEntityType.Builder
                    .of(VaultEntity::new, ModBlocks.VAULT_BLOCK.get()).build(null));

    private ModBlockEntities() {
    }

    public static void register() {
        BLOCK_ENTITIES.register();
    }
}
