package ru.greatdragonx.astralspace.common.energy;

import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.Test;
import ru.greatdragonx.astralspace.common.energy.component.EnergyComponent;
import ru.greatdragonx.astralspace.common.energy.port.EnergyPortId;
import ru.greatdragonx.astralspace.common.energy.port.EnergyPortMode;
import ru.greatdragonx.astralspace.common.energy.port.EnergyPortPolicy;

import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.LongSupplier;

import static org.junit.jupiter.api.Assertions.*;
import static ru.greatdragonx.astralspace.common.energy.EnergyOutputController.ExtractionOrigin.PULL;
import static ru.greatdragonx.astralspace.common.energy.EnergyOutputController.ExtractionOrigin.PUSH;

class EnergyOutputControllerTest {
    private static final EnergyPortId A = new EnergyPortId("test.output.a");
    private static final EnergyPortId B = new EnergyPortId("test.output.b");
    private static final EnergyPortId C = new EnergyPortId("test.output.c");
    private static final EnergyPortId UNKNOWN = new EnergyPortId("test.unknown");
    private static final long TICK = 100L;
    private static final int LIMIT = 80;

    private static final class TestPolicy implements EnergyPortPolicy {
        private final Map<EnergyPortId, EnergyPortMode> modes = new HashMap<>();
        private final Set<EnergyPortId> available = new HashSet<>();
        private final List<EnergyPortId> checks = new ArrayList<>();

        private void enable(EnergyPortId port) {
            modes.put(port, EnergyPortMode.OUTPUT);
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
        public boolean allowsExtract(@NonNull EnergyPortId portId) {
            checks.add(portId);
            return EnergyPortPolicy.super.allowsExtract(portId);
        }
    }

    private static final class Fixture {
        private final AtomicInteger energyChanges = new AtomicInteger();
        private final AtomicInteger tickReads = new AtomicInteger();
        private final EnergyComponent energy;
        private final TestPolicy policy = new TestPolicy();
        private final EnergyDistributionManager distribution = new EnergyDistributionManager(LIMIT);
        private final EnergyTransferBudget budget = new EnergyTransferBudget(LIMIT);
        private final EnergyTransferTracker tracker = new EnergyTransferTracker();
        private long tick = TICK;
        private final LongSupplier clock = () -> {
            tickReads.incrementAndGet();
            return tick;
        };
        private final EnergyOutputController controller;

        private Fixture(long stored, List<EnergyPortId> ports, boolean refresh) {
            energy = new EnergyComponent(Math.max(1000L, stored), energyChanges::incrementAndGet);
            energy.restoreStoredEnergy(stored);
            ports.forEach(policy::enable);
            controller = new EnergyOutputController(
                energy,
                ports,
                policy,
                distribution,
                budget,
                tracker,
                clock
            );
            if (refresh) {
                controller.refreshAvailablePorts();
                policy.checks.clear();
            }
        }
    }

    private static Fixture fixture(long stored, EnergyPortId... ports) {
        return new Fixture(stored, List.of(ports), true);
    }

    private static int extractCommitted(Fixture f, EnergyPortId port, int amount) {
        try (Transaction tx = Transaction.openRoot()) {
            int result = f.controller.extract(port, amount, tx);
            tx.commit();
            return result;
        }
    }

    private static int extractCommitted(
        Fixture f,
        EnergyPortId port,
        int amount,
        EnergyOutputController.ExtractionOrigin origin
    )
    {
        try (Transaction tx = Transaction.openRoot()) {
            int result = f.controller.extract(port, amount, tx, origin);
            tx.commit();
            return result;
        }
    }

    private static void seedDemand(Fixture f, long tick, EnergyPortId... ports) {
        try (Transaction tx = Transaction.openRoot()) {
            for (EnergyPortId port : ports) {
                f.distribution.recordRequest(port, LIMIT, tick, tx);
            }
            tx.commit();
        }
    }

    private static List<?> availablePorts(Fixture f) throws ReflectiveOperationException {
        Field field = EnergyDistributionManager.class.getDeclaredField("availablePorts");
        field.setAccessible(true);
        return (List<?>) field.get(f.distribution);
    }

