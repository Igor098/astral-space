package ru.greatdragonx.astralspace.common.energy;

import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.Test;
import ru.greatdragonx.astralspace.common.energy.component.EnergyComponent;
import ru.greatdragonx.astralspace.common.energy.port.EnergyPortHandler;
import ru.greatdragonx.astralspace.common.energy.port.EnergyPortId;
import ru.greatdragonx.astralspace.common.energy.port.EnergyPortPolicy;
import ru.greatdragonx.astralspace.common.energy.port.FixedEnergyPortPolicy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static ru.greatdragonx.astralspace.common.energy.port.EnergyPortMode.OUTPUT;

class EnergyOutputFactoryTest {
    private static final long TICK = 100L;
    private static final int LIMIT = 80;
    private static final EnergyPortId A = new EnergyPortId("test.output.a");
    private static final EnergyPortId B = new EnergyPortId("test.output.b");
    private static final EnergyPortId UNKNOWN = new EnergyPortId("test.unknown");

    private static final class Fixture {
        private final AtomicInteger changes = new AtomicInteger();
        private final AtomicInteger tickReads = new AtomicInteger();
        private final AtomicBoolean available = new AtomicBoolean(true);
        private final List<EnergyPortId> availabilityChecks = new ArrayList<>();
        private final EnergyComponent energy;
        private final EnergyPortPolicy policy;
        private final EnergyOutputFactory.Result output;
        private long tick = TICK;

        private Fixture(long capacity, long stored, int limit, List<EnergyPortId> ports) {
            energy = new EnergyComponent(capacity, changes::incrementAndGet);
            energy.restoreStoredEnergy(stored);
            policy = new FixedEnergyPortPolicy(
                Map.of(A, OUTPUT, B, OUTPUT), port -> {
                availabilityChecks.add(port);
                return available.get();
            }
            );
            output = EnergyOutputFactory.create(
                energy, ports, policy, limit, () -> {
                    tickReads.incrementAndGet();
                    return tick;
                }
            );
        }

        private void refresh() {
            output.outputController().refreshAvailablePorts();
        }

        private EnergyHandler handler(EnergyPortId port) {
            return new EnergyPortHandler(
                port,
                policy,
                new EnergyPortDispatcher(energy, null, output.outputController())
            );
        }
    }

    private static Fixture fixture() {
        return new Fixture(1000L, 500L, LIMIT, List.of(A));
    }

    private static final class Receiver implements EnergyHandler {
        private final AtomicInteger changes = new AtomicInteger();
        private final EnergyComponent energy = new EnergyComponent(1000L, changes::incrementAndGet);

        @Override
        public long getAmountAsLong() {
            return energy.getStoredEnergy();
        }

        @Override
        public long getCapacityAsLong() {
            return energy.getCapacity();
        }

        @Override
        public int insert(int amount, @NonNull TransactionContext transaction) {
            return energy.insertEnergy(amount, transaction);
        }

        @Override
        public int extract(int amount, @NonNull TransactionContext transaction) {
            return 0;
        }
    }

    private static int pull(Fixture f, EnergyPortId port, int amount) {
        try (Transaction tx = Transaction.openRoot()) {
            int result = f.handler(port).extract(amount, tx);
            tx.commit();
            return result;
        }
    }

    private static int push(Fixture f, EnergyPortId port, Receiver target, int amount) {
        try (Transaction tx = Transaction.openRoot()) {
            int result = f.output.pushTransfer().push(port, target, amount, tx);
            tx.commit();
            return result;
        }
    }

    private static void assertState(Fixture f, long stored, int remaining, int rate, int changes) {
        assertAll(
            () -> assertEquals(stored, f.energy.getStoredEnergy(), "stored energy"),
            () -> assertEquals(remaining, f.output.outputBudget().getRemaining(f.tick), "budget"),
            () -> assertEquals(
                rate,
                f.output.transferTracker().getCurrentTransferRate(f.tick + 1),
                "confirmed rate"
            ),
            () -> assertEquals(changes, f.changes.get(), "energy callbacks")
        );
    }

    @Test
    void shouldRejectNullEnergy() {
        Fixture f = fixture();
        assertThrows(
            NullPointerException.class,
            () -> EnergyOutputFactory.create(null, List.of(A), f.policy, LIMIT, () -> TICK)
        );
    }

