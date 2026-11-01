package ru.greatdragonx.astralspace.common.energy;

public final class EnergyTransferTracker {
    private static final int AVERAGE_WINDOW = 20;

    private final long[] historyTicks = new long[AVERAGE_WINDOW];
    private final int[] historyAmounts = new int[AVERAGE_WINDOW];

    private long firstTransferTick = -1;
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

        if (this.firstTransferTick == -1) {
            this.firstTransferTick = currentTick;
        }

        int index = Math.floorMod(
            currentTick,
            AVERAGE_WINDOW
        );

        if (this.historyTicks[index] != currentTick) {
            this.historyTicks[index] = currentTick;
            this.historyAmounts[index] = 0;
        }

        this.historyAmounts[index] += amount;
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

    public int getAverageTransferRate(long currentTick) {
        if (this.firstTransferTick < 0 || currentTick <= this.firstTransferTick) {
            return 0;
        }

        int samples = (int) Math.min(
            AVERAGE_WINDOW,
            currentTick - this.firstTransferTick
        );

        long total = 0;

        for (int offset = 1; offset <= samples; offset++) {
            long tick = currentTick - offset;
            int index = Math.floorMod(
                tick,
                AVERAGE_WINDOW
            );

            if (this.historyTicks[index] == tick) {
                total += this.historyAmounts[index];
            }
        }

        return (int) Math.round((double) total / samples);
    }
}
