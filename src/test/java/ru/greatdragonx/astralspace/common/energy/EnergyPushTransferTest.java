package ru.greatdragonx.astralspace.common.energy;

import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.Test;
import ru.greatdragonx.astralspace.common.energy.component.EnergyComponent;
import ru.greatdragonx.astralspace.common.energy.port.EnergyPortId;
import ru.greatdragonx.astralspace.common.energy.port.EnergyPortMode;
import ru.greatdragonx.astralspace.common.energy.port.EnergyPortPolicy;
import ru.greatdragonx.astralspace.common.energy.port.FixedEnergyPortPolicy;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class EnergyPushTransferTest {
    private static final EnergyPortId OUTPUT = new EnergyPortId("test.output");
    private static final EnergyPortId INPUT = new EnergyPortId("test.input");
    private static final EnergyPortId DISABLED = new EnergyPortId("test.disabled");
    private static final EnergyPortId UNKNOWN = new EnergyPortId("test.unknown");

    private record Fixture(EnergyComponent source,
        EnergyDistributionManager distribution,
        EnergyTransferBudget insertionBudget,
        EnergyTransferBudget extractionBudget,
        EnergyTransferTracker tracker,
        EnergyTransferCoordinator coordinator,
        EnergyPortPolicy policy,
        EnergyPushTransfer transfer,
        AtomicLong tick,
        AtomicBoolean available,
        AtomicInteger sourceChanges) {}

    private static @NonNull Fixture fixture(
        long capacity,
        long initiallyStored
    )
    {
        AtomicLong tick = new AtomicLong(100L);
        AtomicBoolean available = new AtomicBoolean(true);
        AtomicInteger sourceChanges = new AtomicInteger();
        EnergyComponent source = new EnergyComponent(
            capacity,
            sourceChanges::incrementAndGet
        );
        source.restoreStoredEnergy(initiallyStored);

        EnergyDistributionManager distribution = new EnergyDistributionManager(80);
        distribution.updateAvailablePorts(List.of(OUTPUT));

        EnergyTransferBudget insertion = new EnergyTransferBudget(100);
        EnergyTransferBudget extraction = new EnergyTransferBudget(80);
        EnergyTransferTracker tracker = new EnergyTransferTracker();

        EnergyTransferCoordinator coordinator = new EnergyTransferCoordinator(
            source,
            distribution,
            insertion,
            extraction,
            tracker,
            tick::get
        );
        EnergyPortPolicy policy = new FixedEnergyPortPolicy(
            Map.of(
                OUTPUT,
                EnergyPortMode.OUTPUT,
                INPUT,
                EnergyPortMode.INPUT,
                DISABLED,
                EnergyPortMode.DISABLED
            ),
            ignored -> available.get()
        );
        EnergyPushTransfer transfer = new EnergyPushTransfer(
            coordinator,
            policy
        );

        return new Fixture(
            source,
            distribution,
            insertion,
            extraction,
            tracker,
            coordinator,
            policy,
            transfer,
            tick,
            available,
            sourceChanges
        );
    }

    private static final class Receiver implements EnergyHandler {
        private final EnergyComponent energy;
        private final int perCallLimit;
        private final Runnable onInsert;
        private final AtomicInteger changes = new AtomicInteger();
        private int insertCalls = 0;

        private Receiver(
            long capacity,
            long initial,
            int perCallLimit,
            Runnable onInsert
        )
        {
            this.energy = new EnergyComponent(
                capacity,
                changes::incrementAndGet
            );
            this.energy.restoreStoredEnergy(initial);
            this.perCallLimit = perCallLimit;
            this.onInsert = onInsert;
        }

        private static @NonNull Receiver accepting(long capacity) {
            return new Receiver(
                capacity,
                0,
                Integer.MAX_VALUE,
                () -> {
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
        public int insert(
            int amount,
            @NonNull TransactionContext transaction
        )
        {
            insertCalls++;
            onInsert.run();
            return energy.insertEnergy(
                Math.min(
                    amount,
                    perCallLimit
                ),
                transaction
            );
        }

        @Override
        public int extract(
            int amount,
            @NonNull TransactionContext transaction
        )
        {
            return 0;
        }
    }

    private static int pushCommitted(
        Fixture fixture,
        EnergyPortId port,
        Receiver receiver,
        int amount
    )
    {
        try (Transaction root = Transaction.openRoot()) {
            int transferred = fixture.transfer()
                .push(
                    port,
                    receiver,
                    amount,
                    root
                );
            root.commit();
            return transferred;
        }
    }

    @Test
    void shouldRejectNullConstructorDependencies() {
        Fixture f = fixture(
            1000,
            500
        );
        assertThrows(
            NullPointerException.class,
            () -> new EnergyPushTransfer(
                null,
                f.policy()
            )
        );
        assertThrows(
            NullPointerException.class,
            () -> new EnergyPushTransfer(
                f.coordinator(),
                null
            )
        );
    }

    @Test
    void shouldRejectNullArgumentsWithoutModifyingEnergy() {
        Fixture f = fixture(
            1000,
            500
        );
        Receiver receiver = Receiver.accepting(1000);

        try (Transaction tx = Transaction.openRoot()) {
            assertAll(
                () -> assertThrows(
                    NullPointerException.class,
                    () -> f.transfer()
                        .push(
                            null,
                            receiver,
                            10,
                            tx
                        )
                ),
                () -> assertThrows(
                    NullPointerException.class,
                    () -> f.transfer()
                        .push(
                            OUTPUT,
                            null,
                            10,
                            tx
                        )
                ),
                () -> assertThrows(
                    NullPointerException.class,
                    () -> f.transfer()
                        .push(
                            OUTPUT,
                            receiver,
                            10,
                            null
                        )
                )
            );
        }

        assertEquals(
            500L,
            f.source()
                .getStoredEnergy()
        );
        assertEquals(
            0L,
            receiver.getAmountAsLong()
        );
        assertEquals(
            80,
            f.extractionBudget()
                .getRemaining(100L)
        );
    }

    @Test
    void shouldRejectNegativeAmountBeforeAnyTransfer() {
        Fixture f = fixture(
            1000,
            500
        );
        Receiver receiver = Receiver.accepting(1000);

        try (Transaction tx = Transaction.openRoot()) {
            assertThrows(
                IllegalArgumentException.class,
                () -> f.transfer()
                    .push(
                        OUTPUT,
                        receiver,
                        -1,
                        tx
                    )
            );
        }

        assertEquals(
            500L,
            f.source()
                .getStoredEnergy()
        );
        assertEquals(
            0,
            receiver.insertCalls
        );
    }

    @Test
    void shouldIgnoreZeroAmountWithoutTouchingTarget() {
        Fixture f = fixture(
            1000,
            500
        );
        Receiver receiver = Receiver.accepting(1000);

        try (Transaction tx = Transaction.openRoot()) {
            assertEquals(
                0,
                f.transfer()
                    .push(
                        OUTPUT,
                        receiver,
                        0,
                        tx
                    )
            );
            tx.commit();
        }

        assertEquals(
            500L,
            f.source()
                .getStoredEnergy()
        );
        assertEquals(
            0,
            receiver.insertCalls
        );
        assertEquals(
            0,
            f.distribution()
                .getExtractedByPort(
                    OUTPUT,
                    100L
                )
        );
    }

    @Test
    void shouldMoveEnergyToTargetAndRecordOnlyAfterRootCommit() {
        Fixture f = fixture(
            1000,
            500
        );
        Receiver receiver = Receiver.accepting(1000);

        try (Transaction root = Transaction.openRoot()) {
            assertEquals(
                60,
                f.transfer()
                    .push(
                        OUTPUT,
                        receiver,
                        60,
                        root
                    )
            );
            assertAll(
                () -> assertEquals(
                    440L,
                    f.source()
                        .getStoredEnergy()
                ),
                () -> assertEquals(
                    60L,
                    receiver.getAmountAsLong()
                ),
                () -> assertEquals(
                    20,
                    f.extractionBudget()
                        .getRemaining(100L)
                ),
                () -> assertEquals(
                    100,
                    f.insertionBudget()
                        .getRemaining(100L)
                ),
                () -> assertEquals(
                    60,
                    f.distribution()
                        .getExtractedByPort(
                            OUTPUT,
                            100L
                        )
                ),
                () -> assertEquals(
                    0,
                    f.distribution()
                        .getMaxRequestedByPort(
                            OUTPUT,
                            100L
                        )
                ),
                () -> assertEquals(
                    0,
                    f.tracker()
                        .getCurrentTransferRate(101L)
                ),
                () -> assertEquals(
                    0,
                    f.sourceChanges()
                        .get()
                ),
                () -> assertEquals(
                    0,
                    receiver.changes.get()
                )
            );
            root.commit();
        }

        assertAll(
            () -> assertEquals(
                440L,
                f.source()
                    .getStoredEnergy()
            ),
            () -> assertEquals(
                60L,
                receiver.getAmountAsLong()
            ),
            () -> assertEquals(
                60,
                f.tracker()
                    .getCurrentTransferRate(101L)
            ),
            () -> assertEquals(
                1,
                f.sourceChanges()
                    .get()
            ),
            () -> assertEquals(
                1,
                receiver.changes.get()
            )
        );
    }

    @Test
    void shouldMoveOnlyWhatTargetCanFit() {
        Fixture f = fixture(
            1000,
            500
        );
        Receiver receiver = new Receiver(
            100,
            95,
            Integer.MAX_VALUE,
            () -> {
            }
        );

        assertEquals(
            5,
            pushCommitted(
                f,
                OUTPUT,
                receiver,
                80
            )
        );
        assertAll(
            () -> assertEquals(
                495L,
                f.source()
                    .getStoredEnergy()
            ),
            () -> assertEquals(
                100L,
                receiver.getAmountAsLong()
            ),
            () -> assertEquals(
                75,
                f.extractionBudget()
                    .getRemaining(100L)
            ),
            () -> assertEquals(
                5,
                f.distribution()
                    .getExtractedByPort(
                        OUTPUT,
                        100L
                    )
            ),
            () -> assertEquals(
                5,
                f.tracker()
                    .getCurrentTransferRate(101L)
            )
        );
    }

    @Test
    void shouldMoveOnlyWhatTargetAcceptsPerCall() {
        Fixture f = fixture(
            1000,
            500
        );
        Receiver receiver = new Receiver(
            1000,
            0,
            12,
            () -> {
            }
        );

        assertEquals(
            12,
            pushCommitted(
                f,
                OUTPUT,
                receiver,
                80
            )
        );
        assertAll(
            () -> assertEquals(
                488L,
                f.source()
                    .getStoredEnergy()
            ),
            () -> assertEquals(
                12L,
                receiver.getAmountAsLong()
            ),
            () -> assertEquals(
                68,
                f.extractionBudget()
                    .getRemaining(100L)
            )
        );
    }

    @Test
    void shouldNotTransferWhenTargetIsFull() {
        Fixture f = fixture(
            1000,
            500
        );
        Receiver receiver = new Receiver(
            100,
            100,
            Integer.MAX_VALUE,
            () -> {
            }
        );

        assertEquals(
            0,
            pushCommitted(
                f,
                OUTPUT,
                receiver,
                80
            )
        );
        assertAll(
            () -> assertEquals(
                500L,
                f.source()
                    .getStoredEnergy()
            ),
            () -> assertEquals(
                100L,
                receiver.getAmountAsLong()
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
                        OUTPUT,
                        100L
                    )
            ),
            () -> assertEquals(
                0,
                f.tracker()
                    .getCurrentTransferRate(101L)
            )
        );
    }

    @Test
    void shouldNotTransferWhenSourceIsEmpty() {
        Fixture f = fixture(
            1000,
            0
        );
        Receiver receiver = Receiver.accepting(1000);

        assertEquals(
            0,
            pushCommitted(
                f,
                OUTPUT,
                receiver,
                80
            )
        );
        assertEquals(
            0L,
            receiver.getAmountAsLong()
        );
        assertEquals(
            80,
            f.extractionBudget()
                .getRemaining(100L)
        );
        assertEquals(
            0,
            f.tracker()
                .getCurrentTransferRate(101L)
        );
    }

    @Test
    void shouldRejectInputDisabledAndUnknownPorts() {
        Fixture f = fixture(
            1000,
            500
        );
        Receiver receiver = Receiver.accepting(1000);

        assertEquals(
            0,
            pushCommitted(
                f,
                INPUT,
                receiver,
                40
            )
        );
        assertEquals(
            0,
            pushCommitted(
                f,
                DISABLED,
                receiver,
                40
            )
        );
        assertEquals(
            0,
            pushCommitted(
                f,
                UNKNOWN,
                receiver,
                40
            )
        );

        assertAll(
            () -> assertEquals(
                500L,
                f.source()
                    .getStoredEnergy()
            ),
            () -> assertEquals(
                0L,
                receiver.getAmountAsLong()
            ),
            () -> assertEquals(
                80,
                f.extractionBudget()
                    .getRemaining(100L)
            ),
            () -> assertEquals(
                0,
                receiver.insertCalls
            )
        );
    }

    @Test
    void shouldRespectAvailabilityChangesWithoutRecreatingTransfer() {
        Fixture f = fixture(
            1000,
            500
        );
        Receiver receiver = Receiver.accepting(1000);

        f.available()
            .set(false);
        assertEquals(
            0,
            pushCommitted(
                f,
                OUTPUT,
                receiver,
                20
            )
        );
        f.available()
            .set(true);
        assertEquals(
            20,
            pushCommitted(
                f,
                OUTPUT,
                receiver,
                20
            )
        );
        f.available()
            .set(false);
        assertEquals(
            0,
            pushCommitted(
                f,
                OUTPUT,
                receiver,
                20
            )
        );

        assertAll(
            () -> assertEquals(
                480L,
                f.source()
                    .getStoredEnergy()
            ),
            () -> assertEquals(
                20L,
                receiver.getAmountAsLong()
            ),
            () -> assertEquals(
                60,
                f.extractionBudget()
                    .getRemaining(100L)
            ),
            () -> assertEquals(
                20,
                f.tracker()
                    .getCurrentTransferRate(101L)
            )
        );
    }

    @Test
    void shouldShareOutputBudgetAcrossPushOperations() {
        Fixture f = fixture(
            1000,
            500
        );
        Receiver a = Receiver.accepting(1000);
        Receiver b = Receiver.accepting(1000);

        assertEquals(
            50,
            pushCommitted(
                f,
                OUTPUT,
                a,
                50
            )
        );
        assertEquals(
            30,
            pushCommitted(
                f,
                OUTPUT,
                b,
                50
            )
        );
        assertEquals(
            0,
            pushCommitted(
                f,
                OUTPUT,
                b,
                10
            )
        );

        assertAll(
            () -> assertEquals(
                420L,
                f.source()
                    .getStoredEnergy()
            ),
            () -> assertEquals(
                50L,
                a.getAmountAsLong()
            ),
            () -> assertEquals(
                30L,
                b.getAmountAsLong()
            ),
            () -> assertEquals(
                0,
                f.extractionBudget()
                    .getRemaining(100L)
            ),
            () -> assertEquals(
                80,
                f.tracker()
                    .getCurrentTransferRate(101L)
            )
        );
    }

    @Test
    void shouldShareOutputBudgetBetweenPushThenPull() {
        Fixture f = fixture(
            1000,
            500
        );
        Receiver receiver = Receiver.accepting(1000);

        assertEquals(
            50,
            pushCommitted(
                f,
                OUTPUT,
                receiver,
                50
            )
        );
        try (Transaction tx = Transaction.openRoot()) {
            assertEquals(
                30,
                f.coordinator()
                    .extract(
                        OUTPUT,
                        80,
                        tx
                    )
            );
            tx.commit();
        }

        assertAll(
            () -> assertEquals(
                420L,
                f.source()
                    .getStoredEnergy()
            ),
            () -> assertEquals(
                50L,
                receiver.getAmountAsLong()
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
                        OUTPUT,
                        100L
                    )
            ),
            () -> assertEquals(
                80,
                f.distribution()
                    .getMaxRequestedByPort(
                        OUTPUT,
                        100L
                    )
            ),
            () -> assertEquals(
                80,
                f.tracker()
                    .getCurrentTransferRate(101L)
            )
        );
    }

    @Test
    void shouldShareOutputBudgetBetweenPullThenPush() {
        Fixture f = fixture(
            1000,
            500
        );
        Receiver receiver = Receiver.accepting(1000);

        try (Transaction tx = Transaction.openRoot()) {
            assertEquals(
                25,
                f.coordinator()
                    .extract(
                        OUTPUT,
                        25,
                        tx
                    )
            );
            tx.commit();
        }
        assertEquals(
            55,
            pushCommitted(
                f,
                OUTPUT,
                receiver,
                80
            )
        );

        assertAll(
            () -> assertEquals(
                420L,
                f.source()
                    .getStoredEnergy()
            ),
            () -> assertEquals(
                55L,
                receiver.getAmountAsLong()
            ),
            () -> assertEquals(
                0,
                f.extractionBudget()
                    .getRemaining(100L)
            ),
            () -> assertEquals(
                25,
                f.distribution()
                    .getMaxRequestedByPort(
                        OUTPUT,
                        100L
                    )
            ),
            () -> assertEquals(
                80,
                f.tracker()
                    .getCurrentTransferRate(101L)
            )
        );
    }

    @Test
    void shouldRollBackSourceTargetBudgetAndStatisticsWithRoot() {
        Fixture f = fixture(
            1000,
            500
        );
        Receiver receiver = Receiver.accepting(1000);

        try (Transaction root = Transaction.openRoot()) {
            assertEquals(
                60,
                f.transfer()
                    .push(
                        OUTPUT,
                        receiver,
                        60,
                        root
                    )
            );
            assertEquals(
                440L,
                f.source()
                    .getStoredEnergy()
            );
            assertEquals(
                60L,
                receiver.getAmountAsLong()
            );
        }

        assertAll(
            () -> assertEquals(
                500L,
                f.source()
                    .getStoredEnergy()
            ),
            () -> assertEquals(
                0L,
                receiver.getAmountAsLong()
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
                        OUTPUT,
                        100L
                    )
            ),
            () -> assertEquals(
                0,
                f.distribution()
                    .getMaxRequestedByPort(
                        OUTPUT,
                        100L
                    )
            ),
            () -> assertEquals(
                0,
                f.tracker()
                    .getCurrentTransferRate(101L)
            ),
            () -> assertEquals(
                0,
                f.sourceChanges()
                    .get()
            ),
            () -> assertEquals(
                0,
                receiver.changes.get()
            )
        );
    }

    @Test
    void shouldRollbackCommittedChildTransferIfRootRollsBack() {
        Fixture f = fixture(
            1000,
            500
        );
        Receiver receiver = Receiver.accepting(1000);

        try (Transaction root = Transaction.openRoot()) {
            try (Transaction child = Transaction.open(root)) {
                assertEquals(
                    70,
                    f.transfer()
                        .push(
                            OUTPUT,
                            receiver,
                            70,
                            child
                        )
                );
                child.commit();
            }
            assertEquals(
                430L,
                f.source()
                    .getStoredEnergy()
            );
            assertEquals(
                70L,
                receiver.getAmountAsLong()
            );
            assertEquals(
                0,
                f.tracker()
                    .getCurrentTransferRate(101L)
            );
        }

        assertAll(
            () -> assertEquals(
                500L,
                f.source()
                    .getStoredEnergy()
            ),
            () -> assertEquals(
                0L,
                receiver.getAmountAsLong()
            ),
            () -> assertEquals(
                80,
                f.extractionBudget()
                    .getRemaining(100L)
            ),
            () -> assertEquals(
                0,
                f.tracker()
                    .getCurrentTransferRate(101L)
            ),
            () -> assertEquals(
                0,
                f.sourceChanges()
                    .get()
            ),
            () -> assertEquals(
                0,
                receiver.changes.get()
            )
        );
    }

    @Test
    void shouldCommitNestedTransferOnlyAfterRootCommit() {
        Fixture f = fixture(
            1000,
            500
        );
        Receiver receiver = Receiver.accepting(1000);

        try (Transaction root = Transaction.openRoot()) {
            try (Transaction child = Transaction.open(root)) {
                assertEquals(
                    40,
                    f.transfer()
                        .push(
                            OUTPUT,
                            receiver,
                            40,
                            child
                        )
                );
                child.commit();
            }

            assertEquals(
                0,
                f.tracker()
                    .getCurrentTransferRate(101L)
            );
            assertEquals(
                0,
                f.sourceChanges()
                    .get()
            );
            assertEquals(
                0,
                receiver.changes.get()
            );
            root.commit();
        }

        assertAll(
            () -> assertEquals(
                460L,
                f.source()
                    .getStoredEnergy()
            ),
            () -> assertEquals(
                40L,
                receiver.getAmountAsLong()
            ),
            () -> assertEquals(
                40,
                f.tracker()
                    .getCurrentTransferRate(101L)
            ),
            () -> assertEquals(
                1,
                f.sourceChanges()
                    .get()
            ),
            () -> assertEquals(
                1,
                receiver.changes.get()
            )
        );
    }

    @Test
    void shouldRollBackBothSidesIfOutputIsDisabledAfterSimulation() {
        Fixture f = fixture(
            1000,
            500
        );

        Receiver receiver = new Receiver(
            1000,
            0,
            Integer.MAX_VALUE,
            () -> f.available()
                .set(false)
        );

        assertEquals(
            0,
            pushCommitted(
                f,
                OUTPUT,
                receiver,
                40
            )
        );

        assertAll(
            () -> assertEquals(
                500L,
                f.source()
                    .getStoredEnergy()
            ),
            () -> assertEquals(
                0L,
                receiver.getAmountAsLong()
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
                        OUTPUT,
                        100L
                    )
            ),
            () -> assertEquals(
                0,
                f.tracker()
                    .getCurrentTransferRate(101L)
            ),
            () -> assertEquals(
                0,
                f.sourceChanges()
                    .get()
            ),
            () -> assertEquals(
                0,
                receiver.changes.get()
            )
        );
    }

    @Test
    void shouldHandleLongCapacityAtSource() {
        long large = (long) Integer.MAX_VALUE + 5_000L;
        Fixture f = fixture(
            large + 1000L,
            large
        );
        Receiver receiver = Receiver.accepting(1000);

        assertEquals(
            80,
            pushCommitted(
                f,
                OUTPUT,
                receiver,
                80
            )
        );
        assertEquals(
            large - 80L,
            f.source()
                .getStoredEnergy()
        );
        assertEquals(
            80L,
            receiver.getAmountAsLong()
        );
    }

    @Test
    void shouldResetOutputBudgetAtNextTick() {
        Fixture f = fixture(
            1000,
            500
        );
        Receiver receiver = Receiver.accepting(1000);

        assertEquals(
            80,
            pushCommitted(
                f,
                OUTPUT,
                receiver,
                80
            )
        );
        f.tick()
            .set(101L);
        assertEquals(
            80,
            pushCommitted(
                f,
                OUTPUT,
                receiver,
                80
            )
        );

        assertAll(
            () -> assertEquals(
                340L,
                f.source()
                    .getStoredEnergy()
            ),
            () -> assertEquals(
                160L,
                receiver.getAmountAsLong()
            ),
            () -> assertEquals(
                0,
                f.extractionBudget()
                    .getRemaining(101L)
            ),
            () -> assertEquals(
                80,
                f.tracker()
                    .getCurrentTransferRate(102L)
            )
        );
    }
}
