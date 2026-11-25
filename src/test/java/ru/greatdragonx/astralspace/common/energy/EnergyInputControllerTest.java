package ru.greatdragonx.astralspace.common.energy;

import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.Test;
import ru.greatdragonx.astralspace.common.energy.component.EnergyComponent;
import ru.greatdragonx.astralspace.common.energy.port.EnergyPortId;
import ru.greatdragonx.astralspace.common.energy.port.EnergyPortMode;
import ru.greatdragonx.astralspace.common.energy.port.EnergyPortPolicy;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.LongSupplier;

import static org.junit.jupiter.api.Assertions.*;

class EnergyInputControllerTest {
    private static final EnergyPortId A = new EnergyPortId("test.input.a");
    private static final EnergyPortId B = new EnergyPortId("test.input.b");
    private static final EnergyPortId UNKNOWN = new EnergyPortId("test.unknown");
    private static final long TICK = 100L;
    private static final int LIMIT = 100;

    private static final class TestPolicy implements EnergyPortPolicy {
        private final Map<EnergyPortId, EnergyPortMode> modes = new HashMap<>();
        private final Set<EnergyPortId> available = new HashSet<>();
        private final List<EnergyPortId> checks = new ArrayList<>();

        private void enable(EnergyPortId port) {
            modes.put(port, EnergyPortMode.INPUT);
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
        public boolean canChangeMode(
            @NonNull EnergyPortId portId,
            @NonNull EnergyPortMode newMode
        )
        {
            return false;
        }

        @Override
        public boolean allowsInsert(@NonNull EnergyPortId portId) {
            checks.add(portId);
            return EnergyPortPolicy.super.allowsInsert(portId);
        }
    }

    private static final class Fixture {
        private final AtomicInteger energyChanges = new AtomicInteger();
        private final AtomicInteger tickReads = new AtomicInteger();
        private final EnergyComponent energy;
        private final TestPolicy policy = new TestPolicy();
        private final EnergyTransferBudget budget;
        private long tick = TICK;
        private final LongSupplier clock = () -> {
            tickReads.incrementAndGet();
            return tick;
        };
        private final EnergyInputController controller;

        private Fixture(long capacity, long stored, int limit, List<EnergyPortId> ports) {
            energy = new EnergyComponent(capacity, energyChanges::incrementAndGet);
            energy.restoreStoredEnergy(stored);
            budget = new EnergyTransferBudget(limit);
            ports.forEach(policy::enable);
            controller = new EnergyInputController(energy, ports, policy, budget, clock);
        }
    }

    private static Fixture fixture(long stored, EnergyPortId... ports) {
        return new Fixture(1_000L, stored, LIMIT, List.of(ports));
    }

    private static int insertCommitted(Fixture f, EnergyPortId port, int amount) {
        try (Transaction tx = Transaction.openRoot()) {
            int inserted = f.controller.insert(port, amount, tx);
            tx.commit();
            return inserted;
        }
    }

    private static void assertState(Fixture f, long stored, int remaining, int changes) {
        assertAll(
            () -> assertEquals(stored, f.energy.getStoredEnergy(), "Запас энергии"),
            () -> assertEquals(
                remaining,
                f.budget.getRemaining(f.tick),
                "Остаток входящего бюджета"
            ),
            () -> assertEquals(changes, f.energyChanges.get(), "Уведомления об изменении энергии")
        );
    }

    // Конструктор (5)

    @Test
    void shouldRejectNullDependencies() {
        Fixture f = fixture(500, A);
        assertAll(
            () -> assertThrows(
                NullPointerException.class,
                () -> new EnergyInputController(null, List.of(A), f.policy, f.budget, f.clock)
            ),
            () -> assertThrows(
                NullPointerException.class,
                () -> new EnergyInputController(f.energy, null, f.policy, f.budget, f.clock)
            ),
            () -> assertThrows(
                NullPointerException.class,
                () -> new EnergyInputController(f.energy, List.of(A), null, f.budget, f.clock)
            ),
            () -> assertThrows(
                NullPointerException.class,
                () -> new EnergyInputController(f.energy, List.of(A), f.policy, null, f.clock)
            ),
            () -> assertThrows(
                NullPointerException.class,
                () -> new EnergyInputController(f.energy, List.of(A), f.policy, f.budget, null)
            )
        );
    }

    @Test
    void shouldRejectNullPortElement() {
        Fixture f = fixture(500, A);
        assertThrows(
            NullPointerException.class,
            () -> new EnergyInputController(
                f.energy,
                Arrays.asList(A, null),
                f.policy,
                f.budget,
                f.clock
            )
        );
    }

