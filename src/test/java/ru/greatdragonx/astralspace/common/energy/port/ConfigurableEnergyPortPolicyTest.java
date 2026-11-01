package ru.greatdragonx.astralspace.common.energy.port;

import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class ConfigurableEnergyPortPolicyTest {

    private static final EnergyPortId PORT = new EnergyPortId("main");

    private static final EnergyPortId UNKNOWN = new EnergyPortId("unknown");

    private static final Set<EnergyPortMode> ALL_MODES = Set.of(
        EnergyPortMode.INPUT,
        EnergyPortMode.OUTPUT,
        EnergyPortMode.DISABLED
    );

    private @NonNull ConfigurableEnergyPortPolicy createPolicy(
        Map<EnergyPortId, Set<EnergyPortMode>> allowed,
        @NonNull Map<EnergyPortId, EnergyPortMode> current
    )
    {
        return new ConfigurableEnergyPortPolicy(
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
                () -> new ConfigurableEnergyPortPolicy(
                    null,
                    id -> EnergyPortMode.INPUT,
                    id -> true
                )
            ),

            () -> assertThrows(
                NullPointerException.class,
                () -> new ConfigurableEnergyPortPolicy(
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
                () -> new ConfigurableEnergyPortPolicy(
                    Map.of(
                        PORT,
                        ALL_MODES
                    ),
                    id -> EnergyPortMode.INPUT,
                    null
                )
            )
        );
    }

    @Test
    void shouldRejectNullPortIdInConfiguration() {
        Map<EnergyPortId, Set<EnergyPortMode>> allowed = new HashMap<>();

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
        Map<EnergyPortId, Set<EnergyPortMode>> allowed = new HashMap<>();

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
        Set<EnergyPortMode> modes = new HashSet<>();

        modes.add(EnergyPortMode.INPUT);
        modes.add(null);

        assertThrows(
            NullPointerException.class,
            () -> createPolicy(
                Map.of(
                    PORT,
                    modes
                ),
                Map.of(
                    PORT,
                    EnergyPortMode.INPUT
                )
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
                EnergyPortMode.DISABLED,
                policy.getMode(PORT)
            ),
            () -> assertFalse(policy.isAvailable(PORT)),
            () -> assertFalse(policy.allowsInsert(PORT)),
            () -> assertFalse(policy.allowsExtract(PORT)),
            () -> assertFalse(policy.canChangeMode(
                PORT,
                EnergyPortMode.OUTPUT
            ))
        );
    }


    @Test
    void shouldUseCurrentInputMode() {
        Map<EnergyPortId, EnergyPortMode> current = new HashMap<>();

        current.put(
            PORT,
            EnergyPortMode.INPUT
        );

        var policy = createPolicy(
            Map.of(
                PORT,
                ALL_MODES
            ),
            current
        );

        assertAll(
            () -> assertEquals(
                EnergyPortMode.INPUT,
                policy.getMode(PORT)
            ),
            () -> assertTrue(policy.allowsInsert(PORT)),
            () -> assertFalse(policy.allowsExtract(PORT))
        );
    }

    @Test
    void shouldObserveModeChangesWithoutRecreation() {
        Map<EnergyPortId, EnergyPortMode> current = new HashMap<>();

        current.put(
            PORT,
            EnergyPortMode.INPUT
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
            EnergyPortMode.OUTPUT
        );

        assertFalse(policy.allowsInsert(PORT));
        assertTrue(policy.allowsExtract(PORT));

        current.put(
            PORT,
            EnergyPortMode.DISABLED
        );

        assertFalse(policy.allowsInsert(PORT));
        assertFalse(policy.allowsExtract(PORT));
    }

    @Test
    void shouldAllowOnlyConfiguredModeChanges() {
        var policy = createPolicy(
            Map.of(
                PORT,
                Set.of(
                    EnergyPortMode.OUTPUT,
                    EnergyPortMode.DISABLED
                )
            ),
            Map.of(
                PORT,
                EnergyPortMode.OUTPUT
            )
        );

        assertAll(
            () -> assertTrue(policy.canChangeMode(
                PORT,
                EnergyPortMode.OUTPUT
            )),
            () -> assertTrue(policy.canChangeMode(
                PORT,
                EnergyPortMode.DISABLED
            )),
            () -> assertFalse(policy.canChangeMode(
                PORT,
                EnergyPortMode.INPUT
            ))
        );
    }

    @Test
    void shouldDenyUnknownPortWithoutCallingProviders() {
        AtomicInteger modeChecks = new AtomicInteger();
        AtomicInteger availabilityChecks = new AtomicInteger();

        var policy = new ConfigurableEnergyPortPolicy(
            Map.of(
                PORT,
                ALL_MODES
            ),
            id -> {
                modeChecks.incrementAndGet();
                return EnergyPortMode.OUTPUT;
            },
            id -> {
                availabilityChecks.incrementAndGet();
                return true;
            }
        );

        assertEquals(
            EnergyPortMode.DISABLED,
            policy.getMode(UNKNOWN)
        );
        assertFalse(policy.isAvailable(UNKNOWN));
        assertFalse(policy.allowsExtract(UNKNOWN));
        assertFalse(policy.canChangeMode(
            UNKNOWN,
            EnergyPortMode.INPUT
        ));

        assertEquals(
            0,
            modeChecks.get()
        );
        assertEquals(
            0,
            availabilityChecks.get()
        );
    }

    @Test
    void shouldRespectDynamicAvailability() {
        AtomicBoolean available = new AtomicBoolean(false);

        var policy = new ConfigurableEnergyPortPolicy(
            Map.of(
                PORT,
                ALL_MODES
            ),
            id -> EnergyPortMode.OUTPUT,
            id -> available.get()
        );

        assertFalse(policy.allowsExtract(PORT));

        // Настройка разрешена даже при недоступном порте.
        assertTrue(policy.canChangeMode(
            PORT,
            EnergyPortMode.INPUT
        ));

        available.set(true);

        assertTrue(policy.allowsExtract(PORT));
    }

    @Test
    void shouldDisableInvalidOrMissingCurrentMode() {
        Map<EnergyPortId, EnergyPortMode> current = new HashMap<>();

        current.put(
            PORT,
            EnergyPortMode.INPUT
        );

        var policy = createPolicy(
            Map.of(
                PORT,
                Set.of(
                    EnergyPortMode.OUTPUT,
                    EnergyPortMode.DISABLED
                )
            ),
            current
        );

        // INPUT не входит в допустимые режимы.
        assertEquals(
            EnergyPortMode.DISABLED,
            policy.getMode(PORT)
        );

        current.remove(PORT);

        // Провайдер вернул null.
        assertEquals(
            EnergyPortMode.DISABLED,
            policy.getMode(PORT)
        );

        assertFalse(policy.allowsInsert(PORT));
        assertFalse(policy.allowsExtract(PORT));
    }

    @Test
    void shouldDefensivelyCopyAllowedModesAndSets() {
        Set<EnergyPortMode> allowedSet = new HashSet<>(Set.of(
            EnergyPortMode.OUTPUT,
            EnergyPortMode.DISABLED
        ));

        Map<EnergyPortId, Set<EnergyPortMode>> allowed = new HashMap<>();

        allowed.put(
            PORT,
            allowedSet
        );

        var policy = createPolicy(
            allowed,
            Map.of(
                PORT,
                EnergyPortMode.OUTPUT
            )
        );

        // Изменяем исходные объекты.
        allowedSet.clear();
        allowedSet.add(EnergyPortMode.INPUT);
        allowed.clear();

        assertEquals(
            EnergyPortMode.OUTPUT,
            policy.getMode(PORT)
        );

        assertTrue(policy.canChangeMode(
            PORT,
            EnergyPortMode.OUTPUT
        ));

        assertFalse(policy.canChangeMode(
            PORT,
            EnergyPortMode.INPUT
        ));
    }

    @Test
    void shouldRejectNullPortArguments() {
        var policy = createPolicy(
            Map.of(
                PORT,
                ALL_MODES
            ),
            Map.of(
                PORT,
                EnergyPortMode.INPUT
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
                    EnergyPortMode.INPUT
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
