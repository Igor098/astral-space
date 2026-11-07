package ru.greatdragonx.astralspace.common.machine.visual.layout;

import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.Test;
import ru.greatdragonx.astralspace.common.machine.MachineSide;
import ru.greatdragonx.astralspace.common.machine.visual.VisualPort;
import ru.greatdragonx.astralspace.common.machine.visual.VisualPortId;
import ru.greatdragonx.astralspace.common.machine.visual.VisualPortType;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PortLayoutTest {
    private static final float DELTA = 0.00001f;
    private final PortLayout layout = new PortLayout();

    private static @NonNull VisualPort port(
        String id,
        VisualPortType type,
        MachineSide side
    )
    {
        return new VisualPort(
            new VisualPortId(id),
            type,
            side
        );
    }

    @Test
    void shouldReturnEmptyListForNoPorts() {
        assertEquals(
            List.of(),
            layout.arrange(List.of())
        );
    }

    @Test
    void shouldPlaceSinglePortAtCenterWithOriginalScale() {
        VisualPort energy = port(
            "energy.back",
            VisualPortType.ENERGY,
            MachineSide.BACK
        );

        List<PortPlacement> placements = layout.arrange(List.of(energy));

        assertEquals(
            1,
            placements.size()
        );
        PortPlacement placement = placements.getFirst();
        assertEquals(
            energy,
            placement.port()
        );
        assertEquals(
            0.0f,
            placement.offsetU(),
            DELTA
        );
        assertEquals(
            0.0f,
            placement.offsetV(),
            DELTA
        );
        assertEquals(
            1.0f,
            placement.scale(),
            DELTA
        );
    }

    @Test
    void shouldPlaceTwoPortsVerticallyInTypeOrder() {
        VisualPort energy = port(
            "energy.back",
            VisualPortType.ENERGY,
            MachineSide.BACK
        );
        VisualPort fluid = port(
            "fluid.back",
            VisualPortType.FLUID,
            MachineSide.BACK
        );

        List<PortPlacement> placements = layout.arrange(List.of(
            fluid,
            energy
        ));

        assertEquals(
            2,
            placements.size()
        );

        PortPlacement upper = placements.get(0);
        assertEquals(
            energy,
            upper.port()
        );
        assertEquals(
            0.0f,
            upper.offsetU(),
            DELTA
        );
        assertEquals(
            0.1375f,
            upper.offsetV(),
            DELTA
        );
        assertEquals(
            0.6f,
            upper.scale(),
            DELTA
        );

        PortPlacement lower = placements.get(1);
        assertEquals(
            fluid,
            lower.port()
        );
        assertEquals(
            0.0f,
            lower.offsetU(),
            DELTA
        );
        assertEquals(
            -0.1375f,
            lower.offsetV(),
            DELTA
        );
        assertEquals(
            0.6f,
            lower.scale(),
            DELTA
        );
    }

    @Test
    void shouldNotDependOnInputOrder() {
        VisualPort energy = port(
            "energy.left",
            VisualPortType.ENERGY,
            MachineSide.LEFT
        );
        VisualPort fluid = port(
            "fluid.left",
            VisualPortType.FLUID,
            MachineSide.LEFT
        );

        List<PortPlacement> first = layout.arrange(List.of(
            energy,
            fluid
        ));
        List<PortPlacement> reversed = layout.arrange(List.of(
            fluid,
            energy
        ));

        assertEquals(
            first,
            reversed
        );
    }

    @Test
    void shouldOrderPortsOfSameTypeById() {
        VisualPort alpha = port(
            "energy.alpha",
            VisualPortType.ENERGY,
            MachineSide.TOP
        );
        VisualPort zeta = port(
            "energy.zeta",
            VisualPortType.ENERGY,
            MachineSide.TOP
        );

        List<PortPlacement> placements = layout.arrange(List.of(
            zeta,
            alpha
        ));

        assertEquals(
            alpha,
            placements.get(0)
                .port()
        );
        assertEquals(
            0.1375f,
            placements.get(0)
                .offsetV(),
            DELTA
        );
        assertEquals(
            zeta,
            placements.get(1)
                .port()
        );
        assertEquals(
            -0.1375f,
            placements.get(1)
                .offsetV(),
            DELTA
        );
    }

    @Test
    void shouldNotModifyInputList() {
        VisualPort energy = port(
            "energy.back",
            VisualPortType.ENERGY,
            MachineSide.BACK
        );
        VisualPort fluid = port(
            "fluid.back",
            VisualPortType.FLUID,
            MachineSide.BACK
        );
        List<VisualPort> input = new ArrayList<>(List.of(
            fluid,
            energy
        ));

        layout.arrange(input);

        assertEquals(
            List.of(
                fluid,
                energy
            ),
            input
        );
    }

    @Test
    void shouldReturnUnmodifiablePlacements() {
        VisualPort energy = port(
            "energy.back",
            VisualPortType.ENERGY,
            MachineSide.BACK
        );
        VisualPort fluid = port(
            "fluid.back",
            VisualPortType.FLUID,
            MachineSide.BACK
        );
        List<PortPlacement> placements = layout.arrange(List.of(
            energy,
            fluid
        ));

        assertThrows(
            UnsupportedOperationException.class,
            () -> placements.clear()
        );
    }

    @Test
    void shouldRejectNullList() {
        NullPointerException exception = assertThrows(
            NullPointerException.class,
            () -> layout.arrange(null)
        );

        assertEquals(
            "ports must not be null",
            exception.getMessage()
        );
    }

    @Test
    void shouldRejectNullFirstPortWithClearMessage() {
        VisualPort energy = port(
            "energy.back",
            VisualPortType.ENERGY,
            MachineSide.BACK
        );
        List<VisualPort> ports = new ArrayList<>();
        ports.add(null);
        ports.add(energy);

        NullPointerException exception = assertThrows(
            NullPointerException.class,
            () -> layout.arrange(ports)
        );

        assertEquals(
            "port must not be null",
            exception.getMessage()
        );
    }

    @Test
    void shouldRejectNullSubsequentPort() {
        VisualPort energy = port(
            "energy.back",
            VisualPortType.ENERGY,
            MachineSide.BACK
        );
        List<VisualPort> ports = new ArrayList<>();
        ports.add(energy);
        ports.add(null);

        NullPointerException exception = assertThrows(
            NullPointerException.class,
            () -> layout.arrange(ports)
        );

        assertEquals(
            "port must not be null",
            exception.getMessage()
        );
    }

    @Test
    void shouldRejectPortsFromDifferentSides() {
        VisualPort energy = port(
            "energy.back",
            VisualPortType.ENERGY,
            MachineSide.BACK
        );
        VisualPort fluid = port(
            "fluid.top",
            VisualPortType.FLUID,
            MachineSide.TOP
        );

        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> layout.arrange(List.of(
                energy,
                fluid
            ))
        );

        assertEquals(
            "All visual ports must belong to the same machine side",
            exception.getMessage()
        );
    }

    @Test
    void shouldRejectSamePortTwice() {
        VisualPort energy = port(
            "energy.back",
            VisualPortType.ENERGY,
            MachineSide.BACK
        );

        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> layout.arrange(List.of(
                energy,
                energy
            ))
        );

        assertEquals(
            "Duplicate visual port id: energy.back",
            exception.getMessage()
        );
    }

    @Test
    void shouldRejectSameIdEvenWhenTypesDiffer() {
        VisualPort energy = port(
            "shared.back",
            VisualPortType.ENERGY,
            MachineSide.BACK
        );
        VisualPort fluid = port(
            "shared.back",
            VisualPortType.FLUID,
            MachineSide.BACK
        );

        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> layout.arrange(List.of(
                energy,
                fluid
            ))
        );

        assertEquals(
            "Duplicate visual port id: shared.back",
            exception.getMessage()
        );
    }

    @Test
    void shouldRejectDuplicateIdBeforeUnsupportedCount() {
        VisualPort energy = port(
            "energy.back",
            VisualPortType.ENERGY,
            MachineSide.BACK
        );
        VisualPort fluid = port(
            "fluid.back",
            VisualPortType.FLUID,
            MachineSide.BACK
        );

        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> layout.arrange(List.of(
                energy,
                fluid,
                energy
            ))
        );

        assertEquals(
            "Duplicate visual port id: energy.back",
            exception.getMessage()
        );
    }

    @Test
    void shouldRejectMoreThanTwoPorts() {
        List<VisualPort> ports = List.of(
            port(
                "energy.back",
                VisualPortType.ENERGY,
                MachineSide.BACK
            ),
            port(
                "fluid.back",
                VisualPortType.FLUID,
                MachineSide.BACK
            ),
            port(
                "item.back",
                VisualPortType.ITEM,
                MachineSide.BACK
            )
        );

        assertThrows(
            UnsupportedOperationException.class,
            () -> layout.arrange(ports)
        );
    }
}
