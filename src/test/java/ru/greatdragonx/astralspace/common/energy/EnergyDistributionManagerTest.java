package ru.greatdragonx.astralspace.common.energy;

import net.minecraft.core.Direction;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EnergyDistributionManagerTest {
    @Test
    void shouldTrackActiveSide() {
        EnergyDistributionManager manager = new EnergyDistributionManager(80);

        manager.recordRequest(
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

        manager.recordRequest(
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

        manager.recordRequest(
            Direction.NORTH,
            80,
            100
        );

        assertEquals(
            1,
            manager.countActiveSides(100)
        );

        manager.recordRequest(
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

        manager.recordRequest(
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

        manager.recordRequest(
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

        manager.recordRequest(
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

        manager.recordRequest(
            Direction.NORTH,
            80,
            100
        );

        manager.recordRequest(
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

        manager.recordRequest(
            Direction.NORTH,
            80,
            100
        );

        manager.recordRequest(
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

        manager.recordRequest(
            Direction.NORTH,
            20,
            100
        );

        manager.recordRequest(
            Direction.NORTH,
            60,
            100
        );

        manager.recordRequest(
            Direction.NORTH,
            30,
            100
        );

        manager.recordRequest(
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

        manager.recordRequest(
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

        manager.recordRequest(
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

        manager.recordRequest(
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

        manager.recordRequest(
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

        manager.recordRequest(
            Direction.NORTH,
            10,
            99
        );
        manager.recordRequest(
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

        manager.recordRequest(
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

        manager.recordRequest(
            Direction.NORTH,
            40,
            98
        );
        manager.recordRequest(
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

        manager.recordRequest(
            Direction.NORTH,
            10,
            99
        );
        manager.recordRequest(
            Direction.SOUTH,
            80,
            99
        );

        manager.recordRequest(
            Direction.SOUTH,
            80,
            100
        );

        assertEquals(
            70,
            manager.getAllowedAdaptiveAmount(
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

        manager.recordRequest(
            Direction.NORTH,
            10,
            100
        );
        assertEquals(
            10,
            manager.getAllowedAdaptiveAmount(
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

        manager.recordRequest(
            Direction.NORTH,
            10,
            99
        );
        manager.recordRequest(
            Direction.SOUTH,
            80,
            99
        );

        manager.recordRequest(
            Direction.NORTH,
            10,
            100
        );

        int allowed = manager.getAllowedAdaptiveAmount(
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

        manager.recordRequest(
            Direction.SOUTH,
            80,
            100
        );

        int allowedSouth = manager.getAllowedAdaptiveAmount(
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

        manager.recordRequest(
            Direction.NORTH,
            80,
            100
        );

        assertEquals(
            80,
            manager.getAllowedAdaptiveAmount(
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

        manager.recordRequest(
            Direction.SOUTH,
            80,
            100
        );
        assertEquals(
            0,
            manager.getAllowedAdaptiveAmount(
                Direction.SOUTH,
                80,
                0,
                0,
                100
            )
        );

        manager.recordRequest(
            Direction.NORTH,
            80,
            101
        );

        assertEquals(
            40,
            manager.getAllowedAdaptiveAmount(
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

        manager.recordRequest(
            Direction.SOUTH,
            80,
            101
        );
        assertEquals(
            40,
            manager.getAllowedAdaptiveAmount(
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

        manager.recordRequest(
            Direction.NORTH,
            80,
            99
        );
        manager.recordRequest(
            Direction.SOUTH,
            80,
            99
        );

        manager.recordRequest(
            Direction.NORTH,
            80,
            100
        );
        int allowedNorth = manager.getAllowedAdaptiveAmount(
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

        manager.recordRequest(
            Direction.SOUTH,
            80,
            100
        );
        int allowedSouth = manager.getAllowedAdaptiveAmount(
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

        manager.recordRequest(
            Direction.NORTH,
            128,
            99
        );
        manager.recordRequest(
            Direction.SOUTH,
            128,
            99
        );

        int storedEnergy = 20;
        int totalNorth = 0;
        int totalSouth = 0;

        for (long tick = 100; tick < 120; tick++) {
            manager.recordRequest(
                Direction.SOUTH,
                128,
                tick
            );
            int allowedSouth = manager.getAllowedAdaptiveAmount(
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
            totalSouth += extractedSouth;

            storedEnergy += 40;

            manager.recordRequest(
                Direction.NORTH,
                128,
                tick
            );
            int allowedNorth = manager.getAllowedAdaptiveAmount(
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
            totalNorth += extractedNorth;
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
}
