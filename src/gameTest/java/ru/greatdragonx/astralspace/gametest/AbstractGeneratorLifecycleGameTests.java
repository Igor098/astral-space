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
import ru.greatdragonx.astralspace.common.energy.port.EnergyPortMode;
import ru.greatdragonx.astralspace.common.machine.redstone.RedstoneControlMode;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static ru.greatdragonx.astralspace.gametest.GameTestGeneratorBlockEntity.*;

/**
 * G-01...G-09. G-07/G-08 exercise the unload/reopen callbacks, not disk/chunk IO.
 */
public final class AbstractGeneratorLifecycleGameTests {
    private static final BlockPos POS = new BlockPos(1, 1, 1);
    private static final Identifier ENVIRONMENT = id("abstract_generator/lifecycle_environment");
    private static final Identifier STRUCTURE = id("gametest/empty_3x3x3");
    static final DeferredRegister<Consumer<GameTestHelper>> TEST_FUNCTIONS =
        DeferredRegister.create(BuiltInRegistries.TEST_FUNCTION, AstralSpace.MOD_ID);

    private record Entry(
        Identifier id, DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> function
    ) {}

    private static final List<Entry> TESTS = new ArrayList<>();

    static {
        add("g01_opens_runtime", AbstractGeneratorLifecycleGameTests::g01);
        add("g02_repeated_on_load", AbstractGeneratorLifecycleGameTests::g02);
        add("g03_restore_before_opening", AbstractGeneratorLifecycleGameTests::g03);
        add("g04_removal_closes_runtime", AbstractGeneratorLifecycleGameTests::g04);
        add("g05_removed_handler_invalid", AbstractGeneratorLifecycleGameTests::g05);
        add("g06_replacement_invalidates_old_handler", AbstractGeneratorLifecycleGameTests::g06);
        add("g07_unload_callback", AbstractGeneratorLifecycleGameTests::g07);
        add("g08_reopen_after_unload_callback", AbstractGeneratorLifecycleGameTests::g08);
        add("g09_failed_open_closes_runtime", AbstractGeneratorLifecycleGameTests::g09);
    }

    private AbstractGeneratorLifecycleGameTests() {}

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

    private static GameTestGeneratorBlockEntity place(GameTestHelper h) {
        h.setBlock(POS, GameTestGeneratorRegistry.CONFIGURABLE_GENERATOR.get().defaultBlockState());
        return h.getBlockEntity(POS, GameTestGeneratorBlockEntity.class);
    }