    @Test
    void shouldRejectDuplicatePortIdsByValue() {
        Fixture f = fixture(500, A);
        assertThrows(
            IllegalArgumentException.class,
            () -> new EnergyInputController(
                f.energy,
                List.of(A, new EnergyPortId(A.value())),
                f.policy,
                f.budget,
                f.clock
            )
        );
    }

    @Test
    void shouldCopyOriginalPortListDefensively() {
        List<EnergyPortId> source = new ArrayList<>(List.of(A));
        Fixture f = new Fixture(1_000, 500, LIMIT, source);
        source.clear();
        source.add(B);
        f.policy.enable(B);

        assertEquals(0, insertCommitted(f, B, 40));
        assertTrue(f.policy.checks.isEmpty());
        assertEquals(0, f.tickReads.get());
        assertEquals(40, insertCommitted(f, new EnergyPortId(A.value()), 40));
        assertState(f, 540, 60, 1);
    }

    @Test
    void shouldConstructWithoutReadingPolicyOrTimeOrChangingState() {
        Fixture f = fixture(500, A);
        assertTrue(f.policy.checks.isEmpty());
        assertEquals(0, f.tickReads.get());
        assertState(f, 500, 100, 0);
    }

    // Валидация и актуальная политика (8)

    @Test
    void shouldRejectNullArgumentsEvenForZeroRequest() {
        Fixture f = fixture(500, A);
        try (Transaction tx = Transaction.openRoot()) {
            assertAll(
                () -> assertThrows(
                    NullPointerException.class,
                    () -> f.controller.insert(null, 0, tx)
                ),
                () -> assertThrows(
                    NullPointerException.class,
                    () -> f.controller.insert(A, 0, null)
                ),
                () -> assertThrows(
                    NullPointerException.class,
                    () -> f.controller.insert(UNKNOWN, 10, null)
                )
            );
            tx.commit();
        }
        assertTrue(f.policy.checks.isEmpty());
        assertEquals(0, f.tickReads.get());
        assertState(f, 500, 100, 0);
    }

    @Test
    void shouldRejectNegativeAmountBeforeCheckingPortOrPolicy() {
        Fixture f = fixture(500, A, B);
        f.policy.modes.put(B, EnergyPortMode.DISABLED);
        try (Transaction tx = Transaction.openRoot()) {
            assertAll(
                () -> assertThrows(
                    IllegalArgumentException.class,
                    () -> f.controller.insert(A, -1, tx)
                ),
                () -> assertThrows(
                    IllegalArgumentException.class,
                    () -> f.controller.insert(B, -1, tx)
                ),
                () -> assertThrows(
                    IllegalArgumentException.class,
                    () -> f.controller.insert(UNKNOWN, -1, tx)
                )
            );
            tx.commit();
        }
        assertTrue(f.policy.checks.isEmpty());
        assertEquals(0, f.tickReads.get());
        assertState(f, 500, 100, 0);
    }

    @Test
    void shouldIgnoreZeroRequestsWithoutReadingPolicyOrTime() {
        Fixture f = fixture(500, A);
        assertEquals(0, insertCommitted(f, A, 0));
        assertEquals(0, insertCommitted(f, UNKNOWN, 0));
        assertTrue(f.policy.checks.isEmpty());
        assertEquals(0, f.tickReads.get());
        assertState(f, 500, 100, 0);
    }

    @Test
    void shouldRejectUnknownPortEvenWhenPolicyAllowsIt() {
        Fixture f = fixture(500, A);
        f.policy.enable(UNKNOWN);
        assertEquals(0, insertCommitted(f, UNKNOWN, 40));
        assertTrue(f.policy.checks.isEmpty());
        assertEquals(0, f.tickReads.get());
        assertState(f, 500, 100, 0);
    }

    @Test
    void shouldAllowEmptyPortListButRejectAllInsertions() {
        Fixture f = fixture(500);
        f.policy.enable(A);
        assertEquals(0, insertCommitted(f, A, 40));
        assertTrue(f.policy.checks.isEmpty());
        assertEquals(0, f.tickReads.get());
        assertState(f, 500, 100, 0);
    }

    @Test
    void shouldRejectOutputMode() {
        Fixture f = fixture(500, A);
        f.policy.modes.put(A, EnergyPortMode.OUTPUT);
        assertEquals(0, insertCommitted(f, A, 40));
        assertEquals(List.of(A), f.policy.checks);
        assertEquals(0, f.tickReads.get());
        assertState(f, 500, 100, 0);
    }

