package ru.greatdragonx.astralspace.common.energy.blockentity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.*;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.Test;
import ru.greatdragonx.astralspace.common.energy.generation.GenerationProcess;
import ru.greatdragonx.astralspace.common.energy.generation.GeneratorConfiguration;
import ru.greatdragonx.astralspace.common.energy.generation.GeneratorEnergySpec;
import ru.greatdragonx.astralspace.common.energy.generation.PersistentGenerationProcess;
import ru.greatdragonx.astralspace.common.energy.port.*;
import ru.greatdragonx.astralspace.common.machine.redstone.RedstoneControlMode;
import ru.greatdragonx.astralspace.common.serialization.StrictNbtReader;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static ru.greatdragonx.astralspace.common.energy.port.EnergyPortMode.DISABLED;
import static ru.greatdragonx.astralspace.common.energy.port.EnergyPortMode.OUTPUT;
import static ru.greatdragonx.astralspace.common.energy.port.EnergyPortRules.ConfigurationType.CONFIGURABLE;
import static ru.greatdragonx.astralspace.common.energy.port.EnergyPortRules.ConfigurationType.FIXED;
import static ru.greatdragonx.astralspace.common.machine.redstone.RedstoneControlMode.*;

/**
 * Worldless JUnit scenarios U-01 through U-80 for the base generator.
 * Server runtime, actual capability caches, transfers and production are covered
 * by separate integration tests, not by opening private runtime fields here.
 */
class AbstractGeneratorBlockEntityTest {
    private static final long CAPACITY = 10_000L;
    private static final GeneratorEnergySpec SPEC = new GeneratorEnergySpec(CAPACITY, 80, 40);
    private static final BlockPos POSITION = new BlockPos(10, 64, -20);
    private static final EnergyPortId FRONT = new EnergyPortId("front");
    private static final EnergyPortId BACK = new EnergyPortId("back");
    private static final EnergyPortId TOP = new EnergyPortId("top");
    private static final EnergyPortId UNKNOWN = new EnergyPortId("unknown");
    private static final Set<EnergyPortMode> MODES = Set.of(OUTPUT, DISABLED);

    // Deliberately independent of the private production constants.
    private static final String ENERGY = "energy";
    private static final String STORED_ENERGY = "stored_energy";
    private static final String PORTS = "energy_ports";
    private static final String REDSTONE = "redstone_control_mode";
    private static final String PROCESS = "generation_process";
    // Test-double state, not the real FuelCycleGenerationProcess NBT format.
    private static final String TEST_TICKS = "test_ticks";

    private static final class TestGenerator extends AbstractGeneratorBlockEntity {
        private final List<String> events = new ArrayList<>();
        private int structureChecks;
        private int physicalChecks;
        private int signalReads;

        private TestGenerator(GeneratorConfiguration configuration) {
            this(configuration, BlockEntityType.FURNACE, Blocks.FURNACE.defaultBlockState());
        }

        private TestGenerator(
            GeneratorConfiguration configuration,
            BlockEntityType<?> type,
            BlockState state
        )
        {
            super(type, POSITION, state, configuration);
        }

        @Override
        public void setChanged() {
            events.add("setChanged");
            super.setChanged();
        }

        @Override
        protected void onEnergyChanged() {
            events.add("onEnergyChanged");
        }

        @Override
        protected void onEnergyPortConfigurationChanged(@NonNull EnergyPortId portId) {
            events.add("onEnergyPortConfigurationChanged:" + portId.value());
        }

        @Override
        protected void onProductionStateUpdated(boolean producing) {
            events.add("onProductionStateUpdated:" + producing);
        }

        @Override
        protected boolean isStructureReady() {
            structureChecks++;
            return super.isStructureReady();
        }

        @Override
        protected boolean isEnergyPortPhysicallyAvailable(@NonNull EnergyPortPoint point) {
            physicalChecks++;
            return super.isEnergyPortPhysicallyAvailable(point);
        }

        @Override
        protected boolean readRedstoneSignal() {
            signalReads++;
            return super.readRedstoneSignal();
        }

        private void clearObservations() {
            events.clear();
            structureChecks = 0;
            physicalChecks = 0;
            signalReads = 0;
        }
    }

    private static class RecordingGenerationProcess implements GenerationProcess {
        int prepareCalls;
        int completionCalls;

        @Override
        public boolean prepareProductionTick() {
            prepareCalls++;
            return true;
        }

        @Override
        public void onProductionTickCompleted() {
            completionCalls++;
        }
    }

    private static final class RecordingPersistentGenerationProcess extends
        RecordingGenerationProcess implements PersistentGenerationProcess
    {
        private int ticks;
        private int saveCalls;
        private int restoreCalls;

        @Override
        public void saveState(@NonNull ValueOutput output) {
            saveCalls++;
            output.putInt(TEST_TICKS, ticks);
        }

        @Override
        public void restoreState(@NonNull ValueInput input) {
            restoreCalls++;
            ticks = StrictNbtReader.readStrictInt(input, TEST_TICKS).orElse(0);
        }
    }

    private static GeneratorConfiguration configurable() {
        return new GeneratorConfiguration(
            SPEC,
            new EnergyPortRules(
                CONFIGURABLE, List.of(
                new EnergyPortRule(FRONT, OUTPUT, MODES),
                new EnergyPortRule(BACK, DISABLED, MODES),
                new EnergyPortRule(TOP, OUTPUT, MODES)
            )
            ),
            new EnergyPortTopology(List.of(
                point(FRONT, Direction.NORTH),
                point(BACK, Direction.SOUTH),
                point(TOP, Direction.UP)
            ))
        );
    }

