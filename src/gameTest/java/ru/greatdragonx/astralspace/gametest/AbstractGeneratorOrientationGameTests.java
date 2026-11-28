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
import ru.greatdragonx.astralspace.common.energy.port.EnergyPortId;
import ru.greatdragonx.astralspace.common.energy.port.EnergyPortMode;
import ru.greatdragonx.astralspace.common.machine.redstone.RedstoneControlMode;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static ru.greatdragonx.astralspace.gametest.GameTestGeneratorBlockEntity.*;

/**
 * G-68...G-75: real BlockState changes, physical endpoint lifetime and shared budget.
 */
public final class AbstractGeneratorOrientationGameTests {
    private static final BlockPos POS = new BlockPos(1, 1, 1);
    private static final Identifier ENVIRONMENT = id("abstract_generator/orientation_environment");
    private static final Identifier STRUCTURE = id("gametest/empty_3x3x3");
    static final DeferredRegister<Consumer<GameTestHelper>> TEST_FUNCTIONS =
        DeferredRegister.create(BuiltInRegistries.TEST_FUNCTION, AstralSpace.MOD_ID);

    private record Entry(
        Identifier id, DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> function
    ) {}

    private static final List<Entry> TESTS = new ArrayList<>();

    static {
        add("g68_four_orientations", AbstractGeneratorOrientationGameTests::g68);
        add("g69_horizontal_ports_move", AbstractGeneratorOrientationGameTests::g69);
        add("g70_moved_endpoints_revoked", AbstractGeneratorOrientationGameTests::g70);
        add("g71_rotation_back_does_not_revive", AbstractGeneratorOrientationGameTests::g71);
        add("g72_vertical_handler_preserved", AbstractGeneratorOrientationGameTests::g72);
        add("g73_non_facing_state_change", AbstractGeneratorOrientationGameTests::g73);
        add("g74_rotation_invalidates_caches", AbstractGeneratorOrientationGameTests::g74);
        add("g75_rotation_preserves_state_and_budget", AbstractGeneratorOrientationGameTests::g75);
    }

    private AbstractGeneratorOrientationGameTests() {}

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

