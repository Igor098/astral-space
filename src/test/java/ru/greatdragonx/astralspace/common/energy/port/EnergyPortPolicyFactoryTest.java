package ru.greatdragonx.astralspace.common.energy.port;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static ru.greatdragonx.astralspace.common.energy.port.EnergyPortMode.*;
import static ru.greatdragonx.astralspace.common.energy.port.EnergyPortRules.ConfigurationType.CONFIGURABLE;
import static ru.greatdragonx.astralspace.common.energy.port.EnergyPortRules.ConfigurationType.FIXED;

class EnergyPortPolicyFactoryTest {
    private static final EnergyPortId BACK = new EnergyPortId("test.back");
    private static final EnergyPortId LEFT = new EnergyPortId("test.left");
    private static final EnergyPortId RIGHT = new EnergyPortId("test.right");
    private static final EnergyPortId UNKNOWN = new EnergyPortId("test.unknown");

    private static EnergyPortRules fixedRules() {
        return new EnergyPortRules(FIXED, List.of(
            new EnergyPortRule(BACK, OUTPUT, Set.of(OUTPUT)),
            new EnergyPortRule(LEFT, INPUT, Set.of(INPUT)),
            new EnergyPortRule(RIGHT, DISABLED, Set.of(DISABLED))
        ));
    }

    private static EnergyPortRules configurableRules() {
        return new EnergyPortRules(CONFIGURABLE, List.of(
            new EnergyPortRule(BACK, OUTPUT, Set.of(OUTPUT, DISABLED)),
            new EnergyPortRule(LEFT, INPUT, Set.of(INPUT, DISABLED)),
            new EnergyPortRule(RIGHT, DISABLED, Set.of(INPUT, OUTPUT, DISABLED))
        ));
    }

    private static EnergyPortPolicyFactory.Result create(EnergyPortRules rules) {
        return EnergyPortPolicyFactory.create(rules, ignored -> true, ignored -> {});
    }

    @Test
    void shouldRejectNullRules() {
        assertThrows(NullPointerException.class,
            () -> EnergyPortPolicyFactory.create(null, ignored -> true, ignored -> {}));
    }

    @Test
    void shouldRejectNullAvailabilityForBothConfigurationTypes() {
        for (EnergyPortRules rules : List.of(fixedRules(), configurableRules())) {
            assertThrows(NullPointerException.class,
                () -> EnergyPortPolicyFactory.create(rules, null, ignored -> {}));
        }
    }

    @Test
    void shouldRejectNullCallbackEvenForFixedRules() {
        for (EnergyPortRules rules : List.of(fixedRules(), configurableRules())) {
            assertThrows(NullPointerException.class,
                () -> EnergyPortPolicyFactory.create(rules, ignored -> true, null));
        }
    }

    @Test
    void shouldRejectNullPolicyInResult() {
        EnergyPortConfiguration configuration = new EnergyPortConfiguration(
            configurableRules().defaultModes(), ignored -> {}
        );
        assertThrows(NullPointerException.class, () -> new EnergyPortPolicyFactory.Result(null, null));
        assertThrows(NullPointerException.class, () -> new EnergyPortPolicyFactory.Result(null, configuration));
    }

    @Test
    void shouldCreateFixedPolicyWithoutMutableConfiguration() {
        var result = create(fixedRules());
        assertAll(
            () -> assertInstanceOf(FixedEnergyPortPolicy.class, result.policy()),
            () -> assertNull(result.configuration())
        );
    }

    @Test
    void shouldCreateConfigurablePolicyWithMutableConfiguration() {
        var result = create(configurableRules());
        assertAll(
            () -> assertInstanceOf(ConfigurableEnergyPortPolicy.class, result.policy()),
            () -> assertNotNull(result.configuration())
        );
    }

    @Test
    void shouldUseFixedDefaultModesAndSupportInputPorts() {
        var policy = create(fixedRules()).policy();
        assertAll(
            () -> assertEquals(OUTPUT, policy.getMode(BACK)),
            () -> assertEquals(INPUT, policy.getMode(LEFT)),
            () -> assertEquals(DISABLED, policy.getMode(RIGHT)),
            () -> assertTrue(policy.allowsExtract(BACK)),
            () -> assertFalse(policy.allowsInsert(BACK)),
            () -> assertTrue(policy.allowsInsert(LEFT)),
            () -> assertFalse(policy.allowsExtract(LEFT)),
            () -> assertFalse(policy.allowsInsert(RIGHT)),
            () -> assertFalse(policy.allowsExtract(RIGHT))
        );
    }

