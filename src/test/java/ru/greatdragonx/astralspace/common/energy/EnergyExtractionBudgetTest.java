package ru.greatdragonx.astralspace.common.energy;

import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EnergyExtractionBudgetTest {
    @Test
    void shouldRestoreBudgetAfterRollback() {
        EnergyExtractionBudget budget = new EnergyExtractionBudget(80);
        assertEquals(
            80,
            budget.getRemaining(100)
        );

        try (Transaction tx = Transaction.openRoot()) {
            budget.recordExtraction(
                50,
                100,
                tx
            );

            assertEquals(
                30,
                budget.getRemaining(100)
            );
        }

        assertEquals(
            80,
            budget.getRemaining(100)
        );
    }

    @Test
    void shouldKeepBudgetAfterCommit() {
        EnergyExtractionBudget budget = new EnergyExtractionBudget(80);
        assertEquals(
            80,
            budget.getRemaining(100)
        );

        try (Transaction tx = Transaction.openRoot()) {
            budget.recordExtraction(
                50,
                100,
                tx
            );

            tx.commit();
        }

        assertEquals(
            30,
            budget.getRemaining(100)
        );

        assertEquals(
            80,
            budget.getRemaining(101)
        );
    }

    @Test
    void shouldRollbackCommittedNestedTransaction() {
        EnergyExtractionBudget budget = new EnergyExtractionBudget(80);

        try (Transaction root = Transaction.openRoot()) {
            try (Transaction nested = Transaction.open(root)) {
                budget.recordExtraction(
                    50,
                    100,
                    nested
                );
                nested.commit();
            }

            assertEquals(
                30,
                budget.getRemaining(100)
            );
        }

        assertEquals(
            80,
            budget.getRemaining(100)
        );
    }

    @Test
    void shouldLimitMultipleExtractionsInSameTick() {
        EnergyExtractionBudget budget = new EnergyExtractionBudget(80);

        try (Transaction tx = Transaction.openRoot()) {
            int allowed1 = budget.getAllowedAmount(
                50,
                100
            );
            budget.recordExtraction(
                allowed1,
                100,
                tx
            );
            assertEquals(
                50,
                allowed1
            );
            assertEquals(
                30,
                budget.getRemaining(100)
            );

            int allowed2 = budget.getAllowedAmount(
                60,
                100
            );
            budget.recordExtraction(
                allowed2,
                100,
                tx
            );
            assertEquals(
                30,
                allowed2
            );
            assertEquals(
                0,
                budget.getRemaining(100)
            );

            int allowed3 = budget.getAllowedAmount(
                20,
                100
            );
            budget.recordExtraction(
                allowed3,
                100,
                tx
            );
            assertEquals(
                0,
                allowed3
            );
            assertEquals(
                0,
                budget.getRemaining(100)
            );

            tx.commit();
        }

        assertEquals(
            0,
            budget.getRemaining(100)
        );
        assertEquals(
            80,
            budget.getRemaining(101)
        );
    }

    @Test
    void shouldRestoreBudgetAfterNestedRollback() {
        EnergyExtractionBudget budget = new EnergyExtractionBudget(80);

        try (Transaction root = Transaction.openRoot()) {
            budget.recordExtraction(
                50,
                100,
                root
            );
            assertEquals(
                30,
                budget.getRemaining(100)
            );

            try (Transaction nested = Transaction.open(root)) {
                budget.recordExtraction(
                    20,
                    100,
                    nested
                );
                assertEquals(
                    10,
                    budget.getRemaining(100)
                );
            }

            assertEquals(
                30,
                budget.getRemaining(100)
            );

            root.commit();
        }

        assertEquals(
            30,
            budget.getRemaining(100)
        );
    }
}
