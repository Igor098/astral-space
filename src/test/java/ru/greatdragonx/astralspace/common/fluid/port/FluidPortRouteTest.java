package ru.greatdragonx.astralspace.common.fluid.port;

import org.junit.jupiter.api.Test;
import ru.greatdragonx.astralspace.common.fluid.storage.FluidTankId;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FluidPortRouteTest {
    private static final FluidTankId LAVA = new FluidTankId("lava_input");
    private static final FluidTankId WATER = new FluidTankId("water_input");
    private static final FluidTankId PRODUCT = new FluidTankId("product_output");

    @Test
    void shouldStoreAllThreeTankLists() {
        FluidPortRoute route = new FluidPortRoute(
            List.of(LAVA),
            List.of(PRODUCT),
            List.of(WATER)
        );

        assertAll(
            () -> assertEquals(
                List.of(LAVA),
                route.inputTanks()
            ),
            () -> assertEquals(
                List.of(PRODUCT),
                route.outputTanks()
            ),
            () -> assertEquals(
                List.of(WATER),
                route.serviceTanks()
            )
        );
    }

    @Test
    void shouldAcceptThreeEmptyLists() {
        FluidPortRoute route = new FluidPortRoute(
            List.of(),
            List.of(),
            List.of()
        );

        assertAll(
            () -> assertTrue(route.inputTanks()
                .isEmpty()),
            () -> assertTrue(route.outputTanks()
                .isEmpty()),
            () -> assertTrue(route.serviceTanks()
                .isEmpty())
        );
    }

    @Test
    void shouldAcceptEmptyListsIndependently() {
        FluidPortRoute route = new FluidPortRoute(
            List.of(LAVA),
            List.of(),
            List.of()
        );

        assertAll(
            () -> assertEquals(
                List.of(LAVA),
                route.inputTanks()
            ),
            () -> assertTrue(route.outputTanks()
                .isEmpty()),
            () -> assertTrue(route.serviceTanks()
                .isEmpty())
        );
    }

    @Test
    void shouldPreserveTankOrderInEveryList() {
        FluidPortRoute route = new FluidPortRoute(
            List.of(
                LAVA,
                WATER,
                PRODUCT
            ),
            List.of(
                PRODUCT,
                WATER,
                LAVA
            ),
            List.of(
                WATER,
                LAVA
            )
        );

        assertAll(
            () -> assertEquals(
                List.of(
                    LAVA,
                    WATER,
                    PRODUCT
                ),
                route.inputTanks()
            ),
            () -> assertEquals(
                List.of(
                    PRODUCT,
                    WATER,
                    LAVA
                ),
                route.outputTanks()
            ),
            () -> assertEquals(
                List.of(
                    WATER,
                    LAVA
                ),
                route.serviceTanks()
            )
        );
    }

    @Test
    void shouldDefensivelyCopyAllLists() {
        List<FluidTankId> inputs = new ArrayList<>(List.of(LAVA));
        List<FluidTankId> outputs = new ArrayList<>(List.of(PRODUCT));
        List<FluidTankId> services = new ArrayList<>(List.of(WATER));

        FluidPortRoute route = new FluidPortRoute(
            inputs,
            outputs,
            services
        );

        inputs.add(WATER);
        outputs.clear();
        services.set(
            0,
            PRODUCT
        );

        assertAll(
            () -> assertEquals(
                List.of(LAVA),
                route.inputTanks()
            ),
            () -> assertEquals(
                List.of(PRODUCT),
                route.outputTanks()
            ),
            () -> assertEquals(
                List.of(WATER),
                route.serviceTanks()
            )
        );
    }

    @Test
    void shouldExposeUnmodifiableLists() {
        FluidPortRoute route = new FluidPortRoute(
            List.of(LAVA),
            List.of(PRODUCT),
            List.of(WATER)
        );

        assertAll(
            () -> assertThrows(
                UnsupportedOperationException.class,
                () -> route.inputTanks()
                    .add(WATER)
            ),
            () -> assertThrows(
                UnsupportedOperationException.class,
                () -> route.outputTanks()
                    .clear()
            ),
            () -> assertThrows(
                UnsupportedOperationException.class,
                () -> route.serviceTanks()
                    .set(
                        0,
                        LAVA
                    )
            )
        );
    }

    @Test
    void shouldRejectNullInputList() {
        assertThrows(
            NullPointerException.class,
            () -> new FluidPortRoute(
                null,
                List.of(),
                List.of()
            )
        );
    }

    @Test
    void shouldRejectNullOutputList() {
        assertThrows(
            NullPointerException.class,
            () -> new FluidPortRoute(
                List.of(),
                null,
                List.of()
            )
        );
    }

    @Test
    void shouldRejectNullServiceList() {
        assertThrows(
            NullPointerException.class,
            () -> new FluidPortRoute(
                List.of(),
                List.of(),
                null
            )
        );
    }

    @Test
    void shouldRejectNullElementInInputList() {
        List<FluidTankId> inputs = new ArrayList<>();
        inputs.add(null);

        assertThrows(
            NullPointerException.class,
            () -> new FluidPortRoute(
                inputs,
                List.of(),
                List.of()
            )
        );
    }

    @Test
    void shouldRejectNullElementInOutputList() {
        List<FluidTankId> outputs = new ArrayList<>();
        outputs.add(null);

        assertThrows(
            NullPointerException.class,
            () -> new FluidPortRoute(
                List.of(),
                outputs,
                List.of()
            )
        );
    }

    @Test
    void shouldRejectNullElementInServiceList() {
        List<FluidTankId> services = new ArrayList<>();
        services.add(null);

        assertThrows(
            NullPointerException.class,
            () -> new FluidPortRoute(
                List.of(),
                List.of(),
                services
            )
        );
    }

    @Test
    void shouldAllowOneTankInInputAndServiceLists() {
        FluidPortRoute route = new FluidPortRoute(
            List.of(LAVA),
            List.of(),
            List.of(new FluidTankId("lava_input"))
        );

        assertEquals(
            route.inputTanks(),
            route.serviceTanks()
        );
    }

    @Test
    void shouldCompareRoutesByListContents() {
        FluidPortRoute first = new FluidPortRoute(
            List.of(LAVA),
            List.of(PRODUCT),
            List.of()
        );
        FluidPortRoute second = new FluidPortRoute(
            List.of(new FluidTankId("lava_input")),
            List.of(new FluidTankId("product_output")),
            List.of()
        );

        assertAll(
            () -> assertEquals(
                first,
                second
            ),
            () -> assertEquals(
                first.hashCode(),
                second.hashCode()
            )
        );
    }
}
