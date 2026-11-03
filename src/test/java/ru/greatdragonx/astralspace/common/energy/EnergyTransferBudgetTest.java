package ru.greatdragonx.astralspace.common.energy;

import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EnergyTransferBudgetTest {

    @Test
    void shouldRejectNonPositiveLimit() {
        assertAll(
            () -> assertThrows(IllegalArgumentException.class,
                () -> new EnergyTransferBudget(0)),
            () -> assertThrows(IllegalArgumentException.class,
                () -> new EnergyTransferBudget(-1))
        );
    }

    @Test
    void shouldStartWithFullBudget() {
        EnergyTransferBudget budget = new EnergyTransferBudget(80);

        assertEquals(80, budget.getRemaining(100));
        assertEquals(80, budget.getRemaining(101));
    }

    @Test
    void shouldLimitAllowedAmountWithoutChangingState() {
        EnergyTransferBudget budget = new EnergyTransferBudget(80);

        assertAll(
            () -> assertEquals(80, budget.getAllowedAmount(120, 100)),
            () -> assertEquals(25, budget.getAllowedAmount(25, 100)),
            () -> assertEquals(0, budget.getAllowedAmount(0, 100)),
            () -> assertEquals(0, budget.getAllowedAmount(-5, 100)),
            () -> assertEquals(80, budget.getRemaining(100))
        );
    }

    @Test
    void shouldAccumulateTransfersInOneTickAfterCommit() {
        EnergyTransferBudget budget = new EnergyTransferBudget(80);

        try (Transaction tx = Transaction.openRoot()) {
            budget.recordTransfer(30, 100, tx);
            budget.recordTransfer(20, 100, tx);

            assertEquals(30, budget.getRemaining(100));
            assertEquals(30, budget.getAllowedAmount(50, 100));
            tx.commit();
        }

        assertEquals(30, budget.getRemaining(100));
    }

    @Test
    void shouldAllowExactlyRemainingBudget() {
        EnergyTransferBudget budget = new EnergyTransferBudget(80);

        try (Transaction tx = Transaction.openRoot()) {
            budget.recordTransfer(50, 100, tx);
            budget.recordTransfer(30, 100, tx);
            tx.commit();
        }

        assertEquals(0, budget.getRemaining(100));
        assertEquals(0, budget.getAllowedAmount(1, 100));
    }

    @Test
    void shouldRejectTransferAboveRemainingWithoutChangingBudget() {
        EnergyTransferBudget budget = new EnergyTransferBudget(80);

        try (Transaction tx = Transaction.openRoot()) {
            budget.recordTransfer(50, 100, tx);

            assertThrows(IllegalArgumentException.class,
                () -> budget.recordTransfer(31, 100, tx));
            assertEquals(30, budget.getRemaining(100));

            tx.commit();
        }

        assertEquals(30, budget.getRemaining(100));
    }

    @Test
    void shouldIgnoreZeroAndNegativeRecordedAmounts() {
        EnergyTransferBudget budget = new EnergyTransferBudget(80);

        try (Transaction tx = Transaction.openRoot()) {
            budget.recordTransfer(0, 100, tx);
            budget.recordTransfer(-10, 100, tx);
            tx.commit();
        }

        assertEquals(80, budget.getRemaining(100));
    }

    @Test
    void shouldRestoreBudgetAfterRootRollback() {
        EnergyTransferBudget budget = new EnergyTransferBudget(80);

        try (Transaction tx = Transaction.openRoot()) {
            budget.recordTransfer(50, 100, tx);
            assertEquals(30, budget.getRemaining(100));
            // No commit: close rolls back the transaction.
        }

        assertEquals(80, budget.getRemaining(100));
    }

    @Test
    void shouldPreserveRootTransferWhenNestedTransactionRollsBack() {
        EnergyTransferBudget budget = new EnergyTransferBudget(80);

        try (Transaction root = Transaction.openRoot()) {
            budget.recordTransfer(30, 100, root);

            try (Transaction nested = Transaction.open(root)) {
                budget.recordTransfer(20, 100, nested);
                assertEquals(30, budget.getRemaining(100));
                // Nested transaction rolls back.
            }

            assertEquals(50, budget.getRemaining(100));
            root.commit();
        }

        assertEquals(50, budget.getRemaining(100));
    }

    @Test
    void shouldRollbackCommittedNestedTransferWhenRootRollsBack() {
        EnergyTransferBudget budget = new EnergyTransferBudget(80);

        try (Transaction root = Transaction.openRoot()) {
            try (Transaction nested = Transaction.open(root)) {
                budget.recordTransfer(50, 100, nested);
                nested.commit();
            }

            assertEquals(30, budget.getRemaining(100));
            // No root commit: nested changes also roll back.
        }

        assertEquals(80, budget.getRemaining(100));
    }

    @Test
    void shouldResetAvailableBudgetOnNextTick() {
        EnergyTransferBudget budget = new EnergyTransferBudget(80);

        try (Transaction tx = Transaction.openRoot()) {
            budget.recordTransfer(60, 100, tx);
            tx.commit();
        }

        assertEquals(20, budget.getRemaining(100));
        assertEquals(80, budget.getRemaining(101));

        try (Transaction tx = Transaction.openRoot()) {
            budget.recordTransfer(25, 101, tx);
            tx.commit();
        }

        assertEquals(55, budget.getRemaining(101));
    }

    @Test
    void shouldRestorePreviousTickStateWhenNewTickTransactionRollsBack() {
        EnergyTransferBudget budget = new EnergyTransferBudget(80);

        try (Transaction tx = Transaction.openRoot()) {
            budget.recordTransfer(50, 100, tx);
            tx.commit();
        }

        try (Transaction tx = Transaction.openRoot()) {
            budget.recordTransfer(25, 101, tx);
            assertEquals(55, budget.getRemaining(101));
            // Roll back transition to tick 101.
        }

        assertEquals(30, budget.getRemaining(100));
        assertEquals(80, budget.getRemaining(101));
    }

    @Test
    void shouldKeepInsertionAndExtractionBudgetsIndependent() {
        EnergyTransferBudget insertionBudget = new EnergyTransferBudget(200);
        EnergyTransferBudget extractionBudget = new EnergyTransferBudget(80);

        try (Transaction tx = Transaction.openRoot()) {
            insertionBudget.recordTransfer(150, 100, tx);
            extractionBudget.recordTransfer(30, 100, tx);
            tx.commit();
        }

        assertEquals(50, insertionBudget.getRemaining(100));
        assertEquals(50, extractionBudget.getRemaining(100));
        assertEquals(200, insertionBudget.getRemaining(101));
        assertEquals(80, extractionBudget.getRemaining(101));
    }

    @Test
    void shouldRollbackBothBudgetsInSameTransaction() {
        EnergyTransferBudget insertionBudget = new EnergyTransferBudget(200);
        EnergyTransferBudget extractionBudget = new EnergyTransferBudget(80);

        try (Transaction tx = Transaction.openRoot()) {
            insertionBudget.recordTransfer(150, 100, tx);
            extractionBudget.recordTransfer(30, 100, tx);

            assertEquals(50, insertionBudget.getRemaining(100));
            assertEquals(50, extractionBudget.getRemaining(100));
            // No commit.
        }

        assertEquals(200, insertionBudget.getRemaining(100));
        assertEquals(80, extractionBudget.getRemaining(100));
    }
}
