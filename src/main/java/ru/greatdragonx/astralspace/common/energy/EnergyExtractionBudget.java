package ru.greatdragonx.astralspace.common.energy;

import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jspecify.annotations.NonNull;

public final class EnergyExtractionBudget {
    private final int maxPerTick;
    private long lastExtractionTick = -1L;
    private int extractedThisTick = 0;

    private record BudgetSnapshot(
        long tick, int extracted
    ) {}

    private final SnapshotJournal<BudgetSnapshot> journal = new SnapshotJournal<>() {

        @Override
        protected @NonNull BudgetSnapshot createSnapshot() {
            return new BudgetSnapshot(
                EnergyExtractionBudget.this.lastExtractionTick,
                EnergyExtractionBudget.this.extractedThisTick
            );
        }

        @Override
        protected void revertToSnapshot(@NonNull BudgetSnapshot snapshot) {
            EnergyExtractionBudget.this.lastExtractionTick = snapshot.tick();
            EnergyExtractionBudget.this.extractedThisTick = snapshot.extracted();
        }
    };

    public EnergyExtractionBudget(int maxPerTick) {
        if (maxPerTick <= 0) {
            throw new IllegalArgumentException("Maximum extraction per tick must be positive");
        }
        this.maxPerTick = maxPerTick;
    }

    public void recordExtraction(
        int extracted,
        long currentTick,
        TransactionContext transaction
    )
    {
        if (extracted <= 0) {
            return;
        }

        int remaining = this.getRemaining(currentTick);
        if (extracted > remaining) {
            throw new IllegalArgumentException("Extraction exceeds remaining energy budget");
        }

        this.journal.updateSnapshots(transaction);

        if (this.lastExtractionTick != currentTick) {
            this.lastExtractionTick = currentTick;
            this.extractedThisTick = 0;
        }

        this.extractedThisTick += extracted;
    }

    public int getRemaining(long currentTick) {
        if (lastExtractionTick == currentTick) {
            return this.maxPerTick - this.extractedThisTick;
        }

        return maxPerTick;
    }

    public int getAllowedAmount(
        int requested,
        long currentTick
    )
    {
        if (requested <= 0) {
            return 0;
        }

        int remaining = this.getRemaining(currentTick);

        return Math.min(
            remaining,
            requested
        );
    }
}
