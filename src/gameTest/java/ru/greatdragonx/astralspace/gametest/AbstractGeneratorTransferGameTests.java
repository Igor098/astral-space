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
import ru.greatdragonx.astralspace.common.energy.port.EnergyPortMode;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static ru.greatdragonx.astralspace.gametest.GameTestGeneratorBlockEntity.BACK;
import static ru.greatdragonx.astralspace.gametest.GameTestGeneratorBlockEntity.TOP;

/**
 * G-42...G-59: Push/Pull, transactions, discovery and shared accounting.
 */
public final class AbstractGeneratorTransferGameTests {
    private static final BlockPos POS = new BlockPos(1, 1, 1);
    private static final BlockPos NORTH_SINK = new BlockPos(1, 1, 0);
    private static final BlockPos SOUTH_SINK = new BlockPos(1, 1, 2);
    private static final BlockPos TOP_SINK = new BlockPos(1, 2, 1);
    private static final Identifier ENVIRONMENT = id("abstract_generator/transfer_environment");
    private static final Identifier STRUCTURE = id("gametest/empty_3x3x3");
    static final DeferredRegister<Consumer<GameTestHelper>> TEST_FUNCTIONS =
        DeferredRegister.create(BuiltInRegistries.TEST_FUNCTION, AstralSpace.MOD_ID);

    private record Entry(
        Identifier id, DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> function
    ) {}

    private static final List<Entry> TESTS = new ArrayList<>();

    static {
        add("g42_push_to_neighbor", AbstractGeneratorTransferGameTests::g42);
        add("g43_full_receiver_skipped", AbstractGeneratorTransferGameTests::g43);
        add("g44_partial_acceptance_conserves_fe", AbstractGeneratorTransferGameTests::g44);
        add("g45_probe_rolls_back", AbstractGeneratorTransferGameTests::g45);
        add("g46_discovery_empty_buffer", AbstractGeneratorTransferGameTests::g46);
        add("g47_no_receivers", AbstractGeneratorTransferGameTests::g47);
        add("g48_rotating_discovery_order", AbstractGeneratorTransferGameTests::g48);
        add("g49_two_push_shared_budget", AbstractGeneratorTransferGameTests::g49);
        add("g50_push_pull_shared_budget", AbstractGeneratorTransferGameTests::g50);
        add("g51_multiple_ports_shared_budget", AbstractGeneratorTransferGameTests::g51);
        add("g52_pull_rollback_restores_budget", AbstractGeneratorTransferGameTests::g52);
        add("g53_rollback_not_counted", AbstractGeneratorTransferGameTests::g53);
        add("g54_combined_transfer_statistics", AbstractGeneratorTransferGameTests::g54);
        add("g55_previous_tick_statistics", AbstractGeneratorTransferGameTests::g55);
        add("g56_opposite_neighbor_side", AbstractGeneratorTransferGameTests::g56);
        add("g57_unloaded_remote_neighbor", AbstractGeneratorTransferGameTests::g57);
        add("g58_requery_between_passes", AbstractGeneratorTransferGameTests::g58);
        add("g59_shared_receiver_storage", AbstractGeneratorTransferGameTests::g59);
    }