    private static void assertState(Fixture f, long stored, int remaining, int rate, int changes) {
        assertAll(
            () -> assertEquals(stored, f.energy.getStoredEnergy(), "stored energy"),
            () -> assertEquals(remaining, f.budget.getRemaining(f.tick), "output budget"),
            () -> assertEquals(
                rate,
                f.tracker.getCurrentTransferRate(f.tick + 1),
                "confirmed transfer"
            ),
            () -> assertEquals(changes, f.energyChanges.get(), "energy callbacks")
        );
    }

    private static void assertPort(Fixture f, EnergyPortId port, int demand, int extracted) {
        assertAll(
            () -> assertEquals(
                demand,
                f.distribution.getMaxRequestedByPort(port, f.tick),
                "Pull demand"
            ),
            () -> assertEquals(
                extracted,
                f.distribution.getExtractedByPort(port, f.tick),
                "extracted"
            )
        );
    }

    @Test
    void shouldRejectNullDependencies() {
        Fixture f = fixture(500, A);
        assertAll(
            () -> assertThrows(
                NullPointerException.class,
                () -> new EnergyOutputController(
                    null,
                    List.of(A),
                    f.policy,
                    f.distribution,
                    f.budget,
                    f.tracker,
                    f.clock
                )
            ), () -> assertThrows(
                NullPointerException.class,
                () -> new EnergyOutputController(
                    f.energy,
                    null,
                    f.policy,
                    f.distribution,
                    f.budget,
                    f.tracker,
                    f.clock
                )
            ), () -> assertThrows(
                NullPointerException.class,
                () -> new EnergyOutputController(
                    f.energy,
                    List.of(A),
                    null,
                    f.distribution,
                    f.budget,
                    f.tracker,
                    f.clock
                )
            ), () -> assertThrows(
                NullPointerException.class,
                () -> new EnergyOutputController(
                    f.energy,
                    List.of(A),
                    f.policy,
                    null,
                    f.budget,
                    f.tracker,
                    f.clock
                )
            ), () -> assertThrows(
                NullPointerException.class,
                () -> new EnergyOutputController(
                    f.energy,
                    List.of(A),
                    f.policy,
                    f.distribution,
                    null,
                    f.tracker,
                    f.clock
                )
            ), () -> assertThrows(
                NullPointerException.class,
                () -> new EnergyOutputController(
                    f.energy,
                    List.of(A),
                    f.policy,
                    f.distribution,
                    f.budget,
                    null,
                    f.clock
                )
            ), () -> assertThrows(
                NullPointerException.class,
                () -> new EnergyOutputController(
                    f.energy,
                    List.of(A),
                    f.policy,
                    f.distribution,
                    f.budget,
                    f.tracker,
                    null
                )
            )
        );
    }

    @Test
    void shouldRejectNullPortElement() {
        Fixture f = fixture(500, A);
        assertThrows(
            NullPointerException.class, () -> new EnergyOutputController(
                f.energy,
                Arrays.asList(A, null),
                f.policy,
                f.distribution,
                f.budget,
                f.tracker,
                f.clock
            )
        );
    }

    @Test
    void shouldRejectDuplicatePortIdsByValue() {
        Fixture f = fixture(500, A);
        assertThrows(
            IllegalArgumentException.class, () -> new EnergyOutputController(
                f.energy,
                List.of(A, new EnergyPortId(A.value())),
                f.policy,
                f.distribution,
                f.budget,
                f.tracker,
                f.clock
            )
        );
    }

    @Test
    void shouldCopyOriginalPortListDefensively() throws ReflectiveOperationException {
        List<EnergyPortId> source = new ArrayList<>(List.of(B, A));
        Fixture f = new Fixture(500, source, false);
        source.clear();
        source.add(C);
        f.policy.enable(C);
        f.controller.refreshAvailablePorts();
        assertEquals(List.of(B, A), availablePorts(f));
        // Even with a permissive policy and a stale manager, C is not a known ID.
        f.distribution.updateAvailablePorts(List.of(A, B, C));
        assertEquals(0, extractCommitted(f, C, 10));
        assertEquals(0, f.tickReads.get());
        assertEquals(10, extractCommitted(f, A, 10));
    }

    @Test
    void shouldConstructWithoutReadingPolicyTimeOrChangingDistribution()
        throws ReflectiveOperationException
    {
        Fixture f = new Fixture(500, List.of(A), false);
        assertEquals(List.of(), availablePorts(f));
        assertTrue(f.policy.checks.isEmpty());
        assertEquals(0, f.tickReads.get());
        assertState(f, 500, 80, 0, 0);
    }

