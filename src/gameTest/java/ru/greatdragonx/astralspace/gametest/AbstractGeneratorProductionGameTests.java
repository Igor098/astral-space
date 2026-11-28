package ru.greatdragonx.astralspace.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Rotation;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import ru.greatdragonx.astralspace.AstralSpace;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static ru.greatdragonx.astralspace.gametest.GameTestGeneratorBlockEntity.*;

/**
 * G-26...G-41: production, ordering, boundaries and failure paths.
 */
public final class AbstractGeneratorProductionGameTests {
    private static final BlockPos POS = new BlockPos(1, 1, 1);
    private static final BlockPos SINK = new BlockPos(1, 1, 0);
    private static final Identifier ENVIRONMENT = id("abstract_generator/production_environment");
    private static final Identifier STRUCTURE = id("gametest/empty_3x3x3");
    static final DeferredRegister<Consumer<GameTestHelper>> TEST_FUNCTIONS =
        DeferredRegister.create(BuiltInRegistries.TEST_FUNCTION, AstralSpace.MOD_ID);

    private record Entry(
        Identifier id, DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> function
    ) {}

    private static final List<Entry> TESTS = new ArrayList<>();

    static {
        add("g26_full_production", AbstractGeneratorProductionGameTests::g26);
        add("g27_full_buffer", AbstractGeneratorProductionGameTests::g27);
        add("g28_one_fe_short", AbstractGeneratorProductionGameTests::g28);
        add("g29_exact_space", AbstractGeneratorProductionGameTests::g29);
        add("g30_prepare_false", AbstractGeneratorProductionGameTests::g30);
        add("g31_completion_after_energy", AbstractGeneratorProductionGameTests::g31);
        add("g32_completion_once_per_tick", AbstractGeneratorProductionGameTests::g32);
        add("g33_first_push_frees_space", AbstractGeneratorProductionGameTests::g33);
        add("g34_second_push_new_energy", AbstractGeneratorProductionGameTests::g34);
        add("g35_no_second_push_on_failure", AbstractGeneratorProductionGameTests::g35);
        add("g36_exhausted_budget_still_produces", AbstractGeneratorProductionGameTests::g36);
        add("g37_partial_production_fail_fast", AbstractGeneratorProductionGameTests::g37);
        add("g38_prepare_exception", AbstractGeneratorProductionGameTests::g38);
        add("g39_hook_between_completion_and_push", AbstractGeneratorProductionGameTests::g39);
        add("g40_paused_hook_false", AbstractGeneratorProductionGameTests::g40);
        add("g41_production_rate_next_tick", AbstractGeneratorProductionGameTests::g41);
    }

    private AbstractGeneratorProductionGameTests() {}

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

    private static void prepare(
        GameTestHelper h,
        GameTestGeneratorBlockEntity g,
        int stored,
        boolean allowProduction
    )
    {
        equal(h, stored, g.charge(stored), "Fixture charge");
        g.process.allowProduction = allowProduction;
        g.resetObservations();
    }

    private static void assertProduction(
        GameTestHelper h,
        GameTestGeneratorBlockEntity g,
        long stored,
        int prepareCalls,
        int completionCalls,
        int rate
    )
    {
        equal(h, stored, g.getStoredEnergy(), "Stored FE");
        equal(h, prepareCalls, g.process.prepareCalls, "Prepare calls");
        equal(h, completionCalls, g.process.completionCalls, "Completion calls");
        equal(h, rate, g.getCurrentProductionRate(), "Production rate");
        check(h, g.isProducingEnergy() == (rate > 0), "isProducingEnergy disagrees with rate");
    }

    private static void events(GameTestHelper h, GameTestGeneratorBlockEntity g, String... expected)
    {
        check(
            h,
            g.events.equals(List.of(expected)),
            "Events: expected=" + List.of(expected) + ", actual=" + g.events
        );
    }

    private static GameTestRecordingEnergySink sink(GameTestHelper h) {
        var sink = GameTestRecordingEnergySink.place(h, SINK, 10_000);
        // Guard the test fixture: never silently pass with a missing receiver.
        if (h.getLevel().getCapability(
            Capabilities.Energy.BLOCK,
            h.absolutePos(SINK),
            Direction.SOUTH
        ) != sink)
        {
            sink.close();
            throw new IllegalStateException("Recording sink capability not registered");
        }
        return sink;
    }

    private static void g26(GameTestHelper h) {
        withReadyGenerator(
            h, g -> {
                prepare(h, g, 0, true);
                g.tickOnce();
                assertProduction(h, g, GENERATION, 1, 1, GENERATION);
                equal(h, 1, g.process.completedTicks, "Completed production ticks");
                h.succeed();
            }
        );
    }

