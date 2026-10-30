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
    private final Map<Direction, Long> activeSinceTicks = new EnumMap<>(Direction.class);


    private long lastUsageTick = -1L;
    private long lastDemandTick = -1L;
    private long previousUsageTick = -1L;
    private long previousDemandTick = -1L;
    private long lastFairnessUpdateTick = -1L;

    private final int maxTransferPerTick;

    private record UsageSnapshot(long tick,
        long previousTick,
        Map<Direction, Integer> extracted,
        Map<Direction, Integer> previousExtracted) {}

    private record DemandSnapshot(long lastDemandTick,
        long previousDemandTick,
        Map<Direction, Long> lastRequestTicks,
        Map<Direction, Long> activeSinceTicks,
        Map<Direction, Integer> maxRequestedBySide,
        Map<Direction, Integer> previousMaxRequestedBySide) {}

    private record FairnessSnapshot(long lastFairnessUpdateTick,
        Map<Direction, Long> fairnessCredits) {}

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

    private final SnapshotJournal<DemandSnapshot> demandJournal = new SnapshotJournal<>() {
        @Override
        protected @NonNull DemandSnapshot createSnapshot() {
            return new DemandSnapshot(
                EnergyDistributionManager.this.lastDemandTick,
                EnergyDistributionManager.this.previousDemandTick,
                new EnumMap<>(EnergyDistributionManager.this.lastRequestTicks),
                new EnumMap<>(EnergyDistributionManager.this.activeSinceTicks),
                new EnumMap<>(EnergyDistributionManager.this.maxRequestedBySide),
                new EnumMap<>(EnergyDistributionManager.this.previousMaxRequestedBySide)
            );
        }

        @Override
        protected void revertToSnapshot(@NonNull DemandSnapshot snapshot) {
            EnergyDistributionManager.this.lastDemandTick = snapshot.lastDemandTick();
            EnergyDistributionManager.this.previousDemandTick = snapshot.previousDemandTick();
            EnergyDistributionManager.this.lastRequestTicks.clear();
            EnergyDistributionManager.this.activeSinceTicks.clear();
            EnergyDistributionManager.this.maxRequestedBySide.clear();
            EnergyDistributionManager.this.previousMaxRequestedBySide.clear();
            EnergyDistributionManager.this.lastRequestTicks.putAll(snapshot.lastRequestTicks());
            EnergyDistributionManager.this.activeSinceTicks.putAll(snapshot.activeSinceTicks());
            EnergyDistributionManager.this.maxRequestedBySide.putAll(snapshot.maxRequestedBySide());
            EnergyDistributionManager.this.previousMaxRequestedBySide.putAll(snapshot.previousMaxRequestedBySide());
        }
    };

    private final SnapshotJournal<FairnessSnapshot> fairnessJournal = new SnapshotJournal<>() {

        @Override
        protected @NonNull FairnessSnapshot createSnapshot() {
            return new FairnessSnapshot(
                EnergyDistributionManager.this.lastFairnessUpdateTick,
                new EnumMap<>(EnergyDistributionManager.this.fairnessCredits)
            );
        }

        @Override
        protected void revertToSnapshot(
            @NonNull FairnessSnapshot snapshot
        )
        {
            EnergyDistributionManager.this.lastFairnessUpdateTick =
                snapshot.lastFairnessUpdateTick();

            EnergyDistributionManager.this.fairnessCredits.clear();
            EnergyDistributionManager.this.fairnessCredits.putAll(snapshot.fairnessCredits());
        }
    };

    public EnergyDistributionManager(int maxTransferPerTick) {
        this.maxTransferPerTick = maxTransferPerTick;
    }

    public void recordRequest(
        Direction side,
        int requested,
        long currentTick,
        TransactionContext transaction
    )
    {
        if (requested <= 0) {
            return;
        }

        this.demandJournal.updateSnapshots(transaction);

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

        if (!this.isActive(
            side,
            currentTick
        ))
        {
            this.fairnessJournal.updateSnapshots(transaction);
            this.fairnessCredits.remove(side);

            this.activeSinceTicks.put(
                side,
                currentTick
            );
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
        long currentTick,
        TransactionContext transaction
    )
    {
        this.fairnessJournal.updateSnapshots(transaction);

        this.updateFairnessCredits(currentTick);
        if (this.countActiveSides(currentTick) <= 1) {
            this.fairnessCredits.clear();
        }

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

        long balance = this.fairnessCredits.getOrDefault(
            side,
            0L
        );

        int maxLimit = Math.min(
            storedEnergy,
            remainingGlobalBudget
        );

        long adjustedAvailable = availableForSide + balance;

        int allowed = Math.clamp(
            adjustedAvailable,
            0,
            maxLimit
        );

        return Math.min(
            allowed,
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

    private int getEffectivePreviousDemand(
        Direction side,
        long currentTick
    )
    {
        return Math.max(
            this.getPreviousMaxRequestedBySide(
                side,
                currentTick
            ),
            this.getPreviousExtractedBySide(
                side,
                currentTick
            )
        );
    }

    private int countDemandingSidesPreviousTick(long currentTick) {
        int count = 0;

        for (Direction side : Direction.values()) {
            int previousDemand = this.getEffectivePreviousDemand(
                side,
                currentTick
            );

            if (previousDemand > 0) {
                count++;
            }
        }

        return count;
    }

    private void updateFairnessCredits(long currentTick) {
        if (this.lastFairnessUpdateTick == currentTick) {
            return;
        }

        long previousTick = currentTick - 1;
        this.lastFairnessUpdateTick = currentTick;

        for (Direction side : Direction.values()) {
            int previousDemand = this.getEffectivePreviousDemand(
                side,
                currentTick
            );
            if (previousDemand <= 0) {
                continue;
            }

            Long activeSince = this.activeSinceTicks.get(side);

            if (activeSince == null || activeSince >= previousTick) {
                return;
            }
        }

        int countDemandingSides = this.countDemandingSidesPreviousTick(currentTick);
        if (countDemandingSides == 0) {
            return;
        }

        int totalEnergyExtracted = this.getTotalExtractedPreviousTick(currentTick);

        int fairShare = totalEnergyExtracted / countDemandingSides;
        int unusedQuota = 0;

        for (Direction side : Direction.values()) {
            int demand = this.getEffectivePreviousDemand(
                side,
                currentTick
            );

            if (demand <= 0) {
                continue;
            }

            unusedQuota += Math.max(
                0,
                fairShare - demand
            );
        }

        Map<Direction, Integer> targetShares = new EnumMap<>(Direction.class);

        for (Direction side : Direction.values()) {
            int demand = this.getEffectivePreviousDemand(
                side,
                currentTick
            );

            if (demand <= 0) {
                continue;
            }

            targetShares.put(
                side,
                Math.min(
                    demand,
                    fairShare
                )
            );
        }

        int remainingToDistribute = unusedQuota + totalEnergyExtracted % countDemandingSides;

        while (remainingToDistribute > 0) {
            int eligibleSides = 0;

            for (Direction side : targetShares.keySet()) {
                int demand = this.getEffectivePreviousDemand(
                    side,
                    currentTick
                );

                int currentShare = targetShares.get(side);

                if (currentShare < demand) {
                    eligibleSides++;
                }
            }

            if (eligibleSides == 0) {
                break;
            }

            int portion = Math.max(
                1,
                remainingToDistribute / eligibleSides
            );

            for (Direction side : targetShares.keySet()) {
                int demand = this.getEffectivePreviousDemand(
                    side,
                    currentTick
                );

                int currentShare = targetShares.get(side);

                int remainingDemand = demand - currentShare;
                int addition = Math.min(
                    portion,
                    Math.min(
                        remainingDemand,
                        remainingToDistribute
                    )
                );
                if (addition <= 0) {
                    continue;
                }

                targetShares.put(
                    side,
                    currentShare + addition
                );
                remainingToDistribute -= addition;
            }
        }

        for (Direction side : Direction.values()) {
            int previousDemand = this.getEffectivePreviousDemand(
                side,
                currentTick
            );

            if (previousDemand <= 0) {
                continue;
            }

            int actualExtracted = this.getPreviousExtractedBySide(
                side,
                currentTick
            );

            int targetShare = targetShares.get(side);
            int delta = targetShare - actualExtracted;

            long currentSideBalance = this.fairnessCredits.getOrDefault(
                side,
                0L
            );

            long newBalance = Math.clamp(
                currentSideBalance + delta,
                (long) -this.maxTransferPerTick,
                this.maxTransferPerTick
            );

            this.fairnessCredits.put(
                side,
                newBalance
            );
        }
    }
}
