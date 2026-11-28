package ru.greatdragonx.astralspace.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.NonNull;
import ru.greatdragonx.astralspace.common.energy.blockentity.AbstractGeneratorBlockEntity;
import ru.greatdragonx.astralspace.common.energy.generation.GeneratorConfiguration;
import ru.greatdragonx.astralspace.common.energy.generation.GeneratorEnergySpec;
import ru.greatdragonx.astralspace.common.energy.generation.PersistentGenerationProcess;
import ru.greatdragonx.astralspace.common.energy.port.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Controllable test fixture. No reflection, mocks or production changes.
 */
final class GameTestGeneratorBlockEntity extends AbstractGeneratorBlockEntity {
    static final long CAPACITY = 10_000L;
    static final int GENERATION = 40;
    static final int MAX_OUTPUT = 80;
    static final EnergyPortId FRONT = new EnergyPortId("front");
    static final EnergyPortId BACK = new EnergyPortId("back");
    static final EnergyPortId LEFT = new EnergyPortId("left");
    static final EnergyPortId TOP = new EnergyPortId("top");
    private static final Set<EnergyPortMode> MODES = Set.of(
        EnergyPortMode.OUTPUT,
        EnergyPortMode.DISABLED
    );

    private static final List<EnergyPortPoint> POINTS = List.of(
        new EnergyPortPoint(FRONT, BlockPos.ZERO, Direction.NORTH),
        new EnergyPortPoint(BACK, BlockPos.ZERO, Direction.SOUTH),
        new EnergyPortPoint(LEFT, BlockPos.ZERO, Direction.EAST),
        new EnergyPortPoint(TOP, BlockPos.ZERO, Direction.UP)
    );

    private static GeneratorConfiguration configuration(boolean fixed) {
        List<EnergyPortRule> rules = List.of(
            rule(FRONT, EnergyPortMode.OUTPUT, fixed),
            rule(BACK, EnergyPortMode.OUTPUT, fixed),
            rule(LEFT, EnergyPortMode.DISABLED, fixed),
            rule(TOP, EnergyPortMode.OUTPUT, fixed)
        );
        return new GeneratorConfiguration(
            new GeneratorEnergySpec(CAPACITY, MAX_OUTPUT, GENERATION),
            new EnergyPortRules(
                fixed ? EnergyPortRules.ConfigurationType.FIXED :
                    EnergyPortRules.ConfigurationType.CONFIGURABLE, rules
            ),
            new EnergyPortTopology(POINTS)
        );
    }

    private static EnergyPortRule rule(EnergyPortId id, EnergyPortMode mode, boolean fixed) {
        return new EnergyPortRule(id, mode, fixed ? Set.of(mode) : MODES);
    }

    final List<String> events = new ArrayList<>();
    final RecordingProcess process = new RecordingProcess();
    private final Set<EnergyPortId> unavailablePorts = new HashSet<>();
    private boolean structureReady = true;
    private boolean failNextStructureCheck;
    int structureChecks;
    int physicalChecks;
    int redstoneReads;
    int changedCalls;

    GameTestGeneratorBlockEntity(BlockPos pos, BlockState state) {
        super(
            GameTestGeneratorRegistry.GENERATOR_ENTITY.get(),
            pos,
            state,
            configuration(state.is(GameTestGeneratorRegistry.FIXED_GENERATOR.get()))
        );
        bindGenerationProcess(process);
    }

    void tickOnce() {serverTick();}

    /**
     * Test setup only: charges through the actual EnergyComponent, not through NBT.
     */
    int charge(int amount) {return getEnergyComponent().storeProducedEnergy(amount);}

    void setStructureReady(boolean ready) {
        structureReady = ready;
        onEnergyPortAvailabilityChanged();
    }

    void setPortAvailable(EnergyPortId id, boolean available) {
        if (available) unavailablePorts.remove(id);
        else unavailablePorts.add(id);
        onEnergyPortAvailabilityChanged();
    }

    void replaceEndpoint(EnergyPortId id) {onEnergyPortEndpointChanged(id);}

    void failNextStructureCheck() {failNextStructureCheck = true;}

    void resetObservations() {
        events.clear();
        structureChecks = physicalChecks = redstoneReads = changedCalls = 0;
        process.prepareCalls = process.completionCalls = 0;
    }

    @Override
    protected boolean isStructureReady() {
        structureChecks++;
        if (failNextStructureCheck) {
            failNextStructureCheck = false;
            throw new IllegalStateException("Intentional GameTest structure failure");
        }
        return structureReady;
    }

    @Override
    protected boolean isEnergyPortPhysicallyAvailable(@NonNull EnergyPortPoint point) {
        physicalChecks++;
        return super.isEnergyPortPhysicallyAvailable(point) &&
            !unavailablePorts.contains(point.portId());
    }

    @Override
    protected boolean readRedstoneSignal() {
        redstoneReads++;
        return super.readRedstoneSignal();
    }

    @Override
    public void setChanged() {
        changedCalls++;
        events.add("changed");
        super.setChanged();
    }

    @Override
    protected void onEnergyChanged() {events.add("energy:" + getStoredEnergy());}

    @Override
    protected void onEnergyPortConfigurationChanged(@NonNull EnergyPortId portId) {
        events.add("port:" + portId.value() + ":" + getEnergyPortMode(portId));
    }

    @Override
    protected void onProductionStateUpdated(boolean producing) {
        events.add("producing:" +
            producing);
    }

    final class RecordingProcess implements PersistentGenerationProcess {
        boolean allowProduction;
        int prepareCalls;
        int completionCalls;
        int completedTicks;
        Runnable beforePrepareReturns = () -> {
        };

        @Override
        public boolean prepareProductionTick() {
            prepareCalls++;
            events.add("prepare");
            beforePrepareReturns.run();
            return allowProduction;
        }

        @Override
        public void onProductionTickCompleted() {
            completionCalls++;
            completedTicks++;
            events.add("complete:" + getStoredEnergy());
        }

        @Override
        public void saveState(@NonNull ValueOutput output) {
            output.putInt("completed_ticks", completedTicks);
        }

        @Override
        public void restoreState(@NonNull ValueInput input) {
            completedTicks = Math.max(0, input.getIntOr("completed_ticks", 0));
        }
    }
}
