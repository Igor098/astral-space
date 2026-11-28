package ru.greatdragonx.astralspace.common.energy.port;

import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.Test;

import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;

class EnergyEndpointHandlerTest {
    @Test
    void shouldRejectNullDelegate() {
        assertThrows(NullPointerException.class, () -> new EnergyEndpointHandler(null, () -> true));
    }

    @Test
    void shouldRejectNullValidity() {
        assertThrows(
            NullPointerException.class,
            () -> new EnergyEndpointHandler(new RecordingHandler(), null)
        );
    }

    @Test
    void shouldNotAccessDependenciesDuringConstruction() {
        Fixture f = new Fixture();
        f.assertNoInteractions();
    }

    @Test
    void shouldReadLiveAmountWhenValid() {
        Fixture f = new Fixture();
        f.delegate.amount = 6_000L;
        assertEquals(6_000L, f.handler.getAmountAsLong());
        f.delegate.amount = (long) Integer.MAX_VALUE + 123L;
        assertEquals(f.delegate.amount, f.handler.getAmountAsLong());
        assertEquals(2, f.validity.calls);
        assertEquals(2, f.delegate.amountCalls);
        assertEquals(2, f.delegate.totalCalls());
    }

    @Test
    void shouldReturnZeroAmountWithoutDelegatingWhenInvalid() {
        Fixture f = new Fixture();
        f.validity.valid = false;
        assertEquals(0L, f.handler.getAmountAsLong());
        assertEquals(1, f.validity.calls);
        assertEquals(0, f.delegate.totalCalls());
    }

    @Test
    void shouldReadLiveCapacityWhenValid() {
        Fixture f = new Fixture();
        assertEquals(10_000L, f.handler.getCapacityAsLong());
        f.delegate.capacity = (long) Integer.MAX_VALUE + 1_000L;
        assertEquals(f.delegate.capacity, f.handler.getCapacityAsLong());
        assertEquals(2, f.validity.calls);
        assertEquals(2, f.delegate.capacityCalls);
        assertEquals(2, f.delegate.totalCalls());
    }

    @Test
    void shouldReturnZeroCapacityWithoutDelegatingWhenInvalid() {
        Fixture f = new Fixture();
        f.validity.valid = false;
        assertEquals(0L, f.handler.getCapacityAsLong());
        assertEquals(1, f.validity.calls);
        assertEquals(0, f.delegate.totalCalls());
    }

    @Test
    void shouldDelegateInsertWithExactAmountAndTransaction() {
        Fixture f = new Fixture();
        f.delegate.insertLimit = 32;
        try (Transaction tx = Transaction.openRoot()) {
            assertEquals(32, f.handler.insert(100, tx));
            assertEquals(100, f.delegate.lastInsertAmount);
            assertSame(tx, f.delegate.lastInsertTransaction);
        }
        assertEquals(1, f.validity.calls);
        assertEquals(1, f.delegate.insertCalls);
        assertEquals(1, f.delegate.totalCalls());
    }

    @Test
    void shouldDenyPositiveInsertWhenInvalid() {
        Fixture f = new Fixture();
        f.validity.valid = false;
        try (Transaction tx = Transaction.openRoot()) {
            assertEquals(0, f.handler.insert(100, tx));
        }
        assertEquals(1, f.validity.calls);
        assertEquals(0, f.delegate.totalCalls());
    }

    @Test
    void shouldIgnoreZeroInsertWithoutCheckingValidityOrDelegating() {
        Fixture f = new Fixture();
        try (Transaction tx = Transaction.openRoot()) {
            assertEquals(0, f.handler.insert(0, tx));
            f.validity.valid = false;
            assertEquals(0, f.handler.insert(0, tx));
        }
        f.assertNoInteractions();
    }

    @Test
    void shouldRejectNegativeInsertBeforeCheckingValidity() {
        Fixture f = new Fixture();
        try (Transaction tx = Transaction.openRoot()) {
            assertThrows(IllegalArgumentException.class, () -> f.handler.insert(-1, tx));
            f.validity.valid = false;
            assertThrows(IllegalArgumentException.class, () -> f.handler.insert(-1, tx));
        }
        f.assertNoInteractions();
    }