    @Test
    void shouldRejectNullPorts() {
        Fixture f = fixture();
        assertThrows(
            NullPointerException.class,
            () -> EnergyOutputFactory.create(f.energy, null, f.policy, LIMIT, () -> TICK)
        );
    }

    @Test
    void shouldRejectNullPolicy() {
        Fixture f = fixture();
        assertThrows(
            NullPointerException.class,
            () -> EnergyOutputFactory.create(f.energy, List.of(A), null, LIMIT, () -> TICK)
        );
    }

    @Test
    void shouldRejectNullTickSupplier() {
        Fixture f = fixture();
        assertThrows(
            NullPointerException.class,
            () -> EnergyOutputFactory.create(f.energy, List.of(A), f.policy, LIMIT, null)
        );
    }

    @Test
    void shouldRejectZeroOutputLimit() {
        Fixture f = fixture();
        assertThrows(
            IllegalArgumentException.class,
            () -> EnergyOutputFactory.create(f.energy, List.of(A), f.policy, 0, () -> TICK)
        );
    }

    @Test
    void shouldRejectNegativeOutputLimit() {
        Fixture f = fixture();
        for (int limit : new int[]{-1, Integer.MIN_VALUE}) {
            assertThrows(
                IllegalArgumentException.class,
                () -> EnergyOutputFactory.create(f.energy, List.of(A), f.policy, limit, () -> TICK)
            );
        }
    }

    @Test
    void shouldRejectDuplicatePortsThroughControllerValidation() {
        Fixture f = fixture();
        assertThrows(
            IllegalArgumentException.class,
            () -> EnergyOutputFactory.create(f.energy, List.of(A, A), f.policy, LIMIT, () -> TICK)
        );
    }

    @Test
    void shouldRejectNullPortElementsThroughControllerValidation() {
        Fixture f = fixture();
        assertThrows(
            NullPointerException.class,
            () -> EnergyOutputFactory.create(
                f.energy,
                Arrays.asList(A, null),
                f.policy,
                LIMIT,
                () -> TICK
            )
        );
    }

    @Test
    void shouldRejectNullResultComponents() {
        EnergyOutputFactory.Result r = fixture().output;
        assertAll(
            () -> assertThrows(
                NullPointerException.class,
                () -> new EnergyOutputFactory.Result(
                    null,
                    r.outputBudget(),
                    r.transferTracker(),
                    r.outputController(),
                    r.pushTransfer()
                )
            ),
            () -> assertThrows(
                NullPointerException.class,
                () -> new EnergyOutputFactory.Result(
                    r.distribution(),
                    null,
                    r.transferTracker(),
                    r.outputController(),
                    r.pushTransfer()
                )
            ),
            () -> assertThrows(
                NullPointerException.class,
                () -> new EnergyOutputFactory.Result(
                    r.distribution(),
                    r.outputBudget(),
                    null,
                    r.outputController(),
                    r.pushTransfer()
                )
            ),
            () -> assertThrows(
                NullPointerException.class,
                () -> new EnergyOutputFactory.Result(
                    r.distribution(),
                    r.outputBudget(),
                    r.transferTracker(),
                    null,
                    r.pushTransfer()
                )
            ),
            () -> assertThrows(
                NullPointerException.class,
                () -> new EnergyOutputFactory.Result(
                    r.distribution(),
                    r.outputBudget(),
                    r.transferTracker(),
                    r.outputController(),
                    null
                )
            )
        );
    }

    @Test
    void shouldCreateAllComponentsWithoutReadingPolicyOrTimeOrChangingEnergy() {
        Fixture f = fixture();
        assertAll(
            () -> assertNotNull(f.output.distribution()),
            () -> assertNotNull(f.output.outputBudget()),
            () -> assertNotNull(f.output.transferTracker()),
            () -> assertNotNull(f.output.outputController()),
            () -> assertNotNull(f.output.pushTransfer()),
            () -> assertTrue(f.availabilityChecks.isEmpty()),
            () -> assertEquals(0, f.tickReads.get())
        );
        assertState(f, 500L, LIMIT, 0, 0);
    }

