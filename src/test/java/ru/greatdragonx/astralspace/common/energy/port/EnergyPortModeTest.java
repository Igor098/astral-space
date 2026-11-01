package ru.greatdragonx.astralspace.common.energy.port;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EnergyPortModeTest {

    @Test
    void shouldAllowOnlyInsertionForInput() {
        EnergyPortMode mode = EnergyPortMode.INPUT;

        assertAll(
            () -> assertTrue(mode.allowsInsert()),
            () -> assertFalse(mode.allowsExtract())
        );
    }

    @Test
    void shouldAllowOnlyExtractionForOutput() {
        EnergyPortMode mode = EnergyPortMode.OUTPUT;

        assertAll(
            () -> assertFalse(mode.allowsInsert()),
            () -> assertTrue(mode.allowsExtract())
        );
    }

    @Test
    void shouldDenyBothOperationsWhenDisabled() {
        EnergyPortMode mode = EnergyPortMode.DISABLED;

        assertAll(
            () -> assertFalse(mode.allowsInsert()),
            () -> assertFalse(mode.allowsExtract())
        );
    }

    @Test
    void shouldHaveExactlyThreeSupportedModes() {
        assertArrayEquals(
            new EnergyPortMode[]{EnergyPortMode.INPUT, EnergyPortMode.OUTPUT, EnergyPortMode.DISABLED},
            EnergyPortMode.values()
        );
    }

    @Test
    void shouldNeverAllowInsertionAndExtractionTogether() {
        for (EnergyPortMode mode : EnergyPortMode.values()) {
            assertFalse(
                mode.allowsInsert() && mode.allowsExtract(),
                "Port mode must not allow both operations: " + mode
            );
        }
    }
}
