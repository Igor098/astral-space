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
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import ru.greatdragonx.astralspace.AstralSpace;
import ru.greatdragonx.astralspace.common.energy.port.EnergyPortMode;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static ru.greatdragonx.astralspace.gametest.GameTestGeneratorBlockEntity.*;

/**
 * G-10..G-25: actual server-side energy capabilities and port mode changes.
 */
public final class AbstractGeneratorPortsGameTests {
    private static final BlockPos POS = new BlockPos(1, 1, 1);
    private static final Identifier ENVIRONMENT = id("abstract_generator/ports_environment");
    private static final Identifier STRUCTURE = id("gametest/empty_3x3x3");
    static final DeferredRegister<Consumer<GameTestHelper>> TEST_FUNCTIONS =
        DeferredRegister.create(BuiltInRegistries.TEST_FUNCTION, AstralSpace.MOD_ID);

    private record Entry(
        Identifier id, DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> function
    ) {}

    private static final List<Entry> TESTS = new ArrayList<>();

    static {
        add("g10_allowed_capability", AbstractGeneratorPortsGameTests::g10);
        add("g11_unknown_side", AbstractGeneratorPortsGameTests::g11);
        add("g12_disabled_port", AbstractGeneratorPortsGameTests::g12);
        add("g13_stable_handler", AbstractGeneratorPortsGameTests::g13);
        add("g14_output_refuses_insert", AbstractGeneratorPortsGameTests::g14);
        add("g15_shared_buffer", AbstractGeneratorPortsGameTests::g15);
        add("g16_disable_output", AbstractGeneratorPortsGameTests::g16);
        add("g17_enable_output", AbstractGeneratorPortsGameTests::g17);
        add("g18_reject_input", AbstractGeneratorPortsGameTests::g18);
        add("g19_fixed_port", AbstractGeneratorPortsGameTests::g19);
        add("g20_unchanged_mode", AbstractGeneratorPortsGameTests::g20);
        add("g21_configuration_callback", AbstractGeneratorPortsGameTests::g21);
        add("g22_old_handler_disabled", AbstractGeneratorPortsGameTests::g22);
        add("g23_reuse_after_enable", AbstractGeneratorPortsGameTests::g23);
        add("g24_cache_invalidation_disable", AbstractGeneratorPortsGameTests::g24);
        add("g25_cached_null_invalidation_enable", AbstractGeneratorPortsGameTests::g25);
    }

    private AbstractGeneratorPortsGameTests() {}

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

    private static void g10(GameTestHelper h) {
        withReadyGenerator(
            h, g -> {
                for (Direction side : List.of(Direction.NORTH, Direction.SOUTH, Direction.UP)) {
                    EnergyHandler handler = requireCap(h, side);
                    check(
                        h,
                        handler == g.getEnergyHandlerAt(h.absolutePos(POS), side),
                        "Registered provider does not delegate to the correct endpoint: " + side
                    );
                    equal(h, CAPACITY, handler.getCapacityAsLong(), "Capacity at " + side);
                    equal(h, 0, handler.getAmountAsLong(), "Initial amount at " + side);
                }
                h.succeed();
            }
        );
    }

    private static void g11(GameTestHelper h) {
        withReadyGenerator(
            h, g -> {
                check(h, cap(h, Direction.WEST) == null, "Unregistered WEST has a capability");
                check(h, cap(h, Direction.DOWN) == null, "Unregistered DOWN has a capability");
                check(h, cap(h, null) == null, "Unsided capability must not be exposed");
                check(
                    h,
                    g.getEnergyHandlerAt(
                        h.absolutePos(POS).relative(Direction.WEST),
                        Direction.NORTH
                    ) == null,
                    "Port exposed at an unrelated world position"
                );
                h.succeed();
            }
        );
    }

    private static void g12(GameTestHelper h) {
        withReadyGenerator(
            h, g -> {
                check(h, g.getEnergyPortMode(LEFT) == EnergyPortMode.DISABLED, "LEFT default mode");
                check(h, cap(h, Direction.EAST) == null, "Disabled LEFT exposed a capability");
                check(
                    h,
                    g.getEnergyHandlerForSide(Direction.EAST) == null,
                    "Disabled LEFT direct provider"
                );
                check(h, cap(h, Direction.NORTH) != null, "Other output must remain available");
                h.succeed();
            }
        );
    }