    @Test
    void shouldAcceptEmptyPortsAndRejectExtraction() {
        Fixture f = new Fixture(1000L, 500L, LIMIT, List.of());
        f.refresh();
        try (Transaction tx = Transaction.openRoot()) {
            assertEquals(0, f.output.outputController().extract(A, 40, tx));
            tx.commit();
        }
        assertEquals(0, f.tickReads.get());
        assertState(f, 500L, LIMIT, 0, 0);
    }

    @Test
    void shouldAcceptMinimumAndMaximumPositiveOutputLimits() {
        Fixture min = new Fixture(1000L, 500L, 1, List.of(A));
        min.refresh();
        assertEquals(1, pull(min, A, 80));
        assertState(min, 499L, 0, 1, 1);

        Fixture max = new Fixture(1000L, 500L, Integer.MAX_VALUE, List.of(A));
        assertState(max, 500L, Integer.MAX_VALUE, 0, 0);
    }

    @Test
    void shouldPreservePortOrderAndDefensivelyCopyTheList() {
        List<EnergyPortId> ports = new ArrayList<>(List.of(B, A));
        Fixture f = new Fixture(1000L, 500L, LIMIT, ports);
        ports.clear();
        ports.add(UNKNOWN);
        f.refresh();
        assertEquals(List.of(B, A), f.availabilityChecks);
    }

    @Test
    void shouldExposeControllerBudgetDistributionAndTrackerUsedByPull() {
        Fixture f = fixture();
        f.refresh();
        assertEquals(30, pull(f, A, 30));
        assertState(f, 470L, 50, 30, 1);
        assertEquals(30, f.output.distribution().getExtractedByPort(A, TICK));
        assertEquals(30, f.output.distribution().getMaxRequestedByPort(A, TICK));
    }

    @Test
    void shouldExposeSameComponentsUsedByPushWithoutRecordingPullDemand() {
        Fixture f = fixture();
        f.refresh();
        Receiver receiver = new Receiver();
        assertEquals(40, push(f, A, receiver, 40));
        assertState(f, 460L, 40, 40, 1);
        assertEquals(40L, receiver.getAmountAsLong());
        assertEquals(1, receiver.changes.get());
        assertEquals(40, f.output.distribution().getExtractedByPort(A, TICK));
        assertEquals(0, f.output.distribution().getMaxRequestedByPort(A, TICK));
    }

    @Test
    void shouldShareOutputBudgetBetweenPullAndPushInBothOrders() {
        Fixture first = fixture();
        first.refresh();
        Receiver r1 = new Receiver();
        assertEquals(30, pull(first, A, 30));
        assertEquals(50, push(first, A, r1, 80));
        assertEquals(0, pull(first, A, 1));
        assertState(first, 420L, 0, 80, 2);
        assertEquals(50L, r1.getAmountAsLong());

        Fixture second = fixture();
        second.refresh();
        Receiver r2 = new Receiver();
        assertEquals(50, push(second, A, r2, 50));
        assertEquals(30, pull(second, A, 80));
        assertState(second, 420L, 0, 80, 2);
        assertEquals(50L, r2.getAmountAsLong());
    }

    @Test
    void shouldRollbackSharedEnergyBudgetHistoryAndStatistics() {
        Fixture f = fixture();
        f.refresh();
        Receiver r = new Receiver();
        Map<EnergyPortId, Long> credits = f.output.distribution().getFairnessCreditsSnapshot();
        try (Transaction tx = Transaction.openRoot()) {
            assertEquals(50, f.output.pushTransfer().push(A, r, 50, tx));
            assertEquals(30, f.handler(A).extract(80, tx));
            assertState(f, 420L, 0, 0, 0);
            assertEquals(50L, r.getAmountAsLong());
            assertEquals(0, r.changes.get());
        }
        assertState(f, 500L, LIMIT, 0, 0);
        assertEquals(0L, r.getAmountAsLong());
        assertEquals(0, r.changes.get());
        assertEquals(0, f.output.distribution().getExtractedByPort(A, TICK));
        assertEquals(0, f.output.distribution().getMaxRequestedByPort(A, TICK));
        assertEquals(credits, f.output.distribution().getFairnessCreditsSnapshot());
        assertEquals(80, push(f, A, r, 80));
        assertState(f, 420L, 0, 80, 1);
    }