    @Test
    void shouldInitializeConfigurableModesFromRules() {
        var result = create(configurableRules());
        var configuration = result.configuration();
        assertNotNull(configuration);
        assertAll(
            () -> assertEquals(Map.of(BACK, OUTPUT, LEFT, INPUT, RIGHT, DISABLED), configuration.snapshot()),
            () -> assertEquals(OUTPUT, result.policy().getMode(BACK)),
            () -> assertEquals(INPUT, result.policy().getMode(LEFT)),
            () -> assertEquals(DISABLED, result.policy().getMode(RIGHT)),
            () -> assertTrue(result.policy().allowsExtract(BACK)),
            () -> assertTrue(result.policy().allowsInsert(LEFT)),
            () -> assertFalse(result.policy().allowsExtract(RIGHT)),
            () -> assertFalse(result.policy().allowsInsert(RIGHT))
        );
    }

    @Test
    void shouldNeverAllowFixedModeChanges() {
        var policy = create(fixedRules()).policy();
        for (EnergyPortId id : List.of(BACK, LEFT, RIGHT, UNKNOWN)) {
            for (EnergyPortMode mode : EnergyPortMode.values()) {
                assertFalse(policy.canChangeMode(id, mode));
            }
        }
    }

    @Test
    void shouldRespectAllowedModesForEachConfigurablePort() {
        var policy = create(configurableRules()).policy();
        assertAll(
            () -> assertTrue(policy.canChangeMode(BACK, OUTPUT)),
            () -> assertTrue(policy.canChangeMode(BACK, DISABLED)),
            () -> assertFalse(policy.canChangeMode(BACK, INPUT)),
            () -> assertTrue(policy.canChangeMode(LEFT, INPUT)),
            () -> assertTrue(policy.canChangeMode(LEFT, DISABLED)),
            () -> assertFalse(policy.canChangeMode(LEFT, OUTPUT)),
            () -> assertTrue(policy.canChangeMode(RIGHT, INPUT)),
            () -> assertTrue(policy.canChangeMode(RIGHT, OUTPUT)),
            () -> assertTrue(policy.canChangeMode(RIGHT, DISABLED))
        );
    }

    @Test
    void shouldObserveChangesToReturnedConfiguration() {
        List<EnergyPortId> notifications = new ArrayList<>();
        var result = EnergyPortPolicyFactory.create(configurableRules(), ignored -> true, notifications::add);
        var configuration = result.configuration();
        assertNotNull(configuration);
        var policy = result.policy();
        assertTrue(configuration.changeMode(BACK, DISABLED, policy));
        assertEquals(DISABLED, policy.getMode(BACK));
        assertFalse(policy.allowsExtract(BACK));
        assertEquals(List.of(BACK), notifications);
        assertTrue(configuration.changeMode(BACK, OUTPUT, policy));
        assertEquals(OUTPUT, policy.getMode(BACK));
        assertTrue(policy.allowsExtract(BACK));
        assertEquals(List.of(BACK, BACK), notifications);
    }

    @Test
    void shouldNotifyAfterNewModeBecomesVisibleToPolicy() {
        List<EnergyPortId> notifications = new ArrayList<>();
        AtomicReference<EnergyPortPolicy> policyRef = new AtomicReference<>();
        var result = EnergyPortPolicyFactory.create(configurableRules(), ignored -> true, portId -> {
            assertEquals(BACK, portId);
            assertEquals(DISABLED, policyRef.get().getMode(portId));
            assertFalse(policyRef.get().allowsExtract(portId));
            notifications.add(portId);
        });
        policyRef.set(result.policy());
        var configuration = result.configuration();
        assertNotNull(configuration);
        assertTrue(configuration.changeMode(BACK, DISABLED, result.policy()));
        assertEquals(List.of(BACK), notifications);
    }

    @Test
    void shouldNotNotifyForRejectedOrUnchangedModes() {
        List<EnergyPortId> notifications = new ArrayList<>();
        var result = EnergyPortPolicyFactory.create(configurableRules(), ignored -> true, notifications::add);
        var configuration = result.configuration();
        assertNotNull(configuration);
        var policy = result.policy();
        assertFalse(configuration.changeMode(BACK, INPUT, policy));
        assertFalse(configuration.changeMode(BACK, OUTPUT, policy));
        assertFalse(configuration.changeMode(UNKNOWN, OUTPUT, policy));
        assertTrue(notifications.isEmpty());
        assertEquals(configurableRules().defaultModes(), configuration.snapshot());
    }

    @Test
    void shouldObserveSilentRestorationThroughReturnedConfiguration() {
        List<EnergyPortId> notifications = new ArrayList<>();
        var result = EnergyPortPolicyFactory.create(configurableRules(), ignored -> true, notifications::add);
        var configuration = result.configuration();
        assertNotNull(configuration);
        configuration.restoreModes(Map.of(BACK, DISABLED, LEFT, OUTPUT, RIGHT, INPUT, UNKNOWN, OUTPUT), result.policy());
        assertAll(
            () -> assertEquals(DISABLED, result.policy().getMode(BACK)),
            () -> assertFalse(result.policy().allowsExtract(BACK)),
            () -> assertEquals(INPUT, result.policy().getMode(LEFT)),
            () -> assertTrue(result.policy().allowsInsert(RIGHT)),
            () -> assertFalse(configuration.snapshot().containsKey(UNKNOWN)),
            () -> assertTrue(notifications.isEmpty())
        );
    }

