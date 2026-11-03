package ru.greatdragonx.astralspace.common.energy;

import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jspecify.annotations.NonNull;

public final class EnergyTransferBudget {
    private final int maxPerTick;

    private long lastTransferTick = -1L;
    private int transferredThisTick = 0;

    private record BudgetSnapshot(long tick, int transferred) {}

    private final SnapshotJournal<BudgetSnapshot> journal = new SnapshotJournal<>() {
        @Override
        protected @NonNull BudgetSnapshot createSnapshot() {
            return new BudgetSnapshot(
                EnergyTransferBudget.this.lastTransferTick,
                EnergyTransferBudget.this.transferredThisTick
            );
        }

        @Override
        protected void revertToSnapshot(@NonNull BudgetSnapshot snapshot) {
            EnergyTransferBudget.this.lastTransferTick = snapshot.tick();
            EnergyTransferBudget.this.transferredThisTick = snapshot.transferred();
        }
    };

    public EnergyTransferBudget(int maxPerTick) {
        if (maxPerTick <= 0) {
            throw new IllegalArgumentException("Maximum transfer per tick must be positive");
        }
        this.maxPerTick = maxPerTick;
    }

    public void recordTransfer(
        int transferred,
        long currentTick,
        TransactionContext transaction
    )
    {
        if (transferred <= 0) {
            return;
        }

        int remaining = this.getRemaining(currentTick);
        if (transferred > remaining) {
            throw new IllegalArgumentException("Transfer exceeds remaining energy budget");
        }

        this.journal.updateSnapshots(transaction);

        if (this.lastTransferTick != currentTick) {
            this.lastTransferTick = currentTick;
            this.transferredThisTick = 0;
        }

        this.transferredThisTick += transferred;
    }

    public int getRemaining(long currentTick) {
        if (this.lastTransferTick == currentTick) {
            return this.maxPerTick - this.transferredThisTick;
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
