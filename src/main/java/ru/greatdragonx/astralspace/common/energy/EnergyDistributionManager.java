package ru.greatdragonx.astralspace.common.energy;

import net.minecraft.core.Direction;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jspecify.annotations.NonNull;

import java.util.EnumMap;
import java.util.Map;

public final class EnergyDistributionManager {
    private static final long ACTIVE_WINDOW_TICKS = 20L;

    private final Map<Direction, Long> lastRequestTicks = new EnumMap<>(Direction.class);
    private final Map<Direction, Integer> extractedBySide = new EnumMap<>(Direction.class);
    private final Map<Direction, Integer> maxRequestedBySide = new EnumMap<>(Direction.class);
    private final Map<Direction, Integer> previousExtractedBySide = new EnumMap<>(Direction.class);
    private final Map<Direction, Integer> previousMaxRequestedBySide =
        new EnumMap<>(Direction.class);
    private final Map<Direction, Long> fairnessCredits = new EnumMap<>(Direction.class);


    private long lastUsageTick = -1L;
    private long lastDemandTick = -1L;
    private long previousUsageTick = -1L;
    private long previousDemandTick = -1L;

    private final int maxTransferPerTick;

    private record UsageSnapshot(
        long tick,
        long previousTick,
        Map<Direction, Integer> extracted,
        Map<Direction, Integer> previousExtracted
    ) {}

    private final SnapshotJournal<UsageSnapshot> journal = new SnapshotJournal<>() {
        @Override
        protected @NonNull UsageSnapshot createSnapshot() {
            return new UsageSnapshot(
                EnergyDistributionManager.this.lastUsageTick,
                EnergyDistributionManager.this.previousUsageTick,
                new EnumMap<>(EnergyDistributionManager.this.extractedBySide),
                new EnumMap<>(EnergyDistributionManager.this.previousExtractedBySide)
            );
        }

        @Override
        protected void revertToSnapshot(@NonNull UsageSnapshot snapshot) {
            EnergyDistributionManager.this.lastUsageTick = snapshot.tick();
            EnergyDistributionManager.this.previousUsageTick = snapshot.previousTick();
            EnergyDistributionManager.this.extractedBySide.clear();
            EnergyDistributionManager.this.previousExtractedBySide.clear();
            EnergyDistributionManager.this.extractedBySide.putAll(snapshot.extracted());
            EnergyDistributionManager.this.previousExtractedBySide.putAll(snapshot.previousExtracted());
        }
    };

    public EnergyDistributionManager(int maxTransferPerTick) {
        this.maxTransferPerTick = maxTransferPerTick;
    }

