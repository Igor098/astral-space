package ru.greatdragonx.astralspace.modules.energy.blockentities;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.model.data.ModelData;
import net.neoforged.neoforge.model.data.ModelProperty;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import org.jetbrains.annotations.Unmodifiable;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import ru.greatdragonx.astralspace.common.energy.port.*;
import ru.greatdragonx.astralspace.common.fluid.port.*;
import ru.greatdragonx.astralspace.common.fluid.storage.FluidTankDefinition;
import ru.greatdragonx.astralspace.common.fluid.storage.FluidTankId;
import ru.greatdragonx.astralspace.common.fluid.storage.FluidTankRole;
import ru.greatdragonx.astralspace.common.fluid.storage.MachineFluidStorage;
import ru.greatdragonx.astralspace.common.machine.MachineSide;
import ru.greatdragonx.astralspace.common.machine.MachineSideDirections;
import ru.greatdragonx.astralspace.common.machine.visual.VisualPort;
import ru.greatdragonx.astralspace.common.machine.visual.VisualPortId;
import ru.greatdragonx.astralspace.common.machine.visual.VisualPortType;
import ru.greatdragonx.astralspace.modules.energy.EnergyBlockEntities;
import ru.greatdragonx.astralspace.modules.energy.blocks.LavaGeneratorBlock;
import ru.greatdragonx.astralspace.modules.energy.blocks.state.LavaGeneratorFrontState;

import java.util.*;

public class LavaGeneratorBlockEntity extends BlockEntity {
    private int lavaAmount;
    private boolean generating;

    public static final ModelProperty<LavaGeneratorFrontState> FRONT_STATE = new ModelProperty<>();
    public static final ModelProperty<List<VisualPort>> VISUAL_PORTS = new ModelProperty<>();
    private static final FluidTankId LAVA_TANK_ID = new FluidTankId("lava_input");
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
    private static final Map<MachineSide, FluidPortId> FLUID_PORT_IDS = Map.of(
        MachineSide.BACK,
        new FluidPortId("lava_generator.back"),
        MachineSide.LEFT,
        new FluidPortId("lava_generator.left"),
        MachineSide.RIGHT,
        new FluidPortId("lava_generator.right"),
        MachineSide.TOP,
        new FluidPortId("lava_generator.top"),
        MachineSide.BOTTOM,
        new FluidPortId("lava_generator.bottom")
    );
    private static final List<FluidTankDefinition> FLUID_TANK_DEFINITIONS =
        List.of(new FluidTankDefinition(
            LAVA_TANK_ID,
            FluidTankRole.INPUT,
            8_000,
            resource -> resource.equals(FluidResource.of(Fluids.LAVA))
        ));
    private static final FluidPortRoute ROUTE = new FluidPortRoute(
        List.of(LAVA_TANK_ID),
        List.of(),
        List.of(LAVA_TANK_ID)
    );
    private static final FluidPortRouting FLUID_PORT_ROUTING = new FluidPortRouting(
        Map.of(
            FLUID_PORT_IDS.get(MachineSide.BACK),
            ROUTE,
            FLUID_PORT_IDS.get(MachineSide.LEFT),
            ROUTE,
            FLUID_PORT_IDS.get(MachineSide.RIGHT),
            ROUTE,
            FLUID_PORT_IDS.get(MachineSide.TOP),
            ROUTE,
            FLUID_PORT_IDS.get(MachineSide.BOTTOM),
            ROUTE
        ),
        FLUID_TANK_DEFINITIONS
    );
    private static final int LOW_LAVA_THRESHOLD = 1_000;

    private final EnergyPortConfiguration energyPortConfiguration;
    private final EnergyPortPolicy energyPortPolicy;
    private final MachineFluidStorage fluidStorage;
    private final FluidPortPolicy fluidPortPolicy;
    private final FluidPortConfiguration fluidPortConfiguration;
    private final Map<MachineSide, FluidPortHandler> fluidPortHandlers;

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

        this.fluidStorage = new MachineFluidStorage(
            FLUID_TANK_DEFINITIONS,
            this::setChanged
        );