    @Test
    void shouldObserveModeChangesImmediatelyWithoutResettingBudget() {
        Fixture f = fixture(500, A);
        assertEquals(30, insertCommitted(f, A, 30));
        f.policy.modes.put(A, EnergyPortMode.DISABLED);
        assertEquals(0, insertCommitted(f, A, 70));
        assertEquals(1, f.tickReads.get());
        assertState(f, 530, 70, 1);
        f.policy.modes.put(A, EnergyPortMode.INPUT);
        assertEquals(70, insertCommitted(f, A, 100));
        assertEquals(2, f.tickReads.get());
        assertState(f, 600, 0, 2);
    }

    @Test
    void shouldRejectUnavailableInputBeforeReadingUnsafeClock() {
        Fixture f = fixture(500, A);
        f.policy.available.remove(A);
        EnergyInputController controller = new EnergyInputController(
            f.energy,
            List.of(A),
            f.policy,
            f.budget,
            () -> {
                throw new AssertionError("Серверный мир не готов");
            }
        );
        try (Transaction tx = Transaction.openRoot()) {
            assertEquals(0, controller.insert(A, 40, tx));
            tx.commit();
        }
        assertEquals(List.of(A), f.policy.checks);
        assertState(f, 500, 100, 0);
        f.policy.available.add(A);
        assertEquals(40, insertCommitted(f, A, 40));
        assertEquals(1, f.tickReads.get());
        assertState(f, 540, 60, 1);
    }

    // Вставка, вместимость и бюджет (10)

    @Test
    void shouldInsertRequestedEnergyAndChargeBudgetAfterCommit() {
        Fixture f = fixture(500, A);
        assertEquals(40, insertCommitted(f, A, 40));
        assertEquals(List.of(A), f.policy.checks);
        assertEquals(1, f.tickReads.get());
        assertState(f, 540, 60, 1);
    }

    @Test
    void shouldLimitInsertionToBudgetEvenWhenStorageHasMoreFreeSpace() {
        Fixture f = fixture(500, A);
        assertEquals(100, insertCommitted(f, A, 150));
        assertState(f, 600, 0, 1);
    }

    @Test
    void shouldLimitInsertionToRemainingCapacityAndChargeActualAmount() {
        Fixture f = fixture(970, A);
        assertEquals(30, insertCommitted(f, A, 150));
        assertState(f, 1_000, 70, 1);
    }

    @Test
    void shouldNotChargeBudgetOrNotifyWhenBufferIsFull() {
        Fixture f = fixture(1_000, A);
        assertEquals(0, insertCommitted(f, A, 80));
        assertEquals(1, f.tickReads.get());
        assertState(f, 1_000, 100, 0);
    }

    @Test
    void shouldShareBudgetAcrossPortsAndCommittedTransactions() {
        Fixture f = fixture(500, A, B);
        assertEquals(60, insertCommitted(f, A, 60));
        assertEquals(40, insertCommitted(f, B, 60));
        assertEquals(List.of(A, B), f.policy.checks);
        assertState(f, 600, 0, 2);
    }

    @Test
    void shouldReturnZeroWhenBudgetIsExhausted() {
        Fixture f = fixture(500, A, B);
        assertEquals(100, insertCommitted(f, A, 100));
        assertEquals(0, insertCommitted(f, B, 40));
        assertEquals(2, f.tickReads.get());
        assertState(f, 600, 0, 1);
    }

    @Test
    void shouldRestoreFullBudgetOnNextTick() {
        Fixture f = fixture(500, A);
        assertEquals(100, insertCommitted(f, A, 100));
        f.tick++;
        assertState(f, 600, 100, 1);
        assertEquals(60, insertCommitted(f, A, 60));
        assertEquals(2, f.tickReads.get());
        assertState(f, 660, 40, 2);
    }

    @Test
    void shouldReuseUnspentBudgetWhenSpaceBecomesAvailableInSameTick() {
        Fixture f = fixture(970, A);
        assertEquals(30, insertCommitted(f, A, 100));
        try (Transaction tx = Transaction.openRoot()) {
            assertEquals(80, f.energy.extractEnergy(80, tx));
            tx.commit();
        }
        assertState(f, 920, 70, 2);
        assertEquals(70, insertCommitted(f, A, 100));
        assertState(f, 990, 0, 3);
    }

    @Test
    void shouldInsertIntoStorageLargerThanIntMaximum() {
        Fixture f = new Fixture(5_000_000_000L, 3_000_000_000L, LIMIT, List.of(A));
        assertEquals(100, insertCommitted(f, A, 150));
        assertState(f, 3_000_000_100L, 0, 1);
    }

