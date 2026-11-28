package ru.greatdragonx.astralspace.common.energy.port;

import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static ru.greatdragonx.astralspace.common.energy.port.EnergyPortMode.*;
import static ru.greatdragonx.astralspace.common.energy.port.EnergyPortRules.ConfigurationType.CONFIGURABLE;
import static ru.greatdragonx.astralspace.common.energy.port.EnergyPortRules.ConfigurationType.FIXED;

class EnergyPortRulesTest {
    private static final EnergyPortId BACK = new EnergyPortId("test.back");
    private static final EnergyPortId LEFT = new EnergyPortId("test.left");
    private static final EnergyPortId RIGHT = new EnergyPortId("test.right");
    private static final EnergyPortId UNKNOWN = new EnergyPortId("test.unknown");

    private static EnergyPortRule fixed(EnergyPortId id, EnergyPortMode mode) {
        return new EnergyPortRule(id, mode, Set.of(mode));
    }

    private static EnergyPortRule configurable(
        EnergyPortId id,
        EnergyPortMode defaultMode,
        EnergyPortMode... modes
    )
    {
        return new EnergyPortRule(id, defaultMode, Set.of(modes));
    }

    private static EnergyPortRules configurableRules() {
        return new EnergyPortRules(
            CONFIGURABLE, List.of(
            configurable(RIGHT, DISABLED, OUTPUT, DISABLED),
            configurable(BACK, OUTPUT, OUTPUT, DISABLED),
            configurable(LEFT, INPUT, INPUT, DISABLED)
        )
        );
    }

    @Test
    void shouldAcceptFixedPortWithOneAllowedMode() {
        EnergyPortRule rule = fixed(BACK, OUTPUT);
        EnergyPortRules rules = new EnergyPortRules(FIXED, List.of(rule));

        assertEquals(FIXED, rules.configurationType());
        assertEquals(List.of(rule), rules.rules());
    }

    @Test
    void shouldAcceptFixedPortsWithDifferentModes() {
        List<EnergyPortRule> source = List.of(
            fixed(BACK, OUTPUT),
            fixed(LEFT, INPUT),
            fixed(RIGHT, DISABLED)
        );
        EnergyPortRules rules = new EnergyPortRules(FIXED, source);

        assertEquals(source, rules.rules());
        assertEquals(Map.of(BACK, OUTPUT, LEFT, INPUT, RIGHT, DISABLED), rules.defaultModes());
    }

    @Test
    void shouldAcceptConfigurablePortsWithDifferentAllowedModes() {
        EnergyPortRules rules = configurableRules();

        assertEquals(CONFIGURABLE, rules.configurationType());
        assertEquals(3, rules.rules().size());
        assertEquals(Set.of(INPUT, DISABLED), rules.allowedModes().get(LEFT));
        assertEquals(Set.of(OUTPUT, DISABLED), rules.allowedModes().get(BACK));
    }

    @Test
    void shouldAcceptConfigurablePortWithAllThreeModes() {
        EnergyPortRule rule = configurable(BACK, INPUT, INPUT, OUTPUT, DISABLED);
        EnergyPortRules rules = new EnergyPortRules(CONFIGURABLE, List.of(rule));

        assertEquals(List.of(rule), rules.rules());
        assertEquals(Set.of(INPUT, OUTPUT, DISABLED), rules.allowedModes().get(BACK));
    }

    @Test
    void shouldAcceptEmptyFixedConfiguration() {
        EnergyPortRules rules = new EnergyPortRules(FIXED, List.of());

        assertAll(
            () -> assertEquals(FIXED, rules.configurationType()),
            () -> assertTrue(rules.rules().isEmpty()),
            () -> assertTrue(rules.portIds().isEmpty()),
            () -> assertTrue(rules.defaultModes().isEmpty()),
            () -> assertTrue(rules.allowedModes().isEmpty())
        );
    }

    @Test
    void shouldAcceptEmptyConfigurableConfiguration() {
        EnergyPortRules rules = new EnergyPortRules(CONFIGURABLE, List.of());

        assertAll(
            () -> assertEquals(CONFIGURABLE, rules.configurationType()),
            () -> assertTrue(rules.rules().isEmpty()),
            () -> assertTrue(rules.portIds().isEmpty()),
            () -> assertTrue(rules.defaultModes().isEmpty()),
            () -> assertTrue(rules.allowedModes().isEmpty())
        );
    }

