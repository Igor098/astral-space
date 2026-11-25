package ru.greatdragonx.astralspace.common.energy;

import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.Test;
import ru.greatdragonx.astralspace.common.energy.component.EnergyComponent;
import ru.greatdragonx.astralspace.common.energy.port.*;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static ru.greatdragonx.astralspace.common.energy.EnergyOutputController.ExtractionOrigin.PUSH;

class EnergyPortDispatcherTest {
    private static final EnergyPortId IN = new EnergyPortId("test.input");
    private static final EnergyPortId OUT = new EnergyPortId("test.output");
    private static final EnergyPortId UNKNOWN = new EnergyPortId("test.unknown");
    private static final long TICK = 100L;
    private static final int INPUT_LIMIT = 100;
    private static final int OUTPUT_LIMIT = 80;
    

    private static final class TestPolicy implements EnergyPortPolicy {
        private final Map<EnergyPortId, EnergyPortMode> modes = new HashMap<>();
        private final Set<EnergyPortId> available = new HashSet<>();
        private int insertChecks;
        private int extractChecks;

        private void enable(EnergyPortId port, EnergyPortMode mode) {
            modes.put(port, mode);
            available.add(port);
        }

        @Override
        public EnergyPortMode getMode(@NonNull EnergyPortId portId) {
            return modes.getOrDefault(portId, EnergyPortMode.DISABLED);
        }

        @Override
        public boolean isAvailable(@NonNull EnergyPortId portId) {
            return available.contains(portId);
        }

        @Override
        public boolean canChangeMode(@NonNull EnergyPortId portId, @NonNull EnergyPortMode newMode)
        {
            return false;
        }

        @Override
        public boolean allowsInsert(@NonNull EnergyPortId portId) {
            insertChecks++;
            return EnergyPortPolicy.super.allowsInsert(portId);
        }

        @Override
        public boolean allowsExtract(@NonNull EnergyPortId portId) {
            extractChecks++;
            return EnergyPortPolicy.super.allowsExtract(portId);
        }
    }

    private static final class Fixture {
        private final AtomicInteger changes = new AtomicInteger();
        private final AtomicInteger inputTickReads = new AtomicInteger();
        private final AtomicInteger outputTickReads = new AtomicInteger();
        private final EnergyComponent energy;
        private final TestPolicy policy = new TestPolicy();
        private final EnergyTransferBudget inputBudget = new EnergyTransferBudget(INPUT_LIMIT);
        private final EnergyTransferBudget outputBudget = new EnergyTransferBudget(OUTPUT_LIMIT);
        private final EnergyDistributionManager distribution = new EnergyDistributionManager(
            OUTPUT_LIMIT);
        private final EnergyTransferTracker tracker = new EnergyTransferTracker();
        private final EnergyInputController input;
        private final EnergyOutputController output;
        private final EnergyPortOperations dispatcher;
        private long tick = TICK;

        private Fixture(long capacity, long stored, boolean withInput, boolean withOutput) {
            energy = new EnergyComponent(capacity, changes::incrementAndGet);
            energy.restoreStoredEnergy(stored);
            policy.enable(IN, EnergyPortMode.INPUT);
            policy.enable(OUT, EnergyPortMode.OUTPUT);
            List<EnergyPortId> ports = List.of(IN, OUT);
            input = withInput ? new EnergyInputController(
                energy, ports, policy, inputBudget, () -> {
                inputTickReads.incrementAndGet();
                return tick;
            }
            ) : null;
            output = withOutput ? new EnergyOutputController(
                energy, ports, policy, distribution, outputBudget, tracker, () -> {
                outputTickReads.incrementAndGet();
                return tick;
            }
            ) : null;
            if (output != null) {
                output.refreshAvailablePorts();
            }
            policy.extractChecks = 0;
            dispatcher = new EnergyPortDispatcher(energy, input, output);
        }
    }

