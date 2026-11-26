package ru.greatdragonx.astralspace.common.energy.generation;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;
import ru.greatdragonx.astralspace.common.energy.port.*;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static ru.greatdragonx.astralspace.common.energy.port.EnergyPortMode.*;
import static ru.greatdragonx.astralspace.common.energy.port.EnergyPortRules.ConfigurationType.CONFIGURABLE;
import static ru.greatdragonx.astralspace.common.energy.port.EnergyPortRules.ConfigurationType.FIXED;

class GeneratorConfigurationTest {
    private static final EnergyPortId BACK = new EnergyPortId("test.back");
    private static final EnergyPortId LEFT = new EnergyPortId("test.left");
    private static final EnergyPortId RIGHT = new EnergyPortId("test.right");
    private static final GeneratorEnergySpec ENERGY = new GeneratorEnergySpec(10_000L, 80, 40);

    private static EnergyPortRule fixed(EnergyPortId id, EnergyPortMode mode) {
        return new EnergyPortRule(id, mode, Set.of(mode));
    }

    private static EnergyPortRule configurable(
        EnergyPortId id,
        EnergyPortMode mode,
        EnergyPortMode... allowed
    )
    {
        return new EnergyPortRule(id, mode, Set.of(allowed));
    }

    private static EnergyPortRules rules(
        EnergyPortRules.ConfigurationType type,
        EnergyPortRule... rules
    )
    {
        return new EnergyPortRules(type, List.of(rules));
    }

    private static EnergyPortPoint point(EnergyPortId id, Direction face) {
        return new EnergyPortPoint(id, BlockPos.ZERO, face);
    }

    private static EnergyPortTopology topology(EnergyPortPoint... points) {
        return new EnergyPortTopology(List.of(points));
    }

    private static EnergyPortRules fixedRules() {
        return rules(FIXED, fixed(BACK, OUTPUT), fixed(LEFT, DISABLED));
    }

    private static EnergyPortTopology twoPortTopology() {
        return topology(point(BACK, Direction.SOUTH), point(LEFT, Direction.EAST));
    }

    private static GeneratorConfiguration fixedConfiguration() {
        return new GeneratorConfiguration(ENERGY, fixedRules(), twoPortTopology());
    }

    @Test
    void shouldAcceptFixedOutputPort() {
        EnergyPortRules rules = rules(FIXED, fixed(BACK, OUTPUT));
        EnergyPortTopology topology = topology(point(BACK, Direction.SOUTH));

        GeneratorConfiguration configuration = new GeneratorConfiguration(ENERGY, rules, topology);

        assertAll(
            () -> assertEquals(ENERGY, configuration.energy()),
            () -> assertEquals(40, configuration.energy().generationPerTick()),
            () -> assertEquals(rules, configuration.portRules()),
            () -> assertEquals(topology, configuration.topology())
        );
    }

    @Test
    void shouldAcceptFixedOutputAndDisabledPorts() {
        GeneratorConfiguration configuration = fixedConfiguration();

        assertAll(
            () -> assertEquals(FIXED, configuration.portRules().configurationType()),
            () -> assertEquals(
                Map.of(BACK, OUTPUT, LEFT, DISABLED),
                configuration.portRules().defaultModes()
            ),
            () -> assertEquals(List.of(BACK, LEFT), configuration.topology().portIds())
        );
    }

    @Test
    void shouldAcceptFixedConfigurationWithAllPortsDisabled() {
        EnergyPortRules rules = rules(FIXED, fixed(BACK, DISABLED), fixed(LEFT, DISABLED));
        GeneratorConfiguration configuration = new GeneratorConfiguration(
            ENERGY,
            rules,
            twoPortTopology()
        );

        assertEquals(
            Map.of(BACK, DISABLED, LEFT, DISABLED),
            configuration.portRules().defaultModes()
        );
    }