    private static GeneratorConfiguration fixed() {
        return new GeneratorConfiguration(
            SPEC,
            new EnergyPortRules(
                FIXED, List.of(
                new EnergyPortRule(FRONT, OUTPUT, Set.of(OUTPUT)),
                new EnergyPortRule(BACK, DISABLED, Set.of(DISABLED))
            )
            ),
            new EnergyPortTopology(List.of(
                point(FRONT, Direction.NORTH),
                point(BACK, Direction.SOUTH)
            ))
        );
    }

    private static GeneratorConfiguration empty(EnergyPortRules.ConfigurationType type) {
        return new GeneratorConfiguration(
            SPEC,
            new EnergyPortRules(type, List.of()),
            new EnergyPortTopology(List.of())
        );
    }

    private static EnergyPortPoint point(EnergyPortId id, Direction direction) {
        return new EnergyPortPoint(id, BlockPos.ZERO, direction);
    }

    private static TestGenerator generator() {
        return generator(configurable(), new RecordingGenerationProcess());
    }

    private static TestGenerator generator(
        GeneratorConfiguration configuration,
        GenerationProcess process
    )
    {
        TestGenerator generator = new TestGenerator(configuration);
        generator.bindGenerationProcess(process);
        return generator;
    }

    private static TagValueOutput output() {
        return TagValueOutput.createWithoutContext(ProblemReporter.DISCARDING);
    }

    private static ValueInput input(CompoundTag tag) {
        return TagValueInput.create(
            ProblemReporter.DISCARDING,
            HolderLookup.Provider.create(Stream.empty()),
            tag
        );
    }

    private static CompoundTag save(TestGenerator generator) {
        TagValueOutput output = output();
        generator.saveAdditional(output);
        return output.buildResult();
    }

    private static void load(TestGenerator generator, CompoundTag tag) {
        CompoundTag before = tag.copy();
        generator.loadAdditional(input(tag));
        assertEquals(before, tag, "Loading must not modify the source NBT");
    }

    private static CompoundTag energyTag(long stored) {
        CompoundTag root = new CompoundTag();
        CompoundTag energy = new CompoundTag();
        energy.putLong(STORED_ENERGY, stored);
        root.put(ENERGY, energy);
        return root;
    }

    private static CompoundTag portModes(String front, String back, String top) {
        CompoundTag ports = new CompoundTag();
        ports.putString(FRONT.value(), front);
        ports.putString(BACK.value(), back);
        ports.putString(TOP.value(), top);
        return ports;
    }

    private static CompoundTag portRoot(Tag section) {
        CompoundTag root = new CompoundTag();
        root.put(PORTS, section);
        return root;
    }

    private static CompoundTag redstoneTag(RedstoneControlMode mode) {
        CompoundTag root = new CompoundTag();
        root.putString(REDSTONE, mode.name());
        return root;
    }

    private static CompoundTag processSection(int ticks) {
        CompoundTag section = new CompoundTag();
        section.putInt(TEST_TICKS, ticks);
        return section;
    }

    private static CompoundTag completeTag(long energy, RedstoneControlMode mode, int ticks) {
        CompoundTag root = energyTag(energy);
        root.put(PORTS, portModes("DISABLED", "OUTPUT", "OUTPUT"));
        root.putString(REDSTONE, mode.name());
        root.put(PROCESS, processSection(ticks));
        return root;
    }

    private static void assertModes(
        TestGenerator g,
        EnergyPortMode front,
        EnergyPortMode back,
        EnergyPortMode top
    )
    {
        assertAll(
            () -> assertSame(front, g.getEnergyPortMode(FRONT), "front"),
            () -> assertSame(back, g.getEnergyPortMode(BACK), "back"),
            () -> assertSame(top, g.getEnergyPortMode(TOP), "top")
        );
    }

    private static void assertDefaults(TestGenerator g) {
        assertModes(g, OUTPUT, DISABLED, OUTPUT);
    }

    private static void assertSilent(TestGenerator g) {
        assertAll(
            () -> assertTrue(g.events.isEmpty(), "Unexpected events: " + g.events),
            () -> assertEquals(0, g.structureChecks, "Structure checks"),
            () -> assertEquals(0, g.physicalChecks, "Physical port checks"),
            () -> assertEquals(0, g.signalReads, "Redstone reads")
        );
    }

    private static void assertNoProduction(RecordingGenerationProcess process) {
        assertAll(
            () -> assertEquals(0, process.prepareCalls),
            () -> assertEquals(0, process.completionCalls)
        );
    }

    private static void assertClosedStatistics(TestGenerator g) {
        assertAll(
            () -> assertEquals(0, g.getCurrentProductionRate()),
            () -> assertEquals(0, g.getCurrentTransferRate()),
            () -> assertFalse(g.isProducingEnergy())
        );
    }

    private static void assertSerializedEnergy(CompoundTag root, long energy) {
        CompoundTag section = assertInstanceOf(CompoundTag.class, root.get(ENERGY));
        LongTag stored = assertInstanceOf(LongTag.class, section.get(STORED_ENERGY));
        assertEquals(energy, stored.longValue());
        assertEquals(1, section.size());
    }

    private static void assertInvalidFront(Tag value) {
        TestGenerator g = generator();
        CompoundTag section = portModes("OUTPUT", "OUTPUT", "OUTPUT");
        load(g, portRoot(section));
        assertModes(g, OUTPUT, OUTPUT, OUTPUT);
        section.put(FRONT.value(), value);
        load(g, portRoot(section));
        assertModes(g, DISABLED, OUTPUT, OUTPUT);
        assertSilent(g);
    }