    @Test
    void shouldRejectNullConfigurationType() {
        assertThrows(NullPointerException.class, () -> new EnergyPortRules(null, List.of()));
    }

    @Test
    void shouldRejectNullRules() {
        assertThrows(NullPointerException.class, () -> new EnergyPortRules(FIXED, null));
    }

    @Test
    void shouldRejectNullElementInRules() {
        List<EnergyPortRule> source = Arrays.asList(fixed(BACK, OUTPUT), null);

        assertThrows(NullPointerException.class, () -> new EnergyPortRules(FIXED, source));
    }

    @Test
    void shouldRejectRepeatedIdenticalRule() {
        EnergyPortRule rule = fixed(BACK, OUTPUT);

        assertThrows(
            IllegalArgumentException.class,
            () -> new EnergyPortRules(FIXED, List.of(rule, rule))
        );
    }

    @Test
    void shouldRejectDuplicatePortIdWithDifferentRules() {
        // Разные экземпляры идентификатора с одинаковым значением.
        EnergyPortRule first = fixed(BACK, OUTPUT);
        EnergyPortRule second = fixed(new EnergyPortId(BACK.value()), DISABLED);
        assertNotEquals(first, second);

        IllegalArgumentException error = assertThrows(
            IllegalArgumentException.class,
            () -> new EnergyPortRules(FIXED, List.of(first, second))
        );

        assertTrue(error.getMessage().contains(BACK.value()));
    }

    @Test
    void shouldRejectDuplicatePortIdInConfigurableConfiguration() {
        EnergyPortRule first = configurable(BACK, OUTPUT, OUTPUT, DISABLED);
        EnergyPortRule second = configurable(BACK, INPUT, INPUT, DISABLED);

        assertThrows(
            IllegalArgumentException.class,
            () -> new EnergyPortRules(CONFIGURABLE, List.of(first, second))
        );
    }

    @Test
    void shouldRejectFixedPortWithTwoAllowedModes() {
        EnergyPortRule rule = configurable(BACK, OUTPUT, OUTPUT, DISABLED);

        IllegalArgumentException error = assertThrows(
            IllegalArgumentException.class,
            () -> new EnergyPortRules(FIXED, List.of(rule))
        );

        assertAll(
            () -> assertTrue(error.getMessage().contains("FIXED")),
            () -> assertTrue(error.getMessage().contains(BACK.value())),
            () -> assertTrue(error.getMessage().contains("actual: 2"))
        );
    }

    @Test
    void shouldRejectFixedPortWithThreeAllowedModes() {
        EnergyPortRule rule = configurable(BACK, OUTPUT, INPUT, OUTPUT, DISABLED);

        assertThrows(
            IllegalArgumentException.class,
            () -> new EnergyPortRules(FIXED, List.of(rule))
        );
    }

    @Test
    void shouldRejectConfigurablePortWithOneAllowedMode() {
        IllegalArgumentException error = assertThrows(
            IllegalArgumentException.class,
            () -> new EnergyPortRules(CONFIGURABLE, List.of(fixed(BACK, OUTPUT)))
        );

        assertAll(
            () -> assertTrue(error.getMessage().contains("CONFIGURABLE")),
            () -> assertTrue(error.getMessage().contains(BACK.value())),
            () -> assertTrue(error.getMessage().contains("actual: 1"))
        );
    }

    @Test
    void shouldValidateEveryPortInFixedConfiguration() {
        List<EnergyPortRule> source = List.of(
            fixed(BACK, OUTPUT),
            configurable(LEFT, DISABLED, OUTPUT, DISABLED)
        );

        IllegalArgumentException error = assertThrows(
            IllegalArgumentException.class,
            () -> new EnergyPortRules(FIXED, source)
        );

        assertTrue(error.getMessage().contains(LEFT.value()));
    }