    @Test
    void shouldAcceptConfigurableOutputAndDisabledDefaults() {
        EnergyPortRules rules = rules(
            CONFIGURABLE,
            configurable(BACK, OUTPUT, OUTPUT, DISABLED),
            configurable(LEFT, DISABLED, OUTPUT, DISABLED)
        );

        GeneratorConfiguration configuration = new GeneratorConfiguration(
            ENERGY,
            rules,
            twoPortTopology()
        );

        assertAll(
            () -> assertEquals(CONFIGURABLE, configuration.portRules().configurationType()),
            () -> assertEquals(
                Map.of(BACK, OUTPUT, LEFT, DISABLED),
                configuration.portRules().defaultModes()
            ),
            () -> assertEquals(
                Map.of(BACK, Set.of(OUTPUT, DISABLED), LEFT, Set.of(OUTPUT, DISABLED)),
                configuration.portRules().allowedModes()
            )
        );
    }

    @Test
    void shouldAcceptConfigurableConfigurationWithAllDefaultsDisabled() {
        EnergyPortRules rules = rules(
            CONFIGURABLE,
            configurable(BACK, DISABLED, OUTPUT, DISABLED),
            configurable(LEFT, DISABLED, OUTPUT, DISABLED)
        );

        GeneratorConfiguration configuration = new GeneratorConfiguration(
            ENERGY,
            rules,
            twoPortTopology()
        );

        assertEquals(
            Map.of(BACK, DISABLED, LEFT, DISABLED),
            configuration.portRules().defaultModes()
        );
    }

    @Test
    void shouldAcceptEmptyFixedConfiguration() {
        GeneratorConfiguration configuration = new GeneratorConfiguration(
            ENERGY,
            rules(FIXED),
            topology()
        );

        assertAll(
            () -> assertTrue(configuration.portRules().portIds().isEmpty()),
            () -> assertTrue(configuration.topology().portIds().isEmpty()),
            () -> assertEquals(FIXED, configuration.portRules().configurationType())
        );
    }

    @Test
    void shouldAcceptEmptyConfigurableConfiguration() {
        GeneratorConfiguration configuration = new GeneratorConfiguration(
            ENERGY,
            rules(CONFIGURABLE),
            topology()
        );

        assertAll(
            () -> assertTrue(configuration.portRules().portIds().isEmpty()),
            () -> assertTrue(configuration.topology().portIds().isEmpty()),
            () -> assertEquals(CONFIGURABLE, configuration.portRules().configurationType())
        );
    }

    @Test
    void shouldAcceptMatchingIdsInDifferentOrdersWithoutReordering() {
        EnergyPortTopology reversed = topology(
            point(LEFT, Direction.EAST),
            point(BACK, Direction.SOUTH)
        );
        GeneratorConfiguration configuration = new GeneratorConfiguration(
            ENERGY,
            fixedRules(),
            reversed
        );

        assertAll(
            () -> assertEquals(List.of(BACK, LEFT), configuration.portRules().portIds()),
            () -> assertEquals(List.of(LEFT, BACK), configuration.topology().portIds())
        );
    }

    @Test
    void shouldAcceptMultiblockTopologyWithSameOutwardFace() {
        EnergyPortTopology topology = topology(
            new EnergyPortPoint(
                BACK,
                new BlockPos(-1, 2, 3),
                Direction.SOUTH
            ),
            new EnergyPortPoint(LEFT, new BlockPos(1, 2, 3), Direction.SOUTH)
        );

        GeneratorConfiguration configuration = new GeneratorConfiguration(
            ENERGY,
            fixedRules(),
            topology
        );

        assertEquals(topology, configuration.topology());
    }

    @Test
    void shouldNotRestrictOutputLimitToBufferCapacity() {
        GeneratorEnergySpec energy = new GeneratorEnergySpec(10L, 80, 10);
        GeneratorConfiguration configuration = new GeneratorConfiguration(
            energy,
            fixedRules(),
            twoPortTopology()
        );

        assertEquals(energy, configuration.energy());
    }

    @Test
    void shouldRejectNullEnergySpec() {
        assertThrows(
            NullPointerException.class,
            () -> new GeneratorConfiguration(null, fixedRules(), twoPortTopology())
        );
    }

    @Test
    void shouldRejectNullPortRules() {
        assertThrows(
            NullPointerException.class,
            () -> new GeneratorConfiguration(ENERGY, null, twoPortTopology())
        );
    }

    @Test
    void shouldRejectNullTopology() {
        assertThrows(
            NullPointerException.class,
            () -> new GeneratorConfiguration(ENERGY, fixedRules(), null)
        );
    }

