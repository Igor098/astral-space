package ru.greatdragonx.astralspace.common.energy.port;

import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class EnergyPortHandlerTest {

    private static final EnergyPortId PORT = new EnergyPortId("test.main");
    private static final EnergyPortId OTHER_PORT = new EnergyPortId("test.other");

    private static @NonNull EnergyPortPolicy fixedPolicy(
        EnergyPortId portId,
        EnergyPortMode mode
    )
    {
        return new FixedEnergyPortPolicy(
            Map.of(
                portId,
                mode
            ),
            ignored -> true
        );
    }

    private static @NonNull EnergyPortHandler handler(
        EnergyPortId portId,
        EnergyPortMode mode,
        RecordingOperations operations
    )
    {
        return new EnergyPortHandler(
            portId,
            fixedPolicy(
                portId,
                mode
            ),
            operations
        );
    }

    private static @NonNull EnergyPortPolicy configurablePolicy(
        AtomicReference<EnergyPortMode> mode,
        AtomicBoolean available
    )
    {
        return new ConfigurableEnergyPortPolicy(
            Map.of(
                PORT,
                EnumSet.allOf(EnergyPortMode.class)
            ),
            ignored -> mode.get(),
            ignored -> available.get()
        );
    }

    @Test
    void shouldRejectNullConstructorArguments() {
        RecordingOperations operations = new RecordingOperations();
        EnergyPortPolicy policy = fixedPolicy(
            PORT,
            EnergyPortMode.OUTPUT
        );

        assertAll(
            () -> assertThrows(
                NullPointerException.class,
                () -> new EnergyPortHandler(
                    null,
                    policy,
                    operations
                )
            ),
            () -> assertThrows(
                NullPointerException.class,
                () -> new EnergyPortHandler(
                    PORT,
                    null,
                    operations
                )
            ),
            () -> assertThrows(
                NullPointerException.class,
                () -> new EnergyPortHandler(
                    PORT,
                    policy,
                    null
                )
            )
        );
    }

    @Test
    void shouldReadLiveEnergyAmountAndCapacityFromOperations() {
        RecordingOperations operations = new RecordingOperations();
        operations.storedEnergy = 6_000L;
        operations.capacity = 10_000L;

        EnergyPortHandler handler = handler(
            PORT,
            EnergyPortMode.DISABLED,
            operations
        );

        assertEquals(
            6_000L,
            handler.getAmountAsLong()
        );
        assertEquals(
            10_000L,
            handler.getCapacityAsLong()
        );

        operations.storedEnergy = (long) Integer.MAX_VALUE + 123L;
        operations.capacity = (long) Integer.MAX_VALUE + 1_000L;

        assertEquals(
            (long) Integer.MAX_VALUE + 123L,
            handler.getAmountAsLong()
        );
        assertEquals(
            (long) Integer.MAX_VALUE + 1_000L,
            handler.getCapacityAsLong()
        );
        assertEquals(
            0,
            operations.insertCalls
        );
        assertEquals(
            0,
            operations.extractCalls
        );
    }

    @Test
    void shouldDelegateInsertWithExactPortAmountAndTransaction() {
        RecordingOperations operations = new RecordingOperations();
        operations.insertLimit = 32;
        EnergyPortHandler handler = handler(
            PORT,
            EnergyPortMode.INPUT,
            operations
        );

        try (Transaction tx = Transaction.openRoot()) {
            int accepted = handler.insert(
                100,
                tx
            );

            assertEquals(
                32,
                accepted
            ); // Partial acceptance is the operations layer's decision.
            assertEquals(
                1,
                operations.insertCalls
            );
            assertEquals(
                0,
                operations.extractCalls
            );
            assertEquals(
                PORT,
                operations.lastInsertPort
            );
            assertEquals(
                100,
                operations.lastInsertAmount
            );
            assertSame(
                tx,
                operations.lastInsertTransaction
            );
        }
    }

    @Test
    void shouldDelegateExtractWithExactPortAmountAndTransaction() {
        RecordingOperations operations = new RecordingOperations();
        operations.extractLimit = 45;
        EnergyPortHandler handler = handler(
            PORT,
            EnergyPortMode.OUTPUT,
            operations
        );

        try (Transaction tx = Transaction.openRoot()) {
            int extracted = handler.extract(
                80,
                tx
            );

            assertEquals(
                45,
                extracted
            );
            assertEquals(
                1,
                operations.extractCalls
            );
            assertEquals(
                0,
                operations.insertCalls
            );
            assertEquals(
                PORT,
                operations.lastExtractPort
            );
            assertEquals(
                80,
                operations.lastExtractAmount
            );
            assertSame(
                tx,
                operations.lastExtractTransaction
            );
        }
    }

    @Test
    void shouldDenyExtractionFromInputPort() {
        RecordingOperations operations = new RecordingOperations();
        EnergyPortHandler handler = handler(
            PORT,
            EnergyPortMode.INPUT,
            operations
        );

        try (Transaction tx = Transaction.openRoot()) {
            assertEquals(
                0,
                handler.extract(
                    25,
                    tx
                )
            );
        }

        assertEquals(
            0,
            operations.insertCalls
        );
        assertEquals(
            0,
            operations.extractCalls
        );
    }

    @Test
    void shouldDenyInsertionIntoOutputPort() {
        RecordingOperations operations = new RecordingOperations();
        EnergyPortHandler handler = handler(
            PORT,
            EnergyPortMode.OUTPUT,
            operations
        );

        try (Transaction tx = Transaction.openRoot()) {
            assertEquals(
                0,
                handler.insert(
                    25,
                    tx
                )
            );
        }

        assertEquals(
            0,
            operations.insertCalls
        );
        assertEquals(
            0,
            operations.extractCalls
        );
    }

    @Test
    void shouldDenyBothOperationsForDisabledPort() {
        RecordingOperations operations = new RecordingOperations();
        EnergyPortHandler handler = handler(
            PORT,
            EnergyPortMode.DISABLED,
            operations
        );

        try (Transaction tx = Transaction.openRoot()) {
            assertEquals(
                0,
                handler.insert(
                    50,
                    tx
                )
            );
            assertEquals(
                0,
                handler.extract(
                    50,
                    tx
                )
            );
        }

        assertEquals(
            0,
            operations.insertCalls
        );
        assertEquals(
            0,
            operations.extractCalls
        );
    }

    @Test
    void shouldDenyBothOperationsForUnknownPort() {
        RecordingOperations operations = new RecordingOperations();
        EnergyPortPolicy policy = fixedPolicy(
            OTHER_PORT,
            EnergyPortMode.OUTPUT
        );
        EnergyPortHandler handler = new EnergyPortHandler(
            PORT,
            policy,
            operations
        );

        try (Transaction tx = Transaction.openRoot()) {
            assertEquals(
                0,
                handler.insert(
                    50,
                    tx
                )
            );
            assertEquals(
                0,
                handler.extract(
                    50,
                    tx
                )
            );
        }

        assertEquals(
            0,
            operations.insertCalls
        );
        assertEquals(
            0,
            operations.extractCalls
        );
    }

    @Test
    void shouldIgnoreZeroInsertWithoutDelegating() {
        RecordingOperations operations = new RecordingOperations();
        EnergyPortHandler handler = handler(
            PORT,
            EnergyPortMode.INPUT,
            operations
        );

        try (Transaction tx = Transaction.openRoot()) {
            assertEquals(
                0,
                handler.insert(
                    0,
                    tx
                )
            );
        }

        assertEquals(
            0,
            operations.insertCalls
        );
        assertEquals(
            0,
            operations.extractCalls
        );
    }

    @Test
    void shouldIgnoreZeroExtractWithoutDelegating() {
        RecordingOperations operations = new RecordingOperations();
        EnergyPortHandler handler = handler(
            PORT,
            EnergyPortMode.OUTPUT,
            operations
        );

        try (Transaction tx = Transaction.openRoot()) {
            assertEquals(
                0,
                handler.extract(
                    0,
                    tx
                )
            );
        }

        assertEquals(
            0,
            operations.insertCalls
        );
        assertEquals(
            0,
            operations.extractCalls
        );
    }

    @Test
    void shouldRejectNegativeInsertEvenIfPortIsDisabled() {
        RecordingOperations operations = new RecordingOperations();
        EnergyPortHandler handler = handler(
            PORT,
            EnergyPortMode.DISABLED,
            operations
        );

        try (Transaction tx = Transaction.openRoot()) {
            assertThrows(
                IllegalArgumentException.class,
                () -> handler.insert(
                    -1,
                    tx
                )
            );
        }

        assertEquals(
            0,
            operations.insertCalls
        );
    }

    @Test
    void shouldRejectNegativeExtractEvenIfPortIsDisabled() {
        RecordingOperations operations = new RecordingOperations();
        EnergyPortHandler handler = handler(
            PORT,
            EnergyPortMode.DISABLED,
            operations
        );

        try (Transaction tx = Transaction.openRoot()) {
            assertThrows(
                IllegalArgumentException.class,
                () -> handler.extract(
                    -1,
                    tx
                )
            );
        }

        assertEquals(
            0,
            operations.extractCalls
        );
    }

    @Test
    void shouldApplyCurrentModeWithoutRecreatingExistingHandler() {
        RecordingOperations operations = new RecordingOperations();
        AtomicReference<EnergyPortMode> mode = new AtomicReference<>(EnergyPortMode.INPUT);
        AtomicBoolean available = new AtomicBoolean(true);
        EnergyPortPolicy policy = configurablePolicy(
            mode,
            available
        );
        EnergyPortHandler handler = new EnergyPortHandler(
            PORT,
            policy,
            operations
        );

        try (Transaction tx = Transaction.openRoot()) {
            assertEquals(
                50,
                handler.insert(
                    50,
                    tx
                )
            );
            assertEquals(
                0,
                handler.extract(
                    50,
                    tx
                )
            );
        }

        // Reconfigure outside a transfer transaction. Keep the same handler.
        mode.set(EnergyPortMode.OUTPUT);
        try (Transaction tx = Transaction.openRoot()) {
            assertEquals(
                0,
                handler.insert(
                    50,
                    tx
                )
            );
            assertEquals(
                50,
                handler.extract(
                    50,
                    tx
                )
            );
        }

        mode.set(EnergyPortMode.DISABLED);
        try (Transaction tx = Transaction.openRoot()) {
            assertEquals(
                0,
                handler.insert(
                    50,
                    tx
                )
            );
            assertEquals(
                0,
                handler.extract(
                    50,
                    tx
                )
            );
        }

        assertEquals(
            1,
            operations.insertCalls
        );
        assertEquals(
            1,
            operations.extractCalls
        );
    }

    @Test
    void shouldRespectLivePortAvailabilityWithoutRecreatingHandler() {
        RecordingOperations operations = new RecordingOperations();
        AtomicReference<EnergyPortMode> mode = new AtomicReference<>(EnergyPortMode.OUTPUT);
        AtomicBoolean available = new AtomicBoolean(false);
        EnergyPortHandler handler = new EnergyPortHandler(
            PORT,
            configurablePolicy(
                mode,
                available
            ),
            operations
        );

        try (Transaction tx = Transaction.openRoot()) {
            assertEquals(
                0,
                handler.extract(
                    20,
                    tx
                )
            );
        }

        available.set(true);
        try (Transaction tx = Transaction.openRoot()) {
            assertEquals(
                20,
                handler.extract(
                    20,
                    tx
                )
            );
        }

        available.set(false);
        try (Transaction tx = Transaction.openRoot()) {
            assertEquals(
                0,
                handler.extract(
                    20,
                    tx
                )
            );
        }

        assertEquals(
            1,
            operations.extractCalls
        );
    }

    @Test
    void shouldKeepDistinctPortIdentitiesWithSharedOperations() {
        RecordingOperations operations = new RecordingOperations();
        EnergyPortHandler first = handler(
            PORT,
            EnergyPortMode.OUTPUT,
            operations
        );
        EnergyPortHandler second = handler(
            OTHER_PORT,
            EnergyPortMode.OUTPUT,
            operations
        );

        try (Transaction tx = Transaction.openRoot()) {
            assertEquals(
                10,
                first.extract(
                    10,
                    tx
                )
            );
            assertEquals(
                PORT,
                operations.lastExtractPort
            );
            assertSame(
                tx,
                operations.lastExtractTransaction
            );

            assertEquals(
                20,
                second.extract(
                    20,
                    tx
                )
            );
            assertEquals(
                OTHER_PORT,
                operations.lastExtractPort
            );
            assertSame(
                tx,
                operations.lastExtractTransaction
            );
        }

        assertEquals(
            2,
            operations.extractCalls
        );
    }

    @Test
    void shouldPropagateFailuresFromOperations() {
        RecordingOperations operations = new RecordingOperations();
        operations.extractFailure = new IllegalStateException("Test failure");
        EnergyPortHandler handler = handler(
            PORT,
            EnergyPortMode.OUTPUT,
            operations
        );

        try (Transaction tx = Transaction.openRoot()) {
            IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> handler.extract(
                    25,
                    tx
                )
            );
            assertSame(
                operations.extractFailure,
                failure
            );
        }

        assertEquals(
            1,
            operations.extractCalls
        );
    }

    /**
     * Spy with deterministic partial-transfer behavior; no mocking library required.
     */
    private static final class RecordingOperations implements EnergyPortOperations {
        long storedEnergy = 0L;
        long capacity = 10_000L;

        int insertLimit = Integer.MAX_VALUE;
        int extractLimit = Integer.MAX_VALUE;
        RuntimeException extractFailure;

        int insertCalls;
        int extractCalls;

        EnergyPortId lastInsertPort;
        int lastInsertAmount;
        TransactionContext lastInsertTransaction;

        EnergyPortId lastExtractPort;
        int lastExtractAmount;
        TransactionContext lastExtractTransaction;

        @Override
        public long getStoredEnergy() {
            return storedEnergy;
        }

        @Override
        public long getCapacity() {
            return capacity;
        }

        @Override
        public int insert(
            EnergyPortId portId,
            int amount,
            TransactionContext transaction
        )
        {
            insertCalls++;
            lastInsertPort = portId;
            lastInsertAmount = amount;
            lastInsertTransaction = transaction;
            return Math.min(
                amount,
                insertLimit
            );
        }

        @Override
        public int extract(
            EnergyPortId portId,
            int amount,
            TransactionContext transaction
        )
        {
            extractCalls++;
            lastExtractPort = portId;
            lastExtractAmount = amount;
            lastExtractTransaction = transaction;
            if (extractFailure != null) {
                throw extractFailure;
            }
            return Math.min(
                amount,
                extractLimit
            );
        }
    }
}
