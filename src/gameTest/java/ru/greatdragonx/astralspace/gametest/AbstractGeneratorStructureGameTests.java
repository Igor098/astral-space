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
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import ru.greatdragonx.astralspace.AstralSpace;
import ru.greatdragonx.astralspace.common.energy.port.EnergyPortId;
import ru.greatdragonx.astralspace.common.energy.port.EnergyPortMode;
import ru.greatdragonx.astralspace.common.machine.redstone.RedstoneControlMode;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static ru.greatdragonx.astralspace.gametest.GameTestGeneratorBlockEntity.*;

/**
 * G-76...G-88: structure readiness, port availability and endpoint lifetime.
 */
public final class AbstractGeneratorStructureGameTests {
    private static final BlockPos POS = new BlockPos(1, 1, 1);
    private static final Identifier ENVIRONMENT = id("abstract_generator/structure_environment");
    private static final Identifier STRUCTURE = id("gametest/empty_3x3x3");
    static final DeferredRegister<Consumer<GameTestHelper>> TEST_FUNCTIONS =
        DeferredRegister.create(BuiltInRegistries.TEST_FUNCTION, AstralSpace.MOD_ID);

    private record Entry(
        Identifier id, DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> function
    ) {}

    private static final List<Entry> TESTS = new ArrayList<>();

    static {
        add("g76_unready_blocks_production", AbstractGeneratorStructureGameTests::g76);
        add("g77_unready_blocks_push", AbstractGeneratorStructureGameTests::g77);
        add("g78_unready_blocks_old_pull", AbstractGeneratorStructureGameTests::g78);
        add("g79_unready_hides_capabilities", AbstractGeneratorStructureGameTests::g79);
        add("g80_unready_allows_settings", AbstractGeneratorStructureGameTests::g80);
        add("g81_unready_preserves_energy_and_process", AbstractGeneratorStructureGameTests::g81);
        add("g82_readiness_reopens_ports", AbstractGeneratorStructureGameTests::g82);
        add(
            "g83_temporary_unavailability_preserves_binding",
            AbstractGeneratorStructureGameTests::g83
        );
        add("g84_independent_physical_ports", AbstractGeneratorStructureGameTests::g84);
        add("g85_replaced_endpoint_revoked", AbstractGeneratorStructureGameTests::g85);
        add("g86_structure_independent_of_redstone", AbstractGeneratorStructureGameTests::g86);
        add("g87_production_statistics_until_next_tick", AbstractGeneratorStructureGameTests::g87);
        add("g88_readiness_after_load", AbstractGeneratorStructureGameTests::g88);
    }

    private AbstractGeneratorStructureGameTests() {}

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

    private static void mode(
        GameTestHelper h,
        GameTestGeneratorBlockEntity g,
        EnergyPortId id,
        EnergyPortMode mode
    )
    {
        boolean changed = g.getEnergyPortMode(id) != mode;
        check(h, g.changeEnergyPortMode(id, mode) == changed, "Mode change failed: " + id);
        check(h, g.getEnergyPortMode(id) == mode, "Unexpected port mode: " + id);
    }

    private static void assertNoProduction(GameTestHelper h, GameTestGeneratorBlockEntity g) {
        equal(h, 0, g.process.prepareCalls, "Prepare must not run");
        equal(h, 0, g.process.completionCalls, "Completion must not run");
        equal(h, 0, g.getCurrentProductionRate(), "Production rate");
        check(h, !g.isProducingEnergy(), "Unready generator reports production");
        check(h, g.events.contains("producing:false"), "Pause hook missing: " + g.events);
    }

    private static void assertHidden(GameTestHelper h, GameTestGeneratorBlockEntity g) {
        for (Direction side : Direction.values()) {
            check(h, cap(h, side) == null, "Unexpected world capability: " + side);
            check(
                h,
                g.getEnergyHandlerForSide(side) == null,
                "Unexpected direct capability: " + side
            );
        }
    }

