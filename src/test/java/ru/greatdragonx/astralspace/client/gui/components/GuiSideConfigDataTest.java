package ru.greatdragonx.astralspace.client.gui.components;

import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;
import ru.greatdragonx.astralspace.common.machine.MachineSide;
import ru.greatdragonx.astralspace.common.machine.visual.VisualPortType;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class GuiSideConfigDataTest {
    private static GuiPortEntry energyPort() {
        return new GuiPortEntry(VisualPortType.ENERGY, Component.literal("Output"));
    }

    private static GuiPortEntry fluidPort() {
        return new GuiPortEntry(VisualPortType.FLUID, Component.literal("Input"));
    }

    @Test
    void shouldRejectNullMap() {
        NullPointerException exception = assertThrows(
            NullPointerException.class,
            () -> new GuiSideConfigData(null)
        );

        assertEquals("portsBySide cannot be null", exception.getMessage());
    }

    @Test
    void shouldRejectNullSideKey() {
        Map<MachineSide, List<GuiPortEntry>> ports = new HashMap<>();
        ports.put(null, List.of(energyPort()));

        assertThrows(NullPointerException.class, () -> new GuiSideConfigData(ports));
    }

    @Test
    void shouldRejectNullPortList() {
        Map<MachineSide, List<GuiPortEntry>> ports = new HashMap<>();
        ports.put(MachineSide.BACK, null);

        NullPointerException exception = assertThrows(
            NullPointerException.class,
            () -> new GuiSideConfigData(ports)
        );

        assertEquals("ports cannot be null", exception.getMessage());
    }

    @Test
    void shouldRejectNullPortEntryInList() {
        List<GuiPortEntry> entries = new ArrayList<>();
        entries.add(energyPort());
        entries.add(null);

        assertThrows(
            NullPointerException.class,
            () -> new GuiSideConfigData(Map.of(MachineSide.BACK, entries))
        );
    }

    @Test
    void shouldAllowEmptyMap() {
        GuiSideConfigData data = new GuiSideConfigData(Map.of());

        assertTrue(data.portsBySide()
            .isEmpty());
        for (MachineSide side : MachineSide.values()) {
            assertEquals(List.of(), data.portsForSide(side));
        }
    }

    @Test
    void shouldAllowEmptyListForExistingSide() {
        GuiSideConfigData data = new GuiSideConfigData(Map.of(MachineSide.FRONT, List.of()));

        assertTrue(data.portsBySide()
            .containsKey(MachineSide.FRONT));
        assertEquals(List.of(), data.portsForSide(MachineSide.FRONT));
    }

    @Test
    void shouldReturnEmptyListForMissingSide() {
        GuiSideConfigData data = new GuiSideConfigData(Map.of(
            MachineSide.BACK,
            List.of(energyPort())
        ));

        assertEquals(List.of(), data.portsForSide(MachineSide.FRONT));
    }

    @Test
    void shouldReturnEntriesForRequestedSideOnly() {
        GuiPortEntry energy = energyPort();
        GuiPortEntry fluid = fluidPort();
        GuiSideConfigData data = new GuiSideConfigData(Map.of(
            MachineSide.BACK,
            List.of(energy),
            MachineSide.RIGHT,
            List.of(fluid)
        ));

        assertAll(
            () -> assertEquals(List.of(energy), data.portsForSide(MachineSide.BACK)),
            () -> assertEquals(List.of(fluid), data.portsForSide(MachineSide.RIGHT)),
            () -> assertEquals(List.of(), data.portsForSide(MachineSide.TOP))
        );
    }

    @Test
    void shouldPreserveOrderOfPortsWithinSide() {
        GuiPortEntry energy = energyPort();
        GuiPortEntry fluid = fluidPort();
        GuiSideConfigData data = new GuiSideConfigData(Map.of(
            MachineSide.RIGHT,
            List.of(fluid, energy)
        ));

        assertEquals(List.of(fluid, energy), data.portsForSide(MachineSide.RIGHT));
    }

    @Test
    void shouldCopyOriginalMap() {
        GuiPortEntry energy = energyPort();
        Map<MachineSide, List<GuiPortEntry>> source = new EnumMap<>(MachineSide.class);
        source.put(MachineSide.BACK, List.of(energy));

        GuiSideConfigData data = new GuiSideConfigData(source);
        source.clear();
        source.put(MachineSide.RIGHT, List.of(fluidPort()));

        assertAll(
            () -> assertEquals(List.of(energy), data.portsForSide(MachineSide.BACK)),
            () -> assertEquals(List.of(), data.portsForSide(MachineSide.RIGHT))
        );
    }

    @Test
    void shouldCopyNestedMutableList() {
        GuiPortEntry energy = energyPort();
        List<GuiPortEntry> sourceList = new ArrayList<>();
        sourceList.add(energy);

        GuiSideConfigData data = new GuiSideConfigData(Map.of(MachineSide.BACK, sourceList));

        sourceList.clear();
        sourceList.add(fluidPort());

        assertEquals(List.of(energy), data.portsForSide(MachineSide.BACK));
    }

    @Test
    void shouldExposeUnmodifiableMap() {
        GuiSideConfigData data = new GuiSideConfigData(Map.of(
            MachineSide.BACK,
            List.of(energyPort())
        ));

        assertThrows(
            UnsupportedOperationException.class,
            () -> data.portsBySide()
                .put(MachineSide.FRONT, List.of())
        );
    }

    @Test
    void shouldExposeUnmodifiableNestedListFromMap() {
        GuiSideConfigData data = new GuiSideConfigData(Map.of(
            MachineSide.BACK,
            new ArrayList<>(List.of(energyPort()))
        ));

        assertThrows(
            UnsupportedOperationException.class,
            () -> data.portsBySide()
                .get(MachineSide.BACK)
                .clear()
        );
    }

    @Test
    void shouldExposeUnmodifiableListFromPortsForSide() {
        GuiSideConfigData data = new GuiSideConfigData(Map.of(
            MachineSide.BACK,
            List.of(energyPort())
        ));

        assertThrows(
            UnsupportedOperationException.class,
            () -> data.portsForSide(MachineSide.BACK)
                .add(fluidPort())
        );
    }

    @Test
    void shouldRejectNullSideLookup() {
        GuiSideConfigData data = new GuiSideConfigData(Map.of());

        NullPointerException exception = assertThrows(
            NullPointerException.class,
            () -> data.portsForSide(null)
        );

        assertEquals("side cannot be null", exception.getMessage());
    }
}