    private static void g27(GameTestHelper h) {
        withReadyGenerator(
            h, g -> {
                prepare(h, g, (int) CAPACITY, true);
                g.tickOnce();
                assertProduction(h, g, CAPACITY, 0, 0, 0);
                equal(h, 0, g.redstoneReads, "Space gate precedes redstone read");
                events(h, g, "producing:false");
                h.succeed();
            }
        );
    }

    private static void g28(GameTestHelper h) {
        withReadyGenerator(
            h, g -> {
                int stored = (int) CAPACITY - GENERATION + 1;
                prepare(h, g, stored, true);
                g.tickOnce();
                assertProduction(h, g, stored, 0, 0, 0);
                equal(h, 0, g.process.completedTicks, "No partial production tick");
                events(h, g, "producing:false");
                h.succeed();
            }
        );
    }

    private static void g29(GameTestHelper h) {
        withReadyGenerator(
            h, g -> {
                prepare(h, g, (int) CAPACITY - GENERATION, true);
                g.tickOnce();
                assertProduction(h, g, CAPACITY, 1, 1, GENERATION);
                h.succeed();
            }
        );
    }

    private static void g30(GameTestHelper h) {
        withReadyGenerator(
            h, g -> {
                prepare(h, g, 500, false);
                g.tickOnce();
                assertProduction(h, g, 500, 1, 0, 0);
                equal(h, 0, g.process.completedTicks, "Process must not advance");
                events(h, g, "prepare", "producing:false");
                h.succeed();
            }
        );
    }

    private static void g31(GameTestHelper h) {
        withReadyGenerator(
            h, g -> {
                prepare(h, g, 100, true);
                g.process.beforePrepareReturns = () -> equal(
                    h,
                    100,
                    g.getStoredEnergy(),
                    "FE during Prepare"
                );
                g.tickOnce();
                assertProduction(h, g, 140, 1, 1, GENERATION);
                events(h, g, "prepare", "changed", "energy:140", "complete:140", "producing:true");
                h.succeed();
            }
        );
    }

    private static void g32(GameTestHelper h) {
        withReadyGenerator(
            h, g -> {
                prepare(h, g, 0, true);
                g.tickOnce();
                assertProduction(h, g, 40, 1, 1, GENERATION);
                h.runAfterDelay(
                    1, () -> {
                        g.tickOnce();
                        assertProduction(h, g, 80, 2, 2, GENERATION);
                        equal(
                            h,
                            2,
                            g.process.completedTicks,
                            "Exactly one Completion per successful tick"
                        );
                        h.succeed();
                    }
                );
            }
        );
    }

    private static void g33(GameTestHelper h) {
        withReadyGenerator(
            h, g -> {
                try (var receiver = sink(h)) {
                    prepare(h, g, (int) CAPACITY, true);
                    g.process.beforePrepareReturns = () -> {
                        equal(
                            h,
                            MAX_OUTPUT,
                            receiver.getAmountAsLong(),
                            "Push committed before Prepare"
                        );
                        equal(
                            h,
                            CAPACITY - MAX_OUTPUT,
                            g.getStoredEnergy(),
                            "Space freed before Prepare"
                        );
                    };
                    g.tickOnce();
                    assertProduction(h, g, CAPACITY - MAX_OUTPUT + GENERATION, 1, 1, GENERATION);
                    equal(
                        h,
                        MAX_OUTPUT,
                        receiver.getAmountAsLong(),
                        "First Push consumes global budget"
                    );
                }
                h.succeed();
            }
        );
    }

    private static void g34(GameTestHelper h) {
        withReadyGenerator(
            h, g -> {
                try (var receiver = sink(h)) {
                    prepare(h, g, 0, true);
                    g.process.beforePrepareReturns = () -> equal(
                        h,
                        0,
                        receiver.getAmountAsLong(),
                        "First discovery must roll back its insertion"
                    );
                    g.tickOnce();
                    assertProduction(h, g, 0, 1, 1, GENERATION);
                    equal(
                        h,
                        GENERATION,
                        receiver.getAmountAsLong(),
                        "New FE delivered in same tick"
                    );
                    check(
                        h,
                        receiver.insertCalls() >= 3,
                        "Expected two probes and actual transfer"
                    );
                }
                h.succeed();
            }
        );
    }

    private static void g35(GameTestHelper h) {
        withReadyGenerator(
            h, g -> {
                try (var receiver = sink(h)) {
                    prepare(h, g, 0, false);
                    g.tickOnce();
                    assertProduction(h, g, 0, 1, 0, 0);
                    equal(h, 0, receiver.getAmountAsLong(), "No actual transfer");
                    // The first pass probes even with an empty buffer; no second pass may run.
                    equal(
                        h,
                        1,
                        receiver.insertCalls(),
                        "Only one discovery pass on failed production"
                    );
                    events(h, g, "prepare", "producing:false");
                }
                h.succeed();
            }
        );
    }

