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
import ru.greatdragonx.astralspace.common.energy.port.*;
import ru.greatdragonx.astralspace.common.machine.MachineSide;
import ru.greatdragonx.astralspace.modules.energy.EnergyBlockEntities;
import ru.greatdragonx.astralspace.modules.energy.blocks.state.LavaGeneratorFrontState;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class LavaGeneratorBlockEntity extends BlockEntity {
    private int lavaAmount;
    private boolean generating;

    public static final ModelProperty<LavaGeneratorFrontState> FRONT_STATE = new ModelProperty<>();
    private static final Map<MachineSide, EnergyPortId> ENERGY_PORT_IDS = Map.of(
        MachineSide.BACK,
        new EnergyPortId("lava_generator.back"),
        MachineSide.LEFT,
        new EnergyPortId("lava_generator.left"),
        MachineSide.RIGHT,
        new EnergyPortId("lava_generator.right"),
        MachineSide.TOP,
        new EnergyPortId("lava_generator.top"),
        MachineSide.BOTTOM,
        new EnergyPortId("lava_generator.bottom")
    );

    private static final int LOW_LAVA_THRESHOLD = 1_000;

    private final EnergyPortConfiguration energyPortConfiguration;
    private final EnergyPortPolicy energyPortPolicy;

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

        Map<EnergyPortId, EnergyPortMode> initialModes = new HashMap<>();
        for (Map.Entry<MachineSide, EnergyPortId> entry : ENERGY_PORT_IDS.entrySet()) {
            MachineSide side = entry.getKey();
            EnergyPortId portId = entry.getValue();

            EnergyPortMode mode =
                side == MachineSide.BACK ? EnergyPortMode.OUTPUT : EnergyPortMode.DISABLED;

            initialModes.put(
                portId,
                mode
            );
        }

        this.energyPortConfiguration = new EnergyPortConfiguration(
            initialModes,
            this::setChanged
        );

        this.energyPortPolicy = new ConfigurableEnergyPortPolicy(
            createAllowedModes(),
            this.energyPortConfiguration::getMode,
            portId -> true
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

        this.loadEnergyPorts(input);
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

    public EnergyPortMode getEnergyPortMode(@NonNull MachineSide side) {
        EnergyPortId portId = ENERGY_PORT_IDS.get(side);
        if (portId == null) {
            return EnergyPortMode.DISABLED;
        }

        return this.energyPortConfiguration.getMode(portId);
    }

    public boolean changeEnergyPortMode(
        @NonNull MachineSide side,
        @NonNull EnergyPortMode newMode
    )
    {
        if (this.level == null || this.level.isClientSide()) {
            return false;
        }

        EnergyPortId portId = ENERGY_PORT_IDS.get(side);
        if (portId == null) {
            return false;
        }

        return this.energyPortConfiguration.changeMode(
            portId,
            newMode,
            this.energyPortPolicy
        );
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

    private static @NonNull Map<EnergyPortId, Set<EnergyPortMode>> createAllowedModes() {
        Map<EnergyPortId, Set<EnergyPortMode>> allowedModes = new HashMap<>();

        Set<EnergyPortMode> modes = Set.of(
            EnergyPortMode.OUTPUT,
            EnergyPortMode.DISABLED
        );

        for (EnergyPortId portId : ENERGY_PORT_IDS.values()) {
            allowedModes.put(
                portId,
                modes
            );
        }

        return allowedModes;
    }

    private void saveEnergyPorts(@NonNull ValueOutput output) {
        ValueOutput portsOutput = output.child("energy_ports");

        Map<EnergyPortId, EnergyPortMode> snapshot = this.energyPortConfiguration.snapshot();
        for (Map.Entry<EnergyPortId, EnergyPortMode> entry : snapshot.entrySet()) {
            portsOutput.putString(
                entry.getKey()
                    .value(),
                entry.getValue()
                    .name()
            );
        }
    }

    private void loadEnergyPorts(@NonNull ValueInput input) {
        ValueInput portsInput = input.childOrEmpty("energy_ports");
        Map<EnergyPortId, EnergyPortMode> restoredModes = new HashMap<>();

        for (EnergyPortId portId : ENERGY_PORT_IDS.values()) {
            Optional<String> savedMode = portsInput.getString(portId.value());

            if (savedMode.isEmpty()) {
                continue;
            }

            try {
                EnergyPortMode mode = EnergyPortMode.valueOf(savedMode.get());

                restoredModes.put(
                    portId,
                    mode
                );
            } catch (IllegalArgumentException ignored) {
            }
        }

        this.energyPortConfiguration.restoreModes(
            restoredModes,
            this.energyPortPolicy
        );
    }
}