    @Test
    void shouldRejectRuleWithoutPhysicalPointEvenWhenDisabled() {
        EnergyPortTopology topology = topology(point(BACK, Direction.SOUTH));

        assertThrows(
            IllegalArgumentException.class,
            () -> new GeneratorConfiguration(ENERGY, fixedRules(), topology)
        );
    }

    @Test
    void shouldRejectPhysicalPointWithoutRule() {
        EnergyPortRules rules = rules(FIXED, fixed(BACK, OUTPUT));

        assertThrows(
            IllegalArgumentException.class,
            () -> new GeneratorConfiguration(ENERGY, rules, twoPortTopology())
        );
    }

    @Test
    void shouldRejectDifferentIdSetsOfSameSize() {
        EnergyPortTopology topology = topology(
            point(BACK, Direction.SOUTH),
            point(RIGHT, Direction.WEST)
        );

        assertThrows(
            IllegalArgumentException.class,
            () -> new GeneratorConfiguration(ENERGY, fixedRules(), topology)
        );
    }

    @Test
    void shouldRejectNonEmptyRulesWithEmptyTopology() {
        assertThrows(
            IllegalArgumentException.class,
            () -> new GeneratorConfiguration(ENERGY, fixedRules(), topology())
        );
    }

    @Test
    void shouldRejectEmptyRulesWithNonEmptyTopology() {
        assertThrows(
            IllegalArgumentException.class,
            () -> new GeneratorConfiguration(ENERGY, rules(FIXED), twoPortTopology())
        );
    }

    @Test
    void shouldRejectFixedInputPort() {
        EnergyPortRules rules = rules(FIXED, fixed(BACK, INPUT));
        EnergyPortTopology topology = topology(point(BACK, Direction.SOUTH));

        assertThrows(
            IllegalArgumentException.class,
            () -> new GeneratorConfiguration(ENERGY, rules, topology)
        );
    }

    @Test
    void shouldRejectConfigurableInputDefault() {
        EnergyPortRules rules = rules(CONFIGURABLE, configurable(BACK, INPUT, INPUT, DISABLED));
        EnergyPortTopology topology = topology(point(BACK, Direction.SOUTH));

        assertThrows(
            IllegalArgumentException.class,
            () -> new GeneratorConfiguration(ENERGY, rules, topology)
        );
    }

    @Test
    void shouldRejectInputAllowedButNotDefault() {
        EnergyPortRules rules = rules(
            CONFIGURABLE,
            configurable(BACK, OUTPUT, INPUT, OUTPUT, DISABLED)
        );
        EnergyPortTopology topology = topology(point(BACK, Direction.SOUTH));

        assertThrows(
            IllegalArgumentException.class,
            () -> new GeneratorConfiguration(ENERGY, rules, topology)
        );
    }

    @Test
    void shouldRejectInputAllowedInLaterRuleWithDisabledDefault() {
        EnergyPortRules rules = rules(
            CONFIGURABLE,
            configurable(BACK, OUTPUT, OUTPUT, DISABLED),
            configurable(LEFT, DISABLED, INPUT, DISABLED)
        );

        assertThrows(
            IllegalArgumentException.class,
            () -> new GeneratorConfiguration(ENERGY, rules, twoPortTopology())
        );
    }

    @Test
    void shouldRemainUnchangedAfterMutatingOriginalCollectionsAndPosition() {
        Set<EnergyPortMode> modes = EnumSet.of(OUTPUT, DISABLED);
        List<EnergyPortRule> sourceRules = new ArrayList<>(List.of(new EnergyPortRule(
            BACK,
            OUTPUT,
            modes
        )));
        BlockPos.MutableBlockPos offset = new BlockPos.MutableBlockPos(-1, 2, 3);
        List<EnergyPortPoint> sourcePoints = new ArrayList<>(List.of(new EnergyPortPoint(
            BACK,
            offset,
            Direction.SOUTH
        )));

        GeneratorConfiguration configuration = new GeneratorConfiguration(
            ENERGY,
            new EnergyPortRules(CONFIGURABLE, sourceRules),
            new EnergyPortTopology(sourcePoints)
        );
        int originalHash = configuration.hashCode();

        modes.add(INPUT);
        modes.remove(OUTPUT);
        sourceRules.clear();
        sourcePoints.clear();
        offset.set(10, 20, 30);

        assertAll(
            () -> assertEquals(List.of(BACK), configuration.portRules().portIds()),
            () -> assertEquals(List.of(BACK), configuration.topology().portIds()),
            () -> assertEquals(
                Set.of(OUTPUT, DISABLED),
                configuration.portRules().allowedModes().get(BACK)
            ),
            () -> assertEquals(
                new BlockPos(-1, 2, 3),
                configuration.topology().points().getFirst().localOffset()
            ),
            () -> assertEquals(originalHash, configuration.hashCode())
        );
    }