    private static void g36(GameTestHelper h) {
        withReadyGenerator(
            h, g -> {
                try (var receiver = sink(h)) {
                    prepare(h, g, 500, true);
                    equal(
                        h,
                        MAX_OUTPUT,
                        pull(requireCap(h, Direction.NORTH), MAX_OUTPUT),
                        "Exhaust budget via Pull"
                    );
                    g.resetObservations();
                    g.tickOnce();
                    assertProduction(h, g, 500 - MAX_OUTPUT + GENERATION, 1, 1, GENERATION);
                    equal(h, 0, receiver.getAmountAsLong(), "No Push with exhausted budget");
                    equal(
                        h,
                        1,
                        receiver.insertCalls(),
                        "First discovery only; second Push skipped"
                    );
                }
                h.succeed();
            }
        );
    }

    private static void g37(GameTestHelper h) {
        withReadyGenerator(
            h, g -> {
                // Deliberately violate the process contract to reach the fail-fast branch.
                // This is not a supported production behavior and no general rollback is promised.
                prepare(h, g, (int) CAPACITY - GENERATION, true);
                g.process.beforePrepareReturns = () -> equal(
                    h,
                    1,
                    g.charge(1),
                    "Intentional invalid mutation"
                );
                IllegalStateException failure = null;
                try {
                    g.tickOnce();
                } catch (IllegalStateException ex) {
                    failure = ex;
                }
                check(
                    h,
                    failure != null &&
                        failure.getMessage() != null &&
                        failure.getMessage().contains("Unexpected energy production amount"),
                    "Expected production invariant failure"
                );
                assertProduction(h, g, CAPACITY, 1, 0, 0);
                equal(h, 0, g.process.completedTicks, "No Completion after partial write");
                check(
                    h,
                    g.events.stream()
                        .noneMatch(e -> e.startsWith("complete:") || e.startsWith("producing:")),
                    "No Completion or production hook after failure: " + g.events
                );
                h.succeed();
            }
        );
    }

    private static void g38(GameTestHelper h) {
        withReadyGenerator(
            h, g -> {
                prepare(h, g, 100, true);
                var expected = new IllegalStateException("Intentional Prepare exception");
                g.process.beforePrepareReturns = () -> {
                    throw expected;
                };
                RuntimeException actual = null;
                try {
                    g.tickOnce();
                } catch (RuntimeException ex) {
                    actual = ex;
                }
                check(h, actual == expected, "Prepare exception must propagate unchanged");
                assertProduction(h, g, 100, 1, 0, 0);
                events(h, g, "prepare");
                h.succeed();
            }
        );
    }

    private static void g39(GameTestHelper h) {
        withReadyGenerator(
            h, g -> {
                try (var receiver = sink(h)) {
                    prepare(h, g, 0, true);
                    g.tickOnce();
                    assertProduction(h, g, 0, 1, 1, GENERATION);
                    equal(h, GENERATION, receiver.getAmountAsLong(), "Second Push receiver");
                    events(
                        h,
                        g,
                        "prepare",
                        "changed",
                        "energy:40",
                        "complete:40",
                        "producing:true",
                        "changed",
                        "energy:0"
                    );
                }
                h.succeed();
            }
        );
    }

    private static void g40(GameTestHelper h) {
        withReadyGenerator(
            h, g -> {
                prepare(h, g, 0, false);
                g.tickOnce();
                assertProduction(h, g, 0, 1, 0, 0);
                events(h, g, "prepare", "producing:false");
                h.runAfterDelay(
                    1, () -> {
                        g.setStructureReady(false);
                        g.resetObservations();
                        g.process.allowProduction = true;
                        g.tickOnce();
                        assertProduction(h, g, 0, 0, 0, 0);
                        events(h, g, "producing:false");
                        h.succeed();
                    }
                );
            }
        );
    }

    private static void g41(GameTestHelper h) {
        withReadyGenerator(
            h, g -> {
                prepare(h, g, 0, true);
                g.tickOnce();
                assertProduction(h, g, 40, 1, 1, GENERATION);
                h.runAfterDelay(
                    1, () -> {
                        // Until the next generator tick, rate describes the last completed tick.
                        equal(
                            h,
                            GENERATION,
                            g.getCurrentProductionRate(),
                            "Last recorded rate before next tick"
                        );
                        g.resetObservations();
                        g.process.allowProduction = false;
                        g.tickOnce();
                        assertProduction(h, g, 40, 1, 0, 0);
                        h.runAfterDelay(
                            1, () -> {
                                g.resetObservations();
                                g.process.allowProduction = true;
                                g.tickOnce();
                                assertProduction(h, g, 80, 1, 1, GENERATION);
                                h.succeed();
                            }
                        );
                    }
                );
            }
        );
    }
}