    private static Fixture both(long stored) {
        return new Fixture(1_000L, stored, true, true);
    }

    private static int insertCommitted(Fixture f, EnergyPortId port, int amount) {
        try (Transaction tx = Transaction.openRoot()) {
            int result = f.dispatcher.insert(port, amount, tx);
            tx.commit();
            return result;
        }
    }

    private static int extractCommitted(Fixture f, EnergyPortId port, int amount) {
        try (Transaction tx = Transaction.openRoot()) {
            int result = f.dispatcher.extract(port, amount, tx);
            tx.commit();
            return result;
        }
    }

    private static void assertState(
        Fixture f,
        long stored,
        int inputRemaining,
        int outputRemaining,
        int changes
    )
    {
        assertAll(
            () -> assertEquals(stored, f.energy.getStoredEnergy(), "Запас FE"),
            () -> assertEquals(stored, f.dispatcher.getStoredEnergy(), "Чтение через диспетчер"),
            () -> assertEquals(
                inputRemaining,
                f.inputBudget.getRemaining(f.tick),
                "Входящий бюджет"
            ),
            () -> assertEquals(
                outputRemaining,
                f.outputBudget.getRemaining(f.tick),
                "Исходящий бюджет"
            ),
            () -> assertEquals(changes, f.changes.get(), "Уведомления об изменении FE")
        );
    }

    private static void assertNoChecks(Fixture f) {
        assertAll(
            () -> assertEquals(0, f.policy.insertChecks),
            () -> assertEquals(0, f.policy.extractChecks),
            () -> assertEquals(0, f.inputTickReads.get()),
            () -> assertEquals(0, f.outputTickReads.get())
        );
    }

    // Конструктор, чтение, конфигурации (6)

    @Test
    void shouldRejectNullEnergy() {
        assertThrows(NullPointerException.class, () -> new EnergyPortDispatcher(null, null, null));
    }

    @Test
    void shouldConstructAndReadWithoutPolicyOrTimeForEveryConfiguration() {
        for (boolean input : List.of(false, true)) {
            for (boolean output : List.of(false, true)) {
                Fixture f = new Fixture(1_000, 500, input, output);
                assertEquals(1_000L, f.dispatcher.getCapacity());
                assertState(f, 500, 100, 80, 0);
                assertNoChecks(f);
            }
        }
    }

    @Test
    void shouldReadLongCapacityAndEnergyWithoutTruncation() {
        Fixture f = new Fixture(Long.MAX_VALUE, 3_000_000_000L, false, false);
        assertEquals(Long.MAX_VALUE, f.dispatcher.getCapacity());
        assertEquals(3_000_000_000L, f.dispatcher.getStoredEnergy());
        assertNoChecks(f);
    }

    @Test
    void shouldSupportReadOnlyDispatcher() {
        Fixture f = new Fixture(1_000, 500, false, false);
        assertEquals(0, insertCommitted(f, IN, 100));
        assertEquals(0, extractCommitted(f, OUT, 80));
        assertState(f, 500, 100, 80, 0);
        assertNoChecks(f);
    }

    @Test
    void shouldSupportOutputOnlyDispatcherWithoutInput() {
        Fixture f = new Fixture(1_000, 500, false, true);
        assertEquals(0, insertCommitted(f, IN, 100));
        assertEquals(0, f.policy.insertChecks);
        assertEquals(0, f.inputTickReads.get());
        assertEquals(50, extractCommitted(f, OUT, 50));
        assertState(f, 450, 100, 30, 1);
        assertEquals(50, f.tracker.getCurrentTransferRate(TICK + 1));
    }

    @Test
    void shouldSupportInputOnlyDispatcherWithoutOutput() {
        Fixture f = new Fixture(1_000, 500, true, false);
        assertEquals(0, extractCommitted(f, OUT, 80));
        assertEquals(0, f.policy.extractChecks);
        assertEquals(0, f.outputTickReads.get());
        assertEquals(70, insertCommitted(f, IN, 70));
        assertState(f, 570, 30, 80, 1);
        assertEquals(0, f.tracker.getCurrentTransferRate(TICK + 1));
    }

