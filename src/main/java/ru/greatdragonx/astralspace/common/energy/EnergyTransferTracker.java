package ru.greatdragonx.astralspace.common.energy;

public final class EnergyTransferTracker {
    private long lastTransferTick = -1L;
    private int transferredInLastTransferTick = 0;
    private int transferredInPrecedingTick = 0;

    public void recordTransfer(
        int amount,
        long currentTick
    )
    {
        if (amount <= 0) {
            return;
        }

        if (this.lastTransferTick != currentTick) {
            this.transferredInPrecedingTick =
                this.lastTransferTick == currentTick - 1 ? this.transferredInLastTransferTick : 0;

            this.lastTransferTick = currentTick;
            this.transferredInLastTransferTick = 0;
        }

        this.transferredInLastTransferTick += amount;
    }

    public int getCurrentTransferRate(long currentTick) {
        if (this.lastTransferTick == currentTick) {
            return this.transferredInPrecedingTick;
        }

        if (this.lastTransferTick == currentTick - 1) {
            return this.transferredInLastTransferTick;
        }

        return 0;
    }
}
