package ru.greatdragonx.astralspace.client.gui.components;

import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import ru.greatdragonx.astralspace.common.machine.visual.VisualPortType;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GuiPortEntryTest {
    private static GuiPortModeOption option(String id) {
        return new GuiPortModeOption(id, Component.translatable("gui.astralspace.port_mode." + id));
    }

    @Test
    void shouldKeepTypeCurrentModeAndAvailableModes() {
        GuiPortModeOption disabled = option("disabled");
        GuiPortModeOption output = option("output");
        List<GuiPortModeOption> modes = List.of(disabled, output);

        GuiPortEntry entry = new GuiPortEntry(VisualPortType.ENERGY, "output", modes);

        assertAll(
            () -> assertEquals(VisualPortType.ENERGY, entry.type()),
            () -> assertEquals("output", entry.currentModeId()),
            () -> assertEquals(modes, entry.availableModes()),
            () -> assertSame(output.label(), entry.modeName())
        );
    }

    @Test
    void shouldPreserveOrderOfAvailableModes() {
        GuiPortModeOption disabled = option("disabled");
        GuiPortModeOption input = option("input");
        GuiPortModeOption service = option("service");

        GuiPortEntry entry = new GuiPortEntry(
            VisualPortType.FLUID,
            "service",
            List.of(disabled, input, service)
        );

        assertEquals(List.of(disabled, input, service), entry.availableModes());
    }

    @Test
    void shouldAllowOnlyOneAvailableMode() {
        GuiPortModeOption disabled = option("disabled");
        GuiPortEntry entry = new GuiPortEntry(VisualPortType.ENERGY, "disabled", List.of(disabled));

        assertAll(
            () -> assertEquals(List.of(disabled), entry.availableModes()),
            () -> assertSame(disabled.label(), entry.modeName())
        );
    }

    @Test
    void shouldRejectNullType() {
        NullPointerException error = assertThrows(
            NullPointerException.class,
            () -> new GuiPortEntry(null, "disabled", List.of(option("disabled")))
        );
        assertEquals("type cannot be null", error.getMessage());
    }

    @Test
    void shouldRejectNullCurrentModeId() {
        NullPointerException error = assertThrows(
            NullPointerException.class,
            () -> new GuiPortEntry(VisualPortType.ENERGY, null, List.of(option("disabled")))
        );
        assertEquals("currentModeId cannot be null", error.getMessage());
    }

    @Test
    void shouldRejectNullAvailableModes() {
        NullPointerException error = assertThrows(
            NullPointerException.class,
            () -> new GuiPortEntry(VisualPortType.ENERGY, "disabled", null)
        );
        assertEquals("availableModes cannot be null", error.getMessage());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "  ", "\t", "\n", " \t "})
    void shouldRejectBlankCurrentModeId(String id) {
        IllegalArgumentException error = assertThrows(
            IllegalArgumentException.class,
            () -> new GuiPortEntry(VisualPortType.ENERGY, id, List.of(option("disabled")))
        );
        assertEquals("currentModeId cannot be blank", error.getMessage());
    }

    @ParameterizedTest
    @ValueSource(strings = {" disabled", "disabled ", "\toutput", "service\n"})
    void shouldRejectWhitespaceAtCurrentModeIdEdges(String id) {
        IllegalArgumentException error = assertThrows(
            IllegalArgumentException.class,
            () -> new GuiPortEntry(VisualPortType.FLUID, id, List.of(option("disabled")))
        );
        assertEquals(
            "currentModeId must not have leading or trailing whitespace",
            error.getMessage()
        );
    }

    @Test
    void shouldRejectEmptyAvailableModes() {
        IllegalArgumentException error = assertThrows(
            IllegalArgumentException.class,
            () -> new GuiPortEntry(VisualPortType.ENERGY, "disabled", List.of())
        );
        assertEquals("availableModes cannot be empty", error.getMessage());
    }

    @Test
    void shouldRejectDuplicateIdsEvenWhenLabelsDiffer() {
        GuiPortModeOption first = new GuiPortModeOption("input", Component.literal("Ввод"));
        GuiPortModeOption second = new GuiPortModeOption("input", Component.literal("Приём"));

        IllegalArgumentException error = assertThrows(
            IllegalArgumentException.class,
            () -> new GuiPortEntry(VisualPortType.FLUID, "input", List.of(first, second))
        );
        assertEquals("Duplicate port mode id: input", error.getMessage());
    }

    @Test
    void shouldRejectCurrentModeOutsideAvailableModes() {
        IllegalArgumentException error = assertThrows(
            IllegalArgumentException.class,
            () -> new GuiPortEntry(
                VisualPortType.ENERGY,
                "input",
                List.of(option("disabled"), option("output"))
            )
        );
        assertEquals("Current port mode is not available: input", error.getMessage());
    }

    @Test
    void shouldRejectNullOptionInAvailableModes() {
        List<GuiPortModeOption> modes = new ArrayList<>();
        modes.add(option("disabled"));
        modes.add(null);

        assertThrows(
            NullPointerException.class,
            () -> new GuiPortEntry(VisualPortType.ENERGY, "disabled", modes)
        );
    }

    @Test
    void shouldDefensivelyCopyMutableAvailableModes() {
        GuiPortModeOption disabled = option("disabled");
        GuiPortModeOption output = option("output");
        List<GuiPortModeOption> source = new ArrayList<>(List.of(disabled, output));

        GuiPortEntry entry = new GuiPortEntry(VisualPortType.ENERGY, "output", source);
        source.clear();

        assertAll(
            () -> assertEquals(List.of(disabled, output), entry.availableModes()),
            () -> assertSame(output.label(), entry.modeName())
        );
    }

    @Test
    void shouldReturnUnmodifiableAvailableModes() {
        GuiPortEntry entry = new GuiPortEntry(
            VisualPortType.FLUID,
            "service",
            List.of(option("disabled"), option("service"))
        );

        assertThrows(
            UnsupportedOperationException.class,
            () -> entry.availableModes()
                .add(option("input"))
        );
    }

    @Test
    void shouldFindCurrentModeLabelWhenCurrentModeIsNotFirst() {
        GuiPortModeOption disabled = option("disabled");
        GuiPortModeOption input = option("input");
        GuiPortModeOption service = option("service");

        GuiPortEntry entry = new GuiPortEntry(
            VisualPortType.FLUID,
            "service",
            List.of(disabled, input, service)
        );

        assertSame(service.label(), entry.modeName());
    }

    @Test
    void shouldAllowDifferentIdsWithSameLabel() {
        Component sharedLabel = Component.literal("Один текст");
        GuiPortModeOption first = new GuiPortModeOption("input", sharedLabel);
        GuiPortModeOption second = new GuiPortModeOption("output", sharedLabel);

        GuiPortEntry entry = new GuiPortEntry(
            VisualPortType.FLUID,
            "output",
            List.of(first, second)
        );

        assertEquals(
            2,
            entry.availableModes()
                .size()
        );
        assertSame(sharedLabel, entry.modeName());
    }
}