    private static void g13(GameTestHelper h) {
        withReadyGenerator(
            h, g -> {
                EnergyHandler front = requireCap(h, Direction.NORTH);
                EnergyHandler back = requireCap(h, Direction.SOUTH);
                check(h, front != back, "Distinct ports must not share endpoint wrappers");
                check(
                    h,
                    front == cap(h, Direction.NORTH),
                    "Repeated query recreated FRONT endpoint"
                );
                check(h, back == cap(h, Direction.SOUTH), "Repeated query recreated BACK endpoint");
                equal(h, 200, g.charge(200), "Fixture charge");
                check(h, front == cap(h, Direction.NORTH), "Energy change recreated endpoint");
                equal(h, 200, front.getAmountAsLong(), "Stable endpoint sees new amount");
                h.succeed();
            }
        );
    }

    private static void g14(GameTestHelper h) {
        withReadyGenerator(
            h, g -> {
                g.charge(500);
                EnergyHandler front = requireCap(h, Direction.NORTH);
                g.resetObservations();
                try (Transaction tx = Transaction.openRoot()) {
                    equal(h, 0, front.insert(40, tx), "OUTPUT accepted external energy");
                    tx.commit();
                }
                equal(h, 500, g.getStoredEnergy(), "Rejected insertion altered FE");
                check(h, g.events.isEmpty(), "Rejected insertion invoked callbacks: " + g.events);
                h.succeed();
            }
        );
    }

    private static void g15(GameTestHelper h) {
        withReadyGenerator(
            h, g -> {
                equal(h, 500, g.charge(500), "Fixture charge");
                EnergyHandler front = requireCap(h, Direction.NORTH);
                EnergyHandler back = requireCap(h, Direction.SOUTH);
                EnergyHandler top = requireCap(h, Direction.UP);
                equal(h, CAPACITY, front.getCapacityAsLong(), "FRONT capacity");
                equal(h, CAPACITY, back.getCapacityAsLong(), "BACK capacity");
                equal(h, CAPACITY, top.getCapacityAsLong(), "TOP capacity");
                equal(h, 20, pull(front, 20), "FRONT committed pull");
                equal(h, 480, back.getAmountAsLong(), "BACK sees FRONT extraction");
                equal(h, 20, pull(back, 20), "BACK committed pull");
                equal(h, 460, front.getAmountAsLong(), "FRONT sees BACK extraction");
                equal(h, 460, top.getAmountAsLong(), "TOP sees shared energy");
                equal(h, 460, g.getStoredEnergy(), "Shared owner buffer");
                h.succeed();
            }
        );
    }

    private static void g16(GameTestHelper h) {
        withReadyGenerator(
            h, g -> {
                g.charge(500);
                requireCap(h, Direction.NORTH);
                check(
                    h,
                    g.changeEnergyPortMode(FRONT, EnergyPortMode.DISABLED),
                    "Cannot disable FRONT"
                );
                check(
                    h,
                    g.getEnergyPortMode(FRONT) == EnergyPortMode.DISABLED,
                    "FRONT mode not updated"
                );
                check(h, cap(h, Direction.NORTH) == null, "FRONT still exposed after disable");
                equal(
                    h,
                    40,
                    pull(requireCap(h, Direction.SOUTH), 40),
                    "Unchanged BACK cannot extract"
                );
                equal(h, 460, g.getStoredEnergy(), "Disable or extraction changed FE incorrectly");
                h.succeed();
            }
        );
    }

    private static void g17(GameTestHelper h) {
        withReadyGenerator(
            h, g -> {
                g.charge(500);
                check(h, cap(h, Direction.EAST) == null, "LEFT must start disabled");
                check(h, g.changeEnergyPortMode(LEFT, EnergyPortMode.OUTPUT), "Cannot enable LEFT");
                check(
                    h,
                    g.getEnergyPortMode(LEFT) == EnergyPortMode.OUTPUT,
                    "LEFT mode not updated"
                );
                equal(
                    h,
                    40,
                    pull(requireCap(h, Direction.EAST), 40),
                    "Enabled LEFT missing in distribution"
                );
                equal(h, 460, g.getStoredEnergy(), "LEFT extraction did not affect owner buffer");
                h.succeed();
            }
        );
    }