    @Test
    void shouldObserveLiveAvailabilityForFixedPolicy() {
        assertLiveAvailability(fixedRules());
    }

    @Test
    void shouldObserveLiveAvailabilityForConfigurablePolicy() {
        assertLiveAvailability(configurableRules());
    }

    private static void assertLiveAvailability(EnergyPortRules rules) {
        AtomicBoolean available = new AtomicBoolean(false);
        var policy = EnergyPortPolicyFactory.create(
            rules, id -> available.get() && id.equals(BACK), ignored -> {}
        ).policy();
        assertEquals(OUTPUT, policy.getMode(BACK));
        assertFalse(policy.isAvailable(BACK));
        assertFalse(policy.allowsExtract(BACK));
        available.set(true);
        assertTrue(policy.isAvailable(BACK));
        assertTrue(policy.allowsExtract(BACK));
        assertFalse(policy.isAvailable(LEFT));
        assertFalse(policy.allowsInsert(LEFT));
        available.set(false);
        assertFalse(policy.allowsExtract(BACK));
        assertEquals(OUTPUT, policy.getMode(BACK));
    }

    @Test
    void shouldAllowConfiguredModeChangesWhilePortUnavailable() {
        List<EnergyPortId> notifications = new ArrayList<>();
        var result = EnergyPortPolicyFactory.create(configurableRules(), ignored -> false, notifications::add);
        var configuration = result.configuration();
        assertNotNull(configuration);
        assertTrue(configuration.changeMode(RIGHT, OUTPUT, result.policy()));
        assertEquals(OUTPUT, result.policy().getMode(RIGHT));
        assertFalse(result.policy().isAvailable(RIGHT));
        assertFalse(result.policy().allowsExtract(RIGHT));
        assertEquals(List.of(RIGHT), notifications);
    }

    @Test
    void shouldRejectUnknownPortsWithoutConsultingAvailability() {
        for (EnergyPortRules rules : List.of(fixedRules(), configurableRules())) {
            var policy = EnergyPortPolicyFactory.create(rules, id -> {
                fail("Unknown port must not reach availability predicate");
                return true;
            }, ignored -> {}).policy();
            assertAll(
                () -> assertEquals(DISABLED, policy.getMode(UNKNOWN)),
                () -> assertFalse(policy.isAvailable(UNKNOWN)),
                () -> assertFalse(policy.allowsExtract(UNKNOWN)),
                () -> assertFalse(policy.allowsInsert(UNKNOWN)),
                () -> assertFalse(policy.canChangeMode(UNKNOWN, OUTPUT))
            );
        }
    }

    @Test
    void shouldNotInvokeAvailabilityOrCallbackDuringFixedCreation() {
        assertNoCallbacksDuringCreation(fixedRules());
    }

    @Test
    void shouldNotInvokeAvailabilityOrCallbackDuringConfigurableCreation() {
        assertNoCallbacksDuringCreation(configurableRules());
    }

    private static void assertNoCallbacksDuringCreation(EnergyPortRules rules) {
        AtomicInteger availabilityCalls = new AtomicInteger();
        List<EnergyPortId> notifications = new ArrayList<>();
        var result = EnergyPortPolicyFactory.create(rules, id -> {
            availabilityCalls.incrementAndGet();
            return true;
        }, notifications::add);
        assertNotNull(result.policy());
        assertEquals(0, availabilityCalls.get());
        assertTrue(notifications.isEmpty());
    }

    @Test
    void shouldSupportEmptyFixedRules() {
        var result = create(new EnergyPortRules(FIXED, List.of()));
        assertInstanceOf(FixedEnergyPortPolicy.class, result.policy());
        assertNull(result.configuration());
        assertEquals(DISABLED, result.policy().getMode(BACK));
        assertFalse(result.policy().isAvailable(BACK));
        assertFalse(result.policy().allowsExtract(BACK));
    }

    @Test
    void shouldSupportEmptyConfigurableRules() {
        List<EnergyPortId> notifications = new ArrayList<>();
        var result = EnergyPortPolicyFactory.create(
            new EnergyPortRules(CONFIGURABLE, List.of()), ignored -> true, notifications::add
        );
        assertInstanceOf(ConfigurableEnergyPortPolicy.class, result.policy());
        var configuration = result.configuration();
        assertNotNull(configuration);
        assertTrue(configuration.snapshot().isEmpty());
        assertFalse(configuration.changeMode(BACK, OUTPUT, result.policy()));
        assertEquals(DISABLED, result.policy().getMode(BACK));
        assertFalse(result.policy().isAvailable(BACK));
        assertTrue(configuration.snapshot().isEmpty());
        assertTrue(notifications.isEmpty());
    }