    private static void rotate(GameTestHelper h, GameTestGeneratorBlockEntity g, Direction facing) {
        // Real world update: Minecraft must deliver the new state to the existing BE.
        h.setBlock(POS, g.getBlockState().setValue(GameTestGeneratorBlock.FACING, facing));
        check(
            h,
            h.getLevel().getBlockEntity(h.absolutePos(POS)) == g,
            "Rotation unexpectedly replaced the BlockEntity"
        );
        check(
            h,
            g.getBlockState().getValue(GameTestGeneratorBlock.FACING) == facing,
            "New facing was not propagated to the existing BlockEntity"
        );
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

    private static void assertRevoked(GameTestHelper h, EnergyHandler handler) {
        equal(h, 0, handler.getAmountAsLong(), "Revoked endpoint amount");
        equal(h, 0, handler.getCapacityAsLong(), "Revoked endpoint capacity");
        try (Transaction tx = Transaction.openRoot()) {
            equal(h, 0, handler.extract(40, tx), "Revoked endpoint extracted FE");
            equal(h, 0, handler.insert(40, tx), "Revoked endpoint inserted FE");
            tx.commit();
        }
    }

    private static void g68(GameTestHelper h) {
        withReadyGenerator(
            h, g -> {
                mode(h, g, BACK, EnergyPortMode.DISABLED);
                mode(h, g, LEFT, EnergyPortMode.OUTPUT);
                // Explicit expected directions; don't use the production rotation algorithm as oracle.
                Direction[][] cases =
                    {{Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST}, {Direction.EAST, Direction.SOUTH, Direction.WEST, Direction.NORTH}, {Direction.SOUTH, Direction.WEST, Direction.NORTH, Direction.EAST}, {Direction.WEST, Direction.NORTH, Direction.EAST, Direction.SOUTH}};
                for (Direction[] sides : cases) {
                    Direction front = sides[0], left = sides[1], back = sides[2], right = sides[3];
                    rotate(h, g, front);
                    EnergyHandler frontHandler = requireCap(h, front);
                    EnergyHandler leftHandler = requireCap(h, left);
                    check(h, frontHandler != leftHandler, "Two logical ports share one endpoint");
                    requireCap(h, Direction.UP);
                    check(h, cap(h, back) == null, "Disabled BACK exposed at " + back);
                    check(h, cap(h, right) == null, "Unknown RIGHT exposed at " + right);
                    check(h, cap(h, Direction.DOWN) == null, "Unknown DOWN exposed");
                    // Distinguish FRONT from LEFT rather than just checking two occupied faces.
                    mode(h, g, FRONT, EnergyPortMode.DISABLED);
                    check(h, cap(h, front) == null, "FRONT routed to wrong face at " + front);
                    check(h, cap(h, left) == leftHandler, "FRONT disable affected LEFT at " + left);
                    mode(h, g, FRONT, EnergyPortMode.OUTPUT);
                    check(h, cap(h, front) == frontHandler, "Unmoved FRONT lost its binding");
                }
                h.succeed();
            }
        );
    }

    private static void g69(GameTestHelper h) {
        withReadyGenerator(
            h, g -> {
                mode(h, g, FRONT, EnergyPortMode.DISABLED);
                mode(h, g, TOP, EnergyPortMode.DISABLED);
                g.charge(500);
                requireCap(h, Direction.SOUTH);
                check(h, cap(h, Direction.WEST) == null, "WEST exposed before rotation");
                rotate(h, g, Direction.EAST);
                check(h, cap(h, Direction.SOUTH) == null, "BACK remained on old world face");
                EnergyHandler moved = requireCap(h, Direction.WEST);
                check(
                    h,
                    moved == g.getEnergyHandlerAt(h.absolutePos(POS), Direction.WEST),
                    "Position-based lookup disagrees with NeoForge capability"
                );
                check(
                    h,
                    g.getEnergyHandlerAt(h.absolutePos(POS).above(), Direction.WEST) == null,
                    "Single-block port exposed at a different position"
                );
                equal(h, 40, pull(moved, 40), "Moved BACK cannot extract");
                equal(h, 460, g.getStoredEnergy(), "Moved BACK did not use shared FE");
                h.succeed();
            }
        );
    }

    private static void g70(GameTestHelper h) {
        withReadyGenerator(
            h, g -> {
                g.charge(500);
                EnergyHandler oldFront = requireCap(h, Direction.NORTH);
                EnergyHandler oldBack = requireCap(h, Direction.SOUTH);
                rotate(h, g, Direction.EAST);
                assertRevoked(h, oldFront);
                assertRevoked(h, oldBack);
                equal(h, 500, g.getStoredEnergy(), "Revoked endpoints changed FE");
                EnergyHandler newFront = requireCap(h, Direction.EAST);
                EnergyHandler newBack = requireCap(h, Direction.WEST);
                check(
                    h,
                    newFront != oldFront && newBack != oldBack,
                    "Moved endpoint reused old binding"
                );
                equal(h, 40, pull(newFront, 40), "New endpoint cannot extract");
                equal(h, 460, g.getStoredEnergy(), "New endpoint changed wrong buffer");
                h.succeed();
            }
        );
    }

    private static void g71(GameTestHelper h) {
        withReadyGenerator(
            h, g -> {
                g.charge(500);
                EnergyHandler oldFront = requireCap(h, Direction.NORTH);
                EnergyHandler oldBack = requireCap(h, Direction.SOUTH);
                rotate(h, g, Direction.EAST);
                assertRevoked(h, oldFront);
                assertRevoked(h, oldBack);
                // Do not request intermediate horizontal endpoints. This catches lazy-only revocation.
                rotate(h, g, Direction.NORTH);
                // Check before requesting new endpoints at the original faces.
                assertRevoked(h, oldFront);
                assertRevoked(h, oldBack);
                EnergyHandler currentFront = requireCap(h, Direction.NORTH);
                EnergyHandler currentBack = requireCap(h, Direction.SOUTH);
                check(h, currentFront != oldFront && currentBack != oldBack, "Old binding revived");
                assertRevoked(h, oldFront);
                equal(h, 40, pull(currentFront, 40), "Recreated FRONT is not usable");
                equal(
                    h,
                    460,
                    g.getStoredEnergy(),
                    "Unexpected FE after returning to original facing"
                );
                h.succeed();
            }
        );
    }

    private static void g72(GameTestHelper h) {
        withReadyGenerator(
            h, g -> {
                g.charge(500);
                EnergyHandler top = requireCap(h, Direction.UP);
                EnergyHandler oldFront = requireCap(h, Direction.NORTH);
                for (Direction facing : List.of(
                    Direction.EAST,
                    Direction.SOUTH,
                    Direction.WEST,
                    Direction.NORTH
                )) {
                    rotate(h, g, facing);
                    check(h, cap(h, Direction.UP) == top, "TOP endpoint replaced at " + facing);
                    equal(h, CAPACITY, top.getCapacityAsLong(), "TOP binding revoked");
                    equal(h, 500, top.getAmountAsLong(), "TOP buffer no longer accessible");
                }
                assertRevoked(h, oldFront);
                equal(h, 40, pull(top, 40), "Unmoved TOP cannot extract");
                equal(h, 460, g.getStoredEnergy(), "Unexpected TOP extraction result");
                h.succeed();
            }
        );
    }

    private static void g73(GameTestHelper h) {
        withReadyGenerator(
            h, g -> {
                g.charge(500);
                EnergyHandler front = requireCap(h, Direction.NORTH);
                EnergyHandler back = requireCap(h, Direction.SOUTH);
                EnergyHandler top = requireCap(h, Direction.UP);
                g.resetObservations();
                h.setBlock(POS, g.getBlockState().setValue(GameTestGeneratorBlock.MARKER, true));
                check(
                    h,
                    h.getLevel().getBlockEntity(h.absolutePos(POS)) == g,
                    "Marker change replaced BE"
                );
                check(
                    h,
                    g.getBlockState().getValue(GameTestGeneratorBlock.MARKER),
                    "Marker not propagated"
                );
                check(
                    h,
                    g.getBlockState().getValue(GameTestGeneratorBlock.FACING) == Direction.NORTH,
                    "Marker changed orientation"
                );
                // Read counters before capability queries, which legitimately call availability predicates.
                equal(h, 0, g.structureChecks, "Non-facing change refreshed port structure");
                equal(h, 0, g.physicalChecks, "Non-facing change refreshed port availability");
                check(
                    h,
                    g.events.stream()
                        .noneMatch(e -> e.startsWith("port:") || e.startsWith("producing:")),
                    "Non-facing change invoked generator hooks: " + g.events
                );
                check(
                    h,
                    cap(h, Direction.NORTH) == front &&
                        cap(h, Direction.SOUTH) == back &&
                        cap(h, Direction.UP) == top,
                    "Unchanged physical endpoints were replaced"
                );
                equal(h, 40, pull(front, 40), "Original handler not usable after marker change");
                equal(h, 460, g.getStoredEnergy(), "Unexpected FE after marker change");
                h.succeed();
            }
        );
    }

    private static void g74(GameTestHelper h) {
        withReadyGenerator(
            h, g -> {
                mode(h, g, BACK, EnergyPortMode.DISABLED);
                g.charge(500);
                AtomicInteger oldInvalidations = new AtomicInteger();
                AtomicInteger newInvalidations = new AtomicInteger();
                AtomicInteger topInvalidations = new AtomicInteger();
                var oldCache = BlockCapabilityCache.create(
                    Capabilities.Energy.BLOCK,
                    h.getLevel(),
                    h.absolutePos(POS),
                    Direction.NORTH,
                    () -> !g.isRemoved(),
                    oldInvalidations::incrementAndGet
                );
                var newCache = BlockCapabilityCache.create(
                    Capabilities.Energy.BLOCK,
                    h.getLevel(),
                    h.absolutePos(POS),
                    Direction.EAST,
                    () -> !g.isRemoved(),
                    newInvalidations::incrementAndGet
                );
                var topCache = BlockCapabilityCache.create(
                    Capabilities.Energy.BLOCK,
                    h.getLevel(),
                    h.absolutePos(POS),
                    Direction.UP,
                    () -> !g.isRemoved(),
                    topInvalidations::incrementAndGet
                );
                EnergyHandler old = requireCap(h, Direction.NORTH);
                EnergyHandler top = requireCap(h, Direction.UP);
                check(h, oldCache.getCapability() == old, "Old face failed to cache handler");
                check(h, newCache.getCapability() == null, "New face must initially cache null");
                check(h, topCache.getCapability() == top, "TOP failed to cache handler");
                rotate(h, g, Direction.EAST);
                equal(h, 1, oldInvalidations.get(), "Old face cache notification");
                equal(h, 1, newInvalidations.get(), "Cached null notification");
                // Invalidation is positional, not directional, even if TOP itself did not move.
                equal(h, 1, topInvalidations.get(), "Same-position TOP cache notification");
                h.runAfterDelay(
                    1, () -> {
                        check(h, oldCache.getCapability() == null, "Old face cache did not clear");
                        EnergyHandler moved = requireCap(h, Direction.EAST);
                        check(
                            h,
                            newCache.getCapability() == moved && moved != old,
                            "Cached null not refreshed"
                        );
                        check(
                            h,
                            topCache.getCapability() == top,
                            "Cache refresh replaced unchanged TOP endpoint"
                        );
                        assertRevoked(h, old);
                        equal(h, 40, pull(moved, 40), "Refreshed new face cannot transfer");
                        equal(h, 460, g.getStoredEnergy(), "Unexpected FE after cached rotation");
                        h.succeed();
                    }
                );
            }
        );
    }

    private static void g75(GameTestHelper h) {
        withReadyGenerator(
            h, g -> {
                mode(h, g, BACK, EnergyPortMode.DISABLED);
                mode(h, g, TOP, EnergyPortMode.DISABLED);
                mode(h, g, LEFT, EnergyPortMode.DISABLED);
                check(
                    h,
                    g.changeRedstoneControlMode(RedstoneControlMode.WHEN_POWERED),
                    "Redstone mode setup"
                );
                g.process.completedTicks = 7;
                g.charge(500);
                long tick = h.getLevel().getGameTime();
                EnergyHandler old = requireCap(h, Direction.NORTH);
                equal(h, 30, pull(old, 30), "Initial Pull");
                equal(h, 470, g.getStoredEnergy(), "Initial buffer after Pull");
                rotate(h, g, Direction.EAST);
                equal(h, tick, h.getLevel().getGameTime(), "Rotation crossed tick boundary");
                equal(h, 470, g.getStoredEnergy(), "Rotation changed FE");
                equal(h, CAPACITY, g.getEnergyCapacity(), "Rotation changed capacity");
                check(
                    h,
                    g.getEnergyPortMode(FRONT) == EnergyPortMode.OUTPUT,
                    "Rotation changed FRONT mode"
                );
                for (EnergyPortId id : List.of(BACK, LEFT, TOP)) {
                    check(
                        h,
                        g.getEnergyPortMode(id) == EnergyPortMode.DISABLED,
                        "Rotation changed mode: " + id
                    );
                }
                check(
                    h,
                    g.getRedstoneControlMode() == RedstoneControlMode.WHEN_POWERED,
                    "Rotation changed redstone mode"
                );
                equal(h, 7, g.process.completedTicks, "Rotation reset process state");
                assertRevoked(h, old);
                EnergyHandler moved = requireCap(h, Direction.EAST);
                equal(h, 50, pull(moved, 80), "Rotation reset shared output budget");
                equal(h, 0, pull(moved, 1), "Exceeded shared output budget after rotation");
                equal(h, 420, g.getStoredEnergy(), "Total Pull must be 80 FE");
                h.runAfterDelay(
                    1, () -> {
                        equal(h, tick + 1, h.getLevel().getGameTime(), "Expected next server tick");
                        equal(
                            h,
                            80,
                            g.getCurrentTransferRate(),
                            "Rotation lost committed transfer statistics"
                        );
                        equal(h, 80, pull(moved, 80), "Budget did not renew on next tick");
                        equal(h, 340, g.getStoredEnergy(), "Unexpected FE after next tick");
                        h.succeed();
                    }
                );
            }
        );
    }
}
