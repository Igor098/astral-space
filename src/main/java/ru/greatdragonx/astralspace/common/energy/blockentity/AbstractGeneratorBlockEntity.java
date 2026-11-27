package ru.greatdragonx.astralspace.common.energy.blockentity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.jetbrains.annotations.Unmodifiable;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import ru.greatdragonx.astralspace.common.energy.*;
import ru.greatdragonx.astralspace.common.energy.generation.GenerationProcess;
import ru.greatdragonx.astralspace.common.energy.generation.GeneratorConfiguration;
import ru.greatdragonx.astralspace.common.energy.port.*;
import ru.greatdragonx.astralspace.common.machine.RedstoneControlComponent;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public abstract class AbstractGeneratorBlockEntity extends AbstractEnergyMachineBlockEntity {
    private final GeneratorConfiguration configuration;
    private final EnergyPortPolicy energyPortPolicy;

    private final @Nullable EnergyPortConfiguration energyPortConfiguration;
    private final EnergyDistributionManager distribution;
    private final EnergyTransferBudget outputBudget;
    private final EnergyTransferTracker transferTracker;
    private final EnergyOutputController outputController;
    private final EnergyPushTransfer pushTransfer;

    private final Map<EnergyPortId, EnergyPortHandler> energyPortHandlers;
    private final Map<EnergyPortId, EnergyEndpointBinding> energyEndpointBindings = new HashMap<>();

    private final RedstoneControlComponent redstoneControl;

    private @Nullable GenerationProcess generationProcess;
    private boolean energyRuntimeReady = false;
    private final int currentProductionRate = 0;

    private final class EnergyEndpointBinding {
        private final EnergyPortId portId;
        private final EnergyPortTopology.WorldPortPoint worldPoint;
        private final EnergyEndpointHandler handler;

        private EnergyEndpointBinding(
            @NonNull EnergyPortId portId,
            EnergyPortTopology.@NonNull WorldPortPoint worldPoint,
            @NonNull EnergyPortHandler delegate
        )
        {
            this.portId = Objects.requireNonNull(portId, "portId cannot be null");
            this.worldPoint = Objects.requireNonNull(worldPoint, "worldPoint cannot be null");
            this.handler = new EnergyEndpointHandler(
                delegate,
                () -> AbstractGeneratorBlockEntity.this.isEnergyEndpointValid(this)
            );
        }
    }

    protected AbstractGeneratorBlockEntity(
        @NonNull BlockEntityType<?> type,
        @NonNull BlockPos pos,
        @NonNull BlockState blockState,
        @NonNull GeneratorConfiguration configuration
    )
    {
        super(
            type, pos, blockState, Objects.requireNonNull(
                configuration,
                "configuration cannot be null"
            ).energy().energyCapacity()
        );

        this.configuration = configuration;

        EnergyPortPolicyFactory.Result policyConfig = EnergyPortPolicyFactory.create(
            configuration.portRules(),
            this::isEnergyPortAvailable,
            this::handleEnergyPortConfigurationChanged
        );
        this.energyPortPolicy = policyConfig.policy();
        this.energyPortConfiguration = policyConfig.configuration();

        EnergyOutputFactory.Result outputConfig = EnergyOutputFactory.create(
            this.getEnergyComponent(),
            this.configuration.portRules().portIds(),
            this.energyPortPolicy,
            this.configuration.energy().maxOutputPerTick(),
            this::getCurrentGameTime
        );
        this.distribution = outputConfig.distribution();
        this.outputBudget = outputConfig.outputBudget();
        this.transferTracker = outputConfig.transferTracker();
        this.outputController = outputConfig.outputController();
        this.pushTransfer = outputConfig.pushTransfer();

        EnergyPortDispatcher dispatcher = new EnergyPortDispatcher(
            this.getEnergyComponent(),
            null,
            this.outputController
        );
        this.energyPortHandlers = this.createEnergyPortHandlers(dispatcher);

        this.redstoneControl = new RedstoneControlComponent(this::setChanged);
    }

    @Override
    public final void onLoad() {
        super.onLoad();
        this.requireGenerationProcess();

        if (!this.hasServerContext()) {
            return;
        }

        if (this.energyRuntimeReady) {
            return;
        }

        this.energyRuntimeReady = true;

        try {
            this.synchronizeAvailableEnergyPorts();
        } catch (RuntimeException | Error e) {
            this.energyRuntimeReady = false;
            throw e;
        }
    }

    @Override
    public void setRemoved() {
        this.closeEnergyRuntime();
        super.setRemoved();
    }

    @Override
    public void onChunkUnloaded() {
        this.closeEnergyRuntime();
        super.onChunkUnloaded();
    }

    protected boolean isEnergyPortPhysicallyAvailable(
        @NonNull EnergyPortPoint point
    )
    {
        Objects.requireNonNull(point, "point cannot be null");

        return point.localOffset().equals(BlockPos.ZERO);
    }

    protected final void bindGenerationProcess(
        @NonNull GenerationProcess generationProcess
    )
    {
        Objects.requireNonNull(generationProcess, "generationProcess cannot be null");
        if (this.generationProcess != null) {
            throw new IllegalStateException("Generation process is already bound");
        }

        this.generationProcess = generationProcess;
    }

    protected final @NonNull Direction getFacing() {
        BlockState state = this.getBlockState();
        if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            return state.getValue(BlockStateProperties.HORIZONTAL_FACING);
        }

        return Direction.NORTH;
    }

    private @NonNull Optional<EnergyPortTopology.WorldPortPoint> resolveEnergyPort(
        @NonNull EnergyPortId portId
    )
    {
        EnergyPortTopology topology = this.configuration.topology();

        return topology.resolvePort(portId, this.getBlockPos(), this.getFacing());
    }

    private @NonNull GenerationProcess requireGenerationProcess() {
        if (this.generationProcess == null) {
            throw new IllegalStateException("Generation process is not bound");
        }

        return this.generationProcess;
    }

    private boolean hasServerContext() {
        return this.level instanceof ServerLevel && !this.isRemoved();
    }

    private boolean isEnergyRuntimeReady() {
        return this.hasServerContext() && this.energyRuntimeReady;
    }

    private boolean isEnergyPortAvailable(
        @NonNull EnergyPortId portId
    )
    {
        Objects.requireNonNull(portId, "portId cannot be null");

        if (!isEnergyRuntimeReady()) {
            return false;
        }

        EnergyPortTopology topology = this.configuration.topology();
        Optional<EnergyPortPoint> point = topology.points().stream().filter(p -> p.portId()
            .equals(portId)).findFirst();

        return point.filter(this::isEnergyPortPhysicallyAvailable).isPresent();
    }

    private boolean isEnergyEndpointValid(
        @NonNull EnergyEndpointBinding binding
    )
    {
        Objects.requireNonNull(binding, "binding cannot be null");

        if (!this.isEnergyRuntimeReady()) {
            return false;
        }

        if (this.energyEndpointBindings.get(binding.portId) != binding) {
            return false;
        }

        if (!(this.level instanceof ServerLevel serverLevel)) {
            return false;
        }

        BlockPos pos = this.getBlockPos();
        int chunkX = pos.getX() >> 4;
        int chunkZ = pos.getZ() >> 4;

        if (!serverLevel.getChunkSource().hasChunk(chunkX, chunkZ)) {
            return false;
        }

        BlockEntity blockEntity = serverLevel.getBlockEntity(pos);
        if (blockEntity != this) {
            return false;
        }

        Optional<EnergyPortTopology.WorldPortPoint> currentPoint =
            this.resolveEnergyPort(binding.portId);

        return currentPoint.filter(binding.worldPoint::equals).isPresent();
    }

    private void handleEnergyPortConfigurationChanged(
        @NonNull EnergyPortId portId
    )
    {
        throw new UnsupportedOperationException();
    }

    private long getCurrentGameTime() {
        if (!this.isEnergyRuntimeReady()) {
            throw new IllegalStateException("Generator energy runtime is not ready");
        }

        return Objects.requireNonNull(this.level, "level cannot be null").getGameTime();
    }

    private @NonNull @Unmodifiable Map<EnergyPortId, EnergyPortHandler> createEnergyPortHandlers(
        @NonNull EnergyPortDispatcher dispatcher
    )
    {
        Map<EnergyPortId, EnergyPortHandler> handlers = new HashMap<>();

        for (EnergyPortId portId : this.configuration.portRules().portIds()) {
            handlers.put(portId, new EnergyPortHandler(portId, this.energyPortPolicy, dispatcher));
        }

        return Map.copyOf(handlers);
    }

    private void synchronizeAvailableEnergyPorts() {
        this.outputController.refreshAvailablePorts();
    }

    private void closeEnergyRuntime() {
        this.energyRuntimeReady = false;
    }
}