    // U-01–U-09: construction and initial state

    @Test
    void u01_createsFixedGenerator() {
        TestGenerator g = generator(fixed(), new RecordingGenerationProcess());
        assertEquals(CAPACITY, g.getEnergyCapacity());
        assertEquals(0L, g.getStoredEnergy());
        assertEquals(POSITION, g.getBlockPos());
        assertSame(BlockEntityType.FURNACE, g.getType());
        assertNull(g.getLevel());
        assertModes(g, OUTPUT, DISABLED, DISABLED);
        assertSilent(g);
    }

    @Test
    void u02_createsConfigurableGenerator() {
        TestGenerator g = generator();
        assertEquals(CAPACITY, g.getEnergyCapacity());
        assertEquals(0L, g.getStoredEnergy());
        assertDefaults(g);
        assertNull(g.getLevel());
        assertSilent(g);
    }

    @Test
    void u03_createsGeneratorWithoutPorts() {
        for (var type : EnergyPortRules.ConfigurationType.values()) {
            TestGenerator g = generator(empty(type), new RecordingGenerationProcess());
            assertEquals(CAPACITY, g.getEnergyCapacity());
            assertModes(g, DISABLED, DISABLED, DISABLED);
            assertSilent(g);
        }
    }

    @Test
    void u04_rejectsNullConfiguration() {
        assertThrows(NullPointerException.class, () -> new TestGenerator(null));
    }

    @Test
    void u05_usesDeclaredDefaultModes() {
        TestGenerator g = generator();
        assertDefaults(g);
        assertSame(DISABLED, g.getEnergyPortMode(UNKNOWN));
        assertSilent(g);
    }

    @Test
    void u06_startsWithIgnoreRedstoneMode() {
        TestGenerator g = generator();
        assertSame(IGNORE, g.getRedstoneControlMode());
        assertSilent(g);
    }

    @Test
    void u07_startsWithZeroStatistics() {
        TestGenerator g = generator();
        assertClosedStatistics(g);
        assertSilent(g);
    }

    @Test
    void u08_keepsInstancesIndependent() {
        RecordingPersistentGenerationProcess firstProcess =
            new RecordingPersistentGenerationProcess();
        RecordingPersistentGenerationProcess secondProcess =
            new RecordingPersistentGenerationProcess();
        TestGenerator first = generator(configurable(), firstProcess);
        TestGenerator second = generator(configurable(), secondProcess);
        load(first, completeTag(6_000L, WHEN_POWERED, 27));
        assertModes(first, DISABLED, OUTPUT, OUTPUT);
        assertEquals(6_000L, first.getStoredEnergy());
        assertSame(WHEN_POWERED, first.getRedstoneControlMode());
        assertEquals(27, firstProcess.ticks);
        assertDefaults(second);
        assertEquals(0L, second.getStoredEnergy());
        assertSame(IGNORE, second.getRedstoneControlMode());
        assertEquals(0, secondProcess.ticks);
        assertEquals(0, secondProcess.restoreCalls);
        assertSilent(first);
        assertSilent(second);
    }

    @Test
    void u09_constructionDoesNotCallHooksOrProcess() {
        TestGenerator g = new TestGenerator(configurable());
        RecordingGenerationProcess process = new RecordingGenerationProcess();
        assertSilent(g);
        g.bindGenerationProcess(process);
        assertSilent(g);
        assertNoProduction(process);
    }

    // U-10–U-16: binding the production process

    @Test
    void u10_bindsOneProcess() {
        TestGenerator g = new TestGenerator(configurable());
        RecordingPersistentGenerationProcess process = new RecordingPersistentGenerationProcess();
        process.ticks = 17;
        g.bindGenerationProcess(process);
        assertEquals(processSection(17), save(g).get(PROCESS));
        assertEquals(1, process.saveCalls);
        assertNoProduction(process);
        assertSilent(g);
    }

    @Test
    void u11_rejectsNullProcessWithoutConsumingBinding() {
        TestGenerator g = new TestGenerator(configurable());
        assertThrows(NullPointerException.class, () -> g.bindGenerationProcess(null));
        assertDoesNotThrow(() -> g.bindGenerationProcess(new RecordingGenerationProcess()));
        assertSilent(g);
    }

    @Test
    void u12_rejectsBindingSameProcessTwice() {
        TestGenerator g = new TestGenerator(configurable());
        RecordingGenerationProcess process = new RecordingGenerationProcess();
        g.bindGenerationProcess(process);
        assertThrows(IllegalStateException.class, () -> g.bindGenerationProcess(process));
        assertNoProduction(process);
        assertSilent(g);
    }

    @Test
    void u13_rejectsReplacingBoundProcess() {
        RecordingPersistentGenerationProcess first = new RecordingPersistentGenerationProcess();
        RecordingPersistentGenerationProcess second = new RecordingPersistentGenerationProcess();
        first.ticks = 21;
        second.ticks = 42;
        TestGenerator g = generator(configurable(), first);
        assertThrows(IllegalStateException.class, () -> g.bindGenerationProcess(second));
        assertEquals(processSection(21), save(g).get(PROCESS));
        assertEquals(1, first.saveCalls);
        assertEquals(0, second.saveCalls);
        assertSilent(g);
    }

    @Test
    void u14_savingWithoutProcessFails() {
        TestGenerator g = new TestGenerator(configurable());
        assertThrows(IllegalStateException.class, () -> g.saveAdditional(output()));
    }