    @Test
    void shouldCompareConfigurationsByAllThreeComponents() {
        GeneratorConfiguration first = fixedConfiguration();
        GeneratorConfiguration equal = new GeneratorConfiguration(
            new GeneratorEnergySpec(
            10_000L,
            80,
            40
        ), fixedRules(), twoPortTopology()
        );
        GeneratorConfiguration differentEnergy = new GeneratorConfiguration(
            new GeneratorEnergySpec(120_000L,
            120,
            60
        ), fixedRules(), twoPortTopology()
        );
        GeneratorConfiguration differentRules = new GeneratorConfiguration(
            ENERGY,
            rules(FIXED, fixed(BACK, DISABLED), fixed(LEFT, OUTPUT)),
            twoPortTopology()
        );
        GeneratorConfiguration differentTopology = new GeneratorConfiguration(
            ENERGY,
            fixedRules(),
            topology(point(BACK, Direction.NORTH), point(LEFT, Direction.EAST))
        );

        assertAll(
            () -> assertEquals(first, equal),
            () -> assertEquals(first.hashCode(), equal.hashCode()),
            () -> assertNotEquals(first, differentEnergy),
            () -> assertNotEquals(first, differentRules),
            () -> assertNotEquals(first, differentTopology)
        );
    }

    @Test
    void shouldCompareConfigurationsWithDifferentNominalGeneration() {
        GeneratorConfiguration first = fixedConfiguration();
        GeneratorConfiguration differentGeneration = new GeneratorConfiguration(
            new GeneratorEnergySpec(10_000L, 80, 120),
            fixedRules(),
            twoPortTopology()
        );

        assertAll(
            () -> assertEquals(120, differentGeneration.energy().generationPerTick()),
            () -> assertEquals(
                first.energy().energyCapacity(),
                differentGeneration.energy().energyCapacity()
            ),
            () -> assertEquals(
                first.energy().maxOutputPerTick(),
                differentGeneration.energy().maxOutputPerTick()
            ),
            () -> assertEquals(first.portRules(), differentGeneration.portRules()),
            () -> assertEquals(first.topology(), differentGeneration.topology()),
            () -> assertNotEquals(first, differentGeneration)
        );
    }

    @Test
    void shouldRejectInputDuringModeChangesAndRestorationWithConfiguredPolicy() {
        GeneratorConfiguration generator = new GeneratorConfiguration(
            ENERGY, rules(
            CONFIGURABLE,
            configurable(BACK, OUTPUT, OUTPUT, DISABLED),
            configurable(LEFT, DISABLED, OUTPUT, DISABLED)
        ), twoPortTopology()
        );
        EnergyPortConfiguration runtime = new EnergyPortConfiguration(
            generator.portRules().defaultModes(), ignored -> {
        }
        );
        ConfigurableEnergyPortPolicy policy = new ConfigurableEnergyPortPolicy(
            generator.portRules()
                .allowedModes(), runtime::getMode, ignored -> true
        );

        assertFalse(policy.canChangeMode(BACK, INPUT));
        assertFalse(runtime.changeMode(BACK, INPUT, policy));
        runtime.restoreModes(Map.of(BACK, INPUT, LEFT, OUTPUT), policy);

        assertAll(
            () -> assertEquals(OUTPUT, runtime.getMode(BACK)),
            () -> assertEquals(OUTPUT, runtime.getMode(LEFT)),
            () -> assertFalse(policy.allowsInsert(BACK)),
            () -> assertFalse(policy.allowsInsert(LEFT)),
            () -> assertTrue(policy.allowsExtract(BACK)),
            () -> assertTrue(policy.allowsExtract(LEFT)),
            () -> assertEquals(DISABLED, generator.portRules().defaultModes().get(LEFT))
        );
    }
}
