package ru.greatdragonx.astralspace.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import ru.greatdragonx.astralspace.AstralSpace;
import ru.greatdragonx.astralspace.common.machine.redstone.RedstoneControlMode;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static ru.greatdragonx.astralspace.gametest.GameTestGeneratorBlockEntity.GENERATION;
import static ru.greatdragonx.astralspace.gametest.GameTestGeneratorBlockEntity.MAX_OUTPUT;

/**
 * G-60...G-67: real redstone signals, production gate and persistence.
 */
public final class AbstractGeneratorRedstoneGameTests {
    private static final BlockPos POS = new BlockPos(1, 1, 1);
    private static final BlockPos SINK = new BlockPos(1, 1, 0);
    private static final BlockPos SIGNAL = new BlockPos(0, 1, 1);
    private static final Identifier ENVIRONMENT = id("abstract_generator/redstone_environment");
    private static final Identifier STRUCTURE = id("gametest/empty_3x3x3");
    static final DeferredRegister<Consumer<GameTestHelper>> TEST_FUNCTIONS =
        DeferredRegister.create(BuiltInRegistries.TEST_FUNCTION, AstralSpace.MOD_ID);

    private record Entry(
        Identifier id, DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> function
    ) {}

    private static final List<Entry> TESTS = new ArrayList<>();

    static {
        add("g60_all_mode_signal_combinations", AbstractGeneratorRedstoneGameTests::g60);
        add("g61_blocked_process_does_not_advance", AbstractGeneratorRedstoneGameTests::g61);
        add("g62_blocked_production_allows_push", AbstractGeneratorRedstoneGameTests::g62);
        add("g63_blocked_production_allows_pull", AbstractGeneratorRedstoneGameTests::g63);
        add("g64_signal_is_live", AbstractGeneratorRedstoneGameTests::g64);
        add("g65_mode_change_marks_dirty_once", AbstractGeneratorRedstoneGameTests::g65);
        add("g66_same_mode_is_silent", AbstractGeneratorRedstoneGameTests::g66);
        add("g67_mode_restored_before_runtime", AbstractGeneratorRedstoneGameTests::g67);
    }