    public void recordRequest(
        Direction side,
        int requested,
        long currentTick
    )
    {
        if (requested <= 0) {
            return;
        }

        if (this.lastDemandTick != currentTick) {
            if (this.lastDemandTick == currentTick - 1) {
                this.previousDemandTick = this.lastDemandTick;
                this.previousMaxRequestedBySide.clear();
                this.previousMaxRequestedBySide.putAll(this.maxRequestedBySide);
            } else {
                this.previousDemandTick = currentTick - 1;
                this.previousMaxRequestedBySide.clear();
            }
            this.lastDemandTick = currentTick;
            this.maxRequestedBySide.clear();
        }

        int maxRequested = this.maxRequestedBySide.getOrDefault(
            side,
            0
        );

        this.maxRequestedBySide.put(
            side,
            Math.max(
                maxRequested,
                requested
            )
        );

        this.lastRequestTicks.put(
            side,
            currentTick
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
            if (this.lastUsageTick == currentTick - 1) {
                this.previousUsageTick = this.lastUsageTick;

                this.previousExtractedBySide.clear();
                this.previousExtractedBySide.putAll(this.extractedBySide);
            } else {
                this.previousExtractedBySide.clear();
                this.previousUsageTick = currentTick - 1;
            }

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

        return this.maxTransferPerTick / activeSides;
    }

    public int getRemainingBaseQuota(
        Direction side,
        long currentTick
    )
    {
        int quota = this.getBaseQuota(
            side,
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

    private int getBaseQuotaForSupply(
        Direction side,
        int effectiveSupply,
        long currentTick
    )
    {
        if (!this.isActive(
            side,
            currentTick
        ))
        {
            return 0;
        }

        int activeSides = this.countActiveSides(currentTick);
        if (activeSides == 0) {
            return 0;
        }

        return effectiveSupply / activeSides;
    }

    public int getAllowedBaseAmount(
        Direction side,
        int requested,
        long currentTick
    )
    {
        if (requested <= 0) {
            return 0;
        }

        int remainingQuota = this.getRemainingBaseQuota(
            side,
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

    public int getPreviousExtractedBySide(
        Direction side,
        long currentTick
    )
    {
        if (this.lastUsageTick == currentTick - 1) {
            return this.extractedBySide.getOrDefault(
                side,
                0
            );
        }

        if (this.previousUsageTick == currentTick - 1) {
            return this.previousExtractedBySide.getOrDefault(
                side,
                0
            );
        }

        return 0;
    }

    public int getMaxRequestedBySide(
        Direction side,
        long currentTick
    )
    {
        if (this.lastDemandTick != currentTick) {
            return 0;
        }

        return this.maxRequestedBySide.getOrDefault(
            side,
            0
        );
    }

    public int getPreviousMaxRequestedBySide(
        Direction side,
        long currentTick
    )
    {
        if (this.lastDemandTick == currentTick - 1) {
            return this.maxRequestedBySide.getOrDefault(
                side,
                0
            );
        }

        if (this.previousDemandTick == currentTick - 1) {
            return this.previousMaxRequestedBySide.getOrDefault(
                side,
                0
            );
        }

        return 0;
    }

    public int getProtectedRemainingQuota(
        Direction side,
        int effectiveSupply,
        long currentTick
    )
    {
        int baseQuota = this.getBaseQuotaForSupply(
            side,
            effectiveSupply,
            currentTick
        );
        int previousMaxRequested = this.getPreviousMaxRequestedBySide(
            side,
            currentTick
        );
        int currentMaxRequested = this.getMaxRequestedBySide(
            side,
            currentTick
        );

        int maxRequested = Math.max(
            previousMaxRequested,
            currentMaxRequested
        );

        int reserve;

        if (maxRequested == 0) {
            reserve = baseQuota;
        } else {
            reserve = Math.min(
                maxRequested,
                baseQuota
            );
        }

        int finalQuota = reserve - this.getExtractedBySide(
            side,
            currentTick
        );

        return Math.max(
            0,
            finalQuota
        );
    }

    public int getTotalProtectedRemainingForOtherSides(
        Direction requestingSide,
        int effectiveSupply,
        long currentTick
    )
    {
        int totalProtected = 0;

        for (Direction side : Direction.values()) {
            if (side == requestingSide) {
                continue;
            }

            totalProtected += this.getProtectedRemainingQuota(
                side,
                effectiveSupply,
                currentTick
            );
        }

        return totalProtected;
    }

    public int getAllowedAdaptiveAmount(
        Direction side,
        int requested,
        int remainingGlobalBudget,
        int storedEnergy,
        long currentTick
    )
    {
        if (requested <= 0 || remainingGlobalBudget <= 0) {
            return 0;
        }

        int alreadyExtracted = this.maxTransferPerTick - remainingGlobalBudget;

        int effectiveSupply = Math.min(
            this.maxTransferPerTick,
            alreadyExtracted + storedEnergy
        );

        int protectedFromOtherSides = this.getTotalProtectedRemainingForOtherSides(
            side,
            effectiveSupply,
            currentTick
        );

        int availableForSide = Math.max(
            0,
            effectiveSupply - alreadyExtracted - protectedFromOtherSides
        );

        return Math.min(
            availableForSide,
            requested
        );
    }

    private int getTotalExtractedPreviousTick(long currentTick) {
        int total = 0;

        for (Direction side : Direction.values()) {
            total += this.getPreviousExtractedBySide(
                side,
                currentTick
            );
        }

        return total;
    }

    private int countDemandingSidesPreviousTick(long currentTick) {
        int count = 0;

        for (Direction side : Direction.values()) {
            int previousMaxRequested = this.getPreviousMaxRequestedBySide(
                side,
                currentTick
            );

            if (previousMaxRequested > 0) {
                count++;
            }
        }

        return count;
    }
}