    @Test
    void u15_loadingWithoutProcessFails() {
        TestGenerator g = new TestGenerator(configurable());
        assertThrows(IllegalStateException.class, () -> g.loadAdditional(input(new CompoundTag())));
    }

    @Test
    void u16_onLoadWithoutProcessFails() {
        TestGenerator g = new TestGenerator(configurable());
        assertThrows(IllegalStateException.class, g::onLoad);
        assertClosedStatistics(g);
    }

    // U-17–U-30: runtime remains closed without a ServerLevel

    @Test
    void u17_handlerAtIsAbsent() {
        TestGenerator g = generator();
        assertNull(g.getEnergyHandlerAt(POSITION, Direction.NORTH));
        assertNull(g.getEnergyHandlerAt(POSITION.above(), Direction.UP));
        assertSilent(g);
    }

    @Test
    void u18_handlerForSideIsAbsent() {
        TestGenerator g = generator();
        for (Direction side : Direction.values())
            assertNull(
                g.getEnergyHandlerForSide(side),
                side.name()
            );
        assertSilent(g);
    }

    @Test
    void u19_rejectsPortChangesWhileClosed() {
        TestGenerator g = generator();
        assertFalse(g.changeEnergyPortMode(FRONT, DISABLED));
        assertFalse(g.changeEnergyPortMode(BACK, OUTPUT));
        assertDefaults(g);
        assertSilent(g);
    }

    @Test
    void u20_rejectsRedstoneChangesWhileClosed() {
        TestGenerator g = generator();
        assertFalse(g.changeRedstoneControlMode(WHEN_POWERED));
        assertSame(IGNORE, g.getRedstoneControlMode());
        load(g, redstoneTag(WHEN_UNPOWERED));
        assertFalse(g.changeRedstoneControlMode(IGNORE));
        assertSame(WHEN_UNPOWERED, g.getRedstoneControlMode());
        assertSilent(g);
    }

    @Test
    void u21_canReadRestoredModesWhileClosed() {
        TestGenerator g = generator();
        load(g, completeTag(100L, WHEN_UNPOWERED, 1));
        assertModes(g, DISABLED, OUTPUT, OUTPUT);
        assertSame(WHEN_UNPOWERED, g.getRedstoneControlMode());
        assertSilent(g);
    }

    @Test
    void u22_closedRuntimeReportsNoSignal() {
        TestGenerator g = generator();
        for (RedstoneControlMode mode : RedstoneControlMode.values()) {
            load(g, redstoneTag(mode));
            assertFalse(g.hasRedstoneSignal());
        }
        assertSilent(g);
    }

    @Test
    void u23_closedRuntimeReportsNoRedstoneBlock() {
        TestGenerator g = generator();
        for (RedstoneControlMode mode : RedstoneControlMode.values()) {
            load(g, redstoneTag(mode));
            assertFalse(g.isRedstoneBlockingOperation(), mode.name());
        }
        assertSilent(g);
    }

    @Test
    void u24_closedStatisticsRemainZeroWithStoredEnergy() {
        TestGenerator g = generator();
        load(g, energyTag(6_000L));
        assertEquals(6_000L, g.getStoredEnergy());
        assertClosedStatistics(g);
        assertSilent(g);
    }

    @Test
    void u25_storedEnergyDoesNotImplyProduction() {
        TestGenerator g = generator();
        load(g, energyTag(CAPACITY));
        assertFalse(g.isProducingEnergy());
        assertSilent(g);
    }

    @Test
    void u26_closedTickDoesNotRunProcess() {
        RecordingGenerationProcess process = new RecordingGenerationProcess();
        TestGenerator g = generator(configurable(), process);
        load(g, energyTag(100L));
        g.serverTick();
        g.serverTick();
        assertEquals(100L, g.getStoredEnergy());
        assertNoProduction(process);
        assertClosedStatistics(g);
        assertSilent(g);
    }

    @Test
    void u27_closedTickDoesNotInvokeProductionHook() {
        TestGenerator g = generator();
        g.serverTick();
        assertTrue(g.events.isEmpty(), g.events.toString());
        assertSilent(g);
    }

    @Test
    void u28_closedRuntimeDoesNotQueryStructurePortsOrSignal() {
        TestGenerator g = generator();
        assertNull(g.getEnergyHandlerAt(POSITION, Direction.NORTH));
        assertNull(g.getEnergyHandlerForSide(Direction.SOUTH));
        assertFalse(g.hasRedstoneSignal());
        assertFalse(g.isRedstoneBlockingOperation());
        assertFalse(g.changeEnergyPortMode(FRONT, DISABLED));
        g.serverTick();
        assertSilent(g);
    }

    @Test
    void u29_onLoadWithoutWorldKeepsRuntimeClosed() {
        RecordingGenerationProcess process = new RecordingGenerationProcess();
        TestGenerator g = generator(configurable(), process);
        g.onLoad();
        g.onLoad();
        assertNull(g.getLevel());
        assertNull(g.getEnergyHandlerForSide(Direction.NORTH));
        assertFalse(g.changeEnergyPortMode(FRONT, DISABLED));
        assertFalse(g.changeRedstoneControlMode(WHEN_POWERED));
        g.serverTick();
        assertNoProduction(process);
        assertClosedStatistics(g);
        assertSilent(g);
    }

    @Test
    void u30_closedRuntimeAcceptsAvailabilityNotifications() {
        TestGenerator g = generator();
        assertDoesNotThrow(g::onEnergyPortAvailabilityChanged);
        assertDoesNotThrow(() -> g.onEnergyPortEndpointChanged(FRONT));
        assertDoesNotThrow(() -> g.onEnergyPortEndpointChanged(UNKNOWN));
        assertThrows(NullPointerException.class, () -> g.onEnergyPortEndpointChanged(null));
        assertDefaults(g);
        assertSilent(g);
    }