    @Test
    void shouldRefreshOnlyAvailableOutputPortsInOriginalOrder() throws ReflectiveOperationException
    {
        Fixture f = new Fixture(500, List.of(C, B, A, UNKNOWN), false);
        f.policy.modes.put(B, EnergyPortMode.INPUT);
        f.policy.available.remove(UNKNOWN);
        f.controller.refreshAvailablePorts();
        assertEquals(List.of(C, B, A, UNKNOWN), f.policy.checks);
        assertEquals(List.of(C, A), availablePorts(f));
        assertEquals(0, f.tickReads.get());
        assertState(f, 500, 80, 0, 0);
    }

    @Test
    void shouldRefreshToEmptyListWhenEveryPortIsDenied() throws ReflectiveOperationException {
        Fixture f = fixture(500, A, B, C);
        f.policy.modes.put(A, EnergyPortMode.DISABLED);
        f.policy.modes.put(B, EnergyPortMode.INPUT);
        f.policy.available.remove(C);
        f.controller.refreshAvailablePorts();
        assertEquals(List.of(), availablePorts(f));
        assertEquals(0, extractCommitted(f, A, 40));
        assertEquals(0, f.tickReads.get());
        assertState(f, 500, 80, 0, 0);
    }

    @Test
    void shouldAllowEmptyKnownPortList() throws ReflectiveOperationException {
        Fixture f = fixture(500);
        assertEquals(List.of(), availablePorts(f));
        f.policy.enable(A);
        assertEquals(0, extractCommitted(f, A, 40));
        assertEquals(0, f.tickReads.get());
        assertState(f, 500, 80, 0, 0);
    }

    @Test
    void shouldPreserveBudgetAndRemainingPortHistoryOnRefresh() throws ReflectiveOperationException
    {
        Fixture f = fixture(500, A, B);
        assertEquals(20, extractCommitted(f, A, 20));
        assertEquals(20, extractCommitted(f, B, 20));
        int reads = f.tickReads.get();
        f.policy.available.remove(A);
        f.controller.refreshAvailablePorts();
        assertEquals(List.of(B), availablePorts(f));
        assertPort(f, A, 0, 0);
        assertPort(f, B, 20, 20);
        f.policy.available.add(A);
        f.controller.refreshAvailablePorts();
        assertEquals(List.of(A, B), availablePorts(f));
        assertPort(f, A, 0, 0);
        assertPort(f, B, 20, 20);
        assertEquals(reads, f.tickReads.get());
        assertState(f, 460, 40, 40, 2);
    }

    @Test
    void shouldKeepHistoryOnRepeatedUnchangedRefresh() {
        Fixture f = fixture(500, A);
        assertEquals(30, extractCommitted(f, A, 30));
        Map<EnergyPortId, Long> credits = f.distribution.getFairnessCreditsSnapshot();
        f.controller.refreshAvailablePorts();
        f.controller.refreshAvailablePorts();
        assertPort(f, A, 30, 30);
        assertEquals(credits, f.distribution.getFairnessCreditsSnapshot());
        assertEquals(1, f.tickReads.get());
        assertState(f, 470, 50, 30, 1);
    }

    @Test
    void shouldRejectNullArgumentsInBothOverloadsEvenForZeroRequest() {
        Fixture f = fixture(500, A);
        try (Transaction tx = Transaction.openRoot()) {
            assertAll(
                () -> assertThrows(
                    NullPointerException.class,
                    () -> f.controller.extract(null, 0, tx)
                ),
                () -> assertThrows(
                    NullPointerException.class,
                    () -> f.controller.extract(A, 0, null)
                ),
                () -> assertThrows(
                    NullPointerException.class,
                    () -> f.controller.extract(null, 0, tx, PUSH)
                ),
                () -> assertThrows(
                    NullPointerException.class,
                    () -> f.controller.extract(A, 0, null, PUSH)
                ),
                () -> assertThrows(
                    NullPointerException.class,
                    () -> f.controller.extract(A, 0, tx, null)
                )
            );
            tx.commit();
        }
        assertTrue(f.policy.checks.isEmpty());
        assertEquals(0, f.tickReads.get());
        assertState(f, 500, 80, 0, 0);
    }

