package ru.greatdragonx.astralspace.common.energy.blockentity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.jetbrains.annotations.Unmodifiable;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import ru.greatdragonx.astralspace.common.energy.*;
import ru.greatdragonx.astralspace.common.energy.generation.GenerationProcess;
import ru.greatdragonx.astralspace.common.energy.generation.GeneratorConfiguration;
import ru.greatdragonx.astralspace.common.energy.port.*;
import ru.greatdragonx.astralspace.common.machine.RedstoneControlComponent;
import ru.greatdragonx.astralspace.common.machine.RedstoneControlMode;

import java.util.*;

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
    private Map<EnergyPortId, EnergyPortTopology.WorldPortPoint> exposedEnergyPorts =
        Collections.emptyMap();

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
            this.synchronizeExposedEnergyPorts(Collections.emptySet());
        } catch (RuntimeException | Error e) {
            this.closeEnergyRuntime();
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

    @Override
    @SuppressWarnings("deprecation")
    public void setBlockState(@NonNull BlockState blockState) {
        Objects.requireNonNull(blockState, "blockState cannot be null");

        Direction prevFacing = this.getFacing();
        super.setBlockState(blockState);

        Direction currentFacing = this.getFacing();
        if (prevFacing == currentFacing) {
            return;
        }

        if (!this.isEnergyRuntimeReady()) {
            return;
        }

        this.revokeMovedEnergyEndpointBindings();
        this.synchronizeAvailableEnergyPorts();
        this.synchronizeExposedEnergyPorts(Collections.emptySet());
    }

    public final @NonNull RedstoneControlMode getRedstoneControlMode() {
        return this.redstoneControl.getMode();
    }

    public final @NonNull EnergyPortMode getEnergyPortMode(
        @NonNull EnergyPortId portId
    )
    {
        Objects.requireNonNull(portId, "portId cannot be null");
        return this.energyPortPolicy.getMode(portId);
    }

    public final @Nullable EnergyHandler getEnergyHandlerForSide(
        @Nullable Direction side
    )
    {
        return this.getEnergyHandlerAt(this.getBlockPos(), side);
    }

    public final @Nullable EnergyHandler getEnergyHandlerAt(
        @NonNull BlockPos worldPos,
        @Nullable Direction side
    )
    {
        Objects.requireNonNull(worldPos, "worldPos cannot be null");

        if (!isEnergyRuntimeReady()) {
            return null;
        }

        if (side == null) {
            return null;
        }

        EnergyPortTopology topology = this.configuration.topology();
        Optional<EnergyPortId> portIdOptional = topology.findPort(
            worldPos,
            side,
            this.getBlockPos(),
            this.getFacing()
        );

        if (portIdOptional.isEmpty()) {
            return null;
        }

        EnergyPortId portId = portIdOptional.get();

        if (!this.energyPortPolicy.allowsExtract(portId)) {
            return null;
        }

        EnergyPortTopology.WorldPortPoint worldPoint = new EnergyPortTopology.WorldPortPoint(
            worldPos,
            side
        );

        return this.getOrCreateEnergyEndpointHandler(portId, worldPoint);
    }

    public final boolean changeEnergyPortMode(
        @NonNull EnergyPortId portId,
        @NonNull EnergyPortMode newMode
    )
    {
        Objects.requireNonNull(portId, "portId cannot be null");
        Objects.requireNonNull(newMode, "newMode cannot be null");

        if (!this.isEnergyRuntimeReady()) {
            return false;
        }

        if (this.energyPortConfiguration == null) {
            return false;
        }

        return this.energyPortConfiguration.changeMode(portId, newMode, this.energyPortPolicy);
    }

    protected boolean isStructureReady() {
        return true;
    }

    protected boolean isEnergyPortPhysicallyAvailable(
        @NonNull EnergyPortPoint point
    )
    {
        Objects.requireNonNull(point, "point cannot be null");

        return point.localOffset().equals(BlockPos.ZERO);
    }

    protected void onEnergyPortConfigurationChanged(
        @NonNull EnergyPortId portId
    )
    {}

    protected final void onEnergyPortAvailabilityChanged() {
        if (!isEnergyRuntimeReady()) {
            return;
        }

        this.synchronizeAvailableEnergyPorts();
        this.synchronizeExposedEnergyPorts(Collections.emptySet());
    }

    protected final void onEnergyPortEndpointChanged(
        @NonNull EnergyPortId portId
    )
    {
        Objects.requireNonNull(portId, "portId cannot be null");
        if (!this.isEnergyRuntimeReady()) {
            return;
        }

        if (!this.energyPortHandlers.containsKey(portId)) {
            return;
        }

        this.energyEndpointBindings.remove(portId);

        EnergyPortTopology.WorldPortPoint previousPoint = this.exposedEnergyPorts.get(portId);
        Set<BlockPos> additionalPositions = previousPoint == null ? Collections.emptySet() : Set.of(
            previousPoint.worldPos());

        this.synchronizeAvailableEnergyPorts();
        this.synchronizeExposedEnergyPorts(additionalPositions);
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

        if (!this.isStructureReady()) {
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
        Objects.requireNonNull(portId, "portId cannot be null");

        this.synchronizeAvailableEnergyPorts();
        this.setChanged();
        this.synchronizeExposedEnergyPorts(Collections.emptySet());
        this.onEnergyPortConfigurationChanged(portId);
    }

    private long getCurrentGameTime() {
        if (!this.isEnergyRuntimeReady()) {
            throw new IllegalStateException("Generator energy runtime is not ready");
        }

        return Objects.requireNonNull(this.level, "level cannot be null").getGameTime();
    }

    private int probeEnergyAcceptance(
        @NonNull EnergyHandler target
    )
    {
        Objects.requireNonNull(target, "target cannot be null");

        int maxOutputPerTick = this.configuration.energy().maxOutputPerTick();

        try (Transaction transaction = Transaction.openRoot()) {
            return target.insert(maxOutputPerTick, transaction);
        }
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

    private @NonNull @Unmodifiable Map<EnergyPortId, EnergyPortTopology.WorldPortPoint> collectExposedEnergyPorts() {
        if (!this.isEnergyRuntimeReady()) {
            return Collections.emptyMap();
        }

        Map<EnergyPortId, EnergyPortTopology.WorldPortPoint> points = new HashMap<>();

        for (EnergyPortId portId : this.configuration.portRules().portIds()) {
            if (!this.energyPortPolicy.allowsExtract(portId)) {
                continue;
            }

            EnergyPortTopology.WorldPortPoint worldPoint =
                this.resolveEnergyPort(portId).orElseThrow(() -> new IllegalStateException(
                    "Unable to resolve world point for energy port: " + portId));

            points.put(portId, worldPoint);
        }

        return Map.copyOf(points);
    }

    private static @NonNull @Unmodifiable Set<BlockPos> collectChangedEnergyPortPositions(
        @NonNull Map<EnergyPortId, EnergyPortTopology.WorldPortPoint> previous,
        @NonNull Map<EnergyPortId, EnergyPortTopology.WorldPortPoint> current
    )
    {
        Objects.requireNonNull(previous, "previous cannot be null");
        Objects.requireNonNull(current, "current cannot be null");

        Set<EnergyPortId> energyPortIds = new HashSet<>(previous.keySet());
        energyPortIds.addAll(current.keySet());

        Set<BlockPos> changedPositions = new HashSet<>();

        for (EnergyPortId portId : energyPortIds) {
            EnergyPortTopology.WorldPortPoint previousPoint = previous.get(portId);
            EnergyPortTopology.WorldPortPoint currentPoint = current.get(portId);

            if (Objects.equals(previousPoint, currentPoint)) {
                continue;
            }

            if (previousPoint != null) {
                changedPositions.add(previousPoint.worldPos());
            }

            if (currentPoint != null) {
                changedPositions.add(currentPoint.worldPos());
            }
        }

        return Set.copyOf(changedPositions);
    }

    private @NonNull EnergyEndpointHandler getOrCreateEnergyEndpointHandler(
        @NonNull EnergyPortId portId,
        EnergyPortTopology.@NonNull WorldPortPoint worldPoint
    )
    {
        Objects.requireNonNull(portId, "portId cannot be null");
        Objects.requireNonNull(worldPoint, "worldPoint cannot be null");

        EnergyEndpointBinding binding = this.energyEndpointBindings.get(portId);
        if (binding != null && binding.worldPoint.equals(worldPoint)) {
            return binding.handler;
        }

        EnergyPortHandler handler = this.energyPortHandlers.get(portId);
        if (handler == null) {
            throw new IllegalStateException("Energy port handler is missing for port: " + portId);
        }

        EnergyEndpointBinding newBinding = new EnergyEndpointBinding(portId, worldPoint, handler);
        this.energyEndpointBindings.put(portId, newBinding);

        return newBinding.handler;
    }

    private @NonNull List<EnergyPortId> getRotatedOutputPorts(
        long currentTick
    )
    {
        List<EnergyPortId> ports = new ArrayList<>();
        for (EnergyPortId portId : this.configuration.portRules().portIds()) {
            if (this.energyPortPolicy.allowsExtract(portId)) {
                ports.add(portId);
            }
        }

        if (ports.isEmpty()) {
            return ports;
        }

        int shift = Math.floorMod(currentTick, ports.size());
        Collections.rotate(ports, -shift);

        return ports;
    }

    private @Nullable EnergyHandler getNeighborEnergyHandler(
        @NonNull EnergyPortId portId
    )
    {
        Objects.requireNonNull(portId, "portId cannot be null");

        if (!this.isEnergyRuntimeReady()) {
            return null;
        }

        if (!this.energyPortPolicy.allowsExtract(portId)) {
            return null;
        }

        if (!(this.level instanceof ServerLevel serverLevel)) {
            return null;
        }

        Optional<EnergyPortTopology.WorldPortPoint> pointOptional = this.resolveEnergyPort(portId);
        if (pointOptional.isEmpty()) {
            return null;
        }

        EnergyPortTopology.WorldPortPoint point = pointOptional.get();

        BlockPos neighborPos = point.worldPos().relative(point.outwardFace());

        if (!serverLevel.getChunkSource().hasChunk(
            neighborPos.getX() >> 4,
            neighborPos.getZ() >> 4
        ))
        {
            return null;
        }

        Direction neighborSide = point.outwardFace().getOpposite();

        return serverLevel.getCapability(Capabilities.Energy.BLOCK, neighborPos, neighborSide);
    }

    private void invalidateEnergyPortCapabilities(
        @NonNull Set<BlockPos> positions
    )
    {
        Objects.requireNonNull(positions, "positions cannot be null");
        if (!this.isEnergyRuntimeReady()) {
            return;
        }

        if (!(this.level instanceof ServerLevel serverLevel)) {
            return;
        }

        for (BlockPos pos : positions) {
            if (!serverLevel.getChunkSource().hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) {
                continue;
            }

            serverLevel.invalidateCapabilities(pos);
        }
    }

    private void pushEnergyToNeighbors() {
        if (!this.isEnergyRuntimeReady()) {
            return;
        }
        long currentTick = this.getCurrentGameTime();
        List<EnergyPortId> rotatedPorts = this.getRotatedOutputPorts(currentTick);

        Map<EnergyPortId, Integer> pushAvailability = new HashMap<>();

        for (EnergyPortId portId : rotatedPorts) {
            EnergyHandler handler = this.getNeighborEnergyHandler(portId);
            if (handler == null) {
                continue;
            }

            int accepted = this.probeEnergyAcceptance(handler);
            if (accepted > 0) {
                pushAvailability.put(portId, accepted);
            }
        }

        this.distribution.updatePushAvailability(pushAvailability, currentTick);

        for (EnergyPortId portId : rotatedPorts) {
            if (!this.isEnergyRuntimeReady()) {
                return;
            }

            long storedEnergy = this.getStoredEnergy();
            int budgetRemaining = this.outputBudget.getRemaining(currentTick);
            if (storedEnergy <= 0 || budgetRemaining <= 0) {
                return;
            }

            if (!this.energyPortPolicy.allowsExtract(portId)) {
                continue;
            }

            EnergyHandler handler = this.getNeighborEnergyHandler(portId);
            if (handler == null) {
                continue;
            }

            try (Transaction transaction = Transaction.openRoot()) {
                int pushed = this.pushTransfer.push(
                    portId,
                    handler,
                    this.configuration.energy().maxOutputPerTick(),
                    transaction
                );
                if (pushed > 0) {
                    transaction.commit();
                }
            }
        }
    }

    private void synchronizeExposedEnergyPorts(
        @NonNull Set<BlockPos> additionalPositions
    )
    {
        Objects.requireNonNull(additionalPositions, "additionalPositions cannot be null");
        if (!this.isEnergyRuntimeReady()) {
            return;
        }

        Map<EnergyPortId, EnergyPortTopology.WorldPortPoint> current =
            this.collectExposedEnergyPorts();

        Set<BlockPos> changedPositions =
            new HashSet<>(collectChangedEnergyPortPositions(this.exposedEnergyPorts, current));
        changedPositions.addAll(additionalPositions);

        this.exposedEnergyPorts = current;
        this.invalidateEnergyPortCapabilities(changedPositions);
    }

    private void synchronizeAvailableEnergyPorts() {
        this.outputController.refreshAvailablePorts();
    }

    private void revokeMovedEnergyEndpointBindings() {
        this.energyEndpointBindings.entrySet().removeIf(entry -> {
            EnergyPortId portId = entry.getKey();
            EnergyEndpointBinding binding = entry.getValue();

            Optional<EnergyPortTopology.WorldPortPoint> currentPoint =
                this.resolveEnergyPort(portId);

            return currentPoint.filter(binding.worldPoint::equals).isEmpty();
        });
    }

    private void closeEnergyRuntime() {
        this.energyRuntimeReady = false;
        this.energyEndpointBindings.clear();
        this.exposedEnergyPorts = Collections.emptyMap();
        this.synchronizeAvailableEnergyPorts();
    }
}