    private static void frontOnly(GameTestHelper h, GameTestGeneratorBlockEntity g) {
        mode(h, g, BACK, EnergyPortMode.DISABLED);
        mode(h, g, TOP, EnergyPortMode.DISABLED);
        mode(h, g, LEFT, EnergyPortMode.DISABLED);
    }

    private static void charge(GameTestHelper h, GameTestGeneratorBlockEntity g, int amount) {
        equal(h, amount, g.charge(amount), "Fixture charge");
    }

    private static void g76(GameTestHelper h) {
        withReadyGenerator(
            h, g -> {
                g.process.allowProduction = true;
                g.setStructureReady(false);
                g.resetObservations();
                g.tickOnce();
                assertNoProduction(h, g);
                equal(h, 0, g.getStoredEnergy(), "Unready generator produced FE");
                equal(h, 0, g.process.completedTicks, "Process progressed");
                h.succeed();
            }
        );
    }

    private static void g77(GameTestHelper h) {
        withReadyGenerator(
            h, g -> {
                frontOnly(h, g);
                charge(h, g, 500);
                var receiver = new GameTestTransferReceiver(1000);
                var queries = new AtomicInteger();
                try (var scope = new GameTestTransferReceiver.Scope(h)) {
                    scope.placeProvider(
                        POS.relative(Direction.NORTH), side -> {
                            queries.incrementAndGet();
                            return receiver;
                        }
                    );
                    g.setStructureReady(false);
                    g.resetObservations();
                    g.tickOnce();
                    equal(h, 500, g.getStoredEnergy(), "Unready Push changed FE");
                    equal(h, 0, receiver.getAmountAsLong(), "Receiver got FE");
                    equal(h, 0, receiver.insertCalls, "Unready port probed receiver");
                    equal(h, 0, queries.get(), "Unready port queried receiver");
                    assertNoProduction(h, g);
                    // Positive control: same receiver works when structure becomes ready.
                    g.setStructureReady(true);
                    g.tickOnce();
                    equal(h, MAX_OUTPUT, receiver.getAmountAsLong(), "Ready Push did not resume");
                    equal(h, 500 - MAX_OUTPUT, g.getStoredEnergy(), "Ready Push conservation");
                }
                h.succeed();
            }
        );
    }

    private static void g78(GameTestHelper h) {
        withReadyGenerator(
            h, g -> {
                frontOnly(h, g);
                charge(h, g, 500);
                EnergyHandler held = requireCap(h, Direction.NORTH);
                g.setStructureReady(false);
                equal(h, 0, pull(held, MAX_OUTPUT), "Old handler extracted while unready");
                equal(h, 500, g.getStoredEnergy(), "Blocked Pull changed FE");
                g.setStructureReady(true);
                equal(
                    h,
                    MAX_OUTPUT,
                    pull(held, MAX_OUTPUT),
                    "Blocked Pull spent budget or handler did not resume"
                );
                equal(h, 420, g.getStoredEnergy(), "Resumed Pull conservation");
                h.succeed();
            }
        );
    }

    private static void g79(GameTestHelper h) {
        withReadyGenerator(
            h, g -> {
                requireCap(h, Direction.NORTH);
                requireCap(h, Direction.SOUTH);
                requireCap(h, Direction.UP);
                var invalidations = new AtomicInteger();
                var cache = BlockCapabilityCache.create(
                    Capabilities.Energy.BLOCK,
                    h.getLevel(),
                    h.absolutePos(POS),
                    Direction.NORTH,
                    () -> !g.isRemoved(),
                    invalidations::incrementAndGet
                );
                check(h, cache.getCapability() != null, "Cache was empty initially");
                g.setStructureReady(false);
                check(h, invalidations.get() > 0, "Readiness loss did not invalidate cache");
                check(h, cache.getCapability() == null, "Cache exposes unready port");
                assertHidden(h, g);
                check(
                    h,
                    g.getEnergyPortMode(FRONT) == EnergyPortMode.OUTPUT,
                    "Readiness changed stored mode"
                );
                h.succeed();
            }
        );
    }

