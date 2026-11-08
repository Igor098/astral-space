package ru.greatdragonx.astralspace.common.fluid.port;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class FluidPortIdTest {

    @Test
    void shouldCreatePortId() {
        FluidPortId id = new FluidPortId("lava_generator.back");

        assertEquals(
            "lava_generator.back",
            id.value()
        );
    }

    @Test
    void shouldComparePortIdsByValue() {
        FluidPortId first = new FluidPortId("lava_generator.back");
        FluidPortId second = new FluidPortId("lava_generator.back");

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
        FluidPortId first = new FluidPortId("connector_1");
        FluidPortId second = new FluidPortId("connector_2");

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
            () -> new FluidPortId(null)
        );
    }

    @Test
    void shouldRejectBlankValues() {
        assertAll(
            () -> assertThrows(
                IllegalArgumentException.class,
                () -> new FluidPortId("")
            ),
            () -> assertThrows(
                IllegalArgumentException.class,
                () -> new FluidPortId("   ")
            )
        );
    }

    @Test
    void shouldRejectLeadingAndTrailingWhitespace() {
        assertAll(
            () -> assertThrows(
                IllegalArgumentException.class,
                () -> new FluidPortId(" lava_generator.back")
            ),
            () -> assertThrows(
                IllegalArgumentException.class,
                () -> new FluidPortId("lava_generator.back ")
            )
        );
    }

    @Test
    void shouldRejectUnicodeWhitespaceAtEdges() {
        assertAll(
            () -> assertThrows(
                IllegalArgumentException.class,
                () -> new FluidPortId("\u2003lava_generator.back")
            ),
            () -> assertThrows(
                IllegalArgumentException.class,
                () -> new FluidPortId("lava_generator.back\u2003")
            )
        );
    }
}