    @Test
    void shouldValidateEveryPortInConfigurableConfiguration() {
        List<EnergyPortRule> source = List.of(
            configurable(BACK, OUTPUT, OUTPUT, DISABLED),
            fixed(LEFT, DISABLED)
        );

        IllegalArgumentException error = assertThrows(
            IllegalArgumentException.class,
            () -> new EnergyPortRules(CONFIGURABLE, source)
        );

        assertTrue(error.getMessage().contains(LEFT.value()));
    }

    @Test
    void shouldDefensivelyCopySourceList() {
        EnergyPortRule back = fixed(BACK, OUTPUT);
        EnergyPortRule left = fixed(LEFT, INPUT);
        List<EnergyPortRule> source = new ArrayList<>(List.of(back, left));
        EnergyPortRules rules = new EnergyPortRules(FIXED, source);

        source.clear();
        source.add(fixed(RIGHT, DISABLED));

        assertAll(
            () -> assertEquals(List.of(back, left), rules.rules()),
            () -> assertEquals(List.of(BACK, LEFT), rules.portIds()),
            () -> assertEquals(Map.of(BACK, OUTPUT, LEFT, INPUT), rules.defaultModes()),
            () -> assertEquals(
                Map.of(BACK, Set.of(OUTPUT), LEFT, Set.of(INPUT)),
                rules.allowedModes()
            )
        );
    }

    @Test
    void shouldExposeUnmodifiableRulesList() {
        EnergyPortRules rules = configurableRules();
        List<EnergyPortRule> exposed = rules.rules();

        assertAll(
            () -> assertThrows(UnsupportedOperationException.class, exposed::clear),
            () -> assertThrows(UnsupportedOperationException.class, () -> exposed.remove(0)),
            () -> assertThrows(
                UnsupportedOperationException.class,
                () -> exposed.set(0, fixed(RIGHT, DISABLED))
            )
        );
        assertEquals(3, rules.rules().size());
    }

    @Test
    void shouldPreservePortOrderAndIncludeDisabledPorts() {
        EnergyPortRules rules = configurableRules();

        assertEquals(List.of(RIGHT, BACK, LEFT), rules.portIds());
    }

    @Test
    void shouldReturnUnmodifiablePortIds() {
        List<EnergyPortId> ids = configurableRules().portIds();

        assertAll(
            () -> assertThrows(UnsupportedOperationException.class, () -> ids.add(UNKNOWN)),
            () -> assertThrows(UnsupportedOperationException.class, () -> ids.set(0, UNKNOWN)),
            () -> assertThrows(UnsupportedOperationException.class, ids::clear)
        );
    }

    @Test
    void shouldReturnDefaultModesForEveryPort() {
        EnergyPortRules rules = configurableRules();

        assertEquals(Map.of(RIGHT, DISABLED, BACK, OUTPUT, LEFT, INPUT), rules.defaultModes());
    }

    @Test
    void shouldReturnUnmodifiableDefaultModes() {
        Map<EnergyPortId, EnergyPortMode> modes = configurableRules().defaultModes();

        assertAll(
            () -> assertThrows(UnsupportedOperationException.class, () -> modes.put(BACK, INPUT)),
            () -> assertThrows(UnsupportedOperationException.class, () -> modes.remove(LEFT)),
            () -> assertThrows(UnsupportedOperationException.class, modes::clear)
        );
    }

    @Test
    void shouldReturnAllowedModesForEveryPort() {
        EnergyPortRules rules = configurableRules();

        assertEquals(
            Map.of(
                RIGHT,
                Set.of(OUTPUT, DISABLED),
                BACK,
                Set.of(OUTPUT, DISABLED),
                LEFT,
                Set.of(INPUT, DISABLED)
            ), rules.allowedModes()
        );
    }

    @Test
    void shouldReturnDeeplyUnmodifiableAllowedModes() {
        EnergyPortRules rules = configurableRules();
        Map<EnergyPortId, Set<EnergyPortMode>> modes = rules.allowedModes();

        assertAll(
            () -> assertThrows(
                UnsupportedOperationException.class,
                () -> modes.put(BACK, Set.of(INPUT))
            ),
            () -> assertThrows(UnsupportedOperationException.class, () -> modes.remove(LEFT)),
            () -> assertThrows(UnsupportedOperationException.class, modes::clear),
            () -> assertThrows(
                UnsupportedOperationException.class,
                () -> modes.get(BACK).add(INPUT)
            ),
            () -> assertThrows(
                UnsupportedOperationException.class,
                () -> modes.get(BACK).remove(OUTPUT)
            ),
            () -> assertThrows(UnsupportedOperationException.class, () -> modes.get(BACK).clear())
        );
        assertEquals(Set.of(OUTPUT, DISABLED), rules.allowedModes().get(BACK));
    }

