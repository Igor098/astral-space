package ru.greatdragonx.astralspace.modules.energy.blockentities;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.model.data.ModelData;
import net.neoforged.neoforge.model.data.ModelProperty;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.jetbrains.annotations.Unmodifiable;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import ru.greatdragonx.astralspace.common.energy.*;
import ru.greatdragonx.astralspace.common.energy.component.EnergyComponent;
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
import ru.greatdragonx.astralspace.modules.energy.menus.LavaGeneratorMenu;

import java.util.*;

public class LavaGeneratorBlockEntity extends BlockEntity implements MenuProvider {
    private static final int LOW_LAVA_THRESHOLD = 1_000;
    public static final int LAVA_CAPACITY = 8_000;
    public static final int ENERGY_CAPACITY = 120_000;
    public static final int ENERGY_GENERATION_PER_TICK = 60;
    public static final int MAX_EXTRACT = 120;

    public static final int LAVA_PER_CYCLE = 100;
    public static final int BURN_DURATION_TICKS = 500;

    public static final int LAVA_TANK_INDEX = 0;

    public static final int DATA_LAVA_STORED = 0;
    public static final int DATA_ENERGY_LOW = 1;
    public static final int DATA_ENERGY_HIGH = 2;
    public static final int DATA_BURN_TIME = 3;
    public static final int DATA_BURN_DURATION = 4;
    public static final int DATA_FRONT_STATE = 5;
    public static final int DATA_PRODUCTION = 6;
    public static final int DATA_TRANSFER = 7;
    public static final int DATA_COUNT = 8;

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
            LAVA_CAPACITY,
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

    private final EnergyPortConfiguration energyPortConfiguration;
    private final EnergyPortPolicy energyPortPolicy;
    private final MachineFluidStorage fluidStorage;
    private final FluidPortPolicy fluidPortPolicy;
    private final FluidPortConfiguration fluidPortConfiguration;
    private final Map<MachineSide, FluidPortHandler> fluidPortHandlers;
    private final EnergyComponent energyComponent;
    private final EnergyTransferTracker transferTracker;
    private final EnergyTransferBudget extractionBudget;
    private final EnergyDistributionManager distributionManager;
    private final EnergyTransferCoordinator transferCoordinator;
    private final EnergyPushTransfer energyPushTransfer;
    private final Map<EnergyPortId, EnergyPortHandler> outputHandlers = new HashMap<>();

