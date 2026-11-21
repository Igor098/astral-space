package ru.greatdragonx.astralspace.common.fluid.port;

import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.Test;
import ru.greatdragonx.astralspace.common.fluid.storage.FluidTankDefinition;
import ru.greatdragonx.astralspace.common.fluid.storage.FluidTankId;
import ru.greatdragonx.astralspace.common.fluid.storage.FluidTankRole;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class FluidPortRoutingTest {
    private static final FluidPortId LEFT = new FluidPortId("left");
    private static final FluidPortId RIGHT = new FluidPortId("right");
    private static final FluidPortId MISSING = new FluidPortId("missing");

    private static final List<FluidTankDefinition> TANK_DEFINITIONS = List.of(
        tank(
            "lava_input",
            FluidTankRole.INPUT
        ),
        tank(
            "water_input",
            FluidTankRole.INPUT
        )
    );

    private static FluidTankDefinition tank(
        String id,
        FluidTankRole role
    )
    {
        return new FluidTankDefinition(
            new FluidTankId(id),
            role,
            8_000,
            resource -> true
        );
    }

    private static FluidPortRouting routing(Map<FluidPortId, FluidPortRoute> routes) {
        return new FluidPortRouting(
            routes,
            TANK_DEFINITIONS
        );
    }

    private static @NonNull FluidPortRoute route(String tankId) {
        return new FluidPortRoute(
            List.of(new FluidTankId(tankId)),
            List.of(),
            List.of()
        );
    }

    @Test
    void shouldFindExistingRoute() {
        FluidPortRoute leftRoute = route("lava_input");
        FluidPortRouting routing = routing(Map.of(
            LEFT,
            leftRoute
        ));

        assertEquals(
            Optional.of(leftRoute),
            routing.findRoute(LEFT)
        );
    }

    @Test
    void shouldFindRouteByEqualButDifferentPortIdInstance() {
        FluidPortRoute leftRoute = route("lava_input");
        FluidPortRouting routing = routing(Map.of(
            LEFT,
            leftRoute
        ));
        FluidPortId otherInstance = new FluidPortId("left");

        assertAll(
            () -> assertNotSame(
                LEFT,
                otherInstance
            ),
            () -> assertEquals(
                Optional.of(leftRoute),
                routing.findRoute(otherInstance)
            ),
            () -> assertTrue(routing.hasRoute(otherInstance))
        );
    }

    @Test
    void shouldReturnEmptyForMissingRoute() {
        FluidPortRouting routing = routing(Map.of(
            LEFT,
            route("lava_input")
        ));

        assertEquals(
            Optional.empty(),
            routing.findRoute(MISSING)
        );
    }

    @Test
    void shouldReportExistingRoutes() {
        FluidPortRouting routing = routing(Map.of(
            LEFT,
            route("lava_input"),
            RIGHT,
            route("water_input")
        ));

        assertAll(
            () -> assertTrue(routing.hasRoute(LEFT)),
            () -> assertTrue(routing.hasRoute(RIGHT))
        );
    }

    @Test
    void shouldReportMissingRoute() {
        FluidPortRouting routing = routing(Map.of(
            LEFT,
            route("lava_input")
        ));

        assertFalse(routing.hasRoute(MISSING));
    }

    @Test
    void shouldRejectNullLookupId() {
        FluidPortRouting routing = routing(Map.of());

        assertAll(
            () -> assertThrows(
                NullPointerException.class,
                () -> routing.findRoute(null)
            ),
            () -> assertThrows(
                NullPointerException.class,
                () -> routing.hasRoute(null)
            )
        );
    }

    @Test
    void shouldAcceptEmptyMap() {
        FluidPortRouting routing = routing(Map.of());

        assertAll(
            () -> assertEquals(
                Optional.empty(),
                routing.findRoute(LEFT)
            ),
            () -> assertFalse(routing.hasRoute(LEFT))
        );
    }

    @Test
    void shouldRejectNullMap() {
        assertThrows(
            NullPointerException.class,
            () -> routing(null)
        );
    }

    @Test
    void shouldRejectNullKey() {
        Map<FluidPortId, FluidPortRoute> source = new HashMap<>();
        source.put(
            null,
            route("lava_input")
        );

        assertThrows(
            NullPointerException.class,
            () -> routing(source)
        );
    }

    @Test
    void shouldRejectNullValue() {
        Map<FluidPortId, FluidPortRoute> source = new HashMap<>();
        source.put(
            LEFT,
            null
        );

        assertThrows(
            NullPointerException.class,
            () -> routing(source)
        );
    }

    @Test
    void shouldDefensivelyCopyMap() {
        FluidPortRoute original = route("lava_input");
        FluidPortRoute replacement = route("water_input");
        Map<FluidPortId, FluidPortRoute> source = new HashMap<>();
        source.put(
            LEFT,
            original
        );

        FluidPortRouting routing = routing(source);

        source.put(
            LEFT,
            replacement
        );
        source.put(
            RIGHT,
            replacement
        );
        source.remove(LEFT);

        assertAll(
            () -> assertEquals(
                Optional.of(original),
                routing.findRoute(LEFT)
            ),
            () -> assertTrue(routing.hasRoute(LEFT)),
            () -> assertFalse(routing.hasRoute(RIGHT))
        );
    }

    @Test
    void shouldKeepRoutesOfDifferentPortsIndependent() {
        FluidPortRoute leftRoute = route("lava_input");
        FluidPortRoute rightRoute = route("water_input");
        FluidPortRouting routing = routing(Map.of(
            LEFT,
            leftRoute,
            RIGHT,
            rightRoute
        ));

        assertAll(
            () -> assertEquals(
                Optional.of(leftRoute),
                routing.findRoute(LEFT)
            ),
            () -> assertEquals(
                Optional.of(rightRoute),
                routing.findRoute(RIGHT)
            ),
            () -> assertNotEquals(
                routing.findRoute(LEFT),
                routing.findRoute(RIGHT)
            )
        );
    }

    @Test
    void shouldDistinguishEmptyRouteFromMissingRoute() {
        FluidPortRoute empty = new FluidPortRoute(
            List.of(),
            List.of(),
            List.of()
        );
        FluidPortRouting routing = routing(Map.of(
            LEFT,
            empty
        ));

        assertAll(
            () -> assertEquals(
                Optional.of(empty),
                routing.findRoute(LEFT)
            ),
            () -> assertTrue(routing.hasRoute(LEFT)),
            () -> assertEquals(
                Optional.empty(),
                routing.findRoute(MISSING)
            ),
            () -> assertFalse(routing.hasRoute(MISSING))
        );
    }

    @Test
    void shouldRejectNullTankDefinitions() {
        assertThrows(
            NullPointerException.class,
            () -> new FluidPortRouting(
                Map.of(),
                null
            )
        );
    }

    @Test
    void shouldRejectRouteReferencingUnknownTank() {
        assertThrows(
            IllegalArgumentException.class,
            () -> routing(Map.of(
                LEFT,
                route("unknown_input")
            ))
        );
    }

    @Test
    void shouldRejectInputRoutePointingToOutputTank() {
        FluidPortRoute invalidRoute = new FluidPortRoute(
            List.of(new FluidTankId("product_output")),
            List.of(),
            List.of()
        );

        assertThrows(
            IllegalArgumentException.class,
            () -> new FluidPortRouting(
                Map.of(
                    LEFT,
                    invalidRoute
                ),
                List.of(tank(
                    "product_output",
                    FluidTankRole.OUTPUT
                ))
            )
        );
    }

    @Test
    void shouldRejectOutputRoutePointingToInputTank() {
        FluidPortRoute invalidRoute = new FluidPortRoute(
            List.of(),
            List.of(new FluidTankId("lava_input")),
            List.of()
        );

        assertThrows(
            IllegalArgumentException.class,
            () -> routing(Map.of(
                LEFT,
                invalidRoute
            ))
        );
    }

    @Test
    void shouldRejectServiceRoutePointingToOutputTank() {
        FluidPortRoute invalidRoute = new FluidPortRoute(
            List.of(),
            List.of(),
            List.of(new FluidTankId("product_output"))
        );

        assertThrows(
            IllegalArgumentException.class,
            () -> new FluidPortRouting(
                Map.of(
                    LEFT,
                    invalidRoute
                ),
                List.of(tank(
                    "product_output",
                    FluidTankRole.OUTPUT
                ))
            )
        );
    }

    @Test
    void shouldRejectDuplicateTankDefinitionIds() {
        List<FluidTankDefinition> duplicates = List.of(
            tank(
                "lava_input",
                FluidTankRole.INPUT
            ),
            tank(
                "lava_input",
                FluidTankRole.INPUT
            )
        );

        assertThrows(
            IllegalArgumentException.class,
            () -> new FluidPortRouting(
                Map.of(
                    LEFT,
                    route("lava_input")
                ),
                duplicates
            )
        );
    }

    @Test
    void shouldAcceptValidInputOutputAndServiceRoutes() {
        FluidPortRoute validRoute = new FluidPortRoute(
            List.of(new FluidTankId("lava_input")),
            List.of(new FluidTankId("product_output")),
            List.of(new FluidTankId("lava_input"))
        );
        FluidPortRouting routing = new FluidPortRouting(
            Map.of(
                LEFT,
                validRoute
            ),
            List.of(
                tank(
                    "lava_input",
                    FluidTankRole.INPUT
                ),
                tank(
                    "product_output",
                    FluidTankRole.OUTPUT
                )
            )
        );

        assertAll(
            () -> assertTrue(routing.hasRoute(LEFT)),
            () -> assertEquals(
                Optional.of(validRoute),
                routing.findRoute(LEFT)
            )
        );
    }

}
