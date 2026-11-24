package ru.greatdragonx.astralspace.client.gui.components;

import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class GuiPortModeOptionTest {
    @Test
    void shouldCreateOptionWithValidIdAndLabel() {
        Component label = Component.translatable("gui.astralspace.port_mode.service");

        GuiPortModeOption option = new GuiPortModeOption("service", label);

        assertEquals("service", option.id());
        assertSame(label, option.label());
    }

    @ParameterizedTest
    @ValueSource(strings = {"disabled", "input", "output", "service", "custom-mode", "a.b"})
    void shouldAcceptValidIds(String id) {
        GuiPortModeOption option = new GuiPortModeOption(id, Component.literal("Mode"));

        assertEquals(id, option.id());
    }

    @Test
    void shouldRejectNullId() {
        NullPointerException exception = assertThrows(
            NullPointerException.class,
            () -> new GuiPortModeOption(null, Component.literal("Mode"))
        );

        assertEquals("id cannot be null", exception.getMessage());
    }

    @Test
    void shouldRejectNullLabel() {
        NullPointerException exception = assertThrows(
            NullPointerException.class,
            () -> new GuiPortModeOption("service", null)
        );

        assertEquals("label cannot be null", exception.getMessage());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "  ", "\t", "\n", " \t \n "})
    void shouldRejectBlankIds(String id) {
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> new GuiPortModeOption(id, Component.literal("Mode"))
        );

        assertEquals("id cannot be blank", exception.getMessage());
    }

    @ParameterizedTest
    @ValueSource(strings = {" service", "service ", " service ", "\tservice", "service\n", "\nservice\t"})
    void shouldRejectLeadingOrTrailingWhitespace(String id) {
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> new GuiPortModeOption(id, Component.literal("Mode"))
        );

        assertEquals("id must not have leading or trailing whitespace", exception.getMessage());
    }

    @Test
    void shouldPreserveIdWithoutNormalization() {
        GuiPortModeOption option = new GuiPortModeOption(
            "custom service",
            Component.literal("Custom service")
        );

        assertEquals("custom service", option.id());
    }
}
