package ru.greatdragonx.astralspace.common.energy.port;

import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EnergyPortPolicyTest {

    private static final EnergyPortId PORT = new EnergyPortId("test_port");

    private @NonNull EnergyPortPolicy createPolicy(
        EnergyPortMode mode,
        boolean available
    )
    {
        return new EnergyPortPolicy() {
            @Override
            public EnergyPortMode getMode(@NonNull EnergyPortId portId) {
                return PORT.equals(portId) ? mode : EnergyPortMode.DISABLED;
            }

            @Override
            public boolean isAvailable(@NonNull EnergyPortId portId) {
                return available && PORT.equals(portId);
            }

            @Override
            public boolean canChangeMode(
                @NonNull EnergyPortId portId,
                @NonNull EnergyPortMode newMode
            )
            {
                return false;
            }
        };
    }

    @Test
    void shouldAllowInsertionForAvailableInput() {
        EnergyPortPolicy policy = createPolicy(
            EnergyPortMode.INPUT,
            true
        );

        assertTrue(policy.allowsInsert(PORT));
        assertFalse(policy.allowsExtract(PORT));
    }

    @Test
    void shouldAllowExtractionForAvailableOutput() {
        EnergyPortPolicy policy = createPolicy(
            EnergyPortMode.OUTPUT,
            true
        );

        assertFalse(policy.allowsInsert(PORT));
        assertTrue(policy.allowsExtract(PORT));
    }

    @Test
    void shouldDenyOperationsForDisabledPort() {
        EnergyPortPolicy policy = createPolicy(
            EnergyPortMode.DISABLED,
            true
        );

        assertFalse(policy.allowsInsert(PORT));
        assertFalse(policy.allowsExtract(PORT));
    }

    @Test
    void shouldDenyOperationsForUnavailablePort() {
        EnergyPortPolicy policy = createPolicy(
            EnergyPortMode.OUTPUT,
            false
        );

        assertFalse(policy.allowsInsert(PORT));
        assertFalse(policy.allowsExtract(PORT));
    }

    @Test
    void shouldDenyOperationsForUnknownPort() {
        EnergyPortPolicy policy = createPolicy(
            EnergyPortMode.OUTPUT,
            true
        );

        EnergyPortId unknown = new EnergyPortId("unknown");

        assertFalse(policy.allowsInsert(unknown));
        assertFalse(policy.allowsExtract(unknown));
        assertEquals(
            EnergyPortMode.DISABLED,
            policy.getMode(unknown)
        );
    }

    @Test
    void shouldNotAllowModeChangeForFixedPolicy() {
        EnergyPortPolicy policy = createPolicy(
            EnergyPortMode.OUTPUT,
            true
        );

        assertFalse(policy.canChangeMode(
            PORT,
            EnergyPortMode.INPUT
        ));
        assertFalse(policy.canChangeMode(
            PORT,
            EnergyPortMode.OUTPUT
        ));
        assertFalse(policy.canChangeMode(
            PORT,
            EnergyPortMode.DISABLED
        ));
    }
}