    private int burnTime;
    private int burnDuration;
    private int currentProductionRate;
    private LavaGeneratorFrontState frontState = LavaGeneratorFrontState.OFF;

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case DATA_LAVA_STORED -> LavaGeneratorBlockEntity.this.getLavaStored();
                case DATA_ENERGY_LOW -> (int) (
                    LavaGeneratorBlockEntity.this.energyComponent.getStoredEnergy() & 0xFFFFL
                );
                case DATA_ENERGY_HIGH -> (int) (
                    (LavaGeneratorBlockEntity.this.energyComponent.getStoredEnergy() >>> 16) &
                        0xFFFFL
                );
                case DATA_BURN_TIME -> LavaGeneratorBlockEntity.this.burnTime;
                case DATA_BURN_DURATION -> LavaGeneratorBlockEntity.this.burnDuration;
                case DATA_FRONT_STATE -> LavaGeneratorBlockEntity.this.frontState.ordinal();
                case DATA_PRODUCTION -> LavaGeneratorBlockEntity.this.getCurrentProductionRate();
                case DATA_TRANSFER -> LavaGeneratorBlockEntity.this.getCurrentTransferRate();
                default -> 0;
            };
        }

        @Override
        public void set(
            int index,
            int value
        )
        {
            // Изменение данных через GUI не предусмотрено.
        }

        @Override
        public int getCount() {
            return DATA_COUNT;
        }
    };

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

        this.energyComponent = new EnergyComponent(
            ENERGY_CAPACITY,
            this::setChanged
        );

        this.transferTracker = new EnergyTransferTracker();
        this.extractionBudget = new EnergyTransferBudget(MAX_EXTRACT);
        this.distributionManager = new EnergyDistributionManager(MAX_EXTRACT);

        EnergyTransferBudget insertionBudget = new EnergyTransferBudget(1);
        this.transferCoordinator = new EnergyTransferCoordinator(
            this.energyComponent,
            this.distributionManager,
            insertionBudget,
            this.extractionBudget,
            this.transferTracker,
            () -> Objects.requireNonNull(
                    this.level,
                    "Generator level is not initialized"
                )
                .getGameTime()
        );

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

        this.energyPushTransfer = new EnergyPushTransfer(
            this.transferCoordinator,
            this.energyPortPolicy
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
        this.syncAvailableEnergyPorts();
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
            "burn_time",
            this.burnTime
        );

        output.putInt(
            "burn_duration",
            this.burnDuration
        );

        output.putLong(
            "energy_stored",
            this.energyComponent.getStoredEnergy()
        );

        output.putString(
            "front_state",
            this.frontState.name()
        );

        this.saveEnergyPorts(output);
        this.saveFluidPorts(output);
        this.fluidStorage.serialize(output.child("fluid_storage"));
    }

    @Override
    protected void loadAdditional(@NonNull ValueInput input) {
        super.loadAdditional(input);

        this.burnDuration = Math.clamp(
            input.getIntOr(
                "burn_duration",
                0
            ),
            0,
            BURN_DURATION_TICKS
        );

        this.burnTime = Math.clamp(
            input.getIntOr(
                "burn_time",
                0
            ),
            0,
            this.burnDuration
        );

        this.energyComponent.restoreStoredEnergy(input.getLongOr(
            "energy_stored",
            0L
        ));

        String savedState = input.getStringOr(
            "front_state",
            "OFF"
        );

        this.frontState = switch (savedState) {
            case "RUNNING" -> LavaGeneratorFrontState.RUNNING;
            case "LOW_LAVA" -> LavaGeneratorFrontState.LOW_LAVA;
            default -> LavaGeneratorFrontState.OFF;
        };

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
        return this.frontState;
    }

    @Override
    public @NonNull Component getDisplayName() {
        return Component.translatable("block.astralspace.lava_generator");
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(
        int containerId,
        @NonNull Inventory playerInventory,
        @NonNull Player player
    )
    {
        if (this.level == null) {
            return null;
        }

        return new LavaGeneratorMenu(
            containerId,
            playerInventory,
            ContainerLevelAccess.create(
                this.level,
                this.worldPosition
            ),
            this.data
        );
    }

    public static void serverTick(
        @NonNull Level level,
        @NonNull BlockPos pos,
        @NonNull BlockState state,
        @NonNull LavaGeneratorBlockEntity blockEntity
    )
    {
        blockEntity.currentProductionRate = 0;
        blockEntity.pushEnergyToNeighbors(
            level,
            pos
        );
        long freeSpace = ENERGY_CAPACITY - blockEntity.getEnergyStored();

        if (freeSpace < ENERGY_GENERATION_PER_TICK) {
            blockEntity.updateFrontState(false);
            return;
        }


        if (blockEntity.burnTime <= 0) {
            boolean isBurningStarted = blockEntity.tryStartBurning();
            if (!isBurningStarted || blockEntity.burnTime <= 0) {
                blockEntity.updateFrontState(false);
                return;
            }
        }

        int generatedEnergy =
            blockEntity.energyComponent.storeProducedEnergy(ENERGY_GENERATION_PER_TICK);
        blockEntity.currentProductionRate = generatedEnergy;
        blockEntity.burnTime -= 1;
        blockEntity.setChanged();
        blockEntity.updateFrontState(generatedEnergy > 0);
        if (generatedEnergy > 0 &&
            blockEntity.extractionBudget.getRemaining(level.getGameTime()) > 0)
        {
            blockEntity.pushEnergyToNeighbors(
                level,
                pos
            );
        }
    }

    public int getCurrentProductionRate() {
        return this.currentProductionRate;
    }

    public int getCurrentTransferRate() {
        if (this.level == null) {
            return 0;
        }

        long currentTick = this.level.getGameTime();

        return this.transferTracker.getCurrentTransferRate(currentTick);
    }

    public int getLavaStored() {
        return this.fluidStorage.getAmountAsInt(LAVA_TANK_INDEX);
    }

    public long getEnergyStored() {
        return this.energyComponent.getStoredEnergy();
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

    public @Nullable EnergyHandler getEnergyHandlerForWorldSide(
        @Nullable Direction worldSide
    )
    {
        if (worldSide == null) {
            return null;
        }

        EnergyPortId portId = this.getEnergyPortId(worldSide);

        if (portId == null) {
            return null;
        }

        if (!this.energyPortPolicy.allowsExtract(portId)) {
            return null;
        }

        return this.outputHandlers.computeIfAbsent(
            portId,
            id -> new EnergyPortHandler(
                id,
                this.energyPortPolicy,
                this.transferCoordinator
            )
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

    private @Nullable EnergyPortId getEnergyPortId(Direction worldSide) {
        BlockState state = this.getBlockState();

        Direction facing = state.getValue(LavaGeneratorBlock.FACING);

        MachineSide machineSide = MachineSideDirections.fromWorld(
            worldSide,
            facing
        );

        return ENERGY_PORT_IDS.get(machineSide);
    }

    private void syncAvailableEnergyPorts() {
        List<EnergyPortId> portIds = new ArrayList<>();

        for (MachineSide machineSide : MachineSide.values()) {
            EnergyPortId portId = ENERGY_PORT_IDS.get(machineSide);
            if (portId == null) {
                continue;
            }

            boolean isOutputEnabled = this.energyPortPolicy.allowsExtract(portId);
            if (isOutputEnabled) {
                portIds.add(portId);
            }
        }

        this.distributionManager.updateAvailablePorts(portIds);
    }

    private void updateFrontState(
        boolean isRunning
    )
    {
        LavaGeneratorFrontState desiredState = this.determineFrontState(isRunning);

        if (this.frontState == desiredState) {
            return;
        }

        this.frontState = desiredState;
        this.setChanged();

        if (this.level != null && !this.level.isClientSide()) {
            BlockState state = this.getBlockState();

            this.level.sendBlockUpdated(
                this.worldPosition,
                state,
                state,
                Block.UPDATE_CLIENTS
            );
        }
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

    private boolean tryStartBurning() {
        if (burnTime > 0) {
            return false;
        }

        if (this.fluidStorage.getAmountAsInt(LAVA_TANK_INDEX) < LAVA_PER_CYCLE) {
            return false;
        }

        try (Transaction tx = Transaction.openRoot()) {
            int extracted = this.fluidStorage.extract(
                LAVA_TANK_INDEX,
                FluidResource.of(Fluids.LAVA),
                LAVA_PER_CYCLE,
                tx
            );

            if (extracted != LAVA_PER_CYCLE) {
                return false;
            }

            this.burnTime = BURN_DURATION_TICKS;
            this.burnDuration = BURN_DURATION_TICKS;

            this.setChanged();
            tx.commit();

            return true;
        }
    }

    private LavaGeneratorFrontState determineFrontState(boolean isRunning) {
        if (!isRunning) {
            return LavaGeneratorFrontState.OFF;
        }

        int lavaStored = this.getLavaStored();

        if (lavaStored > LOW_LAVA_THRESHOLD) {
            return LavaGeneratorFrontState.RUNNING;
        }

        return LavaGeneratorFrontState.LOW_LAVA;
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
        this.syncAvailableEnergyPorts();
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
        this.syncAvailableEnergyPorts();
        this.setChanged();

        if (this.level != null && !this.level.isClientSide()) {
            this.level.invalidateCapabilities(this.worldPosition);
            this.level.sendBlockUpdated(
                this.worldPosition,
                this.getBlockState(),
                this.getBlockState(),
                Block.UPDATE_CLIENTS
            );
        }
    }

    private @NonNull List<Direction> getRotatedOutputSides(long currentTick) {
        List<Direction> sides = new ArrayList<>();

        for (Direction side : Direction.values()) {
            EnergyPortId portId = this.getEnergyPortId(side);
            if (portId == null) {
                continue;
            }

            boolean isEnergyOutputEnabled = this.energyPortPolicy.allowsExtract(portId);
            if (isEnergyOutputEnabled) {
                sides.add(side);
            }
        }

        if (sides.isEmpty()) {
            return sides;
        }

        int distance = Math.floorMod(
            currentTick,
            sides.size()
        );

        Collections.rotate(
            sides,
            -distance
        );

        return sides;
    }

    private @Nullable EnergyHandler getNeighborEnergyHandler(
        @NonNull Level level,
        @NonNull BlockPos pos,
        Direction side
    )
    {
        BlockPos neighborPos = pos.relative(side);

        if (!level.getChunkSource()
            .hasChunk(
                neighborPos.getX() >> 4,
                neighborPos.getZ() >> 4
            ))
        {
            return null;
        }

        return level.getCapability(
            Capabilities.Energy.BLOCK,
            neighborPos,
            side.getOpposite()
        );
    }

    private int probeEnergyAcceptance(
        @NonNull EnergyHandler target
    )
    {
        int accepted;

        try (Transaction tx = Transaction.openRoot()) {
            accepted = target.insert(
                MAX_EXTRACT,
                tx
            );
        }

        return accepted;
    }

    private void pushEnergyToNeighbors(
        @NonNull Level level,
        BlockPos pos
    )
    {
        Map<EnergyPortId, Integer> discoveryNeighbours = new LinkedHashMap<>();

        List<Direction> rotatedEnergyOutputSides = this.getRotatedOutputSides(level.getGameTime());
        for (Direction side : rotatedEnergyOutputSides) {
            EnergyPortId portId = this.getEnergyPortId(side);

            if (portId == null) {
                continue;
            }

            EnergyHandler target = this.getNeighborEnergyHandler(
                level,
                pos,
                side
            );
            if (target == null) {
                continue;
            }

            int probeResult = this.probeEnergyAcceptance(target);
            if (probeResult > 0) {
                discoveryNeighbours.put(
                    portId,
                    probeResult
                );
            }
        }

        this.distributionManager.updatePushAvailability(
            discoveryNeighbours,
            level.getGameTime()
        );

        for (Direction side : rotatedEnergyOutputSides) {
            if (this.energyComponent.getStoredEnergy() <= 0 ||
                this.extractionBudget.getRemaining(level.getGameTime()) <= 0)
            {
                break;
            }

            EnergyHandler target = this.getNeighborEnergyHandler(
                level,
                pos,
                side
            );

            if (target == null) {
                continue;
            }

            EnergyPortId portId = this.getEnergyPortId(side);

            if (portId == null) {
                continue;
            }

            try (Transaction tx = Transaction.openRoot()) {
                int transferred = this.energyPushTransfer.push(
                    portId,
                    target,
                    MAX_EXTRACT,
                    tx
                );

                if (transferred > 0) {
                    tx.commit();
                }
            }
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