    @Test
    void shouldCreateIndependentConfigurableComponentsForEachCall() {
        EnergyPortRules rules = configurableRules();
        List<EnergyPortId> firstNotifications = new ArrayList<>();
        List<EnergyPortId> secondNotifications = new ArrayList<>();
        AtomicBoolean firstAvailable = new AtomicBoolean(true);
        var first = EnergyPortPolicyFactory.create(rules, ignored -> firstAvailable.get(), firstNotifications::add);
        var second = EnergyPortPolicyFactory.create(rules, ignored -> true, secondNotifications::add);
        var firstConfiguration = first.configuration();
        var secondConfiguration = second.configuration();
        assertNotNull(firstConfiguration);
        assertNotNull(secondConfiguration);
        assertNotSame(first.policy(), second.policy());
        assertNotSame(firstConfiguration, secondConfiguration);
        assertTrue(firstConfiguration.changeMode(BACK, DISABLED, first.policy()));
        assertEquals(DISABLED, first.policy().getMode(BACK));
        assertEquals(OUTPUT, second.policy().getMode(BACK));
        assertEquals(List.of(BACK), firstNotifications);
        assertTrue(secondNotifications.isEmpty());
        firstAvailable.set(false);
        assertFalse(first.policy().allowsInsert(LEFT));
        assertTrue(second.policy().allowsInsert(LEFT));
        assertEquals(OUTPUT, rules.defaultModes().get(BACK));
        assertTrue(secondConfiguration.changeMode(LEFT, DISABLED, second.policy()));
        assertEquals(List.of(BACK), firstNotifications);
        assertEquals(List.of(LEFT), secondNotifications);
        assertEquals(INPUT, first.policy().getMode(LEFT));
    }

    @Test
    void shouldCreateIndependentFixedPoliciesForEachCall() {
        EnergyPortRules rules = fixedRules();
        var first = EnergyPortPolicyFactory.create(rules, ignored -> false, ignored -> {});
        var second = EnergyPortPolicyFactory.create(rules, ignored -> true, ignored -> {});
        assertNotSame(first.policy(), second.policy());
        assertNull(first.configuration());
        assertNull(second.configuration());
        assertFalse(first.policy().allowsExtract(BACK));
        assertTrue(second.policy().allowsExtract(BACK));
    }

    @Test
    void shouldForwardChangedPortIdsInOrderToProvidedConsumer() {
        List<EnergyPortId> notifications = new ArrayList<>();
        var result = EnergyPortPolicyFactory.create(configurableRules(), ignored -> true, notifications::add);
        var configuration = result.configuration();
        assertNotNull(configuration);
        var policy = result.policy();
        assertTrue(configuration.changeMode(RIGHT, OUTPUT, policy));
        assertTrue(configuration.changeMode(BACK, DISABLED, policy));
        assertFalse(configuration.changeMode(LEFT, OUTPUT, policy));
        assertFalse(configuration.changeMode(RIGHT, OUTPUT, policy));
        assertTrue(configuration.changeMode(LEFT, DISABLED, policy));
        assertTrue(configuration.changeMode(RIGHT, INPUT, policy));
        assertEquals(List.of(RIGHT, BACK, LEFT, RIGHT), notifications);
        assertEquals(Map.of(BACK, DISABLED, LEFT, DISABLED, RIGHT, INPUT), configuration.snapshot());
    }

    @Test
    void shouldExposeUpdatedModesThroughPolicyForEachNotifiedPort() {
        AtomicReference<EnergyPortPolicy> policyRef = new AtomicReference<>();
        List<EnergyPortId> notifications = new ArrayList<>();
        List<EnergyPortMode> observedModes = new ArrayList<>();
        var result = EnergyPortPolicyFactory.create(configurableRules(), ignored -> true, portId -> {
            notifications.add(portId);
            observedModes.add(policyRef.get().getMode(portId));
        });
        policyRef.set(result.policy());
        var configuration = result.configuration();
        assertNotNull(configuration);
        assertTrue(configuration.changeMode(LEFT, DISABLED, result.policy()));
        assertTrue(configuration.changeMode(RIGHT, OUTPUT, result.policy()));
        assertTrue(configuration.changeMode(LEFT, INPUT, result.policy()));
        assertEquals(List.of(LEFT, RIGHT, LEFT), notifications);
        assertEquals(List.of(DISABLED, OUTPUT, INPUT), observedModes);
    }
}
