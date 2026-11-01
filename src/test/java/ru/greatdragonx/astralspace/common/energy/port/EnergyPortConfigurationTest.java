package ru.greatdragonx.astralspace.common.energy.port;

import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class EnergyPortConfigurationTest {

    private static final EnergyPortId INPUT = new EnergyPortId("input");

    private static final EnergyPortId OUTPUT = new EnergyPortId("output");

    private static final EnergyPortId UNKNOWN = new EnergyPortId("unknown");

    private @NonNull EnergyPortPolicy createPolicy(
        @NonNull EnergyPortConfiguration configuration,
        Set<EnergyPortMode> allowedModes
    )
    {
        return new ConfigurableEnergyPortPolicy(
            Map.of(
                INPUT,
                allowedModes
            ),
            configuration::getMode,
            portId -> true
        );
    }

    @Test
    void shouldReturnInitialPortModes() {
        var configuration = new EnergyPortConfiguration(
            Map.of(
                INPUT,
                EnergyPortMode.INPUT,
                OUTPUT,
                EnergyPortMode.OUTPUT
            ),
            () -> {
            }
        );

        assertAll(
            () -> assertEquals(
                EnergyPortMode.INPUT,
                configuration.getMode(INPUT)
            ),
            () -> assertEquals(
                EnergyPortMode.OUTPUT,
                configuration.getMode(OUTPUT)
            )
        );
    }

    @Test
    void shouldReturnDisabledForMissingPort() {
        var configuration = new EnergyPortConfiguration(
            Map.of(),
            () -> {
            }
        );

        assertEquals(
            EnergyPortMode.DISABLED,
            configuration.getMode(UNKNOWN)
        );
    }

    @Test
    void shouldRejectNullPortId() {
        var configuration = new EnergyPortConfiguration(
            Map.of(),
            () -> {
            }
        );

        assertThrows(
            NullPointerException.class,
            () -> configuration.getMode(null)
        );
    }

    @Test
    void shouldDefensivelyCopyInitialModes() {
        Map<EnergyPortId, EnergyPortMode> initial = new HashMap<>();

        initial.put(
            INPUT,
            EnergyPortMode.INPUT
        );

        var configuration = new EnergyPortConfiguration(
            initial,
            () -> {
            }
        );

        initial.put(
            INPUT,
            EnergyPortMode.OUTPUT
        );
        initial.put(
            OUTPUT,
            EnergyPortMode.OUTPUT
        );

        assertAll(
            () -> assertEquals(
                EnergyPortMode.INPUT,
                configuration.getMode(INPUT)
            ),
            () -> assertEquals(
                EnergyPortMode.DISABLED,
                configuration.getMode(OUTPUT)
            )
        );
    }

    @Test
    void shouldRejectNullConstructorArguments() {
        assertAll(
            () -> assertThrows(
                NullPointerException.class,
                () -> new EnergyPortConfiguration(
                    null,
                    () -> {
                    }
                )
            ),
            () -> assertThrows(
                NullPointerException.class,
                () -> new EnergyPortConfiguration(
                    Map.of(),
                    null
                )
            )
        );
    }

    @Test
    void shouldRejectNullKeysAndValues() {
        Map<EnergyPortId, EnergyPortMode> nullKey = new HashMap<>();

        nullKey.put(
            null,
            EnergyPortMode.INPUT
        );

        Map<EnergyPortId, EnergyPortMode> nullValue = new HashMap<>();

        nullValue.put(
            INPUT,
            null
        );

        assertAll(
            () -> assertThrows(
                NullPointerException.class,
                () -> new EnergyPortConfiguration(
                    nullKey,
                    () -> {
                    }
                )
            ),
            () -> assertThrows(
                NullPointerException.class,
                () -> new EnergyPortConfiguration(
                    nullValue,
                    () -> {
                    }
                )
            )
        );
    }

    @Test
    void shouldReturnImmutableIndependentSnapshot() {
        var configuration = new EnergyPortConfiguration(
            Map.of(
                INPUT,
                EnergyPortMode.INPUT
            ),
            () -> {
            }
        );

        Map<EnergyPortId, EnergyPortMode> snapshot = configuration.snapshot();

        assertAll(
            () -> assertEquals(
                Map.of(
                    INPUT,
                    EnergyPortMode.INPUT
                ),
                snapshot
            ),
            () -> assertThrows(
                UnsupportedOperationException.class,
                () -> snapshot.put(
                    OUTPUT,
                    EnergyPortMode.OUTPUT
                )
            ),
            () -> assertThrows(
                UnsupportedOperationException.class,
                () -> snapshot.remove(INPUT)
            ),
            () -> assertEquals(
                EnergyPortMode.INPUT,
                configuration.getMode(INPUT)
            )
        );
    }

    @Test
    void shouldNotNotifyOnConstructionOrReads() {
        AtomicInteger notifications = new AtomicInteger();

        var configuration = new EnergyPortConfiguration(
            Map.of(
                INPUT,
                EnergyPortMode.INPUT
            ),
            notifications::incrementAndGet
        );

        configuration.getMode(INPUT);
        configuration.getMode(UNKNOWN);
        configuration.snapshot();

        assertEquals(
            0,
            notifications.get()
        );
    }


    @Test
    void shouldChangeAllowedModeAndNotify() {
        AtomicInteger notifications = new AtomicInteger();

        var configuration = new EnergyPortConfiguration(
            Map.of(
                INPUT,
                EnergyPortMode.INPUT
            ),
            notifications::incrementAndGet
        );

        var policy = createPolicy(
            configuration,
            Set.of(
                EnergyPortMode.INPUT,
                EnergyPortMode.OUTPUT
            )
        );

        boolean changed = configuration.changeMode(
            INPUT,
            EnergyPortMode.OUTPUT,
            policy
        );

        assertAll(
            () -> assertTrue(changed),
            () -> assertEquals(
                EnergyPortMode.OUTPUT,
                configuration.getMode(INPUT)
            ),
            () -> assertEquals(
                1,
                notifications.get()
            )
        );
    }

    @Test
    void shouldRejectForbiddenModeWithoutChanges() {
        AtomicInteger notifications = new AtomicInteger();

        var configuration = new EnergyPortConfiguration(
            Map.of(
                INPUT,
                EnergyPortMode.INPUT
            ),
            notifications::incrementAndGet
        );

        var policy = createPolicy(
            configuration,
            Set.of(
                EnergyPortMode.INPUT,
                EnergyPortMode.DISABLED
            )
        );

        boolean changed = configuration.changeMode(
            INPUT,
            EnergyPortMode.OUTPUT,
            policy
        );

        assertAll(
            () -> assertFalse(changed),
            () -> assertEquals(
                EnergyPortMode.INPUT,
                configuration.getMode(INPUT)
            ),
            () -> assertEquals(
                0,
                notifications.get()
            )
        );
    }

    @Test
    void shouldNotNotifyWhenModeAlreadySelected() {
        AtomicInteger notifications = new AtomicInteger();

        var configuration = new EnergyPortConfiguration(
            Map.of(
                INPUT,
                EnergyPortMode.INPUT
            ),
            notifications::incrementAndGet
        );

        var policy = createPolicy(
            configuration,
            Set.of(
                EnergyPortMode.INPUT,
                EnergyPortMode.OUTPUT
            )
        );

        assertFalse(configuration.changeMode(
            INPUT,
            EnergyPortMode.INPUT,
            policy
        ));

        assertEquals(
            0,
            notifications.get()
        );
    }

    @Test
    void shouldRejectUnknownPort() {
        AtomicInteger notifications = new AtomicInteger();

        var configuration = new EnergyPortConfiguration(
            Map.of(
                INPUT,
                EnergyPortMode.INPUT
            ),
            notifications::incrementAndGet
        );

        var policy = createPolicy(
            configuration,
            Set.of(
                EnergyPortMode.INPUT,
                EnergyPortMode.OUTPUT
            )
        );

        boolean changed = configuration.changeMode(
            UNKNOWN,
            EnergyPortMode.OUTPUT,
            policy
        );

        assertAll(
            () -> assertFalse(changed),
            () -> assertEquals(
                EnergyPortMode.DISABLED,
                configuration.getMode(UNKNOWN)
            ),
            () -> assertFalse(configuration.snapshot()
                .containsKey(UNKNOWN)),
            () -> assertEquals(
                0,
                notifications.get()
            )
        );
    }

    @Test
    void shouldRejectNullChangeArguments() {
        var configuration = new EnergyPortConfiguration(
            Map.of(
                INPUT,
                EnergyPortMode.INPUT
            ),
            () -> {
            }
        );

        var policy = createPolicy(
            configuration,
            Set.of(
                EnergyPortMode.INPUT,
                EnergyPortMode.OUTPUT
            )
        );

        assertAll(
            () -> assertThrows(
                NullPointerException.class,
                () -> configuration.changeMode(
                    null,
                    EnergyPortMode.OUTPUT,
                    policy
                )
            ),
            () -> assertThrows(
                NullPointerException.class,
                () -> configuration.changeMode(
                    INPUT,
                    null,
                    policy
                )
            ),
            () -> assertThrows(
                NullPointerException.class,
                () -> configuration.changeMode(
                    INPUT,
                    EnergyPortMode.OUTPUT,
                    null
                )
            )
        );
    }

    @Test
    void shouldConfigureRegisteredPortMissingFromInitialModes() {
        AtomicInteger notifications = new AtomicInteger();

        var configuration = new EnergyPortConfiguration(
            Map.of(),
            notifications::incrementAndGet
        );

        var policy = createPolicy(
            configuration,
            Set.of(
                EnergyPortMode.INPUT,
                EnergyPortMode.DISABLED
            )
        );

        boolean changed = configuration.changeMode(
            INPUT,
            EnergyPortMode.INPUT,
            policy
        );

        assertAll(
            () -> assertTrue(changed),
            () -> assertEquals(
                EnergyPortMode.INPUT,
                configuration.getMode(INPUT)
            ),
            () -> assertEquals(
                EnergyPortMode.INPUT,
                configuration.snapshot()
                    .get(INPUT)
            ),
            () -> assertEquals(
                1,
                notifications.get()
            )
        );
    }

    @Test
    void shouldTreatMissingPortDisabledModeAsNoChange() {
        AtomicInteger notifications = new AtomicInteger();

        var configuration = new EnergyPortConfiguration(
            Map.of(),
            notifications::incrementAndGet
        );

        var policy = createPolicy(
            configuration,
            Set.of(
                EnergyPortMode.INPUT,
                EnergyPortMode.DISABLED
            )
        );

        assertFalse(configuration.changeMode(
            INPUT,
            EnergyPortMode.DISABLED,
            policy
        ));

        assertAll(
            () -> assertTrue(configuration.snapshot()
                .isEmpty()),
            () -> assertEquals(
                0,
                notifications.get()
            )
        );
    }

    @Test
    void shouldKeepPreviousSnapshotsUnchanged() {
        var configuration = new EnergyPortConfiguration(
            Map.of(
                INPUT,
                EnergyPortMode.INPUT
            ),
            () -> {
            }
        );

        var policy = createPolicy(
            configuration,
            Set.of(
                EnergyPortMode.INPUT,
                EnergyPortMode.OUTPUT
            )
        );

        Map<EnergyPortId, EnergyPortMode> before = configuration.snapshot();

        assertTrue(configuration.changeMode(
            INPUT,
            EnergyPortMode.OUTPUT,
            policy
        ));

        Map<EnergyPortId, EnergyPortMode> after = configuration.snapshot();

        assertAll(
            () -> assertEquals(
                EnergyPortMode.INPUT,
                before.get(INPUT)
            ),
            () -> assertEquals(
                EnergyPortMode.OUTPUT,
                after.get(INPUT)
            ),
            () -> assertThrows(
                UnsupportedOperationException.class,
                after::clear
            )
        );
    }

    @Test
    void shouldSupportMultipleConsecutiveModeChanges() {
        AtomicInteger notifications = new AtomicInteger();

        var configuration = new EnergyPortConfiguration(
            Map.of(
                INPUT,
                EnergyPortMode.INPUT
            ),
            notifications::incrementAndGet
        );

        var policy = createPolicy(
            configuration,
            Set.of(
                EnergyPortMode.INPUT,
                EnergyPortMode.OUTPUT,
                EnergyPortMode.DISABLED
            )
        );

        assertTrue(configuration.changeMode(
            INPUT,
            EnergyPortMode.OUTPUT,
            policy
        ));

        assertTrue(configuration.changeMode(
            INPUT,
            EnergyPortMode.DISABLED,
            policy
        ));

        assertFalse(configuration.changeMode(
            INPUT,
            EnergyPortMode.DISABLED,
            policy
        ));

        assertTrue(configuration.changeMode(
            INPUT,
            EnergyPortMode.INPUT,
            policy
        ));

        assertAll(
            () -> assertEquals(
                EnergyPortMode.INPUT,
                configuration.getMode(INPUT)
            ),
            () -> assertEquals(
                3,
                notifications.get()
            )
        );
    }

    @Test
    void shouldAllowConfigurationWhilePortIsUnavailable() {
        AtomicInteger notifications = new AtomicInteger();

        var configuration = new EnergyPortConfiguration(
            Map.of(
                INPUT,
                EnergyPortMode.INPUT
            ),
            notifications::incrementAndGet
        );

        var policy = new ConfigurableEnergyPortPolicy(
            Map.of(
                INPUT,
                Set.of(
                    EnergyPortMode.INPUT,
                    EnergyPortMode.OUTPUT
                )
            ),
            configuration::getMode,
            portId -> false
        );

        assertFalse(policy.isAvailable(INPUT));

        assertTrue(configuration.changeMode(
            INPUT,
            EnergyPortMode.OUTPUT,
            policy
        ));

        assertAll(
            () -> assertEquals(
                EnergyPortMode.OUTPUT,
                configuration.getMode(INPUT)
            ),
            () -> assertEquals(
                1,
                notifications.get()
            ),
            () -> assertFalse(policy.allowsExtract(INPUT))
        );
    }

    @Test
    void shouldRestoreAllowedModeWithoutNotification() {
        AtomicInteger notifications = new AtomicInteger();

        var configuration = new EnergyPortConfiguration(
            Map.of(
                INPUT,
                EnergyPortMode.INPUT
            ),
            notifications::incrementAndGet
        );

        var policy = createPolicy(
            configuration,
            Set.of(
                EnergyPortMode.INPUT,
                EnergyPortMode.OUTPUT
            )
        );

        configuration.restoreModes(
            Map.of(
                INPUT,
                EnergyPortMode.OUTPUT
            ),
            policy
        );

        assertAll(
            () -> assertEquals(
                EnergyPortMode.OUTPUT,
                configuration.getMode(INPUT)
            ),
            () -> assertEquals(
                0,
                notifications.get()
            )
        );
    }

    @Test
    void shouldIgnoreForbiddenRestoredMode() {
        var configuration = new EnergyPortConfiguration(
            Map.of(
                INPUT,
                EnergyPortMode.INPUT
            ),
            () -> {
            }
        );

        var policy = createPolicy(
            configuration,
            Set.of(
                EnergyPortMode.INPUT,
                EnergyPortMode.DISABLED
            )
        );

        configuration.restoreModes(
            Map.of(
                INPUT,
                EnergyPortMode.OUTPUT
            ),
            policy
        );

        assertEquals(
            EnergyPortMode.INPUT,
            configuration.getMode(INPUT)
        );
    }

    @Test
    void shouldIgnoreUnknownRestoredPort() {
        var configuration = new EnergyPortConfiguration(
            Map.of(
                INPUT,
                EnergyPortMode.INPUT
            ),
            () -> {
            }
        );

        var policy = createPolicy(
            configuration,
            Set.of(
                EnergyPortMode.INPUT,
                EnergyPortMode.OUTPUT
            )
        );

        configuration.restoreModes(
            Map.of(
                UNKNOWN,
                EnergyPortMode.OUTPUT
            ),
            policy
        );

        assertAll(
            () -> assertEquals(
                EnergyPortMode.DISABLED,
                configuration.getMode(UNKNOWN)
            ),
            () -> assertFalse(configuration.snapshot()
                .containsKey(UNKNOWN))
        );
    }

    @Test
    void shouldRestoreRegisteredPortMissingFromInitialModes() {
        var configuration = new EnergyPortConfiguration(
            Map.of(),
            () -> {
            }
        );

        var policy = createPolicy(
            configuration,
            Set.of(
                EnergyPortMode.INPUT,
                EnergyPortMode.OUTPUT
            )
        );

        configuration.restoreModes(
            Map.of(
                INPUT,
                EnergyPortMode.OUTPUT
            ),
            policy
        );

        assertEquals(
            EnergyPortMode.OUTPUT,
            configuration.getMode(INPUT)
        );
    }

    @Test
    void shouldKeepInitialModesWhenRestoredMapIsEmpty() {
        var configuration = new EnergyPortConfiguration(
            Map.of(
                INPUT,
                EnergyPortMode.INPUT
            ),
            () -> {
            }
        );

        var policy = createPolicy(
            configuration,
            Set.of(
                EnergyPortMode.INPUT,
                EnergyPortMode.OUTPUT
            )
        );

        configuration.restoreModes(
            Map.of(),
            policy
        );

        assertEquals(
            EnergyPortMode.INPUT,
            configuration.getMode(INPUT)
        );
    }

    @Test
    void shouldRejectNullRestoreArguments() {
        var configuration = new EnergyPortConfiguration(
            Map.of(
                INPUT,
                EnergyPortMode.INPUT
            ),
            () -> {
            }
        );

        var policy = createPolicy(
            configuration,
            Set.of(
                EnergyPortMode.INPUT,
                EnergyPortMode.OUTPUT
            )
        );

        assertAll(
            () -> assertThrows(
                NullPointerException.class,
                () -> configuration.restoreModes(
                    null,
                    policy
                )
            ),
            () -> assertThrows(
                NullPointerException.class,
                () -> configuration.restoreModes(
                    Map.of(),
                    null
                )
            )
        );
    }

    @Test
    void shouldRejectMalformedMapBeforeChangingState() {
        var configuration = new EnergyPortConfiguration(
            Map.of(
                INPUT,
                EnergyPortMode.INPUT
            ),
            () -> {
            }
        );

        var policy = createPolicy(
            configuration,
            Set.of(
                EnergyPortMode.INPUT,
                EnergyPortMode.OUTPUT
            )
        );

        Map<EnergyPortId, EnergyPortMode> malformed = new LinkedHashMap<>();

        malformed.put(
            INPUT,
            EnergyPortMode.OUTPUT
        );
        malformed.put(
            UNKNOWN,
            null
        );

        assertThrows(
            NullPointerException.class,
            () -> configuration.restoreModes(
                malformed,
                policy
            )
        );

        assertEquals(
            EnergyPortMode.INPUT,
            configuration.getMode(INPUT)
        );
    }

}