    @Test
    void shouldRejectNullInsertTransactionEvenForZeroOrInvalidEndpoint() {
        Fixture f = new Fixture();
        assertThrows(NullPointerException.class, () -> f.handler.insert(10, null));
        assertThrows(NullPointerException.class, () -> f.handler.insert(0, null));
        f.validity.valid = false;
        assertThrows(NullPointerException.class, () -> f.handler.insert(10, null));
        assertThrows(NullPointerException.class, () -> f.handler.insert(0, null));
        f.assertNoInteractions();
    }

    @Test
    void shouldDelegateExtractWithExactAmountAndTransaction() {
        Fixture f = new Fixture();
        f.delegate.extractLimit = 45;
        try (Transaction tx = Transaction.openRoot()) {
            assertEquals(45, f.handler.extract(80, tx));
            assertEquals(80, f.delegate.lastExtractAmount);
            assertSame(tx, f.delegate.lastExtractTransaction);
        }
        assertEquals(1, f.validity.calls);
        assertEquals(1, f.delegate.extractCalls);
        assertEquals(1, f.delegate.totalCalls());
    }

    @Test
    void shouldDenyPositiveExtractWhenInvalid() {
        Fixture f = new Fixture();
        f.validity.valid = false;
        try (Transaction tx = Transaction.openRoot()) {
            assertEquals(0, f.handler.extract(80, tx));
        }
        assertEquals(1, f.validity.calls);
        assertEquals(0, f.delegate.totalCalls());
    }

    @Test
    void shouldIgnoreZeroExtractWithoutCheckingValidityOrDelegating() {
        Fixture f = new Fixture();
        try (Transaction tx = Transaction.openRoot()) {
            assertEquals(0, f.handler.extract(0, tx));
            f.validity.valid = false;
            assertEquals(0, f.handler.extract(0, tx));
        }
        f.assertNoInteractions();
    }

    @Test
    void shouldRejectNegativeExtractBeforeCheckingValidity() {
        Fixture f = new Fixture();
        try (Transaction tx = Transaction.openRoot()) {
            assertThrows(IllegalArgumentException.class, () -> f.handler.extract(-1, tx));
            f.validity.valid = false;
            assertThrows(IllegalArgumentException.class, () -> f.handler.extract(-1, tx));
        }
        f.assertNoInteractions();
    }

    @Test
    void shouldRejectNullExtractTransactionEvenForZeroOrInvalidEndpoint() {
        Fixture f = new Fixture();
        assertThrows(NullPointerException.class, () -> f.handler.extract(10, null));
        assertThrows(NullPointerException.class, () -> f.handler.extract(0, null));
        f.validity.valid = false;
        assertThrows(NullPointerException.class, () -> f.handler.extract(10, null));
        assertThrows(NullPointerException.class, () -> f.handler.extract(0, null));
        f.assertNoInteractions();
    }

    @Test
    void shouldStopAllDelegationWhenValidityBecomesFalse() {
        Fixture f = new Fixture();
        assertEquals(6_000L, f.handler.getAmountAsLong());
        assertEquals(10_000L, f.handler.getCapacityAsLong());
        try (Transaction tx = Transaction.openRoot()) {
            assertEquals(20, f.handler.insert(20, tx));
            assertEquals(30, f.handler.extract(30, tx));
        }
        f.validity.valid = false;
        assertEquals(0L, f.handler.getAmountAsLong());
        assertEquals(0L, f.handler.getCapacityAsLong());
        try (Transaction tx = Transaction.openRoot()) {
            assertEquals(0, f.handler.insert(20, tx));
            assertEquals(0, f.handler.extract(30, tx));
        }
        assertEquals(8, f.validity.calls);
        assertEquals(4, f.delegate.totalCalls());
    }

    @Test
    void shouldRespectLiveValidityWithoutCachingNegativeResult() {
        Fixture f = new Fixture();
        f.validity.valid = false;
        assertEquals(0L, f.handler.getAmountAsLong());
        try (Transaction tx = Transaction.openRoot()) {
            assertEquals(0, f.handler.extract(30, tx));
        }
        f.validity.valid = true;
        assertEquals(6_000L, f.handler.getAmountAsLong());
        try (Transaction tx = Transaction.openRoot()) {
            assertEquals(30, f.handler.extract(30, tx));
        }
        assertEquals(4, f.validity.calls);
        assertEquals(1, f.delegate.amountCalls);
        assertEquals(1, f.delegate.extractCalls);
        assertEquals(2, f.delegate.totalCalls());
    }