    private static void withReadyGenerator(
        GameTestHelper h,
        Consumer<GameTestGeneratorBlockEntity> body
    )
    {
        GameTestGeneratorBlockEntity generator = place(h);
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

    private static void assertRevoked(GameTestHelper h, EnergyHandler handler) {
        equal(h, 0L, handler.getAmountAsLong(), "Revoked handler amount");
        equal(h, 0L, handler.getCapacityAsLong(), "Revoked handler capacity");
        try (Transaction tx = Transaction.openRoot()) {
            equal(h, 0L, handler.insert(40, tx), "Revoked handler insert");
            equal(h, 0L, handler.extract(40, tx), "Revoked handler extract");
            tx.commit();
        }
    }

    private static void assertClosed(GameTestHelper h, GameTestGeneratorBlockEntity g) {
        check(
            h,
            g.getEnergyHandlerForSide(Direction.NORTH) == null,
            "Closed runtime exposes handler"
        );
        check(
            h,
            !g.changeEnergyPortMode(FRONT, EnergyPortMode.DISABLED),
            "Closed runtime changes port mode"
        );
        check(
            h,
            !g.changeRedstoneControlMode(RedstoneControlMode.WHEN_POWERED),
            "Closed runtime changes redstone mode"
        );
        equal(h, 0L, g.getCurrentProductionRate(), "Closed runtime production rate");
        equal(h, 0L, g.getCurrentTransferRate(), "Closed runtime transfer rate");
        check(h, !g.isProducingEnergy(), "Closed runtime producing");
        check(
            h,
            !g.hasRedstoneSignal() && !g.isRedstoneBlockingOperation(),
            "Closed runtime redstone query"
        );
        g.resetObservations();
        g.tickOnce();
        equal(h, 0L, g.process.prepareCalls, "Closed runtime prepare calls");
        equal(h, 0L, g.structureChecks, "Closed runtime structure checks");
        check(h, g.events.isEmpty(), "Closed runtime invoked events: " + g.events);
    }

    private static void g01(GameTestHelper h) {
        GameTestGeneratorBlockEntity g = place(h);
        check(h, cap(h, Direction.NORTH) == null, "Runtime opened before scheduled onLoad");
        assertClosed(h, g);
        h.runAfterDelay(
            2, () -> {
                EnergyHandler handler = requireCap(h, Direction.NORTH);
                equal(h, CAPACITY, handler.getCapacityAsLong(), "Live capability capacity");
                equal(h, 400, g.charge(400), "Fixture charge");
                equal(h, 40, pull(handler, 40), "Open runtime allows extraction");
                equal(h, 360, g.getStoredEnergy(), "Shared stored energy");
                h.succeed();
            }
        );
    }

    private static void g02(GameTestHelper h) {
        withReadyGenerator(
            h, g -> {
                EnergyHandler before = requireCap(h, Direction.NORTH);
                g.resetObservations();
                g.onLoad();
                equal(h, 0, g.structureChecks, "Repeated onLoad resynchronized structure");
                equal(h, 0, g.physicalChecks, "Repeated onLoad resynchronized ports");
                check(h, g.events.isEmpty(), "Repeated onLoad called hooks");
                check(h, before == cap(h, Direction.NORTH), "Repeated onLoad replaced endpoint");
                h.succeed();
            }
        );
    }

    private static void g03(GameTestHelper h) {
        withReadyGenerator(
            h, original -> {
                equal(h, 500, original.charge(500), "Fixture charge");
                check(
                    h,
                    original.changeEnergyPortMode(FRONT, EnergyPortMode.DISABLED),
                    "Disable FRONT"
                );
                check(h, original.changeEnergyPortMode(LEFT, EnergyPortMode.OUTPUT), "Enable LEFT");
                check(
                    h,
                    original.changeRedstoneControlMode(RedstoneControlMode.WHEN_POWERED),
                    "Set redstone mode"
                );
                original.process.completedTicks = 7;
                CompoundTag tag = original.saveWithFullMetadata(h.getLevel().registryAccess());
                BlockEntity loaded = BlockEntity.loadStatic(
                    original.getBlockPos(),
                    original.getBlockState(),
                    tag,
                    h.getLevel().registryAccess()
                );
                if (!(loaded instanceof GameTestGeneratorBlockEntity restored)) {
                    h.fail("loadStatic failed to restore registered test generator");
                    return;
                }
                check(h, original != restored, "NBT restored same instance");
                check(
                    h,
                    restored.getEnergyHandlerForSide(Direction.EAST) == null,
                    "Restored BE runtime prematurely opened"
                );
                equal(h, 500, restored.getStoredEnergy(), "Restored FE");
                equal(h, 7, restored.process.completedTicks, "Restored process state");
                check(
                    h,
                    restored.getRedstoneControlMode() == RedstoneControlMode.WHEN_POWERED,
                    "Restored redstone mode"
                );
                // Install actual restored BE into the chunk; do not hot-load the original.
                h.getLevel().setBlockEntity(restored);
                h.runAfterDelay(
                    2, () -> {
                        check(
                            h,
                            h.getLevel().getBlockEntity(h.absolutePos(POS)) == restored,
                            "Restored BE not installed"
                        );
                        check(
                            h,
                            cap(h, Direction.NORTH) == null,
                            "Restored disabled FRONT exposed"
                        );
                        equal(
                            h,
                            40,
                            pull(requireCap(h, Direction.EAST), 40),
                            "Restored LEFT absent from distribution"
                        );
                        equal(
                            h,
                            460,
                            restored.getStoredEnergy(),
                            "Restored generator FE after pull"
                        );
                        h.succeed();
                    }
                );
            }
        );
    }

    private static void g04(GameTestHelper h) {
        withReadyGenerator(
            h, g -> {
                g.charge(500);
                g.process.allowProduction = true;
                h.setBlock(POS, Blocks.AIR);
                check(h, g.isRemoved(), "World removal did not mark BE removed");
                check(h, cap(h, Direction.NORTH) == null, "Removed block exposes capability");
                assertClosed(h, g);
                equal(h, 500, g.getStoredEnergy(), "Closing runtime must retain FE");
                h.succeed();
            }
        );
    }

    private static void g05(GameTestHelper h) {
        withReadyGenerator(
            h, g -> {
                g.charge(500);
                EnergyHandler stale = requireCap(h, Direction.NORTH);
                equal(h, 500, stale.getAmountAsLong(), "Live reference before removal");
                h.setBlock(POS, Blocks.AIR);
                assertRevoked(h, stale);
                equal(h, 500, g.getStoredEnergy(), "Stale handler changed old energy");
                h.succeed();
            }
        );
    }

    private static void g06(GameTestHelper h) {
        withReadyGenerator(
            h, old -> {
                old.charge(300);
                EnergyHandler stale = requireCap(h, Direction.NORTH);
                h.setBlock(POS, Blocks.AIR);
                GameTestGeneratorBlockEntity replacement = place(h);
                h.runAfterDelay(
                    2, () -> {
                        check(h, replacement != old, "Replacement reused old BE");
                        EnergyHandler fresh = requireCap(h, Direction.NORTH);
                        check(h, fresh != stale, "Replacement reused stale capability");
                        replacement.charge(500);
                        assertRevoked(h, stale);
                        equal(h, 300, old.getStoredEnergy(), "Old buffer changed");
                        equal(
                            h,
                            500,
                            replacement.getStoredEnergy(),
                            "Replacement buffer changed by stale handler"
                        );
                        equal(h, 40, pull(fresh, 40), "Replacement cannot transfer");
                        h.succeed();
                    }
                );
            }
        );
    }

    private static void g07(GameTestHelper h) {
        withReadyGenerator(
            h, g -> {
                g.charge(500);
                g.process.completedTicks = 4;
                EnergyHandler old = requireCap(h, Direction.NORTH);
                // Exercise the real callback, deliberately without unloading GameTest's chunk.
                g.onChunkUnloaded();
                assertClosed(h, g);
                assertRevoked(h, old);
                equal(h, 500, g.getStoredEnergy(), "Unload callback lost stored energy");
                equal(h, 4, g.process.completedTicks, "Unload callback lost process state");
                check(
                    h,
                    g.getEnergyPortMode(FRONT) == EnergyPortMode.OUTPUT,
                    "Unload callback lost configuration"
                );
                h.succeed();
            }
        );
    }

    private static void g08(GameTestHelper h) {
        withReadyGenerator(
            h, g -> {
                g.charge(500);
                g.process.completedTicks = 4;
                g.changeEnergyPortMode(LEFT, EnergyPortMode.OUTPUT);
                EnergyHandler old = requireCap(h, Direction.NORTH);
                // Explicit callback-level reopen of the SAME object, not a disk reload.
                g.onChunkUnloaded();
                g.onLoad();
                EnergyHandler fresh = requireCap(h, Direction.NORTH);
                check(h, old != fresh, "Reopened runtime reused revoked endpoint");
                assertRevoked(h, old);
                check(h, cap(h, Direction.EAST) != null, "Reopened runtime lost configured port");
                equal(h, 4, g.process.completedTicks, "Reopen lost process state");
                equal(h, 40, pull(fresh, 40), "Reopened runtime did not restore distribution");
                equal(h, 460, g.getStoredEnergy(), "Reopen lost energy");
                h.succeed();
            }
        );
    }

    private static void g09(GameTestHelper h) {
        // Detached BE with a real ServerLevel: inject a failure during onLoad,
        // not during the world's automatic onLoad (which would crash the test server).
        GameTestGeneratorBlockEntity g = new GameTestGeneratorBlockEntity(
            h.absolutePos(POS),
            GameTestGeneratorRegistry.CONFIGURABLE_GENERATOR.get().defaultBlockState()
        );
        g.charge(500);
        g.setLevel(h.getLevel());
        g.failNextStructureCheck();
        boolean thrown = false;
        try {
            g.onLoad();
        } catch (IllegalStateException e) {
            check(
                h,
                "Intentional GameTest structure failure".equals(e.getMessage()),
                "Unexpected failure: " + e
            );
            thrown = true;
        }
        check(h, thrown, "Opening failure was swallowed");
        assertClosed(h, g);
        equal(h, 500, g.getStoredEnergy(), "Failed opening lost stored energy");
        // Install the same recovered object; NeoForge should reopen it automatically.
        place(h);
        h.getLevel().setBlockEntity(g);
        h.runAfterDelay(
            2, () -> {
                check(
                    h,
                    h.getLevel().getBlockEntity(h.absolutePos(POS)) == g,
                    "Recovered BE not installed"
                );
                equal(
                    h,
                    40,
                    pull(requireCap(h, Direction.NORTH), 40),
                    "Runtime cannot recover after opening failure"
                );
                equal(h, 460, g.getStoredEnergy(), "Recovered buffer");
                h.succeed();
            }
        );
    }
}
