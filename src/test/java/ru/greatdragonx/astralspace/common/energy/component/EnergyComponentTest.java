package ru.greatdragonx.astralspace.common.energy.component;

import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class EnergyComponentTest {

    @Test
    void shouldCreateEmptyEnergyComponent() {
        AtomicInteger changes = new AtomicInteger();

        EnergyComponent component = new EnergyComponent(
            10_000L,
            changes::incrementAndGet
        );

        assertAll(
            () -> assertEquals(
                10_000L,
                component.getCapacity()
            ),
            () -> assertEquals(
                0L,
                component.getStoredEnergy()
            ),
            () -> assertEquals(
                0,
                changes.get()
            )
        );
    }

    @Test
    void shouldRejectNonPositiveCapacity() {
        assertAll(
            () -> assertThrows(
                IllegalArgumentException.class,
                () -> new EnergyComponent(
                    0L,
                    () -> {
                    }
                )
            ),
            () -> assertThrows(
                IllegalArgumentException.class,
                () -> new EnergyComponent(
                    -1L,
                    () -> {
                    }
                )
            ),
            () -> assertThrows(
                IllegalArgumentException.class,
                () -> new EnergyComponent(
                    Long.MIN_VALUE,
                    () -> {
                    }
                )
            )
        );
    }

    @Test
    void shouldRejectNullCallback() {
        assertThrows(
            NullPointerException.class,
            () -> new EnergyComponent(
                100L,
                null
            )
        );
    }

    @Test
    void shouldStoreProducedEnergy() {
        AtomicInteger changes = new AtomicInteger();

        EnergyComponent component = new EnergyComponent(
            100L,
            changes::incrementAndGet
        );

        int accepted = component.storeProducedEnergy(40);

        assertAll(
            () -> assertEquals(
                40,
                accepted
            ),
            () -> assertEquals(
                40L,
                component.getStoredEnergy()
            ),
            () -> assertEquals(
                100L,
                component.getCapacity()
            ),
            () -> assertEquals(
                1,
                changes.get()
            )
        );
    }

    @Test
    void shouldAccumulateProducedEnergy() {
        AtomicInteger changes = new AtomicInteger();

        EnergyComponent component = new EnergyComponent(
            100L,
            changes::incrementAndGet
        );

        int first = component.storeProducedEnergy(20);
        int second = component.storeProducedEnergy(30);
        int third = component.storeProducedEnergy(10);

        assertAll(
            () -> assertEquals(
                20,
                first
            ),
            () -> assertEquals(
                30,
                second
            ),
            () -> assertEquals(
                10,
                third
            ),
            () -> assertEquals(
                60L,
                component.getStoredEnergy()
            ),
            () -> assertEquals(
                3,
                changes.get()
            )
        );
    }

    @Test
    void shouldPartiallyAcceptEnergyWhenCapacityIsInsufficient() {
        AtomicInteger changes = new AtomicInteger();

        EnergyComponent component = new EnergyComponent(
            100L,
            changes::incrementAndGet
        );

        component.storeProducedEnergy(80);

        int accepted = component.storeProducedEnergy(50);

        assertAll(
            () -> assertEquals(
                20,
                accepted
            ),
            () -> assertEquals(
                100L,
                component.getStoredEnergy()
            ),
            () -> assertEquals(
                2,
                changes.get()
            )
        );
    }

    @Test
    void shouldRejectEnergyWhenStorageIsFull() {
        AtomicInteger changes = new AtomicInteger();

        EnergyComponent component = new EnergyComponent(
            100L,
            changes::incrementAndGet
        );

        component.storeProducedEnergy(100);

        int accepted = component.storeProducedEnergy(50);

        assertAll(
            () -> assertEquals(
                0,
                accepted
            ),
            () -> assertEquals(
                100L,
                component.getStoredEnergy()
            ),
            () -> assertEquals(
                1,
                changes.get()
            )
        );
    }

    @Test
    void shouldIgnoreZeroProducedEnergy() {
        AtomicInteger changes = new AtomicInteger();

        EnergyComponent component = new EnergyComponent(
            100L,
            changes::incrementAndGet
        );

        component.storeProducedEnergy(40);

        int accepted = component.storeProducedEnergy(0);

        assertAll(
            () -> assertEquals(
                0,
                accepted
            ),
            () -> assertEquals(
                40L,
                component.getStoredEnergy()
            ),
            () -> assertEquals(
                1,
                changes.get()
            )
        );
    }

    @Test
    void shouldRejectNegativeProducedEnergyWithoutChangingState() {
        AtomicInteger changes = new AtomicInteger();

        EnergyComponent component = new EnergyComponent(
            100L,
            changes::incrementAndGet
        );

        component.storeProducedEnergy(40);

        assertThrows(
            IllegalArgumentException.class,
            () -> component.storeProducedEnergy(-10)
        );

        assertAll(
            () -> assertEquals(
                40L,
                component.getStoredEnergy()
            ),
            () -> assertEquals(
                1,
                changes.get()
            )
        );
    }

    @Test
    void shouldSupportEnergyStorageLargerThanIntegerMaxValue() {
        AtomicInteger changes = new AtomicInteger();

        EnergyComponent component = new EnergyComponent(
            Long.MAX_VALUE,
            changes::incrementAndGet
        );

        int first = component.storeProducedEnergy(Integer.MAX_VALUE);

        int second = component.storeProducedEnergy(Integer.MAX_VALUE);

        assertAll(
            () -> assertEquals(
                Integer.MAX_VALUE,
                first
            ),
            () -> assertEquals(
                Integer.MAX_VALUE,
                second
            ),
            () -> assertEquals(
                2L * Integer.MAX_VALUE,
                component.getStoredEnergy()
            ),
            () -> assertEquals(
                Long.MAX_VALUE,
                component.getCapacity()
            ),
            () -> assertEquals(
                2,
                changes.get()
            )
        );
    }


    @Test
    void shouldConsumeEnergyWhenEnoughStored() {
        AtomicInteger changes = new AtomicInteger();

        EnergyComponent component = new EnergyComponent(
            100L,
            changes::incrementAndGet
        );

        component.storeProducedEnergy(80);

        try (Transaction tx = Transaction.openRoot()) {
            boolean consumed = component.tryConsumeForWork(
                40,
                tx
            );

            assertTrue(consumed);
            assertEquals(
                40L,
                component.getStoredEnergy()
            );

            assertEquals(
                1,
                changes.get()
            );

            tx.commit();
        }

        assertEquals(
            40L,
            component.getStoredEnergy()
        );
        assertEquals(
            2,
            changes.get()
        );
    }

    @Test
    void shouldNotConsumeEnergyWhenInsufficient() {
        AtomicInteger changes = new AtomicInteger();

        EnergyComponent component = new EnergyComponent(
            100L,
            changes::incrementAndGet
        );

        component.storeProducedEnergy(30);

        try (Transaction tx = Transaction.openRoot()) {
            boolean consumed = component.tryConsumeForWork(
                40,
                tx
            );

            assertFalse(consumed);
            assertEquals(
                30L,
                component.getStoredEnergy()
            );

            tx.commit();
        }

        assertEquals(
            30L,
            component.getStoredEnergy()
        );
        assertEquals(
            1,
            changes.get()
        );
    }

    @Test
    void shouldRestoreConsumedEnergyOnRollback() {
        AtomicInteger changes = new AtomicInteger();

        EnergyComponent component = new EnergyComponent(
            100L,
            changes::incrementAndGet
        );

        component.storeProducedEnergy(80);

        try (Transaction tx = Transaction.openRoot()) {
            boolean consumed = component.tryConsumeForWork(
                40,
                tx
            );

            assertTrue(consumed);
            assertEquals(
                40L,
                component.getStoredEnergy()
            );
            assertEquals(
                1,
                changes.get()
            );
        }

        assertEquals(
            80L,
            component.getStoredEnergy()
        );
        assertEquals(
            1,
            changes.get()
        );
    }

    @Test
    void shouldNotifyOnceForMultipleConsumptionsInOneTransaction() {
        AtomicInteger changes = new AtomicInteger();

        EnergyComponent component = new EnergyComponent(
            100L,
            changes::incrementAndGet
        );

        component.storeProducedEnergy(100);

        try (Transaction tx = Transaction.openRoot()) {
            assertTrue(component.tryConsumeForWork(
                20,
                tx
            ));
            assertTrue(component.tryConsumeForWork(
                30,
                tx
            ));

            assertEquals(
                50L,
                component.getStoredEnergy()
            );
            assertEquals(
                1,
                changes.get()
            );

            tx.commit();
        }

        assertEquals(
            50L,
            component.getStoredEnergy()
        );
        assertEquals(
            2,
            changes.get()
        );
    }

    @Test
    void shouldHandleZeroConsumptionWithoutChanges() {
        AtomicInteger changes = new AtomicInteger();

        EnergyComponent component = new EnergyComponent(
            100L,
            changes::incrementAndGet
        );

        try (Transaction tx = Transaction.openRoot()) {
            assertTrue(component.tryConsumeForWork(
                0,
                tx
            ));
            tx.commit();
        }

        assertEquals(
            0L,
            component.getStoredEnergy()
        );
        assertEquals(
            0,
            changes.get()
        );
    }

    @Test
    void shouldRejectNegativeConsumption() {
        EnergyComponent component = new EnergyComponent(
            100L,
            () -> {
            }
        );

        try (Transaction tx = Transaction.openRoot()) {
            assertThrows(
                IllegalArgumentException.class,
                () -> component.tryConsumeForWork(
                    -10,
                    tx
                )
            );
        }

        assertEquals(
            0L,
            component.getStoredEnergy()
        );
    }


    @Test
    void shouldRollbackCommittedNestedTransactionWhenRootAborts() {
        AtomicInteger changes = new AtomicInteger();

        EnergyComponent component = new EnergyComponent(
            100L,
            changes::incrementAndGet
        );

        component.storeProducedEnergy(100);

        try (Transaction root = Transaction.openRoot()) {
            try (Transaction nested = Transaction.open(root)) {
                assertTrue(component.tryConsumeForWork(
                    40,
                    nested
                ));

                assertEquals(
                    60L,
                    component.getStoredEnergy()
                );

                nested.commit();
            }

            assertEquals(
                60L,
                component.getStoredEnergy()
            );
            assertEquals(
                1,
                changes.get()
            );

        }

        assertEquals(
            100L,
            component.getStoredEnergy()
        );
        assertEquals(
            1,
            changes.get()
        );
    }


    @Test
    void shouldRestoreStoredEnergy() {
        AtomicInteger changes = new AtomicInteger();

        EnergyComponent component = new EnergyComponent(
            100L,
            changes::incrementAndGet
        );

        component.restoreStoredEnergy(75L);

        assertAll(
            () -> assertEquals(
                75L,
                component.getStoredEnergy()
            ),
            () -> assertEquals(
                0,
                changes.get()
            )
        );
    }

    @Test
    void shouldClampRestoredEnergyToCapacity() {
        EnergyComponent component = new EnergyComponent(
            100L,
            () -> {
            }
        );

        component.restoreStoredEnergy(150L);

        assertEquals(
            100L,
            component.getStoredEnergy()
        );
    }

    @Test
    void shouldClampNegativeRestoredEnergyToZero() {
        EnergyComponent component = new EnergyComponent(
            100L,
            () -> {
            }
        );

        component.restoreStoredEnergy(-50L);

        assertEquals(
            0L,
            component.getStoredEnergy()
        );
    }

    @Test
    void shouldRestoreEnergyWithoutTriggeringChangeCallback() {
        AtomicInteger changes = new AtomicInteger();

        EnergyComponent component = new EnergyComponent(
            100L,
            changes::incrementAndGet
        );

        component.storeProducedEnergy(40);
        assertEquals(
            1,
            changes.get()
        );

        component.restoreStoredEnergy(70L);

        assertAll(
            () -> assertEquals(
                70L,
                component.getStoredEnergy()
            ),
            () -> assertEquals(
                1,
                changes.get()
            )
        );
    }


    @Test
    void shouldInsertEnergyAndNotifyAfterCommit() {
        AtomicInteger changes = new AtomicInteger();
        EnergyComponent component = new EnergyComponent(
            100L,
            changes::incrementAndGet
        );

        try (Transaction tx = Transaction.openRoot()) {
            int inserted = component.insertEnergy(
                40,
                tx
            );

            assertEquals(
                40,
                inserted
            );
            assertEquals(
                40L,
                component.getStoredEnergy()
            );
            assertEquals(
                0,
                changes.get()
            );

            tx.commit();
        }

        assertEquals(
            40L,
            component.getStoredEnergy()
        );
        assertEquals(
            1,
            changes.get()
        );
    }

    @Test
    void shouldPartiallyInsertEnergy() {
        AtomicInteger changes = new AtomicInteger();
        EnergyComponent component = new EnergyComponent(
            100L,
            changes::incrementAndGet
        );
        component.storeProducedEnergy(80);

        try (Transaction tx = Transaction.openRoot()) {
            int inserted = component.insertEnergy(
                50,
                tx
            );

            assertEquals(
                20,
                inserted
            );
            assertEquals(
                100L,
                component.getStoredEnergy()
            );
            assertEquals(
                1,
                changes.get()
            );

            tx.commit();
        }

        assertEquals(
            100L,
            component.getStoredEnergy()
        );
        assertEquals(
            2,
            changes.get()
        );
    }

    @Test
    void shouldNotInsertEnergyWhenFull() {
        AtomicInteger changes = new AtomicInteger();
        EnergyComponent component = new EnergyComponent(
            100L,
            changes::incrementAndGet
        );
        component.storeProducedEnergy(100);

        try (Transaction tx = Transaction.openRoot()) {
            assertEquals(
                0,
                component.insertEnergy(
                    50,
                    tx
                )
            );
            tx.commit();
        }

        assertEquals(
            100L,
            component.getStoredEnergy()
        );
        assertEquals(
            1,
            changes.get()
        );
    }

    @Test
    void shouldRollbackInsertedEnergy() {
        AtomicInteger changes = new AtomicInteger();
        EnergyComponent component = new EnergyComponent(
            100L,
            changes::incrementAndGet
        );
        component.storeProducedEnergy(30);

        try (Transaction tx = Transaction.openRoot()) {
            assertEquals(
                40,
                component.insertEnergy(
                    40,
                    tx
                )
            );
            assertEquals(
                70L,
                component.getStoredEnergy()
            );
        }

        assertEquals(
            30L,
            component.getStoredEnergy()
        );
        assertEquals(
            1,
            changes.get()
        );
    }

    @Test
    void shouldNotifyOnceForMultipleInsertions() {
        AtomicInteger changes = new AtomicInteger();
        EnergyComponent component = new EnergyComponent(
            100L,
            changes::incrementAndGet
        );

        try (Transaction tx = Transaction.openRoot()) {
            assertEquals(
                20,
                component.insertEnergy(
                    20,
                    tx
                )
            );
            assertEquals(
                30,
                component.insertEnergy(
                    30,
                    tx
                )
            );
            assertEquals(
                50L,
                component.getStoredEnergy()
            );
            assertEquals(
                0,
                changes.get()
            );

            tx.commit();
        }

        assertEquals(
            50L,
            component.getStoredEnergy()
        );
        assertEquals(
            1,
            changes.get()
        );
    }

    @Test
    void shouldIgnoreZeroEnergyInsertion() {
        AtomicInteger changes = new AtomicInteger();
        EnergyComponent component = new EnergyComponent(
            100L,
            changes::incrementAndGet
        );

        try (Transaction tx = Transaction.openRoot()) {
            assertEquals(
                0,
                component.insertEnergy(
                    0,
                    tx
                )
            );
            tx.commit();
        }

        assertEquals(
            0L,
            component.getStoredEnergy()
        );
        assertEquals(
            0,
            changes.get()
        );
    }

    @Test
    void shouldRejectNegativeEnergyInsertion() {
        EnergyComponent component = new EnergyComponent(
            100L,
            () -> {
            }
        );

        try (Transaction tx = Transaction.openRoot()) {
            assertThrows(
                IllegalArgumentException.class,
                () -> component.insertEnergy(
                    -10,
                    tx
                )
            );
        }

        assertEquals(
            0L,
            component.getStoredEnergy()
        );
    }

    @Test
    void shouldInsertEnergyWithLongCapacityWithoutOverflow() {
        AtomicInteger changes = new AtomicInteger();
        EnergyComponent component = new EnergyComponent(
            Long.MAX_VALUE,
            changes::incrementAndGet
        );

        component.restoreStoredEnergy(Long.MAX_VALUE - 3);

        try (Transaction tx = Transaction.openRoot()) {
            int inserted = component.insertEnergy(
                10,
                tx
            );

            assertEquals(
                3,
                inserted
            );
            assertEquals(
                Long.MAX_VALUE,
                component.getStoredEnergy()
            );

            tx.commit();
        }

        assertEquals(
            Long.MAX_VALUE,
            component.getStoredEnergy()
        );
        assertEquals(
            1,
            changes.get()
        );
    }

    @Test
    void shouldRollbackInsertedEnergyAfterNestedCommit() {
        AtomicInteger changes = new AtomicInteger();
        EnergyComponent component = new EnergyComponent(
            100L,
            changes::incrementAndGet
        );

        try (Transaction root = Transaction.openRoot()) {
            try (Transaction nested = Transaction.open(root)) {
                assertEquals(
                    40,
                    component.insertEnergy(
                        40,
                        nested
                    )
                );
                nested.commit();
            }

            assertEquals(
                40L,
                component.getStoredEnergy()
            );
            assertEquals(
                0,
                changes.get()
            );
        }

        assertEquals(
            0L,
            component.getStoredEnergy()
        );
        assertEquals(
            0,
            changes.get()
        );
    }


    @Test
    void shouldExtractEnergyAndNotifyAfterCommit() {
        AtomicInteger changes = new AtomicInteger();
        EnergyComponent component = new EnergyComponent(
            100L,
            changes::incrementAndGet
        );

        component.storeProducedEnergy(80);

        try (Transaction tx = Transaction.openRoot()) {
            int extracted = component.extractEnergy(
                40,
                tx
            );

            assertEquals(
                40,
                extracted
            );
            assertEquals(
                40L,
                component.getStoredEnergy()
            );
            assertEquals(
                1,
                changes.get()
            );

            tx.commit();
        }

        assertEquals(
            40L,
            component.getStoredEnergy()
        );
        assertEquals(
            2,
            changes.get()
        );
    }

    @Test
    void shouldPartiallyExtractEnergyWhenInsufficient() {
        EnergyComponent component = new EnergyComponent(
            100L,
            () -> {
            }
        );

        component.storeProducedEnergy(30);

        try (Transaction tx = Transaction.openRoot()) {
            int extracted = component.extractEnergy(
                50,
                tx
            );

            assertEquals(
                30,
                extracted
            );
            assertEquals(
                0L,
                component.getStoredEnergy()
            );

            tx.commit();
        }

        assertEquals(
            0L,
            component.getStoredEnergy()
        );
    }

    @Test
    void shouldNotExtractEnergyWhenEmpty() {
        AtomicInteger changes = new AtomicInteger();
        EnergyComponent component = new EnergyComponent(
            100L,
            changes::incrementAndGet
        );

        try (Transaction tx = Transaction.openRoot()) {
            assertEquals(
                0,
                component.extractEnergy(
                    50,
                    tx
                )
            );
            tx.commit();
        }

        assertEquals(
            0L,
            component.getStoredEnergy()
        );
        assertEquals(
            0,
            changes.get()
        );
    }

    @Test
    void shouldIgnoreZeroEnergyExtraction() {
        AtomicInteger changes = new AtomicInteger();
        EnergyComponent component = new EnergyComponent(
            100L,
            changes::incrementAndGet
        );

        component.storeProducedEnergy(60);

        try (Transaction tx = Transaction.openRoot()) {
            assertEquals(
                0,
                component.extractEnergy(
                    0,
                    tx
                )
            );
            tx.commit();
        }

        assertEquals(
            60L,
            component.getStoredEnergy()
        );
        assertEquals(
            1,
            changes.get()
        );
    }

    @Test
    void shouldRejectNegativeEnergyExtraction() {
        EnergyComponent component = new EnergyComponent(
            100L,
            () -> {
            }
        );

        component.storeProducedEnergy(50);

        try (Transaction tx = Transaction.openRoot()) {
            assertThrows(
                IllegalArgumentException.class,
                () -> component.extractEnergy(
                    -10,
                    tx
                )
            );
        }

        assertEquals(
            50L,
            component.getStoredEnergy()
        );
    }

    @Test
    void shouldRollbackExtractedEnergy() {
        AtomicInteger changes = new AtomicInteger();
        EnergyComponent component = new EnergyComponent(
            100L,
            changes::incrementAndGet
        );

        component.storeProducedEnergy(80);

        try (Transaction tx = Transaction.openRoot()) {
            assertEquals(
                40,
                component.extractEnergy(
                    40,
                    tx
                )
            );
            assertEquals(
                40L,
                component.getStoredEnergy()
            );
        }

        assertEquals(
            80L,
            component.getStoredEnergy()
        );
        assertEquals(
            1,
            changes.get()
        );
    }

    @Test
    void shouldRollbackExtractionAfterNestedCommit() {
        AtomicInteger changes = new AtomicInteger();
        EnergyComponent component = new EnergyComponent(
            100L,
            changes::incrementAndGet
        );

        component.storeProducedEnergy(100);

        try (Transaction root = Transaction.openRoot()) {
            try (Transaction nested = Transaction.open(root)) {
                assertEquals(
                    40,
                    component.extractEnergy(
                        40,
                        nested
                    )
                );
                nested.commit();
            }

            assertEquals(
                60L,
                component.getStoredEnergy()
            );
            assertEquals(
                1,
                changes.get()
            );

        }

        assertEquals(
            100L,
            component.getStoredEnergy()
        );
        assertEquals(
            1,
            changes.get()
        );
    }

    @Test
    void shouldNotifyOnceForMultipleExtractions() {
        AtomicInteger changes = new AtomicInteger();
        EnergyComponent component = new EnergyComponent(
            100L,
            changes::incrementAndGet
        );

        component.storeProducedEnergy(100);

        try (Transaction tx = Transaction.openRoot()) {
            assertEquals(
                20,
                component.extractEnergy(
                    20,
                    tx
                )
            );
            assertEquals(
                30,
                component.extractEnergy(
                    30,
                    tx
                )
            );

            assertEquals(
                50L,
                component.getStoredEnergy()
            );
            assertEquals(
                1,
                changes.get()
            );

            tx.commit();
        }

        assertEquals(
            50L,
            component.getStoredEnergy()
        );
        assertEquals(
            2,
            changes.get()
        );
    }

    @Test
    void shouldNotNotifyWhenInsertAndExtractCancelEachOther() {
        AtomicInteger changes = new AtomicInteger();
        EnergyComponent component = new EnergyComponent(
            100L,
            changes::incrementAndGet
        );

        component.storeProducedEnergy(50);

        try (Transaction tx = Transaction.openRoot()) {
            assertEquals(
                30,
                component.insertEnergy(
                    30,
                    tx
                )
            );
            assertEquals(
                30,
                component.extractEnergy(
                    30,
                    tx
                )
            );

            assertEquals(
                50L,
                component.getStoredEnergy()
            );

            tx.commit();
        }

        assertEquals(
            50L,
            component.getStoredEnergy()
        );

        assertEquals(
            1,
            changes.get()
        );
    }

    @Test
    void shouldExtractFromStorageLargerThanIntegerMaxValue() {
        EnergyComponent component = new EnergyComponent(
            Long.MAX_VALUE,
            () -> {
            }
        );

        component.restoreStoredEnergy(2L * Integer.MAX_VALUE);

        try (Transaction tx = Transaction.openRoot()) {
            int extracted = component.extractEnergy(
                Integer.MAX_VALUE,
                tx
            );

            assertEquals(
                Integer.MAX_VALUE,
                extracted
            );

            tx.commit();
        }

        assertEquals(
            Integer.MAX_VALUE,
            component.getStoredEnergy()
        );
    }
}