    @Test
    void shouldPropagateValidityFailureWithoutDelegating() {
        Fixture f = new Fixture();
        var failure = new IllegalStateException("Validity failure");
        f.validity.failure = failure;
        assertSame(failure, assertThrows(IllegalStateException.class, f.handler::getAmountAsLong));
        assertSame(
            failure,
            assertThrows(IllegalStateException.class, f.handler::getCapacityAsLong)
        );
        try (Transaction tx = Transaction.openRoot()) {
            assertSame(
                failure,
                assertThrows(IllegalStateException.class, () -> f.handler.insert(10, tx))
            );
            assertSame(
                failure,
                assertThrows(IllegalStateException.class, () -> f.handler.extract(10, tx))
            );
        }
        assertEquals(4, f.validity.calls);
        assertEquals(0, f.delegate.totalCalls());
    }

    @Test
    void shouldPropagateDelegateFailureWithoutRetrying() {
        Fixture f = new Fixture();
        var failure = new IllegalStateException("Delegate failure");
        f.delegate.failure = failure;
        assertSame(failure, assertThrows(IllegalStateException.class, f.handler::getAmountAsLong));
        assertSame(
            failure,
            assertThrows(IllegalStateException.class, f.handler::getCapacityAsLong)
        );
        try (Transaction tx = Transaction.openRoot()) {
            assertSame(
                failure,
                assertThrows(IllegalStateException.class, () -> f.handler.insert(10, tx))
            );
            assertSame(
                failure,
                assertThrows(IllegalStateException.class, () -> f.handler.extract(10, tx))
            );
        }
        assertEquals(4, f.validity.calls);
        assertEquals(1, f.delegate.amountCalls);
        assertEquals(1, f.delegate.capacityCalls);
        assertEquals(1, f.delegate.insertCalls);
        assertEquals(1, f.delegate.extractCalls);
    }

    private static final class Fixture {
        final RecordingHandler delegate = new RecordingHandler();
        final RecordingValidity validity = new RecordingValidity();
        final EnergyEndpointHandler handler = new EnergyEndpointHandler(delegate, validity);

        void assertNoInteractions() {
            assertEquals(0, validity.calls);
            assertEquals(0, delegate.totalCalls());
        }
    }

    private static final class RecordingValidity implements BooleanSupplier {
        boolean valid = true;
        int calls;
        RuntimeException failure;

        @Override
        public boolean getAsBoolean() {
            calls++;
            if (failure != null) {
                throw failure;
            }
            return valid;
        }
    }

    private static final class RecordingHandler implements EnergyHandler {
        long amount = 6_000L;
        long capacity = 10_000L;
        int insertLimit = Integer.MAX_VALUE;
        int extractLimit = Integer.MAX_VALUE;
        int amountCalls;
        int capacityCalls;
        int insertCalls;
        int extractCalls;
        int lastInsertAmount;
        int lastExtractAmount;
        TransactionContext lastInsertTransaction;
        TransactionContext lastExtractTransaction;
        RuntimeException failure;

        int totalCalls() {
            return amountCalls + capacityCalls + insertCalls + extractCalls;
        }

        @Override
        public long getAmountAsLong() {
            amountCalls++;
            checkFailure();
            return amount;
        }

        @Override
        public long getCapacityAsLong() {
            capacityCalls++;
            checkFailure();
            return capacity;
        }

        @Override
        public int insert(int amount, @NonNull TransactionContext transaction) {
            insertCalls++;
            lastInsertAmount = amount;
            lastInsertTransaction = transaction;
            checkFailure();
            return Math.min(amount, insertLimit);
        }

        @Override
        public int extract(int amount, @NonNull TransactionContext transaction) {
            extractCalls++;
            lastExtractAmount = amount;
            lastExtractTransaction = transaction;
            checkFailure();
            return Math.min(amount, extractLimit);
        }

        private void checkFailure() {
            if (failure != null) {
                throw failure;
            }
        }
    }
}