    @Test
    void shouldReadCurrentTickLazilyAndRestoreBudgetOnNextTick() {
        Fixture f = fixture();
        f.refresh();
        assertEquals(80, pull(f, A, 80));
        assertState(f, 420L, 0, 80, 1);
        f.tick++;
        assertEquals(80, f.output.outputBudget().getRemaining(f.tick));
        assertEquals(80, f.output.transferTracker().getCurrentTransferRate(f.tick));
        assertEquals(80, push(f, A, new Receiver(), 80));
        assertState(f, 340L, 0, 80, 2);
    }

    @Test
    void shouldUseLivePolicyForBothPushAndPreviouslyCreatedPullHandler() {
        Fixture f = fixture();
        f.refresh();
        EnergyHandler handler = f.handler(A);
        Receiver receiver = new Receiver();
        f.available.set(false);
        f.tickReads.set(0);
        try (Transaction tx = Transaction.openRoot()) {
            assertEquals(0, handler.extract(40, tx));
            assertEquals(0, f.output.pushTransfer().push(A, receiver, 40, tx));
            tx.commit();
        }
        assertEquals(0, f.tickReads.get());
        assertState(f, 500L, LIMIT, 0, 0);
        assertEquals(0L, receiver.getAmountAsLong());
        f.available.set(true);
        f.refresh();
        assertEquals(40, push(f, A, receiver, 40));
        assertState(f, 460L, 40, 40, 1);
    }

    @Test
    void shouldCreateIndependentOutputGraphsOnRepeatedCalls() {
        Fixture f = fixture();
        f.refresh();
        EnergyComponent otherEnergy = new EnergyComponent(
            1000L, () -> {
        }
        );
        otherEnergy.restoreStoredEnergy(500L);
        EnergyOutputFactory.Result other = EnergyOutputFactory.create(
            otherEnergy,
            List.of(A),
            f.policy,
            LIMIT,
            () -> f.tick
        );
        other.outputController().refreshAvailablePorts();
        assertAll(
            () -> assertNotSame(f.output.distribution(), other.distribution()),
            () -> assertNotSame(f.output.outputBudget(), other.outputBudget()),
            () -> assertNotSame(f.output.transferTracker(), other.transferTracker()),
            () -> assertNotSame(f.output.outputController(), other.outputController()),
            () -> assertNotSame(f.output.pushTransfer(), other.pushTransfer())
        );
        assertEquals(80, pull(f, A, 80));
        assertEquals(500L, otherEnergy.getStoredEnergy());
        assertEquals(LIMIT, other.outputBudget().getRemaining(TICK));
        assertEquals(0, other.transferTracker().getCurrentTransferRate(TICK + 1));
        assertEquals(0, other.distribution().getExtractedByPort(A, TICK));
        try (Transaction tx = Transaction.openRoot()) {
            assertEquals(80, other.outputController().extract(A, 80, tx));
            tx.commit();
        }
        assertEquals(420L, otherEnergy.getStoredEnergy());
        assertState(f, 420L, 0, 80, 1);
    }

    @Test
    void shouldUseSameOutputLimitForDistributionAndBudgetAcrossPorts() {
        Fixture f = new Fixture(1000L, 500L, 60, List.of(A, B));
        f.refresh();
        try (Transaction tx = Transaction.openRoot()) {
            f.output.distribution().recordRequest(A, 60, TICK - 1, tx);
            f.output.distribution().recordRequest(B, 60, TICK - 1, tx);
            tx.commit();
        }
        assertEquals(30, pull(f, A, 60));
        assertEquals(30, pull(f, B, 60));
        assertState(f, 440L, 0, 60, 2);
    }

    @Test
    void shouldAllowOutputLimitGreaterThanCapacityWhenBufferIsRefilled() {
        Fixture f = new Fixture(50L, 50L, 100, List.of(A));
        f.refresh();
        Receiver r = new Receiver();
        assertEquals(50, push(f, A, r, 100));
        assertEquals(50, f.output.outputBudget().getRemaining(TICK));
        assertEquals(50, f.energy.storeProducedEnergy(50));
        assertEquals(50, push(f, A, r, 100));
        assertState(f, 0L, 0, 100, 3);
        assertEquals(100L, r.getAmountAsLong());
    }
}
