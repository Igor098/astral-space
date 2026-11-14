package ru.greatdragonx.astralspace.common.fluid.port;

import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import ru.greatdragonx.astralspace.common.fluid.storage.FluidTankDefinition;
import ru.greatdragonx.astralspace.common.fluid.storage.FluidTankId;
import ru.greatdragonx.astralspace.common.fluid.storage.FluidTankRole;
import ru.greatdragonx.astralspace.common.fluid.storage.MachineFluidStorage;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class FluidPortHandlerTest {

    private static final FluidPortId LEFT = new FluidPortId("left");
    private static final FluidPortId RIGHT = new FluidPortId("right");
    private static final FluidTankId LAVA_INPUT = new FluidTankId("lava_input");
    private static final FluidTankId WATER_INPUT = new FluidTankId("water_input");
    private static final FluidTankId PRODUCT_OUTPUT = new FluidTankId("product_output");

    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        Bootstrap.bootStrap();
    }

    private static FluidResource lava() {
        return FluidResource.of(Fluids.LAVA);
    }

    private static FluidResource water() {
        return FluidResource.of(Fluids.WATER);
    }

    /**
     * Internal order (MachineFluidStorage):
     * 0 -> lava_input, 1 -> water_input, 2 -> product_output.
     */
    private static List<FluidTankDefinition> tankDefinitions() {
        return List.of(
            new FluidTankDefinition(
                LAVA_INPUT,
                FluidTankRole.INPUT,
                8_000,
                resource -> resource.equals(lava())
            ),
            new FluidTankDefinition(
                WATER_INPUT,
                FluidTankRole.INPUT,
                4_000,
                resource -> resource.equals(water())
            ),
            new FluidTankDefinition(
                PRODUCT_OUTPUT,
                FluidTankRole.OUTPUT,
                16_000,
                resource -> resource.equals(water())
            )
        );
    }

    private static MachineFluidStorage storage(AtomicInteger notifications) {
        return new MachineFluidStorage(
            tankDefinitions(),
            notifications::incrementAndGet
        );
    }

    /**
     * External order (FluidPortHandler):
     * 0 -> lava_input (storage 0)
     * 1 -> product_output (storage 2)
     * <p>
     * SERVICE repeats lava_input but does not create a third external index.
     */
    private static FluidPortRoute standardRoute() {
        return new FluidPortRoute(
            List.of(LAVA_INPUT),
            List.of(PRODUCT_OUTPUT),
            List.of(LAVA_INPUT)
        );
    }

    private static Fixture fixture(FluidPortMode initialMode) {
        return fixture(
            standardRoute(),
            initialMode
        );
    }

    private static Fixture fixture(
        FluidPortRoute route,
        FluidPortMode initialMode
    )
    {
        AtomicInteger notifications = new AtomicInteger();
        MachineFluidStorage storage = storage(notifications);
        AtomicReference<FluidPortMode> mode = new AtomicReference<>(initialMode);
        AtomicBoolean available = new AtomicBoolean(true);
        FluidPortRouting routing = new FluidPortRouting(
            Map.of(
                LEFT,
                route
            ),
            tankDefinitions()
        );
        FluidPortPolicy policy = new ConfigurableFluidPortPolicy(
            Map.of(
                LEFT,
                EnumSet.allOf(FluidPortMode.class)
            ),
            ignored -> mode.get(),
            ignored -> available.get()
        );
        FluidPortHandler handler = new FluidPortHandler(
            LEFT,
            policy,
            routing,
            storage
        );

        return new Fixture(
            handler,
            storage,
            routing,
            policy,
            mode,
            available,
            notifications
        );
    }

    private record Fixture(
        FluidPortHandler handler,
        MachineFluidStorage storage,
        FluidPortRouting routing,
        FluidPortPolicy policy,
        AtomicReference<FluidPortMode> mode,
        AtomicBoolean available,
        AtomicInteger notifications
    )
    {}

    private static int insertCommitted(
        FluidPortHandler handler,
        int externalIndex,
        FluidResource resource,
        int amount
    )
    {
        try (Transaction tx = Transaction.openRoot()) {
            int inserted = handler.insert(
                externalIndex,
                resource,
                amount,
                tx
            );
            tx.commit();
            return inserted;
        }
    }

    private static int extractCommitted(
        FluidPortHandler handler,
        int externalIndex,
        FluidResource resource,
        int amount
    )
    {
        try (Transaction tx = Transaction.openRoot()) {
            int extracted = handler.extract(
                externalIndex,
                resource,
                amount,
                tx
            );
            tx.commit();
            return extracted;
        }
    }

    private static int seedStorage(
        MachineFluidStorage storage,
        int internalIndex,
        FluidResource resource,
        int amount
    )
    {
        try (Transaction tx = Transaction.openRoot()) {
            int inserted = storage.insert(
                internalIndex,
                resource,
                amount,
                tx
            );
            tx.commit();
            return inserted;
        }
    }

    // -------------------------------------------------------------------------
    // Construction and fixed external-to-internal mapping
    // -------------------------------------------------------------------------

    @Test
    void shouldRejectNullConstructorArguments() {
        Fixture fixture = fixture(FluidPortMode.INPUT);

        assertAll(
            () -> assertThrows(
                NullPointerException.class,
                () -> new FluidPortHandler(
                    null,
                    fixture.policy(),
                    fixture.routing(),
                    fixture.storage()
                )
            ),
            () -> assertThrows(
                NullPointerException.class,
                () -> new FluidPortHandler(
                    LEFT,
                    null,
                    fixture.routing(),
                    fixture.storage()
                )
            ),
            () -> assertThrows(
                NullPointerException.class,
                () -> new FluidPortHandler(
                    LEFT,
                    fixture.policy(),
                    null,
                    fixture.storage()
                )
            ),
            () -> assertThrows(
                NullPointerException.class,
                () -> new FluidPortHandler(
                    LEFT,
                    fixture.policy(),
                    fixture.routing(),
                    null
                )
            )
        );
    }

    @Test
    void shouldRejectPortWithoutRoute() {
        Fixture fixture = fixture(FluidPortMode.INPUT);

        IllegalArgumentException error = assertThrows(
            IllegalArgumentException.class,
            () -> new FluidPortHandler(
                RIGHT,
                fixture.policy(),
                fixture.routing(),
                fixture.storage()
            )
        );

        assertTrue(error.getMessage()
            .contains("right"));
    }

    @Test
    void shouldRejectInvalidRouteBeforeHandlerConstruction() {
        AtomicInteger notifications = new AtomicInteger();
        MachineFluidStorage storage = storage(notifications);
        FluidPortRoute invalidRoute = new FluidPortRoute(
            List.of(new FluidTankId("missing_tank")),
            List.of(),
            List.of()
        );

        IllegalArgumentException error = assertThrows(
            IllegalArgumentException.class,
            () -> new FluidPortRouting(
                Map.of(
                    LEFT,
                    invalidRoute
                ),
                tankDefinitions()
            )
        );

        assertAll(
            () -> assertTrue(error.getMessage()
                .contains("missing_tank")),
            () -> assertEquals(
                0L,
                storage.getAmountAsLong(0)
            ),
            () -> assertEquals(
                0,
                notifications.get()
            )
        );
    }

    @Test
    void shouldAllowEmptyRouteWithNoExternalTanks() {
        Fixture fixture = fixture(
            new FluidPortRoute(
                List.of(),
                List.of(),
                List.of()
            ),
            FluidPortMode.INPUT
        );

        assertAll(
            () -> assertEquals(
                0,
                fixture.handler()
                    .size()
            ),
            () -> assertEquals(
                0,
                fixture.notifications()
                    .get()
            )
        );
    }

    @Test
    void shouldNotNotifyOrModifyStorageDuringConstruction() {
        Fixture fixture = fixture(FluidPortMode.INPUT);

        assertAll(
            () -> assertEquals(
                0,
                fixture.storage()
                    .getAmountAsLong(0)
            ),
            () -> assertEquals(
                0,
                fixture.storage()
                    .getAmountAsLong(1)
            ),
            () -> assertEquals(
                0,
                fixture.storage()
                    .getAmountAsLong(2)
            ),
            () -> assertEquals(
                0,
                fixture.notifications()
                    .get()
            )
        );
    }

    @Test
    void shouldExposeOnlyUniqueRoutedTanks() {
        Fixture fixture = fixture(FluidPortMode.INPUT);

        assertAll(
            () -> assertEquals(
                3,
                fixture.storage()
                    .size()
            ),
            () -> assertEquals(
                2,
                fixture.handler()
                    .size()
            ),
            () -> assertEquals(
                8_000L,
                fixture.handler()
                    .getCapacityAsLong(
                        0,
                        lava()
                    )
            ),
            () -> assertEquals(
                16_000L,
                fixture.handler()
                    .getCapacityAsLong(
                        1,
                        water()
                    )
            )
        );
    }

    @Test
    void shouldPreserveFirstAppearanceOrderAcrossRouteLists() {
        FluidPortRoute route = new FluidPortRoute(
            List.of(
                WATER_INPUT,
                LAVA_INPUT
            ),
            List.of(PRODUCT_OUTPUT),
            List.of(
                LAVA_INPUT,
                WATER_INPUT
            )
        );
        Fixture fixture = fixture(
            route,
            FluidPortMode.INPUT
        );

        assertAll(
            () -> assertEquals(
                3,
                fixture.handler()
                    .size()
            ),
            () -> assertEquals(
                4_000L,
                fixture.handler()
                    .getCapacityAsLong(
                        0,
                        water()
                    )
            ),
            () -> assertEquals(
                8_000L,
                fixture.handler()
                    .getCapacityAsLong(
                        1,
                        lava()
                    )
            ),
            () -> assertEquals(
                16_000L,
                fixture.handler()
                    .getCapacityAsLong(
                        2,
                        water()
                    )
            )
        );
    }

    @Test
    void shouldResolveTankIdsByValueRatherThanObjectIdentity() {
        FluidPortRoute route = new FluidPortRoute(
            List.of(new FluidTankId("lava_input")),
            List.of(new FluidTankId("product_output")),
            List.of(new FluidTankId("lava_input"))
        );
        Fixture fixture = fixture(
            route,
            FluidPortMode.INPUT
        );

        assertAll(
            () -> assertEquals(
                2,
                fixture.handler()
                    .size()
            ),
            () -> assertEquals(
                8_000L,
                fixture.handler()
                    .getCapacityAsLong(
                        0,
                        lava()
                    )
            ),
            () -> assertEquals(
                16_000L,
                fixture.handler()
                    .getCapacityAsLong(
                        1,
                        water()
                    )
            )
        );
    }

    // -------------------------------------------------------------------------
    // Read-only API and external-index validation
    // -------------------------------------------------------------------------

    @Test
    void shouldReportEmptyResourcesAndZeroAmountsInitially() {
        Fixture fixture = fixture(FluidPortMode.INPUT);

        assertAll(
            () -> assertEquals(
                FluidResource.EMPTY,
                fixture.handler()
                    .getResource(0)
            ),
            () -> assertEquals(
                FluidResource.EMPTY,
                fixture.handler()
                    .getResource(1)
            ),
            () -> assertEquals(
                0L,
                fixture.handler()
                    .getAmountAsLong(0)
            ),
            () -> assertEquals(
                0L,
                fixture.handler()
                    .getAmountAsLong(1)
            ),
            () -> assertEquals(
                0,
                fixture.notifications()
                    .get()
            )
        );
    }

    @Test
    void shouldReadAmountAndResourceFromCorrectNonContiguousStorageIndex() {
        Fixture fixture = fixture(FluidPortMode.DISABLED);
        assertEquals(
            1_750,
            seedStorage(
                fixture.storage(),
                2,
                water(),
                1_750
            )
        );

        assertAll(
            () -> assertEquals(
                water(),
                fixture.handler()
                    .getResource(1)
            ),
            () -> assertEquals(
                1_750L,
                fixture.handler()
                    .getAmountAsLong(1)
            ),
            () -> assertEquals(
                0L,
                fixture.handler()
                    .getAmountAsLong(0)
            ),
            () -> assertEquals(
                0L,
                fixture.storage()
                    .getAmountAsLong(1)
            ),
            () -> assertEquals(
                1,
                fixture.notifications()
                    .get()
            )
        );
    }

    @Test
    void shouldDelegateFluidValidityToCorrectTank() {
        Fixture fixture = fixture(FluidPortMode.INPUT);

        assertAll(
            () -> assertTrue(fixture.handler()
                .isValid(
                    0,
                    lava()
                )),
            () -> assertFalse(fixture.handler()
                .isValid(
                    0,
                    water()
                )),
            () -> assertTrue(fixture.handler()
                .isValid(
                    1,
                    water()
                )),
            () -> assertFalse(fixture.handler()
                .isValid(
                    1,
                    lava()
                ))
        );
    }

    @Test
    void shouldReportZeroCapacityForRejectedFluid() {
        Fixture fixture = fixture(FluidPortMode.DISABLED);

        assertAll(
            () -> assertEquals(
                8_000L,
                fixture.handler()
                    .getCapacityAsLong(
                        0,
                        lava()
                    )
            ),
            () -> assertEquals(
                0L,
                fixture.handler()
                    .getCapacityAsLong(
                        0,
                        water()
                    )
            ),
            () -> assertEquals(
                0L,
                fixture.handler()
                    .getCapacityAsLong(
                        1,
                        lava()
                    )
            ),
            () -> assertEquals(
                16_000L,
                fixture.handler()
                    .getCapacityAsLong(
                        1,
                        water()
                    )
            )
        );
    }

    @Test
    void shouldKeepReadOnlyInformationAvailableWhenPortIsDisabled() {
        Fixture fixture = fixture(FluidPortMode.DISABLED);
        seedStorage(
            fixture.storage(),
            0,
            lava(),
            650
        );

        assertAll(
            () -> assertEquals(
                2,
                fixture.handler()
                    .size()
            ),
            () -> assertEquals(
                lava(),
                fixture.handler()
                    .getResource(0)
            ),
            () -> assertEquals(
                650L,
                fixture.handler()
                    .getAmountAsLong(0)
            ),
            () -> assertEquals(
                8_000L,
                fixture.handler()
                    .getCapacityAsLong(
                        0,
                        lava()
                    )
            ),
            () -> assertTrue(fixture.handler()
                .isValid(
                    0,
                    lava()
                ))
        );
    }

    @Test
    void shouldKeepExternalIndicesStableAcrossModeSwitches() {
        Fixture fixture = fixture(FluidPortMode.INPUT);
        seedStorage(
            fixture.storage(),
            0,
            lava(),
            900
        );
        seedStorage(
            fixture.storage(),
            2,
            water(),
            1_100
        );

        fixture.mode()
            .set(FluidPortMode.OUTPUT);

        assertAll(
            () -> assertEquals(
                2,
                fixture.handler()
                    .size()
            ),
            () -> assertEquals(
                lava(),
                fixture.handler()
                    .getResource(0)
            ),
            () -> assertEquals(
                water(),
                fixture.handler()
                    .getResource(1)
            ),
            () -> assertEquals(
                900L,
                fixture.handler()
                    .getAmountAsLong(0)
            ),
            () -> assertEquals(
                1_100L,
                fixture.handler()
                    .getAmountAsLong(1)
            )
        );

        fixture.mode()
            .set(FluidPortMode.SERVICE);

        assertAll(
            () -> assertEquals(
                lava(),
                fixture.handler()
                    .getResource(0)
            ),
            () -> assertEquals(
                water(),
                fixture.handler()
                    .getResource(1)
            ),
            () -> assertEquals(
                8_000L,
                fixture.handler()
                    .getCapacityAsLong(
                        0,
                        lava()
                    )
            ),
            () -> assertEquals(
                16_000L,
                fixture.handler()
                    .getCapacityAsLong(
                        1,
                        water()
                    )
            )
        );
    }

    @Test
    void shouldRejectInvalidExternalIndicesInReadMethods() {
        Fixture fixture = fixture(FluidPortMode.INPUT);

        for (int index : new int[]{-1, 2, Integer.MAX_VALUE}) {
            assertAll(
                "Invalid external index: " + index,
                () -> assertThrows(
                    IndexOutOfBoundsException.class,
                    () -> fixture.handler()
                        .getResource(index)
                ),
                () -> assertThrows(
                    IndexOutOfBoundsException.class,
                    () -> fixture.handler()
                        .getAmountAsLong(index)
                ),
                () -> assertThrows(
                    IndexOutOfBoundsException.class,
                    () -> fixture.handler()
                        .getCapacityAsLong(
                            index,
                            lava()
                        )
                ),
                () -> assertThrows(
                    IndexOutOfBoundsException.class,
                    () -> fixture.handler()
                        .isValid(
                            index,
                            lava()
                        )
                )
            );
        }
    }

    @Test
    void shouldRejectAnyExternalIndexWhenRouteIsEmpty() {
        Fixture fixture = fixture(
            new FluidPortRoute(
                List.of(),
                List.of(),
                List.of()
            ),
            FluidPortMode.INPUT
        );

        assertAll(
            () -> assertThrows(
                IndexOutOfBoundsException.class,
                () -> fixture.handler()
                    .getResource(0)
            ),
            () -> assertThrows(
                IndexOutOfBoundsException.class,
                () -> fixture.handler()
                    .getAmountAsLong(-1)
            )
        );
    }

    // -------------------------------------------------------------------------
    // Insertion rules: mode + route + tank validity + capacity
    // -------------------------------------------------------------------------

    @Test
    void shouldInsertLavaIntoInputTankAndNotifyOnCommit() {
        Fixture fixture = fixture(FluidPortMode.INPUT);

        try (Transaction tx = Transaction.openRoot()) {
            assertEquals(
                1_500,
                fixture.handler()
                    .insert(
                        0,
                        lava(),
                        1_500,
                        tx
                    )
            );
            assertEquals(
                0,
                fixture.notifications()
                    .get()
            );
            tx.commit();
        }

        assertAll(
            () -> assertEquals(
                1_500L,
                fixture.storage()
                    .getAmountAsLong(0)
            ),
            () -> assertEquals(
                lava(),
                fixture.handler()
                    .getResource(0)
            ),
            () -> assertEquals(
                1,
                fixture.notifications()
                    .get()
            )
        );
    }

    @Test
    void shouldRejectInsertIntoOutputTankEvenInInputMode() {
        Fixture fixture = fixture(FluidPortMode.INPUT);

        assertEquals(
            0,
            insertCommitted(
                fixture.handler(),
                1,
                water(),
                700
            )
        );

        assertAll(
            () -> assertEquals(
                0L,
                fixture.storage()
                    .getAmountAsLong(2)
            ),
            () -> assertEquals(
                0,
                fixture.notifications()
                    .get()
            )
        );
    }

    @Test
    void shouldRejectInvalidFluidInInputTank() {
        Fixture fixture = fixture(FluidPortMode.INPUT);

        assertEquals(
            0,
            insertCommitted(
                fixture.handler(),
                0,
                water(),
                400
            )
        );

        assertAll(
            () -> assertEquals(
                0L,
                fixture.storage()
                    .getAmountAsLong(0)
            ),
            () -> assertEquals(
                0,
                fixture.notifications()
                    .get()
            )
        );
    }

    @Test
    void shouldPartiallyInsertUpToTankCapacity() {
        Fixture fixture = fixture(FluidPortMode.INPUT);

        assertEquals(
            8_000,
            insertCommitted(
                fixture.handler(),
                0,
                lava(),
                12_000
            )
        );

        assertAll(
            () -> assertEquals(
                8_000L,
                fixture.handler()
                    .getAmountAsLong(0)
            ),
            () -> assertEquals(
                1,
                fixture.notifications()
                    .get()
            )
        );
    }

    @Test
    void shouldRejectInsertionIntoFullTankWithoutExtraNotification() {
        Fixture fixture = fixture(FluidPortMode.INPUT);

        assertEquals(
            8_000,
            insertCommitted(
                fixture.handler(),
                0,
                lava(),
                8_000
            )
        );
        assertEquals(
            0,
            insertCommitted(
                fixture.handler(),
                0,
                lava(),
                1
            )
        );

        assertAll(
            () -> assertEquals(
                8_000L,
                fixture.storage()
                    .getAmountAsLong(0)
            ),
            () -> assertEquals(
                1,
                fixture.notifications()
                    .get()
            )
        );
    }

    @Test
    void shouldDenyInsertionInOutputMode() {
        Fixture fixture = fixture(FluidPortMode.OUTPUT);

        assertAll(
            () -> assertEquals(
                0,
                insertCommitted(
                    fixture.handler(),
                    0,
                    lava(),
                    500
                )
            ),
            () -> assertEquals(
                0,
                insertCommitted(
                    fixture.handler(),
                    1,
                    water(),
                    500
                )
            )
        );

        assertEquals(
            0,
            fixture.notifications()
                .get()
        );
    }

    @Test
    void shouldDenyInsertionInServiceMode() {
        Fixture fixture = fixture(FluidPortMode.SERVICE);

        assertEquals(
            0,
            insertCommitted(
                fixture.handler(),
                0,
                lava(),
                300
            )
        );
        assertEquals(
            0L,
            fixture.storage()
                .getAmountAsLong(0)
        );
        assertEquals(
            0,
            fixture.notifications()
                .get()
        );
    }

    @Test
    void shouldDenyInsertionInDisabledMode() {
        Fixture fixture = fixture(FluidPortMode.DISABLED);

        assertEquals(
            0,
            insertCommitted(
                fixture.handler(),
                0,
                lava(),
                300
            )
        );
        assertEquals(
            0L,
            fixture.storage()
                .getAmountAsLong(0)
        );
        assertEquals(
            0,
            fixture.notifications()
                .get()
        );
    }

    @Test
    void shouldDenyInsertionWhenPortIsUnavailable() {
        Fixture fixture = fixture(FluidPortMode.INPUT);
        fixture.available()
            .set(false);

        assertEquals(
            0,
            insertCommitted(
                fixture.handler(),
                0,
                lava(),
                300
            )
        );

        assertAll(
            () -> assertEquals(
                0L,
                fixture.storage()
                    .getAmountAsLong(0)
            ),
            () -> assertEquals(
                0,
                fixture.notifications()
                    .get()
            )
        );
    }

    @Test
    void shouldRecheckModeForAlreadyCreatedHandlerDuringInsertion() {
        Fixture fixture = fixture(FluidPortMode.INPUT);

        assertEquals(
            600,
            insertCommitted(
                fixture.handler(),
                0,
                lava(),
                600
            )
        );

        fixture.mode()
            .set(FluidPortMode.DISABLED);
        assertEquals(
            0,
            insertCommitted(
                fixture.handler(),
                0,
                lava(),
                300
            )
        );

        fixture.mode()
            .set(FluidPortMode.INPUT);
        assertEquals(
            300,
            insertCommitted(
                fixture.handler(),
                0,
                lava(),
                300
            )
        );

        assertAll(
            () -> assertEquals(
                900L,
                fixture.storage()
                    .getAmountAsLong(0)
            ),
            () -> assertEquals(
                2,
                fixture.notifications()
                    .get()
            )
        );
    }

    @Test
    void shouldRecheckAvailabilityForAlreadyCreatedHandler() {
        Fixture fixture = fixture(FluidPortMode.INPUT);
        fixture.available()
            .set(false);

        assertEquals(
            0,
            insertCommitted(
                fixture.handler(),
                0,
                lava(),
                400
            )
        );

        fixture.available()
            .set(true);
        assertEquals(
            400,
            insertCommitted(
                fixture.handler(),
                0,
                lava(),
                400
            )
        );

        fixture.available()
            .set(false);
        assertEquals(
            0,
            insertCommitted(
                fixture.handler(),
                0,
                lava(),
                200
            )
        );

        assertAll(
            () -> assertEquals(
                400L,
                fixture.storage()
                    .getAmountAsLong(0)
            ),
            () -> assertEquals(
                1,
                fixture.notifications()
                    .get()
            )
        );
    }

    @Test
    void shouldAllowTwoPortsToFillSamePhysicalTank() {
        AtomicInteger notifications = new AtomicInteger();
        MachineFluidStorage storage = storage(notifications);

        FluidPortRouting routing = new FluidPortRouting(
            Map.of(
                LEFT,
                new FluidPortRoute(
                    List.of(LAVA_INPUT),
                    List.of(),
                    List.of()
                ),
                RIGHT,
                new FluidPortRoute(
                    List.of(LAVA_INPUT),
                    List.of(),
                    List.of()
                )
            ),
            tankDefinitions()
        );

        FluidPortPolicy policy = new FixedFluidPortPolicy(
            Map.of(
                LEFT,
                FluidPortMode.INPUT,
                RIGHT,
                FluidPortMode.INPUT
            ),
            ignored -> true
        );

        FluidPortHandler leftHandler = new FluidPortHandler(
            LEFT,
            policy,
            routing,
            storage
        );
        FluidPortHandler rightHandler = new FluidPortHandler(
            RIGHT,
            policy,
            routing,
            storage
        );

        assertEquals(
            1_000,
            insertCommitted(
                leftHandler,
                0,
                lava(),
                1_000
            )
        );
        assertEquals(
            2_000,
            insertCommitted(
                rightHandler,
                0,
                lava(),
                2_000
            )
        );

        assertAll(
            () -> assertEquals(
                1,
                leftHandler.size()
            ),
            () -> assertEquals(
                1,
                rightHandler.size()
            ),
            () -> assertEquals(
                3_000L,
                storage.getAmountAsLong(0)
            ),
            () -> assertEquals(
                3_000L,
                leftHandler.getAmountAsLong(0)
            ),
            () -> assertEquals(
                3_000L,
                rightHandler.getAmountAsLong(0)
            ),
            () -> assertEquals(
                2,
                notifications.get()
            )
        );
    }

    // -------------------------------------------------------------------------
    // Extraction rules: OUTPUT vs SERVICE vs INPUT vs DISABLED
    // -------------------------------------------------------------------------

    @Test
    void shouldExtractProductThroughOutputMode() {
        Fixture fixture = fixture(FluidPortMode.OUTPUT);
        seedStorage(
            fixture.storage(),
            2,
            water(),
            2_000
        );

        assertEquals(
            750,
            extractCommitted(
                fixture.handler(),
                1,
                water(),
                750
            )
        );

        assertAll(
            () -> assertEquals(
                1_250L,
                fixture.storage()
                    .getAmountAsLong(2)
            ),
            () -> assertEquals(
                2,
                fixture.notifications()
                    .get()
            )
        );
    }

    @Test
    void shouldNotExtractInputTankThroughNormalOutput() {
        Fixture fixture = fixture(FluidPortMode.OUTPUT);
        seedStorage(
            fixture.storage(),
            0,
            lava(),
            1_500
        );

        assertEquals(
            0,
            extractCommitted(
                fixture.handler(),
                0,
                lava(),
                400
            )
        );

        assertAll(
            () -> assertEquals(
                1_500L,
                fixture.storage()
                    .getAmountAsLong(0)
            ),
            () -> assertEquals(
                1,
                fixture.notifications()
                    .get()
            )
        );
    }

    @Test
    void shouldExtractInputTankThroughServiceMode() {
        Fixture fixture = fixture(FluidPortMode.SERVICE);
        seedStorage(
            fixture.storage(),
            0,
            lava(),
            1_500
        );

        assertEquals(
            650,
            extractCommitted(
                fixture.handler(),
                0,
                lava(),
                650
            )
        );

        assertAll(
            () -> assertEquals(
                850L,
                fixture.storage()
                    .getAmountAsLong(0)
            ),
            () -> assertEquals(
                2,
                fixture.notifications()
                    .get()
            )
        );
    }

    @Test
    void shouldNotExtractOutputTankThroughServiceMode() {
        Fixture fixture = fixture(FluidPortMode.SERVICE);
        seedStorage(
            fixture.storage(),
            2,
            water(),
            1_500
        );

        assertEquals(
            0,
            extractCommitted(
                fixture.handler(),
                1,
                water(),
                350
            )
        );

        assertAll(
            () -> assertEquals(
                1_500L,
                fixture.storage()
                    .getAmountAsLong(2)
            ),
            () -> assertEquals(
                1,
                fixture.notifications()
                    .get()
            )
        );
    }

    @Test
    void shouldNotExtractAnythingInInputMode() {
        Fixture fixture = fixture(FluidPortMode.INPUT);
        seedStorage(
            fixture.storage(),
            0,
            lava(),
            1_000
        );
        seedStorage(
            fixture.storage(),
            2,
            water(),
            1_000
        );

        assertAll(
            () -> assertEquals(
                0,
                extractCommitted(
                    fixture.handler(),
                    0,
                    lava(),
                    100
                )
            ),
            () -> assertEquals(
                0,
                extractCommitted(
                    fixture.handler(),
                    1,
                    water(),
                    100
                )
            )
        );

        assertEquals(
            2,
            fixture.notifications()
                .get()
        );
    }

    @Test
    void shouldNotExtractAnythingInDisabledMode() {
        Fixture fixture = fixture(FluidPortMode.DISABLED);
        seedStorage(
            fixture.storage(),
            0,
            lava(),
            1_000
        );
        seedStorage(
            fixture.storage(),
            2,
            water(),
            1_000
        );

        assertAll(
            () -> assertEquals(
                0,
                extractCommitted(
                    fixture.handler(),
                    0,
                    lava(),
                    100
                )
            ),
            () -> assertEquals(
                0,
                extractCommitted(
                    fixture.handler(),
                    1,
                    water(),
                    100
                )
            )
        );

        assertEquals(
            2,
            fixture.notifications()
                .get()
        );
    }

    @Test
    void shouldDenyExtractionWhenPortIsUnavailable() {
        Fixture fixture = fixture(FluidPortMode.SERVICE);
        seedStorage(
            fixture.storage(),
            0,
            lava(),
            1_000
        );
        fixture.available()
            .set(false);

        assertEquals(
            0,
            extractCommitted(
                fixture.handler(),
                0,
                lava(),
                250
            )
        );

        assertAll(
            () -> assertEquals(
                1_000L,
                fixture.storage()
                    .getAmountAsLong(0)
            ),
            () -> assertEquals(
                1,
                fixture.notifications()
                    .get()
            )
        );
    }

    @Test
    void shouldNotExtractWrongResourceEvenIfRouteAllowsIt() {
        Fixture fixture = fixture(FluidPortMode.SERVICE);
        seedStorage(
            fixture.storage(),
            0,
            lava(),
            1_000
        );

        assertEquals(
            0,
            extractCommitted(
                fixture.handler(),
                0,
                water(),
                250
            )
        );

        assertAll(
            () -> assertEquals(
                1_000L,
                fixture.storage()
                    .getAmountAsLong(0)
            ),
            () -> assertEquals(
                1,
                fixture.notifications()
                    .get()
            )
        );
    }

    @Test
    void shouldExtractOnlyAvailableAmount() {
        Fixture fixture = fixture(FluidPortMode.SERVICE);
        seedStorage(
            fixture.storage(),
            0,
            lava(),
            350
        );

        assertEquals(
            350,
            extractCommitted(
                fixture.handler(),
                0,
                lava(),
                2_000
            )
        );

        assertAll(
            () -> assertEquals(
                0L,
                fixture.storage()
                    .getAmountAsLong(0)
            ),
            () -> assertEquals(
                FluidResource.EMPTY,
                fixture.handler()
                    .getResource(0)
            ),
            () -> assertEquals(
                2,
                fixture.notifications()
                    .get()
            )
        );
    }

    @Test
    void shouldRecheckModeForAlreadyCreatedHandlerDuringExtraction() {
        Fixture fixture = fixture(FluidPortMode.OUTPUT);
        seedStorage(
            fixture.storage(),
            0,
            lava(),
            1_000
        );
        seedStorage(
            fixture.storage(),
            2,
            water(),
            1_000
        );

        assertEquals(
            200,
            extractCommitted(
                fixture.handler(),
                1,
                water(),
                200
            )
        );
        assertEquals(
            0,
            extractCommitted(
                fixture.handler(),
                0,
                lava(),
                200
            )
        );

        fixture.mode()
            .set(FluidPortMode.SERVICE);

        assertEquals(
            0,
            extractCommitted(
                fixture.handler(),
                1,
                water(),
                200
            )
        );
        assertEquals(
            250,
            extractCommitted(
                fixture.handler(),
                0,
                lava(),
                250
            )
        );

        assertAll(
            () -> assertEquals(
                750L,
                fixture.storage()
                    .getAmountAsLong(0)
            ),
            () -> assertEquals(
                800L,
                fixture.storage()
                    .getAmountAsLong(2)
            ),
            () -> assertEquals(
                4,
                fixture.notifications()
                    .get()
            )
        );
    }

    // -------------------------------------------------------------------------
    // Transactional correctness and argument validation
    // -------------------------------------------------------------------------

    @Test
    void shouldRollbackUncommittedInsertThroughPort() {
        Fixture fixture = fixture(FluidPortMode.INPUT);

        try (Transaction tx = Transaction.openRoot()) {
            assertEquals(
                1_000,
                fixture.handler()
                    .insert(
                        0,
                        lava(),
                        1_000,
                        tx
                    )
            );
            assertEquals(
                0,
                fixture.notifications()
                    .get()
            );
        }

        assertAll(
            () -> assertEquals(
                0L,
                fixture.storage()
                    .getAmountAsLong(0)
            ),
            () -> assertEquals(
                FluidResource.EMPTY,
                fixture.handler()
                    .getResource(0)
            ),
            () -> assertEquals(
                0,
                fixture.notifications()
                    .get()
            )
        );
    }

    @Test
    void shouldRollbackUncommittedExtractThroughPort() {
        Fixture fixture = fixture(FluidPortMode.SERVICE);
        seedStorage(
            fixture.storage(),
            0,
            lava(),
            1_000
        );

        try (Transaction tx = Transaction.openRoot()) {
            assertEquals(
                400,
                fixture.handler()
                    .extract(
                        0,
                        lava(),
                        400,
                        tx
                    )
            );
            assertEquals(
                1,
                fixture.notifications()
                    .get()
            );
        }

        assertAll(
            () -> assertEquals(
                1_000L,
                fixture.storage()
                    .getAmountAsLong(0)
            ),
            () -> assertEquals(
                1,
                fixture.notifications()
                    .get()
            )
        );
    }

    @Test
    void shouldNotifyOnceForSeveralInsertsToSameTankInOneTransaction() {
        Fixture fixture = fixture(FluidPortMode.INPUT);

        try (Transaction tx = Transaction.openRoot()) {
            assertEquals(
                400,
                fixture.handler()
                    .insert(
                        0,
                        lava(),
                        400,
                        tx
                    )
            );
            assertEquals(
                600,
                fixture.handler()
                    .insert(
                        0,
                        lava(),
                        600,
                        tx
                    )
            );
            assertEquals(
                0,
                fixture.notifications()
                    .get()
            );
            tx.commit();
        }

        assertAll(
            () -> assertEquals(
                1_000L,
                fixture.storage()
                    .getAmountAsLong(0)
            ),
            () -> assertEquals(
                1,
                fixture.notifications()
                    .get()
            )
        );
    }

    @Test
    void shouldRollbackCommittedNestedTransactionWhenRootAborts() {
        Fixture fixture = fixture(FluidPortMode.INPUT);

        try (Transaction root = Transaction.openRoot()) {
            try (Transaction child = Transaction.open(root)) {
                assertEquals(
                    1_000,
                    fixture.handler()
                        .insert(
                            0,
                            lava(),
                            1_000,
                            child
                        )
                );
                child.commit();
            }
            assertEquals(
                0,
                fixture.notifications()
                    .get()
            );
            // The root is deliberately not committed.
        }

        assertAll(
            () -> assertEquals(
                0L,
                fixture.storage()
                    .getAmountAsLong(0)
            ),
            () -> assertEquals(
                0,
                fixture.notifications()
                    .get()
            )
        );
    }

    @Test
    void shouldCommitNestedTransactionOnlyAfterRootCommit() {
        Fixture fixture = fixture(FluidPortMode.INPUT);

        try (Transaction root = Transaction.openRoot()) {
            try (Transaction child = Transaction.open(root)) {
                assertEquals(
                    900,
                    fixture.handler()
                        .insert(
                            0,
                            lava(),
                            900,
                            child
                        )
                );
                child.commit();
            }
            assertEquals(
                0,
                fixture.notifications()
                    .get()
            );
            root.commit();
        }

        assertAll(
            () -> assertEquals(
                900L,
                fixture.storage()
                    .getAmountAsLong(0)
            ),
            () -> assertEquals(
                1,
                fixture.notifications()
                    .get()
            )
        );
    }

    @Test
    void shouldRejectNegativeAmountsInBothOperations() {
        Fixture fixture = fixture(FluidPortMode.INPUT);

        try (Transaction tx = Transaction.openRoot()) {
            assertAll(
                () -> assertThrows(
                    IllegalArgumentException.class,
                    () -> fixture.handler()
                        .insert(
                            0,
                            lava(),
                            -1,
                            tx
                        )
                ),
                () -> assertThrows(
                    IllegalArgumentException.class,
                    () -> fixture.handler()
                        .extract(
                            0,
                            lava(),
                            -1,
                            tx
                        )
                )
            );
        }

        assertEquals(
            0,
            fixture.notifications()
                .get()
        );
    }

    @Test
    void shouldRejectNullFluidAndTransactionInBothOperations() {
        Fixture fixture = fixture(FluidPortMode.INPUT);

        try (Transaction tx = Transaction.openRoot()) {
            assertAll(
                () -> assertThrows(
                    NullPointerException.class,
                    () -> fixture.handler()
                        .insert(
                            0,
                            null,
                            50,
                            tx
                        )
                ),
                () -> assertThrows(
                    NullPointerException.class,
                    () -> fixture.handler()
                        .extract(
                            0,
                            null,
                            50,
                            tx
                        )
                ),
                () -> assertThrows(
                    NullPointerException.class,
                    () -> fixture.handler()
                        .insert(
                            0,
                            lava(),
                            50,
                            null
                        )
                ),
                () -> assertThrows(
                    NullPointerException.class,
                    () -> fixture.handler()
                        .extract(
                            0,
                            lava(),
                            50,
                            null
                        )
                )
            );
        }

        assertEquals(
            0,
            fixture.notifications()
                .get()
        );
    }

    @Test
    void shouldRejectInvalidExternalIndicesBeforeTransfer() {
        Fixture fixture = fixture(FluidPortMode.DISABLED);

        try (Transaction tx = Transaction.openRoot()) {
            for (int index : new int[]{-1, 2, Integer.MAX_VALUE}) {
                assertAll(
                    "Invalid external index: " + index,
                    () -> assertThrows(
                        IndexOutOfBoundsException.class,
                        () -> fixture.handler()
                            .insert(
                                index,
                                lava(),
                                100,
                                tx
                            )
                    ),
                    () -> assertThrows(
                        IndexOutOfBoundsException.class,
                        () -> fixture.handler()
                            .extract(
                                index,
                                lava(),
                                100,
                                tx
                            )
                    )
                );
            }
        }

        assertEquals(
            0,
            fixture.notifications()
                .get()
        );
    }

    @Test
    void shouldReturnZeroForZeroAmountWithoutNotification() {
        Fixture fixture = fixture(FluidPortMode.INPUT);

        try (Transaction tx = Transaction.openRoot()) {
            assertAll(
                () -> assertEquals(
                    0,
                    fixture.handler()
                        .insert(
                            0,
                            lava(),
                            0,
                            tx
                        )
                ),
                () -> assertEquals(
                    0,
                    fixture.handler()
                        .extract(
                            0,
                            lava(),
                            0,
                            tx
                        )
                )
            );
            tx.commit();
        }

        assertAll(
            () -> assertEquals(
                0L,
                fixture.storage()
                    .getAmountAsLong(0)
            ),
            () -> assertEquals(
                0,
                fixture.notifications()
                    .get()
            )
        );
    }

    @Test
    void shouldNeverNotifyOnDeniedOperations() {
        Fixture fixture = fixture(FluidPortMode.DISABLED);

        assertEquals(
            0,
            insertCommitted(
                fixture.handler(),
                0,
                lava(),
                300
            )
        );
        assertEquals(
            0,
            extractCommitted(
                fixture.handler(),
                0,
                lava(),
                300
            )
        );

        assertAll(
            () -> assertEquals(
                0L,
                fixture.storage()
                    .getAmountAsLong(0)
            ),
            () -> assertEquals(
                0,
                fixture.notifications()
                    .get()
            )
        );
    }
}
