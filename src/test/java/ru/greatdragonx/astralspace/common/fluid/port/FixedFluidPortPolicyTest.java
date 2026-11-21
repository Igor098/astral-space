package ru.greatdragonx.astralspace.common.fluid.port;

import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class FixedFluidPortPolicyTest {

    private static final FluidPortId INPUT = new FluidPortId("input");
    private static final FluidPortId OUTPUT = new FluidPortId("output");
    private static final FluidPortId SERVICE = new FluidPortId("service");
    private static final FluidPortId DISABLED = new FluidPortId("disabled");
    private static final FluidPortId UNKNOWN = new FluidPortId("unknown");

    private static @NonNull FixedFluidPortPolicy createPolicy(Map<FluidPortId, FluidPortMode> modes) {
        return new FixedFluidPortPolicy(
            modes,
            id -> true
        );
    }

    @Test
    void shouldRejectNullConstructorArguments() {
        assertAll(
            () -> assertThrows(
                NullPointerException.class,
                () -> new FixedFluidPortPolicy(
                    null,
                    id -> true
                )
            ),
            () -> assertThrows(
                NullPointerException.class,
                () -> new FixedFluidPortPolicy(
                    Map.of(
                        INPUT,
                        FluidPortMode.INPUT
                    ),
                    null
                )
            )
        );
    }

    @Test
    void shouldRejectNullPortIdInConfiguration() {
        Map<FluidPortId, FluidPortMode> modes = new HashMap<>();
        modes.put(
            null,
            FluidPortMode.INPUT
        );

        assertThrows(
            NullPointerException.class,
            () -> createPolicy(modes)
        );
    }

    @Test
    void shouldRejectNullModeInConfiguration() {
        Map<FluidPortId, FluidPortMode> modes = new HashMap<>();
        modes.put(
            INPUT,
            null
        );

        assertThrows(
            NullPointerException.class,
            () -> createPolicy(modes)
        );
    }

    @Test
    void shouldSupportEmptyConfiguration() {
        var policy = createPolicy(Map.of());

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
    }

    @Test
    void shouldReturnFixedModesForRegisteredPorts() {
        var policy = createPolicy(Map.of(
            INPUT,
            FluidPortMode.INPUT,
            OUTPUT,
            FluidPortMode.OUTPUT,
            SERVICE,
            FluidPortMode.SERVICE,
            DISABLED,
            FluidPortMode.DISABLED
        ));

        assertAll(
            () -> assertEquals(
                FluidPortMode.INPUT,
                policy.getMode(INPUT)
            ),
            () -> assertEquals(
                FluidPortMode.OUTPUT,
                policy.getMode(OUTPUT)
            ),
            () -> assertEquals(
                FluidPortMode.SERVICE,
                policy.getMode(SERVICE)
            ),
            () -> assertEquals(
                FluidPortMode.DISABLED,
                policy.getMode(DISABLED)
            )
        );
    }

    @Test
    void shouldReturnDisabledForUnknownPort() {
        var policy = createPolicy(Map.of(
            INPUT,
            FluidPortMode.INPUT
        ));

        assertEquals(
            FluidPortMode.DISABLED,
            policy.getMode(UNKNOWN)
        );
    }

    @Test
    void shouldAllowOnlyInsertionForInputMode() {
        var policy = createPolicy(Map.of(
            INPUT,
            FluidPortMode.INPUT
        ));

        assertAll(
            () -> assertTrue(policy.allowsInsert(INPUT)),
            () -> assertFalse(policy.allowsExtract(INPUT))
        );
    }

    @Test
    void shouldAllowOnlyExtractionForOutputMode() {
        var policy = createPolicy(Map.of(
            OUTPUT,
            FluidPortMode.OUTPUT
        ));

        assertAll(
            () -> assertFalse(policy.allowsInsert(OUTPUT)),
            () -> assertTrue(policy.allowsExtract(OUTPUT))
        );
    }

    @Test
    void shouldAllowOnlyExtractionForServiceMode() {
        var policy = createPolicy(Map.of(
            SERVICE,
            FluidPortMode.SERVICE
        ));

        assertAll(
            () -> assertFalse(policy.allowsInsert(SERVICE)),
            () -> assertTrue(policy.allowsExtract(SERVICE))
        );
    }

    @Test
    void shouldDenyTransfersForDisabledModeWhileRemainingAvailable() {
        var policy = createPolicy(Map.of(
            DISABLED,
            FluidPortMode.DISABLED
        ));

        assertAll(
            () -> assertTrue(policy.isAvailable(DISABLED)),
            () -> assertFalse(policy.allowsInsert(DISABLED)),
            () -> assertFalse(policy.allowsExtract(DISABLED))
        );
    }

    @Test
    void shouldCheckAvailabilityDynamically() {
        AtomicBoolean available = new AtomicBoolean(false);
        var policy = new FixedFluidPortPolicy(
            Map.of(
                INPUT,
                FluidPortMode.INPUT
            ),
            id -> available.get()
        );

        assertFalse(policy.isAvailable(INPUT));
        assertFalse(policy.allowsInsert(INPUT));
        assertEquals(
            FluidPortMode.INPUT,
            policy.getMode(INPUT)
        );

        available.set(true);
        assertTrue(policy.isAvailable(INPUT));
        assertTrue(policy.allowsInsert(INPUT));

        available.set(false);
        assertFalse(policy.allowsInsert(INPUT));
        assertEquals(
            FluidPortMode.INPUT,
            policy.getMode(INPUT)
        );
    }

    @Test
    void shouldNotCallAvailabilityForUnknownPort() {
        AtomicInteger availabilityCalls = new AtomicInteger();
        var policy = new FixedFluidPortPolicy(
            Map.of(
                INPUT,
                FluidPortMode.INPUT
            ),
            id -> {
                availabilityCalls.incrementAndGet();
                return true;
            }
        );

        assertAll(
            () -> assertFalse(policy.isAvailable(UNKNOWN)),
            () -> assertFalse(policy.allowsInsert(UNKNOWN)),
            () -> assertFalse(policy.allowsExtract(UNKNOWN))
        );
        assertEquals(
            0,
            availabilityCalls.get()
        );
    }

    @Test
    void shouldNotCallAvailabilityInConstructorOrGetMode() {
        AtomicInteger availabilityCalls = new AtomicInteger();
        var policy = new FixedFluidPortPolicy(
            Map.of(
                INPUT,
                FluidPortMode.INPUT
            ),
            id -> {
                availabilityCalls.incrementAndGet();
                return true;
            }
        );

        assertEquals(
            0,
            availabilityCalls.get()
        );
        assertEquals(
            FluidPortMode.INPUT,
            policy.getMode(INPUT)
        );
        assertEquals(
            FluidPortMode.DISABLED,
            policy.getMode(UNKNOWN)
        );
        assertEquals(
            0,
            availabilityCalls.get()
        );
    }

    @Test
    void shouldAlwaysDenyModeChangesIncludingSameMode() {
        var policy = createPolicy(Map.of(
            INPUT,
            FluidPortMode.INPUT
        ));

        for (FluidPortMode mode : FluidPortMode.values()) {
            assertFalse(
                policy.canChangeMode(
                    INPUT,
                    mode
                ),
                "Requested mode: " + mode
            );
        }
        assertFalse(policy.canChangeMode(
            UNKNOWN,
            FluidPortMode.INPUT
        ));
        assertEquals(
            FluidPortMode.INPUT,
            policy.getMode(INPUT)
        );
    }

    @Test
    void shouldNotCallAvailabilityOnModeChange() {
        AtomicInteger availabilityCalls = new AtomicInteger();
        var policy = new FixedFluidPortPolicy(
            Map.of(
                INPUT,
                FluidPortMode.INPUT
            ),
            id -> {
                availabilityCalls.incrementAndGet();
                return false;
            }
        );

        assertFalse(policy.canChangeMode(
            INPUT,
            FluidPortMode.OUTPUT
        ));
        assertFalse(policy.canChangeMode(
            UNKNOWN,
            FluidPortMode.INPUT
        ));
        assertEquals(
            0,
            availabilityCalls.get()
        );
    }

    @Test
    void shouldDefensivelyCopyInputModes() {
        Map<FluidPortId, FluidPortMode> modes = new HashMap<>();
        modes.put(
            INPUT,
            FluidPortMode.INPUT
        );

        var policy = createPolicy(modes);
        modes.put(
            INPUT,
            FluidPortMode.OUTPUT
        );
        modes.put(
            OUTPUT,
            FluidPortMode.OUTPUT
        );
        modes.clear();

        assertAll(
            () -> assertEquals(
                FluidPortMode.INPUT,
                policy.getMode(INPUT)
            ),
            () -> assertTrue(policy.allowsInsert(INPUT)),
            () -> assertEquals(
                FluidPortMode.DISABLED,
                policy.getMode(OUTPUT)
            ),
            () -> assertFalse(policy.isAvailable(OUTPUT))
        );
    }

    @Test
    void shouldRejectNullPortAndModeArguments() {
        var policy = createPolicy(Map.of(
            INPUT,
            FluidPortMode.INPUT
        ));

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
                    INPUT,
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