    @Test
    void shouldRejectNegativeAmountBeforeCheckingUnknownOrDisabledPort() {
        Fixture f = fixture(500, A);
        f.policy.modes.put(A, EnergyPortMode.DISABLED);
        try (Transaction tx = Transaction.openRoot()) {
            assertThrows(
                IllegalArgumentException.class,
                () -> f.controller.extract(UNKNOWN, -1, tx)
            );
            assertThrows(
                IllegalArgumentException.class,
                () -> f.controller.extract(A, -1, tx, PUSH)
            );
            tx.commit();
        }
        assertTrue(f.policy.checks.isEmpty());
        assertEquals(0, f.tickReads.get());
        assertState(f, 500, 80, 0, 0);
    }

    @Test
    void shouldIgnoreZeroRequestsBeforeReadingPolicyOrTick() {
        Fixture f = fixture(500, A);
        assertEquals(0, extractCommitted(f, A, 0));
        assertEquals(0, extractCommitted(f, A, 0, PUSH));
        assertTrue(f.policy.checks.isEmpty());
        assertEquals(0, f.tickReads.get());
        assertPort(f, A, 0, 0);
        assertState(f, 500, 80, 0, 0);
    }

    @Test
    void shouldRejectUnknownPortEvenWhenPolicyAndDistributionAllowIt() {
        Fixture f = fixture(500, A);
        f.policy.enable(UNKNOWN);
        f.distribution.updateAvailablePorts(List.of(A, UNKNOWN));
        assertEquals(0, extractCommitted(f, UNKNOWN, 40));
        assertEquals(0, extractCommitted(f, UNKNOWN, 40, PUSH));
        assertTrue(f.policy.checks.isEmpty());
        assertEquals(0, f.tickReads.get());
        assertPort(f, UNKNOWN, 0, 0);
        assertState(f, 500, 80, 0, 0);
    }

    @Test
    void shouldDenyDisabledPortImmediatelyBeforeRefresh() {
        Fixture f = fixture(500, A);
        assertEquals(20, extractCommitted(f, A, 20));
        Map<EnergyPortId, Long> credits = f.distribution.getFairnessCreditsSnapshot();
        f.policy.modes.put(A, EnergyPortMode.DISABLED);
        assertEquals(0, extractCommitted(f, A, 60));
        assertEquals(0, extractCommitted(f, A, 60, PUSH));
        assertEquals(1, f.tickReads.get());
        assertPort(f, A, 20, 20);
        assertEquals(credits, f.distribution.getFairnessCreditsSnapshot());
        assertState(f, 480, 60, 20, 1);
    }

    @Test
    void shouldDenyInputModeEvenBeforeRefresh() {
        Fixture f = fixture(500, A);
        f.policy.modes.put(A, EnergyPortMode.INPUT);
        assertEquals(0, extractCommitted(f, A, 40));
        assertEquals(0, extractCommitted(f, A, 40, PUSH));
        assertEquals(0, f.tickReads.get());
        assertPort(f, A, 0, 0);
        assertState(f, 500, 80, 0, 0);
    }

    @Test
    void shouldDenyPhysicallyUnavailableOutputBeforeReadingUnsafeClock() {
        Fixture f = fixture(500, A);
        f.policy.available.remove(A);
        EnergyOutputController controller = new EnergyOutputController(
            f.energy, List.of(A), f.policy, f.distribution, f.budget, f.tracker, () -> {
            throw new AssertionError("World not ready");
        }
        );
        try (Transaction tx = Transaction.openRoot()) {
            assertEquals(0, controller.extract(A, 40, tx));
            assertEquals(0, controller.extract(A, 40, tx, PUSH));
            tx.commit();
        }
        assertPort(f, A, 0, 0);
        assertState(f, 500, 80, 0, 0);
    }

    @Test
    void shouldExtractPullAndTrackOriginalDemandAfterCommit() {
        Fixture f = fixture(500, A);
        assertEquals(80, extractCommitted(f, A, 120));
        assertPort(f, A, 120, 80);
        assertEquals(1, f.tickReads.get());
        assertState(f, 420, 0, 80, 1);
    }

    @Test
    void shouldSupportExplicitPullOrigin() {
        Fixture f = fixture(500, A);
        assertEquals(30, extractCommitted(f, A, 30, PULL));
        assertPort(f, A, 30, 30);
        assertState(f, 470, 50, 30, 1);
    }

    @Test
    void shouldExtractPushWithoutCreatingPullDemand() {
        Fixture f = fixture(500, A);
        f.distribution.updatePushAvailability(Map.of(A, 80), TICK);
        assertEquals(45, extractCommitted(f, A, 45, PUSH));
        assertPort(f, A, 0, 45);
        assertEquals(1, f.tickReads.get());
        assertState(f, 455, 35, 45, 1);
    }

