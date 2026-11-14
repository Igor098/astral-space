package ru.greatdragonx.astralspace.common.fluid.port;

import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class FluidPortPolicyTest {

    private static final FluidPortId PORT = new FluidPortId("test_port");

    private @NonNull FluidPortPolicy createPolicy(
        FluidPortMode mode,
        boolean available
    )
    {
        return new FluidPortPolicy() {
            @Override
            public FluidPortMode getMode(@NonNull FluidPortId portId) {
                return PORT.equals(portId) ? mode : FluidPortMode.DISABLED;
            }

            @Override
            public boolean isAvailable(@NonNull FluidPortId portId) {
                return available && PORT.equals(portId);
            }

            @Override
            public boolean canChangeMode(
                @NonNull FluidPortId portId,
                @NonNull FluidPortMode newMode
            )
            {
                return false;
            }
        };
    }

    @Test
    void shouldAllowInsertionForAvailableInput() {
        FluidPortPolicy policy = createPolicy(
            FluidPortMode.INPUT,
            true
        );

        assertTrue(policy.allowsInsert(PORT));
        assertFalse(policy.allowsExtract(PORT));
    }

    @Test
    void shouldAllowExtractionForAvailableOutput() {
        FluidPortPolicy policy = createPolicy(
            FluidPortMode.OUTPUT,
            true
        );

        assertFalse(policy.allowsInsert(PORT));
        assertTrue(policy.allowsExtract(PORT));
    }

    @Test
    void shouldAllowExtractionForAvailableService() {
        FluidPortPolicy policy = createPolicy(
            FluidPortMode.SERVICE,
            true
        );

        assertFalse(policy.allowsInsert(PORT));
        assertTrue(policy.allowsExtract(PORT));
    }

    @Test
    void shouldDenyOperationsForDisabledPort() {
        FluidPortPolicy policy = createPolicy(
            FluidPortMode.DISABLED,
            true
        );

        assertFalse(policy.allowsInsert(PORT));
        assertFalse(policy.allowsExtract(PORT));
    }

    @Test
    void shouldDenyOperationsForUnavailablePort() {
        FluidPortPolicy policy = createPolicy(
            FluidPortMode.OUTPUT,
            false
        );

        assertFalse(policy.allowsInsert(PORT));
        assertFalse(policy.allowsExtract(PORT));
    }

    @Test
    void shouldDenyOperationsForUnknownPort() {
        FluidPortPolicy policy = createPolicy(
            FluidPortMode.OUTPUT,
            true
        );

        FluidPortId unknown = new FluidPortId("unknown");

        assertFalse(policy.allowsInsert(unknown));
        assertFalse(policy.allowsExtract(unknown));
        assertEquals(
            FluidPortMode.DISABLED,
            policy.getMode(unknown)
        );
    }

    @Test
    void shouldNotAllowModeChangeForFixedPolicy() {
        FluidPortPolicy policy = createPolicy(
            FluidPortMode.OUTPUT,
            true
        );

        for (FluidPortMode mode : FluidPortMode.values()) {
            assertFalse(policy.canChangeMode(
                PORT,
                mode
            ));
        }
    }

    @Test
    void shouldRejectNullPortInDefaultMethods() {
        FluidPortPolicy policy = createPolicy(
            FluidPortMode.INPUT,
            true
        );

        assertAll(
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

    @Test
    void shouldSkipModeLookupWhenPortIsUnavailable() {
        AtomicInteger modeChecks = new AtomicInteger();

        FluidPortPolicy policy = new FluidPortPolicy() {
            @Override
            public FluidPortMode getMode(@NonNull FluidPortId portId) {
                modeChecks.incrementAndGet();
                return FluidPortMode.SERVICE;
            }

            @Override
            public boolean isAvailable(@NonNull FluidPortId portId) {
                return false;
            }

            @Override
            public boolean canChangeMode(
                @NonNull FluidPortId portId,
                @NonNull FluidPortMode newMode
            )
            {
                return false;
            }
        };

        assertFalse(policy.allowsInsert(PORT));
        assertFalse(policy.allowsExtract(PORT));
        assertEquals(
            0,
            modeChecks.get()
        );
    }
}