    private static void g80(GameTestHelper h) {
        withReadyGenerator(
            h, g -> {
                g.setStructureReady(false);
                mode(h, g, FRONT, EnergyPortMode.DISABLED);
                mode(h, g, LEFT, EnergyPortMode.OUTPUT);
                check(
                    h,
                    g.changeRedstoneControlMode(RedstoneControlMode.WHEN_POWERED),
                    "Unready redstone setting rejected"
                );
                check(
                    h,
                    g.getRedstoneControlMode() == RedstoneControlMode.WHEN_POWERED,
                    "Wrong redstone mode"
                );
                assertHidden(h, g);
                g.setStructureReady(true);
                check(h, cap(h, Direction.NORTH) == null, "Disabled FRONT exposed");
                requireCap(h, Direction.EAST);
                h.succeed();
            }
        );
    }

    private static void g81(GameTestHelper h) {
        withReadyGenerator(
            h, g -> {
                charge(h, g, 500);
                g.process.allowProduction = true;
                g.process.completedTicks = 7;
                g.setStructureReady(false);
                g.resetObservations();
                g.tickOnce();
                assertNoProduction(h, g);
                equal(h, 500, g.getStoredEnergy(), "Pause lost FE");
                equal(h, 7, g.process.completedTicks, "Pause lost process progress");
                h.runAfterDelay(
                    1, () -> {
                        g.setStructureReady(true);
                        g.tickOnce();
                        equal(h, 540, g.getStoredEnergy(), "Production did not resume");
                        equal(h, 8, g.process.completedTicks, "Process progress did not resume");
                        equal(h, 1, g.process.prepareCalls, "Resumed Prepare");
                        equal(h, 1, g.process.completionCalls, "Resumed Completion");
                        h.succeed();
                    }
                );
            }
        );
    }

    private static void g82(GameTestHelper h) {
        withReadyGenerator(
            h, g -> {
                g.setStructureReady(false);
                var invalidations = new AtomicInteger();
                var cache = BlockCapabilityCache.create(
                    Capabilities.Energy.BLOCK,
                    h.getLevel(),
                    h.absolutePos(POS),
                    Direction.NORTH,
                    () -> !g.isRemoved(),
                    invalidations::incrementAndGet
                );
                check(h, cache.getCapability() == null, "Cache must initially contain null");
                g.setStructureReady(true);
                check(
                    h,
                    invalidations.get() > 0,
                    "Ready transition did not invalidate cached null"
                );
                check(
                    h,
                    cache.getCapability() == requireCap(h, Direction.NORTH),
                    "Cache did not reopen FRONT"
                );
                requireCap(h, Direction.SOUTH);
                requireCap(h, Direction.UP);
                check(h, cap(h, Direction.EAST) == null, "Readiness enabled disabled LEFT");
                charge(h, g, 100);
                equal(h, 40, pull(cache.getCapability(), 40), "Reopened port not functional");
                h.succeed();
            }
        );
    }

    private static void g83(GameTestHelper h) {
        withReadyGenerator(
            h, g -> {
                frontOnly(h, g);
                charge(h, g, 500);
                EnergyHandler original = requireCap(h, Direction.NORTH);
                g.setPortAvailable(FRONT, false);
                check(h, cap(h, Direction.NORTH) == null, "Unavailable port exposed");
                equal(h, 0, pull(original, 40), "Unavailable endpoint extracted");
                g.setPortAvailable(FRONT, true);
                check(
                    h,
                    requireCap(h, Direction.NORTH) == original,
                    "Temporary physical unavailability replaced binding"
                );
                g.setStructureReady(false);
                check(h, cap(h, Direction.NORTH) == null, "Unready port exposed");
                equal(h, 0, pull(original, 40), "Suspended endpoint extracted");
                g.setStructureReady(true);
                check(
                    h,
                    requireCap(h, Direction.NORTH) == original,
                    "Temporary readiness loss replaced binding"
                );
                equal(h, MAX_OUTPUT, pull(original, MAX_OUTPUT), "Retained endpoint cannot resume");
                equal(h, 420, g.getStoredEnergy(), "Unexpected FE after temporary suspension");
                h.succeed();
            }
        );
    }