    // U-31–U-42: API and orientation

    @Test
    void u31_unknownPortIsDisabled() {
        for (GeneratorConfiguration config : List.of(configurable(), fixed(), empty(FIXED))) {
            TestGenerator g = generator(config, new RecordingGenerationProcess());
            assertSame(DISABLED, g.getEnergyPortMode(UNKNOWN));
            assertSilent(g);
        }
    }

    @Test
    void u32_rejectsNullPortIdForRead() {
        TestGenerator g = generator();
        assertThrows(NullPointerException.class, () -> g.getEnergyPortMode(null));
    }

    @Test
    void u33_rejectsNullPortChangeArgumentsBeforeRuntimeCheck() {
        TestGenerator g = generator();
        assertThrows(NullPointerException.class, () -> g.changeEnergyPortMode(null, OUTPUT));
        assertThrows(NullPointerException.class, () -> g.changeEnergyPortMode(FRONT, null));
        assertDefaults(g);
        assertSilent(g);
    }

    @Test
    void u34_rejectsNullRedstoneModeBeforeRuntimeCheck() {
        TestGenerator g = generator();
        assertThrows(NullPointerException.class, () -> g.changeRedstoneControlMode(null));
        assertSame(IGNORE, g.getRedstoneControlMode());
        assertSilent(g);
    }

    @Test
    void u35_rejectsNullHandlerPosition() {
        TestGenerator g = generator();
        assertThrows(NullPointerException.class, () -> g.getEnergyHandlerAt(null, Direction.NORTH));
    }

    @Test
    void u36_acceptsNullHandlerSide() {
        TestGenerator g = generator();
        assertNull(g.getEnergyHandlerAt(POSITION, null));
        assertNull(g.getEnergyHandlerForSide(null));
        assertSilent(g);
    }

    @Test
    void u37_readsEveryHorizontalFacing() {
        for (Direction facing : List.of(
            Direction.NORTH,
            Direction.EAST,
            Direction.SOUTH,
            Direction.WEST
        )) {
            BlockState state = Blocks.FURNACE.defaultBlockState().setValue(
                BlockStateProperties.HORIZONTAL_FACING,
                facing
            );
            TestGenerator g = new TestGenerator(configurable(), BlockEntityType.FURNACE, state);
            assertSame(facing, g.getFacing());
            assertSilent(g);
        }
    }

    @Test
    void u38_fallsBackToNorthWithoutHorizontalFacing() {
        BlockState state = Blocks.BARREL.defaultBlockState().setValue(
            BlockStateProperties.FACING,
            Direction.UP
        );
        assertFalse(state.hasProperty(BlockStateProperties.HORIZONTAL_FACING));
        TestGenerator g = new TestGenerator(configurable(), BlockEntityType.BARREL, state);
        assertSame(Direction.NORTH, g.getFacing());
        assertSilent(g);
    }

    @Test
    void u39_readsFacingAfterBlockStateChange() {
        TestGenerator g = generator();
        for (Direction facing : List.of(
            Direction.EAST,
            Direction.SOUTH,
            Direction.WEST,
            Direction.NORTH
        )) {
            BlockState state = g.getBlockState().setValue(
                BlockStateProperties.HORIZONTAL_FACING,
                facing
            );
            g.setBlockState(state);
            assertSame(facing, g.getFacing());
            assertSame(state, g.getBlockState());
        }
        assertDefaults(g);
        assertSilent(g);
    }

    @Test
    void u40_rejectsNullBlockState() {
        TestGenerator g = generator();
        BlockState before = g.getBlockState();
        assertThrows(NullPointerException.class, () -> g.setBlockState(null));
        assertSame(before, g.getBlockState());
        assertSilent(g);
    }

    @Test
    void u41_defaultStructureIsReady() {
        TestGenerator g = generator();
        assertTrue(g.isStructureReady());
        assertEquals(1, g.structureChecks);
        assertEquals(0, g.physicalChecks);
    }

    @Test
    void u42_defaultPortAvailabilityRequiresZeroOffset() {
        TestGenerator g = generator();
        for (Direction direction : Direction.values()) {
            assertTrue(g.isEnergyPortPhysicallyAvailable(point(FRONT, direction)));
        }
        for (BlockPos offset : List.of(
            new BlockPos(1, 0, 0),
            new BlockPos(0, -1, 0),
            new BlockPos(0, 0, 1)
        )) {
            assertFalse(g.isEnergyPortPhysicallyAvailable(new EnergyPortPoint(
                FRONT,
                offset,
                Direction.NORTH
            )));
        }
        assertThrows(NullPointerException.class, () -> g.isEnergyPortPhysicallyAvailable(null));
    }

    // U-43–U-52: saving persistent state

    @Test
    void u43_savesParentEnergySection() {
        TestGenerator g = generator();
        load(g, energyTag(6_000L));
        assertSerializedEnergy(save(g), 6_000L);
        assertSilent(g);
    }

    @Test
    void u44_savesAllConfigurableModesAsStrings() {
        TestGenerator g = generator();
        CompoundTag expected = portModes("DISABLED", "OUTPUT", "DISABLED");
        load(g, portRoot(expected));
        CompoundTag saved = assertInstanceOf(CompoundTag.class, save(g).get(PORTS));
        assertEquals(expected, saved);
        for (EnergyPortId id : List.of(FRONT, BACK, TOP)) {
            assertInstanceOf(StringTag.class, saved.get(id.value()));
        }
        assertSilent(g);
    }