    private AbstractGeneratorTransferGameTests() {}

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
        int energy,
        boolean produce
    )
    {
        equal(h, energy, g.charge(energy), "Fixture charge");
        g.process.allowProduction = produce;
        g.resetObservations();
    }

    private static void disable(
        GameTestHelper h,
        GameTestGeneratorBlockEntity g,
        ru.greatdragonx.astralspace.common.energy.port.EnergyPortId port
    )
    {
        check(h, g.changeEnergyPortMode(port, EnergyPortMode.DISABLED), "Cannot disable " + port);
    }

    private static void frontOnly(GameTestHelper h, GameTestGeneratorBlockEntity g) {
        disable(h, g, BACK);
        disable(h, g, TOP);
    }

    private static void frontAndBack(GameTestHelper h, GameTestGeneratorBlockEntity g) {
        disable(h, g, TOP);
    }

    private static void energy(
        GameTestHelper h,
        long initial,
        GameTestGeneratorBlockEntity g,
        GameTestTransferReceiver... receivers
    )
    {
        long total = g.getStoredEnergy();
        for (var receiver : receivers) total += receiver.getAmountAsLong();
        equal(h, initial, total, "FE conservation");
    }

    private static void g42(GameTestHelper h) {
        withReadyGenerator(
            h, g -> {
                frontOnly(h, g);
                prepare(h, g, 500, false);
                try (var scope = new GameTestTransferReceiver.Scope(h)) {
                    var receiver = new GameTestTransferReceiver(1000);
                    scope.place(NORTH_SINK, receiver);
                    g.tickOnce();
                    equal(h, 80, receiver.getAmountAsLong(), "Push received FE");
                    equal(h, 420, g.getStoredEnergy(), "Source FE");
                    energy(h, 500, g, receiver);
                }
                h.succeed();
            }
        );
    }

    private static void g43(GameTestHelper h) {
        withReadyGenerator(
            h, g -> {
                frontAndBack(h, g);
                prepare(h, g, 500, false);
                try (var scope = new GameTestTransferReceiver.Scope(h)) {
                    var full = new GameTestTransferReceiver(100, 100);
                    var empty = new GameTestTransferReceiver(1000);
                    scope.place(NORTH_SINK, full);
                    scope.place(SOUTH_SINK, empty);
                    g.tickOnce();
                    equal(h, 100, full.getAmountAsLong(), "Full receiver unchanged");
                    equal(h, 0, full.attemptedAccepted, "Full receiver accepted nothing");
                    equal(h, 80, empty.getAmountAsLong(), "Other receiver uses budget");
                    energy(h, 600, g, full, empty);
                }
                h.succeed();
            }
        );
    }

    private static void g44(GameTestHelper h) {
        withReadyGenerator(
            h, g -> {
                frontOnly(h, g);
                prepare(h, g, 500, false);
                try (var scope = new GameTestTransferReceiver.Scope(h)) {
                    var receiver = new GameTestTransferReceiver(100, 73);
                    scope.place(NORTH_SINK, receiver);
                    g.tickOnce();
                    equal(h, 100, receiver.getAmountAsLong(), "Only 27 FE accepted");
                    equal(h, 473, g.getStoredEnergy(), "Only accepted FE extracted");
                    energy(h, 573, g, receiver);
                }
                h.succeed();
            }
        );
    }

    private static void g45(GameTestHelper h) {
        withReadyGenerator(
            h, g -> {
                frontOnly(h, g);
                prepare(h, g, 50, false);
                try (var scope = new GameTestTransferReceiver.Scope(h)) {
                    var receiver = new GameTestTransferReceiver(1000);
                    scope.place(NORTH_SINK, receiver);
                    g.tickOnce();
                    check(h, receiver.insertCalls >= 2, "Probe and real insertion must both run");
                    check(
                        h,
                        receiver.attemptedAccepted >= 130,
                        "Probe accepts 80 FE before rollback"
                    );
                    equal(h, 50, receiver.getAmountAsLong(), "Only committed transfer is retained");
                    energy(h, 50, g, receiver);
                }
                h.succeed();
            }
        );
    }

    private static void g46(GameTestHelper h) {
        withReadyGenerator(
            h, g -> {
                frontOnly(h, g);
                prepare(h, g, 0, false);
                try (var scope = new GameTestTransferReceiver.Scope(h)) {
                    var receiver = new GameTestTransferReceiver(1000);
                    scope.place(NORTH_SINK, receiver);
                    g.process.beforePrepareReturns = () -> {
                        equal(
                            h,
                            1,
                            receiver.insertCalls,
                            "Discovery before Prepare despite empty FE"
                        );
                        equal(h, 80, receiver.attemptedAccepted, "Probe uses configured maximum");
                        equal(h, 0, receiver.getAmountAsLong(), "Probe rolled back before Prepare");
                    };
                    g.tickOnce();
                    equal(h, 1, g.process.prepareCalls, "Prepare was reached");
                    equal(h, 1, receiver.insertCalls, "No transfer or second discovery");
                    energy(h, 0, g, receiver);
                }
                h.succeed();
            }
        );
    }

    private static void g47(GameTestHelper h) {
        withReadyGenerator(
            h, g -> {
                frontAndBack(h, g);
                prepare(h, g, 500, false);
                try (var scope = new GameTestTransferReceiver.Scope(h)) {
                    AtomicInteger queries = new AtomicInteger();
                    // No capability at either neighbor, but observe actual queries.
                    scope.placeProvider(
                        NORTH_SINK, side -> {
                            queries.incrementAndGet();
                            return null;
                        }
                    );
                    scope.placeProvider(
                        SOUTH_SINK, side -> {
                            queries.incrementAndGet();
                            return null;
                        }
                    );
                    g.tickOnce();
                    check(h, queries.get() >= 2, "Discovery must visit both neighbors");
                    equal(h, 500, g.getStoredEnergy(), "No phantom transfer");
                    equal(
                        h,
                        80,
                        pull(requireCap(h, Direction.NORTH), 80),
                        "No phantom Push demand blocks Pull"
                    );
                    equal(h, 420, g.getStoredEnergy(), "Committed Pull only");
                }
                h.succeed();
            }
        );
    }

    private static List<String> observeOrder(GameTestHelper h, GameTestGeneratorBlockEntity g) {
        List<String> order = new ArrayList<>();
        try (var scope = new GameTestTransferReceiver.Scope(h)) {
            var north = new GameTestTransferReceiver(1000);
            var south = new GameTestTransferReceiver(1000);
            scope.placeProvider(
                NORTH_SINK, side -> {
                    order.add("front");
                    return north;
                }
            );
            scope.placeProvider(
                SOUTH_SINK, side -> {
                    order.add("back");
                    return south;
                }
            );
            long tick = h.getLevel().getGameTime();
            g.tickOnce();
            List<String> expected = Math.floorMod(tick, 2) == 0 ? List.of("front", "back") :
                List.of("back", "front");
            check(
                h,
                order.equals(expected),
                "Wrong discovery order for tick " + tick + ": " + order
            );
            equal(h, 0, north.getAmountAsLong() + south.getAmountAsLong(), "Only probes");
        }
        return order;
    }

    private static void g48(GameTestHelper h) {
        withReadyGenerator(
            h, g -> {
                frontAndBack(h, g);
                prepare(h, g, 0, false);
                List<String> first = observeOrder(h, g);
                long tick = h.getLevel().getGameTime();
                h.runAfterDelay(
                    1, () -> {
                        equal(h, tick + 1, h.getLevel().getGameTime(), "Next world tick");
                        List<String> second = observeOrder(h, g);
                        check(h, !first.equals(second), "First port must rotate on the next tick");
                        h.succeed();
                    }
                );
            }
        );
    }

    private static void g49(GameTestHelper h) {
        withReadyGenerator(
            h, g -> {
                frontOnly(h, g);
                prepare(h, g, 60, true);
                try (var scope = new GameTestTransferReceiver.Scope(h)) {
                    var receiver = new GameTestTransferReceiver(1000);
                    scope.place(NORTH_SINK, receiver);
                    g.process.beforePrepareReturns = () -> equal(
                        h,
                        60,
                        receiver.getAmountAsLong(),
                        "First Push"
                    );
                    g.tickOnce();
                    equal(h, 80, receiver.getAmountAsLong(), "Both Push passes share 80 FE");
                    equal(h, 20, g.getStoredEnergy(), "20 newly produced FE remain");
                    equal(h, 40, g.getCurrentProductionRate(), "Production rate");
                    energy(h, 100, g, receiver);
                }
                h.succeed();
            }
        );
    }

    private static void g50(GameTestHelper h) {
        withReadyGenerator(
            h, g -> {
                frontOnly(h, g);
                prepare(h, g, 500, false);
                try (var scope = new GameTestTransferReceiver.Scope(h)) {
                    var receiver = new GameTestTransferReceiver(1000);
                    scope.place(NORTH_SINK, receiver);
                    equal(h, 30, pull(requireCap(h, Direction.NORTH), 30), "Pull first");
                    g.tickOnce();
                    equal(h, 50, receiver.getAmountAsLong(), "Push gets remaining budget");
                    equal(
                        h,
                        0,
                        pull(requireCap(h, Direction.NORTH), 80),
                        "Shared budget exhausted"
                    );
                    energy(h, 470, g, receiver);
                }
                h.succeed();
            }
        );
    }

    private static void g51(GameTestHelper h) {
        withReadyGenerator(
            h, g -> {
                prepare(h, g, 500, false);
                try (var scope = new GameTestTransferReceiver.Scope(h)) {
                    var north = new GameTestTransferReceiver(1000);
                    var south = new GameTestTransferReceiver(1000);
                    var top = new GameTestTransferReceiver(1000);
                    scope.place(NORTH_SINK, north);
                    scope.place(SOUTH_SINK, south);
                    scope.place(TOP_SINK, top);
                    g.tickOnce();
                    equal(
                        h,
                        80,
                        north.getAmountAsLong() + south.getAmountAsLong() + top.getAmountAsLong(),
                        "Three ports, one limit"
                    );
                    equal(h, 420, g.getStoredEnergy(), "Shared stored energy");
                    energy(h, 500, g, north, south, top);
                }
                h.succeed();
            }
        );
    }

    private static void g52(GameTestHelper h) {
        withReadyGenerator(
            h, g -> {
                frontOnly(h, g);
                prepare(h, g, 500, false);
                EnergyHandler handler = requireCap(h, Direction.NORTH);
                try (Transaction tx = Transaction.openRoot()) {
                    equal(h, 80, handler.extract(80, tx), "Provisional Pull");
                    equal(h, 420, g.getStoredEnergy(), "Provisional FE");
                    equal(h, 0, handler.extract(1, tx), "Provisional budget exhausted");
                    equal(h, 0, g.changedCalls, "No notification before commit");
                }
                equal(h, 500, g.getStoredEnergy(), "FE restored");
                equal(h, 0, g.changedCalls, "No notification after rollback");
                equal(h, 80, pull(handler, 80), "Entire budget restored");
                equal(h, 420, g.getStoredEnergy(), "Committed FE");
                h.succeed();
            }
        );
    }

    private static void g53(GameTestHelper h) {
        withReadyGenerator(
            h, g -> {
                frontOnly(h, g);
                prepare(h, g, 500, false);
                try (Transaction tx = Transaction.openRoot()) {
                    equal(
                        h,
                        80,
                        requireCap(h, Direction.NORTH).extract(80, tx),
                        "Rolled-back extraction"
                    );
                }
                equal(h, 500, g.getStoredEnergy(), "Rollback restores FE");
                h.runAfterDelay(
                    1, () -> {
                        equal(
                            h,
                            0,
                            g.getCurrentTransferRate(),
                            "Rollback is not recorded on next tick"
                        );
                        h.succeed();
                    }
                );
            }
        );
    }

    private static void g54(GameTestHelper h) {
        withReadyGenerator(
            h, g -> {
                frontOnly(h, g);
                prepare(h, g, 500, false);
                try (var scope = new GameTestTransferReceiver.Scope(h)) {
                    var receiver = new GameTestTransferReceiver(30);
                    scope.place(NORTH_SINK, receiver);
                    g.tickOnce();
                    equal(h, 30, receiver.getAmountAsLong(), "Committed Push");
                    equal(h, 50, pull(requireCap(h, Direction.NORTH), 50), "Committed Pull");
                    equal(h, 420, g.getStoredEnergy(), "Both committed transfers");
                }
                h.runAfterDelay(
                    1, () -> {
                        equal(
                            h,
                            80,
                            g.getCurrentTransferRate(),
                            "Push and Pull combined after commit"
                        );
                        h.succeed();
                    }
                );
            }
        );
    }

    private static void g55(GameTestHelper h) {
        withReadyGenerator(
            h, g -> {
                frontOnly(h, g);
                prepare(h, g, 500, false);
                equal(h, 40, pull(requireCap(h, Direction.NORTH), 40), "Current-tick transfer");
                equal(h, 0, g.getCurrentTransferRate(), "Current tick is not the previous tick");
                h.runAfterDelay(
                    1, () -> {
                        equal(h, 40, g.getCurrentTransferRate(), "Previous-tick transfer visible");
                        equal(
                            h,
                            20,
                            pull(requireCap(h, Direction.NORTH), 20),
                            "New current-tick transfer"
                        );
                        equal(
                            h,
                            40,
                            g.getCurrentTransferRate(),
                            "New transfer does not overwrite previous rate"
                        );
                        h.runAfterDelay(
                            1, () -> {
                                equal(
                                    h,
                                    20,
                                    g.getCurrentTransferRate(),
                                    "Only the immediately previous tick"
                                );
                                h.runAfterDelay(
                                    1, () -> {
                                        equal(
                                            h,
                                            0,
                                            g.getCurrentTransferRate(),
                                            "Idle previous tick"
                                        );
                                        h.succeed();
                                    }
                                );
                            }
                        );
                    }
                );
            }
        );
    }

    private static void g56(GameTestHelper h) {
        withReadyGenerator(
            h, g -> {
                frontAndBack(h, g);
                prepare(h, g, 500, false);
                try (var scope = new GameTestTransferReceiver.Scope(h)) {
                    List<Direction> northQueries = new ArrayList<>();
                    List<Direction> southQueries = new ArrayList<>();
                    var north = new GameTestTransferReceiver(1000);
                    var south = new GameTestTransferReceiver(1000);
                    scope.placeProvider(
                        NORTH_SINK, side -> {
                            northQueries.add(side);
                            return side == Direction.SOUTH ? north : null;
                        }
                    );
                    scope.placeProvider(
                        SOUTH_SINK, side -> {
                            southQueries.add(side);
                            return side == Direction.NORTH ? south : null;
                        }
                    );
                    g.tickOnce();
                    check(
                        h,
                        !northQueries.isEmpty() &&
                            northQueries.stream().allMatch(s -> s == Direction.SOUTH),
                        "North receiver queried from wrong side: " + northQueries
                    );
                    check(
                        h,
                        !southQueries.isEmpty() &&
                            southQueries.stream().allMatch(s -> s == Direction.NORTH),
                        "South receiver queried from wrong side: " + southQueries
                    );
                    equal(
                        h,
                        80,
                        north.getAmountAsLong() + south.getAmountAsLong(),
                        "Valid sided receivers accept FE"
                    );
                    energy(h, 500, g, north, south);
                }
                h.succeed();
            }
        );
    }

    private static void g57(GameTestHelper h) {
        // Do not unload GameTest's own chunk or place blocks at the remote location.
        BlockPos remote = h.absolutePos(POS).offset(GameTestRemotePortGenerator.REMOTE_OFFSET);
        BlockPos neighbor = remote.relative(Direction.EAST);
        var chunkSource = h.getLevel().getChunkSource();
        check(
            h,
            !chunkSource.hasChunk(neighbor.getX() >> 4, neighbor.getZ() >> 4),
            "G-57 requires an unloaded remote neighbor chunk"
        );
        h.setBlock(POS, GameTestRemotePortGenerator.BLOCK.get().defaultBlockState());
        var g = h.getBlockEntity(POS, GameTestRemotePortGenerator.class);
        h.runAfterDelay(
            2, () -> {
                check(
                    h,
                    g.getEnergyHandlerAt(g.remotePoint(), Direction.EAST) != null,
                    "Synthetic remote output must be available; otherwise the test would not exercise the guard"
                );
                check(
                    h,
                    !chunkSource.hasChunk(neighbor.getX() >> 4, neighbor.getZ() >> 4),
                    "Remote chunk loaded during onLoad"
                );
                equal(h, 500, g.charge(500), "Fixture charge");
                g.tickOnce();
                equal(h, 500, g.getStoredEnergy(), "No remote energy transfer");
                check(
                    h,
                    !chunkSource.hasChunk(neighbor.getX() >> 4, neighbor.getZ() >> 4),
                    "Push must not load remote chunk"
                );
                h.runAfterDelay(
                    1, () -> {
                        check(
                            h,
                            !chunkSource.hasChunk(neighbor.getX() >> 4, neighbor.getZ() >> 4),
                            "Remote chunk must remain unloaded"
                        );
                        h.succeed();
                    }
                );
            }
        );
    }

    private static void g58(GameTestHelper h) {
        withReadyGenerator(
            h, g -> {
                frontOnly(h, g);
                prepare(h, g, 500, false);
                try (var scope = new GameTestTransferReceiver.Scope(h)) {
                    var discovery = new GameTestTransferReceiver(1000);
                    var transfer = new GameTestTransferReceiver(1000);
                    AtomicInteger queries = new AtomicInteger();
                    // Deliberately change the provider's result between two lookups, not world state.
                    scope.placeProvider(
                        NORTH_SINK,
                        side -> queries.incrementAndGet() == 1 ? discovery : transfer
                    );
                    g.tickOnce();
                    check(h, queries.get() >= 2, "Transfer must re-query capability");
                    equal(h, 1, discovery.insertCalls, "Old receiver probed only");
                    equal(h, 0, discovery.getAmountAsLong(), "Discovery probe rolled back");
                    equal(
                        h,
                        80,
                        transfer.getAmountAsLong(),
                        "Fresh receiver gets committed transfer"
                    );
                    energy(h, 500, g, discovery, transfer);
                }
                h.succeed();
            }
        );
    }

    private static void g59(GameTestHelper h) {
        withReadyGenerator(
            h, g -> {
                frontAndBack(h, g);
                prepare(h, g, 500, false);
                try (var scope = new GameTestTransferReceiver.Scope(h)) {
                    var shared = new GameTestTransferReceiver(50);
                    scope.place(NORTH_SINK, shared);
                    scope.place(SOUTH_SINK, shared);
                    g.tickOnce();
                    equal(
                        h,
                        50,
                        shared.getAmountAsLong(),
                        "Both ports share one receiver's 50 FE capacity"
                    );
                    equal(h, 450, g.getStoredEnergy(), "No duplicated or lost FE");
                    energy(h, 500, g, shared); // Count the physical storage exactly once.
                }
                h.succeed();
            }
        );
    }
}
