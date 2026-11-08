package ru.greatdragonx.astralspace.common.fluid.port;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FluidPortModeTest {

    @Test
    void shouldAllowOnlyInsertionForInput() {
        FluidPortMode mode = FluidPortMode.INPUT;

        assertAll(
            () -> assertTrue(mode.allowsInsert()),
            () -> assertFalse(mode.allowsExtract())
        );
    }

    @Test
    void shouldAllowOnlyExtractionForOutput() {
        FluidPortMode mode = FluidPortMode.OUTPUT;

        assertAll(
            () -> assertFalse(mode.allowsInsert()),
            () -> assertTrue(mode.allowsExtract())
        );
    }

    @Test
    void shouldAllowOnlyExtractionForService() {
        FluidPortMode mode = FluidPortMode.SERVICE;

        assertAll(
            () -> assertFalse(mode.allowsInsert()),
            () -> assertTrue(mode.allowsExtract())
        );
    }

    @Test
    void shouldDenyBothOperationsWhenDisabled() {
        FluidPortMode mode = FluidPortMode.DISABLED;

        assertAll(
            () -> assertFalse(mode.allowsInsert()),
            () -> assertFalse(mode.allowsExtract())
        );
    }

    @Test
    void shouldHaveExactlyFourSupportedModes() {
        assertArrayEquals(
            new FluidPortMode[]{FluidPortMode.INPUT, FluidPortMode.OUTPUT, FluidPortMode.DISABLED, FluidPortMode.SERVICE},
            FluidPortMode.values()
        );
    }

    @Test
    void shouldNeverAllowInsertionAndExtractionTogether() {
        for (FluidPortMode mode : FluidPortMode.values()) {
            assertFalse(
                mode.allowsInsert() && mode.allowsExtract(),
                "Port mode must not allow both operations: " + mode
            );
        }
    }
}
