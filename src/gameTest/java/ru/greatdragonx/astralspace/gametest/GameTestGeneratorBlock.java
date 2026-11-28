package ru.greatdragonx.astralspace.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import org.jspecify.annotations.NonNull;

/**
 * Real block/BE lifecycle, but no automatic production ticker.
 */
final class GameTestGeneratorBlock extends Block implements EntityBlock {
    static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    static final BooleanProperty MARKER = BooleanProperty.create("marker");

    GameTestGeneratorBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
            .setValue(FACING, Direction.NORTH)
            .setValue(MARKER, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.@NonNull Builder<Block, BlockState> builder) {
        builder.add(FACING, MARKER);
    }

    @Override
    public @NonNull BlockEntity newBlockEntity(@NonNull BlockPos pos, @NonNull BlockState state) {
        return new GameTestGeneratorBlockEntity(pos, state);
    }
    // EntityBlock's default getTicker returns null. Tests explicitly call tickOnce().
}