    // Валидация и живая политика (6)

    @Test
    void shouldRejectNullArgumentsInEveryConfiguration() {
        for (boolean input : List.of(false, true)) {
            for (boolean output : List.of(false, true)) {
                Fixture f = new Fixture(1_000, 500, input, output);
                try (Transaction tx = Transaction.openRoot()) {
                    assertAll(
                        () -> assertThrows(
                            NullPointerException.class,
                            () -> f.dispatcher.insert(null, 0, tx)
                        ),
                        () -> assertThrows(
                            NullPointerException.class,
                            () -> f.dispatcher.insert(IN, 0, null)
                        ),
                        () -> assertThrows(
                            NullPointerException.class,
                            () -> f.dispatcher.extract(null, 0, tx)
                        ),
                        () -> assertThrows(
                            NullPointerException.class,
                            () -> f.dispatcher.extract(OUT, 0, null)
                        )
                    );
                    tx.commit();
                }
                assertNoChecks(f);
                assertState(f, 500, 100, 80, 0);
            }
        }
    }

    @Test
    void shouldRejectNegativeAmountsInEveryConfiguration() {
        for (boolean input : List.of(false, true)) {
            for (boolean output : List.of(false, true)) {
                Fixture f = new Fixture(1_000, 500, input, output);
                try (Transaction tx = Transaction.openRoot()) {
                    assertAll(
                        () -> assertThrows(
                            IllegalArgumentException.class,
                            () -> f.dispatcher.insert(IN, -1, tx)
                        ),
                        () -> assertThrows(
                            IllegalArgumentException.class,
                            () -> f.dispatcher.extract(OUT, -1, tx)
                        ),
                        () -> assertThrows(
                            IllegalArgumentException.class,
                            () -> f.dispatcher.insert(UNKNOWN, -1, tx)
                        ),
                        () -> assertThrows(
                            IllegalArgumentException.class,
                            () -> f.dispatcher.extract(UNKNOWN, -1, tx)
                        )
                    );
                    tx.commit();
                }
                assertNoChecks(f);
                assertState(f, 500, 100, 80, 0);
            }
        }
    }

    @Test
    void shouldIgnoreZeroRequestsForConnectedControllers() {
        Fixture f = both(500);
        assertEquals(0, insertCommitted(f, IN, 0));
        assertEquals(0, extractCommitted(f, OUT, 0));
        assertNoChecks(f);
        assertState(f, 500, 100, 80, 0);
        assertEquals(0, f.distribution.getMaxRequestedByPort(OUT, TICK));
    }

    @Test
    void shouldRejectUnknownPortsEvenWithPermissivePolicy() {
        Fixture f = both(500);
        f.policy.enable(UNKNOWN, EnergyPortMode.INPUT);
        assertEquals(0, insertCommitted(f, UNKNOWN, 50));
        f.policy.enable(UNKNOWN, EnergyPortMode.OUTPUT);
        assertEquals(0, extractCommitted(f, UNKNOWN, 50));
        assertNoChecks(f);
        assertState(f, 500, 100, 80, 0);
    }

    @Test
    void shouldRejectWrongDirectionsThroughDispatcher() {
        Fixture f = both(500);
        assertEquals(0, insertCommitted(f, OUT, 50));
        assertEquals(0, extractCommitted(f, IN, 50));
        assertEquals(1, f.policy.insertChecks);
        assertEquals(1, f.policy.extractChecks);
        assertEquals(0, f.inputTickReads.get());
        assertEquals(0, f.outputTickReads.get());
        assertState(f, 500, 100, 80, 0);
    }

