package ru.greatdragonx.astralspace.common.energy;

import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.Test;
import ru.greatdragonx.astralspace.common.energy.component.EnergyComponent;
import ru.greatdragonx.astralspace.common.energy.port.*;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class EnergyPushTransferTest {
    private static final long TICK = 100L;
    private static final int LIMIT = 80;
    private static final EnergyPortId OUT = new EnergyPortId("test.output");
    private static final EnergyPortId OTHER = new EnergyPortId("test.other");
    private static final EnergyPortId IN = new EnergyPortId("test.input");
    private static final EnergyPortId DISABLED = new EnergyPortId("test.disabled");
    private static final EnergyPortId UNKNOWN = new EnergyPortId("test.unknown");
    private static final EnergyPortId PHANTOM = new EnergyPortId("test.phantom");

    private static final class Fixture {
        private final AtomicInteger sourceChanges = new AtomicInteger();
        private final AtomicInteger policyReads = new AtomicInteger();
        private final AtomicInteger tickReads = new AtomicInteger();
        private final AtomicBoolean available = new AtomicBoolean(true);
        private final EnergyComponent source;
        private final EnergyTransferBudget budget = new EnergyTransferBudget(LIMIT);
        private final EnergyDistributionManager distribution = new EnergyDistributionManager(LIMIT);
        private final EnergyTransferTracker tracker = new EnergyTransferTracker();
        private final EnergyPortPolicy policy;
        private final EnergyOutputController output;
        private final EnergyPortDispatcher dispatcher;
        private final EnergyPushTransfer push;
        private long tick = TICK;

        private Fixture(long capacity, long stored, List<EnergyPortId> ports) {
            source = new EnergyComponent(capacity, sourceChanges::incrementAndGet);
            source.restoreStoredEnergy(stored);
            policy = new FixedEnergyPortPolicy(
                Map.of(
                    OUT,
                    EnergyPortMode.OUTPUT,
                    OTHER,
                    EnergyPortMode.OUTPUT,
                    IN,
                    EnergyPortMode.INPUT,
                    DISABLED,
                    EnergyPortMode.DISABLED,
                    PHANTOM,
                    EnergyPortMode.OUTPUT
                ), ignored -> {
                policyReads.incrementAndGet();
                return available.get();
            }
            );
            output = new EnergyOutputController(
                source, ports, policy, distribution, budget, tracker, () -> {
                tickReads.incrementAndGet();
                return tick;
            }
            );
            output.refreshAvailablePorts();
            policyReads.set(0);
            dispatcher = new EnergyPortDispatcher(source, null, output);
            push = new EnergyPushTransfer(source, output, policy);
        }

        private EnergyHandler pullHandler(EnergyPortId port) {
            return new EnergyPortHandler(port, policy, dispatcher);
        }
    }

    private static Fixture fixture(long stored) {
        return new Fixture(1000, stored, List.of(OUT, IN, DISABLED));
    }

    private static final class Receiver implements EnergyHandler {
        private final AtomicInteger changes = new AtomicInteger();
        private final EnergyComponent energy;
        private final int perCallLimit;
        private final Runnable onInsert;
        private int insertCalls;

        private Receiver(long capacity, long stored, int perCallLimit, Runnable onInsert) {
            energy = new EnergyComponent(capacity, changes::incrementAndGet);
            energy.restoreStoredEnergy(stored);
            this.perCallLimit = perCallLimit;
            this.onInsert = onInsert;
        }

        private static Receiver accepting() {
            return new Receiver(
                1000, 0, Integer.MAX_VALUE, () -> {
            }
            );
        }

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
            insertCalls++;
            onInsert.run();
            return energy.insertEnergy(Math.min(amount, perCallLimit), transaction);
        }

        @Override
        public int extract(int amount, @NonNull TransactionContext transaction) {
            return 0;
        }
    }

    private static int pushCommitted(Fixture f, EnergyPortId port, EnergyHandler target, int amount)
    {
        try (Transaction tx = Transaction.openRoot()) {
            int transferred = f.push.push(port, target, amount, tx);
            tx.commit();
            return transferred;
        }
    }

    private static int pullCommitted(Fixture f, EnergyPortId port, int amount) {
        try (Transaction tx = Transaction.openRoot()) {
            int transferred = f.pullHandler(port).extract(amount, tx);
            tx.commit();
            return transferred;
        }
    }

    private static void assertState(
        Fixture f,
        long stored,
        int remaining,
        int confirmed,
        int changes
    )
    {
        assertAll(
            () -> assertEquals(stored, f.source.getStoredEnergy(), "Запас источника"),
            () -> assertEquals(remaining, f.budget.getRemaining(f.tick), "Исходящий бюджет"),
            () -> assertEquals(
                confirmed,
                f.tracker.getCurrentTransferRate(f.tick + 1),
                "Статистика"
            ),
            () -> assertEquals(changes, f.sourceChanges.get(), "Уведомления источника")
        );
    }

    private static void assertPort(Fixture f, EnergyPortId port, int demand, int extracted) {
        assertAll(
            () -> assertEquals(
                demand,
                f.distribution.getMaxRequestedByPort(port, f.tick),
                "Pull-спрос"
            ),
            () -> assertEquals(
                extracted,
                f.distribution.getExtractedByPort(port, f.tick),
                "Извлечение"
            )
        );
    }

    private static void assertReceiver(Receiver receiver, long stored, int changes) {
        assertEquals(stored, receiver.getAmountAsLong());
        assertEquals(changes, receiver.changes.get());
    }

    @Test
    void shouldRejectNullConstructorDependencies() {
        Fixture f = fixture(500);
        assertAll(
            () -> assertThrows(
                NullPointerException.class,
                () -> new EnergyPushTransfer(null, f.output, f.policy)
            ),
            () -> assertThrows(
                NullPointerException.class,
                () -> new EnergyPushTransfer(f.source, null, f.policy)
            ),
            () -> assertThrows(
                NullPointerException.class,
                () -> new EnergyPushTransfer(f.source, f.output, null)
            )
        );
    }

    @Test
    void shouldConstructWithoutReadingPolicyOrTimeOrChangingState() {
        Fixture f = fixture(500);
        assertEquals(0, f.policyReads.get());
        assertEquals(0, f.tickReads.get());
        assertState(f, 500, 80, 0, 0);
        assertPort(f, OUT, 0, 0);
    }

    @Test
    void shouldRejectNullArgumentsWithoutModifyingEnergy() {
        Fixture f = fixture(500);
        Receiver r = Receiver.accepting();
        try (Transaction tx = Transaction.openRoot()) {
            assertAll(
                () -> assertThrows(NullPointerException.class, () -> f.push.push(null, r, 0, tx)),
                () -> assertThrows(NullPointerException.class, () -> f.push.push(OUT, null, 0, tx)),
                () -> assertThrows(NullPointerException.class, () -> f.push.push(OUT, r, 0, null))
            );
            tx.commit();
        }
        assertEquals(0, f.policyReads.get());
        assertEquals(0, f.tickReads.get());
        assertEquals(0, r.insertCalls);
        assertState(f, 500, 80, 0, 0);
        assertReceiver(r, 0, 0);
    }

    @Test
    void shouldRejectNegativeAmountBeforeAnyTransfer() {
        Fixture f = fixture(500);
        Receiver r = Receiver.accepting();
        try (Transaction tx = Transaction.openRoot()) {
            assertThrows(IllegalArgumentException.class, () -> f.push.push(DISABLED, r, -1, tx));
            tx.commit();
        }
        assertEquals(0, f.policyReads.get());
        assertEquals(0, f.tickReads.get());
        assertEquals(0, r.insertCalls);
        assertState(f, 500, 80, 0, 0);
    }

    @Test
    void shouldIgnoreZeroAmountWithoutTouchingTarget() {
        Fixture f = fixture(500);
        Receiver r = Receiver.accepting();
        assertEquals(0, pushCommitted(f, OUT, r, 0));
        assertEquals(0, f.policyReads.get());
        assertEquals(0, f.tickReads.get());
        assertEquals(0, r.insertCalls);
        assertState(f, 500, 80, 0, 0);
        assertPort(f, OUT, 0, 0);
    }

    @Test
    void shouldRejectInputDisabledAndUnknownPorts() {
        Fixture f = fixture(500);
        Receiver r = Receiver.accepting();
        for (EnergyPortId port : List.of(IN, DISABLED, UNKNOWN)) {
            assertEquals(0, pushCommitted(f, port, r, 40));
            assertPort(f, port, 0, 0);
        }
        assertEquals(0, r.insertCalls);
        assertEquals(0, f.tickReads.get());
        assertState(f, 500, 80, 0, 0);
        assertReceiver(r, 0, 0);
    }

    @Test
    void shouldRejectPortUnknownToControllerEvenIfPolicyAllowsIt() {
        Fixture f = fixture(500);
        Receiver r = Receiver.accepting();
        assertTrue(f.policy.allowsExtract(PHANTOM));
        f.distribution.updateAvailablePorts(List.of(OUT, PHANTOM));
        assertEquals(0, pushCommitted(f, PHANTOM, r, 40));
        assertEquals(0, f.tickReads.get());
        assertState(f, 500, 80, 0, 0);
        assertReceiver(r, 0, 0);
        assertPort(f, PHANTOM, 0, 0);
    }

    @Test
    void shouldRespectAvailabilityChangesWithoutRecreatingTransfer() {
        Fixture f = fixture(500);
        Receiver r = Receiver.accepting();
        f.available.set(false);
        assertEquals(0, pushCommitted(f, OUT, r, 20));
        assertEquals(0, f.tickReads.get());
        assertEquals(0, r.insertCalls);
        f.available.set(true);
        assertEquals(20, pushCommitted(f, OUT, r, 20));
        int tickReads = f.tickReads.get();
        int insertCalls = r.insertCalls;
        f.available.set(false);
        assertEquals(0, pushCommitted(f, OUT, r, 20));
        assertEquals(tickReads, f.tickReads.get());
        assertEquals(insertCalls, r.insertCalls);
        f.available.set(true);
        assertEquals(20, pushCommitted(f, OUT, r, 20));
        assertState(f, 460, 40, 40, 2);
        assertReceiver(r, 40, 2);
        assertPort(f, OUT, 0, 40);
    }

    @Test
    void shouldMoveEnergyToTargetAndRecordOnlyAfterRootCommit() {
        Fixture f = fixture(500);
        Receiver r = Receiver.accepting();
        try (Transaction root = Transaction.openRoot()) {
            assertEquals(60, f.push.push(OUT, r, 60, root));
            assertState(f, 440, 20, 0, 0);
            assertReceiver(r, 60, 0);
            assertPort(f, OUT, 0, 60);
            root.commit();
        }
        assertState(f, 440, 20, 60, 1);
        assertReceiver(r, 60, 1);
        assertPort(f, OUT, 0, 60);
    }

    @Test
    void shouldMoveOnlyWhatTargetCanFit() {
        Fixture f = fixture(500);
        Receiver r = new Receiver(
            100, 95, Integer.MAX_VALUE, () -> {
        }
        );
        assertEquals(5, pushCommitted(f, OUT, r, 80));
        assertState(f, 495, 75, 5, 1);
        assertReceiver(r, 100, 1);
        assertPort(f, OUT, 0, 5);
    }

    @Test
    void shouldMoveOnlyWhatTargetAcceptsPerCall() {
        Fixture f = fixture(500);
        Receiver r = new Receiver(
            1000, 0, 12, () -> {
        }
        );
        assertEquals(12, pushCommitted(f, OUT, r, 80));
        assertState(f, 488, 68, 12, 1);
        assertReceiver(r, 12, 1);
        assertPort(f, OUT, 0, 12);
    }

    @Test
    void shouldNotTransferWhenTargetIsFull() {
        Fixture f = fixture(500);
        Receiver r = new Receiver(
            100, 100, Integer.MAX_VALUE, () -> {
        }
        );
        assertEquals(0, pushCommitted(f, OUT, r, 80));
        assertState(f, 500, 80, 0, 0);
        assertReceiver(r, 100, 0);
        assertPort(f, OUT, 0, 0);
    }

    @Test
    void shouldNotTransferWhenSourceIsEmpty() {
        Fixture f = fixture(0);
        Receiver r = Receiver.accepting();
        assertEquals(0, pushCommitted(f, OUT, r, 80));
        assertState(f, 0, 80, 0, 0);
        assertReceiver(r, 0, 0);
        assertPort(f, OUT, 0, 0);
    }

    @Test
    void shouldShareOutputBudgetAcrossPushOperations() {
        Fixture f = fixture(500);
        Receiver a = Receiver.accepting();
        Receiver b = Receiver.accepting();
        assertEquals(50, pushCommitted(f, OUT, a, 50));
        assertEquals(30, pushCommitted(f, OUT, b, 50));
        assertEquals(0, pushCommitted(f, OUT, b, 10));
        assertState(f, 420, 0, 80, 2);
        assertReceiver(a, 50, 1);
        assertReceiver(b, 30, 1);
        assertPort(f, OUT, 0, 80);
    }

    @Test
    void shouldShareOutputBudgetBetweenPushThenPull() {
        Fixture f = fixture(500);
        Receiver r = Receiver.accepting();
        assertEquals(50, pushCommitted(f, OUT, r, 50));
        assertPort(f, OUT, 0, 50);
        assertEquals(30, pullCommitted(f, OUT, 80));
        assertState(f, 420, 0, 80, 2);
        assertReceiver(r, 50, 1);
        assertPort(f, OUT, 80, 80);
    }

    @Test
    void shouldShareOutputBudgetBetweenPullThenPush() {
        Fixture f = fixture(500);
        Receiver r = Receiver.accepting();
        assertEquals(25, pullCommitted(f, OUT, 25));
        assertEquals(55, pushCommitted(f, OUT, r, 80));
        assertState(f, 420, 0, 80, 2);
        assertReceiver(r, 55, 1);
        assertPort(f, OUT, 25, 80);
    }

    @Test
    void shouldProtectOtherPortQuotaDuringPush() {
        Fixture f = new Fixture(1000, 40, List.of(OUT, OTHER));
        Receiver r = Receiver.accepting();
        try (Transaction tx = Transaction.openRoot()) {
            f.distribution.recordRequest(OUT, 80, TICK - 1, tx);
            f.distribution.recordRequest(OTHER, 80, TICK - 1, tx);
            tx.commit();
        }
        f.distribution.updatePushAvailability(Map.of(OUT, 80), TICK);
        assertEquals(20, pushCommitted(f, OUT, r, 80));
        assertEquals(20, pullCommitted(f, OTHER, 80));
        assertState(f, 0, 40, 40, 2);
        assertReceiver(r, 20, 1);
        assertPort(f, OUT, 0, 20);
        assertPort(f, OTHER, 80, 20);
    }

    @Test
    void shouldHandleLongCapacityAtSource() {
        long stored = (long) Integer.MAX_VALUE + 5000L;
        Fixture f = new Fixture(stored + 1000, stored, List.of(OUT));
        Receiver r = Receiver.accepting();
        assertEquals(80, pushCommitted(f, OUT, r, 80));
        assertState(f, stored - 80, 0, 80, 1);
        assertReceiver(r, 80, 1);
        assertPort(f, OUT, 0, 80);
    }

    @Test
    void shouldResetOutputBudgetAtNextTick() {
        Fixture f = fixture(500);
        Receiver r = Receiver.accepting();
        assertEquals(80, pushCommitted(f, OUT, r, 80));
        f.tick++;
        assertEquals(80, f.budget.getRemaining(f.tick));
        assertEquals(80, f.tracker.getCurrentTransferRate(f.tick));
        assertEquals(80, pushCommitted(f, OUT, r, 80));
        assertState(f, 340, 0, 80, 2);
        assertReceiver(r, 160, 2);
        assertPort(f, OUT, 0, 80);
    }

    @Test
    void shouldRollBackSourceTargetBudgetAndStatisticsWithRoot() {
        Fixture f = fixture(500);
        Receiver r = Receiver.accepting();
        Map<EnergyPortId, Long> credits = f.distribution.getFairnessCreditsSnapshot();
        try (Transaction root = Transaction.openRoot()) {
            assertEquals(60, f.push.push(OUT, r, 60, root));
            assertState(f, 440, 20, 0, 0);
            assertReceiver(r, 60, 0);
            assertPort(f, OUT, 0, 60);
        }
        assertState(f, 500, 80, 0, 0);
        assertReceiver(r, 0, 0);
        assertPort(f, OUT, 0, 0);
        assertEquals(credits, f.distribution.getFairnessCreditsSnapshot());
    }

    @Test
    void shouldRollbackCommittedChildTransferIfRootRollsBack() {
        Fixture f = fixture(500);
        Receiver r = Receiver.accepting();
        try (Transaction root = Transaction.openRoot()) {
            try (Transaction child = Transaction.open(root)) {
                assertEquals(70, f.push.push(OUT, r, 70, child));
                child.commit();
            }
            assertState(f, 430, 10, 0, 0);
            assertReceiver(r, 70, 0);
            assertPort(f, OUT, 0, 70);
        }
        assertState(f, 500, 80, 0, 0);
        assertReceiver(r, 0, 0);
        assertPort(f, OUT, 0, 0);
    }

    @Test
    void shouldCommitNestedTransferOnlyAfterRootCommit() {
        Fixture f = fixture(500);
        Receiver r = Receiver.accepting();
        try (Transaction root = Transaction.openRoot()) {
            try (Transaction child = Transaction.open(root)) {
                assertEquals(40, f.push.push(OUT, r, 40, child));
                child.commit();
            }
            assertState(f, 460, 40, 0, 0);
            assertReceiver(r, 40, 0);
            root.commit();
        }
        assertState(f, 460, 40, 40, 1);
        assertReceiver(r, 40, 1);
        assertPort(f, OUT, 0, 40);
    }

    @Test
    void shouldRollBackBothSidesIfOutputIsDisabledAfterSimulation() {
        Fixture f = fixture(500);
        Receiver r = new Receiver(1000, 0, Integer.MAX_VALUE, () -> f.available.set(false));
        assertEquals(0, pushCommitted(f, OUT, r, 40));
        assertTrue(r.insertCalls > 0, "Получатель должен участвовать в симуляции");
        assertFalse(f.available.get());
        assertState(f, 500, 80, 0, 0);
        assertReceiver(r, 0, 0);
        assertPort(f, OUT, 0, 0);
    }

    @Test
    void shouldRollbackChildPushWithoutLosingParentPull() {
        Fixture f = fixture(500);
        Receiver r = Receiver.accepting();
        try (Transaction root = Transaction.openRoot()) {
            assertEquals(30, f.pullHandler(OUT).extract(30, root));
            try (Transaction child = Transaction.open(root)) {
                assertEquals(40, f.push.push(OUT, r, 40, child));
                assertState(f, 430, 10, 0, 0);
                assertReceiver(r, 40, 0);
            }
            assertState(f, 470, 50, 0, 0);
            assertReceiver(r, 0, 0);
            assertPort(f, OUT, 30, 30);
            assertEquals(50, f.push.push(OUT, r, 80, root));
            root.commit();
        }
        assertState(f, 420, 0, 80, 1);
        assertReceiver(r, 50, 1);
        assertPort(f, OUT, 30, 80);
    }

    @Test
    void shouldRollbackPushAndPullTogetherAndReuseBudget() {
        Fixture f = fixture(500);
        Receiver r = Receiver.accepting();
        Map<EnergyPortId, Long> credits = f.distribution.getFairnessCreditsSnapshot();
        try (Transaction tx = Transaction.openRoot()) {
            assertEquals(50, f.push.push(OUT, r, 50, tx));
            assertEquals(30, f.pullHandler(OUT).extract(80, tx));
            assertState(f, 420, 0, 0, 0);
            assertReceiver(r, 50, 0);
            assertPort(f, OUT, 80, 80);
        }
        assertState(f, 500, 80, 0, 0);
        assertReceiver(r, 0, 0);
        assertPort(f, OUT, 0, 0);
        assertEquals(credits, f.distribution.getFairnessCreditsSnapshot());
        assertEquals(80, pushCommitted(f, OUT, r, 80));
        assertState(f, 420, 0, 80, 1);
        assertReceiver(r, 80, 1);
        assertPort(f, OUT, 0, 80);
    }

    @Test
    void shouldRespectTargetInputBudgetAndRollbackBothRealControllers() {
        Fixture f = fixture(500);
        AtomicInteger targetChanges = new AtomicInteger();
        EnergyComponent targetEnergy = new EnergyComponent(1000, targetChanges::incrementAndGet);
        targetEnergy.restoreStoredEnergy(100);
        EnergyPortPolicy targetPolicy = new FixedEnergyPortPolicy(
            Map.of(IN, EnergyPortMode.INPUT),
            ignored -> true
        );
        EnergyTransferBudget targetBudget = new EnergyTransferBudget(30);
        EnergyInputController input = new EnergyInputController(
            targetEnergy,
            List.of(IN),
            targetPolicy,
            targetBudget,
            () -> f.tick
        );
        EnergyPortDispatcher targetDispatcher = new EnergyPortDispatcher(targetEnergy, input, null);
        EnergyHandler target = new EnergyPortHandler(IN, targetPolicy, targetDispatcher);

        try (Transaction root = Transaction.openRoot()) {
            assertEquals(30, f.push.push(OUT, target, 80, root));
            assertState(f, 470, 50, 0, 0);
            assertEquals(130L, target.getAmountAsLong());
            assertEquals(0, targetBudget.getRemaining(TICK));
            assertEquals(0, targetChanges.get());
        }
        assertState(f, 500, 80, 0, 0);
        assertPort(f, OUT, 0, 0);
        assertEquals(100L, target.getAmountAsLong());
        assertEquals(30, targetBudget.getRemaining(TICK));
        assertEquals(0, targetChanges.get());

        assertEquals(30, pushCommitted(f, OUT, target, 80));
        assertEquals(0, pushCommitted(f, OUT, target, 80));
        assertState(f, 470, 50, 30, 1);
        assertPort(f, OUT, 0, 30);
        assertEquals(130L, target.getAmountAsLong());
        assertEquals(0, targetBudget.getRemaining(TICK));
        assertEquals(1, targetChanges.get());
    }
}
