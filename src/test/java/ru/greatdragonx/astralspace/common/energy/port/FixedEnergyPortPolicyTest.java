package ru.greatdragonx.astralspace.common.energy.port;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class FixedEnergyPortPolicyTest {

    private static final EnergyPortId INPUT = new EnergyPortId("input");

    private static final EnergyPortId OUTPUT = new EnergyPortId("output");

    private static final EnergyPortId DISABLED = new EnergyPortId("disabled");

    private static final EnergyPortId UNKNOWN = new EnergyPortId("unknown");

    @Test
    void shouldReturnConfiguredPortModes() {
        FixedEnergyPortPolicy policy = new FixedEnergyPortPolicy(
            Map.of(
                INPUT,
                EnergyPortMode.INPUT,
                OUTPUT,
                EnergyPortMode.OUTPUT,
                DISABLED,
                EnergyPortMode.DISABLED
            ),
            portId -> true
        );

        assertAll(
            () -> assertEquals(
                EnergyPortMode.INPUT,
                policy.getMode(INPUT)
            ),
            () -> assertEquals(
                EnergyPortMode.OUTPUT,
                policy.getMode(OUTPUT)
            ),
            () -> assertEquals(
                EnergyPortMode.DISABLED,
                policy.getMode(DISABLED)
            )
        );
    }

    @Test
    void shouldRespectFixedInputAndOutputModes() {
        FixedEnergyPortPolicy policy = new FixedEnergyPortPolicy(
            Map.of(
                INPUT,
                EnergyPortMode.INPUT,
                OUTPUT,
                EnergyPortMode.OUTPUT
            ),
            portId -> true
        );

        assertAll(
            () -> assertTrue(policy.allowsInsert(INPUT)),
            () -> assertFalse(policy.allowsExtract(INPUT)),
            () -> assertFalse(policy.allowsInsert(OUTPUT)),
            () -> assertTrue(policy.allowsExtract(OUTPUT))
        );
    }

    @Test
    void shouldDenyTransfersForDisabledPort() {
        FixedEnergyPortPolicy policy = new FixedEnergyPortPolicy(
            Map.of(
                DISABLED,
                EnergyPortMode.DISABLED
            ),
            portId -> true
        );

        assertAll(
            () -> assertTrue(policy.isAvailable(DISABLED)),
            () -> assertFalse(policy.allowsInsert(DISABLED)),
            () -> assertFalse(policy.allowsExtract(DISABLED))
        );
    }

    @Test
    void shouldDenyUnknownPortsWithoutCheckingAvailability() {
        AtomicInteger checks = new AtomicInteger();

        FixedEnergyPortPolicy policy = new FixedEnergyPortPolicy(
            Map.of(
                OUTPUT,
                EnergyPortMode.OUTPUT
            ),
            portId -> {
                checks.incrementAndGet();
                return true;
            }
        );

        assertAll(
            () -> assertEquals(
                EnergyPortMode.DISABLED,
                policy.getMode(UNKNOWN)
            ),
            () -> assertFalse(policy.isAvailable(UNKNOWN)),
            () -> assertFalse(policy.allowsInsert(UNKNOWN)),
            () -> assertFalse(policy.allowsExtract(UNKNOWN)),
            () -> assertEquals(
                0,
                checks.get()
            )
        );
    }

    @Test
    void shouldNeverAllowChangingFixedPortMode() {
        FixedEnergyPortPolicy policy = new FixedEnergyPortPolicy(
            Map.of(
                OUTPUT,
                EnergyPortMode.OUTPUT
            ),
            portId -> true
        );

        for (EnergyPortMode mode : EnergyPortMode.values()) {
            assertFalse(policy.canChangeMode(
                OUTPUT,
                mode
            ));

            assertFalse(policy.canChangeMode(
                UNKNOWN,
                mode
            ));
        }
    }

    @Test
    void shouldObserveDynamicAvailabilityChanges() {
        AtomicBoolean available = new AtomicBoolean(true);

        FixedEnergyPortPolicy policy = new FixedEnergyPortPolicy(
            Map.of(
                OUTPUT,
                EnergyPortMode.OUTPUT
            ),
            portId -> available.get()
        );

        assertTrue(policy.allowsExtract(OUTPUT));

        available.set(false);

        assertFalse(policy.isAvailable(OUTPUT));
        assertFalse(policy.allowsExtract(OUTPUT));

        available.set(true);

        assertTrue(policy.allowsExtract(OUTPUT));
    }

    @Test
    void shouldCheckAvailabilityIndependentlyForEachPort() {
        Set<EnergyPortId> availablePorts = Set.of(INPUT);

        FixedEnergyPortPolicy policy = new FixedEnergyPortPolicy(
            Map.of(
                INPUT,
                EnergyPortMode.INPUT,
                OUTPUT,
                EnergyPortMode.OUTPUT
            ),
            availablePorts::contains
        );

        assertAll(
            () -> assertTrue(policy.isAvailable(INPUT)),
            () -> assertFalse(policy.isAvailable(OUTPUT)),
            () -> assertTrue(policy.allowsInsert(INPUT)),
            () -> assertFalse(policy.allowsExtract(OUTPUT))
        );
    }

    @Test
    void shouldDefensivelyCopyPortConfiguration() {
        Map<EnergyPortId, EnergyPortMode> modes = new HashMap<>();

        modes.put(
            OUTPUT,
            EnergyPortMode.OUTPUT
        );

        FixedEnergyPortPolicy policy = new FixedEnergyPortPolicy(
            modes,
            portId -> true
        );

        modes.put(
            OUTPUT,
            EnergyPortMode.INPUT
        );
        modes.put(
            INPUT,
            EnergyPortMode.INPUT
        );

        assertAll(
            () -> assertEquals(
                EnergyPortMode.OUTPUT,
                policy.getMode(OUTPUT)
            ),
            () -> assertEquals(
                EnergyPortMode.DISABLED,
                policy.getMode(INPUT)
            ),
            () -> assertTrue(policy.allowsExtract(OUTPUT)),
            () -> assertFalse(policy.allowsInsert(OUTPUT))
        );
    }

    @Test
    void shouldRejectNullConstructorArguments() {
        assertAll(
            () -> assertThrows(
                NullPointerException.class,
                () -> new FixedEnergyPortPolicy(
                    null,
                    portId -> true
                )
            ),
            () -> assertThrows(
                NullPointerException.class,
                () -> new FixedEnergyPortPolicy(
                    Map.of(),
                    null
                )
            )
        );
    }

    @Test
    void shouldRejectNullPortIdsAndModesInConfiguration() {
        Map<EnergyPortId, EnergyPortMode> nullKey = new HashMap<>();
        nullKey.put(
            null,
            EnergyPortMode.OUTPUT
        );

        Map<EnergyPortId, EnergyPortMode> nullMode = new HashMap<>();
        nullMode.put(
            OUTPUT,
            null
        );

        assertAll(
            () -> assertThrows(
                NullPointerException.class,
                () -> new FixedEnergyPortPolicy(
                    nullKey,
                    portId -> true
                )
            ),
            () -> assertThrows(
                NullPointerException.class,
                () -> new FixedEnergyPortPolicy(
                    nullMode,
                    portId -> true
                )
            )
        );
    }


    @Test
    void shouldRejectNullArgumentsInPortOperations() {
        FixedEnergyPortPolicy policy = new FixedEnergyPortPolicy(
            Map.of(
                OUTPUT,
                EnergyPortMode.OUTPUT
            ),
            portId -> true
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
                    OUTPUT,
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
