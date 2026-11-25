package ru.greatdragonx.astralspace.common.energy.port;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class EnergyPortRuleTest {
    private static final EnergyPortId PORT = new EnergyPortId("test.port");

    @Test
    void shouldCreateRuleWithMultipleAllowedModes() {
        EnergyPortRule rule = new EnergyPortRule(
            PORT,
            EnergyPortMode.OUTPUT,
            Set.of(EnergyPortMode.OUTPUT, EnergyPortMode.DISABLED)
        );

        assertAll(
            () -> assertEquals(PORT, rule.portId()),
            () -> assertEquals(EnergyPortMode.OUTPUT, rule.defaultMode()),
            () -> assertEquals(
                Set.of(EnergyPortMode.OUTPUT, EnergyPortMode.DISABLED),
                rule.allowedModes()
            )
        );
    }

    @Test
    void shouldAcceptSingleAllowedMode() {
        EnergyPortRule rule = new EnergyPortRule(
            PORT,
            EnergyPortMode.OUTPUT,
            Set.of(EnergyPortMode.OUTPUT)
        );

        assertEquals(Set.of(EnergyPortMode.OUTPUT), rule.allowedModes());
        assertTrue(rule.allowsMode(EnergyPortMode.OUTPUT));
    }

    @Test
    void shouldAcceptInputAsDefaultMode() {
        EnergyPortRule rule = new EnergyPortRule(
            PORT,
            EnergyPortMode.INPUT,
            Set.of(EnergyPortMode.INPUT, EnergyPortMode.DISABLED)
        );

        assertEquals(EnergyPortMode.INPUT, rule.defaultMode());
        assertTrue(rule.allowsMode(EnergyPortMode.INPUT));
    }

    @Test
    void shouldAcceptDisabledAsDefaultMode() {
        EnergyPortRule rule = new EnergyPortRule(
            PORT,
            EnergyPortMode.DISABLED,
            Set.of(EnergyPortMode.OUTPUT, EnergyPortMode.DISABLED)
        );

        assertEquals(EnergyPortMode.DISABLED, rule.defaultMode());
        assertTrue(rule.allowsMode(EnergyPortMode.DISABLED));
    }

    @Test
    void shouldAcceptAllEnergyPortModes() {
        EnergyPortRule rule = new EnergyPortRule(
            PORT,
            EnergyPortMode.INPUT,
            Set.of(EnergyPortMode.INPUT, EnergyPortMode.OUTPUT, EnergyPortMode.DISABLED)
        );

        assertAll(
            () -> assertTrue(rule.allowsMode(EnergyPortMode.INPUT)),
            () -> assertTrue(rule.allowsMode(EnergyPortMode.OUTPUT)),
            () -> assertTrue(rule.allowsMode(EnergyPortMode.DISABLED))
        );
    }

    @Test
    void shouldRejectNullPortId() {
        assertThrows(
            NullPointerException.class,
            () -> new EnergyPortRule(null, EnergyPortMode.OUTPUT, Set.of(EnergyPortMode.OUTPUT))
        );
    }

    @Test
    void shouldRejectNullDefaultMode() {
        assertThrows(
            NullPointerException.class,
            () -> new EnergyPortRule(PORT, null, Set.of(EnergyPortMode.OUTPUT))
        );
    }

    @Test
    void shouldRejectNullAllowedModes() {
        assertThrows(
            NullPointerException.class,
            () -> new EnergyPortRule(PORT, EnergyPortMode.OUTPUT, null)
        );
    }

    @Test
    void shouldRejectNullElementInAllowedModes() {
        Set<EnergyPortMode> modes = new HashSet<>();
        modes.add(EnergyPortMode.OUTPUT);
        modes.add(null);

        assertThrows(
            NullPointerException.class,
            () -> new EnergyPortRule(PORT, EnergyPortMode.OUTPUT, modes)
        );
    }

    @Test
    void shouldRejectEmptyAllowedModes() {
        assertThrows(
            IllegalArgumentException.class,
            () -> new EnergyPortRule(PORT, EnergyPortMode.OUTPUT, Set.of())
        );
    }

    @Test
    void shouldRejectDefaultModeOutsideAllowedModes() {
        assertThrows(
            IllegalArgumentException.class,
            () -> new EnergyPortRule(
                PORT,
                EnergyPortMode.INPUT,
                Set.of(EnergyPortMode.OUTPUT, EnergyPortMode.DISABLED)
            )
        );
    }

    @Test
    void shouldDefensivelyCopyAllowedModes() {
        Set<EnergyPortMode> modes = EnumSet.of(EnergyPortMode.OUTPUT, EnergyPortMode.DISABLED);
        EnergyPortRule rule = new EnergyPortRule(PORT, EnergyPortMode.OUTPUT, modes);

        modes.remove(EnergyPortMode.OUTPUT);
        modes.add(EnergyPortMode.INPUT);

        assertAll(
            () -> assertEquals(
                Set.of(EnergyPortMode.OUTPUT, EnergyPortMode.DISABLED),
                rule.allowedModes()
            ),
            () -> assertTrue(rule.allowsMode(rule.defaultMode())),
            () -> assertFalse(rule.allowsMode(EnergyPortMode.INPUT))
        );
    }

    @Test
    void shouldExposeUnmodifiableAllowedModes() {
        EnergyPortRule rule = new EnergyPortRule(
            PORT,
            EnergyPortMode.OUTPUT,
            Set.of(EnergyPortMode.OUTPUT, EnergyPortMode.DISABLED)
        );

        assertAll(
            () -> assertThrows(
                UnsupportedOperationException.class,
                () -> rule.allowedModes().add(EnergyPortMode.INPUT)
            ),
            () -> assertThrows(
                UnsupportedOperationException.class,
                () -> rule.allowedModes().remove(EnergyPortMode.OUTPUT)
            ),
            () -> assertThrows(
                UnsupportedOperationException.class,
                () -> rule.allowedModes().clear()
            )
        );
        assertEquals(Set.of(EnergyPortMode.OUTPUT, EnergyPortMode.DISABLED), rule.allowedModes());
    }

    @Test
    void shouldAllowOnlyModesPresentInAllowedModes() {
        EnergyPortRule rule = new EnergyPortRule(
            PORT,
            EnergyPortMode.OUTPUT,
            Set.of(EnergyPortMode.OUTPUT, EnergyPortMode.DISABLED)
        );

        assertAll(
            () -> assertTrue(rule.allowsMode(EnergyPortMode.OUTPUT)),
            () -> assertTrue(rule.allowsMode(EnergyPortMode.DISABLED)),
            () -> assertFalse(rule.allowsMode(EnergyPortMode.INPUT))
        );
    }

    @Test
    void shouldRejectNullModeInAllowsMode() {
        EnergyPortRule rule = new EnergyPortRule(
            PORT,
            EnergyPortMode.OUTPUT,
            Set.of(EnergyPortMode.OUTPUT)
        );

        assertThrows(NullPointerException.class, () -> rule.allowsMode(null));
    }

    @Test
    void shouldCompareRulesByValue() {
        EnergyPortRule first = new EnergyPortRule(
            PORT,
            EnergyPortMode.OUTPUT,
            Set.of(EnergyPortMode.OUTPUT, EnergyPortMode.DISABLED)
        );
        EnergyPortRule equivalent = new EnergyPortRule(
            new EnergyPortId("test.port"),
            EnergyPortMode.OUTPUT,
            EnumSet.of(EnergyPortMode.DISABLED, EnergyPortMode.OUTPUT)
        );
        EnergyPortRule differentPort = new EnergyPortRule(
            new EnergyPortId("test.other"),
            EnergyPortMode.OUTPUT,
            first.allowedModes()
        );
        EnergyPortRule differentDefault = new EnergyPortRule(
            PORT,
            EnergyPortMode.DISABLED,
            first.allowedModes()
        );
        EnergyPortRule differentModes = new EnergyPortRule(
            PORT,
            EnergyPortMode.OUTPUT,
            Set.of(EnergyPortMode.OUTPUT)
        );

        assertAll(
            () -> assertEquals(first, equivalent),
            () -> assertEquals(first.hashCode(), equivalent.hashCode()),
            () -> assertNotEquals(first, differentPort),
            () -> assertNotEquals(first, differentDefault),
            () -> assertNotEquals(first, differentModes)
        );
    }
}