    @Test
    void shouldShareBudgetWhenPullPrecedesPush() {
        Fixture f = fixture(500, A);
        assertEquals(50, extractCommitted(f, A, 50));
        assertEquals(30, extractCommitted(f, A, 70, PUSH));
        assertPort(f, A, 50, 80);
        assertState(f, 420, 0, 80, 2);
    }

    @Test
    void shouldShareBudgetWhenPushPrecedesPull() {
        Fixture f = fixture(500, A);
        f.distribution.updatePushAvailability(Map.of(A, 80), TICK);
        assertEquals(50, extractCommitted(f, A, 50, PUSH));
        assertEquals(30, extractCommitted(f, A, 70));
        assertPort(f, A, 70, 80);
        assertState(f, 420, 0, 80, 2);
    }

    @Test
    void shouldRecordDemandEvenWithExhaustedBudget() {
        Fixture f = fixture(500, A);
        assertEquals(80, extractCommitted(f, A, 80));
        assertEquals(0, extractCommitted(f, A, 150));
        assertPort(f, A, 150, 80);
        assertState(f, 420, 0, 80, 1);
    }

    @Test
    void shouldLimitExtractionToStoredEnergy() {
        Fixture f = fixture(25, A);
        assertEquals(25, extractCommitted(f, A, 100));
        assertPort(f, A, 100, 25);
        assertState(f, 0, 55, 25, 1);
    }

    @Test
    void shouldRecordPullDemandButNoTransferForEmptyBuffer() {
        Fixture f = fixture(0, A);
        assertEquals(0, extractCommitted(f, A, 50));
        assertPort(f, A, 50, 0);
        assertState(f, 0, 80, 0, 0);
    }

    @Test
    void shouldRestoreFullBudgetOnNextTick() {
        Fixture f = fixture(500, A);
        assertEquals(80, extractCommitted(f, A, 80));
        f.tick++;
        assertEquals(80, f.budget.getRemaining(f.tick));
        assertEquals(80, f.tracker.getCurrentTransferRate(f.tick));
        assertEquals(60, extractCommitted(f, A, 60));
        assertPort(f, A, 60, 60);
        assertState(f, 360, 20, 60, 2);
        assertEquals(80, f.tracker.getCurrentTransferRate(f.tick));
    }

    @Test
    void shouldRespectOtherPortQuotaAcrossPushAndPull() {
        Fixture f = fixture(40, A, B);
        seedDemand(f, TICK - 1, A, B);
        f.distribution.updatePushAvailability(Map.of(A, 80), TICK);
        assertEquals(20, extractCommitted(f, A, 80, PUSH));
        assertEquals(20, extractCommitted(f, B, 80));
        assertPort(f, A, 0, 20);
        assertPort(f, B, 80, 20);
        assertState(f, 0, 40, 40, 2);
    }

    @Test
    void shouldExtractFromStorageLargerThanIntMaximum() {
        Fixture f = fixture(3_000_000_000L, A);
        assertEquals(80, extractCommitted(f, A, 100));
        assertPort(f, A, 100, 80);
        assertState(f, 2_999_999_920L, 0, 80, 1);
    }

    @Test
    void shouldUpdateStatisticsOnlyAfterRootCommit() {
        Fixture f = fixture(500, A);
        try (Transaction tx = Transaction.openRoot()) {
            assertEquals(40, f.controller.extract(A, 40, tx));
            assertPort(f, A, 40, 40);
            assertState(f, 460, 40, 0, 0);
            tx.commit();
        }
        assertState(f, 460, 40, 40, 1);
    }

    @Test
    void shouldRollbackPullAndPushTogether() {
        Fixture f = fixture(500, A);
        try (Transaction tx = Transaction.openRoot()) {
            assertEquals(30, f.controller.extract(A, 30, tx));
            assertEquals(50, f.controller.extract(A, 70, tx, PUSH));
            assertPort(f, A, 30, 80);
            assertState(f, 420, 0, 0, 0);
        }
        assertPort(f, A, 0, 0);
        assertFalse(f.distribution.isActive(A, TICK));
        assertState(f, 500, 80, 0, 0);
        assertEquals(80, extractCommitted(f, A, 80));
        assertState(f, 420, 0, 80, 1);
    }

