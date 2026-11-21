package ru.greatdragonx.astralspace.common.machine.visual.layout;

import org.junit.jupiter.api.Test;
import ru.greatdragonx.astralspace.common.machine.MachineSide;
import ru.greatdragonx.astralspace.common.machine.visual.VisualPort;
import ru.greatdragonx.astralspace.common.machine.visual.VisualPortId;
import ru.greatdragonx.astralspace.common.machine.visual.VisualPortType;

import static org.junit.jupiter.api.Assertions.*;

class PortPlacementTest {
    private static final VisualPort PORT = new VisualPort(
        new VisualPortId("energy.back"),
        VisualPortType.ENERGY,
        MachineSide.BACK
    );

    @Test
    void shouldStoreValidPlacement() {
        PortPlacement placement = new PortPlacement(
            PORT,
            0.0f,
            -0.1375f,
            0.6f
        );

        assertEquals(
            PORT,
            placement.port()
        );
        assertEquals(
            0.0f,
            placement.offsetU()
        );
        assertEquals(
            -0.1375f,
            placement.offsetV()
        );
        assertEquals(
            0.6f,
            placement.scale()
        );
    }

    @Test
    void shouldRejectNullPort() {
        assertThrows(
            NullPointerException.class,
            () -> new PortPlacement(
                null,
                0.0f,
                0.0f,
                1.0f
            )
        );
    }

    @Test
    void shouldRejectNonFiniteHorizontalOffset() {
        assertAll(
            () -> assertThrows(
                IllegalArgumentException.class,
                () -> new PortPlacement(
                    PORT,
                    Float.NaN,
                    0.0f,
                    1.0f
                )
            ),
            () -> assertThrows(
                IllegalArgumentException.class,
                () -> new PortPlacement(
                    PORT,
                    Float.POSITIVE_INFINITY,
                    0.0f,
                    1.0f
                )
            ),
            () -> assertThrows(
                IllegalArgumentException.class,
                () -> new PortPlacement(
                    PORT,
                    Float.NEGATIVE_INFINITY,
                    0.0f,
                    1.0f
                )
            )
        );
    }

    @Test
    void shouldRejectNonFiniteVerticalOffset() {
        assertAll(
            () -> assertThrows(
                IllegalArgumentException.class,
                () -> new PortPlacement(
                    PORT,
                    0.0f,
                    Float.NaN,
                    1.0f
                )
            ),
            () -> assertThrows(
                IllegalArgumentException.class,
                () -> new PortPlacement(
                    PORT,
                    0.0f,
                    Float.POSITIVE_INFINITY,
                    1.0f
                )
            ),
            () -> assertThrows(
                IllegalArgumentException.class,
                () -> new PortPlacement(
                    PORT,
                    0.0f,
                    Float.NEGATIVE_INFINITY,
                    1.0f
                )
            )
        );
    }

    @Test
    void shouldRejectZeroOrNegativeScale() {
        assertAll(
            () -> assertThrows(
                IllegalArgumentException.class,
                () -> new PortPlacement(
                    PORT,
                    0.0f,
                    0.0f,
                    0.0f
                )
            ),
            () -> assertThrows(
                IllegalArgumentException.class,
                () -> new PortPlacement(
                    PORT,
                    0.0f,
                    0.0f,
                    -0.6f
                )
            )
        );
    }

    @Test
    void shouldRejectNonFiniteScale() {
        assertAll(
            () -> assertThrows(
                IllegalArgumentException.class,
                () -> new PortPlacement(
                    PORT,
                    0.0f,
                    0.0f,
                    Float.NaN
                )
            ),
            () -> assertThrows(
                IllegalArgumentException.class,
                () -> new PortPlacement(
                    PORT,
                    0.0f,
                    0.0f,
                    Float.POSITIVE_INFINITY
                )
            ),
            () -> assertThrows(
                IllegalArgumentException.class,
                () -> new PortPlacement(
                    PORT,
                    0.0f,
                    0.0f,
                    Float.NEGATIVE_INFINITY
                )
            )
        );
    }
}
