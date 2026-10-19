package ru.greatdragonx.astralspace.modules.energy.blocks;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import ru.greatdragonx.astralspace.modules.energy.EnergyBlockEntities;
import ru.greatdragonx.astralspace.modules.energy.EnergyBlockTypes;
import ru.greatdragonx.astralspace.modules.energy.blockentities.CoalGeneratorBlockEntity;
import ru.greatdragonx.astralspace.modules.energy.blocks.state.CoalGeneratorFrontState;

public class CoalGeneratorBlock extends BaseEntityBlock {
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final EnumProperty<CoalGeneratorFrontState> FRONT_STATE = EnumProperty.create(
        "front_state",
        CoalGeneratorFrontState.class
    );
    public static final BooleanProperty OUTPUT_BACK = BooleanProperty.create("output_back");
    public static final BooleanProperty OUTPUT_LEFT = BooleanProperty.create("output_left");
    public static final BooleanProperty OUTPUT_RIGHT = BooleanProperty.create("output_right");
    public static final BooleanProperty OUTPUT_TOP = BooleanProperty.create("output_top");
    public static final BooleanProperty OUTPUT_BOTTOM = BooleanProperty.create("output_bottom");

    public CoalGeneratorBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
            .setValue(
                FACING,
                Direction.NORTH
            )
            .setValue(
                FRONT_STATE,
                CoalGeneratorFrontState.OFF
            )
            .setValue(
                OUTPUT_BACK,
                true
            )
            .setValue(
                OUTPUT_BOTTOM,
                false
            )
            .setValue(
                OUTPUT_LEFT,
                false
            )
            .setValue(
                OUTPUT_RIGHT,
                false
            )
            .setValue(
                OUTPUT_TOP,
                false
            ));
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
    protected @NonNull InteractionResult useWithoutItem(
        @NonNull BlockState state,
        Level level,
        @NonNull BlockPos pos,
        @NonNull Player player,
        @NonNull BlockHitResult hitResult
    ) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.openMenu(state.getMenuProvider(
                level,
                pos
            ));
            return InteractionResult.SUCCESS;
        }
        
        return InteractionResult.PASS;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(
            FACING,
            FRONT_STATE,
            OUTPUT_BACK,
            OUTPUT_BOTTOM,
            OUTPUT_LEFT,
            OUTPUT_RIGHT,
            OUTPUT_TOP
        );
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState()
            .setValue(
                FACING,
                context.getHorizontalDirection()
                    .getOpposite()
            );
    }

    @Override
    protected @NonNull BlockState rotate(
        BlockState state,
        Rotation rotation
    ) {
        return state.setValue(
            FACING,
            rotation.rotate(state.getValue(FACING))
        );
    }

    @Override
    protected @NonNull BlockState mirror(
        @NonNull BlockState state,
        Mirror mirror
    ) {
        return this.rotate(
            state,
            mirror.getRotation(state.getValue(FACING))
        );
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