    private static void g84(GameTestHelper h) {
        withReadyGenerator(
            h, g -> {
                charge(h, g, 500);
                EnergyHandler front = requireCap(h, Direction.NORTH);
                EnergyHandler back = requireCap(h, Direction.SOUTH);
                EnergyHandler top = requireCap(h, Direction.UP);
                g.setPortAvailable(FRONT, false);
                check(h, cap(h, Direction.NORTH) == null, "Unavailable FRONT exposed");
                check(h, cap(h, Direction.SOUTH) == back, "FRONT availability affected BACK");
                check(h, cap(h, Direction.UP) == top, "FRONT availability affected TOP");
                equal(h, 0, pull(front, 40), "Unavailable FRONT extracted");
                equal(h, 40, pull(back, 40), "Available BACK failed");
                g.process.allowProduction = true;
                g.tickOnce();
                equal(
                    h,
                    GENERATION,
                    g.getCurrentProductionRate(),
                    "Unavailable port blocked production"
                );
                equal(h, 500, g.getStoredEnergy(), "Unexpected FE after Pull and production");
                h.succeed();
            }
        );
    }

    private static void g85(GameTestHelper h) {
        withReadyGenerator(
            h, g -> {
                frontOnly(h, g);
                charge(h, g, 500);
                EnergyHandler original = requireCap(h, Direction.NORTH);
                var invalidations = new AtomicInteger();
                var cache = BlockCapabilityCache.create(
                    Capabilities.Energy.BLOCK,
                    h.getLevel(),
                    h.absolutePos(POS),
                    Direction.NORTH,
                    () -> !g.isRemoved(),
                    invalidations::incrementAndGet
                );
                check(h, cache.getCapability() == original, "Wrong initial cached endpoint");
                // Same logical port and world point, but physical identity is explicitly replaced.
                g.replaceEndpoint(FRONT);
                check(h, invalidations.get() > 0, "Endpoint replacement did not invalidate cache");
                equal(h, 0, original.getAmountAsLong(), "Revoked endpoint amount");
                equal(h, 0, original.getCapacityAsLong(), "Revoked endpoint capacity");
                equal(h, 0, pull(original, 40), "Revoked endpoint extracted");
                EnergyHandler replacement = requireCap(h, Direction.NORTH);
                check(h, replacement != original, "Replacement reused revoked endpoint");
                check(
                    h,
                    cache.getCapability() == replacement,
                    "Cache did not discover replacement"
                );
                equal(h, 40, pull(replacement, 40), "Replacement cannot extract");
                equal(h, 460, g.getStoredEnergy(), "Replacement Pull conservation");
                equal(h, 0, pull(original, 40), "Revoked endpoint revived");
                h.succeed();
            }
        );
    }

