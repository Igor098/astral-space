package ru.greatdragonx.astralspace.common.energy;

import net.minecraft.core.Direction;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class EnergyDistributionManagerTest {
    private static void recordCommittedRequest(
        @NonNull EnergyDistributionManager manager,
        Direction side,
        int requested,
        long currentTick
    )
    {
        try (Transaction tx = Transaction.openRoot()) {
            manager.recordRequest(
                side,
                requested,
                currentTick,
                tx
            );
            tx.commit();
        }
    }

    private static int getCommittedAdaptiveAmount(
        @NonNull EnergyDistributionManager manager,
        @NonNull Direction side,
        int requested,
        int remainingGlobalBudget,
        int storedEnergy,
        long currentTick
    )
    {
        try (Transaction tx = Transaction.openRoot()) {
            int allowed = manager.getAllowedAdaptiveAmount(
                side,
                requested,
                remainingGlobalBudget,
                storedEnergy,
                currentTick,
                tx
            );
            tx.commit();
            return allowed;
        }
    }

    private static @NonNull EnergyDistributionManager managerWithThreeSideCredits()
        throws ReflectiveOperationException
    {
        EnergyDistributionManager manager = new EnergyDistributionManager(80);

        for (long tick = 99; tick <= 100; tick++) {
            recordCommittedRequest(
                manager,
                Direction.NORTH,
                80,
                tick
            );
            recordCommittedRequest(
                manager,
                Direction.SOUTH,
                80,
                tick
            );
            recordCommittedRequest(
                manager,
                Direction.EAST,
                80,
                tick
            );
        }

        try (Transaction tx = Transaction.openRoot()) {
            manager.recordExtraction(
                Direction.NORTH,
                30,
                100,
                tx
            );
            manager.recordExtraction(
                Direction.SOUTH,
                10,
                100,
                tx
            );
            tx.commit();
        }

        getCommittedAdaptiveAmount(
            manager,
            Direction.NORTH,
            80,
            80,
            80,
            101
        );

        assertTrue(fairnessCredits(manager).getOrDefault(
            Direction.EAST,
            0L
        ) > 0);

        for (long tick : new long[]{110, 119}) {
            recordCommittedRequest(
                manager,
                Direction.NORTH,
                80,
                tick
            );
            recordCommittedRequest(
                manager,
                Direction.SOUTH,
                80,
                tick
            );
        }

        assertFalse(manager.isActive(
            Direction.EAST,
            120
        ));
        assertEquals(
            2,
            manager.countActiveSides(120)
        );

        return manager;
    }

    @Test
    void shouldTrackActiveSide() {
        EnergyDistributionManager manager = new EnergyDistributionManager(80);

        recordCommittedRequest(
            manager,
            Direction.NORTH,
            80,
            100
        );

        assertTrue(manager.isActive(
            Direction.NORTH,
            100
        ));
        assertTrue(manager.isActive(
            Direction.NORTH,
            119
        ));
        assertFalse(manager.isActive(
            Direction.NORTH,
            120
        ));

        assertFalse(manager.isActive(
            Direction.SOUTH,
            100
        ));

        recordCommittedRequest(
            manager,
            Direction.EAST,
            0,
            100
        );

        assertFalse(manager.isActive(
            Direction.EAST,
            100
        ));
    }

    @Test
    void shouldCountActiveSides() {
        EnergyDistributionManager manager = new EnergyDistributionManager(80);
        assertEquals(
            0,
            manager.countActiveSides(100)
        );

        recordCommittedRequest(
            manager,
            Direction.NORTH,
            80,
            100
        );

        assertEquals(
            1,
            manager.countActiveSides(100)
        );

        recordCommittedRequest(
            manager,
            Direction.SOUTH,
            80,
            105
        );

        assertEquals(
            2,
            manager.countActiveSides(105)
        );

        assertTrue(manager.isActive(
            Direction.NORTH,
            119
        ));
        assertTrue(manager.isActive(
            Direction.SOUTH,
            119
        ));
        assertEquals(
            2,
            manager.countActiveSides(119)
        );

        assertFalse(manager.isActive(
            Direction.NORTH,
            120
        ));
        assertEquals(
            1,
            manager.countActiveSides(120)
        );

        assertFalse(manager.isActive(
            Direction.SOUTH,
            125
        ));
        assertEquals(
            0,
            manager.countActiveSides(125)
        );
    }

    @Test
    void shouldCalculateBaseQuota() {
        EnergyDistributionManager manager = new EnergyDistributionManager(80);

        recordCommittedRequest(
            manager,
            Direction.NORTH,
            80,
            100
        );

        assertEquals(
            80,
            manager.getBaseQuota(
                Direction.NORTH,
                100
            )
        );
        assertEquals(
            0,
            manager.getBaseQuota(
                Direction.SOUTH,
                100
            )
        );

        recordCommittedRequest(
            manager,
            Direction.SOUTH,
            40,
            105
        );
        assertEquals(
            40,
            manager.getBaseQuota(
                Direction.NORTH,
                105
            )
        );
        assertEquals(
            40,
            manager.getBaseQuota(
                Direction.SOUTH,
                105
            )
        );

        recordCommittedRequest(
            manager,
            Direction.EAST,
            20,
            108
        );
        assertEquals(
            26,
            manager.getBaseQuota(
                Direction.NORTH,
                108
            )
        );
        assertEquals(
            26,
            manager.getBaseQuota(
                Direction.SOUTH,
                108
            )
        );
        assertEquals(
            26,
            manager.getBaseQuota(
                Direction.EAST,
                108
            )
        );
    }

    @Test
    void shouldRestoreSideUsageAfterRollback() {
        EnergyDistributionManager manager = new EnergyDistributionManager(80);

        try (Transaction tx = Transaction.openRoot()) {
            manager.recordExtraction(
                Direction.NORTH,
                30,
                100,
                tx
            );
            assertEquals(
                30,
                manager.getExtractedBySide(
                    Direction.NORTH,
                    100
                )
            );

            tx.commit();
        }

        assertEquals(
            30,
            manager.getExtractedBySide(
                Direction.NORTH,
                100
            )
        );

        try (Transaction tx = Transaction.openRoot()) {
            manager.recordExtraction(
                Direction.NORTH,
                20,
                100,
                tx
            );
            assertEquals(
                50,
                manager.getExtractedBySide(
                    Direction.NORTH,
                    100
                )
            );
        }

        assertEquals(
            30,
            manager.getExtractedBySide(
                Direction.NORTH,
                100
            )
        );

        assertEquals(
            0,
            manager.getExtractedBySide(
                Direction.NORTH,
                101
            )
        );
    }

    @Test
    void shouldRestoreMultipleSidesAfterNestedRollback() {
        EnergyDistributionManager manager = new EnergyDistributionManager(80);

        try (Transaction root = Transaction.openRoot()) {
            manager.recordExtraction(
                Direction.NORTH,
                30,
                100,
                root
            );
            assertEquals(
                30,
                manager.getExtractedBySide(
                    Direction.NORTH,
                    100
                )
            );

            manager.recordExtraction(
                Direction.SOUTH,
                20,
                100,
                root
            );
            assertEquals(
                20,
                manager.getExtractedBySide(
                    Direction.SOUTH,
                    100
                )
            );

            try (Transaction nested = Transaction.open(root)) {
                manager.recordExtraction(
                    Direction.NORTH,
                    10,
                    100,
                    nested
                );
                manager.recordExtraction(
                    Direction.EAST,
                    15,
                    100,
                    nested
                );

                assertEquals(
                    40,
                    manager.getExtractedBySide(
                        Direction.NORTH,
                        100
                    )
                );
                assertEquals(
                    15,
                    manager.getExtractedBySide(
                        Direction.EAST,
                        100
                    )
                );
            }

            assertEquals(
                30,
                manager.getExtractedBySide(
                    Direction.NORTH,
                    100
                )
            );
            assertEquals(
                20,
                manager.getExtractedBySide(
                    Direction.SOUTH,
                    100
                )
            );
            assertEquals(
                0,
                manager.getExtractedBySide(
                    Direction.EAST,
                    100
                )
            );

            root.commit();
        }

        assertEquals(
            30,
            manager.getExtractedBySide(
                Direction.NORTH,
                100
            )
        );
        assertEquals(
            20,
            manager.getExtractedBySide(
                Direction.SOUTH,
                100
            )
        );
        assertEquals(
            0,
            manager.getExtractedBySide(
                Direction.EAST,
                100
            )
        );
    }

    @Test
    void shouldCalculateRemainingBaseQuota() {
        EnergyDistributionManager manager = new EnergyDistributionManager(80);

        recordCommittedRequest(
            manager,
            Direction.NORTH,
            80,
            100
        );

        recordCommittedRequest(
            manager,
            Direction.SOUTH,
            80,
            100
        );

        assertEquals(
            40,
            manager.getBaseQuota(
                Direction.NORTH,
                100
            )
        );
        assertEquals(
            40,
            manager.getBaseQuota(
                Direction.SOUTH,
                100
            )
        );

        try (Transaction tx = Transaction.openRoot()) {
            manager.recordExtraction(
                Direction.NORTH,
                25,
                100,
                tx
            );
            assertEquals(
                15,
                manager.getRemainingBaseQuota(
                    Direction.NORTH,
                    100
                )
            );
            assertEquals(
                40,
                manager.getRemainingBaseQuota(
                    Direction.SOUTH,
                    100
                )
            );

            manager.recordExtraction(
                Direction.NORTH,
                20,
                100,
                tx
            );
            assertEquals(
                0,
                manager.getRemainingBaseQuota(
                    Direction.NORTH,
                    100
                )
            );
            assertEquals(
                40,
                manager.getRemainingBaseQuota(
                    Direction.SOUTH,
                    100
                )
            );

            tx.commit();
        }

        assertEquals(
            0,
            manager.getRemainingBaseQuota(
                Direction.NORTH,
                100
            )
        );
        assertEquals(
            40,
            manager.getRemainingBaseQuota(
                Direction.SOUTH,
                100
            )
        );

        assertEquals(
            40,
            manager.getRemainingBaseQuota(
                Direction.NORTH,
                101
            )
        );
        assertEquals(
            40,
            manager.getRemainingBaseQuota(
                Direction.SOUTH,
                101
            )
        );
    }

    @Test
    void shouldLimitRequestByRemainingBaseQuota() {
        EnergyDistributionManager manager = new EnergyDistributionManager(80);

        recordCommittedRequest(
            manager,
            Direction.NORTH,
            80,
            100
        );

        recordCommittedRequest(
            manager,
            Direction.SOUTH,
            80,
            100
        );

        int allowed = manager.getAllowedBaseAmount(
            Direction.NORTH,
            60,
            100
        );
        assertEquals(
            40,
            allowed
        );

        try (Transaction tx = Transaction.openRoot()) {
            manager.recordExtraction(
                Direction.NORTH,
                25,
                100,
                tx
            );

            tx.commit();
        }

        assertEquals(
            15,
            manager.getAllowedBaseAmount(
                Direction.NORTH,
                50,
                100
            )
        );

        assertEquals(
            10,
            manager.getAllowedBaseAmount(
                Direction.NORTH,
                10,
                100
            )
        );

        assertEquals(
            40,
            manager.getAllowedBaseAmount(
                Direction.SOUTH,
                80,
                100
            )
        );

        assertEquals(
            0,
            manager.getAllowedBaseAmount(
                Direction.EAST,
                80,
                100
            )
        );

        assertEquals(
            0,
            manager.getAllowedBaseAmount(
                Direction.NORTH,
                0,
                100
            )
        );
    }

    @Test
    void shouldReadPreviousTickUsage() {
        EnergyDistributionManager manager = new EnergyDistributionManager(80);

        try (Transaction tx = Transaction.openRoot()) {
            manager.recordExtraction(
                Direction.NORTH,
                30,
                100,
                tx
            );

            tx.commit();
        }

        assertEquals(
            30,
            manager.getPreviousExtractedBySide(
                Direction.NORTH,
                101
            )
        );

        try (Transaction tx = Transaction.openRoot()) {
            manager.recordExtraction(
                Direction.NORTH,
                20,
                101,
                tx
            );

            tx.commit();
        }

        assertEquals(
            30,
            manager.getPreviousExtractedBySide(
                Direction.NORTH,
                101
            )
        );

        assertEquals(
            20,
            manager.getPreviousExtractedBySide(
                Direction.NORTH,
                102
            )
        );

        assertEquals(
            0,
            manager.getPreviousExtractedBySide(
                Direction.NORTH,
                105
            )
        );
    }

    @Test
    void shouldRestoreUsageHistoryAfterRollback() {
        EnergyDistributionManager manager = new EnergyDistributionManager(80);

        try (Transaction tx = Transaction.openRoot()) {
            manager.recordExtraction(
                Direction.NORTH,
                30,
                100,
                tx
            );
            assertEquals(
                30,
                manager.getPreviousExtractedBySide(
                    Direction.NORTH,
                    101
                )
            );
            tx.commit();
        }
        assertEquals(
            30,
            manager.getPreviousExtractedBySide(
                Direction.NORTH,
                101
            )
        );

        try (Transaction tx = Transaction.openRoot()) {
            manager.recordExtraction(
                Direction.NORTH,
                20,
                101,
                tx
            );
            assertEquals(
                20,
                manager.getExtractedBySide(
                    Direction.NORTH,
                    101
                )
            );
            assertEquals(
                30,
                manager.getPreviousExtractedBySide(
                    Direction.NORTH,
                    101
                )
            );
        }

        assertEquals(
            30,
            manager.getExtractedBySide(
                Direction.NORTH,
                100
            )
        );

        assertEquals(
            0,
            manager.getExtractedBySide(
                Direction.NORTH,
                101
            )
        );

        assertEquals(
            30,
            manager.getPreviousExtractedBySide(
                Direction.NORTH,
                101
            )
        );
    }

    @Test
    void shouldTrackMaximumDemandPerSide() {
        EnergyDistributionManager manager = new EnergyDistributionManager(80);

        recordCommittedRequest(
            manager,
            Direction.NORTH,
            20,
            100
        );

        recordCommittedRequest(
            manager,
            Direction.NORTH,
            60,
            100
        );

        recordCommittedRequest(
            manager,
            Direction.NORTH,
            30,
            100
        );

        recordCommittedRequest(
            manager,
            Direction.SOUTH,
            40,
            100
        );

        assertEquals(
            60,
            manager.getMaxRequestedBySide(
                Direction.NORTH,
                100
            )
        );
        assertEquals(
            40,
            manager.getMaxRequestedBySide(
                Direction.SOUTH,
                100
            )
        );
        assertEquals(
            0,
            manager.getMaxRequestedBySide(
                Direction.EAST,
                100
            )
        );
        assertEquals(
            0,
            manager.getMaxRequestedBySide(
                Direction.NORTH,
                101
            )
        );

        recordCommittedRequest(
            manager,
            Direction.NORTH,
            10,
            101
        );
        assertEquals(
            10,
            manager.getMaxRequestedBySide(
                Direction.NORTH,
                101
            )
        );
        assertEquals(
            0,
            manager.getMaxRequestedBySide(
                Direction.SOUTH,
                101
            )
        );
    }

    @Test
    void shouldReadPreviousTickDemand() {
        EnergyDistributionManager manager = new EnergyDistributionManager(80);

        recordCommittedRequest(
            manager,
            Direction.NORTH,
            80,
            100
        );
        assertEquals(
            80,
            manager.getPreviousMaxRequestedBySide(
                Direction.NORTH,
                101
            )
        );

        recordCommittedRequest(
            manager,
            Direction.SOUTH,
            40,
            100
        );
        assertEquals(
            40,
            manager.getPreviousMaxRequestedBySide(
                Direction.SOUTH,
                101
            )
        );

        recordCommittedRequest(
            manager,
            Direction.NORTH,
            30,
            101
        );
        assertEquals(
            80,
            manager.getPreviousMaxRequestedBySide(
                Direction.NORTH,
                101
            )
        );
        assertEquals(
            40,
            manager.getPreviousMaxRequestedBySide(
                Direction.SOUTH,
                101
            )
        );

        assertEquals(
            30,
            manager.getPreviousMaxRequestedBySide(
                Direction.NORTH,
                102
            )
        );
        assertEquals(
            0,
            manager.getPreviousMaxRequestedBySide(
                Direction.SOUTH,
                102
            )
        );
        assertEquals(
            0,
            manager.getPreviousMaxRequestedBySide(
                Direction.NORTH,
                105
            )
        );
    }

    @Test
    void shouldCalculateProtectedRemainingQuota() {
        EnergyDistributionManager manager = new EnergyDistributionManager(80);

        recordCommittedRequest(
            manager,
            Direction.NORTH,
            10,
            99
        );
        recordCommittedRequest(
            manager,
            Direction.SOUTH,
            80,
            99
        );

        assertEquals(
            10,
            manager.getProtectedRemainingQuota(
                Direction.NORTH,
                80,
                100
            )
        );

        try (Transaction tx = Transaction.openRoot()) {
            manager.recordExtraction(
                Direction.NORTH,
                5,
                100,
                tx
            );
            assertEquals(
                5,
                manager.getProtectedRemainingQuota(
                    Direction.NORTH,
                    80,
                    100
                )
            );

            manager.recordExtraction(
                Direction.NORTH,
                5,
                100,
                tx
            );

            tx.commit();
        }
        assertEquals(
            0,
            manager.getProtectedRemainingQuota(
                Direction.NORTH,
                80,
                100
            )
        );

        recordCommittedRequest(
            manager,
            Direction.NORTH,
            80,
            100
        );
        assertEquals(
            30,
            manager.getProtectedRemainingQuota(
                Direction.NORTH,
                80,
                100
            )
        );
    }

    @Test
    void shouldReduceProtectedQuotaWithoutRecentDemand() {
        EnergyDistributionManager manager = new EnergyDistributionManager(80);

        recordCommittedRequest(
            manager,
            Direction.NORTH,
            40,
            98
        );
        recordCommittedRequest(
            manager,
            Direction.SOUTH,
            40,
            98
        );

        assertEquals(
            40,
            manager.getProtectedRemainingQuota(
                Direction.NORTH,
                80,
                100
            )
        );

        try (Transaction tx = Transaction.openRoot()) {
            manager.recordExtraction(
                Direction.NORTH,
                15,
                100,
                tx
            );

            tx.commit();
        }

        assertEquals(
            25,
            manager.getProtectedRemainingQuota(
                Direction.NORTH,
                80,
                100
            )
        );
    }

    @Test
    void shouldBorrowUnusedProtectedQuota() {
        EnergyDistributionManager manager = new EnergyDistributionManager(80);

        recordCommittedRequest(
            manager,
            Direction.NORTH,
            10,
            99
        );
        recordCommittedRequest(
            manager,
            Direction.SOUTH,
            80,
            99
        );

        recordCommittedRequest(
            manager,
            Direction.SOUTH,
            80,
            100
        );

        assertEquals(
            70,
            getCommittedAdaptiveAmount(
                manager,
                Direction.SOUTH,
                80,
                80,
                80,
                100
            )
        );

        try (Transaction tx = Transaction.openRoot()) {
            manager.recordExtraction(
                Direction.SOUTH,
                70,
                100,
                tx
            );

            tx.commit();
        }

        recordCommittedRequest(
            manager,
            Direction.NORTH,
            10,
            100
        );
        assertEquals(
            10,
            getCommittedAdaptiveAmount(
                manager,
                Direction.NORTH,
                10,
                10,
                10,
                100
            )
        );
    }

    @Test
    void shouldDistributeEnergyInReverseRequestOrder() {
        EnergyDistributionManager manager = new EnergyDistributionManager(80);

        recordCommittedRequest(
            manager,
            Direction.NORTH,
            10,
            99
        );
        recordCommittedRequest(
            manager,
            Direction.SOUTH,
            80,
            99
        );

        recordCommittedRequest(
            manager,
            Direction.NORTH,
            10,
            100
        );

        int allowed = getCommittedAdaptiveAmount(
            manager,
            Direction.NORTH,
            10,
            80,
            80,
            100
        );

        assertEquals(
            10,
            allowed
        );

        try (Transaction tx = Transaction.openRoot()) {
            manager.recordExtraction(
                Direction.NORTH,
                10,
                100,
                tx
            );

            tx.commit();
        }

        recordCommittedRequest(
            manager,
            Direction.SOUTH,
            80,
            100
        );

        int allowedSouth = getCommittedAdaptiveAmount(
            manager,
            Direction.SOUTH,
            80,
            70,
            70,
            100
        );

        assertEquals(
            70,
            allowedSouth
        );
    }

    @Test
    void shouldRecoverAfterSecondSideBecomesActive() {
        EnergyDistributionManager manager = new EnergyDistributionManager(80);

        recordCommittedRequest(
            manager,
            Direction.NORTH,
            80,
            100
        );

        assertEquals(
            80,
            getCommittedAdaptiveAmount(
                manager,
                Direction.NORTH,
                80,
                80,
                80,
                100
            )
        );

        try (Transaction tx = Transaction.openRoot()) {
            manager.recordExtraction(
                Direction.NORTH,
                80,
                100,
                tx
            );

            tx.commit();
        }

        recordCommittedRequest(
            manager,
            Direction.SOUTH,
            80,
            100
        );
        assertEquals(
            0,
            getCommittedAdaptiveAmount(
                manager,
                Direction.SOUTH,
                80,
                0,
                0,
                100
            )
        );

        recordCommittedRequest(
            manager,
            Direction.NORTH,
            80,
            101
        );

        assertEquals(
            40,
            getCommittedAdaptiveAmount(
                manager,
                Direction.NORTH,
                80,
                80,
                80,
                101
            )
        );

        try (Transaction tx = Transaction.openRoot()) {
            manager.recordExtraction(
                Direction.NORTH,
                40,
                101,
                tx
            );

            tx.commit();
        }

        recordCommittedRequest(
            manager,
            Direction.SOUTH,
            80,
            101
        );
        assertEquals(
            40,
            getCommittedAdaptiveAmount(
                manager,
                Direction.SOUTH,
                80,
                40,
                40,
                101
            )
        );
    }

    @Test
    void shouldDistributeEnergyWhenBufferIsLow() {
        EnergyDistributionManager manager = new EnergyDistributionManager(80);

        recordCommittedRequest(
            manager,
            Direction.NORTH,
            80,
            99
        );
        recordCommittedRequest(
            manager,
            Direction.SOUTH,
            80,
            99
        );

        recordCommittedRequest(
            manager,
            Direction.NORTH,
            80,
            100
        );
        int allowedNorth = getCommittedAdaptiveAmount(
            manager,
            Direction.NORTH,
            80,
            80,
            40,
            100
        );
        assertEquals(
            20,
            allowedNorth
        );

        try (Transaction tx = Transaction.openRoot()) {
            manager.recordExtraction(
                Direction.NORTH,
                allowedNorth,
                100,
                tx
            );
            tx.commit();
        }

        recordCommittedRequest(
            manager,
            Direction.SOUTH,
            80,
            100
        );
        int allowedSouth = getCommittedAdaptiveAmount(
            manager,
            Direction.SOUTH,
            80,
            60,
            20,
            100
        );
        assertEquals(
            20,
            allowedSouth
        );

        try (Transaction tx = Transaction.openRoot()) {
            manager.recordExtraction(
                Direction.SOUTH,
                allowedSouth,
                100,
                tx
            );
            tx.commit();
        }

        assertEquals(
            20,
            manager.getExtractedBySide(
                Direction.NORTH,
                100
            )
        );
        assertEquals(
            20,
            manager.getExtractedBySide(
                Direction.SOUTH,
                100
            )
        );
    }

    @Test
    void shouldShareEnergyFairlyWhenProductionOccursBetweenPullRequests() {
        EnergyDistributionManager manager = new EnergyDistributionManager(80);

        recordCommittedRequest(
            manager,
            Direction.NORTH,
            128,
            99
        );
        recordCommittedRequest(
            manager,
            Direction.SOUTH,
            128,
            99
        );

        int storedEnergy = 20;
        int totalNorth = 0;
        int totalSouth = 0;

        for (long tick = 100; tick < 121; tick++) {
            recordCommittedRequest(
                manager,
                Direction.SOUTH,
                128,
                tick
            );
            int allowedSouth = getCommittedAdaptiveAmount(
                manager,
                Direction.SOUTH,
                128,
                80,
                storedEnergy,
                tick
            );
            int extractedSouth = Math.min(
                allowedSouth,
                storedEnergy
            );

            try (Transaction tx = Transaction.openRoot()) {
                manager.recordExtraction(
                    Direction.SOUTH,
                    extractedSouth,
                    tick,
                    tx
                );
                tx.commit();
            }

            storedEnergy -= extractedSouth;

            if (tick > 100) {
                totalSouth += extractedSouth;
            }

            storedEnergy += 40;

            recordCommittedRequest(
                manager,
                Direction.NORTH,
                128,
                tick
            );
            int allowedNorth = getCommittedAdaptiveAmount(
                manager,
                Direction.NORTH,
                128,
                80 - extractedSouth,
                storedEnergy,
                tick
            );
            int extractedNorth = Math.min(
                allowedNorth,
                storedEnergy
            );

            if (tick > 100) {
                assertEquals(
                    20,
                    extractedSouth
                );
                assertEquals(
                    20,
                    extractedNorth
                );
            }

            try (Transaction tx = Transaction.openRoot()) {
                manager.recordExtraction(
                    Direction.NORTH,
                    extractedNorth,
                    tick,
                    tx
                );
                tx.commit();
            }

            storedEnergy -= extractedNorth;

            if (tick > 100) {
                totalNorth += extractedNorth;
            }
        }

        assertEquals(
            800,
            totalNorth + totalSouth
        );
        assertEquals(
            20,
            storedEnergy
        );

        assertEquals(
            400,
            totalSouth,
            "SOUTH не должен голодать из-за порядка Pull-запросов"
        );
        assertEquals(
            400,
            totalNorth,
            "NORTH не должен получать львиную долю энергии"
        );
    }

    @Test
    void shouldAllowFullExtractionAfterOtherSideDisconnects() {
        EnergyDistributionManager manager = new EnergyDistributionManager(80);

        recordCommittedRequest(
            manager,
            Direction.NORTH,
            128,
            100
        );
        recordCommittedRequest(
            manager,
            Direction.SOUTH,
            128,
            100
        );

        recordCommittedRequest(
            manager,
            Direction.NORTH,
            128,
            101
        );
        recordCommittedRequest(
            manager,
            Direction.SOUTH,
            128,
            101
        );

        try (Transaction tx = Transaction.openRoot()) {
            manager.recordExtraction(
                Direction.NORTH,
                30,
                101,
                tx
            );
            manager.recordExtraction(
                Direction.SOUTH,
                10,
                101,
                tx
            );
            tx.commit();
        }

        recordCommittedRequest(
            manager,
            Direction.NORTH,
            40,
            102
        );

        getCommittedAdaptiveAmount(
            manager,
            Direction.NORTH,
            40,
            80,
            40,
            102
        );

        recordCommittedRequest(
            manager,
            Direction.NORTH,
            40,
            121
        );

        assertFalse(manager.isActive(
            Direction.SOUTH,
            121
        ));
        assertTrue(manager.isActive(
            Direction.NORTH,
            121
        ));
        assertEquals(
            1,
            manager.countActiveSides(121)
        );

        int allowed = getCommittedAdaptiveAmount(
            manager,
            Direction.NORTH,
            40,
            80,
            40,
            121
        );

        assertEquals(
            40,
            allowed,
            "Единственный потребитель не должен ограничиваться старым fairnessCredits"
        );
    }

    @Test
    void shouldRedistributeEnergyAfterSideReconnects() {
        EnergyDistributionManager manager = new EnergyDistributionManager(80);

        recordCommittedRequest(
            manager,
            Direction.NORTH,
            128,
            99
        );
        recordCommittedRequest(
            manager,
            Direction.SOUTH,
            128,
            99
        );

        assertEquals(
            10,
            pullEnergy(
                manager,
                Direction.SOUTH,
                100,
                20,
                80
            )
        );
        assertEquals(
            30,
            pullEnergy(
                manager,
                Direction.NORTH,
                100,
                50,
                70
            )
        );

        assertEquals(
            20,
            pullEnergy(
                manager,
                Direction.SOUTH,
                101,
                20,
                80
            )
        );
        assertEquals(
            20,
            pullEnergy(
                manager,
                Direction.NORTH,
                101,
                40,
                60
            )
        );

        for (long tick = 102; tick <= 121; tick++) {
            pullEnergy(
                manager,
                Direction.NORTH,
                tick,
                40,
                80
            );
        }

        assertFalse(manager.isActive(
            Direction.SOUTH,
            122
        ));
        assertEquals(
            1,
            manager.countActiveSides(122)
        );

        assertEquals(
            40,
            pullEnergy(
                manager,
                Direction.NORTH,
                122,
                40,
                80
            )
        );

        assertEquals(
            40,
            pullEnergy(
                manager,
                Direction.NORTH,
                123,
                40,
                80
            )
        );
        assertEquals(
            0,
            pullEnergy(
                manager,
                Direction.SOUTH,
                123,
                0,
                40
            )
        );

        assertEquals(
            40,
            pullEnergy(
                manager,
                Direction.NORTH,
                124,
                40,
                80
            ),
            "NORTH: первый тик возвращения SOUTH"
        );

        assertEquals(
            20,
            pullEnergy(
                manager,
                Direction.SOUTH,
                124,
                20,
                40
            ),
            "SOUTH должен успешно зарегистрироваться при возвращении"
        );
        assertTrue(manager.isActive(
            Direction.SOUTH,
            124
        ));
        assertEquals(
            2,
            manager.countActiveSides(124)
        );

        for (long tick = 125; tick <= 128; tick++) {
            int north = pullEnergy(
                manager,
                Direction.NORTH,
                tick,
                40,
                80
            );
            int south = pullEnergy(
                manager,
                Direction.SOUTH,
                tick,
                20,
                60
            );

            assertEquals(
                20,
                north,
                "NORTH: тик " + tick
            );
            assertEquals(
                20,
                south,
                "SOUTH: тик " + tick
            );
        }
    }

    @Test
    void shouldNotWasteEnergyWhenConsumersHaveDifferentDemand() {
        EnergyDistributionManager manager = new EnergyDistributionManager(80);

        recordCommittedRequest(
            manager,
            Direction.NORTH,
            10,
            99
        );
        recordCommittedRequest(
            manager,
            Direction.SOUTH,
            128,
            99
        );

        assertEquals(
            10,
            pullEnergy(
                manager,
                Direction.NORTH,
                10,
                100,
                40,
                80
            )
        );

        assertEquals(
            30,
            pullEnergy(
                manager,
                Direction.SOUTH,
                128,
                100,
                30,
                70
            )
        );

        assertEquals(
            10,
            pullEnergy(
                manager,
                Direction.NORTH,
                10,
                101,
                40,
                80
            )
        );

        assertEquals(
            30,
            pullEnergy(
                manager,
                Direction.SOUTH,
                128,
                101,
                30,
                70
            )
        );
    }

    @Test
    void shouldRollbackRequestAfterRootTransactionIsAborted() {
        EnergyDistributionManager manager = new EnergyDistributionManager(80);

        try (Transaction tx = Transaction.openRoot()) {
            manager.recordRequest(
                Direction.NORTH,
                64,
                100,
                tx
            );

            assertTrue(manager.isActive(
                Direction.NORTH,
                100
            ));
            assertEquals(
                1,
                manager.countActiveSides(100)
            );
            assertEquals(
                64,
                manager.getMaxRequestedBySide(
                    Direction.NORTH,
                    100
                )
            );
        }

        assertFalse(manager.isActive(
            Direction.NORTH,
            100
        ));
        assertEquals(
            0,
            manager.countActiveSides(100)
        );
        assertEquals(
            0,
            manager.getMaxRequestedBySide(
                Direction.NORTH,
                100
            )
        );
        assertEquals(
            0,
            manager.getPreviousMaxRequestedBySide(
                Direction.NORTH,
                101
            )
        );
    }

    @Test
    void shouldRollbackSimulatedRequestWithoutUndoingParentRequest() {
        EnergyDistributionManager manager = new EnergyDistributionManager(80);

        try (Transaction root = Transaction.openRoot()) {
            manager.recordRequest(
                Direction.NORTH,
                30,
                100,
                root
            );

            try (Transaction simulated = Transaction.open(root)) {
                manager.recordRequest(
                    Direction.NORTH,
                    80,
                    100,
                    simulated
                );
                manager.recordRequest(
                    Direction.SOUTH,
                    128,
                    100,
                    simulated
                );

                assertEquals(
                    80,
                    manager.getMaxRequestedBySide(
                        Direction.NORTH,
                        100
                    )
                );
                assertTrue(manager.isActive(
                    Direction.SOUTH,
                    100
                ));
            }

            assertEquals(
                30,
                manager.getMaxRequestedBySide(
                    Direction.NORTH,
                    100
                )
            );
            assertEquals(
                0,
                manager.getMaxRequestedBySide(
                    Direction.SOUTH,
                    100
                )
            );
            assertFalse(manager.isActive(
                Direction.SOUTH,
                100
            ));
            root.commit();
        }

        assertEquals(
            30,
            manager.getMaxRequestedBySide(
                Direction.NORTH,
                100
            )
        );
        assertFalse(manager.isActive(
            Direction.SOUTH,
            100
        ));
    }

    @Test
    void shouldRollbackChildRequestWhenParentTransactionIsAborted() {
        EnergyDistributionManager manager = new EnergyDistributionManager(80);

        try (Transaction root = Transaction.openRoot()) {
            try (Transaction child = Transaction.open(root)) {
                manager.recordRequest(
                    Direction.EAST,
                    128,
                    100,
                    child
                );
                child.commit();
            }

            assertTrue(manager.isActive(
                Direction.EAST,
                100
            ));
            assertEquals(
                128,
                manager.getMaxRequestedBySide(
                    Direction.EAST,
                    100
                )
            );
        }

        assertFalse(manager.isActive(
            Direction.EAST,
            100
        ));
        assertEquals(
            0,
            manager.getMaxRequestedBySide(
                Direction.EAST,
                100
            )
        );
        assertEquals(
            0,
            manager.getPreviousMaxRequestedBySide(
                Direction.EAST,
                101
            )
        );
    }

    @Test
    void shouldRestorePreviousDemandHistoryAfterRollback() {
        EnergyDistributionManager manager = new EnergyDistributionManager(80);
        recordCommittedRequest(
            manager,
            Direction.NORTH,
            60,
            100
        );

        try (Transaction tx = Transaction.openRoot()) {
            manager.recordRequest(
                Direction.NORTH,
                10,
                101,
                tx
            );
            manager.recordRequest(
                Direction.SOUTH,
                40,
                101,
                tx
            );

            assertEquals(
                60,
                manager.getPreviousMaxRequestedBySide(
                    Direction.NORTH,
                    101
                )
            );
            assertEquals(
                10,
                manager.getMaxRequestedBySide(
                    Direction.NORTH,
                    101
                )
            );
            assertEquals(
                40,
                manager.getMaxRequestedBySide(
                    Direction.SOUTH,
                    101
                )
            );
        }

        assertEquals(
            60,
            manager.getMaxRequestedBySide(
                Direction.NORTH,
                100
            )
        );
        assertEquals(
            60,
            manager.getPreviousMaxRequestedBySide(
                Direction.NORTH,
                101
            )
        );
        assertEquals(
            0,
            manager.getMaxRequestedBySide(
                Direction.NORTH,
                101
            )
        );
        assertEquals(
            0,
            manager.getPreviousMaxRequestedBySide(
                Direction.SOUTH,
                101
            )
        );
        assertFalse(manager.isActive(
            Direction.SOUTH,
            101
        ));
    }

    private static int pullEnergy(
        EnergyDistributionManager manager,
        Direction side,
        long currentTick,
        int storedEnergy,
        int remainingGlobalBudget
    )
    {
        return pullEnergy(
            manager,
            side,
            128,
            currentTick,
            storedEnergy,
            remainingGlobalBudget
        );
    }

    private static int pullEnergy(
        @NonNull EnergyDistributionManager manager,
        Direction side,
        int requested,
        long currentTick,
        int storedEnergy,
        int remainingGlobalBudget
    )
    {
        try (Transaction tx = Transaction.openRoot()) {
            manager.recordRequest(
                side,
                requested,
                currentTick,
                tx
            );

            int extracted = manager.getAllowedAdaptiveAmount(
                side,
                requested,
                remainingGlobalBudget,
                storedEnergy,
                currentTick,
                tx
            );

            if (extracted > 0) {
                manager.recordExtraction(
                    side,
                    extracted,
                    currentTick,
                    tx
                );
                tx.commit();
            }
            return extracted;
        }
    }

    @Test
    void shouldDistributeEnergyFairlyAmongThreeConsumers() {
        EnergyDistributionManager manager = new EnergyDistributionManager(80);

        recordCommittedRequest(
            manager,
            Direction.NORTH,
            5,
            99
        );
        recordCommittedRequest(
            manager,
            Direction.SOUTH,
            128,
            99
        );
        recordCommittedRequest(
            manager,
            Direction.EAST,
            128,
            99
        );

        for (long tick = 100; tick < 110; tick++) {
            int storedEnergy = 40;
            int budget = 80;

            int north = pullEnergy(
                manager,
                Direction.NORTH,
                5,
                tick,
                storedEnergy,
                budget
            );
            storedEnergy -= north;
            budget -= north;

            int south = pullEnergy(
                manager,
                Direction.SOUTH,
                128,
                tick,
                storedEnergy,
                budget
            );
            storedEnergy -= south;
            budget -= south;

            int east = pullEnergy(
                manager,
                Direction.EAST,
                128,
                tick,
                storedEnergy,
                budget
            );
            storedEnergy -= east;
            budget -= east;

            assertEquals(
                5,
                north,
                "NORTH: тик " + tick
            );

            assertEquals(
                40,
                north + south + east,
                "Тик " + tick
            );

            if (tick > 100) {
                assertTrue(
                    Math.abs(south - east) <= 1,
                    "SOUTH и EAST должны получать почти поровну на тике " + tick
                );
            }
        }
    }

    private static @NonNull EnergyDistributionManager managerWithPendingFairnessCorrection() {
        EnergyDistributionManager manager = new EnergyDistributionManager(80);

        for (long tick = 99; tick <= 100; tick++) {
            recordCommittedRequest(
                manager,
                Direction.NORTH,
                80,
                tick
            );
            recordCommittedRequest(
                manager,
                Direction.SOUTH,
                80,
                tick
            );
        }

        try (Transaction tx = Transaction.openRoot()) {
            manager.recordExtraction(
                Direction.NORTH,
                30,
                100,
                tx
            );
            manager.recordExtraction(
                Direction.SOUTH,
                10,
                100,
                tx
            );
            tx.commit();
        }
        return manager;
    }

    private static long fairnessUpdateTick(EnergyDistributionManager manager)
        throws ReflectiveOperationException
    {
        Field field = EnergyDistributionManager.class.getDeclaredField("lastFairnessUpdateTick");
        field.setAccessible(true);
        return field.getLong(manager);
    }

    @SuppressWarnings("unchecked")
    private static Map<Direction, Long> fairnessCredits(EnergyDistributionManager manager)
        throws ReflectiveOperationException
    {
        Field field = EnergyDistributionManager.class.getDeclaredField("fairnessCredits");
        field.setAccessible(true);
        return (Map<Direction, Long>) field.get(manager);
    }

    @Test
    void shouldRollbackFairnessCreditsAndTickAfterSimulation() throws ReflectiveOperationException {
        EnergyDistributionManager manager = managerWithPendingFairnessCorrection();
        long beforeTick = fairnessUpdateTick(manager);
        Map<Direction, Long> beforeCredits = Map.copyOf(fairnessCredits(manager));

        try (Transaction simulated = Transaction.openRoot()) {
            assertEquals(
                30,
                manager.getAllowedAdaptiveAmount(
                    Direction.NORTH,
                    80,
                    80,
                    80,
                    101,
                    simulated
                )
            );
            assertEquals(
                101L,
                fairnessUpdateTick(manager)
            );
            assertEquals(
                -10L,
                fairnessCredits(manager).get(Direction.NORTH)
            );
            assertEquals(
                10L,
                fairnessCredits(manager).get(Direction.SOUTH)
            );
        }

        assertEquals(
            beforeTick,
            fairnessUpdateTick(manager)
        );
        assertEquals(
            beforeCredits,
            fairnessCredits(manager)
        );

        try (Transaction real = Transaction.openRoot()) {
            assertEquals(
                30,
                manager.getAllowedAdaptiveAmount(
                    Direction.NORTH,
                    80,
                    80,
                    80,
                    101,
                    real
                )
            );
            real.commit();
        }
        assertEquals(
            101L,
            fairnessUpdateTick(manager)
        );
        assertEquals(
            -10L,
            fairnessCredits(manager).get(Direction.NORTH)
        );
        assertEquals(
            10L,
            fairnessCredits(manager).get(Direction.SOUTH)
        );
    }

    @Test
    void shouldRollbackFairnessFromCommittedChildWhenRootAborts()
        throws ReflectiveOperationException
    {
        EnergyDistributionManager manager = managerWithPendingFairnessCorrection();
        long beforeTick = fairnessUpdateTick(manager);
        Map<Direction, Long> beforeCredits = Map.copyOf(fairnessCredits(manager));

        try (Transaction root = Transaction.openRoot()) {
            try (Transaction child = Transaction.open(root)) {
                assertEquals(
                    30,
                    manager.getAllowedAdaptiveAmount(
                        Direction.NORTH,
                        80,
                        80,
                        80,
                        101,
                        child
                    )
                );
                child.commit();
            }

            assertEquals(
                101L,
                fairnessUpdateTick(manager)
            );
            assertEquals(
                -10L,
                fairnessCredits(manager).get(Direction.NORTH)
            );
            assertEquals(
                10L,
                fairnessCredits(manager).get(Direction.SOUTH)
            );
        }

        assertEquals(
            beforeTick,
            fairnessUpdateTick(manager)
        );
        assertEquals(
            beforeCredits,
            fairnessCredits(manager)
        );
    }

    @Test
    void shouldRestoreCreditsAfterRollbackOfSingleSideReset() throws ReflectiveOperationException {
        EnergyDistributionManager manager = managerWithPendingFairnessCorrection();
        getCommittedAdaptiveAmount(
            manager,
            Direction.NORTH,
            80,
            80,
            80,
            101
        );

        assertEquals(
            -10L,
            fairnessCredits(manager).get(Direction.NORTH)
        );
        assertEquals(
            10L,
            fairnessCredits(manager).get(Direction.SOUTH)
        );
        assertEquals(
            101L,
            fairnessUpdateTick(manager)
        );

        recordCommittedRequest(
            manager,
            Direction.NORTH,
            80,
            110
        );
        recordCommittedRequest(
            manager,
            Direction.NORTH,
            80,
            121
        );
        assertEquals(
            1,
            manager.countActiveSides(121)
        );

        try (Transaction simulated = Transaction.openRoot()) {
            assertEquals(
                80,
                manager.getAllowedAdaptiveAmount(
                    Direction.NORTH,
                    80,
                    80,
                    80,
                    121,
                    simulated
                )
            );
            assertTrue(fairnessCredits(manager).isEmpty());
            assertEquals(
                121L,
                fairnessUpdateTick(manager)
            );
        }

        assertEquals(
            101L,
            fairnessUpdateTick(manager)
        );
        assertEquals(
            -10L,
            fairnessCredits(manager).get(Direction.NORTH)
        );
        assertEquals(
            10L,
            fairnessCredits(manager).get(Direction.SOUTH)
        );
    }

    @Test
    void shouldNotTreatSimulatedRequestAsActiveAfterRollback() {
        EnergyDistributionManager manager = new EnergyDistributionManager(80);
        recordCommittedRequest(
            manager,
            Direction.NORTH,
            80,
            120
        );

        try (Transaction root = Transaction.openRoot()) {
            try (Transaction simulated = Transaction.open(root)) {
                manager.recordRequest(
                    Direction.SOUTH,
                    128,
                    121,
                    simulated
                );
                assertEquals(
                    2,
                    manager.countActiveSides(121)
                );
                assertEquals(
                    40,
                    manager.getAllowedAdaptiveAmount(
                        Direction.SOUTH,
                        128,
                        80,
                        80,
                        121,
                        simulated
                    )
                );
            }

            assertFalse(manager.isActive(
                Direction.SOUTH,
                121
            ));
            assertEquals(
                1,
                manager.countActiveSides(121)
            );
            assertEquals(
                80,
                manager.getAllowedAdaptiveAmount(
                    Direction.NORTH,
                    80,
                    80,
                    80,
                    121,
                    root
                )
            );
            root.commit();
        }
    }


    @Test
    void shouldNotPenalizeRepeatedSmallTransfersAsExcessDemand() {
        EnergyDistributionManager manager = new EnergyDistributionManager(80);

        recordCommittedRequest(
            manager,
            Direction.NORTH,
            10,
            99
        );
        recordCommittedRequest(
            manager,
            Direction.SOUTH,
            10,
            99
        );

        int storedEnergy = 80;
        int remainingBudget = 80;

        for (int packet = 0; packet < 4; packet++) {
            int north = pullEnergy(
                manager,
                Direction.NORTH,
                10,
                100,
                storedEnergy,
                remainingBudget
            );
            assertEquals(
                10,
                north,
                "NORTH packet " + packet
            );
            storedEnergy -= north;
            remainingBudget -= north;

            int south = pullEnergy(
                manager,
                Direction.SOUTH,
                10,
                100,
                storedEnergy,
                remainingBudget
            );
            assertEquals(
                10,
                south,
                "SOUTH packet " + packet
            );
            storedEnergy -= south;
            remainingBudget -= south;
        }

        assertEquals(
            40,
            manager.getExtractedBySide(
                Direction.NORTH,
                100
            )
        );
        assertEquals(
            40,
            manager.getExtractedBySide(
                Direction.SOUTH,
                100
            )
        );
        assertEquals(
            10,
            manager.getMaxRequestedBySide(
                Direction.NORTH,
                100
            )
        );
        assertEquals(
            10,
            manager.getMaxRequestedBySide(
                Direction.SOUTH,
                100
            )
        );

        int north = pullEnergy(
            manager,
            Direction.NORTH,
            10,
            101,
            40,
            80
        );
        assertEquals(
            10,
            north,
            "NORTH must still receive energy on tick 101"
        );

        int south = pullEnergy(
            manager,
            Direction.SOUTH,
            10,
            101,
            40 - north,
            80 - north
        );
        assertEquals(
            10,
            south,
            "SOUTH must still receive energy on tick 101"
        );
    }


    @Test
    void shouldKeepOtherFairnessCreditsWhileThirdSideIsInactive()
        throws ReflectiveOperationException
    {
        EnergyDistributionManager manager = managerWithThreeSideCredits();
        Map<Direction, Long> before = Map.copyOf(fairnessCredits(manager));

        assertTrue(before.getOrDefault(
            Direction.EAST,
            0L
        ) > 0);

        assertTrue(manager.isActive(
            Direction.NORTH,
            120
        ));
        assertTrue(manager.isActive(
            Direction.SOUTH,
            120
        ));
        assertFalse(manager.isActive(
            Direction.EAST,
            120
        ));

        assertEquals(
            before,
            fairnessCredits(manager),
            "Отключение EAST не должно сбрасывать остальные компенсации"
        );
    }

    @Test
    void shouldResetOnlyReconnectedSideFairnessCredit() throws ReflectiveOperationException
    {
        EnergyDistributionManager manager = managerWithThreeSideCredits();

        Map<Direction, Long> before = Map.copyOf(fairnessCredits(manager));
        long lastUpdate = fairnessUpdateTick(manager);

        recordCommittedRequest(
            manager,
            Direction.EAST,
            80,
            120
        );

        assertTrue(manager.isActive(
            Direction.EAST,
            120
        ));
        assertEquals(
            3,
            manager.countActiveSides(120)
        );
        assertEquals(
            80,
            manager.getMaxRequestedBySide(
                Direction.EAST,
                120
            )
        );

        assertFalse(
            fairnessCredits(manager).containsKey(Direction.EAST),
            "EAST не должен сохранять компенсацию после переподключения"
        );

        assertEquals(
            before.get(Direction.NORTH),
            fairnessCredits(manager).get(Direction.NORTH)
        );

        assertEquals(
            before.get(Direction.SOUTH),
            fairnessCredits(manager).get(Direction.SOUTH)
        );

        assertEquals(
            lastUpdate,
            fairnessUpdateTick(manager),
            "Повторное подключение не должно пересчитывать компенсации"
        );
    }

    @Test
    void shouldRollbackReconnectionAndRestoreExpiredSideCredit() throws ReflectiveOperationException
    {
        EnergyDistributionManager manager = managerWithThreeSideCredits();

        Map<Direction, Long> before = Map.copyOf(fairnessCredits(manager));
        long lastUpdate = fairnessUpdateTick(manager);

        try (Transaction root = Transaction.openRoot()) {
            try (Transaction child = Transaction.open(root)) {
                manager.recordRequest(
                    Direction.EAST,
                    80,
                    120,
                    child
                );

                assertTrue(manager.isActive(
                    Direction.EAST,
                    120
                ));
                assertFalse(fairnessCredits(manager).containsKey(Direction.EAST));

                child.commit();
            }

            assertEquals(
                3,
                manager.countActiveSides(120)
            );

        }

        assertFalse(manager.isActive(
            Direction.EAST,
            120
        ));
        assertEquals(
            2,
            manager.countActiveSides(120)
        );
        assertEquals(
            0,
            manager.getMaxRequestedBySide(
                Direction.EAST,
                120
            )
        );

        assertEquals(
            before,
            fairnessCredits(manager),
            "После отката должны восстановиться прежние компенсации"
        );

        assertEquals(
            lastUpdate,
            fairnessUpdateTick(manager)
        );
    }

    @Test
    void abortedRequestIsNotRememberedAsDemand() {
        EnergyDistributionManager manager = new EnergyDistributionManager(80);

        long tick = 100L;

        try (Transaction tx = Transaction.openRoot()) {
            manager.recordRequest(
                Direction.NORTH,
                40,
                tick,
                tx
            );

            assertTrue(manager.isActive(
                Direction.NORTH,
                tick
            ));
        }

        assertFalse(manager.isActive(
            Direction.NORTH,
            tick
        ));

        try (Transaction tx = Transaction.openRoot()) {
            manager.recordRequest(
                Direction.SOUTH,
                40,
                tick,
                tx
            );

            int allowed = manager.getAllowedAdaptiveAmount(
                Direction.SOUTH,
                40,
                80,
                40,
                tick,
                tx
            );

            assertEquals(
                40,
                allowed
            );
            tx.commit();
        }
    }
}