    private static void g18(GameTestHelper h) {
        withReadyGenerator(
            h, g -> {
                EnergyHandler original = requireCap(h, Direction.NORTH);
                g.resetObservations();
                check(
                    h,
                    !g.changeEnergyPortMode(FRONT, EnergyPortMode.INPUT),
                    "OUTPUT changed to forbidden INPUT"
                );
                check(
                    h,
                    !g.changeEnergyPortMode(LEFT, EnergyPortMode.INPUT),
                    "DISABLED changed to forbidden INPUT"
                );
                check(h, g.getEnergyPortMode(FRONT) == EnergyPortMode.OUTPUT, "FRONT mode changed");
                check(h, g.getEnergyPortMode(LEFT) == EnergyPortMode.DISABLED, "LEFT mode changed");
                check(h, original == cap(h, Direction.NORTH), "Rejected change replaced endpoint");
                check(h, cap(h, Direction.EAST) == null, "Rejected change opened LEFT");
                check(h, g.events.isEmpty(), "Rejected INPUT invoked callbacks: " + g.events);
                equal(h, 0, g.changedCalls, "Rejected INPUT dirtied generator");
                h.succeed();
            }
        );
    }

    private static void g19(GameTestHelper h) {
        withReadyGenerator(
            h, true, g -> {
                EnergyHandler front = requireCap(h, Direction.NORTH);
                g.resetObservations();
                check(
                    h,
                    !g.changeEnergyPortMode(FRONT, EnergyPortMode.DISABLED),
                    "FIXED output changed"
                );
                check(
                    h,
                    !g.changeEnergyPortMode(LEFT, EnergyPortMode.OUTPUT),
                    "FIXED disabled port changed"
                );
                check(
                    h,
                    !g.changeEnergyPortMode(FRONT, EnergyPortMode.INPUT),
                    "FIXED accepted INPUT"
                );
                check(h, g.getEnergyPortMode(FRONT) == EnergyPortMode.OUTPUT, "FIXED FRONT mode");
                check(h, g.getEnergyPortMode(LEFT) == EnergyPortMode.DISABLED, "FIXED LEFT mode");
                check(h, front == cap(h, Direction.NORTH), "FIXED endpoint changed");
                check(h, cap(h, Direction.EAST) == null, "FIXED disabled port exposed");
                equal(h, 0, g.changedCalls, "FIXED rejected change dirtied generator");
                check(h, g.events.isEmpty(), "FIXED rejected change invoked hooks: " + g.events);
                g.charge(500);
                equal(h, 40, pull(front, 40), "FIXED output no longer transfers");
                h.succeed();
            }
        );
    }

    private static void g20(GameTestHelper h) {
        withReadyGenerator(
            h, g -> {
                AtomicInteger invalidations = new AtomicInteger();
                var cache = BlockCapabilityCache.create(
                    Capabilities.Energy.BLOCK,
                    h.getLevel(),
                    h.absolutePos(POS),
                    Direction.NORTH,
                    () -> !g.isRemoved(),
                    invalidations::incrementAndGet
                );
                EnergyHandler before = requireCap(h, Direction.NORTH);
                check(h, cache.getCapability() == before, "Cache did not resolve FRONT");
                g.resetObservations();
                check(
                    h,
                    !g.changeEnergyPortMode(FRONT, EnergyPortMode.OUTPUT),
                    "Repeated OUTPUT reported change"
                );
                check(
                    h,
                    !g.changeEnergyPortMode(LEFT, EnergyPortMode.DISABLED),
                    "Repeated DISABLED reported change"
                );
                equal(h, 0, g.changedCalls, "Repeated mode dirtied generator");
                equal(h, 0, g.structureChecks, "Repeated mode refreshed structure");
                equal(h, 0, g.physicalChecks, "Repeated mode refreshed physical ports");
                equal(h, 0, invalidations.get(), "Repeated mode invalidated cache");
                check(h, g.events.isEmpty(), "Repeated mode invoked hooks: " + g.events);
                check(h, cache.getCapability() == before, "Repeated mode replaced endpoint");
                h.succeed();
            }
        );
    }

    private static void g21(GameTestHelper h) {
        withReadyGenerator(
            h, g -> {
                g.resetObservations();
                check(h, g.changeEnergyPortMode(FRONT, EnergyPortMode.DISABLED), "Disable FRONT");
                equal(h, 1, g.changedCalls, "Disable dirty count");
                check(
                    h,
                    g.events.equals(List.of("changed", "port:front:DISABLED")),
                    "Disable callback ID, mode or order incorrect: " + g.events
                );
                g.resetObservations();
                check(h, g.changeEnergyPortMode(LEFT, EnergyPortMode.OUTPUT), "Enable LEFT");
                equal(h, 1, g.changedCalls, "Enable dirty count");
                check(
                    h,
                    g.events.equals(List.of("changed", "port:left:OUTPUT")),
                    "Enable callback ID, mode or order incorrect: " + g.events
                );
                h.succeed();
            }
        );
    }