        Map<FluidPortId, FluidPortMode> initialFluidModes = new HashMap<>();
        for (Map.Entry<MachineSide, FluidPortId> entry : FLUID_PORT_IDS.entrySet()) {
            MachineSide side = entry.getKey();
            FluidPortId portId = entry.getValue();

            FluidPortMode mode =
                side == MachineSide.RIGHT ? FluidPortMode.INPUT : FluidPortMode.DISABLED;

            initialFluidModes.put(
                portId,
                mode
            );
        }

        this.fluidPortConfiguration = new FluidPortConfiguration(
            initialFluidModes,
            this::onPortConfigurationChanged
        );

        this.fluidPortPolicy = new ConfigurableFluidPortPolicy(
            createAllowedFluidModes(),
            this.fluidPortConfiguration::getMode,
            portId -> true
        );

        Map<MachineSide, FluidPortHandler> handlers =
            new EnumMap<MachineSide, FluidPortHandler>(MachineSide.class);
        for (Map.Entry<MachineSide, FluidPortId> entry : FLUID_PORT_IDS.entrySet()) {
            MachineSide side = entry.getKey();
            FluidPortId portId = entry.getValue();

            FluidPortHandler handler = new FluidPortHandler(
                portId,
                this.fluidPortPolicy,
                FLUID_PORT_ROUTING,
                this.fluidStorage
            );

            handlers.put(
                side,
                handler
            );
        }

        this.fluidPortHandlers = Map.copyOf(handlers);

        Map<EnergyPortId, EnergyPortMode> initialEnergyModes = new HashMap<>();
        for (Map.Entry<MachineSide, EnergyPortId> entry : ENERGY_PORT_IDS.entrySet()) {
            MachineSide side = entry.getKey();
            EnergyPortId portId = entry.getValue();

            EnergyPortMode mode =
                side == MachineSide.BACK ? EnergyPortMode.OUTPUT : EnergyPortMode.DISABLED;

            initialEnergyModes.put(
                portId,
                mode
            );
        }

        this.energyPortConfiguration = new EnergyPortConfiguration(
            initialEnergyModes,
            this::onPortConfigurationChanged
        );

