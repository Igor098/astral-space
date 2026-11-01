package ru.greatdragonx.astralspace.common.energy.port;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class EnergyPortIdTest {

    @Test
    void shouldCreatePortId() {
        EnergyPortId id = new EnergyPortId("north");

        assertEquals(
            "north",
            id.value()
        );
    }

    @Test
    void shouldComparePortIdsByValue() {
        EnergyPortId first = new EnergyPortId("north");
        EnergyPortId second = new EnergyPortId("north");

        assertEquals(
            first,
            second
        );
        assertEquals(
            first.hashCode(),
            second.hashCode()
        );
    }

    @Test
    void shouldDistinguishDifferentPorts() {
        EnergyPortId first = new EnergyPortId("connector_1");
        EnergyPortId second = new EnergyPortId("connector_2");

        assertNotEquals(
            first,
            second
        );
        assertEquals(
            2,
            Set.of(
                    first,
                    second
                )
                .size()
        );
    }

    @Test
    void shouldRejectNullValue() {
        assertThrows(
            NullPointerException.class,
            () -> new EnergyPortId(null)
        );
    }

    @Test
    void shouldRejectBlankValues() {
        assertAll(
            () -> assertThrows(
                IllegalArgumentException.class,
                () -> new EnergyPortId("")
            ),
            () -> assertThrows(
                IllegalArgumentException.class,
                () -> new EnergyPortId("   ")
            )
        );
    }

    @Test
    void shouldRejectLeadingAndTrailingWhitespace() {
        assertAll(
            () -> assertThrows(
                IllegalArgumentException.class,
                () -> new EnergyPortId(" north")
            ),
            () -> assertThrows(
                IllegalArgumentException.class,
                () -> new EnergyPortId("north ")
            )
        );
    }

    @Test
    void shouldRejectUnicodeWhitespaceAtEdges() {
        assertAll(
            () -> assertThrows(
                IllegalArgumentException.class,
                () -> new EnergyPortId("\u2003north")
            ),
            () -> assertThrows(
                IllegalArgumentException.class,
                () -> new EnergyPortId("north\u2003")
            )
        );
    }
}