    @Test
    void u45_fixedPortsDoNotCreatePortSection() {
        TestGenerator g = generator(fixed(), new RecordingGenerationProcess());
        assertFalse(save(g).contains(PORTS));
        assertSilent(g);
    }

    @Test
    void u46_portlessGeneratorHasNoSerializedPortEntries() {
        TestGenerator fixed = generator(empty(FIXED), new RecordingGenerationProcess());
        assertFalse(save(fixed).contains(PORTS));
        // Empty CONFIGURABLE rules are also legal. No entries may be persisted;
        // the current writer may retain an empty child or omit it.
        TestGenerator configurable = generator(
            empty(CONFIGURABLE),
            new RecordingGenerationProcess()
        );
        Tag section = save(configurable).get(PORTS);
        if (section != null) assertEquals(0, assertInstanceOf(CompoundTag.class, section).size());
        assertSilent(fixed);
        assertSilent(configurable);
    }

    @Test
    void u47_savesEveryRedstoneMode() {
        TestGenerator g = generator();
        for (RedstoneControlMode mode : RedstoneControlMode.values()) {
            load(g, redstoneTag(mode));
            StringTag saved = assertInstanceOf(StringTag.class, save(g).get(REDSTONE));
            assertEquals(mode.name(), saved.value());
        }
        assertSilent(g);
    }

    @Test
    void u48_persistentProcessWritesOnlyItsChild() {
        RecordingPersistentGenerationProcess process = new RecordingPersistentGenerationProcess();
        process.ticks = 81;
        TestGenerator g = generator(configurable(), process);
        CompoundTag root = save(g);
        assertEquals(processSection(81), root.get(PROCESS));
        assertFalse(root.contains(TEST_TICKS));
        assertEquals(1, process.saveCalls);
        assertEquals(0, process.restoreCalls);
        assertNoProduction(process);
        assertSilent(g);
    }

    @Test
    void u49_statelessProcessDoesNotCreateProcessSection() {
        RecordingGenerationProcess process = new RecordingGenerationProcess();
        TestGenerator g = generator(configurable(), process);
        assertFalse(save(g).contains(PROCESS));
        assertNoProduction(process);
        assertSilent(g);
    }

    @Test
    void u50_savesOnlyTheAgreedPersistentSections() {
        RecordingPersistentGenerationProcess process = new RecordingPersistentGenerationProcess();
        TestGenerator g = generator(configurable(), process);
        CompoundTag expected = completeTag(6_000L, WHEN_POWERED, 37);
        load(g, expected);
        assertEquals(expected, save(g));
        assertSilent(g);
    }

    @Test
    void u51_savingDoesNotNotifyOrRunProduction() {
        RecordingPersistentGenerationProcess process = new RecordingPersistentGenerationProcess();
        TestGenerator g = generator(configurable(), process);
        load(g, completeTag(2_000L, WHEN_UNPOWERED, 9));
        g.clearObservations();
        int restores = process.restoreCalls;
        save(g);
        save(g);
        assertEquals(2, process.saveCalls);
        assertEquals(restores, process.restoreCalls);
        assertNoProduction(process);
        assertSilent(g);
    }

    @Test
    void u52_rejectsNullOutput() {
        TestGenerator g = generator();
        assertThrows(NullPointerException.class, () -> g.saveAdditional(null));
        assertSilent(g);
    }

    // U-53–U-80: restoring persistent state

    @Test
    void u53_roundTripsFullStateBetweenIndependentGenerators() {
        RecordingPersistentGenerationProcess firstProcess =
            new RecordingPersistentGenerationProcess();
        RecordingPersistentGenerationProcess secondProcess =
            new RecordingPersistentGenerationProcess();
        TestGenerator first = generator(configurable(), firstProcess);
        TestGenerator second = generator(configurable(), secondProcess);
        load(first, completeTag(6_000L, WHEN_POWERED, 123));
        load(second, completeTag(100L, WHEN_UNPOWERED, 1));
        CompoundTag saved = save(first);
        load(second, saved);
        assertEquals(6_000L, second.getStoredEnergy());
        assertEquals(CAPACITY, second.getEnergyCapacity());
        assertModes(second, DISABLED, OUTPUT, OUTPUT);
        assertSame(WHEN_POWERED, second.getRedstoneControlMode());
        assertEquals(123, secondProcess.ticks);
        assertEquals(2, secondProcess.restoreCalls);
        assertEquals(saved, save(second));
        assertNoProduction(firstProcess);
        assertNoProduction(secondProcess);
        assertSilent(first);
        assertSilent(second);
    }

    @Test
    void u54_restoresOutputEvenWhenDefaultIsDisabled() {
        TestGenerator g = generator();
        assertSame(DISABLED, g.getEnergyPortMode(BACK));
        load(g, portRoot(portModes("OUTPUT", "OUTPUT", "OUTPUT")));
        assertModes(g, OUTPUT, OUTPUT, OUTPUT);
        assertSilent(g);
    }

    @Test
    void u55_restoresDisabledEvenWhenDefaultIsOutput() {
        TestGenerator g = generator();
        load(g, portRoot(portModes("DISABLED", "DISABLED", "DISABLED")));
        assertModes(g, DISABLED, DISABLED, DISABLED);
        assertSilent(g);
    }

    @Test
    void u56_forbiddenInputFallsBackToDisabled() {
        assertInvalidFront(StringTag.valueOf("INPUT"));
    }

