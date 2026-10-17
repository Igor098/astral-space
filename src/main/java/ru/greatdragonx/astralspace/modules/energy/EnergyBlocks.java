package ru.greatdragonx.astralspace.modules.energy;

import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;
import ru.greatdragonx.astralspace.AstralSpace;
import ru.greatdragonx.astralspace.modules.energy.blocks.CoalGeneratorBlock;

public final class EnergyBlocks {
    public static final DeferredRegister.Blocks BLOCKS =
        DeferredRegister.createBlocks(AstralSpace.MOD_ID);
    public static final DeferredBlock<CoalGeneratorBlock> COAL_GENERATOR = BLOCKS.registerBlock(
        "coal_generator",
        CoalGeneratorBlock::new,
        () -> BlockBehaviour.Properties.of()
            .destroyTime(5.0f)
            .explosionResistance(6.0f)
            .sound(SoundType.METAL)
            .requiresCorrectToolForDrops()
    );


    private EnergyBlocks() {
    }
}