    @Test
    void shouldApplyLivePolicyEvenBeforeOutputRefresh() {
        Fixture f = both(500);
        f.policy.modes.put(IN, EnergyPortMode.DISABLED);
        f.policy.available.remove(OUT);
        assertEquals(0, insertCommitted(f, IN, 50));
        assertEquals(0, extractCommitted(f, OUT, 50));
        assertEquals(0, f.inputTickReads.get());
        assertEquals(0, f.outputTickReads.get());
        assertEquals(0, f.distribution.getMaxRequestedByPort(OUT, TICK));
        assertState(f, 500, 100, 80, 0);

        f.policy.enable(IN, EnergyPortMode.INPUT);
        f.policy.enable(OUT, EnergyPortMode.OUTPUT);
        assertEquals(20, insertCommitted(f, IN, 20));
        assertEquals(30, extractCommitted(f, OUT, 30));
        assertState(f, 490, 80, 50, 2);
    }

    // Делегирование, общий буфер и независимые бюджеты (8)

    @Test
    void shouldDelegateInsertionAndPreserveActualAcceptedAmount() {
        Fixture f = both(970);
        assertEquals(30, insertCommitted(f, IN, 150));
        assertState(f, 1_000, 70, 80, 1);
        assertEquals(1, f.inputTickReads.get());
        assertEquals(0, f.outputTickReads.get());
        assertEquals(0, f.tracker.getCurrentTransferRate(TICK + 1));
        assertEquals(0, f.distribution.getMaxRequestedByPort(OUT, TICK));
    }

    @Test
    void shouldDelegateExtractionAsPullAndPreserveOriginalDemand() {
        Fixture f = both(500);
        assertEquals(80, extractCommitted(f, OUT, 150));
        assertState(f, 420, 100, 0, 1);
        assertEquals(150, f.distribution.getMaxRequestedByPort(OUT, TICK));
        assertEquals(80, f.distribution.getExtractedByPort(OUT, TICK));
        assertEquals(80, f.tracker.getCurrentTransferRate(TICK + 1));
        assertEquals(0, f.inputTickReads.get());
        assertEquals(1, f.outputTickReads.get());
    }

    @Test
    void shouldUseFullIndependentBudgetsWhenInputPrecedesOutput() {
        Fixture f = both(500);
        assertEquals(100, insertCommitted(f, IN, 150));
        assertEquals(80, extractCommitted(f, OUT, 150));
        assertEquals(0, insertCommitted(f, IN, 1));
        assertEquals(0, extractCommitted(f, OUT, 1));
        assertState(f, 520, 0, 0, 2);
        assertEquals(80, f.tracker.getCurrentTransferRate(TICK + 1));
    }

    @Test
    void shouldUseFullIndependentBudgetsWhenOutputPrecedesInput() {
        Fixture f = both(500);
        assertEquals(80, extractCommitted(f, OUT, 150));
        assertEquals(100, insertCommitted(f, IN, 150));
        assertState(f, 520, 0, 0, 2);
    }

    @Test
    void shouldExtractEnergyPreviouslyInsertedThroughSameDispatcher() {
        Fixture f = both(0);
        assertEquals(60, insertCommitted(f, IN, 60));
        assertEquals(60, extractCommitted(f, OUT, 80));
        assertState(f, 0, 40, 20, 2);
        assertEquals(60, f.tracker.getCurrentTransferRate(TICK + 1));
    }

    @Test
    void shouldInsertIntoSpaceFreedByOutputInSameTick() {
        Fixture f = both(1_000);
        assertEquals(0, insertCommitted(f, IN, 100));
        assertEquals(80, extractCommitted(f, OUT, 80));
        assertEquals(80, insertCommitted(f, IN, 100));
        assertState(f, 1_000, 20, 0, 2);
    }