    @Test
    void u57_unknownModeFallsBackToDisabled() {
        assertInvalidFront(StringTag.valueOf("UNKNOWN"));
    }

    @Test
    void u58_emptyModeFallsBackToDisabled() {
        assertInvalidFront(StringTag.valueOf(""));
    }

    @Test
    void u59_modeNamesAreCaseSensitive() {
        for (String value : List.of("output", "Output", "disabled", "Disabled")) {
            assertInvalidFront(StringTag.valueOf(value));
        }
    }

    @Test
    void u60_doesNotTrimModeNames() {
        for (String value : List.of(" OUTPUT", "OUTPUT ", "\tOUTPUT", "OUTPUT\n")) {
            assertInvalidFront(StringTag.valueOf(value));
        }
    }

    @Test
    void u61_wrongPortTagTypesFallBackToDisabled() {
        for (Tag value : List.of(
            IntTag.valueOf(123),
            LongTag.valueOf(1L),
            new CompoundTag(),
            new ListTag()
        )) {
            assertInvalidFront(value);
        }
    }

    @Test
    void u62_missingPortKeyRestoresItsDefault() {
        TestGenerator g = generator();
        load(g, portRoot(portModes("DISABLED", "OUTPUT", "DISABLED")));
        CompoundTag section = new CompoundTag();
        section.putString(TOP.value(), "DISABLED");
        load(g, portRoot(section));
        assertModes(g, OUTPUT, DISABLED, DISABLED);
        assertSilent(g);
    }

    @Test
    void u63_emptyPortSectionRestoresAllDefaults() {
        TestGenerator g = generator();
        load(g, portRoot(portModes("DISABLED", "OUTPUT", "DISABLED")));
        load(g, portRoot(new CompoundTag()));
        assertDefaults(g);
        assertSilent(g);
    }

    @Test
    void u64_missingPortSectionRestoresAllDefaults() {
        TestGenerator g = generator();
        load(g, portRoot(portModes("DISABLED", "OUTPUT", "DISABLED")));
        load(g, new CompoundTag());
        assertDefaults(g);
        assertSilent(g);
    }

    @Test
    void u65_wrongPortSectionTypeDisablesEveryPort() {
        TestGenerator g = generator();
        for (Tag section : List.of(
            StringTag.valueOf("OUTPUT"),
            IntTag.valueOf(1),
            LongTag.valueOf(1L),
            new ListTag()
        )) {
            load(g, portRoot(portModes("OUTPUT", "OUTPUT", "OUTPUT")));
            load(g, portRoot(section));
            assertModes(g, DISABLED, DISABLED, DISABLED);
            assertEquals(portModes("DISABLED", "DISABLED", "DISABLED"), save(g).get(PORTS));
        }
        assertSilent(g);
    }

    @Test
    void u66_ignoresUnknownPortIds() {
        TestGenerator g = generator();
        CompoundTag section = portModes("OUTPUT", "OUTPUT", "DISABLED");
        section.putString(UNKNOWN.value(), "OUTPUT");
        section.putInt("removed_port", 123);
        load(g, portRoot(section));
        assertModes(g, OUTPUT, OUTPUT, DISABLED);
        assertSame(DISABLED, g.getEnergyPortMode(UNKNOWN));
        assertEquals(portModes("OUTPUT", "OUTPUT", "DISABLED"), save(g).get(PORTS));
        assertSilent(g);
    }

    @Test
    void u67_oneCorruptPortDoesNotAffectOtherPorts() {
        TestGenerator g = generator();
        CompoundTag section = portModes("OUTPUT", "OUTPUT", "DISABLED");
        section.putInt(FRONT.value(), 77);
        load(g, portRoot(section));
        assertModes(g, DISABLED, OUTPUT, DISABLED);
        assertSilent(g);
    }

    @Test
    void u68_fixedPortsIgnoreSavedConfiguration() {
        TestGenerator g = generator(fixed(), new RecordingGenerationProcess());
        for (Tag section : List.of(
            portModes("DISABLED", "OUTPUT", "OUTPUT"),
            IntTag.valueOf(42),
            new CompoundTag()
        )) {
            load(g, portRoot(section));
            assertModes(g, OUTPUT, DISABLED, DISABLED);
            assertFalse(save(g).contains(PORTS));
        }
        assertSilent(g);
    }

    @Test
    void u69_repeatedClosedRestorationIsIndependentOfPreviousModes() {
        TestGenerator g = generator();
        TestGenerator fresh = generator();
        load(g, portRoot(portModes("DISABLED", "OUTPUT", "DISABLED")));
        load(g, portRoot(IntTag.valueOf(7)));
        assertModes(g, DISABLED, DISABLED, DISABLED);
        CompoundTag section = new CompoundTag();
        section.putString(BACK.value(), "OUTPUT");
        CompoundTag next = portRoot(section);
        load(g, next);
        load(fresh, next);
        assertModes(g, OUTPUT, OUTPUT, OUTPUT);
        assertEquals(save(fresh), save(g));
        assertSilent(g);
        assertSilent(fresh);
    }

    @Test
    void u70_restoresEveryRedstoneMode() {
        TestGenerator g = generator();
        for (RedstoneControlMode mode : List.of(WHEN_POWERED, WHEN_UNPOWERED, IGNORE)) {
            load(g, redstoneTag(mode));
            assertSame(mode, g.getRedstoneControlMode());
        }
        assertSilent(g);
    }

