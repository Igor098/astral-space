package ru.greatdragonx.astralspace.common.fluid.port;

import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class FluidPortConfigurationTest {

    private static final FluidPortId INPUT = new FluidPortId("input");
    private static final FluidPortId OUTPUT = new FluidPortId("output");
    private static final FluidPortId UNKNOWN = new FluidPortId("unknown");

    private @NonNull FluidPortPolicy createPolicy(
        @NonNull FluidPortConfiguration configuration,
        @NonNull Set<FluidPortMode> allowedModes
    )
    {
        return new ConfigurableFluidPortPolicy(
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
        var configuration = new FluidPortConfiguration(
            Map.of(
                INPUT,
                FluidPortMode.INPUT,
                OUTPUT,
                FluidPortMode.OUTPUT
            ),
            () -> {
            }
        );

        assertAll(
            () -> assertEquals(
                FluidPortMode.INPUT,
                configuration.getMode(INPUT)
            ),
            () -> assertEquals(
                FluidPortMode.OUTPUT,
                configuration.getMode(OUTPUT)
            )
        );
    }

    @Test
    void shouldReturnDisabledForMissingPort() {
        var configuration = new FluidPortConfiguration(
            Map.of(),
            () -> {
            }
        );
        assertEquals(
            FluidPortMode.DISABLED,
            configuration.getMode(UNKNOWN)
        );
    }

    @Test
    void shouldRejectNullPortId() {
        var configuration = new FluidPortConfiguration(
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
        Map<FluidPortId, FluidPortMode> initial = new HashMap<>();
        initial.put(
            INPUT,
            FluidPortMode.INPUT
        );

        var configuration = new FluidPortConfiguration(
            initial,
            () -> {
            }
        );
        initial.put(
            INPUT,
            FluidPortMode.OUTPUT
        );
        initial.put(
            OUTPUT,
            FluidPortMode.OUTPUT
        );

        assertAll(
            () -> assertEquals(
                FluidPortMode.INPUT,
                configuration.getMode(INPUT)
            ),
            () -> assertEquals(
                FluidPortMode.DISABLED,
                configuration.getMode(OUTPUT)
            )
        );
    }

    @Test
    void shouldRejectNullConstructorArguments() {
        assertAll(
            () -> assertThrows(
                NullPointerException.class,
                () -> new FluidPortConfiguration(
                    null,
                    () -> {
                    }
                )
            ),
            () -> assertThrows(
                NullPointerException.class,
                () -> new FluidPortConfiguration(
                    Map.of(),
                    null
                )
            )
        );
    }

    @Test
    void shouldRejectNullKeysAndValues() {
        Map<FluidPortId, FluidPortMode> nullKey = new HashMap<>();
        nullKey.put(
            null,
            FluidPortMode.INPUT
        );

        Map<FluidPortId, FluidPortMode> nullValue = new HashMap<>();
        nullValue.put(
            INPUT,
            null
        );

        assertAll(
            () -> assertThrows(
                NullPointerException.class,
                () -> new FluidPortConfiguration(
                    nullKey,
                    () -> {
                    }
                )
            ),
            () -> assertThrows(
                NullPointerException.class,
                () -> new FluidPortConfiguration(
                    nullValue,
                    () -> {
                    }
                )
            )
        );
    }

    @Test
    void shouldAcceptEmptyInitialModes() {
        var configuration = new FluidPortConfiguration(
            Map.of(),
            () -> {
            }
        );

        assertAll(
            () -> assertTrue(configuration.snapshot()
                .isEmpty()),
            () -> assertEquals(
                FluidPortMode.DISABLED,
                configuration.getMode(INPUT)
            )
        );
    }

    @Test
    void shouldReturnImmutableIndependentSnapshot() {
        var configuration = new FluidPortConfiguration(
            Map.of(
                INPUT,
                FluidPortMode.INPUT
            ),
            () -> {
            }
        );
        Map<FluidPortId, FluidPortMode> snapshot = configuration.snapshot();

        assertAll(
            () -> assertEquals(
                Map.of(
                    INPUT,
                    FluidPortMode.INPUT
                ),
                snapshot
            ),
            () -> assertThrows(
                UnsupportedOperationException.class,
                () -> snapshot.put(
                    OUTPUT,
                    FluidPortMode.OUTPUT
                )
            ),
            () -> assertThrows(
                UnsupportedOperationException.class,
                () -> snapshot.remove(INPUT)
            ),
            () -> assertEquals(
                FluidPortMode.INPUT,
                configuration.getMode(INPUT)
            )
        );
    }

    @Test
    void shouldNotNotifyOnConstructionOrReads() {
        AtomicInteger notifications = new AtomicInteger();
        var configuration = new FluidPortConfiguration(
            Map.of(
                INPUT,
                FluidPortMode.INPUT
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
        var configuration = new FluidPortConfiguration(
            Map.of(
                INPUT,
                FluidPortMode.INPUT
            ),
            notifications::incrementAndGet
        );
        var policy = createPolicy(
            configuration,
            Set.of(
                FluidPortMode.INPUT,
                FluidPortMode.OUTPUT
            )
        );

        boolean changed = configuration.changeMode(
            INPUT,
            FluidPortMode.OUTPUT,
            policy
        );

        assertAll(
            () -> assertTrue(changed),
            () -> assertEquals(
                FluidPortMode.OUTPUT,
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
        var configuration = new FluidPortConfiguration(
            Map.of(
                INPUT,
                FluidPortMode.INPUT
            ),
            notifications::incrementAndGet
        );
        var policy = createPolicy(
            configuration,
            Set.of(
                FluidPortMode.INPUT,
                FluidPortMode.DISABLED
            )
        );

        boolean changed = configuration.changeMode(
            INPUT,
            FluidPortMode.OUTPUT,
            policy
        );

        assertAll(
            () -> assertFalse(changed),
            () -> assertEquals(
                FluidPortMode.INPUT,
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
        var configuration = new FluidPortConfiguration(
            Map.of(
                INPUT,
                FluidPortMode.INPUT
            ),
            notifications::incrementAndGet
        );
        var policy = createPolicy(
            configuration,
            Set.of(
                FluidPortMode.INPUT,
                FluidPortMode.OUTPUT
            )
        );

        assertFalse(configuration.changeMode(
            INPUT,
            FluidPortMode.INPUT,
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
        var configuration = new FluidPortConfiguration(
            Map.of(
                INPUT,
                FluidPortMode.INPUT
            ),
            notifications::incrementAndGet
        );
        var policy = createPolicy(
            configuration,
            Set.of(
                FluidPortMode.INPUT,
                FluidPortMode.OUTPUT
            )
        );

        boolean changed = configuration.changeMode(
            UNKNOWN,
            FluidPortMode.OUTPUT,
            policy
        );

        assertAll(
            () -> assertFalse(changed),
            () -> assertEquals(
                FluidPortMode.DISABLED,
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
        var configuration = new FluidPortConfiguration(
            Map.of(
                INPUT,
                FluidPortMode.INPUT
            ),
            () -> {
            }
        );
        var policy = createPolicy(
            configuration,
            Set.of(
                FluidPortMode.INPUT,
                FluidPortMode.OUTPUT
            )
        );

        assertAll(
            () -> assertThrows(
                NullPointerException.class,
                () -> configuration.changeMode(
                    null,
                    FluidPortMode.OUTPUT,
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
                    FluidPortMode.OUTPUT,
                    null
                )
            )
        );
    }

    @Test
    void shouldConfigureRegisteredPortMissingFromInitialModes() {
        AtomicInteger notifications = new AtomicInteger();
        var configuration = new FluidPortConfiguration(
            Map.of(),
            notifications::incrementAndGet
        );
        var policy = createPolicy(
            configuration,
            Set.of(
                FluidPortMode.INPUT,
                FluidPortMode.DISABLED
            )
        );

        boolean changed = configuration.changeMode(
            INPUT,
            FluidPortMode.INPUT,
            policy
        );

        assertAll(
            () -> assertTrue(changed),
            () -> assertEquals(
                FluidPortMode.INPUT,
                configuration.getMode(INPUT)
            ),
            () -> assertEquals(
                FluidPortMode.INPUT,
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
        var configuration = new FluidPortConfiguration(
            Map.of(),
            notifications::incrementAndGet
        );
        var policy = createPolicy(
            configuration,
            Set.of(
                FluidPortMode.INPUT,
                FluidPortMode.DISABLED
            )
        );

        assertFalse(configuration.changeMode(
            INPUT,
            FluidPortMode.DISABLED,
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
        var configuration = new FluidPortConfiguration(
            Map.of(
                INPUT,
                FluidPortMode.INPUT
            ),
            () -> {
            }
        );
        var policy = createPolicy(
            configuration,
            Set.of(
                FluidPortMode.INPUT,
                FluidPortMode.OUTPUT
            )
        );
        Map<FluidPortId, FluidPortMode> before = configuration.snapshot();

        assertTrue(configuration.changeMode(
            INPUT,
            FluidPortMode.OUTPUT,
            policy
        ));
        Map<FluidPortId, FluidPortMode> after = configuration.snapshot();

        assertAll(
            () -> assertEquals(
                FluidPortMode.INPUT,
                before.get(INPUT)
            ),
            () -> assertEquals(
                FluidPortMode.OUTPUT,
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
        var configuration = new FluidPortConfiguration(
            Map.of(
                INPUT,
                FluidPortMode.INPUT
            ),
            notifications::incrementAndGet
        );
        var policy = createPolicy(
            configuration,
            Set.of(
                FluidPortMode.INPUT,
                FluidPortMode.OUTPUT,
                FluidPortMode.DISABLED
            )
        );

        assertTrue(configuration.changeMode(
            INPUT,
            FluidPortMode.OUTPUT,
            policy
        ));
        assertTrue(configuration.changeMode(
            INPUT,
            FluidPortMode.DISABLED,
            policy
        ));
        assertFalse(configuration.changeMode(
            INPUT,
            FluidPortMode.DISABLED,
            policy
        ));
        assertTrue(configuration.changeMode(
            INPUT,
            FluidPortMode.INPUT,
            policy
        ));

        assertAll(
            () -> assertEquals(
                FluidPortMode.INPUT,
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
        var configuration = new FluidPortConfiguration(
            Map.of(
                INPUT,
                FluidPortMode.INPUT
            ),
            notifications::incrementAndGet
        );
        var policy = new ConfigurableFluidPortPolicy(
            Map.of(
                INPUT,
                Set.of(
                    FluidPortMode.INPUT,
                    FluidPortMode.OUTPUT
                )
            ),
            configuration::getMode,
            portId -> false
        );

        assertFalse(policy.isAvailable(INPUT));
        assertTrue(configuration.changeMode(
            INPUT,
            FluidPortMode.OUTPUT,
            policy
        ));

        assertAll(
            () -> assertEquals(
                FluidPortMode.OUTPUT,
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
    void shouldSupportChangingToServiceMode() {
        AtomicInteger notifications = new AtomicInteger();
        var configuration = new FluidPortConfiguration(
            Map.of(
                INPUT,
                FluidPortMode.INPUT
            ),
            notifications::incrementAndGet
        );
        var policy = createPolicy(
            configuration,
            Set.of(
                FluidPortMode.INPUT,
                FluidPortMode.SERVICE
            )
        );

        assertTrue(configuration.changeMode(
            INPUT,
            FluidPortMode.SERVICE,
            policy
        ));

        assertAll(
            () -> assertEquals(
                FluidPortMode.SERVICE,
                configuration.getMode(INPUT)
            ),
            () -> assertTrue(policy.allowsExtract(INPUT)),
            () -> assertFalse(policy.allowsInsert(INPUT)),
            () -> assertEquals(
                1,
                notifications.get()
            )
        );
    }

    @Test
    void shouldRejectServiceModeIfNotAllowed() {
        var configuration = new FluidPortConfiguration(
            Map.of(
                INPUT,
                FluidPortMode.INPUT
            ),
            () -> {
            }
        );
        var policy = createPolicy(
            configuration,
            Set.of(
                FluidPortMode.INPUT,
                FluidPortMode.DISABLED
            )
        );

        assertFalse(configuration.changeMode(
            INPUT,
            FluidPortMode.SERVICE,
            policy
        ));
        assertEquals(
            FluidPortMode.INPUT,
            configuration.getMode(INPUT)
        );
    }

    @Test
    void shouldRestoreAllowedModeWithoutNotification() {
        AtomicInteger notifications = new AtomicInteger();
        var configuration = new FluidPortConfiguration(
            Map.of(
                INPUT,
                FluidPortMode.INPUT
            ),
            notifications::incrementAndGet
        );
        var policy = createPolicy(
            configuration,
            Set.of(
                FluidPortMode.INPUT,
                FluidPortMode.OUTPUT
            )
        );

        configuration.restoreModes(
            Map.of(
                INPUT,
                FluidPortMode.OUTPUT
            ),
            policy
        );

        assertAll(
            () -> assertEquals(
                FluidPortMode.OUTPUT,
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
        var configuration = new FluidPortConfiguration(
            Map.of(
                INPUT,
                FluidPortMode.INPUT
            ),
            () -> {
            }
        );
        var policy = createPolicy(
            configuration,
            Set.of(
                FluidPortMode.INPUT,
                FluidPortMode.DISABLED
            )
        );

        configuration.restoreModes(
            Map.of(
                INPUT,
                FluidPortMode.OUTPUT
            ),
            policy
        );
        assertEquals(
            FluidPortMode.INPUT,
            configuration.getMode(INPUT)
        );
    }

    @Test
    void shouldIgnoreUnknownRestoredPort() {
        var configuration = new FluidPortConfiguration(
            Map.of(
                INPUT,
                FluidPortMode.INPUT
            ),
            () -> {
            }
        );
        var policy = createPolicy(
            configuration,
            Set.of(
                FluidPortMode.INPUT,
                FluidPortMode.OUTPUT
            )
        );

        configuration.restoreModes(
            Map.of(
                UNKNOWN,
                FluidPortMode.OUTPUT
            ),
            policy
        );

        assertAll(
            () -> assertEquals(
                FluidPortMode.DISABLED,
                configuration.getMode(UNKNOWN)
            ),
            () -> assertFalse(configuration.snapshot()
                .containsKey(UNKNOWN))
        );
    }

    @Test
    void shouldRestoreRegisteredPortMissingFromInitialModes() {
        var configuration = new FluidPortConfiguration(
            Map.of(),
            () -> {
            }
        );
        var policy = createPolicy(
            configuration,
            Set.of(
                FluidPortMode.INPUT,
                FluidPortMode.OUTPUT
            )
        );

        configuration.restoreModes(
            Map.of(
                INPUT,
                FluidPortMode.OUTPUT
            ),
            policy
        );
        assertEquals(
            FluidPortMode.OUTPUT,
            configuration.getMode(INPUT)
        );
    }

    @Test
    void shouldKeepInitialModesWhenRestoredMapIsEmpty() {
        var configuration = new FluidPortConfiguration(
            Map.of(
                INPUT,
                FluidPortMode.INPUT
            ),
            () -> {
            }
        );
        var policy = createPolicy(
            configuration,
            Set.of(
                FluidPortMode.INPUT,
                FluidPortMode.OUTPUT
            )
        );

        configuration.restoreModes(
            Map.of(),
            policy
        );
        assertEquals(
            FluidPortMode.INPUT,
            configuration.getMode(INPUT)
        );
    }

    @Test
    void shouldKeepPortsOmittedFromPartialRestore() {
        var configuration = new FluidPortConfiguration(
            Map.of(
                INPUT,
                FluidPortMode.INPUT,
                OUTPUT,
                FluidPortMode.OUTPUT
            ),
            () -> {
            }
        );
        var policy = createPolicy(
            configuration,
            Set.of(
                FluidPortMode.INPUT,
                FluidPortMode.DISABLED
            )
        );

        configuration.restoreModes(
            Map.of(
                INPUT,
                FluidPortMode.DISABLED
            ),
            policy
        );

        assertAll(
            () -> assertEquals(
                FluidPortMode.DISABLED,
                configuration.getMode(INPUT)
            ),
            () -> assertEquals(
                FluidPortMode.OUTPUT,
                configuration.getMode(OUTPUT)
            )
        );
    }

    @Test
    void shouldRestoreServiceModeWhenAllowed() {
        var configuration = new FluidPortConfiguration(
            Map.of(
                INPUT,
                FluidPortMode.INPUT
            ),
            () -> {
            }
        );
        var policy = createPolicy(
            configuration,
            Set.of(
                FluidPortMode.INPUT,
                FluidPortMode.SERVICE
            )
        );

        configuration.restoreModes(
            Map.of(
                INPUT,
                FluidPortMode.SERVICE
            ),
            policy
        );
        assertEquals(
            FluidPortMode.SERVICE,
            configuration.getMode(INPUT)
        );
    }

    @Test
    void shouldRejectNullRestoreArguments() {
        var configuration = new FluidPortConfiguration(
            Map.of(
                INPUT,
                FluidPortMode.INPUT
            ),
            () -> {
            }
        );
        var policy = createPolicy(
            configuration,
            Set.of(
                FluidPortMode.INPUT,
                FluidPortMode.OUTPUT
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
        var configuration = new FluidPortConfiguration(
            Map.of(
                INPUT,
                FluidPortMode.INPUT
            ),
            () -> {
            }
        );
        var policy = createPolicy(
            configuration,
            Set.of(
                FluidPortMode.INPUT,
                FluidPortMode.OUTPUT
            )
        );
        Map<FluidPortId, FluidPortMode> malformed = new LinkedHashMap<>();
        malformed.put(
            INPUT,
            FluidPortMode.OUTPUT
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
            FluidPortMode.INPUT,
            configuration.getMode(INPUT)
        );
    }

    @Test
    void shouldRejectNullKeyBeforeChangingState() {
        var configuration = new FluidPortConfiguration(
            Map.of(
                INPUT,
                FluidPortMode.INPUT
            ),
            () -> {
            }
        );
        var policy = createPolicy(
            configuration,
            Set.of(
                FluidPortMode.INPUT,
                FluidPortMode.OUTPUT
            )
        );
        Map<FluidPortId, FluidPortMode> malformed = new LinkedHashMap<>();
        malformed.put(
            INPUT,
            FluidPortMode.OUTPUT
        );
        malformed.put(
            null,
            FluidPortMode.INPUT
        );

        assertThrows(
            NullPointerException.class,
            () -> configuration.restoreModes(
                malformed,
                policy
            )
        );
        assertEquals(
            FluidPortMode.INPUT,
            configuration.getMode(INPUT)
        );
    }

    @Test
    void shouldNotNotifyWhenRestoringMultiplePorts() {
        AtomicInteger notifications = new AtomicInteger();
        var configuration = new FluidPortConfiguration(
            Map.of(
                INPUT,
                FluidPortMode.INPUT,
                OUTPUT,
                FluidPortMode.DISABLED
            ),
            notifications::incrementAndGet
        );
        var policy = new ConfigurableFluidPortPolicy(
            Map.of(
                INPUT,
                Set.of(
                    FluidPortMode.INPUT,
                    FluidPortMode.DISABLED
                ),
                OUTPUT,
                Set.of(
                    FluidPortMode.DISABLED,
                    FluidPortMode.OUTPUT
                )
            ),
            configuration::getMode,
            portId -> true
        );

        configuration.restoreModes(
            Map.of(
                INPUT,
                FluidPortMode.DISABLED,
                OUTPUT,
                FluidPortMode.OUTPUT
            ),
            policy
        );

        assertAll(
            () -> assertEquals(
                FluidPortMode.DISABLED,
                configuration.getMode(INPUT)
            ),
            () -> assertEquals(
                FluidPortMode.OUTPUT,
                configuration.getMode(OUTPUT)
            ),
            () -> assertEquals(
                0,
                notifications.get()
            )
        );
    }

    @Test
    void shouldRejectChangesUsingFixedPolicy() {
        AtomicInteger notifications = new AtomicInteger();
        var configuration = new FluidPortConfiguration(
            Map.of(
                INPUT,
                FluidPortMode.INPUT
            ),
            notifications::incrementAndGet
        );
        var policy = new FixedFluidPortPolicy(
            Map.of(
                INPUT,
                FluidPortMode.INPUT
            ),
            portId -> true
        );

        assertFalse(configuration.changeMode(
            INPUT,
            FluidPortMode.OUTPUT,
            policy
        ));
        configuration.restoreModes(
            Map.of(
                INPUT,
                FluidPortMode.OUTPUT
            ),
            policy
        );

        assertAll(
            () -> assertEquals(
                FluidPortMode.INPUT,
                configuration.getMode(INPUT)
            ),
            () -> assertEquals(
                0,
                notifications.get()
            )
        );
    }
}