    @Test
    void shouldSupportMaximumIntBudgetAndRequest() {
        Fixture f = new Fixture(Long.MAX_VALUE, 3_000_000_000L, Integer.MAX_VALUE, List.of(A, B));
        assertEquals(Integer.MAX_VALUE - 1, insertCommitted(f, A, Integer.MAX_VALUE - 1));
        assertState(f, 5_147_483_646L, 1, 1);
        assertEquals(1, insertCommitted(f, B, Integer.MAX_VALUE));
        assertState(f, 5_147_483_647L, 0, 2);
    }

    // Транзакционные гарантии (8)

    @Test
    void shouldExposePendingChangesButNotifyOnlyAfterRootCommit() {
        Fixture f = fixture(500, A);
        try (Transaction tx = Transaction.openRoot()) {
            assertEquals(40, f.controller.insert(A, 40, tx));
            assertState(f, 540, 60, 0);
            tx.commit();
        }
        assertState(f, 540, 60, 1);
    }

    @Test
    void shouldShareBudgetInsideOneTransactionAndNotifyOnlyOnce() {
        Fixture f = fixture(500, A, B);
        try (Transaction tx = Transaction.openRoot()) {
            assertEquals(60, f.controller.insert(A, 60, tx));
            assertEquals(40, f.controller.insert(B, 60, tx));
            assertEquals(0, f.controller.insert(A, 10, tx));
            assertState(f, 600, 0, 0);
            tx.commit();
        }
        assertEquals(3, f.tickReads.get());
        assertState(f, 600, 0, 1);
    }

    @Test
    void shouldRollbackEnergyAndBudgetTogether() {
        Fixture f = fixture(500, A, B);
        try (Transaction tx = Transaction.openRoot()) {
            assertEquals(60, f.controller.insert(A, 60, tx));
            assertEquals(40, f.controller.insert(B, 60, tx));
            assertState(f, 600, 0, 0);
        }
        assertState(f, 500, 100, 0);
        assertEquals(100, insertCommitted(f, B, 100));
        assertState(f, 600, 0, 1);
    }

    @Test
    void shouldPreservePreviousCommittedInsertionAfterLaterRollback() {
        Fixture f = fixture(500, A);
        assertEquals(30, insertCommitted(f, A, 30));
        try (Transaction tx = Transaction.openRoot()) {
            assertEquals(70, f.controller.insert(A, 100, tx));
            assertState(f, 600, 0, 1);
        }
        assertState(f, 530, 70, 1);
    }

    @Test
    void shouldRollbackNestedInsertionWithoutUndoingParentChanges() {
        Fixture f = fixture(500, A, B);
        try (Transaction root = Transaction.openRoot()) {
            assertEquals(30, f.controller.insert(A, 30, root));
            try (Transaction child = Transaction.open(root)) {
                assertEquals(50, f.controller.insert(B, 50, child));
                assertState(f, 580, 20, 0);
            }
            assertState(f, 530, 70, 0);
            assertEquals(70, f.controller.insert(B, 100, root));
            root.commit();
        }
        assertState(f, 600, 0, 1);
    }

    @Test
    void shouldRollbackCommittedChildWhenRootRollsBack() {
        Fixture f = fixture(500, A, B);
        try (Transaction root = Transaction.openRoot()) {
            assertEquals(20, f.controller.insert(A, 20, root));
            try (Transaction child = Transaction.open(root)) {
                assertEquals(60, f.controller.insert(B, 60, child));
                child.commit();
            }
            assertState(f, 580, 20, 0);
        }
        assertState(f, 500, 100, 0);
    }

    @Test
    void shouldPreserveCommittedChildOnlyAfterRootCommit() {
        Fixture f = fixture(500, A, B);
        try (Transaction root = Transaction.openRoot()) {
            try (Transaction child = Transaction.open(root)) {
                assertEquals(60, f.controller.insert(B, 60, child));
                child.commit();
            }
            assertState(f, 560, 40, 0);
            assertEquals(40, f.controller.insert(A, 80, root));
            assertState(f, 600, 0, 0);
            root.commit();
        }
        assertState(f, 600, 0, 1);
    }

    @Test
    void shouldRestorePreviousTickBudgetHistoryAfterRollbackOnNewTick() {
        Fixture f = fixture(500, A);
        assertEquals(30, insertCommitted(f, A, 30));
        f.tick++;
        try (Transaction tx = Transaction.openRoot()) {
            assertEquals(80, f.controller.insert(A, 80, tx));
            assertState(f, 610, 20, 1);
        }
        assertState(f, 530, 100, 1);
        assertEquals(70, f.budget.getRemaining(TICK));
        assertEquals(100, insertCommitted(f, A, 100));
        assertState(f, 630, 0, 2);
    }
}
