package ru.greatdragonx.astralspace.common.fluid.port;

import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.Test;
import ru.greatdragonx.astralspace.common.fluid.storage.FluidTankDefinition;
import ru.greatdragonx.astralspace.common.fluid.storage.FluidTankId;
import ru.greatdragonx.astralspace.common.fluid.storage.FluidTankRole;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class FluidPortRoutingValidatorTest {
    private static final FluidPortId LEFT = new FluidPortId("left");
    private static final FluidPortId RIGHT = new FluidPortId("right");

    private static @NonNull FluidTankDefinition tank(
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

    private static @NonNull FluidPortRoute route(
        List<FluidTankId> input,
        List<FluidTankId> output,
        List<FluidTankId> service
    )
    {
        return new FluidPortRoute(
            input,
            output,
            service
        );
    }

    private static @NonNull FluidPortRoute input(String id) {
        return route(
            List.of(new FluidTankId(id)),
            List.of(),
            List.of()
        );
    }

    private static @NonNull FluidPortRoute output(String id) {
        return route(
            List.of(),
            List.of(new FluidTankId(id)),
            List.of()
        );
    }

    private static @NonNull FluidPortRoute service(String id) {
        return route(
            List.of(),
            List.of(),
            List.of(new FluidTankId(id))
        );
    }

    @Test
    void shouldAcceptEmptyRoutesAndEmptyDefinitions() {
        assertDoesNotThrow(() -> FluidPortRoutingValidator.validate(
            Map.of(),
            List.of()
        ));
    }

    @Test
    void shouldAcceptNoRoutesWithDefinedTanks() {
        assertDoesNotThrow(() -> FluidPortRoutingValidator.validate(
            Map.of(),
            List.of(tank(
                "lava_input",
                FluidTankRole.INPUT
            ))
        ));
    }

    @Test
    void shouldAcceptRouteWithoutAnyTankReferences() {
        assertDoesNotThrow(() -> FluidPortRoutingValidator.validate(
            Map.of(
                LEFT,
                route(
                    List.of(),
                    List.of(),
                    List.of()
                )
            ),
            List.of()
        ));
    }

    @Test
    void shouldAllowInputRouteToInputTank() {
        assertDoesNotThrow(() -> FluidPortRoutingValidator.validate(
            Map.of(
                LEFT,
                input("lava_input")
            ),
            List.of(tank(
                "lava_input",
                FluidTankRole.INPUT
            ))
        ));
    }

    @Test
    void shouldAllowInputRouteToBidirectionalTank() {
        assertDoesNotThrow(() -> FluidPortRoutingValidator.validate(
            Map.of(
                LEFT,
                input("universal")
            ),
            List.of(tank(
                "universal",
                FluidTankRole.BIDIRECTIONAL
            ))
        ));
    }

    @Test
    void shouldAllowOutputRouteToOutputTank() {
        assertDoesNotThrow(() -> FluidPortRoutingValidator.validate(
            Map.of(
                LEFT,
                output("product")
            ),
            List.of(tank(
                "product",
                FluidTankRole.OUTPUT
            ))
        ));
    }

    @Test
    void shouldAllowOutputRouteToBidirectionalTank() {
        assertDoesNotThrow(() -> FluidPortRoutingValidator.validate(
            Map.of(
                LEFT,
                output("universal")
            ),
            List.of(tank(
                "universal",
                FluidTankRole.BIDIRECTIONAL
            ))
        ));
    }

    @Test
    void shouldAllowServiceRouteToInputTank() {
        assertDoesNotThrow(() -> FluidPortRoutingValidator.validate(
            Map.of(
                LEFT,
                service("lava_input")
            ),
            List.of(tank(
                "lava_input",
                FluidTankRole.INPUT
            ))
        ));
    }

    @Test
    void shouldAllowSameInputTankForInputAndServiceModes() {
        FluidTankId lava = new FluidTankId("lava_input");
        FluidPortRoute route = route(
            List.of(lava),
            List.of(),
            List.of(new FluidTankId("lava_input"))
        );

        assertDoesNotThrow(() -> FluidPortRoutingValidator.validate(
            Map.of(
                LEFT,
                route
            ),
            List.of(tank(
                "lava_input",
                FluidTankRole.INPUT
            ))
        ));
    }

    @Test
    void shouldAllowDifferentTanksForDifferentModesOfSamePort() {
        FluidPortRoute route = route(
            List.of(new FluidTankId("lava_input")),
            List.of(new FluidTankId("product_output")),
            List.of(new FluidTankId("lava_input"))
        );

        assertDoesNotThrow(() -> FluidPortRoutingValidator.validate(
            Map.of(
                LEFT,
                route
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
        ));
    }

    @Test
    void shouldAllowMultiplePortsToReferenceSameTank() {
        assertDoesNotThrow(() -> FluidPortRoutingValidator.validate(
            Map.of(
                LEFT,
                input("lava_input"),
                RIGHT,
                input("lava_input")
            ),
            List.of(tank(
                "lava_input",
                FluidTankRole.INPUT
            ))
        ));
    }

    @Test
    void shouldAllowSeveralTanksInOneRoute() {
        FluidPortRoute route = route(
            List.of(
                new FluidTankId("lava_input"),
                new FluidTankId("water_input")
            ),
            List.of(),
            List.of()
        );

        assertDoesNotThrow(() -> FluidPortRoutingValidator.validate(
            Map.of(
                LEFT,
                route
            ),
            List.of(
                tank(
                    "lava_input",
                    FluidTankRole.INPUT
                ),
                tank(
                    "water_input",
                    FluidTankRole.BIDIRECTIONAL
                )
            )
        ));
    }

    @Test
    void shouldRejectInputRouteToOutputTank() {
        assertThrows(
            IllegalArgumentException.class,
            () -> FluidPortRoutingValidator.validate(
                Map.of(
                    LEFT,
                    input("product")
                ),
                List.of(tank(
                    "product",
                    FluidTankRole.OUTPUT
                ))
            )
        );
    }

    @Test
    void shouldRejectOutputRouteToInputTank() {
        assertThrows(
            IllegalArgumentException.class,
            () -> FluidPortRoutingValidator.validate(
                Map.of(
                    LEFT,
                    output("lava_input")
                ),
                List.of(tank(
                    "lava_input",
                    FluidTankRole.INPUT
                ))
            )
        );
    }

    @Test
    void shouldRejectServiceRouteToOutputTank() {
        assertThrows(
            IllegalArgumentException.class,
            () -> FluidPortRoutingValidator.validate(
                Map.of(
                    LEFT,
                    service("product")
                ),
                List.of(tank(
                    "product",
                    FluidTankRole.OUTPUT
                ))
            )
        );
    }

    @Test
    void shouldRejectServiceRouteToBidirectionalTank() {
        // Текущий договорённый контракт: SERVICE предназначен только для INPUT.
        assertThrows(
            IllegalArgumentException.class,
            () -> FluidPortRoutingValidator.validate(
                Map.of(
                    LEFT,
                    service("universal")
                ),
                List.of(tank(
                    "universal",
                    FluidTankRole.BIDIRECTIONAL
                ))
            )
        );
    }

    @Test
    void shouldRejectUnknownInputTank() {
        assertThrows(
            IllegalArgumentException.class,
            () -> FluidPortRoutingValidator.validate(
                Map.of(
                    LEFT,
                    input("missing")
                ),
                List.of(tank(
                    "lava_input",
                    FluidTankRole.INPUT
                ))
            )
        );
    }

    @Test
    void shouldRejectUnknownOutputTank() {
        assertThrows(
            IllegalArgumentException.class,
            () -> FluidPortRoutingValidator.validate(
                Map.of(
                    LEFT,
                    output("missing")
                ),
                List.of(tank(
                    "product",
                    FluidTankRole.OUTPUT
                ))
            )
        );
    }

    @Test
    void shouldRejectUnknownServiceTank() {
        assertThrows(
            IllegalArgumentException.class,
            () -> FluidPortRoutingValidator.validate(
                Map.of(
                    LEFT,
                    service("missing")
                ),
                List.of(tank(
                    "lava_input",
                    FluidTankRole.INPUT
                ))
            )
        );
    }

    @Test
    void shouldRejectUnknownTankWhenNoDefinitionsExist() {
        assertThrows(
            IllegalArgumentException.class,
            () -> FluidPortRoutingValidator.validate(
                Map.of(
                    LEFT,
                    input("lava_input")
                ),
                List.of()
            )
        );
    }

    @Test
    void shouldCheckEveryTankInTheList() {
        FluidPortRoute route = route(
            List.of(
                new FluidTankId("lava_input"),
                new FluidTankId("missing")
            ),
            List.of(),
            List.of()
        );

        assertThrows(
            IllegalArgumentException.class,
            () -> FluidPortRoutingValidator.validate(
                Map.of(
                    LEFT,
                    route
                ),
                List.of(tank(
                    "lava_input",
                    FluidTankRole.INPUT
                ))
            )
        );
    }

    @Test
    void shouldCheckAllPorts() {
        assertThrows(
            IllegalArgumentException.class,
            () -> FluidPortRoutingValidator.validate(
                Map.of(
                    LEFT,
                    input("lava_input"),
                    RIGHT,
                    output("missing")
                ),
                List.of(tank(
                    "lava_input",
                    FluidTankRole.INPUT
                ))
            )
        );
    }

    @Test
    void shouldCheckRoleOfEveryReferencedTank() {
        FluidPortRoute route = route(
            List.of(
                new FluidTankId("lava_input"),
                new FluidTankId("product")
            ),
            List.of(),
            List.of()
        );

        assertThrows(
            IllegalArgumentException.class,
            () -> FluidPortRoutingValidator.validate(
                Map.of(
                    LEFT,
                    route
                ),
                List.of(
                    tank(
                        "lava_input",
                        FluidTankRole.INPUT
                    ),
                    tank(
                        "product",
                        FluidTankRole.OUTPUT
                    )
                )
            )
        );
    }

    @Test
    void shouldRejectDuplicateTankIdsByValue() {
        assertThrows(
            IllegalArgumentException.class,
            () -> FluidPortRoutingValidator.validate(
                Map.of(),
                List.of(
                    tank(
                        "same_id",
                        FluidTankRole.INPUT
                    ),
                    tank(
                        "same_id",
                        FluidTankRole.OUTPUT
                    )
                )
            )
        );
    }

    @Test
    void shouldRejectRepeatedSameDefinition() {
        FluidTankDefinition definition = tank(
            "lava_input",
            FluidTankRole.INPUT
        );

        assertThrows(
            IllegalArgumentException.class,
            () -> FluidPortRoutingValidator.validate(
                Map.of(),
                List.of(
                    definition,
                    definition
                )
            )
        );
    }

    @Test
    void shouldRejectNullRoutesArgument() {
        assertThrows(
            NullPointerException.class,
            () -> FluidPortRoutingValidator.validate(
                null,
                List.of()
            )
        );
    }

    @Test
    void shouldRejectNullDefinitionsArgument() {
        assertThrows(
            NullPointerException.class,
            () -> FluidPortRoutingValidator.validate(
                Map.of(),
                null
            )
        );
    }

    @Test
    void shouldRejectNullDefinitionElement() {
        List<FluidTankDefinition> definitions = new ArrayList<>();
        definitions.add(null);

        assertThrows(
            NullPointerException.class,
            () -> FluidPortRoutingValidator.validate(
                Map.of(),
                definitions
            )
        );
    }

    @Test
    void shouldIncludePortAndTankInUnknownTankError() {
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> FluidPortRoutingValidator.validate(
                Map.of(
                    LEFT,
                    input("missing")
                ),
                List.of(tank(
                    "lava_input",
                    FluidTankRole.INPUT
                ))
            )
        );

        assertAll(
            () -> assertTrue(exception.getMessage()
                .contains("missing")),
            () -> assertTrue(exception.getMessage()
                .contains("left"))
        );
    }

    @Test
    void shouldIncludeTankPortAndRoleInRoleError() {
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> FluidPortRoutingValidator.validate(
                Map.of(
                    LEFT,
                    input("product")
                ),
                List.of(tank(
                    "product",
                    FluidTankRole.OUTPUT
                ))
            )
        );

        assertAll(
            () -> assertTrue(exception.getMessage()
                .contains("product")),
            () -> assertTrue(exception.getMessage()
                .contains("left")),
            () -> assertTrue(exception.getMessage()
                .contains("OUTPUT"))
        );
    }

    @Test
    void shouldIncludeDuplicateTankIdInError() {
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> FluidPortRoutingValidator.validate(
                Map.of(),
                List.of(
                    tank(
                        "duplicate",
                        FluidTankRole.INPUT
                    ),
                    tank(
                        "duplicate",
                        FluidTankRole.INPUT
                    )
                )
            )
        );

        assertTrue(exception.getMessage()
            .contains("duplicate"));
    }

    @Test
    void shouldNotModifyRoutesOrDefinitions() {
        FluidPortRoute route = input("lava_input");
        Map<FluidPortId, FluidPortRoute> routes = new HashMap<>(Map.of(
            LEFT,
            route
        ));
        List<FluidTankDefinition> definitions = new ArrayList<>(List.of(tank(
            "lava_input",
            FluidTankRole.INPUT
        )));
        Map<FluidPortId, FluidPortRoute> routesBefore = new HashMap<>(routes);
        List<FluidTankDefinition> definitionsBefore = new ArrayList<>(definitions);

        FluidPortRoutingValidator.validate(
            routes,
            definitions
        );

        assertAll(
            () -> assertEquals(
                routesBefore,
                routes
            ),
            () -> assertEquals(
                definitionsBefore,
                definitions
            )
        );
    }
}