    private AbstractGeneratorRedstoneGameTests() {}

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(AstralSpace.MOD_ID, path);
    }

    private static void add(String name, Consumer<GameTestHelper> body) {
        String path = "abstract_generator/" + name;
        TESTS.add(new Entry(id(path), TEST_FUNCTIONS.register(path, () -> body)));
    }

    static void registerGameTests(RegisterGameTestsEvent event) {
        Holder<TestEnvironmentDefinition> environment = event.registerEnvironment(
            ENVIRONMENT,
            new TestEnvironmentDefinition.AllOf(List.of())
        );
        for (Entry entry : TESTS) {
            event.registerTest(
                entry.id(), new FunctionGameTestInstance(
                    entry.function().getKey(),
                    new TestData<>(
                        environment,
                        STRUCTURE,
                        100,
                        0,
                        true,
                        Rotation.NONE,
                        false,
                        1,
                        1,
                        false
                    )
                )
            );
        }
    }

    private static GameTestGeneratorBlockEntity place(GameTestHelper h, boolean fixed) {
        var block = fixed ? GameTestGeneratorRegistry.FIXED_GENERATOR.get() :
            GameTestGeneratorRegistry.CONFIGURABLE_GENERATOR.get();
        h.setBlock(POS, block.defaultBlockState());
        return h.getBlockEntity(POS, GameTestGeneratorBlockEntity.class);
    }

    private static void withReadyGenerator(
        GameTestHelper h,
        Consumer<GameTestGeneratorBlockEntity> body
    )
    {
        withReadyGenerator(h, false, body);
    }

    private static void withReadyGenerator(
        GameTestHelper h,
        boolean fixed,
        Consumer<GameTestGeneratorBlockEntity> body
    )
    {
        GameTestGeneratorBlockEntity generator = place(h, fixed);
        // Do not call onLoad here. NeoForge invokes it for the freshly added BE.
        h.runAfterDelay(
            2, () -> {
                check(
                    h,
                    h.getLevel().getBlockEntity(h.absolutePos(POS)) == generator,
                    "Fixture replaced unexpectedly"
                );
                check(
                    h,
                    cap(h, Direction.NORTH) != null,
                    "Runtime did not open through NeoForge lifecycle"
                );
                body.accept(generator);
            }
        );
    }

    private static EnergyHandler cap(GameTestHelper h, Direction side) {
        return h.getLevel().getCapability(Capabilities.Energy.BLOCK, h.absolutePos(POS), side);
    }

    private static EnergyHandler requireCap(GameTestHelper h, Direction side) {
        EnergyHandler result = cap(h, side);
        if (result == null) throw new IllegalStateException("Missing capability: " + side);
        return result;
    }

    private static void check(GameTestHelper h, boolean condition, String message) {
        if (!condition) h.fail(message);
    }

    private static void equal(GameTestHelper h, long expected, long actual, String message) {
        check(h, expected == actual, message + ": expected=" + expected + ", actual=" + actual);
    }

    private static int pull(EnergyHandler handler, int amount) {
        try (Transaction tx = Transaction.openRoot()) {
            int extracted = handler.extract(amount, tx);
            tx.commit();
            return extracted;
        }
    }

    private static void signal(GameTestHelper h, boolean powered) {
        h.setBlock(SIGNAL, powered ? Blocks.REDSTONE_BLOCK : Blocks.AIR);
    }

    private static void mode(
        GameTestHelper h,
        GameTestGeneratorBlockEntity g,
        RedstoneControlMode mode
    )
    {
        boolean changed = g.getRedstoneControlMode() != mode;
        check(
            h,
            g.changeRedstoneControlMode(mode) == changed,
            "Unexpected result changing mode to " + mode
        );
        check(h, g.getRedstoneControlMode() == mode, "Unexpected mode after change");
    }

    private static void assertBlocked(GameTestHelper h, GameTestGeneratorBlockEntity g) {
        check(h, g.isRedstoneBlockingOperation(), "Redstone must block production");
        equal(h, 0, g.process.prepareCalls, "Prepare must not run");
        equal(h, 0, g.process.completionCalls, "Completion must not run");
        equal(h, 0, g.getCurrentProductionRate(), "Blocked production rate");
        check(h, !g.isProducingEnergy(), "Blocked generator reports production");
    }

    private record ModeCase(RedstoneControlMode mode, boolean powered, boolean blocked) {}

    private static final List<ModeCase> CASES = List.of(
        new ModeCase(RedstoneControlMode.IGNORE, false, false),
        new ModeCase(RedstoneControlMode.IGNORE, true, false),
        new ModeCase(RedstoneControlMode.WHEN_POWERED, false, true),
        new ModeCase(RedstoneControlMode.WHEN_POWERED, true, false),
        new ModeCase(RedstoneControlMode.WHEN_UNPOWERED, false, false),
        new ModeCase(RedstoneControlMode.WHEN_UNPOWERED, true, true)
    );

    private static void g60(GameTestHelper h) {
        withReadyGenerator(h, g -> runCase(h, g, 0));
    }

    private static void runCase(GameTestHelper h, GameTestGeneratorBlockEntity g, int index) {
        ModeCase c = CASES.get(index);
        mode(h, g, c.mode());
        signal(h, c.powered());
        g.process.allowProduction = true;
        g.resetObservations();
        check(h, g.hasRedstoneSignal() == c.powered(), "Real signal mismatch: " + c);
        check(h, g.isRedstoneBlockingOperation() == c.blocked(), "Blocking mismatch: " + c);
        long before = g.getStoredEnergy();
        int completed = g.process.completedTicks;
        g.tickOnce();
        int expectedCalls = c.blocked() ? 0 : 1;
        int expectedEnergy = c.blocked() ? 0 : GENERATION;
        equal(h, before + expectedEnergy, g.getStoredEnergy(), "Energy: " + c);
        equal(h, expectedCalls, g.process.prepareCalls, "Prepare: " + c);
        equal(h, expectedCalls, g.process.completionCalls, "Completion: " + c);
        equal(h, completed + expectedCalls, g.process.completedTicks, "Progress: " + c);
        equal(h, expectedEnergy, g.getCurrentProductionRate(), "Rate: " + c);
        check(h, g.isProducingEnergy() == !c.blocked(), "Producing: " + c);
        check(
            h,
            g.events.stream()
                .filter(e -> e.startsWith("producing:"))
                .toList()
                .equals(List.of("producing:" + !c.blocked())),
            "Production hook: " + c + " " + g.events
        );
        if (index + 1 == CASES.size()) h.succeed();
        else h.runAfterDelay(1, () -> runCase(h, g, index + 1));
    }

    private static void g61(GameTestHelper h) {
        withReadyGenerator(
            h, g -> {
                signal(h, true);
                mode(h, g, RedstoneControlMode.WHEN_UNPOWERED);
                equal(h, 500, g.charge(500), "Fixture charge");
                g.process.allowProduction = true;
                g.process.completedTicks = 7;
                g.resetObservations();
                g.tickOnce();
                assertBlocked(h, g);
                equal(h, 500, g.getStoredEnergy(), "FE must not increase");
                equal(h, 7, g.process.completedTicks, "Existing process state must not advance");
                check(
                    h,
                    g.events.equals(List.of("producing:false")),
                    "Unexpected events: " + g.events
                );
                h.succeed();
            }
        );
    }

    private static void g62(GameTestHelper h) {
        withReadyGenerator(
            h, g -> {
                signal(h, true);
                mode(h, g, RedstoneControlMode.WHEN_UNPOWERED);
                equal(h, 500, g.charge(500), "Fixture charge");
                g.process.allowProduction = true;
                g.resetObservations();
                try (var sink = GameTestRecordingEnergySink.place(h, SINK, 1000)) {
                    check(
                        h,
                        h.getLevel()
                            .getCapability(
                                Capabilities.Energy.BLOCK,
                                h.absolutePos(SINK),
                                Direction.SOUTH
                            ) ==
                            sink,
                        "Receiver not registered"
                    );
                    g.tickOnce();
                    assertBlocked(h, g);
                    equal(h, MAX_OUTPUT, sink.getAmountAsLong(), "Push must remain available");
                    equal(h, 500 - MAX_OUTPUT, g.getStoredEnergy(), "Energy conservation");
                    equal(h, 0, g.process.completedTicks, "Process must not advance");
                }
                h.succeed();
            }
        );
    }

    private static void g63(GameTestHelper h) {
        withReadyGenerator(
            h, g -> {
                signal(h, false);
                mode(h, g, RedstoneControlMode.WHEN_POWERED);
                equal(h, 500, g.charge(500), "Fixture charge");
                g.process.allowProduction = true;
                g.resetObservations();
                g.tickOnce();
                assertBlocked(h, g);
                equal(
                    h,
                    MAX_OUTPUT,
                    pull(requireCap(h, Direction.NORTH), MAX_OUTPUT),
                    "Pull while blocked"
                );
                equal(h, 500 - MAX_OUTPUT, g.getStoredEnergy(), "Remaining FE");
                equal(h, 0, pull(requireCap(h, Direction.SOUTH), 1), "Shared budget exhausted");
                h.succeed();
            }
        );
    }

    private static void g64(GameTestHelper h) {
        withReadyGenerator(
            h, g -> {
                mode(h, g, RedstoneControlMode.WHEN_POWERED);
                g.resetObservations();
                long tick = h.getLevel().getGameTime();
                for (boolean powered : new boolean[]{false, true, false, true}) {
                    signal(h, powered);
                    check(h, g.hasRedstoneSignal() == powered, "Signal was cached");
                    check(
                        h,
                        g.isRedstoneBlockingOperation() == !powered,
                        "Blocking decision was cached"
                    );
                }
                equal(
                    h,
                    tick,
                    h.getLevel().getGameTime(),
                    "All signal changes occur in same game tick"
                );
                equal(h, 8, g.redstoneReads, "Every query must read current signal");
                check(h, g.events.isEmpty(), "Reading signal invoked machine hooks: " + g.events);
                h.succeed();
            }
        );
    }

    private static void g65(GameTestHelper h) {
        withReadyGenerator(
            h, g -> {
                for (var next : List.of(
                    RedstoneControlMode.WHEN_POWERED,
                    RedstoneControlMode.WHEN_UNPOWERED,
                    RedstoneControlMode.IGNORE
                )) {
                    g.resetObservations();
                    mode(h, g, next);
                    equal(h, 1, g.changedCalls, "Exactly one dirty notification for " + next);
                    check(
                        h,
                        g.events.equals(List.of("changed")),
                        "Unexpected callbacks: " + g.events
                    );
                    equal(h, 0, g.redstoneReads, "Changing mode should not read signal");
                    equal(h, 0, g.structureChecks, "Changing mode should not refresh ports");
                }
                h.succeed();
            }
        );
    }

    private static void g66(GameTestHelper h) {
        withReadyGenerator(
            h, g -> {
                for (var next : RedstoneControlMode.values()) {
                    mode(h, g, next);
                    g.resetObservations();
                    check(h, !g.changeRedstoneControlMode(next), "Same mode must not change state");
                    equal(h, 0, g.changedCalls, "Repeated mode dirtied BE");
                    equal(h, 0, g.redstoneReads, "Repeated mode read signal");
                    check(h, g.events.isEmpty(), "Repeated mode invoked hooks: " + g.events);
                }
                h.succeed();
            }
        );
    }

    private static void g67(GameTestHelper h) {
        withReadyGenerator(
            h, original -> {
                signal(h, false);
                mode(h, original, RedstoneControlMode.WHEN_POWERED);
                equal(h, 500, original.charge(500), "Fixture charge");
                original.process.completedTicks = 7;
                CompoundTag tag = original.saveWithFullMetadata(h.getLevel().registryAccess());
                check(
                    h,
                    tag.getStringOr("redstone_control_mode", "").equals("WHEN_POWERED"),
                    "Wrong saved mode"
                );
                BlockEntity loaded = BlockEntity.loadStatic(
                    original.getBlockPos(),
                    original.getBlockState(),
                    tag,
                    h.getLevel().registryAccess()
                );
                if (!(loaded instanceof GameTestGeneratorBlockEntity restored)) {
                    h.fail("Could not restore generator");
                    return;
                }
                check(h, restored != original, "Must restore a new BE");
                check(
                    h,
                    restored.getRedstoneControlMode() == RedstoneControlMode.WHEN_POWERED,
                    "Wrong restored mode"
                );
                equal(h, 0, restored.changedCalls, "Restore must not mark dirty");
                equal(h, 0, restored.redstoneReads, "Restore must not read signal");
                check(
                    h,
                    restored.events.isEmpty(),
                    "Restore must not invoke hooks: " + restored.events
                );
                check(
                    h,
                    restored.getEnergyHandlerForSide(Direction.NORTH) == null,
                    "Restored runtime opened early"
                );
                h.getLevel().setBlockEntity(restored);
                h.runAfterDelay(
                    2, () -> {
                        check(
                            h,
                            h.getLevel().getBlockEntity(h.absolutePos(POS)) == restored,
                            "Restored BE not installed"
                        );
                        requireCap(h, Direction.NORTH);
                        restored.process.allowProduction = true;
                        restored.resetObservations();
                        restored.tickOnce();
                        assertBlocked(h, restored);
                        equal(
                            h,
                            500,
                            restored.getStoredEnergy(),
                            "Restored energy unchanged while blocked"
                        );
                        equal(
                            h,
                            7,
                            restored.process.completedTicks,
                            "Restored progress unchanged while blocked"
                        );
                        h.runAfterDelay(
                            1, () -> {
                                signal(h, true);
                                restored.tickOnce();
                                equal(
                                    h,
                                    500 + GENERATION,
                                    restored.getStoredEnergy(),
                                    "Production resumes on live signal"
                                );
                                equal(
                                    h,
                                    8,
                                    restored.process.completedTicks,
                                    "Restored process resumes"
                                );
                                equal(h, 1, restored.process.prepareCalls, "Prepare after signal");
                                equal(
                                    h,
                                    1,
                                    restored.process.completionCalls,
                                    "Completion after signal"
                                );
                                h.succeed();
                            }
                        );
                    }
                );
            }
        );
    }
}
