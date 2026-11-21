package ru.greatdragonx.astralspace.client.gui.components;

import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;
import ru.greatdragonx.astralspace.common.machine.visual.VisualPortType;

import static org.junit.jupiter.api.Assertions.*;

class GuiPortEntryTest {
    @Test
    void shouldKeepPortTypeAndModeName() {
        Component modeName = Component.literal("Output");
        GuiPortEntry entry = new GuiPortEntry(VisualPortType.ENERGY, modeName);

        assertAll(
            () -> assertEquals(VisualPortType.ENERGY, entry.type()),
            () -> assertSame(modeName, entry.modeName())
        );
    }

    @Test
    void shouldRejectNullType() {
        NullPointerException exception = assertThrows(
            NullPointerException.class,
            () -> new GuiPortEntry(null, Component.literal("Output"))
        );

        assertEquals("type cannot be null", exception.getMessage());
    }

    @Test
    void shouldRejectNullModeName() {
        NullPointerException exception = assertThrows(
            NullPointerException.class,
            () -> new GuiPortEntry(VisualPortType.FLUID, null)
        );

        assertEquals("modeName cannot be null", exception.getMessage());
    }

    @Test
    void shouldUseRecordValueEquality() {
        Component name = Component.literal("Input");
        GuiPortEntry first = new GuiPortEntry(VisualPortType.FLUID, name);
        GuiPortEntry second = new GuiPortEntry(VisualPortType.FLUID, name);

        assertAll(
            () -> assertEquals(first, second),
            () -> assertEquals(first.hashCode(), second.hashCode())
        );
    }
}
