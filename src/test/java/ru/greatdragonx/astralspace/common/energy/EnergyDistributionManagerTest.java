package ru.greatdragonx.astralspace.common.energy;

import net.minecraft.core.Direction;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EnergyDistributionManagerTest {
    @Test
    void shouldTrackActiveSide() {
        EnergyDistributionManager manager = new EnergyDistributionManager();

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
        EnergyDistributionManager manager = new EnergyDistributionManager();
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
        EnergyDistributionManager manager = new EnergyDistributionManager();

        manager.recordRequest(
            Direction.NORTH,
            80,
            100
        );

        assertEquals(
            80,
            manager.getBaseQuota(
                Direction.NORTH,
                80,
                100
            )
        );
        assertEquals(
            0,
            manager.getBaseQuota(
                Direction.SOUTH,
                80,
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
                80,
                105
            )
        );
        assertEquals(
            40,
            manager.getBaseQuota(
                Direction.SOUTH,
                80,
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
                80,
                108
            )
        );
        assertEquals(
            26,
            manager.getBaseQuota(
                Direction.SOUTH,
                80,
                108
            )
        );
        assertEquals(
            26,
            manager.getBaseQuota(
                Direction.EAST,
                80,
                108
            )
        );
    }

    @Test
    void shouldRestoreSideUsageAfterRollback() {
        EnergyDistributionManager manager = new EnergyDistributionManager();

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
        EnergyDistributionManager manager = new EnergyDistributionManager();

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
        EnergyDistributionManager manager = new EnergyDistributionManager();

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
                80,
                100
            )
        );
        assertEquals(
            40,
            manager.getBaseQuota(
                Direction.SOUTH,
                80,
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
                    80,
                    100
                )
            );
            assertEquals(
                40,
                manager.getRemainingBaseQuota(
                    Direction.SOUTH,
                    80,
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
                    80,
                    100
                )
            );
            assertEquals(
                40,
                manager.getRemainingBaseQuota(
                    Direction.SOUTH,
                    80,
                    100
                )
            );

            tx.commit();
        }

        assertEquals(
            0,
            manager.getRemainingBaseQuota(
                Direction.NORTH,
                80,
                100
            )
        );
        assertEquals(
            40,
            manager.getRemainingBaseQuota(
                Direction.SOUTH,
                80,
                100
            )
        );

        assertEquals(
            40,
            manager.getRemainingBaseQuota(
                Direction.NORTH,
                80,
                101
            )
        );
        assertEquals(
            40,
            manager.getRemainingBaseQuota(
                Direction.SOUTH,
                80,
                101
            )
        );
    }

    @Test
    void shouldLimitRequestByRemainingBaseQuota() {
        EnergyDistributionManager manager = new EnergyDistributionManager();

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
            80,
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
                80,
                100
            )
        );

        assertEquals(
            10,
            manager.getAllowedBaseAmount(
                Direction.NORTH,
                10,
                80,
                100
            )
        );

        assertEquals(
            40,
            manager.getAllowedBaseAmount(
                Direction.SOUTH,
                80,
                80,
                100
            )
        );

        assertEquals(
            0,
            manager.getAllowedBaseAmount(
                Direction.EAST,
                80,
                80,
                100
            )
        );

        assertEquals(
            0,
            manager.getAllowedBaseAmount(
                Direction.NORTH,
                0,
                80,
                100
            )
        );
    }
}