    @Test
    void shouldPreservePreviousCommitAfterLaterRollback() {
        Fixture f = fixture(500, A);
        assertEquals(30, extractCommitted(f, A, 30));
        try (Transaction tx = Transaction.openRoot()) {
            assertEquals(50, f.controller.extract(A, 80, tx));
            assertPort(f, A, 80, 80);
            assertState(f, 420, 0, 30, 1);
        }
        assertPort(f, A, 30, 30);
        assertState(f, 470, 50, 30, 1);
    }

    @Test
    void shouldRollbackChildWithoutLosingOuterChanges() {
        Fixture f = fixture(500, A);
        try (Transaction root = Transaction.openRoot()) {
            assertEquals(30, f.controller.extract(A, 30, root));
            try (Transaction child = Transaction.open(root)) {
                assertEquals(40, f.controller.extract(A, 40, child, PUSH));
                assertState(f, 430, 10, 0, 0);
            }
            assertPort(f, A, 30, 30);
            assertState(f, 470, 50, 0, 0);
            root.commit();
        }
        assertState(f, 470, 50, 30, 1);
    }

    @Test
    void shouldRollbackCommittedChildWhenRootRollsBack() {
        Fixture f = fixture(500, A);
        try (Transaction root = Transaction.openRoot()) {
            try (Transaction child = Transaction.open(root)) {
                assertEquals(60, f.controller.extract(A, 60, child, PUSH));
                child.commit();
            }
            assertPort(f, A, 0, 60);
            assertState(f, 440, 20, 0, 0);
        }
        assertPort(f, A, 0, 0);
        assertState(f, 500, 80, 0, 0);
    }

    @Test
    void shouldAccumulateAllCommittedChildAndRootTransfers() {
        Fixture f = fixture(500, A);
        try (Transaction root = Transaction.openRoot()) {
            assertEquals(20, f.controller.extract(A, 20, root));
            try (Transaction child = Transaction.open(root)) {
                assertEquals(30, f.controller.extract(A, 30, child, PUSH));
                child.commit();
            }
            assertEquals(30, f.controller.extract(A, 40, root));
            assertPort(f, A, 40, 80);
            assertState(f, 420, 0, 0, 0);
            root.commit();
        }
        assertState(f, 420, 0, 80, 1);
        assertEquals(3, f.tickReads.get());
    }

    @Test
    void shouldRollbackFairnessCorrectionAndReapplyItOnRetry() {
        Fixture f = fixture(80, A, B);
        seedDemand(f, 99, A, B);
        seedDemand(f, 100, A, B);
        try (Transaction tx = Transaction.openRoot()) {
            f.distribution.recordExtraction(A, 30, 100, tx);
            f.distribution.recordExtraction(B, 10, 100, tx);
            tx.commit();
        }
        f.tick = 101;
        Map<EnergyPortId, Long> before = f.distribution.getFairnessCreditsSnapshot();
        Map<EnergyPortId, Long> during;
        try (Transaction tx = Transaction.openRoot()) {
            assertEquals(30, f.controller.extract(A, 80, tx));
            during = f.distribution.getFairnessCreditsSnapshot();
            assertNotEquals(before, during);
            assertState(f, 50, 50, 0, 0);
        }
        assertEquals(before, f.distribution.getFairnessCreditsSnapshot());
        assertPort(f, A, 0, 0);
        assertEquals(80, f.distribution.getPreviousMaxRequestedByPort(A, 101));
        assertEquals(30, f.distribution.getPreviousExtractedByPort(A, 101));
        assertState(f, 80, 80, 0, 0);
        assertEquals(30, extractCommitted(f, A, 80));
        assertEquals(during, f.distribution.getFairnessCreditsSnapshot());
        assertState(f, 50, 50, 30, 1);
    }

    @Test
    void shouldPreserveOtherPortQuotaWithLargeStorageAndRepeatedExtraction() {
        Fixture f = fixture(3_000_000_000L, A, B);
        seedDemand(f, TICK - 1, A, B);
        assertEquals(20, extractCommitted(f, A, 20));
        assertEquals(20, extractCommitted(f, A, 60), "A must not take B's reserved 40 FE");
        assertEquals(40, extractCommitted(f, B, 80));
        assertPort(f, A, 60, 40);
        assertPort(f, B, 80, 40);
        assertState(f, 2_999_999_920L, 0, 80, 3);
    }
}