    @Test
    void shouldProvideDefaultsForFixedPolicy() {
        EnergyPortRules rules = new EnergyPortRules(
            FIXED,
            List.of(fixed(BACK, OUTPUT), fixed(LEFT, INPUT), fixed(RIGHT, DISABLED))
        );
        AtomicBoolean available = new AtomicBoolean(true);
        EnergyPortPolicy policy = new FixedEnergyPortPolicy(
            rules.defaultModes(),
            ignored -> available.get()
        );

        assertAll(
            () -> assertTrue(policy.allowsExtract(BACK)),
            () -> assertTrue(policy.allowsInsert(LEFT)),
            () -> assertFalse(policy.allowsInsert(RIGHT)),
            () -> assertFalse(policy.allowsExtract(RIGHT)),
            () -> assertFalse(policy.allowsExtract(UNKNOWN)),
            () -> assertFalse(policy.canChangeMode(BACK, DISABLED))
        );
        available.set(false);
        assertFalse(policy.allowsExtract(BACK));
        assertEquals(OUTPUT, policy.getMode(BACK));
        assertEquals(OUTPUT, rules.defaultModes().get(BACK));
    }

    @Test
    void shouldProvideDefaultsAndAllowedModesForConfigurablePolicy() {
        EnergyPortRules rules = configurableRules();
        AtomicInteger changes = new AtomicInteger();
        EnergyPortConfiguration configuration = new EnergyPortConfiguration(
            rules.defaultModes(),
            ignored -> changes.incrementAndGet()
        );
        AtomicBoolean available = new AtomicBoolean(true);
        EnergyPortPolicy policy = new ConfigurableEnergyPortPolicy(
            rules.allowedModes(),
            configuration::getMode,
            ignored -> available.get()
        );

        assertAll(
            () -> assertEquals(0, changes.get()),
            () -> assertTrue(policy.allowsExtract(BACK)),
            () -> assertTrue(policy.allowsInsert(LEFT)),
            () -> assertFalse(policy.allowsExtract(RIGHT)),
            () -> assertFalse(policy.canChangeMode(BACK, INPUT)),
            () -> assertFalse(policy.canChangeMode(UNKNOWN, OUTPUT))
        );
        assertFalse(configuration.changeMode(BACK, INPUT, policy));
        assertEquals(0, changes.get());
        assertTrue(configuration.changeMode(RIGHT, OUTPUT, policy));
        assertTrue(configuration.changeMode(BACK, DISABLED, policy));
        assertEquals(2, changes.get());
        assertTrue(policy.allowsExtract(RIGHT));
        assertFalse(policy.allowsExtract(BACK));
        available.set(false);
        assertFalse(policy.allowsExtract(RIGHT));
        assertEquals(OUTPUT, policy.getMode(RIGHT));
        // Изменяемая конфигурация не меняет исходные декларативные правила.
        assertEquals(Map.of(RIGHT, DISABLED, BACK, OUTPUT, LEFT, INPUT), rules.defaultModes());
        assertEquals(Set.of(OUTPUT, DISABLED), rules.allowedModes().get(RIGHT));
    }

    @Test
    void shouldCompareRulesByConfigurationTypeAndOrderedValues() {
        EnergyPortRules first = configurableRules();
        EnergyPortRules same = configurableRules();
        List<EnergyPortRule> reordered = new ArrayList<>(first.rules());
        java.util.Collections.swap(reordered, 0, 1);

        assertAll(
            () -> assertEquals(first, same),
            () -> assertEquals(first.hashCode(), same.hashCode()),
            () -> assertNotEquals(first, new EnergyPortRules(CONFIGURABLE, reordered)),
            () -> assertNotEquals(
                new EnergyPortRules(FIXED, List.of()),
                new EnergyPortRules(CONFIGURABLE, List.of())
            )
        );
    }
}
