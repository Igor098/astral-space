package ru.greatdragonx.astralspace.modules.energy.blocks;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import ru.greatdragonx.astralspace.modules.energy.EnergyBlockEntities;
import ru.greatdragonx.astralspace.modules.energy.EnergyBlockTypes;
import ru.greatdragonx.astralspace.modules.energy.blockentities.CoalGeneratorBlockEntity;

public class CoalGeneratorBlock extends BaseEntityBlock {
    public CoalGeneratorBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(
        @NonNull BlockPos pos,
        @NonNull BlockState state
    ) {
        return new CoalGeneratorBlockEntity(
            pos,
            state
        );
    }

    @Override
    protected @NonNull MapCodec<? extends BaseEntityBlock> codec() {
        return EnergyBlockTypes.COAL_GENERATOR.get();
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(
        Level level,
        @NonNull BlockState state,
        @NonNull BlockEntityType<T> blockEntityType
    ) {
        if (level.isClientSide()) return null;

        return CoalGeneratorBlock.createTickerHelper(
            blockEntityType,
            EnergyBlockEntities.COAL_GENERATOR_BLOCK_ENTITY.get(),
            CoalGeneratorBlockEntity::serverTick
        );
    }
}
