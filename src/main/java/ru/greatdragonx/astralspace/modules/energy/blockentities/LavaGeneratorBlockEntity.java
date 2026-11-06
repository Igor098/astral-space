package ru.greatdragonx.astralspace.modules.energy.blockentities;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.model.data.ModelData;
import net.neoforged.neoforge.model.data.ModelProperty;
import org.jspecify.annotations.NonNull;
import ru.greatdragonx.astralspace.modules.energy.EnergyBlockEntities;
import ru.greatdragonx.astralspace.modules.energy.blocks.state.LavaGeneratorFrontState;

public class LavaGeneratorBlockEntity extends BlockEntity {
    private int lavaAmount;
    private boolean generating;

    public static final ModelProperty<LavaGeneratorFrontState> FRONT_STATE = new ModelProperty<>();

    private static final int LOW_LAVA_THRESHOLD = 1_000;

    public LavaGeneratorBlockEntity(
        BlockPos pos,
        BlockState state
    )
    {
        super(
            EnergyBlockEntities.LAVA_GENERATOR_BLOCK_ENTITY.get(),
            pos,
            state
        );
    }

    @Override
    public @NonNull ModelData getModelData() {
        return ModelData.builder()
            .with(
                FRONT_STATE,
                this.getFrontState()
            )
            .build();
    }

    @Override
    protected void saveAdditional(@NonNull ValueOutput output) {
        super.saveAdditional(output);

        output.putInt(
            "lava_amount",
            this.lavaAmount
        );
        output.putBoolean(
            "generating",
            this.generating
        );
    }

    @Override
    protected void loadAdditional(@NonNull ValueInput input) {
        super.loadAdditional(input);

        this.generating = input.getBooleanOr(
            "generating",
            false
        );

        this.lavaAmount = input.getIntOr(
            "lava_amount",
            0
        );
    }

    @Override
    public @NonNull CompoundTag getUpdateTag(
        HolderLookup.@NonNull Provider registries
    )
    {
        return this.saveWithoutMetadata(registries);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(
        @NonNull Connection connection,
        @NonNull ValueInput input
    )
    {
        LavaGeneratorFrontState previousState = this.getFrontState();
        super.onDataPacket(
            connection,
            input
        );
        LavaGeneratorFrontState currentState = this.getFrontState();

        if (previousState != currentState) {
            this.requestModelDataUpdate();

            Level level = this.getLevel();
            if (level != null && level.isClientSide()) {
                BlockState state = this.getBlockState();
                level.sendBlockUpdated(
                    worldPosition,
                    state,
                    state,
                    Block.UPDATE_CLIENTS
                );
            }
        }
    }

    @Override
    public void handleUpdateTag(@NonNull ValueInput input) {
        super.handleUpdateTag(input);
        this.requestModelDataUpdate();
    }

    public LavaGeneratorFrontState getFrontState() {
        if (!this.generating) {
            return LavaGeneratorFrontState.OFF;
        }

        if (this.lavaAmount <= LOW_LAVA_THRESHOLD) {
            return LavaGeneratorFrontState.LOW_LAVA;
        }

        return LavaGeneratorFrontState.RUNNING;
    }

    public void cycleDebugState() {
        switch (this.getFrontState()) {
            case OFF:
                this.generating = true;
                this.lavaAmount = 3000;
                break;
            case RUNNING:
                this.lavaAmount = 500;
                break;
            case LOW_LAVA:
                this.generating = false;
                break;
        }

        this.setChanged();
    }
}