    private static void g86(GameTestHelper h) {
        withReadyGenerator(
            h, g -> {
                BlockPos signal = POS.relative(Direction.WEST);
                h.setBlock(signal, Blocks.AIR);
                check(
                    h,
                    g.changeRedstoneControlMode(RedstoneControlMode.WHEN_POWERED),
                    "Mode change failed"
                );
                g.process.allowProduction = true;
                g.setStructureReady(false);
                h.setBlock(signal, Blocks.REDSTONE_BLOCK);
                check(h, g.hasRedstoneSignal(), "Signal missing");
                check(
                    h,
                    !g.isRedstoneBlockingOperation(),
                    "Structure readiness incorrectly used as redstone gate"
                );
                g.resetObservations();
                g.tickOnce();
                assertNoProduction(h, g);
                assertHidden(h, g);
                h.runAfterDelay(
                    1, () -> {
                        g.setStructureReady(true);
                        h.setBlock(signal, Blocks.AIR);
                        check(
                            h,
                            g.isRedstoneBlockingOperation(),
                            "Redstone gate lost on readiness transition"
                        );
                        requireCap(h, Direction.NORTH);
                        g.resetObservations();
                        g.tickOnce();
                        assertNoProduction(h, g);
                        h.runAfterDelay(
                            1, () -> {
                                h.setBlock(signal, Blocks.REDSTONE_BLOCK);
                                g.tickOnce();
                                equal(
                                    h,
                                    GENERATION,
                                    g.getStoredEnergy(),
                                    "Both gates open but no production"
                                );
                                h.succeed();
                            }
                        );
                    }
                );
            }
        );
    }

    private static void g87(GameTestHelper h) {
        withReadyGenerator(
            h, g -> {
                g.process.allowProduction = true;
                g.tickOnce();
                equal(h, GENERATION, g.getCurrentProductionRate(), "Initial production rate");
                g.setStructureReady(false);
                equal(
                    h,
                    GENERATION,
                    g.getCurrentProductionRate(),
                    "Readiness change erased last tick result early"
                );
                check(h, g.isProducingEnergy(), "Last production result should persist until tick");
                h.runAfterDelay(
                    1, () -> {
                        g.resetObservations();
                        g.tickOnce();
                        assertNoProduction(h, g);
                        equal(h, GENERATION, g.getStoredEnergy(), "Unexpected FE while unready");
                        h.succeed();
                    }
                );
            }
        );
    }

    private static void g88(GameTestHelper h) {
        withReadyGenerator(
            h, original -> {
                charge(h, original, 500);
                original.process.completedTicks = 7;
                mode(h, original, LEFT, EnergyPortMode.OUTPUT);
                original.setStructureReady(false);
                CompoundTag tag = original.saveWithFullMetadata(h.getLevel().registryAccess());
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
                check(h, restored != original, "Expected new instance");
                equal(h, 500, restored.getStoredEnergy(), "Restored FE");
                equal(h, 7, restored.process.completedTicks, "Restored process");
                check(
                    h,
                    restored.getEnergyPortMode(LEFT) == EnergyPortMode.OUTPUT,
                    "Restored port mode"
                );
                check(
                    h,
                    restored.getEnergyHandlerForSide(Direction.NORTH) == null,
                    "Runtime opened during restore"
                );
                // Fixture supplies current physical readiness independently of persisted generator state.
                // Saved readiness was false; fresh fixture default is true. Do not copy readiness from original.
                h.getLevel().setBlockEntity(restored);
                h.runAfterDelay(
                    2, () -> {
                        check(
                            h,
                            h.getLevel().getBlockEntity(h.absolutePos(POS)) == restored,
                            "Restored BE not installed"
                        );
                        requireCap(h, Direction.NORTH);
                        requireCap(h, Direction.EAST);
                        check(
                            h,
                            restored.structureChecks > 0,
                            "Readiness not consulted during opening"
                        );
                        restored.setStructureReady(false);
                        assertHidden(h, restored);
                        restored.process.allowProduction = true;
                        restored.resetObservations();
                        restored.tickOnce();
                        assertNoProduction(h, restored);
                        equal(h, 500, restored.getStoredEnergy(), "Restored FE lost while unready");
                        equal(
                            h,
                            7,
                            restored.process.completedTicks,
                            "Restored process lost while unready"
                        );
                        h.runAfterDelay(
                            1, () -> {
                                restored.setStructureReady(true);
                                restored.tickOnce();
                                equal(
                                    h,
                                    540,
                                    restored.getStoredEnergy(),
                                    "Restored generator did not resume"
                                );
                                equal(
                                    h,
                                    8,
                                    restored.process.completedTicks,
                                    "Restored process did not resume"
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