    @Test
    void u71_missingOrInvalidRedstoneModeRestoresIgnore() {
        TestGenerator g = generator();
        List<CompoundTag> invalid = new ArrayList<>();
        invalid.add(new CompoundTag());
        for (Tag value : List.of(
            StringTag.valueOf("UNKNOWN"),
            StringTag.valueOf(""),
            IntTag.valueOf(1),
            new CompoundTag(),
            new ListTag()
        )) {
            CompoundTag root = new CompoundTag();
            root.put(REDSTONE, value);
            invalid.add(root);
        }
        for (CompoundTag root : invalid) {
            load(g, redstoneTag(WHEN_POWERED));
            assertSame(WHEN_POWERED, g.getRedstoneControlMode());
            load(g, root);
            assertSame(IGNORE, g.getRedstoneControlMode());
        }
        assertSilent(g);
    }

    @Test
    void u72_persistentProcessReceivesItsOwnSection() {
        RecordingPersistentGenerationProcess process = new RecordingPersistentGenerationProcess();
        TestGenerator g = generator(configurable(), process);
        CompoundTag root = completeTag(10L, IGNORE, 72);
        root.putInt(TEST_TICKS, 999); // A root-level value must not be used by the process.
        load(g, root);
        assertEquals(72, process.ticks);
        assertEquals(1, process.restoreCalls);
        assertEquals(0, process.saveCalls);
        assertNoProduction(process);
        assertSilent(g);
    }

    @Test
    void u73_missingProcessSectionStillInvokesRestoreWithEmptyInput() {
        RecordingPersistentGenerationProcess process = new RecordingPersistentGenerationProcess();
        TestGenerator g = generator(configurable(), process);
        load(g, completeTag(10L, IGNORE, 81));
        CompoundTag root = energyTag(200L);
        root.putInt(TEST_TICKS, 999);
        load(g, root);
        assertEquals(0, process.ticks);
        assertEquals(2, process.restoreCalls);
        assertEquals(200L, g.getStoredEnergy());
        assertNoProduction(process);
        assertSilent(g);
    }

    @Test
    void u74_wrongProcessSectionTypeStillInvokesRestoreWithEmptyInput() {
        RecordingPersistentGenerationProcess process = new RecordingPersistentGenerationProcess();
        TestGenerator g = generator(configurable(), process);
        for (Tag section : List.of(IntTag.valueOf(1), StringTag.valueOf("bad"), new ListTag())) {
            int restores = process.restoreCalls;
            load(g, completeTag(10L, IGNORE, 81));
            CompoundTag root = energyTag(200L);
            root.put(PROCESS, section);
            root.putInt(TEST_TICKS, 999);
            load(g, root);
            assertEquals(0, process.ticks);
            assertEquals(restores + 2, process.restoreCalls);
        }
        assertNoProduction(process);
        assertSilent(g);
    }

    @Test
    void u75_statelessProcessIgnoresSavedProcessSection() {
        RecordingGenerationProcess process = new RecordingGenerationProcess();
        TestGenerator g = generator(configurable(), process);
        for (Tag section : List.of(processSection(123), IntTag.valueOf(42))) {
            CompoundTag root = energyTag(100L);
            root.put(PROCESS, section);
            load(g, root);
            assertFalse(save(g).contains(PROCESS));
            assertEquals(100L, g.getStoredEnergy());
        }
        assertNoProduction(process);
        assertSilent(g);
    }

    @Test
    void u76_restorationDoesNotMarkBlockEntityDirty() {
        RecordingPersistentGenerationProcess process = new RecordingPersistentGenerationProcess();
        TestGenerator g = generator(configurable(), process);
        load(g, completeTag(6_000L, WHEN_POWERED, 81));
        load(g, new CompoundTag());
        assertFalse(g.events.contains("setChanged"));
        assertSilent(g);
    }

    @Test
    void u77_restorationDoesNotCallEnergyOrPortHooks() {
        TestGenerator g = generator();
        load(g, completeTag(6_000L, WHEN_UNPOWERED, 81));
        assertModes(g, DISABLED, OUTPUT, OUTPUT);
        load(g, portRoot(IntTag.valueOf(42)));
        assertModes(g, DISABLED, DISABLED, DISABLED);
        assertSilent(g);
    }

    @Test
    void u78_restorationDoesNotRunProduction() {
        RecordingPersistentGenerationProcess process = new RecordingPersistentGenerationProcess();
        TestGenerator g = generator(configurable(), process);
        load(g, completeTag(100L, WHEN_POWERED, 81));
        assertEquals(81, process.ticks);
        assertEquals(1, process.restoreCalls);
        assertNoProduction(process);
        assertClosedStatistics(g);
        assertSilent(g);
    }

    @Test
    void u79_restorationWorksWithoutWorldOrAvailabilityQueries() {
        RecordingPersistentGenerationProcess process = new RecordingPersistentGenerationProcess();
        TestGenerator g = generator(configurable(), process);
        assertNull(g.getLevel());
        load(g, completeTag(100L, WHEN_UNPOWERED, 81));
        assertNull(g.getLevel());
        assertEquals(100L, g.getStoredEnergy());
        assertSame(WHEN_UNPOWERED, g.getRedstoneControlMode());
        assertModes(g, DISABLED, OUTPUT, OUTPUT);
        assertEquals(81, process.ticks);
        assertSilent(g);
    }

    @Test
    void u80_rejectsNullInput() {
        RecordingPersistentGenerationProcess process = new RecordingPersistentGenerationProcess();
        TestGenerator g = generator(configurable(), process);
        assertThrows(NullPointerException.class, () -> g.loadAdditional(null));
        assertEquals(0, process.restoreCalls);
        assertSilent(g);
    }

}