    private static void g22(GameTestHelper h) {
        withReadyGenerator(
            h, g -> {
                g.charge(500);
                EnergyHandler old = requireCap(h, Direction.NORTH);
                check(h, g.changeEnergyPortMode(FRONT, EnergyPortMode.DISABLED), "Disable FRONT");
                g.resetObservations();
                try (Transaction tx = Transaction.openRoot()) {
                    equal(
                        h,
                        0,
                        old.extract(40, tx),
                        "Previously exposed handler bypasses disabled mode"
                    );
                    equal(h, 0, old.insert(40, tx), "Previously exposed handler accepts energy");
                    tx.commit();
                }
                // Disabled is not physical revocation. Do not require zero amount/capacity getters.
                equal(h, 500, g.getStoredEnergy(), "Disabled handler changed FE");
                check(h, g.events.isEmpty(), "Disabled handler invoked hooks: " + g.events);
                check(h, cap(h, Direction.NORTH) == null, "Disabled FRONT exposed again");
                h.succeed();
            }
        );
    }

    private static void g23(GameTestHelper h) {
        withReadyGenerator(
            h, g -> {
                g.charge(500);
                EnergyHandler old = requireCap(h, Direction.NORTH);
                check(h, g.changeEnergyPortMode(FRONT, EnergyPortMode.DISABLED), "Disable FRONT");
                equal(h, 0, pull(old, 40), "Disabled endpoint still transfers");
                check(h, g.changeEnergyPortMode(FRONT, EnergyPortMode.OUTPUT), "Re-enable FRONT");
                EnergyHandler current = requireCap(h, Direction.NORTH);
                check(h, current == old, "Unchanged physical binding was recreated");
                equal(h, 40, pull(old, 40), "Valid retained endpoint not usable after enable");
                equal(h, 460, g.getStoredEnergy(), "Re-enabled endpoint incorrect FE");
                h.succeed();
            }
        );
    }

    private static void g24(GameTestHelper h) {
        withReadyGenerator(
            h, g -> {
                g.charge(500);
                AtomicInteger invalidations = new AtomicInteger();
                var cache = BlockCapabilityCache.create(
                    Capabilities.Energy.BLOCK,
                    h.getLevel(),
                    h.absolutePos(POS),
                    Direction.NORTH,
                    () -> !g.isRemoved(),
                    invalidations::incrementAndGet
                );
                EnergyHandler old = requireCap(h, Direction.NORTH);
                check(h, cache.getCapability() == old, "Cache failed to resolve FRONT");
                equal(h, 0, invalidations.get(), "Unexpected initial notification");
                check(h, g.changeEnergyPortMode(FRONT, EnergyPortMode.DISABLED), "Disable FRONT");
                equal(
                    h,
                    1,
                    invalidations.get(),
                    "Disabling FRONT did not invalidate live cache exactly once"
                );
                // Never access the cache or level from its invalidation callback.
                h.runAfterDelay(
                    1, () -> {
                        check(
                            h,
                            cache.getCapability() == null,
                            "Cache retained capability after disable"
                        );
                        equal(h, 0, pull(old, 40), "Stale cached handler bypassed disabled port");
                        equal(
                            h,
                            500,
                            g.getStoredEnergy(),
                            "Invalidated cached handler altered buffer"
                        );
                        h.succeed();
                    }
                );
            }
        );
    }

    private static void g25(GameTestHelper h) {
        withReadyGenerator(
            h, g -> {
                g.charge(500);
                AtomicInteger invalidations = new AtomicInteger();
                var cache = BlockCapabilityCache.create(
                    Capabilities.Energy.BLOCK,
                    h.getLevel(),
                    h.absolutePos(POS),
                    Direction.EAST,
                    () -> !g.isRemoved(),
                    invalidations::incrementAndGet
                );
                check(h, cache.getCapability() == null, "LEFT must initially cache null");
                equal(h, 0, invalidations.get(), "Unexpected initial notification");
                check(h, g.changeEnergyPortMode(LEFT, EnergyPortMode.OUTPUT), "Enable LEFT");
                equal(
                    h,
                    1,
                    invalidations.get(),
                    "Enabling LEFT did not invalidate cached null exactly once"
                );
                h.runAfterDelay(
                    1, () -> {
                        EnergyHandler current = requireCap(h, Direction.EAST);
                        check(
                            h,
                            cache.getCapability() == current,
                            "Cache did not discover newly enabled LEFT"
                        );
                        equal(h, 40, pull(current, 40), "Newly discovered endpoint cannot extract");
                        equal(
                            h,
                            460,
                            g.getStoredEnergy(),
                            "Cached endpoint did not use shared buffer"
                        );
                        h.succeed();
                    }
                );
            }
        );
    }
}
