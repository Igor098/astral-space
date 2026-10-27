package ru.greatdragonx.astralspace.common.energy;

import net.minecraft.core.Direction;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jspecify.annotations.NonNull;

import java.util.EnumMap;
import java.util.Map;

public final class EnergyDistributionManager {
    private static final long ACTIVE_WINDOW_TICKS = 20L;

    private final Map<Direction, Integer> extractedBySide = new EnumMap<>(Direction.class);
    private final Map<Direction, Long> lastRequestTicks = new EnumMap<>(Direction.class);

    private long lastUsageTick = -1L;

    private record UsageSnapshot(
        long tick, Map<Direction, Integer> extracted
    ) {}

    private final SnapshotJournal<UsageSnapshot> journal = new SnapshotJournal<>() {
        @Override
        protected @NonNull UsageSnapshot createSnapshot() {
            return new UsageSnapshot(
                EnergyDistributionManager.this.lastUsageTick,
                new EnumMap<>(EnergyDistributionManager.this.extractedBySide)
            );
        }

        @Override
        protected void revertToSnapshot(@NonNull UsageSnapshot snapshot) {
            EnergyDistributionManager.this.lastUsageTick = snapshot.tick();
            EnergyDistributionManager.this.extractedBySide.clear();
            EnergyDistributionManager.this.extractedBySide.putAll(snapshot.extracted());
        }
    };

    public void recordRequest(
        Direction side,
        int requested,
        long currentTick
    )
    {
        if (requested <= 0) {
            return;
        }

        this.lastRequestTicks.put(
            side,
            currentTick
        );
    }

    public boolean isActive(
        Direction side,
        long currentTick
    )
    {
        Long lastTick = this.lastRequestTicks.get(side);
        if (lastTick == null) {
            return false;
        }

        long elapsed = currentTick - lastTick;
        if (elapsed < 0) {
            return false;
        }

        return elapsed < ACTIVE_WINDOW_TICKS;
    }

    public int countActiveSides(long currentTick) {
        int count = 0;

        for (Direction side : Direction.values()) {
            boolean isActive = this.isActive(
                side,
                currentTick
            );
            if (isActive) {
                count++;
            }
        }

        return count;
    }

    public int getBaseQuota(
        Direction side,
        int maxPerTick,
        long currentTick
    )
    {
        boolean isActive = this.isActive(
            side,
            currentTick
        );
        if (!isActive) {
            return 0;
        }

        int activeSides = this.countActiveSides(currentTick);

        return maxPerTick / activeSides;
    }

    public int getRemainingBaseQuota(
        Direction side,
        int maxPerTick,
        long currentTick
    )
    {
        int quota = this.getBaseQuota(
            side,
            maxPerTick,
            currentTick
        );

        int extracted = this.getExtractedBySide(
            side,
            currentTick
        );

        int remains = quota - extracted;

        return Math.max(
            0,
            remains
        );
    }

    public int getAllowedBaseAmount(
        Direction side,
        int requested,
        int maxPerTick,
        long currentTick
    )
    {
        if (requested <= 0) {
            return 0;
        }

        int remainingQuota = this.getRemainingBaseQuota(
            side,
            maxPerTick,
            currentTick
        );

        return Math.min(
            remainingQuota,
            requested
        );
    }

    public int getExtractedBySide(
        Direction side,
        long currentTick
    )
    {
        if (this.lastUsageTick != currentTick) {
            return 0;
        }

        return this.extractedBySide.getOrDefault(
            side,
            0
        );
    }

    public void recordExtraction(
        Direction side,
        int extracted,
        long currentTick,
        TransactionContext transaction
    )
    {
        if (extracted <= 0) {
            return;
        }

        this.journal.updateSnapshots(transaction);

        if (this.lastUsageTick != currentTick) {
            this.lastUsageTick = currentTick;
            this.extractedBySide.clear();
        }

        int alreadyExtracted = this.getExtractedBySide(
            side,
            currentTick
        );
        this.extractedBySide.put(
            side,
            alreadyExtracted + extracted
        );
    }
}
