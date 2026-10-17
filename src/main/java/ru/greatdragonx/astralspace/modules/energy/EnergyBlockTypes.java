package ru.greatdragonx.astralspace.modules.energy;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.neoforge.registries.DeferredRegister;
import ru.greatdragonx.astralspace.AstralSpace;
import ru.greatdragonx.astralspace.modules.energy.blocks.CoalGeneratorBlock;

import java.util.function.Supplier;

public final class EnergyBlockTypes {
    public static final DeferredRegister<MapCodec<? extends Block>> BLOCK_TYPES =
        DeferredRegister.create(
            BuiltInRegistries.BLOCK_TYPE,
            AstralSpace.MOD_ID
        );
    public static final Supplier<MapCodec<CoalGeneratorBlock>> COAL_GENERATOR =
        BLOCK_TYPES.register(
            "coal_generator",
            () -> BlockBehaviour.simpleCodec(CoalGeneratorBlock::new)
        );

    private EnergyBlockTypes() {
    }
}