        this.energyPortPolicy = new ConfigurableEnergyPortPolicy(
            createAllowedEnergyModes(),
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
            .with(
                VISUAL_PORTS,
                this.createVisualPortsSnapshot()
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

        this.saveEnergyPorts(output);
        this.saveFluidPorts(output);
        this.fluidStorage.serialize(output.child("fluid_storage"));
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
        this.loadFluidPorts(input);
        this.fluidStorage.deserialize(input.childOrEmpty("fluid_storage"));
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
        Set<VisualPort> previousPortsState = Set.copyOf(this.createVisualPortsSnapshot());
        super.onDataPacket(
            connection,
            input
        );
        LavaGeneratorFrontState currentState = this.getFrontState();
        Set<VisualPort> currentPortsState = Set.copyOf(this.createVisualPortsSnapshot());

        boolean hasChanged =
            !previousPortsState.equals(currentPortsState) || previousState != currentState;

        if (hasChanged) {
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
        Objects.requireNonNull(
            side,
            "side cannot be null"
        );

        EnergyPortId portId = ENERGY_PORT_IDS.get(side);
        if (portId == null) {
            return EnergyPortMode.DISABLED;
        }

        return this.energyPortConfiguration.getMode(portId);
    }

    public FluidPortMode getFluidPortMode(
        @NonNull MachineSide side
    )
    {
        Objects.requireNonNull(
            side,
            "side cannot be null"
        );
        FluidPortId portId = FLUID_PORT_IDS.get(side);
        if (portId == null) {
            return FluidPortMode.DISABLED;
        }

        return this.fluidPortConfiguration.getMode(portId);
    }

    public boolean changeEnergyPortMode(
        @NonNull MachineSide side,
        @NonNull EnergyPortMode newMode
    )
    {
        Objects.requireNonNull(
            side,
            "side cannot be null"
        );

        Objects.requireNonNull(
            newMode,
            "newMode cannot be null"
        );

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

    public boolean changeFluidPortMode(
        @NonNull MachineSide side,
        @NonNull FluidPortMode newMode
    )
    {
        Objects.requireNonNull(
            side,
            "side cannot be null"
        );

        Objects.requireNonNull(
            newMode,
            "newMode cannot be null"
        );

        if (this.level == null || this.level.isClientSide()) {
            return false;
        }

        FluidPortId portId = FLUID_PORT_IDS.get(side);
        if (portId == null) {
            return false;
        }

        return this.fluidPortConfiguration.changeMode(
            portId,
            newMode,
            this.fluidPortPolicy
        );
    }

    public @Nullable FluidPortHandler getFluidPortHandlerForWorldSide(
        @Nullable Direction worldSide
    )
    {
        if (worldSide == null) {
            return null;
        }

        Direction facing = this.getBlockState()
            .getValue(LavaGeneratorBlock.FACING);

        MachineSide side = MachineSideDirections.fromWorld(
            worldSide,
            facing
        );
        
        return this.getFluidPortHandler(side)
            .orElse(null);
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

    public Optional<FluidPortHandler> getFluidPortHandler(
        @NonNull MachineSide side
    )
    {
        Objects.requireNonNull(
            side,
            "side cannot be null"
        );

        return Optional.ofNullable(this.fluidPortHandlers.get(side));
    }

    private static @NonNull Map<EnergyPortId, Set<EnergyPortMode>> createAllowedEnergyModes() {
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

    private static @NonNull Map<FluidPortId, Set<FluidPortMode>> createAllowedFluidModes()
    {
        Map<FluidPortId, Set<FluidPortMode>> allowedModes = new HashMap<>();

        Set<FluidPortMode> modes = Set.of(
            FluidPortMode.INPUT,
            FluidPortMode.SERVICE,
            FluidPortMode.DISABLED
        );

        for (FluidPortId portId : FLUID_PORT_IDS.values()) {
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

    private void saveFluidPorts(
        @NonNull ValueOutput output
    )
    {
        ValueOutput portsOutput = output.child("fluid_ports");

        Map<FluidPortId, FluidPortMode> snapshot = this.fluidPortConfiguration.snapshot();
        for (Map.Entry<FluidPortId, FluidPortMode> entry : snapshot.entrySet()) {
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

    private void loadFluidPorts(
        @NonNull ValueInput input
    )
    {
        ValueInput portsInput = input.childOrEmpty("fluid_ports");
        Map<FluidPortId, FluidPortMode> restoredModes = new HashMap<>();

        for (FluidPortId portId : FLUID_PORT_IDS.values()) {
            Optional<String> savedMode = portsInput.getString(portId.value());

            if (savedMode.isEmpty()) {
                continue;
            }

            try {
                FluidPortMode mode = FluidPortMode.valueOf(savedMode.get());

                restoredModes.put(
                    portId,
                    mode
                );
            } catch (IllegalArgumentException ignored) {
            }
        }

        this.fluidPortConfiguration.restoreModes(
            restoredModes,
            this.fluidPortPolicy
        );
    }

    private void onPortConfigurationChanged() {
        this.setChanged();

        if (this.level != null && !this.level.isClientSide()) {
            this.level.sendBlockUpdated(
                this.worldPosition,
                this.getBlockState(),
                this.getBlockState(),
                Block.UPDATE_CLIENTS
            );
        }
    }

    private @NonNull @Unmodifiable List<VisualPort> createVisualPortsSnapshot() {
        List<VisualPort> visualPorts = new ArrayList<>();

        for (Map.Entry<MachineSide, EnergyPortId> entry : ENERGY_PORT_IDS.entrySet()) {
            MachineSide side = entry.getKey();
            EnergyPortId portId = entry.getValue();

            EnergyPortMode mode = this.getEnergyPortMode(side);
            if (mode == EnergyPortMode.DISABLED) {
                continue;
            }

            VisualPortId visualPortId = new VisualPortId("energy." + portId.value());

            visualPorts.add(new VisualPort(
                visualPortId,
                VisualPortType.ENERGY,
                side
            ));
        }

        for (Map.Entry<MachineSide, FluidPortId> entry : FLUID_PORT_IDS.entrySet()) {
            MachineSide side = entry.getKey();
            FluidPortId portId = entry.getValue();

            FluidPortMode mode = this.getFluidPortMode(side);
            if (mode == FluidPortMode.DISABLED) {
                continue;
            }

            VisualPortId visualPortId = new VisualPortId("fluid." + portId.value());
            visualPorts.add(new VisualPort(
                visualPortId,
                VisualPortType.FLUID,
                side
            ));
        }

        return List.copyOf(visualPorts);
    }
}
