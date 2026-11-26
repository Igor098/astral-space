package ru.greatdragonx.astralspace.common.energy.port;

import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

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
            Map.of(INPUT, allowedModes),
            configuration::getMode,
            portId -> true
        );
    }

    @Test
    void shouldReturnInitialPortModes() {
        var configuration = new EnergyPortConfiguration(
            Map.of(INPUT, EnergyPortMode.INPUT, OUTPUT, EnergyPortMode.OUTPUT), ignored -> {
        }
        );
        assertAll(
            () -> assertEquals(EnergyPortMode.INPUT, configuration.getMode(INPUT)),
            () -> assertEquals(EnergyPortMode.OUTPUT, configuration.getMode(OUTPUT))
        );
    }

    @Test
    void shouldReturnDisabledForMissingPort() {
        var configuration = new EnergyPortConfiguration(
            Map.of(), ignored -> {
        }
        );
        assertEquals(EnergyPortMode.DISABLED, configuration.getMode(UNKNOWN));
    }

    @Test
    void shouldRejectNullPortId() {
        var configuration = new EnergyPortConfiguration(
            Map.of(), ignored -> {
        }
        );
        assertThrows(NullPointerException.class, () -> configuration.getMode(null));
    }

    @Test
    void shouldDefensivelyCopyInitialModes() {
        Map<EnergyPortId, EnergyPortMode> initial = new HashMap<>();
        initial.put(INPUT, EnergyPortMode.INPUT);
        var configuration = new EnergyPortConfiguration(
            initial, ignored -> {
        }
        );
        initial.put(INPUT, EnergyPortMode.OUTPUT);
        initial.put(OUTPUT, EnergyPortMode.OUTPUT);
        assertAll(
            () -> assertEquals(EnergyPortMode.INPUT, configuration.getMode(INPUT)),
            () -> assertEquals(EnergyPortMode.DISABLED, configuration.getMode(OUTPUT))
        );
    }

    @Test
    void shouldRejectNullConstructorArguments() {
        assertAll(
            () -> assertThrows(
                NullPointerException.class, () -> new EnergyPortConfiguration(
                    null, ignored -> {
                }
                )
            ),
            () -> assertThrows(
                NullPointerException.class,
                () -> new EnergyPortConfiguration(Map.of(), null)
            )
        );
    }

    @Test
    void shouldRejectNullKeysAndValues() {
        Map<EnergyPortId, EnergyPortMode> nullKey = new HashMap<>();
        nullKey.put(null, EnergyPortMode.INPUT);
        Map<EnergyPortId, EnergyPortMode> nullValue = new HashMap<>();
        nullValue.put(INPUT, null);
        assertAll(
            () -> assertThrows(
                NullPointerException.class, () -> new EnergyPortConfiguration(
                    nullKey, ignored -> {
                }
                )
            ), () -> assertThrows(
                NullPointerException.class, () -> new EnergyPortConfiguration(
                    nullValue, ignored -> {
                }
                )
            )
        );
    }

    @Test
    void shouldReturnImmutableIndependentSnapshot() {
        var configuration = new EnergyPortConfiguration(
            Map.of(INPUT, EnergyPortMode.INPUT), ignored -> {
        }
        );
        Map<EnergyPortId, EnergyPortMode> snapshot = configuration.snapshot();
        assertAll(
            () -> assertEquals(Map.of(INPUT, EnergyPortMode.INPUT), snapshot),
            () -> assertThrows(
                UnsupportedOperationException.class,
                () -> snapshot.put(OUTPUT, EnergyPortMode.OUTPUT)
            ),
            () -> assertThrows(UnsupportedOperationException.class, () -> snapshot.remove(INPUT)),
            () -> assertEquals(EnergyPortMode.INPUT, configuration.getMode(INPUT))
        );
    }

    @Test
    void shouldNotNotifyOnConstructionOrReads() {
        List<EnergyPortId> notifications = new ArrayList<>();
        var configuration = new EnergyPortConfiguration(
            Map.of(INPUT, EnergyPortMode.INPUT),
            notifications::add
        );
        configuration.getMode(INPUT);
        configuration.getMode(UNKNOWN);
        configuration.snapshot();
        assertTrue(notifications.isEmpty());
    }

    @Test
    void shouldChangeAllowedModeAndNotify() {
        List<EnergyPortId> notifications = new ArrayList<>();
        var configuration = new EnergyPortConfiguration(
            Map.of(INPUT, EnergyPortMode.INPUT),
            notifications::add
        );
        var policy = createPolicy(
            configuration,
            Set.of(EnergyPortMode.INPUT, EnergyPortMode.OUTPUT)
        );
        boolean changed = configuration.changeMode(INPUT, EnergyPortMode.OUTPUT, policy);
        assertAll(
            () -> assertTrue(changed),
            () -> assertEquals(EnergyPortMode.OUTPUT, configuration.getMode(INPUT)),
            () -> assertEquals(List.of(INPUT), notifications)
        );
    }

    @Test
    void shouldRejectForbiddenModeWithoutChanges() {
        List<EnergyPortId> notifications = new ArrayList<>();
        var configuration = new EnergyPortConfiguration(
            Map.of(INPUT, EnergyPortMode.INPUT),
            notifications::add
        );
        var policy = createPolicy(
            configuration,
            Set.of(EnergyPortMode.INPUT, EnergyPortMode.DISABLED)
        );
        boolean changed = configuration.changeMode(INPUT, EnergyPortMode.OUTPUT, policy);
        assertAll(
            () -> assertFalse(changed),
            () -> assertEquals(EnergyPortMode.INPUT, configuration.getMode(INPUT)),
            () -> assertTrue(notifications.isEmpty())
        );
    }

    @Test
    void shouldNotNotifyWhenModeAlreadySelected() {
        List<EnergyPortId> notifications = new ArrayList<>();
        var configuration = new EnergyPortConfiguration(
            Map.of(INPUT, EnergyPortMode.INPUT),
            notifications::add
        );
        var policy = createPolicy(
            configuration,
            Set.of(EnergyPortMode.INPUT, EnergyPortMode.OUTPUT)
        );
        assertFalse(configuration.changeMode(INPUT, EnergyPortMode.INPUT, policy));
        assertTrue(notifications.isEmpty());
    }

    @Test
    void shouldRejectUnknownPort() {
        List<EnergyPortId> notifications = new ArrayList<>();
        var configuration = new EnergyPortConfiguration(
            Map.of(INPUT, EnergyPortMode.INPUT),
            notifications::add
        );
        var policy = createPolicy(
            configuration,
            Set.of(EnergyPortMode.INPUT, EnergyPortMode.OUTPUT)
        );
        boolean changed = configuration.changeMode(UNKNOWN, EnergyPortMode.OUTPUT, policy);
        assertAll(
            () -> assertFalse(changed),
            () -> assertEquals(EnergyPortMode.DISABLED, configuration.getMode(UNKNOWN)),
            () -> assertFalse(configuration.snapshot().containsKey(UNKNOWN)),
            () -> assertTrue(notifications.isEmpty())
        );
    }

    @Test
    void shouldRejectNullChangeArguments() {
        List<EnergyPortId> notifications = new ArrayList<>();
        var configuration = new EnergyPortConfiguration(
            Map.of(INPUT, EnergyPortMode.INPUT),
            notifications::add
        );
        var policy = createPolicy(
            configuration,
            Set.of(EnergyPortMode.INPUT, EnergyPortMode.OUTPUT)
        );
        assertAll(
            () -> assertThrows(
                NullPointerException.class,
                () -> configuration.changeMode(null, EnergyPortMode.OUTPUT, policy)
            ),
            () -> assertThrows(
                NullPointerException.class,
                () -> configuration.changeMode(INPUT, null, policy)
            ),
            () -> assertThrows(
                NullPointerException.class,
                () -> configuration.changeMode(INPUT, EnergyPortMode.OUTPUT, null)
            )
        );
        assertEquals(Map.of(INPUT, EnergyPortMode.INPUT), configuration.snapshot());
        assertTrue(notifications.isEmpty());
    }

    @Test
    void shouldConfigureRegisteredPortMissingFromInitialModes() {
        List<EnergyPortId> notifications = new ArrayList<>();
        var configuration = new EnergyPortConfiguration(Map.of(), notifications::add);
        var policy = createPolicy(
            configuration,
            Set.of(EnergyPortMode.INPUT, EnergyPortMode.DISABLED)
        );
        boolean changed = configuration.changeMode(INPUT, EnergyPortMode.INPUT, policy);
        assertAll(
            () -> assertTrue(changed),
            () -> assertEquals(EnergyPortMode.INPUT, configuration.getMode(INPUT)),
            () -> assertEquals(EnergyPortMode.INPUT, configuration.snapshot().get(INPUT)),
            () -> assertEquals(List.of(INPUT), notifications)
        );
    }

    @Test
    void shouldTreatMissingPortDisabledModeAsNoChange() {
        List<EnergyPortId> notifications = new ArrayList<>();
        var configuration = new EnergyPortConfiguration(Map.of(), notifications::add);
        var policy = createPolicy(
            configuration,
            Set.of(EnergyPortMode.INPUT, EnergyPortMode.DISABLED)
        );
        assertFalse(configuration.changeMode(INPUT, EnergyPortMode.DISABLED, policy));
        assertAll(
            () -> assertTrue(configuration.snapshot().isEmpty()),
            () -> assertTrue(notifications.isEmpty())
        );
    }

    @Test
    void shouldKeepPreviousSnapshotsUnchanged() {
        var configuration = new EnergyPortConfiguration(
            Map.of(INPUT, EnergyPortMode.INPUT), ignored -> {
        }
        );
        var policy = createPolicy(
            configuration,
            Set.of(EnergyPortMode.INPUT, EnergyPortMode.OUTPUT)
        );
        Map<EnergyPortId, EnergyPortMode> before = configuration.snapshot();
        assertTrue(configuration.changeMode(INPUT, EnergyPortMode.OUTPUT, policy));
        Map<EnergyPortId, EnergyPortMode> after = configuration.snapshot();
        assertAll(
            () -> assertEquals(EnergyPortMode.INPUT, before.get(INPUT)),
            () -> assertEquals(EnergyPortMode.OUTPUT, after.get(INPUT)),
            () -> assertThrows(UnsupportedOperationException.class, after::clear)
        );
    }

    @Test
    void shouldSupportMultipleConsecutiveModeChanges() {
        List<EnergyPortId> notifications = new ArrayList<>();
        var configuration = new EnergyPortConfiguration(
            Map.of(INPUT, EnergyPortMode.INPUT),
            notifications::add
        );
        var policy = createPolicy(
            configuration,
            Set.of(EnergyPortMode.INPUT, EnergyPortMode.OUTPUT, EnergyPortMode.DISABLED)
        );
        assertTrue(configuration.changeMode(INPUT, EnergyPortMode.OUTPUT, policy));
        assertTrue(configuration.changeMode(INPUT, EnergyPortMode.DISABLED, policy));
        assertFalse(configuration.changeMode(INPUT, EnergyPortMode.DISABLED, policy));
        assertTrue(configuration.changeMode(INPUT, EnergyPortMode.INPUT, policy));
        assertAll(
            () -> assertEquals(EnergyPortMode.INPUT, configuration.getMode(INPUT)),
            () -> assertEquals(List.of(INPUT, INPUT, INPUT), notifications)
        );
    }

    @Test
    void shouldAllowConfigurationWhilePortIsUnavailable() {
        List<EnergyPortId> notifications = new ArrayList<>();
        var configuration = new EnergyPortConfiguration(
            Map.of(INPUT, EnergyPortMode.INPUT),
            notifications::add
        );
        var policy = new ConfigurableEnergyPortPolicy(
            Map.of(INPUT, Set.of(EnergyPortMode.INPUT, EnergyPortMode.OUTPUT)),
            configuration::getMode,
            portId -> false
        );
        assertFalse(policy.isAvailable(INPUT));
        assertTrue(configuration.changeMode(INPUT, EnergyPortMode.OUTPUT, policy));
        assertAll(
            () -> assertEquals(EnergyPortMode.OUTPUT, configuration.getMode(INPUT)),
            () -> assertEquals(List.of(INPUT), notifications),
            () -> assertFalse(policy.allowsExtract(INPUT))
        );
    }

    @Test
    void shouldRestoreAllowedModeWithoutNotification() {
        List<EnergyPortId> notifications = new ArrayList<>();
        var configuration = new EnergyPortConfiguration(
            Map.of(INPUT, EnergyPortMode.INPUT),
            notifications::add
        );
        var policy = createPolicy(
            configuration,
            Set.of(EnergyPortMode.INPUT, EnergyPortMode.OUTPUT)
        );
        configuration.restoreModes(Map.of(INPUT, EnergyPortMode.OUTPUT), policy);
        assertAll(
            () -> assertEquals(EnergyPortMode.OUTPUT, configuration.getMode(INPUT)),
            () -> assertTrue(notifications.isEmpty())
        );
    }

    @Test
    void shouldIgnoreForbiddenRestoredMode() {
        var configuration = new EnergyPortConfiguration(
            Map.of(INPUT, EnergyPortMode.INPUT), ignored -> {
        }
        );
        var policy = createPolicy(
            configuration,
            Set.of(EnergyPortMode.INPUT, EnergyPortMode.DISABLED)
        );
        configuration.restoreModes(Map.of(INPUT, EnergyPortMode.OUTPUT), policy);
        assertEquals(EnergyPortMode.INPUT, configuration.getMode(INPUT));
    }

    @Test
    void shouldIgnoreUnknownRestoredPort() {
        var configuration = new EnergyPortConfiguration(
            Map.of(INPUT, EnergyPortMode.INPUT), ignored -> {
        }
        );
        var policy = createPolicy(
            configuration,
            Set.of(EnergyPortMode.INPUT, EnergyPortMode.OUTPUT)
        );
        configuration.restoreModes(Map.of(UNKNOWN, EnergyPortMode.OUTPUT), policy);
        assertAll(
            () -> assertEquals(EnergyPortMode.DISABLED, configuration.getMode(UNKNOWN)),
            () -> assertFalse(configuration.snapshot().containsKey(UNKNOWN))
        );
    }

    @Test
    void shouldRestoreRegisteredPortMissingFromInitialModes() {
        var configuration = new EnergyPortConfiguration(
            Map.of(), ignored -> {
        }
        );
        var policy = createPolicy(
            configuration,
            Set.of(EnergyPortMode.INPUT, EnergyPortMode.OUTPUT)
        );
        configuration.restoreModes(Map.of(INPUT, EnergyPortMode.OUTPUT), policy);
        assertEquals(EnergyPortMode.OUTPUT, configuration.getMode(INPUT));
    }

    @Test
    void shouldKeepInitialModesWhenRestoredMapIsEmpty() {
        var configuration = new EnergyPortConfiguration(
            Map.of(INPUT, EnergyPortMode.INPUT), ignored -> {
        }
        );
        var policy = createPolicy(
            configuration,
            Set.of(EnergyPortMode.INPUT, EnergyPortMode.OUTPUT)
        );
        configuration.restoreModes(Map.of(), policy);
        assertEquals(EnergyPortMode.INPUT, configuration.getMode(INPUT));
    }

    @Test
    void shouldRejectNullRestoreArguments() {
        var configuration = new EnergyPortConfiguration(
            Map.of(INPUT, EnergyPortMode.INPUT), ignored -> {
        }
        );
        var policy = createPolicy(
            configuration,
            Set.of(EnergyPortMode.INPUT, EnergyPortMode.OUTPUT)
        );
        assertAll(
            () -> assertThrows(
                NullPointerException.class,
                () -> configuration.restoreModes(null, policy)
            ),
            () -> assertThrows(
                NullPointerException.class,
                () -> configuration.restoreModes(Map.of(), null)
            )
        );
    }

    @Test
    void shouldRejectMalformedMapBeforeChangingState() {
        var configuration = new EnergyPortConfiguration(
            Map.of(INPUT, EnergyPortMode.INPUT), ignored -> {
        }
        );
        var policy = createPolicy(
            configuration,
            Set.of(EnergyPortMode.INPUT, EnergyPortMode.OUTPUT)
        );
        Map<EnergyPortId, EnergyPortMode> malformed = new LinkedHashMap<>();
        malformed.put(INPUT, EnergyPortMode.OUTPUT);
        malformed.put(UNKNOWN, null);
        assertThrows(
            NullPointerException.class,
            () -> configuration.restoreModes(malformed, policy)
        );
        assertEquals(EnergyPortMode.INPUT, configuration.getMode(INPUT));
    }

    @Test
    void shouldNotifyOnlyChangedPortsInChangeOrder() {
        List<EnergyPortId> notifications = new ArrayList<>();
        var configuration = new EnergyPortConfiguration(
            Map.of(
                INPUT,
                EnergyPortMode.INPUT,
                OUTPUT,
                EnergyPortMode.OUTPUT
        ), notifications::add
        );
        var policy = new ConfigurableEnergyPortPolicy(
            Map.of(
                INPUT,
                Set.of(EnergyPortMode.INPUT, EnergyPortMode.DISABLED),
                OUTPUT,
                Set.of(EnergyPortMode.OUTPUT, EnergyPortMode.DISABLED)
            ), configuration::getMode, ignored -> true
        );
        assertTrue(configuration.changeMode(OUTPUT, EnergyPortMode.DISABLED, policy));
        assertFalse(configuration.changeMode(INPUT, EnergyPortMode.OUTPUT, policy));
        assertTrue(configuration.changeMode(INPUT, EnergyPortMode.DISABLED, policy));
        assertFalse(configuration.changeMode(OUTPUT, EnergyPortMode.DISABLED, policy));
        assertFalse(configuration.changeMode(UNKNOWN, EnergyPortMode.OUTPUT, policy));
        assertTrue(configuration.changeMode(OUTPUT, EnergyPortMode.OUTPUT, policy));
        assertEquals(List.of(OUTPUT, INPUT, OUTPUT), notifications);
        assertEquals(
            Map.of(INPUT, EnergyPortMode.DISABLED, OUTPUT, EnergyPortMode.OUTPUT),
            configuration.snapshot()
        );
    }

    @Test
    void shouldNotifyAfterNewModeIsStored() {
        AtomicReference<EnergyPortConfiguration> configurationRef = new AtomicReference<>();
        List<EnergyPortId> notifications = new ArrayList<>();
        List<EnergyPortMode> observedModes = new ArrayList<>();
        var configuration = new EnergyPortConfiguration(
            Map.of(INPUT, EnergyPortMode.INPUT), portId -> {
            notifications.add(portId);
            observedModes.add(configurationRef.get().getMode(portId));
        }
        );
        configurationRef.set(configuration);
        var policy = createPolicy(
            configuration,
            Set.of(EnergyPortMode.INPUT, EnergyPortMode.OUTPUT)
        );
        assertTrue(configuration.changeMode(INPUT, EnergyPortMode.OUTPUT, policy));
        assertTrue(configuration.changeMode(INPUT, EnergyPortMode.INPUT, policy));
        assertEquals(List.of(INPUT, INPUT), notifications);
        assertEquals(List.of(EnergyPortMode.OUTPUT, EnergyPortMode.INPUT), observedModes);
    }

    @Test
    void shouldRestoreMultiplePortsWithoutAddressedNotifications() {
        List<EnergyPortId> notifications = new ArrayList<>();
        var configuration = new EnergyPortConfiguration(
            Map.of(
                INPUT,
                EnergyPortMode.INPUT,
                OUTPUT,
                EnergyPortMode.OUTPUT
        ), notifications::add
        );
        var policy = new ConfigurableEnergyPortPolicy(
            Map.of(
                INPUT,
                Set.of(EnergyPortMode.INPUT, EnergyPortMode.DISABLED),
                OUTPUT,
                Set.of(EnergyPortMode.OUTPUT, EnergyPortMode.DISABLED)
            ), configuration::getMode, ignored -> true
        );
        configuration.restoreModes(
            Map.of(
                INPUT,
                EnergyPortMode.DISABLED,
                OUTPUT,
                EnergyPortMode.DISABLED
            ), policy
        );
        assertEquals(
            Map.of(INPUT, EnergyPortMode.DISABLED, OUTPUT, EnergyPortMode.DISABLED),
            configuration.snapshot()
        );
        assertTrue(notifications.isEmpty());
        assertTrue(configuration.changeMode(OUTPUT, EnergyPortMode.OUTPUT, policy));
        assertEquals(List.of(OUTPUT), notifications);
    }
}
