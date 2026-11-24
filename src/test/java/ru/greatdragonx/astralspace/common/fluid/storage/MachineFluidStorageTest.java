package ru.greatdragonx.astralspace.common.fluid.storage;

import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;

class MachineFluidStorageTest {

    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        Bootstrap.bootStrap();
    }

    private static @NonNull FluidResource water() {
        return FluidResource.of(Fluids.WATER);
    }

    private static @NonNull FluidResource lava() {
        return FluidResource.of(Fluids.LAVA);
    }

    private static @NonNull FluidTankDefinition tank(
        String id,
        FluidTankRole role,
        int capacity,
        Predicate<FluidResource> filter
    )
    {
        return new FluidTankDefinition(
            new FluidTankId(id),
            role,
            capacity,
            filter
        );
    }

    private static @NonNull MachineFluidStorage storage(
        @NonNull AtomicInteger notifications,
        FluidTankDefinition... definitions
    )
    {
        return new MachineFluidStorage(
            List.of(definitions),
            notifications::incrementAndGet
        );
    }

    private static @NonNull MachineFluidStorage mixedStorage(AtomicInteger notifications) {
        return storage(
            notifications,
            tank(
                "lava_input",
                FluidTankRole.INPUT,
                8_000,
                resource -> resource.equals(lava())
            ),
            tank(
                "water_input",
                FluidTankRole.INPUT,
                4_000,
                resource -> resource.equals(water())
            ),
            tank(
                "product_output",
                FluidTankRole.OUTPUT,
                16_000,
                resource -> true
            )
        );
    }

    private static @NonNull MachineFluidStorage anyFluidStorage(
        AtomicInteger notifications,
        int capacity
    )
    {
        return storage(
            notifications,
            tank(
                "universal",
                FluidTankRole.BIDIRECTIONAL,
                capacity,
                resource -> true
            )
        );
    }

    private static int insertCommitted(
        @NonNull MachineFluidStorage storage,
        int index,
        FluidResource fluid,
        int amount
    )
    {
        try (Transaction tx = Transaction.openRoot()) {
            int inserted = storage.insert(
                index,
                fluid,
                amount,
                tx
            );
            tx.commit();
            return inserted;
        }
    }

    private static int extractCommitted(
        @NonNull MachineFluidStorage storage,
        int index,
        FluidResource fluid,
        int amount
    )
    {
        try (Transaction tx = Transaction.openRoot()) {
            int extracted = storage.extract(
                index,
                fluid,
                amount,
                tx
            );
            tx.commit();
            return extracted;
        }
    }

    private static @NonNull MachineFluidStorage createStorageForLookupTests(
        Runnable changeListener
    )
    {
        return new MachineFluidStorage(
            List.of(
                new FluidTankDefinition(
                    new FluidTankId("lava_input"),
                    FluidTankRole.INPUT,
                    8_000,
                    resource -> true
                ),
                new FluidTankDefinition(
                    new FluidTankId("water_input"),
                    FluidTankRole.INPUT,
                    4_000,
                    resource -> true
                ),
                new FluidTankDefinition(
                    new FluidTankId("product_output"),
                    FluidTankRole.OUTPUT,
                    16_000,
                    resource -> true
                )
            ),
            changeListener
        );
    }

    @Test
    void shouldCreateSeveralEmptyTanksWithIndividualCapacities() {
        AtomicInteger notifications = new AtomicInteger();
        MachineFluidStorage storage = mixedStorage(notifications);

        assertAll(
            () -> assertEquals(
                3,
                storage.size()
            ),
            () -> assertEquals(
                8_000,
                storage.getCapacityAsInt(
                    0,
                    lava()
                )
            ),
            () -> assertEquals(
                4_000,
                storage.getCapacityAsInt(
                    1,
                    water()
                )
            ),
            () -> assertEquals(
                16_000,
                storage.getCapacityAsInt(
                    2,
                    lava()
                )
            ),
            () -> assertEquals(
                0,
                storage.getAmountAsInt(0)
            ),
            () -> assertEquals(
                0,
                storage.getAmountAsInt(1)
            ),
            () -> assertEquals(
                0,
                storage.getAmountAsInt(2)
            ),
            () -> assertEquals(
                FluidResource.EMPTY,
                storage.getResource(0)
            ),
            () -> assertEquals(
                FluidResource.EMPTY,
                storage.getResource(1)
            ),
            () -> assertEquals(
                FluidResource.EMPTY,
                storage.getResource(2)
            ),
            () -> assertEquals(
                0,
                notifications.get()
            )
        );
    }

    @Test
    void shouldSupportSingleTankWithMinimumCapacity() {
        MachineFluidStorage storage = storage(
            new AtomicInteger(),
            tank(
                "minimal",
                FluidTankRole.INPUT,
                1,
                resource -> true
            )
        );

        assertAll(
            () -> assertEquals(
                1,
                storage.size()
            ),
            () -> assertEquals(
                1,
                storage.getCapacityAsInt(
                    0,
                    water()
                )
            )
        );
    }

    @Test
    void shouldRejectNullDefinitionsList() {
        assertThrows(
            NullPointerException.class,
            () -> new MachineFluidStorage(
                null,
                () -> {
                }
            )
        );
    }

    @Test
    void shouldRejectEmptyDefinitionsList() {
        assertThrows(
            IllegalArgumentException.class,
            () -> new MachineFluidStorage(
                List.of(),
                () -> {
                }
            )
        );
    }

    @Test
    void shouldRejectNullDefinitionInsideList() {
        List<FluidTankDefinition> definitions = new ArrayList<>();
        definitions.add(tank(
            "first",
            FluidTankRole.INPUT,
            1_000,
            resource -> true
        ));
        definitions.add(null);

        assertThrows(
            NullPointerException.class,
            () -> new MachineFluidStorage(
                definitions,
                () -> {
                }
            )
        );
    }

    @Test
    void shouldRejectDuplicateTankIdWithDifferentProperties() {
        List<FluidTankDefinition> definitions = List.of(
            tank(
                "same",
                FluidTankRole.INPUT,
                1_000,
                resource -> true
            ),
            tank(
                "same",
                FluidTankRole.OUTPUT,
                9_000,
                resource -> false
            )
        );

        assertThrows(
            IllegalArgumentException.class,
            () -> new MachineFluidStorage(
                definitions,
                () -> {
                }
            )
        );
    }

    @Test
    void shouldRejectNullChangeListener() {
        assertThrows(
            NullPointerException.class,
            () -> new MachineFluidStorage(
                List.of(tank(
                    "one",
                    FluidTankRole.INPUT,
                    1_000,
                    resource -> true
                )),
                null
            )
        );
    }

    @Test
    void shouldDefensivelyCopyInputDefinitionsList() {
        List<FluidTankDefinition> definitions = new ArrayList<>();
        definitions.add(tank(
            "original",
            FluidTankRole.INPUT,
            3_000,
            resource -> true
        ));

        MachineFluidStorage storage = new MachineFluidStorage(
            definitions,
            () -> {
            }
        );
        definitions.clear();
        definitions.add(tank(
            "replacement",
            FluidTankRole.INPUT,
            7_000,
            resource -> true
        ));

        assertAll(
            () -> assertEquals(
                1,
                storage.size()
            ),
            () -> assertEquals(
                3_000,
                storage.getCapacityAsInt(
                    0,
                    water()
                )
            )
        );
    }

    @Test
    void shouldPreserveDefinitionOrderAsTankIndices() {
        MachineFluidStorage storage = new MachineFluidStorage(
            List.of(
                tank(
                    "large",
                    FluidTankRole.INPUT,
                    9_000,
                    resource -> true
                ),
                tank(
                    "small",
                    FluidTankRole.OUTPUT,
                    200,
                    resource -> true
                )
            ),
            () -> {
            }
        );

        assertAll(
            () -> assertEquals(
                9_000,
                storage.getCapacityAsInt(
                    0,
                    water()
                )
            ),
            () -> assertEquals(
                200,
                storage.getCapacityAsInt(
                    1,
                    lava()
                )
            )
        );
    }

    @Test
    void shouldNotNotifyOnConstructionOrQueries() {
        AtomicInteger notifications = new AtomicInteger();
        MachineFluidStorage storage = mixedStorage(notifications);

        storage.size();
        storage.getCapacityAsInt(
            0,
            lava()
        );
        storage.getAmountAsInt(1);
        storage.getResource(2);
        storage.isValid(
            0,
            lava()
        );

        assertEquals(
            0,
            notifications.get()
        );
    }

    @Test
    void shouldApplyDifferentFluidFiltersToEachTank() {
        MachineFluidStorage storage = mixedStorage(new AtomicInteger());

        assertAll(
            () -> assertTrue(storage.isValid(
                0,
                lava()
            )),
            () -> assertFalse(storage.isValid(
                0,
                water()
            )),
            () -> assertTrue(storage.isValid(
                1,
                water()
            )),
            () -> assertFalse(storage.isValid(
                1,
                lava()
            )),
            () -> assertTrue(storage.isValid(
                2,
                water()
            )),
            () -> assertTrue(storage.isValid(
                2,
                lava()
            ))
        );
    }

    @Test
    void shouldReturnFalseForInvalidIndicesInIsValid() {
        MachineFluidStorage storage = mixedStorage(new AtomicInteger());

        assertAll(
            () -> assertFalse(storage.isValid(
                -1,
                water()
            )),
            () -> assertFalse(storage.isValid(
                3,
                water()
            )),
            () -> assertFalse(storage.isValid(
                Integer.MAX_VALUE,
                water()
            ))
        );
    }

    @Test
    void shouldNotEvaluateFilterForInvalidIndex() {
        AtomicInteger evaluations = new AtomicInteger();
        MachineFluidStorage storage = storage(
            new AtomicInteger(),
            tank(
                "one",
                FluidTankRole.INPUT,
                1_000,
                resource -> {
                    evaluations.incrementAndGet();
                    return true;
                }
            )
        );

        assertFalse(storage.isValid(
            -1,
            lava()
        ));
        assertFalse(storage.isValid(
            1,
            lava()
        ));
        assertEquals(
            0,
            evaluations.get()
        );
    }

    @Test
    void shouldInsertAllowedFluidAndNotifyAfterCommit() {
        AtomicInteger notifications = new AtomicInteger();
        MachineFluidStorage storage = mixedStorage(notifications);

        try (Transaction tx = Transaction.openRoot()) {
            assertEquals(
                2_000,
                storage.insert(
                    0,
                    lava(),
                    2_000,
                    tx
                )
            );
            assertEquals(
                0,
                notifications.get(),
                "Do not notify before root commit"
            );
            tx.commit();
        }

        assertAll(
            () -> assertEquals(
                2_000,
                storage.getAmountAsInt(0)
            ),
            () -> assertEquals(
                lava(),
                storage.getResource(0)
            ),
            () -> assertEquals(
                1,
                notifications.get()
            )
        );
    }

    @Test
    void shouldRejectForbiddenFluidWithoutNotification() {
        AtomicInteger notifications = new AtomicInteger();
        MachineFluidStorage storage = mixedStorage(notifications);

        assertEquals(
            0,
            insertCommitted(
                storage,
                0,
                water(),
                2_000
            )
        );

        assertAll(
            () -> assertEquals(
                0,
                storage.getAmountAsInt(0)
            ),
            () -> assertEquals(
                FluidResource.EMPTY,
                storage.getResource(0)
            ),
            () -> assertEquals(
                0,
                notifications.get()
            )
        );
    }

    @Test
    void shouldPartiallyInsertWhenCapacityIsReached() {
        AtomicInteger notifications = new AtomicInteger();
        MachineFluidStorage storage = mixedStorage(notifications);

        assertEquals(
            8_000,
            insertCommitted(
                storage,
                0,
                lava(),
                10_000
            )
        );

        assertAll(
            () -> assertEquals(
                8_000,
                storage.getAmountAsInt(0)
            ),
            () -> assertEquals(
                1,
                notifications.get()
            )
        );
    }

    @Test
    void shouldRejectInsertWhenTankIsFull() {
        AtomicInteger notifications = new AtomicInteger();
        MachineFluidStorage storage = mixedStorage(notifications);

        assertEquals(
            4_000,
            insertCommitted(
                storage,
                1,
                water(),
                4_000
            )
        );
        assertEquals(
            0,
            insertCommitted(
                storage,
                1,
                water(),
                100
            )
        );

        assertAll(
            () -> assertEquals(
                4_000,
                storage.getAmountAsInt(1)
            ),
            () -> assertEquals(
                1,
                notifications.get()
            )
        );
    }

    @Test
    void shouldNotMixDifferentFluidsWithinSameTank() {
        AtomicInteger notifications = new AtomicInteger();
        MachineFluidStorage storage = anyFluidStorage(
            notifications,
            5_000
        );

        assertEquals(
            2_000,
            insertCommitted(
                storage,
                0,
                water(),
                2_000
            )
        );
        assertEquals(
            0,
            insertCommitted(
                storage,
                0,
                lava(),
                500
            )
        );

        assertAll(
            () -> assertEquals(
                2_000,
                storage.getAmountAsInt(0)
            ),
            () -> assertEquals(
                water(),
                storage.getResource(0)
            ),
            () -> assertEquals(
                1,
                notifications.get()
            )
        );
    }

    @Test
    void shouldAllowDifferentFluidsInDifferentTanks() {
        AtomicInteger notifications = new AtomicInteger();
        MachineFluidStorage storage = mixedStorage(notifications);

        assertEquals(
            1_000,
            insertCommitted(
                storage,
                0,
                lava(),
                1_000
            )
        );
        assertEquals(
            700,
            insertCommitted(
                storage,
                1,
                water(),
                700
            )
        );

        assertAll(
            () -> assertEquals(
                lava(),
                storage.getResource(0)
            ),
            () -> assertEquals(
                water(),
                storage.getResource(1)
            ),
            () -> assertEquals(
                1_000,
                storage.getAmountAsInt(0)
            ),
            () -> assertEquals(
                700,
                storage.getAmountAsInt(1)
            ),
            () -> assertEquals(
                2,
                notifications.get()
            )
        );
    }

    @Test
    void shouldExtractRequestedAmountFromMatchingTank() {
        AtomicInteger notifications = new AtomicInteger();
        MachineFluidStorage storage = mixedStorage(notifications);

        insertCommitted(
            storage,
            0,
            lava(),
            3_000
        );
        assertEquals(
            1_200,
            extractCommitted(
                storage,
                0,
                lava(),
                1_200
            )
        );

        assertAll(
            () -> assertEquals(
                1_800,
                storage.getAmountAsInt(0)
            ),
            () -> assertEquals(
                lava(),
                storage.getResource(0)
            ),
            () -> assertEquals(
                2,
                notifications.get()
            )
        );
    }

    @Test
    void shouldNotExtractDifferentFluid() {
        AtomicInteger notifications = new AtomicInteger();
        MachineFluidStorage storage = anyFluidStorage(
            notifications,
            5_000
        );

        insertCommitted(
            storage,
            0,
            water(),
            2_000
        );
        assertEquals(
            0,
            extractCommitted(
                storage,
                0,
                lava(),
                500
            )
        );

        assertAll(
            () -> assertEquals(
                2_000,
                storage.getAmountAsInt(0)
            ),
            () -> assertEquals(
                water(),
                storage.getResource(0)
            ),
            () -> assertEquals(
                1,
                notifications.get()
            )
        );
    }

    @Test
    void shouldExtractAtMostStoredAmount() {
        AtomicInteger notifications = new AtomicInteger();
        MachineFluidStorage storage = mixedStorage(notifications);

        insertCommitted(
            storage,
            0,
            lava(),
            1_000
        );
        assertEquals(
            1_000,
            extractCommitted(
                storage,
                0,
                lava(),
                4_000
            )
        );

        assertAll(
            () -> assertEquals(
                0,
                storage.getAmountAsInt(0)
            ),
            () -> assertEquals(
                FluidResource.EMPTY,
                storage.getResource(0)
            ),
            () -> assertEquals(
                2,
                notifications.get()
            )
        );
    }

    @Test
    void shouldKeepAllOtherTanksUnchangedOnExtraction() {
        MachineFluidStorage storage = mixedStorage(new AtomicInteger());

        insertCommitted(
            storage,
            0,
            lava(),
            2_000
        );
        insertCommitted(
            storage,
            1,
            water(),
            1_500
        );
        assertEquals(
            700,
            extractCommitted(
                storage,
                0,
                lava(),
                700
            )
        );

        assertAll(
            () -> assertEquals(
                1_300,
                storage.getAmountAsInt(0)
            ),
            () -> assertEquals(
                1_500,
                storage.getAmountAsInt(1)
            ),
            () -> assertEquals(
                water(),
                storage.getResource(1)
            ),
            () -> assertEquals(
                0,
                storage.getAmountAsInt(2)
            )
        );
    }

    @Test
    void shouldRollbackUncommittedInsertWithoutNotification() {
        AtomicInteger notifications = new AtomicInteger();
        MachineFluidStorage storage = mixedStorage(notifications);

        try (Transaction tx = Transaction.openRoot()) {
            assertEquals(
                700,
                storage.insert(
                    1,
                    water(),
                    700,
                    tx
                )
            );
            assertEquals(
                0,
                notifications.get()
            );
            // No commit: closing the transaction must restore the previous state.
        }

        assertAll(
            () -> assertEquals(
                0,
                storage.getAmountAsInt(1)
            ),
            () -> assertEquals(
                FluidResource.EMPTY,
                storage.getResource(1)
            ),
            () -> assertEquals(
                0,
                notifications.get()
            )
        );
    }

    @Test
    void shouldRollbackUncommittedExtractWithoutNotification() {
        AtomicInteger notifications = new AtomicInteger();
        MachineFluidStorage storage = mixedStorage(notifications);

        insertCommitted(
            storage,
            0,
            lava(),
            1_000
        );
        assertEquals(
            1,
            notifications.get()
        );

        try (Transaction tx = Transaction.openRoot()) {
            assertEquals(
                400,
                storage.extract(
                    0,
                    lava(),
                    400,
                    tx
                )
            );
            assertEquals(
                1,
                notifications.get()
            );
        }

        assertAll(
            () -> assertEquals(
                1_000,
                storage.getAmountAsInt(0)
            ),
            () -> assertEquals(
                lava(),
                storage.getResource(0)
            ),
            () -> assertEquals(
                1,
                notifications.get()
            )
        );
    }

    @Test
    void shouldNotifyOnlyOnceForRepeatedChangesToSameTankInOneTransaction() {
        AtomicInteger notifications = new AtomicInteger();
        MachineFluidStorage storage = anyFluidStorage(
            notifications,
            5_000
        );

        try (Transaction tx = Transaction.openRoot()) {
            assertEquals(
                400,
                storage.insert(
                    0,
                    water(),
                    400,
                    tx
                )
            );
            assertEquals(
                600,
                storage.insert(
                    0,
                    water(),
                    600,
                    tx
                )
            );
            assertEquals(
                100,
                storage.extract(
                    0,
                    water(),
                    100,
                    tx
                )
            );
            assertEquals(
                0,
                notifications.get()
            );
            tx.commit();
        }

        assertAll(
            () -> assertEquals(
                900,
                storage.getAmountAsInt(0)
            ),
            () -> assertEquals(
                1,
                notifications.get()
            )
        );
    }

    @Test
    void shouldNotifyOncePerChangedTankAfterSingleCommit() {
        AtomicInteger notifications = new AtomicInteger();
        MachineFluidStorage storage = mixedStorage(notifications);

        try (Transaction tx = Transaction.openRoot()) {
            assertEquals(
                1_000,
                storage.insert(
                    0,
                    lava(),
                    1_000,
                    tx
                )
            );
            assertEquals(
                500,
                storage.insert(
                    1,
                    water(),
                    500,
                    tx
                )
            );
            assertEquals(
                0,
                notifications.get()
            );
            tx.commit();
        }

        assertAll(
            () -> assertEquals(
                2,
                notifications.get()
            ),
            () -> assertEquals(
                1_000,
                storage.getAmountAsInt(0)
            ),
            () -> assertEquals(
                500,
                storage.getAmountAsInt(1)
            )
        );
    }

    @Test
    void shouldNotNotifyIfNoOperationChangesContents() {
        AtomicInteger notifications = new AtomicInteger();
        MachineFluidStorage storage = mixedStorage(notifications);

        try (Transaction tx = Transaction.openRoot()) {
            assertEquals(
                0,
                storage.insert(
                    0,
                    water(),
                    500,
                    tx
                )
            );
            assertEquals(
                0,
                storage.extract(
                    0,
                    lava(),
                    500,
                    tx
                )
            );
            tx.commit();
        }

        assertEquals(
            0,
            notifications.get()
        );
    }

    @Test
    void shouldRollbackCommittedChildWhenRootIsAborted() {
        AtomicInteger notifications = new AtomicInteger();
        MachineFluidStorage storage = anyFluidStorage(
            notifications,
            5_000
        );

        try (Transaction root = Transaction.openRoot()) {
            try (Transaction child = Transaction.open(root)) {
                assertEquals(
                    1_000,
                    storage.insert(
                        0,
                        water(),
                        1_000,
                        child
                    )
                );
                child.commit();
            }
            assertEquals(
                0,
                notifications.get()
            );
            // Do not commit root; child commit alone cannot make the change permanent.
        }

        assertAll(
            () -> assertEquals(
                0,
                storage.getAmountAsInt(0)
            ),
            () -> assertEquals(
                FluidResource.EMPTY,
                storage.getResource(0)
            ),
            () -> assertEquals(
                0,
                notifications.get()
            )
        );
    }

    @Test
    void shouldCommitChangesMadeInCommittedChildWithCommittedRoot() {
        AtomicInteger notifications = new AtomicInteger();
        MachineFluidStorage storage = anyFluidStorage(
            notifications,
            5_000
        );

        try (Transaction root = Transaction.openRoot()) {
            try (Transaction child = Transaction.open(root)) {
                assertEquals(
                    1_000,
                    storage.insert(
                        0,
                        water(),
                        1_000,
                        child
                    )
                );
                child.commit();
            }
            assertEquals(
                0,
                notifications.get()
            );
            root.commit();
        }

        assertAll(
            () -> assertEquals(
                1_000,
                storage.getAmountAsInt(0)
            ),
            () -> assertEquals(
                water(),
                storage.getResource(0)
            ),
            () -> assertEquals(
                1,
                notifications.get()
            )
        );
    }

    @Test
    void shouldDiscardUncommittedChildChangesButCommitRootChanges() {
        AtomicInteger notifications = new AtomicInteger();
        MachineFluidStorage storage = anyFluidStorage(
            notifications,
            5_000
        );

        try (Transaction root = Transaction.openRoot()) {
            assertEquals(
                700,
                storage.insert(
                    0,
                    water(),
                    700,
                    root
                )
            );
            try (Transaction child = Transaction.open(root)) {
                assertEquals(
                    300,
                    storage.insert(
                        0,
                        water(),
                        300,
                        child
                    )
                );
                // Child closes without commit.
            }
            assertEquals(
                0,
                notifications.get()
            );
            root.commit();
        }

        assertAll(
            () -> assertEquals(
                700,
                storage.getAmountAsInt(0)
            ),
            () -> assertEquals(
                1,
                notifications.get()
            )
        );
    }

    @Test
    void shouldAllowDifferentFluidAfterTankBecomesEmpty() {
        AtomicInteger notifications = new AtomicInteger();
        MachineFluidStorage storage = anyFluidStorage(
            notifications,
            3_000
        );

        assertEquals(
            2_000,
            insertCommitted(
                storage,
                0,
                water(),
                2_000
            )
        );
        assertEquals(
            2_000,
            extractCommitted(
                storage,
                0,
                water(),
                2_000
            )
        );
        assertEquals(
            1_000,
            insertCommitted(
                storage,
                0,
                lava(),
                1_000
            )
        );

        assertAll(
            () -> assertEquals(
                lava(),
                storage.getResource(0)
            ),
            () -> assertEquals(
                1_000,
                storage.getAmountAsInt(0)
            ),
            () -> assertEquals(
                3,
                notifications.get()
            )
        );
    }

    @Test
    void shouldNotApplyExternalPortPermissionsInsideStorage() {
        AtomicInteger notifications = new AtomicInteger();
        MachineFluidStorage storage = storage(
            notifications,
            tank(
                "output",
                FluidTankRole.OUTPUT,
                4_000,
                resource -> true
            )
        );

        // Production-side operations can fill an OUTPUT tank;
        // a future port handler must enforce external access rules.
        assertEquals(
            800,
            insertCommitted(
                storage,
                0,
                water(),
                800
            )
        );
        assertEquals(
            800,
            storage.getAmountAsInt(0)
        );
        assertEquals(
            1,
            notifications.get()
        );
    }

    @Test
    void shouldNotifyImmediatelyForDirectSet() {
        AtomicInteger notifications = new AtomicInteger();
        MachineFluidStorage storage = anyFluidStorage(
            notifications,
            4_000
        );

        storage.set(
            0,
            water(),
            1_500
        );

        assertAll(
            () -> assertEquals(
                1_500,
                storage.getAmountAsInt(0)
            ),
            () -> assertEquals(
                water(),
                storage.getResource(0)
            ),
            () -> assertEquals(
                1,
                notifications.get()
            )
        );
    }


    @Test
    void shouldFindExistingTankById() {
        MachineFluidStorage storage = createStorageForLookupTests(() -> {
        });

        assertAll(
            () -> assertEquals(
                OptionalInt.of(0),
                storage.findTankIndex(new FluidTankId("lava_input"))
            ),
            () -> assertEquals(
                OptionalInt.of(1),
                storage.findTankIndex(new FluidTankId("water_input"))
            ),
            () -> assertEquals(
                OptionalInt.of(2),
                storage.findTankIndex(new FluidTankId("product_output"))
            )
        );
    }

    @Test
    void shouldFindTankUsingDifferentIdInstanceWithSameValue() {
        FluidTankId originalId = new FluidTankId("lava_input");

        MachineFluidStorage storage = new MachineFluidStorage(
            List.of(new FluidTankDefinition(
                originalId,
                FluidTankRole.INPUT,
                8_000,
                resource -> true
            )),
            () -> {
            }
        );

        FluidTankId lookupId = new FluidTankId("lava_input");

        assertNotSame(
            originalId,
            lookupId
        );

        assertEquals(
            OptionalInt.of(0),
            storage.findTankIndex(lookupId)
        );
    }

    @Test
    void shouldReturnEmptyWhenTankIdDoesNotExist() {
        MachineFluidStorage storage = createStorageForLookupTests(() -> {
        });

        assertEquals(
            OptionalInt.empty(),
            storage.findTankIndex(new FluidTankId("unknown"))
        );
    }

    @Test
    void shouldRejectNullTankId() {
        MachineFluidStorage storage = createStorageForLookupTests(() -> {
        });

        assertThrows(
            NullPointerException.class,
            () -> storage.findTankIndex(null)
        );
    }

}