    @Test
    void shouldShareOutputBudgetBetweenDirectPushAndDispatchedPull() {
        Fixture f = both(500);
        f.distribution.updatePushAvailability(Map.of(OUT, 80), TICK);
        try (Transaction tx = Transaction.openRoot()) {
            assertEquals(30, f.output.extract(OUT, 30, tx, PUSH));
            assertEquals(0, f.distribution.getMaxRequestedByPort(OUT, TICK));
            assertEquals(50, f.dispatcher.extract(OUT, 80, tx));
            assertEquals(80, f.distribution.getMaxRequestedByPort(OUT, TICK));
            assertEquals(80, f.distribution.getExtractedByPort(OUT, TICK));
            assertEquals(0, f.tracker.getCurrentTransferRate(TICK + 1));
            tx.commit();
        }
        assertState(f, 420, 100, 0, 1);
        assertEquals(80, f.tracker.getCurrentTransferRate(TICK + 1));
    }

    @Test
    void shouldRefreshBothIndependentBudgetsOnNextTick() {
        Fixture f = both(500);
        assertEquals(100, insertCommitted(f, IN, 100));
        assertEquals(80, extractCommitted(f, OUT, 80));
        f.tick++;
        assertState(f, 520, 100, 80, 2);
        assertEquals(100, insertCommitted(f, IN, 100));
        assertEquals(80, extractCommitted(f, OUT, 80));
        assertState(f, 540, 0, 0, 4);
        assertEquals(80, f.tracker.getCurrentTransferRate(f.tick + 1));
    }

    // Общая транзакция, откат и интеграция с handler (7)

    @Test
    void shouldExposeProvisionalStateAndCommitBothDirectionsTogether() {
        Fixture f = both(500);
        try (Transaction tx = Transaction.openRoot()) {
            assertEquals(100, f.dispatcher.insert(IN, 100, tx));
            assertState(f, 600, 0, 80, 0);
            assertEquals(80, f.dispatcher.extract(OUT, 80, tx));
            assertState(f, 520, 0, 0, 0);
            assertEquals(0, f.tracker.getCurrentTransferRate(TICK + 1));
            tx.commit();
        }
        assertState(f, 520, 0, 0, 1);
        assertEquals(80, f.tracker.getCurrentTransferRate(TICK + 1));
    }

    @Test
    void shouldNotNotifyEnergyChangeWhenNetChangeIsZero() {
        Fixture f = both(500);
        try (Transaction tx = Transaction.openRoot()) {
            assertEquals(60, f.dispatcher.insert(IN, 60, tx));
            assertEquals(60, f.dispatcher.extract(OUT, 60, tx));
            tx.commit();
        }
        assertState(f, 500, 40, 20, 0);
        assertEquals(60, f.tracker.getCurrentTransferRate(TICK + 1));
        assertEquals(60, f.distribution.getExtractedByPort(OUT, TICK));
    }

    @Test
    void shouldRollbackBothDirectionsAndOutputAccounting() {
        Fixture f = both(500);
        Map<EnergyPortId, Long> credits = f.distribution.getFairnessCreditsSnapshot();
        try (Transaction tx = Transaction.openRoot()) {
            assertEquals(100, f.dispatcher.insert(IN, 100, tx));
            assertEquals(80, f.dispatcher.extract(OUT, 100, tx));
            assertState(f, 520, 0, 0, 0);
            assertEquals(100, f.distribution.getMaxRequestedByPort(OUT, TICK));
            assertEquals(80, f.distribution.getExtractedByPort(OUT, TICK));
        }
        assertState(f, 500, 100, 80, 0);
        assertEquals(0, f.distribution.getMaxRequestedByPort(OUT, TICK));
        assertEquals(0, f.distribution.getExtractedByPort(OUT, TICK));
        assertEquals(credits, f.distribution.getFairnessCreditsSnapshot());
        assertEquals(0, f.tracker.getCurrentTransferRate(TICK + 1));
    }

