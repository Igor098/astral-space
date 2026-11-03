package ru.greatdragonx.astralspace.common.energy;

import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.junit.jupiter.api.Test;
import ru.greatdragonx.astralspace.common.energy.component.EnergyComponent;
import ru.greatdragonx.astralspace.common.energy.port.EnergyPortId;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class EnergyTransferCoordinatorTest {
    private static final EnergyPortId INPUT_A = new EnergyPortId("test.input.a");
    private static final EnergyPortId INPUT_B = new EnergyPortId("test.input.b");
    private static final EnergyPortId OUTPUT_A = new EnergyPortId("test.output.a");
    private static final EnergyPortId OUTPUT_B = new EnergyPortId("test.output.b");
    private static final EnergyPortId UNKNOWN = new EnergyPortId("test.unknown");

    private record Fixture(EnergyComponent energy,
        EnergyDistributionManager distribution,
        EnergyTransferBudget insertionBudget,
        EnergyTransferBudget extractionBudget,
        EnergyTransferTracker transferTracker,
        EnergyTransferCoordinator coordinator,
        AtomicLong tick,
        AtomicInteger energyChangedCalls) {}

    private static Fixture fixture(
        long capacity,
        int maxInsertPerTick,
        int maxExtractPerTick
    )
    {
        return fixture(
            capacity,
            0L,
            maxInsertPerTick,
            maxExtractPerTick
        );
    }

    private static Fixture fixture(
        long capacity,
        long initialEnergy,
        int maxInsertPerTick,
        int maxExtractPerTick,
        EnergyPortId... outputPorts
    )
    {
        AtomicLong tick = new AtomicLong(100L);
        AtomicInteger energyChangedCalls = new AtomicInteger();
        EnergyComponent energy = new EnergyComponent(
            capacity,
            energyChangedCalls::incrementAndGet
        );
        energy.restoreStoredEnergy(initialEnergy);

        EnergyDistributionManager distribution = new EnergyDistributionManager(maxExtractPerTick);
        distribution.updateAvailablePorts(List.of(outputPorts));

        EnergyTransferBudget insertionBudget = new EnergyTransferBudget(maxInsertPerTick);
        EnergyTransferBudget extractionBudget = new EnergyTransferBudget(maxExtractPerTick);
        EnergyTransferTracker tracker = new EnergyTransferTracker();

        EnergyTransferCoordinator coordinator = new EnergyTransferCoordinator(
            energy,
            distribution,
            insertionBudget,
            extractionBudget,
            tracker,
            tick::get
        );

        return new Fixture(
            energy,
            distribution,
            insertionBudget,
            extractionBudget,
            tracker,
            coordinator,
            tick,
            energyChangedCalls
        );
    }

    private static Fixture singleOutput(
        long capacity,
        long initialEnergy
    )
    {
        return fixture(
            capacity,
            initialEnergy,
            100,
            80,
            OUTPUT_A
        );
    }

    private static int extractCommitted(
        Fixture f,
        EnergyPortId portId,
        int amount
    )
    {
        try (Transaction tx = Transaction.openRoot()) {
            int extracted = f.coordinator()
                .extract(
                    portId,
                    amount,
                    tx
                );
            tx.commit();
            return extracted;
        }
    }

    private static int extractCommitted(
        Fixture f,
        EnergyPortId portId,
        int amount,
        EnergyTransferCoordinator.ExtractionOrigin origin
    )
    {
        try (Transaction tx = Transaction.openRoot()) {
            int extracted = f.coordinator()
                .extract(
                    portId,
                    amount,
                    tx,
                    origin
                );
            tx.commit();
            return extracted;
        }
    }

    @Test
    void shouldRejectNullDependencies() {
        Fixture f = fixture(
            1_000L,
            100,
            80
        );
        EnergyDistributionManager distribution = new EnergyDistributionManager(80);

        assertAll(
            () -> assertThrows(
                NullPointerException.class,
                () -> new EnergyTransferCoordinator(
                    null,
                    distribution,
                    f.insertionBudget(),
                    f.extractionBudget(),
                    f.transferTracker(),
                    f.tick()::get
                )
            ),
            () -> assertThrows(
                NullPointerException.class,
                () -> new EnergyTransferCoordinator(
                    f.energy(),
                    null,
                    f.insertionBudget(),
                    f.extractionBudget(),
                    f.transferTracker(),
                    f.tick()::get
                )
            ),
            () -> assertThrows(
                NullPointerException.class,
                () -> new EnergyTransferCoordinator(
                    f.energy(),
                    distribution,
                    null,
                    f.extractionBudget(),
                    f.transferTracker(),
                    f.tick()::get
                )
            ),
            () -> assertThrows(
                NullPointerException.class,
                () -> new EnergyTransferCoordinator(
                    f.energy(),
                    distribution,
                    f.insertionBudget(),
                    null,
                    f.transferTracker(),
                    f.tick()::get
                )
            ),
            () -> assertThrows(
                NullPointerException.class,
                () -> new EnergyTransferCoordinator(
                    f.energy(),
                    distribution,
                    f.insertionBudget(),
                    f.extractionBudget(),
                    null,
                    f.tick()::get
                )
            ),
            () -> assertThrows(
                NullPointerException.class,
                () -> new EnergyTransferCoordinator(
                    f.energy(),
                    distribution,
                    f.insertionBudget(),
                    f.extractionBudget(),
                    f.transferTracker(),
                    null
                )
            )
        );
    }

    @Test
    void shouldRejectSharedInsertionAndExtractionBudgetInstance() {
        Fixture f = fixture(
            1_000L,
            100,
            80
        );
        EnergyTransferBudget sameBudget = new EnergyTransferBudget(100);

        assertThrows(
            IllegalArgumentException.class,
            () -> new EnergyTransferCoordinator(
                f.energy(),
                new EnergyDistributionManager(100),
                sameBudget,
                sameBudget,
                f.transferTracker(),
                f.tick()::get
            )
        );
    }

    @Test
    void shouldDelegateLongStorageReadOperations() {
        Fixture f = fixture(
            Long.MAX_VALUE,
            100,
            80
        );
        long largeAmount = 2L * Integer.MAX_VALUE;
        f.energy()
            .restoreStoredEnergy(largeAmount);

        assertAll(
            () -> assertEquals(
                largeAmount,
                f.coordinator()
                    .getStoredEnergy()
            ),
            () -> assertEquals(
                Long.MAX_VALUE,
                f.coordinator()
                    .getCapacity()
            ),
            () -> assertEquals(
                0,
                f.energyChangedCalls()
                    .get()
            )
        );
    }

    @Test
    void shouldInsertEnergyAndConsumeOnlyInputBudgetAfterCommit() {
        Fixture f = fixture(
            1_000L,
            100,
            80
        );

        try (Transaction tx = Transaction.openRoot()) {
            assertEquals(
                60,
                f.coordinator()
                    .insert(
                        INPUT_A,
                        60,
                        tx
                    )
            );
            assertEquals(
                60L,
                f.energy()
                    .getStoredEnergy()
            );
            assertEquals(
                40,
                f.insertionBudget()
                    .getRemaining(100)
            );
            assertEquals(
                80,
                f.extractionBudget()
                    .getRemaining(100)
            );
            assertEquals(
                0,
                f.energyChangedCalls()
                    .get()
            );
            tx.commit();
        }

        assertAll(
            () -> assertEquals(
                60L,
                f.energy()
                    .getStoredEnergy()
            ),
            () -> assertEquals(
                40,
                f.insertionBudget()
                    .getRemaining(100)
            ),
            () -> assertEquals(
                80,
                f.extractionBudget()
                    .getRemaining(100)
            ),
            () -> assertEquals(
                1,
                f.energyChangedCalls()
                    .get()
            )
        );
    }

    @Test
    void shouldInsertOnlyWhatFitsAndChargeActualAmount() {
        Fixture f = fixture(
            100L,
            80,
            80
        );
        f.energy()
            .restoreStoredEnergy(90);

        try (Transaction tx = Transaction.openRoot()) {
            assertEquals(
                10,
                f.coordinator()
                    .insert(
                        INPUT_A,
                        50,
                        tx
                    )
            );
            tx.commit();
        }

        assertAll(
            () -> assertEquals(
                100L,
                f.energy()
                    .getStoredEnergy()
            ),
            () -> assertEquals(
                70,
                f.insertionBudget()
                    .getRemaining(100)
            ),
            () -> assertEquals(
                1,
                f.energyChangedCalls()
                    .get()
            )
        );
    }

    @Test
    void shouldRestrictInsertionToRemainingPerTickBudget() {
        Fixture f = fixture(
            1_000L,
            80,
            50
        );

        try (Transaction tx = Transaction.openRoot()) {
            assertEquals(
                80,
                f.coordinator()
                    .insert(
                        INPUT_A,
                        300,
                        tx
                    )
            );
            tx.commit();
        }

        assertAll(
            () -> assertEquals(
                80L,
                f.energy()
                    .getStoredEnergy()
            ),
            () -> assertEquals(
                0,
                f.insertionBudget()
                    .getRemaining(100)
            ),
            () -> assertEquals(
                50,
                f.extractionBudget()
                    .getRemaining(100)
            )
        );
    }

    @Test
    void shouldShareOneInsertionBudgetAcrossDifferentPortsAndTransactions() {
        Fixture f = fixture(
            1_000L,
            80,
            80
        );

        try (Transaction tx = Transaction.openRoot()) {
            assertEquals(
                50,
                f.coordinator()
                    .insert(
                        INPUT_A,
                        50,
                        tx
                    )
            );
            tx.commit();
        }

        try (Transaction tx = Transaction.openRoot()) {
            assertEquals(
                30,
                f.coordinator()
                    .insert(
                        INPUT_B,
                        50,
                        tx
                    )
            );
            tx.commit();
        }

        assertAll(
            () -> assertEquals(
                80L,
                f.energy()
                    .getStoredEnergy()
            ),
            () -> assertEquals(
                0,
                f.insertionBudget()
                    .getRemaining(100)
            ),
            () -> assertEquals(
                2,
                f.energyChangedCalls()
                    .get()
            )
        );
    }

    @Test
    void shouldReturnZeroWhenInsertionBudgetIsExhausted() {
        Fixture f = fixture(
            1_000L,
            80,
            80
        );

        try (Transaction tx = Transaction.openRoot()) {
            assertEquals(
                80,
                f.coordinator()
                    .insert(
                        INPUT_A,
                        80,
                        tx
                    )
            );
            tx.commit();
        }
        try (Transaction tx = Transaction.openRoot()) {
            assertEquals(
                0,
                f.coordinator()
                    .insert(
                        INPUT_B,
                        20,
                        tx
                    )
            );
            tx.commit();
        }

        assertAll(
            () -> assertEquals(
                80L,
                f.energy()
                    .getStoredEnergy()
            ),
            () -> assertEquals(
                0,
                f.insertionBudget()
                    .getRemaining(100)
            ),
            () -> assertEquals(
                1,
                f.energyChangedCalls()
                    .get()
            )
        );
    }

    @Test
    void shouldRefreshInsertionBudgetOnNextTick() {
        Fixture f = fixture(
            1_000L,
            80,
            80
        );

        try (Transaction tx = Transaction.openRoot()) {
            assertEquals(
                80,
                f.coordinator()
                    .insert(
                        INPUT_A,
                        80,
                        tx
                    )
            );
            tx.commit();
        }

        f.tick()
            .set(101);
        assertEquals(
            80,
            f.insertionBudget()
                .getRemaining(101)
        );

        try (Transaction tx = Transaction.openRoot()) {
            assertEquals(
                50,
                f.coordinator()
                    .insert(
                        INPUT_B,
                        50,
                        tx
                    )
            );
            tx.commit();
        }

        assertAll(
            () -> assertEquals(
                130L,
                f.energy()
                    .getStoredEnergy()
            ),
            () -> assertEquals(
                30,
                f.insertionBudget()
                    .getRemaining(101)
            )
        );
    }

    @Test
    void shouldNotConsumeBudgetWhenStorageIsFull() {
        Fixture f = fixture(
            100L,
            80,
            80
        );
        f.energy()
            .restoreStoredEnergy(100L);

        try (Transaction tx = Transaction.openRoot()) {
            assertEquals(
                0,
                f.coordinator()
                    .insert(
                        INPUT_A,
                        40,
                        tx
                    )
            );
            tx.commit();
        }

        assertAll(
            () -> assertEquals(
                100L,
                f.energy()
                    .getStoredEnergy()
            ),
            () -> assertEquals(
                80,
                f.insertionBudget()
                    .getRemaining(100)
            ),
            () -> assertEquals(
                0,
                f.energyChangedCalls()
                    .get()
            )
        );
    }

    @Test
    void shouldIgnoreZeroInsertionWithoutReadingCurrentTick() {
        AtomicInteger tickReads = new AtomicInteger();
        EnergyComponent energy = new EnergyComponent(
            100L,
            () -> {
            }
        );
        EnergyTransferBudget insertion = new EnergyTransferBudget(80);
        EnergyTransferBudget extraction = new EnergyTransferBudget(80);
        EnergyTransferCoordinator coordinator = new EnergyTransferCoordinator(
            energy,
            new EnergyDistributionManager(80),
            insertion,
            extraction,
            new EnergyTransferTracker(),
            () -> {
                tickReads.incrementAndGet();
                return 100L;
            }
        );

        try (Transaction tx = Transaction.openRoot()) {
            assertEquals(
                0,
                coordinator.insert(
                    INPUT_A,
                    0,
                    tx
                )
            );
            tx.commit();
        }

        assertAll(
            () -> assertEquals(
                0,
                tickReads.get()
            ),
            () -> assertEquals(
                0L,
                energy.getStoredEnergy()
            ),
            () -> assertEquals(
                80,
                insertion.getRemaining(100)
            )
        );
    }

    @Test
    void shouldRejectNegativeInsertionWithoutChangingState() {
        Fixture f = fixture(
            1_000L,
            80,
            80
        );

        try (Transaction tx = Transaction.openRoot()) {
            assertThrows(
                IllegalArgumentException.class,
                () -> f.coordinator()
                    .insert(
                        INPUT_A,
                        -1,
                        tx
                    )
            );
            tx.commit();
        }

        assertAll(
            () -> assertEquals(
                0L,
                f.energy()
                    .getStoredEnergy()
            ),
            () -> assertEquals(
                80,
                f.insertionBudget()
                    .getRemaining(100)
            ),
            () -> assertEquals(
                0,
                f.energyChangedCalls()
                    .get()
            )
        );
    }

    @Test
    void shouldRejectNullPortAndNullTransaction() {
        Fixture f = fixture(
            1_000L,
            80,
            80
        );

        try (Transaction tx = Transaction.openRoot()) {
            assertThrows(
                NullPointerException.class,
                () -> f.coordinator()
                    .insert(
                        null,
                        10,
                        tx
                    )
            );
            assertThrows(
                NullPointerException.class,
                () -> f.coordinator()
                    .insert(
                        INPUT_A,
                        10,
                        null
                    )
            );
        }

        assertAll(
            () -> assertEquals(
                0L,
                f.energy()
                    .getStoredEnergy()
            ),
            () -> assertEquals(
                80,
                f.insertionBudget()
                    .getRemaining(100)
            )
        );
    }

    @Test
    void shouldRollbackEnergyAndBudgetTogether() {
        Fixture f = fixture(
            1_000L,
            100,
            80
        );

        try (Transaction tx = Transaction.openRoot()) {
            assertEquals(
                70,
                f.coordinator()
                    .insert(
                        INPUT_A,
                        70,
                        tx
                    )
            );
            assertEquals(
                70L,
                f.energy()
                    .getStoredEnergy()
            );
            assertEquals(
                30,
                f.insertionBudget()
                    .getRemaining(100)
            );
        }

        assertAll(
            () -> assertEquals(
                0L,
                f.energy()
                    .getStoredEnergy()
            ),
            () -> assertEquals(
                100,
                f.insertionBudget()
                    .getRemaining(100)
            ),
            () -> assertEquals(
                0,
                f.energyChangedCalls()
                    .get()
            )
        );
    }

    @Test
    void shouldNotifyEnergyChangesOnlyOnceForMultipleInsertsInOneCommit() {
        Fixture f = fixture(
            1_000L,
            100,
            80
        );

        try (Transaction tx = Transaction.openRoot()) {
            assertEquals(
                30,
                f.coordinator()
                    .insert(
                        INPUT_A,
                        30,
                        tx
                    )
            );
            assertEquals(
                20,
                f.coordinator()
                    .insert(
                        INPUT_B,
                        20,
                        tx
                    )
            );
            assertEquals(
                0,
                f.energyChangedCalls()
                    .get()
            );
            tx.commit();
        }

        assertAll(
            () -> assertEquals(
                50L,
                f.energy()
                    .getStoredEnergy()
            ),
            () -> assertEquals(
                50,
                f.insertionBudget()
                    .getRemaining(100)
            ),
            () -> assertEquals(
                1,
                f.energyChangedCalls()
                    .get()
            )
        );
    }

    @Test
    void shouldPreservePreviousCommitAfterLaterRollback() {
        Fixture f = fixture(
            1_000L,
            100,
            80
        );

        try (Transaction tx = Transaction.openRoot()) {
            assertEquals(
                30,
                f.coordinator()
                    .insert(
                        INPUT_A,
                        30,
                        tx
                    )
            );
            tx.commit();
        }

        try (Transaction tx = Transaction.openRoot()) {
            assertEquals(
                40,
                f.coordinator()
                    .insert(
                        INPUT_B,
                        40,
                        tx
                    )
            );
            assertEquals(
                70L,
                f.energy()
                    .getStoredEnergy()
            );
        }

        assertAll(
            () -> assertEquals(
                30L,
                f.energy()
                    .getStoredEnergy()
            ),
            () -> assertEquals(
                70,
                f.insertionBudget()
                    .getRemaining(100)
            ),
            () -> assertEquals(
                1,
                f.energyChangedCalls()
                    .get()
            )
        );
    }

    @Test
    void shouldRollbackNestedInsertionWithoutLosingOuterChanges() {
        Fixture f = fixture(
            1_000L,
            100,
            80
        );

        try (Transaction root = Transaction.openRoot()) {
            assertEquals(
                30,
                f.coordinator()
                    .insert(
                        INPUT_A,
                        30,
                        root
                    )
            );

            try (Transaction nested = Transaction.open(root)) {
                assertEquals(
                    40,
                    f.coordinator()
                        .insert(
                            INPUT_B,
                            40,
                            nested
                        )
                );
                assertEquals(
                    70,
                    f.energy()
                        .getStoredEnergy()
                );
            }

            assertEquals(
                30L,
                f.energy()
                    .getStoredEnergy()
            );
            assertEquals(
                70,
                f.insertionBudget()
                    .getRemaining(100)
            );
            root.commit();
        }

        assertAll(
            () -> assertEquals(
                30L,
                f.energy()
                    .getStoredEnergy()
            ),
            () -> assertEquals(
                70,
                f.insertionBudget()
                    .getRemaining(100)
            ),
            () -> assertEquals(
                1,
                f.energyChangedCalls()
                    .get()
            )
        );
    }

    @Test
    void shouldRollbackCommittedNestedInsertionWhenRootRollsBack() {
        Fixture f = fixture(
            1_000L,
            100,
            80
        );

        try (Transaction root = Transaction.openRoot()) {
            try (Transaction nested = Transaction.open(root)) {
                assertEquals(
                    60,
                    f.coordinator()
                        .insert(
                            INPUT_A,
                            60,
                            nested
                        )
                );
                nested.commit();
            }

            assertEquals(
                60L,
                f.energy()
                    .getStoredEnergy()
            );
            assertEquals(
                40,
                f.insertionBudget()
                    .getRemaining(100)
            );
        }

        assertAll(
            () -> assertEquals(
                0L,
                f.energy()
                    .getStoredEnergy()
            ),
            () -> assertEquals(
                100,
                f.insertionBudget()
                    .getRemaining(100)
            ),
            () -> assertEquals(
                0,
                f.energyChangedCalls()
                    .get()
            )
        );
    }

    @Test
    void shouldPreserveCommittedNestedInsertionWhenRootCommits() {
        Fixture f = fixture(
            1_000L,
            100,
            80
        );

        try (Transaction root = Transaction.openRoot()) {
            try (Transaction nested = Transaction.open(root)) {
                assertEquals(
                    60,
                    f.coordinator()
                        .insert(
                            INPUT_A,
                            60,
                            nested
                        )
                );
                nested.commit();
            }

            assertEquals(
                0,
                f.energyChangedCalls()
                    .get()
            );
            root.commit();
        }

        assertAll(
            () -> assertEquals(
                60L,
                f.energy()
                    .getStoredEnergy()
            ),
            () -> assertEquals(
                40,
                f.insertionBudget()
                    .getRemaining(100)
            ),
            () -> assertEquals(
                1,
                f.energyChangedCalls()
                    .get()
            )
        );
    }

    @Test
    void shouldKeepInputAndOutputBudgetsIndependent() {
        Fixture f = fixture(
            1_000L,
            100,
            80
        );

        try (Transaction tx = Transaction.openRoot()) {
            f.extractionBudget()
                .recordTransfer(
                    80,
                    100,
                    tx
                );
            tx.commit();
        }

        assertEquals(
            0,
            f.extractionBudget()
                .getRemaining(100)
        );

        try (Transaction tx = Transaction.openRoot()) {
            assertEquals(
                70,
                f.coordinator()
                    .insert(
                        INPUT_A,
                        70,
                        tx
                    )
            );
            tx.commit();
        }

        assertAll(
            () -> assertEquals(
                70L,
                f.energy()
                    .getStoredEnergy()
            ),
            () -> assertEquals(
                30,
                f.insertionBudget()
                    .getRemaining(100)
            ),
            () -> assertEquals(
                0,
                f.extractionBudget()
                    .getRemaining(100)
            )
        );
    }

    @Test
    void shouldNotRecordIncomingEnergyAsOutgoingTransfer() {
        Fixture f = fixture(
            1_000L,
            100,
            80
        );

        try (Transaction tx = Transaction.openRoot()) {
            assertEquals(
                40,
                f.coordinator()
                    .insert(
                        INPUT_A,
                        40,
                        tx
                    )
            );
            tx.commit();
        }

        assertEquals(
            0,
            f.transferTracker()
                .getCurrentTransferRate(100)
        );
    }

    @Test
    void shouldExtractEnergyAndChargeOnlyOutputBudget() {
        Fixture f = singleOutput(
            1_000L,
            500L
        );

        try (Transaction tx = Transaction.openRoot()) {
            assertEquals(
                60,
                f.coordinator()
                    .extract(
                        OUTPUT_A,
                        60,
                        tx
                    )
            );
            assertEquals(
                440L,
                f.energy()
                    .getStoredEnergy()
            );
            assertEquals(
                20,
                f.extractionBudget()
                    .getRemaining(100L)
            );
            assertEquals(
                100,
                f.insertionBudget()
                    .getRemaining(100L)
            );
            assertEquals(
                60,
                f.distribution()
                    .getExtractedByPort(
                        OUTPUT_A,
                        100L
                    )
            );
            assertEquals(
                0,
                f.energyChangedCalls()
                    .get()
            );
            assertEquals(
                0,
                f.transferTracker()
                    .getCurrentTransferRate(101L)
            );
            tx.commit();
        }

        assertAll(
            () -> assertEquals(
                440L,
                f.energy()
                    .getStoredEnergy()
            ),
            () -> assertEquals(
                20,
                f.extractionBudget()
                    .getRemaining(100L)
            ),
            () -> assertEquals(
                60,
                f.distribution()
                    .getExtractedByPort(
                        OUTPUT_A,
                        100L
                    )
            ),
            () -> assertEquals(
                1,
                f.energyChangedCalls()
                    .get()
            ),
            () -> assertEquals(
                60,
                f.transferTracker()
                    .getCurrentTransferRate(101L)
            )
        );
    }

    @Test
    void shouldLimitExtractionToOutputBudget() {
        Fixture f = singleOutput(
            1_000L,
            500L
        );

        assertEquals(
            80,
            extractCommitted(
                f,
                OUTPUT_A,
                300
            )
        );
        assertAll(
            () -> assertEquals(
                420L,
                f.energy()
                    .getStoredEnergy()
            ),
            () -> assertEquals(
                0,
                f.extractionBudget()
                    .getRemaining(100L)
            ),
            () -> assertEquals(
                80,
                f.distribution()
                    .getExtractedByPort(
                        OUTPUT_A,
                        100L
                    )
            )
        );
    }

    @Test
    void shouldShareOutputBudgetBetweenPortsAcrossCommittedTransactions() {
        Fixture f = fixture(
            1_000L,
            500L,
            100,
            80,
            OUTPUT_A,
            OUTPUT_B
        );

        assertEquals(
            50,
            extractCommitted(
                f,
                OUTPUT_A,
                50
            )
        );
        int second = extractCommitted(
            f,
            OUTPUT_B,
            80
        );

        assertTrue(second >= 0 && second <= 30);
        assertEquals(
            50 + second,
            f.distribution()
                .getExtractedByPort(
                    OUTPUT_A,
                    100L
                ) + f.distribution()
                .getExtractedByPort(
                    OUTPUT_B,
                    100L
                )
        );
        assertEquals(
            80 - 50 - second,
            f.extractionBudget()
                .getRemaining(100L)
        );
        assertEquals(
            450L - second,
            f.energy()
                .getStoredEnergy()
        );
    }

    @Test
    void shouldAllowOnlyRemainingStoredEnergy() {
        Fixture f = singleOutput(
            1_000L,
            25L
        );

        assertEquals(
            25,
            extractCommitted(
                f,
                OUTPUT_A,
                80
            )
        );
        assertAll(
            () -> assertEquals(
                0L,
                f.energy()
                    .getStoredEnergy()
            ),
            () -> assertEquals(
                55,
                f.extractionBudget()
                    .getRemaining(100L)
            ),
            () -> assertEquals(
                25,
                f.distribution()
                    .getExtractedByPort(
                        OUTPUT_A,
                        100L
                    )
            ),
            () -> assertEquals(
                25,
                f.transferTracker()
                    .getCurrentTransferRate(101L)
            )
        );
    }

    @Test
    void shouldNotChargeBudgetOrTransferTrackerWhenStorageIsEmpty() {
        Fixture f = singleOutput(
            1_000L,
            0L
        );

        assertEquals(
            0,
            extractCommitted(
                f,
                OUTPUT_A,
                80
            )
        );
        assertAll(
            () -> assertEquals(
                0L,
                f.energy()
                    .getStoredEnergy()
            ),
            () -> assertEquals(
                80,
                f.extractionBudget()
                    .getRemaining(100L)
            ),
            () -> assertEquals(
                0,
                f.distribution()
                    .getExtractedByPort(
                        OUTPUT_A,
                        100L
                    )
            ),
            () -> assertEquals(
                0,
                f.transferTracker()
                    .getCurrentTransferRate(101L)
            ),
            () -> assertEquals(
                0,
                f.energyChangedCalls()
                    .get()
            )
        );
    }

    @Test
    void shouldRejectUnavailablePortWithoutTouchingEnergyOrBudget() {
        Fixture f = singleOutput(
            1_000L,
            500L
        );

        assertEquals(
            0,
            extractCommitted(
                f,
                UNKNOWN,
                40
            )
        );
        assertAll(
            () -> assertEquals(
                500L,
                f.energy()
                    .getStoredEnergy()
            ),
            () -> assertEquals(
                80,
                f.extractionBudget()
                    .getRemaining(100L)
            ),
            () -> assertEquals(
                0,
                f.distribution()
                    .getMaxRequestedByPort(
                        UNKNOWN,
                        100L
                    )
            ),
            () -> assertEquals(
                0,
                f.distribution()
                    .getExtractedByPort(
                        UNKNOWN,
                        100L
                    )
            ),
            () -> assertEquals(
                0,
                f.transferTracker()
                    .getCurrentTransferRate(101L)
            )
        );
    }

    @Test
    void shouldRejectExtractionWhenNoOutputPortsAreConfigured() {
        Fixture f = fixture(
            1_000L,
            500L,
            100,
            80
        );

        assertEquals(
            0,
            extractCommitted(
                f,
                OUTPUT_A,
                50
            )
        );
        assertEquals(
            500L,
            f.energy()
                .getStoredEnergy()
        );
        assertEquals(
            80,
            f.extractionBudget()
                .getRemaining(100L)
        );
    }

    @Test
    void shouldRejectNegativeAmountWithoutChangingState() {
        Fixture f = singleOutput(
            1_000L,
            500L
        );

        try (Transaction tx = Transaction.openRoot()) {
            assertThrows(
                IllegalArgumentException.class,
                () -> f.coordinator()
                    .extract(
                        OUTPUT_A,
                        -1,
                        tx
                    )
            );
        }

        assertAll(
            () -> assertEquals(
                500L,
                f.energy()
                    .getStoredEnergy()
            ),
            () -> assertEquals(
                80,
                f.extractionBudget()
                    .getRemaining(100L)
            ),
            () -> assertEquals(
                0,
                f.distribution()
                    .getMaxRequestedByPort(
                        OUTPUT_A,
                        100L
                    )
            )
        );
    }

    @Test
    void shouldRejectNullPortAndTransaction() {
        Fixture f = singleOutput(
            1_000L,
            500L
        );

        try (Transaction tx = Transaction.openRoot()) {
            assertThrows(
                NullPointerException.class,
                () -> f.coordinator()
                    .extract(
                        null,
                        10,
                        tx
                    )
            );
            assertThrows(
                NullPointerException.class,
                () -> f.coordinator()
                    .extract(
                        OUTPUT_A,
                        10,
                        null
                    )
            );
        }

        assertEquals(
            500L,
            f.energy()
                .getStoredEnergy()
        );
        assertEquals(
            80,
            f.extractionBudget()
                .getRemaining(100L)
        );
    }

    @Test
    void shouldReturnZeroWithoutReadingTickWhenAmountIsZero() {
        AtomicInteger tickReads = new AtomicInteger();
        EnergyComponent energy = new EnergyComponent(
            1_000L,
            () -> {
            }
        );
        energy.restoreStoredEnergy(500L);
        EnergyDistributionManager distribution = new EnergyDistributionManager(80);
        distribution.updateAvailablePorts(List.of(OUTPUT_A));
        EnergyTransferCoordinator coordinator = new EnergyTransferCoordinator(
            energy,
            distribution,
            new EnergyTransferBudget(100),
            new EnergyTransferBudget(80),
            new EnergyTransferTracker(),
            () -> {
                tickReads.incrementAndGet();
                return 100L;
            }
        );

        try (Transaction tx = Transaction.openRoot()) {
            assertEquals(
                0,
                coordinator.extract(
                    OUTPUT_A,
                    0,
                    tx
                )
            );
            tx.commit();
        }

        assertAll(
            () -> assertEquals(
                0,
                tickReads.get()
            ),
            () -> assertEquals(
                500L,
                energy.getStoredEnergy()
            ),
            () -> assertEquals(
                0,
                distribution.getMaxRequestedByPort(
                    OUTPUT_A,
                    100L
                )
            )
        );
    }

    @Test
    void shouldHandleStorageLargerThanIntMaximum() {
        long largeAmount = (long) Integer.MAX_VALUE + 1_000L;
        Fixture f = singleOutput(
            largeAmount + 1_000L,
            largeAmount
        );

        assertEquals(
            80,
            extractCommitted(
                f,
                OUTPUT_A,
                80
            )
        );
        assertAll(
            () -> assertEquals(
                largeAmount - 80L,
                f.energy()
                    .getStoredEnergy()
            ),
            () -> assertEquals(
                0,
                f.extractionBudget()
                    .getRemaining(100L)
            ),
            () -> assertEquals(
                80,
                f.transferTracker()
                    .getCurrentTransferRate(101L)
            )
        );
    }

    @Test
    void shouldRollBackEnergyOutputBudgetUsageAndTracker() {
        Fixture f = singleOutput(
            1_000L,
            500L
        );

        try (Transaction tx = Transaction.openRoot()) {
            assertEquals(
                60,
                f.coordinator()
                    .extract(
                        OUTPUT_A,
                        60,
                        tx
                    )
            );
            assertEquals(
                440L,
                f.energy()
                    .getStoredEnergy()
            );
            assertEquals(
                20,
                f.extractionBudget()
                    .getRemaining(100L)
            );
            assertEquals(
                60,
                f.distribution()
                    .getExtractedByPort(
                        OUTPUT_A,
                        100L
                    )
            );
        }

        assertAll(
            () -> assertEquals(
                500L,
                f.energy()
                    .getStoredEnergy()
            ),
            () -> assertEquals(
                80,
                f.extractionBudget()
                    .getRemaining(100L)
            ),
            () -> assertEquals(
                0,
                f.distribution()
                    .getExtractedByPort(
                        OUTPUT_A,
                        100L
                    )
            ),
            () -> assertEquals(
                0,
                f.distribution()
                    .getMaxRequestedByPort(
                        OUTPUT_A,
                        100L
                    )
            ),
            () -> assertEquals(
                0,
                f.transferTracker()
                    .getCurrentTransferRate(101L)
            ),
            () -> assertEquals(
                0,
                f.energyChangedCalls()
                    .get()
            )
        );
    }

    @Test
    void shouldKeepPreviousCommittedExtractionAfterLaterRollback() {
        Fixture f = singleOutput(
            1_000L,
            500L
        );

        assertEquals(
            30,
            extractCommitted(
                f,
                OUTPUT_A,
                30
            )
        );

        try (Transaction tx = Transaction.openRoot()) {
            assertEquals(
                40,
                f.coordinator()
                    .extract(
                        OUTPUT_A,
                        40,
                        tx
                    )
            );
        }

        assertAll(
            () -> assertEquals(
                470L,
                f.energy()
                    .getStoredEnergy()
            ),
            () -> assertEquals(
                50,
                f.extractionBudget()
                    .getRemaining(100L)
            ),
            () -> assertEquals(
                30,
                f.distribution()
                    .getExtractedByPort(
                        OUTPUT_A,
                        100L
                    )
            ),
            () -> assertEquals(
                30,
                f.transferTracker()
                    .getCurrentTransferRate(101L)
            ),
            () -> assertEquals(
                1,
                f.energyChangedCalls()
                    .get()
            )
        );
    }

    @Test
    void shouldRollbackNestedExtractionWithoutUndoingOuterChanges() {
        Fixture f = singleOutput(
            1_000L,
            500L
        );

        try (Transaction root = Transaction.openRoot()) {
            assertEquals(
                30,
                f.coordinator()
                    .extract(
                        OUTPUT_A,
                        30,
                        root
                    )
            );

            try (Transaction child = Transaction.open(root)) {
                assertEquals(
                    40,
                    f.coordinator()
                        .extract(
                            OUTPUT_A,
                            40,
                            child
                        )
                );
                assertEquals(
                    430L,
                    f.energy()
                        .getStoredEnergy()
                );
            }

            assertEquals(
                470L,
                f.energy()
                    .getStoredEnergy()
            );
            assertEquals(
                50,
                f.extractionBudget()
                    .getRemaining(100L)
            );
            assertEquals(
                30,
                f.distribution()
                    .getExtractedByPort(
                        OUTPUT_A,
                        100L
                    )
            );
            root.commit();
        }

        assertAll(
            () -> assertEquals(
                470L,
                f.energy()
                    .getStoredEnergy()
            ),
            () -> assertEquals(
                30,
                f.transferTracker()
                    .getCurrentTransferRate(101L)
            ),
            () -> assertEquals(
                1,
                f.energyChangedCalls()
                    .get()
            )
        );
    }

    @Test
    void shouldRollbackCommittedNestedExtractionWithRoot() {
        Fixture f = singleOutput(
            1_000L,
            500L
        );

        try (Transaction root = Transaction.openRoot()) {
            try (Transaction child = Transaction.open(root)) {
                assertEquals(
                    60,
                    f.coordinator()
                        .extract(
                            OUTPUT_A,
                            60,
                            child
                        )
                );
                child.commit();
            }
            assertEquals(
                440L,
                f.energy()
                    .getStoredEnergy()
            );
            assertEquals(
                20,
                f.extractionBudget()
                    .getRemaining(100L)
            );
            assertEquals(
                0,
                f.transferTracker()
                    .getCurrentTransferRate(101L)
            );
        }

        assertAll(
            () -> assertEquals(
                500L,
                f.energy()
                    .getStoredEnergy()
            ),
            () -> assertEquals(
                80,
                f.extractionBudget()
                    .getRemaining(100L)
            ),
            () -> assertEquals(
                0,
                f.distribution()
                    .getExtractedByPort(
                        OUTPUT_A,
                        100L
                    )
            ),
            () -> assertEquals(
                0,
                f.transferTracker()
                    .getCurrentTransferRate(101L)
            ),
            () -> assertEquals(
                0,
                f.energyChangedCalls()
                    .get()
            )
        );
    }

    @Test
    void shouldRecordCommittedNestedExtractionOnlyAfterRootCommit() {
        Fixture f = singleOutput(
            1_000L,
            500L
        );

        try (Transaction root = Transaction.openRoot()) {
            try (Transaction child = Transaction.open(root)) {
                assertEquals(
                    60,
                    f.coordinator()
                        .extract(
                            OUTPUT_A,
                            60,
                            child
                        )
                );
                child.commit();
            }
            assertEquals(
                0,
                f.transferTracker()
                    .getCurrentTransferRate(101L)
            );
            root.commit();
        }

        assertAll(
            () -> assertEquals(
                440L,
                f.energy()
                    .getStoredEnergy()
            ),
            () -> assertEquals(
                20,
                f.extractionBudget()
                    .getRemaining(100L)
            ),
            () -> assertEquals(
                60,
                f.distribution()
                    .getExtractedByPort(
                        OUTPUT_A,
                        100L
                    )
            ),
            () -> assertEquals(
                60,
                f.transferTracker()
                    .getCurrentTransferRate(101L)
            ),
            () -> assertEquals(
                1,
                f.energyChangedCalls()
                    .get()
            )
        );
    }

    @Test
    void shouldAccumulateCommittedTransfersInTrackerWithinOneTick() {
        Fixture f = singleOutput(
            1_000L,
            500L
        );

        assertEquals(
            30,
            extractCommitted(
                f,
                OUTPUT_A,
                30
            )
        );
        assertEquals(
            20,
            extractCommitted(
                f,
                OUTPUT_A,
                20
            )
        );

        assertAll(
            () -> assertEquals(
                450L,
                f.energy()
                    .getStoredEnergy()
            ),
            () -> assertEquals(
                30,
                f.extractionBudget()
                    .getRemaining(100L)
            ),
            () -> assertEquals(
                50,
                f.distribution()
                    .getExtractedByPort(
                        OUTPUT_A,
                        100L
                    )
            ),
            () -> assertEquals(
                50,
                f.transferTracker()
                    .getCurrentTransferRate(101L)
            )
        );
    }

    @Test
    void shouldStartWithFreshOutputBudgetOnNextTick() {
        Fixture f = singleOutput(
            1_000L,
            500L
        );

        assertEquals(
            80,
            extractCommitted(
                f,
                OUTPUT_A,
                80
            )
        );
        assertEquals(
            0,
            f.extractionBudget()
                .getRemaining(100L)
        );

        f.tick()
            .set(101L);
        assertEquals(
            80,
            f.extractionBudget()
                .getRemaining(101L)
        );
        assertEquals(
            50,
            extractCommitted(
                f,
                OUTPUT_A,
                50
            )
        );

        assertAll(
            () -> assertEquals(
                370L,
                f.energy()
                    .getStoredEnergy()
            ),
            () -> assertEquals(
                30,
                f.extractionBudget()
                    .getRemaining(101L)
            ),
            () -> assertEquals(
                50,
                f.distribution()
                    .getExtractedByPort(
                        OUTPUT_A,
                        101L
                    )
            ),
            () -> assertEquals(
                80,
                f.transferTracker()
                    .getCurrentTransferRate(101L)
            ),
            () -> assertEquals(
                50,
                f.transferTracker()
                    .getCurrentTransferRate(102L)
            )
        );
    }

    @Test
    void shouldKeepInputAndOutputBudgetsIndependentWhenBothDirectionsAreUsed() {
        Fixture f = singleOutput(
            1_000L,
            500L
        );

        try (Transaction tx = Transaction.openRoot()) {
            assertEquals(
                70,
                f.coordinator()
                    .insert(
                        INPUT_A,
                        70,
                        tx
                    )
            );
            tx.commit();
        }
        assertEquals(
            30,
            f.insertionBudget()
                .getRemaining(100L)
        );

        assertEquals(
            80,
            extractCommitted(
                f,
                OUTPUT_A,
                80
            )
        );
        assertAll(
            () -> assertEquals(
                490L,
                f.energy()
                    .getStoredEnergy()
            ),
            () -> assertEquals(
                30,
                f.insertionBudget()
                    .getRemaining(100L)
            ),
            () -> assertEquals(
                0,
                f.extractionBudget()
                    .getRemaining(100L)
            ),
            () -> assertEquals(
                80,
                f.transferTracker()
                    .getCurrentTransferRate(101L)
            )
        );
    }

    @Test
    void shouldRespectFairnessBetweenTwoActivePortsWhenBufferIsLow() {
        Fixture f = fixture(
            1_000L,
            40L,
            100,
            80,
            OUTPUT_A,
            OUTPUT_B
        );

        try (Transaction tx = Transaction.openRoot()) {
            f.distribution()
                .recordRequest(
                    OUTPUT_A,
                    80,
                    99L,
                    tx
                );
            f.distribution()
                .recordRequest(
                    OUTPUT_B,
                    80,
                    99L,
                    tx
                );
            tx.commit();
        }

        assertEquals(
            20,
            extractCommitted(
                f,
                OUTPUT_A,
                80
            )
        );
        assertEquals(
            20,
            extractCommitted(
                f,
                OUTPUT_B,
                80
            )
        );

        assertAll(
            () -> assertEquals(
                0L,
                f.energy()
                    .getStoredEnergy()
            ),
            () -> assertEquals(
                40,
                f.extractionBudget()
                    .getRemaining(100L)
            ),
            () -> assertEquals(
                20,
                f.distribution()
                    .getExtractedByPort(
                        OUTPUT_A,
                        100L
                    )
            ),
            () -> assertEquals(
                20,
                f.distribution()
                    .getExtractedByPort(
                        OUTPUT_B,
                        100L
                    )
            ),
            () -> assertEquals(
                40,
                f.transferTracker()
                    .getCurrentTransferRate(101L)
            )
        );
    }

    @Test
    void shouldLeaveUncommittedExtractionOutOfTransferStatistics() {
        Fixture f = singleOutput(
            1_000L,
            500L
        );

        try (Transaction tx = Transaction.openRoot()) {
            assertEquals(
                60,
                f.coordinator()
                    .extract(
                        OUTPUT_A,
                        60,
                        tx
                    )
            );
        }
        assertEquals(
            0,
            f.transferTracker()
                .getCurrentTransferRate(101L)
        );
        assertEquals(
            20,
            extractCommitted(
                f,
                OUTPUT_A,
                20
            )
        );

        assertAll(
            () -> assertEquals(
                480L,
                f.energy()
                    .getStoredEnergy()
            ),
            () -> assertEquals(
                60,
                f.extractionBudget()
                    .getRemaining(100L)
            ),
            () -> assertEquals(
                20,
                f.distribution()
                    .getExtractedByPort(
                        OUTPUT_A,
                        100L
                    )
            ),
            () -> assertEquals(
                20,
                f.transferTracker()
                    .getCurrentTransferRate(101L)
            )
        );
    }

    @Test
    void shouldExtractPushWithoutRecordingPullDemand() {
        Fixture f = singleOutput(
            1_000L,
            500L
        );
        f.distribution()
            .updatePushAvailability(
                Map.of(
                    OUTPUT_A,
                    80
                ),
                100L
            );

        assertEquals(
            45,
            extractCommitted(
                f,
                OUTPUT_A,
                45,
                EnergyTransferCoordinator.ExtractionOrigin.PUSH
            )
        );

        assertAll(
            () -> assertEquals(
                455L,
                f.energy()
                    .getStoredEnergy()
            ),
            () -> assertEquals(
                35,
                f.extractionBudget()
                    .getRemaining(100L)
            ),
            () -> assertEquals(
                0,
                f.distribution()
                    .getMaxRequestedByPort(
                        OUTPUT_A,
                        100L
                    )
            ),
            () -> assertEquals(
                45,
                f.distribution()
                    .getExtractedByPort(
                        OUTPUT_A,
                        100L
                    )
            ),
            () -> assertEquals(
                45,
                f.transferTracker()
                    .getCurrentTransferRate(101L)
            )
        );
    }

    @Test
    void shouldRecordDemandForExplicitPullOrigin() {
        Fixture f = singleOutput(
            1_000L,
            500L
        );

        assertEquals(
            35,
            extractCommitted(
                f,
                OUTPUT_A,
                35,
                EnergyTransferCoordinator.ExtractionOrigin.PULL
            )
        );

        assertAll(
            () -> assertEquals(
                35,
                f.distribution()
                    .getMaxRequestedByPort(
                        OUTPUT_A,
                        100L
                    )
            ),
            () -> assertEquals(
                35,
                f.distribution()
                    .getExtractedByPort(
                        OUTPUT_A,
                        100L
                    )
            ),
            () -> assertEquals(
                45,
                f.extractionBudget()
                    .getRemaining(100L)
            )
        );
    }

    @Test
    void shouldShareOutputBudgetWhenPushIsFollowedByPull() {
        Fixture f = singleOutput(
            1_000L,
            500L
        );
        f.distribution()
            .updatePushAvailability(
                Map.of(
                    OUTPUT_A,
                    80
                ),
                100L
            );

        assertEquals(
            50,
            extractCommitted(
                f,
                OUTPUT_A,
                50,
                EnergyTransferCoordinator.ExtractionOrigin.PUSH
            )
        );
        assertEquals(
            30,
            extractCommitted(
                f,
                OUTPUT_A,
                60
            )
        );

        assertAll(
            () -> assertEquals(
                420L,
                f.energy()
                    .getStoredEnergy()
            ),
            () -> assertEquals(
                0,
                f.extractionBudget()
                    .getRemaining(100L)
            ),
            () -> assertEquals(
                80,
                f.distribution()
                    .getExtractedByPort(
                        OUTPUT_A,
                        100L
                    )
            ),
            () -> assertEquals(
                60,
                f.distribution()
                    .getMaxRequestedByPort(
                        OUTPUT_A,
                        100L
                    )
            ),
            () -> assertEquals(
                80,
                f.transferTracker()
                    .getCurrentTransferRate(101L)
            )
        );
    }

    @Test
    void shouldShareOutputBudgetWhenPullIsFollowedByPush() {
        Fixture f = singleOutput(
            1_000L,
            500L
        );
        f.distribution()
            .updatePushAvailability(
                Map.of(
                    OUTPUT_A,
                    80
                ),
                100L
            );

        assertEquals(
            30,
            extractCommitted(
                f,
                OUTPUT_A,
                30
            )
        );
        assertEquals(
            50,
            extractCommitted(
                f,
                OUTPUT_A,
                70,
                EnergyTransferCoordinator.ExtractionOrigin.PUSH
            )
        );

        assertAll(
            () -> assertEquals(
                420L,
                f.energy()
                    .getStoredEnergy()
            ),
            () -> assertEquals(
                0,
                f.extractionBudget()
                    .getRemaining(100L)
            ),
            () -> assertEquals(
                30,
                f.distribution()
                    .getMaxRequestedByPort(
                        OUTPUT_A,
                        100L
                    )
            ),
            () -> assertEquals(
                80,
                f.distribution()
                    .getExtractedByPort(
                        OUTPUT_A,
                        100L
                    )
            ),
            () -> assertEquals(
                80,
                f.transferTracker()
                    .getCurrentTransferRate(101L)
            )
        );
    }

    @Test
    void shouldRollbackPushEnergyBudgetAndUsageWithoutTrackingTransfer() {
        Fixture f = singleOutput(
            1_000L,
            500L
        );
        f.distribution()
            .updatePushAvailability(
                Map.of(
                    OUTPUT_A,
                    80
                ),
                100L
            );

        try (Transaction tx = Transaction.openRoot()) {
            assertEquals(
                60,
                f.coordinator()
                    .extract(
                        OUTPUT_A,
                        60,
                        tx,
                        EnergyTransferCoordinator.ExtractionOrigin.PUSH
                    )
            );
            assertEquals(
                440L,
                f.energy()
                    .getStoredEnergy()
            );
            assertEquals(
                20,
                f.extractionBudget()
                    .getRemaining(100L)
            );
        }

        assertAll(
            () -> assertEquals(
                500L,
                f.energy()
                    .getStoredEnergy()
            ),
            () -> assertEquals(
                80,
                f.extractionBudget()
                    .getRemaining(100L)
            ),
            () -> assertEquals(
                0,
                f.distribution()
                    .getExtractedByPort(
                        OUTPUT_A,
                        100L
                    )
            ),
            () -> assertEquals(
                0,
                f.distribution()
                    .getMaxRequestedByPort(
                        OUTPUT_A,
                        100L
                    )
            ),
            () -> assertEquals(
                0,
                f.transferTracker()
                    .getCurrentTransferRate(101L)
            ),
            () -> assertEquals(
                0,
                f.energyChangedCalls()
                    .get()
            )
        );
    }

    @Test
    void shouldRollbackCommittedNestedPushWhenRootRollsBack() {
        Fixture f = singleOutput(
            1_000L,
            500L
        );
        f.distribution()
            .updatePushAvailability(
                Map.of(
                    OUTPUT_A,
                    80
                ),
                100L
            );

        try (Transaction root = Transaction.openRoot()) {
            try (Transaction nested = Transaction.open(root)) {
                assertEquals(
                    40,
                    f.coordinator()
                        .extract(
                            OUTPUT_A,
                            40,
                            nested,
                            EnergyTransferCoordinator.ExtractionOrigin.PUSH
                        )
                );
                nested.commit();
            }
            assertEquals(
                460L,
                f.energy()
                    .getStoredEnergy()
            );
            assertEquals(
                40,
                f.extractionBudget()
                    .getRemaining(100L)
            );
            assertEquals(
                0,
                f.transferTracker()
                    .getCurrentTransferRate(101L)
            );
        }

        assertAll(
            () -> assertEquals(
                500L,
                f.energy()
                    .getStoredEnergy()
            ),
            () -> assertEquals(
                80,
                f.extractionBudget()
                    .getRemaining(100L)
            ),
            () -> assertEquals(
                0,
                f.distribution()
                    .getExtractedByPort(
                        OUTPUT_A,
                        100L
                    )
            ),
            () -> assertEquals(
                0,
                f.transferTracker()
                    .getCurrentTransferRate(101L)
            )
        );
    }

    @Test
    void shouldRejectNullExtractionOriginBeforeChangingState() {
        Fixture f = singleOutput(
            1_000L,
            500L
        );

        try (Transaction tx = Transaction.openRoot()) {
            assertThrows(
                NullPointerException.class,
                () -> f.coordinator()
                    .extract(
                        OUTPUT_A,
                        40,
                        tx,
                        null
                    )
            );
        }

        assertAll(
            () -> assertEquals(
                500L,
                f.energy()
                    .getStoredEnergy()
            ),
            () -> assertEquals(
                80,
                f.extractionBudget()
                    .getRemaining(100L)
            ),
            () -> assertEquals(
                0,
                f.distribution()
                    .getMaxRequestedByPort(
                        OUTPUT_A,
                        100L
                    )
            )
        );
    }

    @Test
    void shouldRejectPushThroughUnavailablePort() {
        Fixture f = singleOutput(
            1_000L,
            500L
        );

        assertEquals(
            0,
            extractCommitted(
                f,
                UNKNOWN,
                50,
                EnergyTransferCoordinator.ExtractionOrigin.PUSH
            )
        );

        assertAll(
            () -> assertEquals(
                500L,
                f.energy()
                    .getStoredEnergy()
            ),
            () -> assertEquals(
                80,
                f.extractionBudget()
                    .getRemaining(100L)
            ),
            () -> assertEquals(
                0,
                f.distribution()
                    .getExtractedByPort(
                        UNKNOWN,
                        100L
                    )
            ),
            () -> assertEquals(
                0,
                f.transferTracker()
                    .getCurrentTransferRate(101L)
            )
        );
    }

    @Test
    void shouldPreserveOtherOutputPortQuotaAcrossPushAndPull() {
        Fixture f = fixture(
            1_000L,
            40L,
            100,
            80,
            OUTPUT_A,
            OUTPUT_B
        );

        try (Transaction tx = Transaction.openRoot()) {
            f.distribution()
                .recordRequest(
                    OUTPUT_A,
                    80,
                    99L,
                    tx
                );
            f.distribution()
                .recordRequest(
                    OUTPUT_B,
                    80,
                    99L,
                    tx
                );
            tx.commit();
        }

        f.distribution()
            .updatePushAvailability(
                Map.of(
                    OUTPUT_A,
                    80
                ),
                100L
            );

        assertEquals(
            20,
            extractCommitted(
                f,
                OUTPUT_A,
                80,
                EnergyTransferCoordinator.ExtractionOrigin.PUSH
            )
        );
        assertEquals(
            20,
            extractCommitted(
                f,
                OUTPUT_B,
                80
            )
        );

        assertAll(
            () -> assertEquals(
                0L,
                f.energy()
                    .getStoredEnergy()
            ),
            () -> assertEquals(
                40,
                f.extractionBudget()
                    .getRemaining(100L)
            ),
            () -> assertEquals(
                0,
                f.distribution()
                    .getMaxRequestedByPort(
                        OUTPUT_A,
                        100L
                    )
            ),
            () -> assertEquals(
                80,
                f.distribution()
                    .getMaxRequestedByPort(
                        OUTPUT_B,
                        100L
                    )
            ),
            () -> assertEquals(
                20,
                f.distribution()
                    .getExtractedByPort(
                        OUTPUT_A,
                        100L
                    )
            ),
            () -> assertEquals(
                20,
                f.distribution()
                    .getExtractedByPort(
                        OUTPUT_B,
                        100L
                    )
            )
        );
    }
}
