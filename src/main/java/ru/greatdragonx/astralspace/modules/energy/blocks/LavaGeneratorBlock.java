package ru.greatdragonx.astralspace.modules.energy.blocks;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.NonNull;
import ru.greatdragonx.astralspace.common.energy.port.EnergyPortMode;
import ru.greatdragonx.astralspace.common.machine.MachineSide;
import ru.greatdragonx.astralspace.modules.energy.EnergyBlockTypes;
import ru.greatdragonx.astralspace.modules.energy.blockentities.LavaGeneratorBlockEntity;

public class LavaGeneratorBlock extends BaseEntityBlock {
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;

    public LavaGeneratorBlock(BlockBehaviour.Properties properties) {
        super(properties);

        this.registerDefaultState(this.stateDefinition.any()
            .setValue(
                FACING,
                Direction.NORTH
            ));
    }

    @Override
    protected @NonNull MapCodec<? extends BaseEntityBlock> codec() {
        return EnergyBlockTypes.LAVA_GENERATOR.get();
    }

    @Override
    public @NonNull LavaGeneratorBlockEntity newBlockEntity(
        @NonNull BlockPos pos,
        @NonNull BlockState state
    )
    {
        return new LavaGeneratorBlockEntity(
            pos,
            state
        );
    }

    @Override
    protected void createBlockStateDefinition(
        StateDefinition.@NonNull Builder<Block, BlockState> builder
    )
    {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(
        @NonNull BlockPlaceContext context
    )
    {
        return this.defaultBlockState()
            .setValue(
                FACING,
                context.getHorizontalDirection()
                    .getOpposite()
            );
    }

    @Override
    protected @NonNull BlockState rotate(
        @NonNull BlockState state,
        @NonNull Rotation rotation
    )
    {
        return state.setValue(
            FACING,
            rotation.rotate(state.getValue(FACING))
        );
    }

    @Override
    protected @NonNull BlockState mirror(
        @NonNull BlockState state,
        @NonNull Mirror mirror
    )
    {
        return this.rotate(
            state,
            mirror.getRotation(state.getValue(FACING))
        );
    }

    @Override
    protected @NonNull InteractionResult useWithoutItem(
        @NonNull BlockState state,
        @NonNull Level level,
        @NonNull BlockPos pos,
        @NonNull Player player,
        @NonNull BlockHitResult hitResult
    )
    {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof LavaGeneratorBlockEntity entity) {
            if (player.isShiftKeyDown()) {
                EnergyPortMode currentMode = entity.getEnergyPortMode(MachineSide.BACK);
                EnergyPortMode mode =
                    currentMode == EnergyPortMode.DISABLED ? EnergyPortMode.OUTPUT :
                        EnergyPortMode.DISABLED;
                boolean changed = entity.changeEnergyPortMode(
                    MachineSide.BACK,
                    mode
                );

                if (changed) {
                    player.displayClientMessage(
                        Component.literal("Энергетический порт BACK: " + mode.name()),
                        false
                    );
                } else {
                    player.displayClientMessage(
                        Component.literal("Не удалось изменить режим порта"),
                        false
                    );
                }

                return InteractionResult.SUCCESS;
            }
            entity.cycleDebugState();
            level.sendBlockUpdated(
                pos,
                state,
                state,
                Block.UPDATE_CLIENTS
            );
            return InteractionResult.SUCCESS;
        }

        return InteractionResult.PASS;
    }
}
