package ru.greatdragonx.astralspace.common.fluid.storage;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FluidTankIdTest {

    @Test
    void shouldStoreIdentifierValue() {
        FluidTankId id = new FluidTankId("lava_input");

        assertEquals(
            "lava_input",
            id.value()
        );
    }

    @Test
    void shouldCompareIdsByValue() {
        FluidTankId first = new FluidTankId("lava_input");
        FluidTankId second = new FluidTankId("lava_input");

        assertAll(
            () -> assertEquals(
                first,
                second
            ),
            () -> assertEquals(
                first.hashCode(),
                second.hashCode()
            )
        );
    }

    @Test
    void shouldDistinguishDifferentIdentifiers() {
        assertNotEquals(
            new FluidTankId("lava_input"),
            new FluidTankId("water_input")
        );
    }

    @Test
    void shouldPreserveCase() {
        assertNotEquals(
            new FluidTankId("lava_input"),
            new FluidTankId("Lava_Input")
        );

        assertEquals(
            "Lava_Input",
            new FluidTankId("Lava_Input").value()
        );
    }

    @Test
    void shouldRejectNullValue() {
        assertThrows(
            NullPointerException.class,
            () -> new FluidTankId(null)
        );
    }

    @Test
    void shouldRejectEmptyAndBlankValues() {
        assertAll(
            () -> assertThrows(
                IllegalArgumentException.class,
                () -> new FluidTankId("")
            ),
            () -> assertThrows(
                IllegalArgumentException.class,
                () -> new FluidTankId("   ")
            ),
            () -> assertThrows(
                IllegalArgumentException.class,
                () -> new FluidTankId("\t\n")
            )
        );
    }

    @Test
    void shouldRejectLeadingAndTrailingWhitespace() {
        assertAll(
            () -> assertThrows(
                IllegalArgumentException.class,
                () -> new FluidTankId(" lava")
            ),
            () -> assertThrows(
                IllegalArgumentException.class,
                () -> new FluidTankId("lava ")
            ),
            () -> assertThrows(
                IllegalArgumentException.class,
                () -> new FluidTankId("\tlava\n")
            )
        );
    }

    @Test
    void shouldRejectUnicodeWhitespaceAtEdges() {
        assertAll(
            () -> assertThrows(
                IllegalArgumentException.class,
                () -> new FluidTankId("\u2003lava")
            ),
            () -> assertThrows(
                IllegalArgumentException.class,
                () -> new FluidTankId("lava\u2003")
            ),
            () -> assertThrows(
                IllegalArgumentException.class,
                () -> new FluidTankId("\u2003\u2003")
            )
        );
    }

    @Test
    void shouldNotRejectWhitespaceInsideIdentifier() {
        assertEquals(
            "lava input",
            new FluidTankId("lava input").value()
        );
    }
}
