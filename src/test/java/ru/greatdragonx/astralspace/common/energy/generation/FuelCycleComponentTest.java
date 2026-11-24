package ru.greatdragonx.astralspace.common.energy.generation;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class FuelCycleComponentTest {
    private void assertCycleState(
        FuelCycleComponent component,
        int expectedRemainingTicks,
        int expectedTotalTicks,
        boolean expectedActive
    )
    {
        assertAll(
            () -> assertEquals(expectedRemainingTicks, component.getRemainingTicks()),
            () -> assertEquals(expectedTotalTicks, component.getTotalTicks()),
            () -> assertEquals(expectedActive, component.isActive())
        );
    }

    private void startCommittedCycle(FuelCycleComponent component, int durationTicks)
    {
        try (Transaction tx = Transaction.openRoot()) {
            component.start(durationTicks, tx);
            tx.commit();
        }
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

    private CompoundTag createCycleTag(int remaining, int total)
    {
        CompoundTag tag = new CompoundTag();

        tag.putInt("remaining_ticks", remaining);
        tag.putInt("total_ticks", total);

        return tag;
    }

    private void assertSerializedCycle(CompoundTag tag, int expectedRemaining, int expectedTotal)
    {
        IntTag remainingTag = assertInstanceOf(IntTag.class, tag.get("remaining_ticks"));

        IntTag totalTag = assertInstanceOf(IntTag.class, tag.get("total_ticks"));

        assertAll(
            () -> assertEquals(expectedRemaining, remainingTag.intValue()),
            () -> assertEquals(expectedTotal, totalTag.intValue()),
            () -> assertEquals(2, tag.size())
        );
    }

    @Test
    void shouldCreateEmptyFuelCycleComponent() {
        FuelCycleComponent component = new FuelCycleComponent(() -> {
        });

        assertAll(
            () -> assertEquals(0, component.getRemainingTicks()),
            () -> assertEquals(0, component.getTotalTicks()),
            () -> assertFalse(component.isActive())
        );
    }

    @Test
    void shouldRejectNullCallback() {
        assertThrows(NullPointerException.class, () -> new FuelCycleComponent(null));
    }

    @Test
    void shouldNotNotifyOnCreation() {
        AtomicInteger changes = new AtomicInteger();

        FuelCycleComponent component = new FuelCycleComponent(changes::incrementAndGet);

        assertEquals(0, changes.get());
    }

    @Test
    void shouldNotNotifyOnStateReads() {
        AtomicInteger changes = new AtomicInteger();

        FuelCycleComponent component = new FuelCycleComponent(changes::incrementAndGet);

        component.isActive();
        component.getRemainingTicks();
        component.getTotalTicks();

        assertEquals(0, changes.get());
    }

    @Test
    void shouldPreserveStateOnRepeatedReads() {
        FuelCycleComponent component = new FuelCycleComponent(() -> {
        });

        for (int i = 0; i < 10; i++) {
            assertAll(
                () -> assertFalse(component.isActive()),
                () -> assertEquals(0, component.getRemainingTicks()),
                () -> assertEquals(0, component.getTotalTicks())
            );
        }
    }

    @Test
    void shouldStartFuelCycleOnCommit() {
        AtomicInteger changes = new AtomicInteger();

        FuelCycleComponent component = new FuelCycleComponent(changes::incrementAndGet);

        try (Transaction tx = Transaction.openRoot()) {
            component.start(500, tx);

            assertCycleState(component, 500, 500, true);
            assertEquals(0, changes.get());

            tx.commit();
        }

        assertCycleState(component, 500, 500, true);
        assertEquals(1, changes.get());
    }

    @Test
    void shouldRestoreFuelCycleOnRollback() {
        AtomicInteger changes = new AtomicInteger();

        FuelCycleComponent component = new FuelCycleComponent(changes::incrementAndGet);

        try (Transaction tx = Transaction.openRoot()) {
            component.start(500, tx);

            assertCycleState(component, 500, 500, true);
            assertEquals(0, changes.get());
        }

        assertCycleState(component, 0, 0, false);
        assertEquals(0, changes.get());
    }

    @Test
    void shouldRejectNonPositiveDurationWithoutChangingState() {
        AtomicInteger changes = new AtomicInteger();

        FuelCycleComponent component = new FuelCycleComponent(changes::incrementAndGet);

        try (Transaction tx = Transaction.openRoot()) {
            assertAll(
                () -> assertThrows(IllegalArgumentException.class, () -> component.start(0, tx)),
                () -> assertThrows(IllegalArgumentException.class, () -> component.start(-1, tx)),
                () -> assertThrows(
                    IllegalArgumentException.class,
                    () -> component.start(Integer.MIN_VALUE, tx)
                )
            );

            assertCycleState(component, 0, 0, false);
            assertEquals(0, changes.get());

            tx.commit();
        }

        assertCycleState(component, 0, 0, false);
        assertEquals(0, changes.get());
    }

    @Test
    void shouldRejectNullTransactionWithoutChangingState() {
        AtomicInteger changes = new AtomicInteger();

        FuelCycleComponent component = new FuelCycleComponent(changes::incrementAndGet);

        assertThrows(NullPointerException.class, () -> component.start(500, null));

        assertCycleState(component, 0, 0, false);
        assertEquals(0, changes.get());
    }

    @Test
    void shouldRejectStartingOverActiveFuelCycle() {
        AtomicInteger changes = new AtomicInteger();

        FuelCycleComponent component = new FuelCycleComponent(changes::incrementAndGet);

        try (Transaction tx = Transaction.openRoot()) {
            component.start(500, tx);
            tx.commit();
        }

        try (Transaction tx = Transaction.openRoot()) {
            assertThrows(IllegalStateException.class, () -> component.start(800, tx));

            assertCycleState(component, 500, 500, true);
            assertEquals(1, changes.get());

            tx.commit();
        }

        assertCycleState(component, 500, 500, true);
        assertEquals(1, changes.get());
    }

    @Test
    void shouldRejectStartingOverUncommittedFuelCycle() {
        AtomicInteger changes = new AtomicInteger();

        FuelCycleComponent component = new FuelCycleComponent(changes::incrementAndGet);

        try (Transaction tx = Transaction.openRoot()) {
            component.start(500, tx);

            assertThrows(IllegalStateException.class, () -> component.start(800, tx));

            assertCycleState(component, 500, 500, true);
            assertEquals(0, changes.get());

            tx.commit();
        }

        assertCycleState(component, 500, 500, true);
        assertEquals(1, changes.get());
    }

    @Test
    void shouldSupportSingleTickFuelCycle() {
        AtomicInteger changes = new AtomicInteger();

        FuelCycleComponent component = new FuelCycleComponent(changes::incrementAndGet);

        try (Transaction tx = Transaction.openRoot()) {
            component.start(1, tx);
            tx.commit();
        }

        assertCycleState(component, 1, 1, true);
        assertEquals(1, changes.get());
    }

    @Test
    void shouldSupportMaximumFuelCycleDuration() {
        AtomicInteger changes = new AtomicInteger();

        FuelCycleComponent component = new FuelCycleComponent(changes::incrementAndGet);

        try (Transaction tx = Transaction.openRoot()) {
            component.start(Integer.MAX_VALUE, tx);
            tx.commit();
        }

        assertCycleState(component, Integer.MAX_VALUE, Integer.MAX_VALUE, true);

        assertEquals(1, changes.get());
    }

    @Test
    void shouldNotifyOnlyAfterRootCommit() {
        AtomicInteger changes = new AtomicInteger();

        FuelCycleComponent component = new FuelCycleComponent(changes::incrementAndGet);

        try (Transaction root = Transaction.openRoot()) {
            try (Transaction nested = Transaction.open(root)) {
                component.start(500, nested);

                assertCycleState(component, 500, 500, true);
                assertEquals(0, changes.get());

                nested.commit();
            }

            assertCycleState(component, 500, 500, true);
            assertEquals(0, changes.get());

            root.commit();
        }

        assertCycleState(component, 500, 500, true);
        assertEquals(1, changes.get());
    }

    @Test
    void shouldRollbackCommittedNestedFuelCycleOnRootRollback() {
        AtomicInteger changes = new AtomicInteger();

        FuelCycleComponent component = new FuelCycleComponent(changes::incrementAndGet);

        try (Transaction root = Transaction.openRoot()) {
            try (Transaction nested = Transaction.open(root)) {
                component.start(500, nested);
                nested.commit();
            }

            assertCycleState(component, 500, 500, true);
            assertEquals(0, changes.get());
        }

        assertCycleState(component, 0, 0, false);
        assertEquals(0, changes.get());
    }

    @Test
    void shouldRestoreFuelCycleOnNestedRollback() {
        AtomicInteger changes = new AtomicInteger();

        FuelCycleComponent component = new FuelCycleComponent(changes::incrementAndGet);

        try (Transaction root = Transaction.openRoot()) {
            try (Transaction nested = Transaction.open(root)) {
                component.start(500, nested);

                assertCycleState(component, 500, 500, true);
                assertEquals(0, changes.get());
            }

            assertCycleState(component, 0, 0, false);
            assertEquals(0, changes.get());

            root.commit();
        }

        assertCycleState(component, 0, 0, false);
        assertEquals(0, changes.get());
    }

    @Test
    void shouldAllowStartingAgainAfterNestedRollback() {
        AtomicInteger changes = new AtomicInteger();

        FuelCycleComponent component = new FuelCycleComponent(changes::incrementAndGet);

        try (Transaction root = Transaction.openRoot()) {
            try (Transaction nested = Transaction.open(root)) {
                component.start(500, nested);
            }

            assertCycleState(component, 0, 0, false);

            component.start(800, root);

            assertCycleState(component, 800, 800, true);
            assertEquals(0, changes.get());

            root.commit();
        }

        assertCycleState(component, 800, 800, true);
        assertEquals(1, changes.get());
    }

    @Test
    void shouldCompleteSingleProductionTick() {
        AtomicInteger changes = new AtomicInteger();

        FuelCycleComponent component = new FuelCycleComponent(changes::incrementAndGet);

        startCommittedCycle(component, 500);

        component.completeProductionTick();

        assertCycleState(component, 499, 500, true);
        assertEquals(2, changes.get());
    }

    @Test
    void shouldCompleteMultipleProductionTicks() {
        AtomicInteger changes = new AtomicInteger();

        FuelCycleComponent component = new FuelCycleComponent(changes::incrementAndGet);

        startCommittedCycle(component, 500);

        for (int i = 1; i <= 10; i++) {
            component.completeProductionTick();

            assertCycleState(component, 500 - i, 500, true);

            assertEquals(i + 1, changes.get());
        }
    }

    @Test
    void shouldPreserveTotalDurationAfterCompletingLastTick() {
        AtomicInteger changes = new AtomicInteger();

        FuelCycleComponent component = new FuelCycleComponent(changes::incrementAndGet);

        startCommittedCycle(component, 3);

        component.completeProductionTick();
        component.completeProductionTick();

        assertCycleState(component, 1, 3, true);
        assertEquals(3, changes.get());

        component.completeProductionTick();

        assertCycleState(component, 0, 3, false);
        assertEquals(4, changes.get());
    }

    @Test
    void shouldRejectCompletingTickWithoutStartedCycle() {
        AtomicInteger changes = new AtomicInteger();

        FuelCycleComponent component = new FuelCycleComponent(changes::incrementAndGet);

        assertThrows(IllegalStateException.class, component::completeProductionTick);

        assertCycleState(component, 0, 0, false);
        assertEquals(0, changes.get());
    }

    @Test
    void shouldRejectCompletingTickAfterCycleFinished() {
        AtomicInteger changes = new AtomicInteger();

        FuelCycleComponent component = new FuelCycleComponent(changes::incrementAndGet);

        startCommittedCycle(component, 1);

        component.completeProductionTick();

        assertCycleState(component, 0, 1, false);
        assertEquals(2, changes.get());

        assertThrows(IllegalStateException.class, component::completeProductionTick);

        assertCycleState(component, 0, 1, false);
        assertEquals(2, changes.get());
    }

    @Test
    void shouldNotifyAfterProductionTickStateChanged() {
        AtomicReference<FuelCycleComponent> reference = new AtomicReference<>();

        List<Integer> observedRemainingTicks = new ArrayList<>();

        List<Boolean> observedActivity = new ArrayList<>();

        FuelCycleComponent component = new FuelCycleComponent(() -> {
            FuelCycleComponent current = reference.get();

            observedRemainingTicks.add(current.getRemainingTicks());

            observedActivity.add(current.isActive());
        });

        reference.set(component);

        startCommittedCycle(component, 1);

        component.completeProductionTick();

        assertAll(
            () -> assertEquals(List.of(1, 0), observedRemainingTicks),
            () -> assertEquals(List.of(true, false), observedActivity)
        );

        assertCycleState(component, 0, 1, false);
    }

    @Test
    void shouldCompleteTickWithMaximumCycleDuration() {
        AtomicInteger changes = new AtomicInteger();

        FuelCycleComponent component = new FuelCycleComponent(changes::incrementAndGet);

        startCommittedCycle(component, Integer.MAX_VALUE);

        component.completeProductionTick();

        assertCycleState(component, Integer.MAX_VALUE - 1, Integer.MAX_VALUE, true);

        assertEquals(2, changes.get());
    }

    @Test
    void shouldStartNewCycleAfterPreviousCycleFinished() {
        AtomicInteger changes = new AtomicInteger();

        FuelCycleComponent component = new FuelCycleComponent(changes::incrementAndGet);

        startCommittedCycle(component, 1);

        component.completeProductionTick();

        assertCycleState(component, 0, 1, false);
        assertEquals(2, changes.get());

        try (Transaction tx = Transaction.openRoot()) {
            component.start(800, tx);

            assertCycleState(component, 800, 800, true);
            assertEquals(2, changes.get());

            tx.commit();
        }

        assertCycleState(component, 800, 800, true);
        assertEquals(3, changes.get());
    }

    @Test
    void shouldNotifyWhenRestartingCycleWithSameDuration() {
        AtomicInteger changes = new AtomicInteger();

        FuelCycleComponent component = new FuelCycleComponent(changes::incrementAndGet);

        startCommittedCycle(component, 2);

        component.completeProductionTick();
        component.completeProductionTick();

        assertCycleState(component, 0, 2, false);
        assertEquals(3, changes.get());

        try (Transaction tx = Transaction.openRoot()) {
            component.start(2, tx);

            assertCycleState(component, 2, 2, true);
            assertEquals(3, changes.get());

            tx.commit();
        }

        assertCycleState(component, 2, 2, true);
        assertEquals(4, changes.get());
    }

    @Test
    void shouldRestorePreviousDurationOnRestartRollback() {
        AtomicInteger changes = new AtomicInteger();

        FuelCycleComponent component = new FuelCycleComponent(changes::incrementAndGet);

        startCommittedCycle(component, 2);

        component.completeProductionTick();
        component.completeProductionTick();

        assertCycleState(component, 0, 2, false);
        assertEquals(3, changes.get());

        try (Transaction tx = Transaction.openRoot()) {
            component.start(800, tx);

            assertCycleState(component, 800, 800, true);
            assertEquals(3, changes.get());
        }

        assertCycleState(component, 0, 2, false);
        assertEquals(3, changes.get());
    }

    @Test
    void shouldRestorePreviousDurationOnRootRollbackAfterNestedCommit() {
        AtomicInteger changes = new AtomicInteger();

        FuelCycleComponent component = new FuelCycleComponent(changes::incrementAndGet);

        startCommittedCycle(component, 2);

        component.completeProductionTick();
        component.completeProductionTick();

        try (Transaction root = Transaction.openRoot()) {
            try (Transaction nested = Transaction.open(root)) {
                component.start(800, nested);

                assertCycleState(component, 800, 800, true);
                assertEquals(3, changes.get());

                nested.commit();
            }

            assertCycleState(component, 800, 800, true);
            assertEquals(3, changes.get());
        }

        assertCycleState(component, 0, 2, false);
        assertEquals(3, changes.get());
    }

    @Test
    void shouldRestorePreviousDurationOnNestedRollback() {
        AtomicInteger changes = new AtomicInteger();

        FuelCycleComponent component = new FuelCycleComponent(changes::incrementAndGet);

        startCommittedCycle(component, 2);

        component.completeProductionTick();
        component.completeProductionTick();

        try (Transaction root = Transaction.openRoot()) {
            try (Transaction nested = Transaction.open(root)) {
                component.start(800, nested);

                assertCycleState(component, 800, 800, true);
                assertEquals(3, changes.get());
            }

            assertCycleState(component, 0, 2, false);
            assertEquals(3, changes.get());

            root.commit();
        }

        assertCycleState(component, 0, 2, false);
        assertEquals(3, changes.get());
    }

    @Test
    void shouldSerializeEmptyFuelCycle() {
        AtomicInteger changes = new AtomicInteger();

        FuelCycleComponent component = new FuelCycleComponent(changes::incrementAndGet);

        TagValueOutput output = createOutput();

        component.serialize(output);

        assertSerializedCycle(output.buildResult(), 0, 0);

        assertCycleState(component, 0, 0, false);
        assertEquals(0, changes.get());
    }

    @Test
    void shouldSerializeActiveFuelCycle() {
        AtomicInteger changes = new AtomicInteger();

        FuelCycleComponent component = new FuelCycleComponent(changes::incrementAndGet);

        startCommittedCycle(component, 500);

        component.completeProductionTick();

        TagValueOutput output = createOutput();

        component.serialize(output);

        assertSerializedCycle(output.buildResult(), 499, 500);

        assertCycleState(component, 499, 500, true);
        assertEquals(2, changes.get());
    }

    @Test
    void shouldSerializeCompletedFuelCycle() {
        AtomicInteger changes = new AtomicInteger();

        FuelCycleComponent component = new FuelCycleComponent(changes::incrementAndGet);

        startCommittedCycle(component, 1);

        component.completeProductionTick();

        TagValueOutput output = createOutput();

        component.serialize(output);

        assertSerializedCycle(output.buildResult(), 0, 1);

        assertCycleState(component, 0, 1, false);
        assertEquals(2, changes.get());
    }

    @Test
    void shouldRejectNullSerializationOutput() {
        AtomicInteger changes = new AtomicInteger();

        FuelCycleComponent component = new FuelCycleComponent(changes::incrementAndGet);

        startCommittedCycle(component, 500);

        assertThrows(NullPointerException.class, () -> component.serialize(null));

        assertCycleState(component, 500, 500, true);
        assertEquals(1, changes.get());
    }

    @Test
    void shouldDeserializeActiveFuelCycleSilently() {
        AtomicInteger changes = new AtomicInteger();

        FuelCycleComponent component = new FuelCycleComponent(changes::incrementAndGet);

        startCommittedCycle(component, 800);

        component.deserialize(createInput(createCycleTag(350, 500)));

        assertCycleState(component, 350, 500, true);
        assertEquals(1, changes.get());
    }

    @Test
    void shouldDeserializeCompletedFuelCycleSilently() {
        AtomicInteger changes = new AtomicInteger();

        FuelCycleComponent component = new FuelCycleComponent(changes::incrementAndGet);

        startCommittedCycle(component, 800);

        component.deserialize(createInput(createCycleTag(0, 500)));

        assertCycleState(component, 0, 500, false);
        assertEquals(1, changes.get());
    }

    @Test
    void shouldDeserializeEmptyFuelCycleSilently() {
        AtomicInteger changes = new AtomicInteger();

        FuelCycleComponent component = new FuelCycleComponent(changes::incrementAndGet);

        startCommittedCycle(component, 800);

        component.deserialize(createInput(createCycleTag(0, 0)));

        assertCycleState(component, 0, 0, false);
        assertEquals(1, changes.get());
    }

    @Test
    void shouldRejectNullDeserializationInput() {
        AtomicInteger changes = new AtomicInteger();

        FuelCycleComponent component = new FuelCycleComponent(changes::incrementAndGet);

        startCommittedCycle(component, 500);

        assertThrows(NullPointerException.class, () -> component.deserialize(null));

        assertCycleState(component, 500, 500, true);
        assertEquals(1, changes.get());
    }

    private void assertInvalidRestoration(CompoundTag tag) {
        AtomicInteger changes = new AtomicInteger();

        FuelCycleComponent component = new FuelCycleComponent(changes::incrementAndGet);

        startCommittedCycle(component, 800);

        component.completeProductionTick();

        assertCycleState(component, 799, 800, true);
        assertEquals(2, changes.get());

        component.deserialize(createInput(tag));

        assertCycleState(component, 0, 0, false);
        assertEquals(2, changes.get());
    }

    @Test
    void shouldResetFuelCycleWhenRequiredFieldsAreMissing() {
        CompoundTag missingRemaining = new CompoundTag();
        missingRemaining.putInt("total_ticks", 500);

        CompoundTag missingTotal = new CompoundTag();
        missingTotal.putInt("remaining_ticks", 350);

        assertAll(
            () -> assertInvalidRestoration(missingRemaining),
            () -> assertInvalidRestoration(missingTotal),
            () -> assertInvalidRestoration(new CompoundTag())
        );
    }

    @Test
    void shouldResetFuelCycleWhenInvariantIsViolated() {
        assertAll(
            () -> assertInvalidRestoration(createCycleTag(-1, 500)),
            () -> assertInvalidRestoration(createCycleTag(-100, 500)),
            () -> assertInvalidRestoration(createCycleTag(501, 500)),
            () -> assertInvalidRestoration(createCycleTag(100, 0)),
            () -> assertInvalidRestoration(createCycleTag(0, -500)),
            () -> assertInvalidRestoration(createCycleTag(-1, -1)),
            () -> assertInvalidRestoration(createCycleTag(Integer.MIN_VALUE, 500)),
            () -> assertInvalidRestoration(createCycleTag(0, Integer.MIN_VALUE))
        );
    }

    @Test
    void shouldRejectNonIntNumericTags() {
        List<BiConsumer<CompoundTag, String>> writers = List.of(
            (tag, key) -> tag.putByte(key, (byte) 100),
            (tag, key) -> tag.putShort(key, (short) 100),
            (tag, key) -> tag.putLong(key, 500L),
            (tag, key) -> tag.putFloat(key, 350.5F),
            (tag, key) -> tag.putDouble(key, 350.5D),
            (tag, key) -> tag.putLong(key, 4_294_967_796L)
        );

        for (BiConsumer<CompoundTag, String> writer : writers) {
            CompoundTag invalidRemaining = createCycleTag(350, 500);

            writer.accept(invalidRemaining, "remaining_ticks");

            assertInvalidRestoration(invalidRemaining);

            CompoundTag invalidTotal = createCycleTag(0, 500);

            writer.accept(invalidTotal, "total_ticks");

            assertInvalidRestoration(invalidTotal);
        }
    }

    @Test
    void shouldRejectNonNumericTags() {
        List<BiConsumer<CompoundTag, String>> writers = List.of(
            (tag, key) -> tag.putString(key, "500"),
            (tag, key) -> tag.put(key, new CompoundTag()),
            (tag, key) -> tag.put(key, new ListTag()),
            (tag, key) -> tag.putIntArray(key, new int[]{500})
        );

        for (BiConsumer<CompoundTag, String> writer : writers) {
            CompoundTag invalidRemaining = createCycleTag(350, 500);

            writer.accept(invalidRemaining, "remaining_ticks");

            assertInvalidRestoration(invalidRemaining);

            CompoundTag invalidTotal = createCycleTag(350, 500);

            writer.accept(invalidTotal, "total_ticks");

            assertInvalidRestoration(invalidTotal);
        }
    }

    @Test
    void shouldPreserveFuelCycleDuringSerializationRoundTrip() {
        int[][] states =
            {{0, 0}, {500, 500}, {350, 500}, {1, 500}, {0, 500}, {Integer.MAX_VALUE, Integer.MAX_VALUE}, {0, Integer.MAX_VALUE}};

        for (int[] state : states) {
            AtomicInteger sourceChanges = new AtomicInteger();
            AtomicInteger restoredChanges = new AtomicInteger();

            FuelCycleComponent source = new FuelCycleComponent(sourceChanges::incrementAndGet);

            source.deserialize(createInput(createCycleTag(state[0], state[1])));

            TagValueOutput output = createOutput();

            source.serialize(output);

            CompoundTag serialized = output.buildResult();

            assertSerializedCycle(serialized, state[0], state[1]);

            FuelCycleComponent restored = new FuelCycleComponent(restoredChanges::incrementAndGet);

            restored.deserialize(createInput(serialized));

            assertCycleState(restored, state[0], state[1], state[0] > 0);

            assertCycleState(source, state[0], state[1], state[0] > 0);

            assertEquals(0, sourceChanges.get());
            assertEquals(0, restoredChanges.get());
        }
    }

    @Test
    void shouldContinueProductionAfterDeserialization() {
        AtomicInteger changes = new AtomicInteger();

        FuelCycleComponent component = new FuelCycleComponent(changes::incrementAndGet);

        component.deserialize(createInput(createCycleTag(2, 500)));

        assertCycleState(component, 2, 500, true);
        assertEquals(0, changes.get());

        component.completeProductionTick();

        assertCycleState(component, 1, 500, true);
        assertEquals(1, changes.get());

        component.completeProductionTick();

        assertCycleState(component, 0, 500, false);
        assertEquals(2, changes.get());

        try (Transaction tx = Transaction.openRoot()) {
            component.start(800, tx);

            assertCycleState(component, 800, 800, true);
            assertEquals(2, changes.get());
        }

        assertCycleState(component, 0, 500, false);
        assertEquals(2, changes.get());

        startCommittedCycle(component, 800);

        assertCycleState(component, 800, 800, true);
        assertEquals(3, changes.get());
    }
}
