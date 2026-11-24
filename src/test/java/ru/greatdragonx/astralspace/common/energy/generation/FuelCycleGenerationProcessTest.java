package ru.greatdragonx.astralspace.common.energy.generation;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.OptionalInt;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class FuelCycleGenerationProcessTest {
    private static final class TestFuelStorage {
        private int amount;
        private int changes;

        private final SnapshotJournal<Integer> journal = new SnapshotJournal<>() {
            @Override
            protected @NonNull Integer createSnapshot() {
                return TestFuelStorage.this.amount;
            }

            @Override
            protected void revertToSnapshot(
                @NonNull Integer snapshot
            )
            {
                TestFuelStorage.this.amount = snapshot;
            }

            @Override
            protected void onRootCommit(
                @NonNull Integer originalState
            )
            {
                if (TestFuelStorage.this.amount != originalState) {
                    TestFuelStorage.this.changes++;
                }
            }
        };

        private TestFuelStorage(int amount) {
            this.amount = amount;
        }

        private int extract(int requested, TransactionContext transaction) {
            int extracted = Math.min(requested, this.amount);

            if (extracted > 0) {
                this.journal.updateSnapshots(transaction);
                this.amount -= extracted;
            }

            return extracted;
        }
    }

    private static final class Fixture {
        private final AtomicInteger cycleChanges = new AtomicInteger();

        private final AtomicInteger consumptionCalls = new AtomicInteger();

        private final FuelCycleComponent cycle =
            new FuelCycleComponent(cycleChanges::incrementAndGet);

        private final TestFuelStorage fuel;

        private FuelConsumption consumption;

        private final FuelCycleGenerationProcess process;

        private Fixture(int initialFuel) {
            this.fuel = new TestFuelStorage(initialFuel);

            this.consumption = transaction -> {
                int extracted = this.fuel.extract(100, transaction);

                if (extracted != 100) {
                    return OptionalInt.empty();
                }

                return OptionalInt.of(500);
            };

            this.process = new FuelCycleGenerationProcess(
                this.cycle, transaction -> {
                this.consumptionCalls.incrementAndGet();

                return this.consumption.tryConsume(transaction);
            }
            );
        }
    }

    private void assertCycle(Fixture fixture, int remaining, int total, int changes) {
        assertAll(
            () -> assertEquals(remaining, fixture.cycle.getRemainingTicks(), "remainingTicks"),
            () -> assertEquals(total, fixture.cycle.getTotalTicks(), "totalTicks"),
            () -> assertEquals(remaining > 0, fixture.cycle.isActive(), "isActive"),
            () -> assertEquals(changes, fixture.cycleChanges.get(), "cycleChanges")
        );
    }

    private void assertFuel(Fixture fixture, int amount, int consumptionCalls, int changes) {
        assertAll(
            () -> assertEquals(amount, fixture.fuel.amount, "fuel amount"),
            () -> assertEquals(
                consumptionCalls,
                fixture.consumptionCalls.get(),
                "consumption calls"
            ),
            () -> assertEquals(changes, fixture.fuel.changes, "fuel changes")
        );
    }

    private TagValueOutput createOutput() {
        return TagValueOutput.createWithoutContext(ProblemReporter.DISCARDING);
    }

    private ValueInput createInput(CompoundTag tag) {
        return TagValueInput.create(
            ProblemReporter.DISCARDING,
            HolderLookup.Provider.create(Stream.empty()),
            tag
        );
    }

    private CompoundTag createCycleTag(int remaining, int total) {
        CompoundTag tag = new CompoundTag();

        tag.putInt("remaining_ticks", remaining);
        tag.putInt("total_ticks", total);

        return tag;
    }

    private CompoundTag createProcessTag(int remaining, int total) {
        CompoundTag tag = new CompoundTag();

        tag.put("fuel_cycle", createCycleTag(remaining, total));

        return tag;
    }

    private void restoreCycle(Fixture fixture, int remaining, int total) {
        fixture.cycle.deserialize(createInput(createCycleTag(remaining, total)));
    }

    @Test
    void shouldRejectNullFuelCycle() {
        assertThrows(
            NullPointerException.class,
            () -> new FuelCycleGenerationProcess(null, transaction -> OptionalInt.empty())
        );
    }

    @Test
    void shouldRejectNullFuelConsumption() {
        FuelCycleComponent cycle = new FuelCycleComponent(() -> {
        });

        assertThrows(NullPointerException.class, () -> new FuelCycleGenerationProcess(cycle, null));
    }

    @Test
    void shouldReuseActiveFuelCycleWithoutConsumingFuel() {
        Fixture fixture = new Fixture(1000);

        restoreCycle(fixture, 350, 500);

        assertTrue(fixture.process.prepareProductionTick());

        assertCycle(fixture, 350, 500, 0);
        assertFuel(fixture, 1000, 0, 0);
    }

    @Test
    void shouldConsumeFuelAndStartNewCycle() {
        Fixture fixture = new Fixture(1000);

        assertTrue(fixture.process.prepareProductionTick());

        assertCycle(fixture, 500, 500, 1);
        assertFuel(fixture, 900, 1, 1);
    }

    @Test
    void shouldNotConsumeFuelOnRepeatedPreparation() {
        Fixture fixture = new Fixture(1000);

        assertTrue(fixture.process.prepareProductionTick());

        for (int i = 0; i < 10; i++) {
            assertTrue(fixture.process.prepareProductionTick());
        }

        assertCycle(fixture, 500, 500, 1);
        assertFuel(fixture, 900, 1, 1);
    }

    @Test
    void shouldRejectPreparationWithoutFuel() {
        Fixture fixture = new Fixture(0);

        assertFalse(fixture.process.prepareProductionTick());

        assertCycle(fixture, 0, 0, 0);
        assertFuel(fixture, 0, 1, 0);
    }

    @Test
    void shouldRollbackPartiallyConsumedFuel() {
        Fixture fixture = new Fixture(70);

        assertFalse(fixture.process.prepareProductionTick());

        assertCycle(fixture, 0, 0, 0);
        assertFuel(fixture, 70, 1, 0);
    }

    @Test
    void shouldPreserveCompletedCycleWhenFuelIsInsufficient() {
        Fixture fixture = new Fixture(70);

        restoreCycle(fixture, 0, 800);

        assertFalse(fixture.process.prepareProductionTick());

        assertCycle(fixture, 0, 800, 0);
        assertFuel(fixture, 70, 1, 0);
    }

    @Test
    void shouldStartNewCycleAfterPreviousCycleCompleted() {
        Fixture fixture = new Fixture(1000);

        restoreCycle(fixture, 0, 800);

        assertTrue(fixture.process.prepareProductionTick());

        assertCycle(fixture, 500, 500, 1);
        assertFuel(fixture, 900, 1, 1);
    }

    @Test
    void shouldRollbackFuelWhenDurationIsZero() {
        Fixture fixture = new Fixture(1000);

        restoreCycle(fixture, 0, 800);

        fixture.consumption = transaction -> {
            fixture.fuel.extract(100, transaction);

            return OptionalInt.of(0);
        };

        assertThrows(IllegalArgumentException.class, fixture.process::prepareProductionTick);

        assertCycle(fixture, 0, 800, 0);
        assertFuel(fixture, 1000, 1, 0);
    }

    @Test
    void shouldRollbackFuelWhenDurationIsNegative() {
        Fixture fixture = new Fixture(1000);

        restoreCycle(fixture, 0, 800);

        fixture.consumption = transaction -> {
            fixture.fuel.extract(100, transaction);

            return OptionalInt.of(-500);
        };

        assertThrows(IllegalArgumentException.class, fixture.process::prepareProductionTick);

        assertCycle(fixture, 0, 800, 0);
        assertFuel(fixture, 1000, 1, 0);
    }

    @Test
    void shouldRollbackFuelWhenConsumptionReturnsNull() {
        Fixture fixture = new Fixture(1000);

        fixture.consumption = transaction -> {
            fixture.fuel.extract(100, transaction);

            return null;
        };

        assertThrows(NullPointerException.class, fixture.process::prepareProductionTick);

        assertCycle(fixture, 0, 0, 0);
        assertFuel(fixture, 1000, 1, 0);
    }

    @Test
    void shouldRollbackFuelWhenConsumptionThrowsException() {
        Fixture fixture = new Fixture(1000);

        restoreCycle(fixture, 0, 800);

        IllegalStateException expected = new IllegalStateException("Fuel source failure");

        fixture.consumption = transaction -> {
            fixture.fuel.extract(100, transaction);

            throw expected;
        };

        IllegalStateException actual = assertThrows(
            IllegalStateException.class,
            fixture.process::prepareProductionTick
        );

        assertSame(expected, actual);

        assertCycle(fixture, 0, 800, 0);
        assertFuel(fixture, 1000, 1, 0);
    }

    @Test
    void shouldCompleteExactlyOneProductionTick() {
        Fixture fixture = new Fixture(1000);

        assertTrue(fixture.process.prepareProductionTick());

        fixture.process.onProductionTickCompleted();

        assertCycle(fixture, 499, 500, 2);
        assertFuel(fixture, 900, 1, 1);
    }

    @Test
    void shouldNotConsumeNewFuelWhenLastTickCompletes() {
        Fixture fixture = new Fixture(1000);

        restoreCycle(fixture, 1, 500);

        fixture.process.onProductionTickCompleted();

        assertCycle(fixture, 0, 500, 1);
        assertFuel(fixture, 1000, 0, 0);

        assertTrue(fixture.process.prepareProductionTick());

        assertCycle(fixture, 500, 500, 2);
        assertFuel(fixture, 900, 1, 1);
    }

    @Test
    void shouldRejectCompletionWithoutActiveCycle() {
        Fixture fixture = new Fixture(1000);

        restoreCycle(fixture, 0, 500);

        assertThrows(IllegalStateException.class, fixture.process::onProductionTickCompleted);

        assertCycle(fixture, 0, 500, 0);
        assertFuel(fixture, 1000, 0, 0);
    }

    @Test
    void shouldSaveStateInsideFuelCycleSection() {
        Fixture fixture = new Fixture(1000);

        int[][] states = {{0, 0}, {350, 500}, {0, 500}};

        for (int[] state : states) {
            restoreCycle(fixture, state[0], state[1]);

            TagValueOutput output = createOutput();

            fixture.process.saveState(output);

            CompoundTag result = output.buildResult();

            CompoundTag fuelCycleTag = assertInstanceOf(
                CompoundTag.class,
                result.get("fuel_cycle")
            );

            IntTag remainingTag = assertInstanceOf(
                IntTag.class,
                fuelCycleTag.get("remaining_ticks")
            );

            IntTag totalTag = assertInstanceOf(IntTag.class, fuelCycleTag.get("total_ticks"));

            assertAll(
                () -> assertEquals(1, result.size()),
                () -> assertEquals(2, fuelCycleTag.size()),
                () -> assertEquals(state[0], remainingTag.intValue()),
                () -> assertEquals(state[1], totalTag.intValue())
            );

            assertCycle(fixture, state[0], state[1], 0);

            assertFuel(fixture, 1000, 0, 0);
        }
    }

    @Test
    void shouldRejectNullSaveOutput() {
        Fixture fixture = new Fixture(1000);

        restoreCycle(fixture, 350, 500);

        assertThrows(NullPointerException.class, () -> fixture.process.saveState(null));

        assertCycle(fixture, 350, 500, 0);
        assertFuel(fixture, 1000, 0, 0);
    }

    @Test
    void shouldRestoreFuelCycleFromNestedSection() {
        Fixture fixture = new Fixture(1000);

        int[][] states =
            {{0, 0}, {500, 500}, {350, 500}, {1, 500}, {0, 500}, {Integer.MAX_VALUE, Integer.MAX_VALUE}};

        for (int[] state : states) {
            restoreCycle(fixture, 700, 800);

            fixture.process.restoreState(createInput(createProcessTag(state[0], state[1])));

            assertCycle(fixture, state[0], state[1], 0);

            assertFuel(fixture, 1000, 0, 0);
        }
    }

    @Test
    void shouldResetCycleWhenNestedStateIsInvalid() {
        Fixture fixture = new Fixture(1000);

        CompoundTag wrongSectionType = new CompoundTag();
        wrongSectionType.putInt("fuel_cycle", 500);

        CompoundTag emptySection = new CompoundTag();
        emptySection.put("fuel_cycle", new CompoundTag());

        CompoundTag missingField = new CompoundTag();

        CompoundTag incompleteCycle = new CompoundTag();
        incompleteCycle.putInt("remaining_ticks", 350);

        missingField.put("fuel_cycle", incompleteCycle);

        CompoundTag wrongCounterType = createProcessTag(350, 500);

        CompoundTag invalidCycle = createCycleTag(350, 500);

        invalidCycle.putLong("remaining_ticks", 350L);

        wrongCounterType.put("fuel_cycle", invalidCycle);

        CompoundTag legacyState = new CompoundTag();
        legacyState.putInt("burnTime", 350);
        legacyState.putInt("burnDuration", 500);

        List<CompoundTag> invalidStates = List.of(
            new CompoundTag(),
            wrongSectionType,
            emptySection,
            missingField,
            wrongCounterType,
            createProcessTag(600, 500),
            createProcessTag(-1, 500),
            createCycleTag(350, 500),
            legacyState
        );

        for (CompoundTag state : invalidStates) {
            restoreCycle(fixture, 700, 800);

            fixture.process.restoreState(createInput(state));

            assertCycle(fixture, 0, 0, 0);
            assertFuel(fixture, 1000, 0, 0);
        }
    }

    @Test
    void shouldRejectNullRestoreInput() {
        Fixture fixture = new Fixture(1000);

        restoreCycle(fixture, 350, 500);

        assertThrows(NullPointerException.class, () -> fixture.process.restoreState(null));

        assertCycle(fixture, 350, 500, 0);
        assertFuel(fixture, 1000, 0, 0);
    }

    @Test
    void shouldContinuePaidCycleAfterStateRoundTrip() {
        Fixture source = new Fixture(1000);

        assertTrue(source.process.prepareProductionTick());

        source.process.onProductionTickCompleted();
        source.process.onProductionTickCompleted();

        assertCycle(source, 498, 500, 3);
        assertFuel(source, 900, 1, 1);

        TagValueOutput output = createOutput();

        source.process.saveState(output);

        CompoundTag savedState = output.buildResult();

        Fixture restored = new Fixture(0);

        restored.process.restoreState(createInput(savedState));

        assertCycle(restored, 498, 500, 0);
        assertFuel(restored, 0, 0, 0);

        assertTrue(restored.process.prepareProductionTick());

        assertCycle(restored, 498, 500, 0);
        assertFuel(restored, 0, 0, 0);

        restored.process.onProductionTickCompleted();

        assertCycle(restored, 497, 500, 1);
        assertFuel(restored, 0, 0, 0);
    }
}
