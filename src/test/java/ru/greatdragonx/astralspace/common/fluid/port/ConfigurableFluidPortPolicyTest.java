package ru.greatdragonx.astralspace.common.fluid.port;

import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class ConfigurableFluidPortPolicyTest {

    private static final FluidPortId PORT = new FluidPortId("main");
    private static final FluidPortId UNKNOWN = new FluidPortId("unknown");

    private static final Set<FluidPortMode> ALL_MODES = Set.of(
        FluidPortMode.INPUT,
        FluidPortMode.OUTPUT,
        FluidPortMode.DISABLED,
        FluidPortMode.SERVICE
    );

    private @NonNull ConfigurableFluidPortPolicy createPolicy(
        @NonNull Map<FluidPortId, Set<FluidPortMode>> allowed,
        @NonNull Map<FluidPortId, FluidPortMode> current
    )
    {
        return new ConfigurableFluidPortPolicy(
            allowed,
            current::get,
            id -> true
        );
    }

    @Test
    void shouldRejectNullConstructorArguments() {
        assertAll(
            () -> assertThrows(
                NullPointerException.class,
                () -> new ConfigurableFluidPortPolicy(
                    null,
                    id -> FluidPortMode.INPUT,
                    id -> true
                )
            ),
            () -> assertThrows(
                NullPointerException.class,
                () -> new ConfigurableFluidPortPolicy(
                    Map.of(
                        PORT,
                        ALL_MODES
                    ),
                    null,
                    id -> true
                )
            ),
            () -> assertThrows(
                NullPointerException.class,
                () -> new ConfigurableFluidPortPolicy(
                    Map.of(
                        PORT,
                        ALL_MODES
                    ),
                    id -> FluidPortMode.INPUT,
                    null
                )
            )
        );
    }

    @Test
    void shouldRejectNullPortIdInAllowedConfiguration() {
        Map<FluidPortId, Set<FluidPortMode>> allowed = new HashMap<>();
        allowed.put(
            null,
            ALL_MODES
        );

        assertThrows(
            NullPointerException.class,
            () -> createPolicy(
                allowed,
                Map.of()
            )
        );
    }

    @Test
    void shouldRejectNullAllowedModesSet() {
        Map<FluidPortId, Set<FluidPortMode>> allowed = new HashMap<>();
        allowed.put(
            PORT,
            null
        );

        assertThrows(
            NullPointerException.class,
            () -> createPolicy(
                allowed,
                Map.of()
            )
        );
    }

    @Test
    void shouldRejectNullModeInsideAllowedSet() {
        Set<FluidPortMode> modes = new HashSet<>();
        modes.add(FluidPortMode.INPUT);
        modes.add(null);

        assertThrows(
            NullPointerException.class,
            () -> createPolicy(
                Map.of(
                    PORT,
                    modes
                ),
                Map.of()
            )
        );
    }

    @Test
    void shouldRejectEmptyAllowedModesSet() {
        assertThrows(
            IllegalArgumentException.class,
            () -> createPolicy(
                Map.of(
                    PORT,
                    Set.of()
                ),
                Map.of()
            )
        );
    }

    @Test
    void shouldSupportEmptyPortConfiguration() {
        var policy = createPolicy(
            Map.of(),
            Map.of()
        );

        assertAll(
            () -> assertEquals(
                FluidPortMode.DISABLED,
                policy.getMode(PORT)
            ),
            () -> assertFalse(policy.isAvailable(PORT)),
            () -> assertFalse(policy.allowsInsert(PORT)),
            () -> assertFalse(policy.allowsExtract(PORT)),
            () -> assertFalse(policy.canChangeMode(
                PORT,
                FluidPortMode.INPUT
            ))
        );
    }

    @Test
    void shouldNotCallProvidersDuringConstruction() {
        AtomicInteger modeCalls = new AtomicInteger();
        AtomicInteger availabilityCalls = new AtomicInteger();

        new ConfigurableFluidPortPolicy(
            Map.of(
                PORT,
                ALL_MODES
            ),
            id -> {
                modeCalls.incrementAndGet();
                return FluidPortMode.INPUT;
            },
            id -> {
                availabilityCalls.incrementAndGet();
                return true;
            }
        );

        assertAll(
            () -> assertEquals(
                0,
                modeCalls.get()
            ),
            () -> assertEquals(
                0,
                availabilityCalls.get()
            )
        );
    }

    @Test
    void shouldAllowInsertionOnlyInInputMode() {
        var policy = createPolicy(
            Map.of(
                PORT,
                ALL_MODES
            ),
            Map.of(
                PORT,
                FluidPortMode.INPUT
            )
        );

        assertAll(
            () -> assertEquals(
                FluidPortMode.INPUT,
                policy.getMode(PORT)
            ),
            () -> assertTrue(policy.allowsInsert(PORT)),
            () -> assertFalse(policy.allowsExtract(PORT))
        );
    }

    @Test
    void shouldAllowExtractionInOutputMode() {
        var policy = createPolicy(
            Map.of(
                PORT,
                ALL_MODES
            ),
            Map.of(
                PORT,
                FluidPortMode.OUTPUT
            )
        );

        assertAll(
            () -> assertTrue(policy.allowsExtract(PORT)),
            () -> assertFalse(policy.allowsInsert(PORT))
        );
    }

    @Test
    void shouldAllowExtractionInServiceMode() {
        var policy = createPolicy(
            Map.of(
                PORT,
                ALL_MODES
            ),
            Map.of(
                PORT,
                FluidPortMode.SERVICE
            )
        );

        assertAll(
            () -> assertEquals(
                FluidPortMode.SERVICE,
                policy.getMode(PORT)
            ),
            () -> assertTrue(policy.allowsExtract(PORT)),
            () -> assertFalse(policy.allowsInsert(PORT))
        );
    }

    @Test
    void shouldDenyBothOperationsInDisabledModeWhilePortRemainsAvailable() {
        var policy = createPolicy(
            Map.of(
                PORT,
                ALL_MODES
            ),
            Map.of(
                PORT,
                FluidPortMode.DISABLED
            )
        );

        assertAll(
            () -> assertEquals(
                FluidPortMode.DISABLED,
                policy.getMode(PORT)
            ),
            () -> assertTrue(policy.isAvailable(PORT)),
            () -> assertFalse(policy.allowsInsert(PORT)),
            () -> assertFalse(policy.allowsExtract(PORT))
        );
    }

    @Test
    void shouldObserveModeChangesWithoutRecreation() {
        Map<FluidPortId, FluidPortMode> current = new HashMap<>();
        current.put(
            PORT,
            FluidPortMode.INPUT
        );

        var policy = createPolicy(
            Map.of(
                PORT,
                ALL_MODES
            ),
            current
        );
        assertTrue(policy.allowsInsert(PORT));

        current.put(
            PORT,
            FluidPortMode.SERVICE
        );
        assertAll(
            () -> assertFalse(policy.allowsInsert(PORT)),
            () -> assertTrue(policy.allowsExtract(PORT)),
            () -> assertEquals(
                FluidPortMode.SERVICE,
                policy.getMode(PORT)
            )
        );

        current.put(
            PORT,
            FluidPortMode.DISABLED
        );
        assertAll(
            () -> assertFalse(policy.allowsInsert(PORT)),
            () -> assertFalse(policy.allowsExtract(PORT))
        );
    }

    @Test
    void shouldDenyModesNotPermittedForThisPort() {
        var policy = createPolicy(
            Map.of(
                PORT,
                Set.of(
                    FluidPortMode.INPUT,
                    FluidPortMode.DISABLED
                )
            ),
            Map.of(
                PORT,
                FluidPortMode.INPUT
            )
        );

        assertAll(
            () -> assertTrue(policy.canChangeMode(
                PORT,
                FluidPortMode.INPUT
            )),
            () -> assertTrue(policy.canChangeMode(
                PORT,
                FluidPortMode.DISABLED
            )),
            () -> assertFalse(policy.canChangeMode(
                PORT,
                FluidPortMode.OUTPUT
            )),
            () -> assertFalse(policy.canChangeMode(
                PORT,
                FluidPortMode.SERVICE
            ))
        );
    }

    @Test
    void shouldNotCallProvidersWhenCheckingAllowedModeChange() {
        AtomicInteger modeCalls = new AtomicInteger();
        AtomicInteger availabilityCalls = new AtomicInteger();

        var policy = new ConfigurableFluidPortPolicy(
            Map.of(
                PORT,
                ALL_MODES
            ),
            id -> {
                modeCalls.incrementAndGet();
                return FluidPortMode.INPUT;
            },
            id -> {
                availabilityCalls.incrementAndGet();
                return false;
            }
        );

        assertTrue(policy.canChangeMode(
            PORT,
            FluidPortMode.SERVICE
        ));
        assertAll(
            () -> assertEquals(
                0,
                modeCalls.get()
            ),
            () -> assertEquals(
                0,
                availabilityCalls.get()
            )
        );
    }

    @Test
    void shouldDenyUnknownPortWithoutCallingProviders() {
        AtomicInteger modeCalls = new AtomicInteger();
        AtomicInteger availabilityCalls = new AtomicInteger();

        var policy = new ConfigurableFluidPortPolicy(
            Map.of(
                PORT,
                ALL_MODES
            ),
            id -> {
                modeCalls.incrementAndGet();
                return FluidPortMode.OUTPUT;
            },
            id -> {
                availabilityCalls.incrementAndGet();
                return true;
            }
        );

        assertAll(
            () -> assertEquals(
                FluidPortMode.DISABLED,
                policy.getMode(UNKNOWN)
            ),
            () -> assertFalse(policy.isAvailable(UNKNOWN)),
            () -> assertFalse(policy.allowsInsert(UNKNOWN)),
            () -> assertFalse(policy.allowsExtract(UNKNOWN)),
            () -> assertFalse(policy.canChangeMode(
                UNKNOWN,
                FluidPortMode.INPUT
            ))
        );

        assertAll(
            () -> assertEquals(
                0,
                modeCalls.get()
            ),
            () -> assertEquals(
                0,
                availabilityCalls.get()
            )
        );
    }

    @Test
    void shouldRespectDynamicAvailabilityWithoutLosingCurrentMode() {
        AtomicBoolean available = new AtomicBoolean(false);

        var policy = new ConfigurableFluidPortPolicy(
            Map.of(
                PORT,
                ALL_MODES
            ),
            id -> FluidPortMode.OUTPUT,
            id -> available.get()
        );

        assertAll(
            () -> assertFalse(policy.isAvailable(PORT)),
            () -> assertEquals(
                FluidPortMode.OUTPUT,
                policy.getMode(PORT)
            ),
            () -> assertFalse(policy.allowsExtract(PORT)),
            () -> assertTrue(policy.canChangeMode(
                PORT,
                FluidPortMode.SERVICE
            ))
        );

        available.set(true);
        assertAll(
            () -> assertTrue(policy.isAvailable(PORT)),
            () -> assertTrue(policy.allowsExtract(PORT))
        );
    }

    @Test
    void shouldDisableForbiddenOrMissingCurrentMode() {
        Map<FluidPortId, FluidPortMode> current = new HashMap<>();
        current.put(
            PORT,
            FluidPortMode.SERVICE
        );

        var policy = createPolicy(
            Map.of(
                PORT,
                Set.of(
                    FluidPortMode.INPUT,
                    FluidPortMode.DISABLED
                )
            ),
            current
        );

        assertEquals(
            FluidPortMode.DISABLED,
            policy.getMode(PORT)
        );

        current.remove(PORT);

        assertAll(
            () -> assertEquals(
                FluidPortMode.DISABLED,
                policy.getMode(PORT)
            ),
            () -> assertFalse(policy.allowsInsert(PORT)),
            () -> assertFalse(policy.allowsExtract(PORT))
        );
    }

    @Test
    void shouldDefensivelyCopyAllowedMapAndNestedSets() {
        Set<FluidPortMode> originalSet = new HashSet<>(Set.of(
            FluidPortMode.INPUT,
            FluidPortMode.DISABLED
        ));
        Map<FluidPortId, Set<FluidPortMode>> originalMap = new HashMap<>();
        originalMap.put(
            PORT,
            originalSet
        );

        var policy = createPolicy(
            originalMap,
            Map.of(
                PORT,
                FluidPortMode.INPUT
            )
        );

        originalSet.clear();
        originalSet.add(FluidPortMode.OUTPUT);
        originalSet.add(FluidPortMode.SERVICE);
        originalMap.clear();

        assertAll(
            () -> assertEquals(
                FluidPortMode.INPUT,
                policy.getMode(PORT)
            ),
            () -> assertTrue(policy.canChangeMode(
                PORT,
                FluidPortMode.INPUT
            )),
            () -> assertTrue(policy.canChangeMode(
                PORT,
                FluidPortMode.DISABLED
            )),
            () -> assertFalse(policy.canChangeMode(
                PORT,
                FluidPortMode.OUTPUT
            )),
            () -> assertFalse(policy.canChangeMode(
                PORT,
                FluidPortMode.SERVICE
            ))
        );
    }

    @Test
    void shouldRejectNullPortAndModeArguments() {
        var policy = createPolicy(
            Map.of(
                PORT,
                ALL_MODES
            ),
            Map.of(
                PORT,
                FluidPortMode.INPUT
            )
        );

        assertAll(
            () -> assertThrows(
                NullPointerException.class,
                () -> policy.getMode(null)
            ),
            () -> assertThrows(
                NullPointerException.class,
                () -> policy.isAvailable(null)
            ),
            () -> assertThrows(
                NullPointerException.class,
                () -> policy.canChangeMode(
                    null,
                    FluidPortMode.INPUT
                )
            ),
            () -> assertThrows(
                NullPointerException.class,
                () -> policy.canChangeMode(
                    PORT,
                    null
                )
            ),
            () -> assertThrows(
                NullPointerException.class,
                () -> policy.allowsInsert(null)
            ),
            () -> assertThrows(
                NullPointerException.class,
                () -> policy.allowsExtract(null)
            )
        );
    }
}