    @Test
    void shouldRollbackNestedOperationsWithoutLosingParentChanges() {
        Fixture f = both(500);
        try (Transaction root = Transaction.openRoot()) {
            assertEquals(30, f.dispatcher.insert(IN, 30, root));
            assertEquals(20, f.dispatcher.extract(OUT, 20, root));
            try (Transaction child = Transaction.open(root)) {
                assertEquals(70, f.dispatcher.insert(IN, 70, child));
                assertEquals(60, f.dispatcher.extract(OUT, 60, child));
                assertState(f, 520, 0, 0, 0);
            }
            assertState(f, 510, 70, 60, 0);
            assertEquals(20, f.distribution.getExtractedByPort(OUT, TICK));
            assertEquals(20, f.distribution.getMaxRequestedByPort(OUT, TICK));
            assertEquals(40, f.dispatcher.insert(IN, 40, root));
            assertEquals(30, f.dispatcher.extract(OUT, 30, root));
            root.commit();
        }
        assertState(f, 520, 30, 30, 1);
        assertEquals(50, f.tracker.getCurrentTransferRate(TICK + 1));
    }

    @Test
    void shouldRollbackCommittedChildWhenRootRollsBack() {
        Fixture f = both(500);
        try (Transaction root = Transaction.openRoot()) {
            try (Transaction child = Transaction.open(root)) {
                assertEquals(100, f.dispatcher.insert(IN, 100, child));
                assertEquals(80, f.dispatcher.extract(OUT, 80, child));
                child.commit();
            }
            assertState(f, 520, 0, 0, 0);
            assertEquals(0, f.tracker.getCurrentTransferRate(TICK + 1));
        }
        assertState(f, 500, 100, 80, 0);
        assertEquals(0, f.distribution.getMaxRequestedByPort(OUT, TICK));
        assertEquals(0, f.distribution.getExtractedByPort(OUT, TICK));
        assertEquals(0, f.tracker.getCurrentTransferRate(TICK + 1));
    }

    @Test
    void shouldCommitNestedBothDirectionsOnlyAtRootCommit() {
        Fixture f = both(500);
        try (Transaction root = Transaction.openRoot()) {
            try (Transaction child = Transaction.open(root)) {
                assertEquals(70, f.dispatcher.insert(IN, 70, child));
                assertEquals(30, f.dispatcher.extract(OUT, 30, child));
                child.commit();
            }
            assertState(f, 540, 30, 50, 0);
            assertEquals(0, f.tracker.getCurrentTransferRate(TICK + 1));
            root.commit();
        }
        assertState(f, 540, 30, 50, 1);
        assertEquals(30, f.tracker.getCurrentTransferRate(TICK + 1));
    }

    @Test
    void shouldWorkWithExistingHandlersAndRespectLaterPolicyChanges() {
        Fixture f = both(500);
        EnergyPortHandler in = new EnergyPortHandler(IN, f.policy, f.dispatcher);
        EnergyPortHandler out = new EnergyPortHandler(OUT, f.policy, f.dispatcher);
        try (Transaction tx = Transaction.openRoot()) {
            assertEquals(70, in.insert(70, tx));
            assertEquals(570L, out.getAmountAsLong());
            assertEquals(1_000L, in.getCapacityAsLong());
            assertEquals(40, out.extract(40, tx));
            assertEquals(0, in.extract(20, tx));
            assertEquals(0, out.insert(20, tx));
            tx.commit();
        }
        assertState(f, 530, 30, 40, 1);
        f.policy.available.remove(IN);
        f.policy.modes.put(OUT, EnergyPortMode.DISABLED);
        try (Transaction tx = Transaction.openRoot()) {
            assertEquals(0, in.insert(30, tx));
            assertEquals(0, out.extract(40, tx));
            tx.commit();
        }
        assertState(f, 530, 30, 40, 1);
        assertEquals(40, f.tracker.getCurrentTransferRate(TICK + 1));
        assertEquals(40, f.distribution.getMaxRequestedByPort(OUT, TICK));
    }
}
