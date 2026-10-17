package ru.greatdragonx.astralspace.modules.energy;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredRegister;
import ru.greatdragonx.astralspace.AstralSpace;
import ru.greatdragonx.astralspace.modules.energy.blockentities.CoalGeneratorBlockEntity;

import java.util.function.Supplier;

public final class EnergyBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
        DeferredRegister.create(
            Registries.BLOCK_ENTITY_TYPE,
            AstralSpace.MOD_ID
        );
    public static final Supplier<BlockEntityType<CoalGeneratorBlockEntity>>
        COAL_GENERATOR_BLOCK_ENTITY = BLOCK_ENTITY_TYPES.register(
        "coal_generator_entity",
        () -> new BlockEntityType<>(
            CoalGeneratorBlockEntity::new,
            false,
            EnergyBlocks.COAL_GENERATOR.get()
        )
    );

    private EnergyBlockEntities() {
    }
}
