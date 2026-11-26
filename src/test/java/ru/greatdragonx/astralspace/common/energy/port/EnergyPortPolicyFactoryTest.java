package ru.greatdragonx.astralspace.common.energy.port;

import org.junit.jupiter.api.Test;

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
        return new EnergyPortRules(
            FIXED, List.of(
            new EnergyPortRule(BACK, OUTPUT, Set.of(OUTPUT)),
            new EnergyPortRule(LEFT, INPUT, Set.of(INPUT)),
            new EnergyPortRule(RIGHT, DISABLED, Set.of(DISABLED))
        )
        );
    }

    private static EnergyPortRules configurableRules() {
        return new EnergyPortRules(
            CONFIGURABLE, List.of(
            new EnergyPortRule(BACK, OUTPUT, Set.of(OUTPUT, DISABLED)),
            new EnergyPortRule(LEFT, INPUT, Set.of(INPUT, DISABLED)),
            new EnergyPortRule(RIGHT, DISABLED, Set.of(INPUT, OUTPUT, DISABLED))
        )
        );
    }

    private static EnergyPortPolicyFactory.Result create(EnergyPortRules rules) {
        return EnergyPortPolicyFactory.create(
            rules, ignored -> true, () -> {
            }
        );
    }

    @Test
    void shouldRejectNullRules() {
        assertThrows(
            NullPointerException.class,
            () -> EnergyPortPolicyFactory.create(
                null, ignored -> true, () -> {
                }
            )
        );
    }

    @Test
    void shouldRejectNullAvailabilityForBothConfigurationTypes() {
        for (EnergyPortRules rules : List.of(fixedRules(), configurableRules())) {
            assertThrows(
                NullPointerException.class,
                () -> EnergyPortPolicyFactory.create(
                    rules, null, () -> {
                    }
                )
            );
        }
    }

    @Test
    void shouldRejectNullCallbackEvenForFixedRules() {
        for (EnergyPortRules rules : List.of(fixedRules(), configurableRules())) {
            assertThrows(
                NullPointerException.class,
                () -> EnergyPortPolicyFactory.create(rules, ignored -> true, null)
            );
        }
    }

    @Test
    void shouldRejectNullPolicyInResult() {
        EnergyPortConfiguration configuration = new EnergyPortConfiguration(
            configurableRules().defaultModes(), () -> {
        }
        );
        assertThrows(
            NullPointerException.class,
            () -> new EnergyPortPolicyFactory.Result(null, null)
        );
        assertThrows(
            NullPointerException.class,
            () -> new EnergyPortPolicyFactory.Result(null, configuration)
        );
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
            () -> assertEquals(
                Map.of(BACK, OUTPUT, LEFT, INPUT, RIGHT, DISABLED),
                configuration.snapshot()
            ),
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
        AtomicInteger notifications = new AtomicInteger();
        var result = EnergyPortPolicyFactory.create(
            configurableRules(),
            ignored -> true,
            notifications::incrementAndGet
        );
        var configuration = result.configuration();
        assertNotNull(configuration);
        var policy = result.policy();

        assertTrue(configuration.changeMode(BACK, DISABLED, policy));
        assertEquals(DISABLED, policy.getMode(BACK));
        assertFalse(policy.allowsExtract(BACK));
        assertEquals(1, notifications.get());

        assertTrue(configuration.changeMode(BACK, OUTPUT, policy));
        assertEquals(OUTPUT, policy.getMode(BACK));
        assertTrue(policy.allowsExtract(BACK));
        assertEquals(2, notifications.get());
    }

    @Test
    void shouldNotifyAfterNewModeBecomesVisibleToPolicy() {
        AtomicInteger notifications = new AtomicInteger();
        AtomicReference<EnergyPortPolicy> policyRef = new AtomicReference<>();
        var result = EnergyPortPolicyFactory.create(
            configurableRules(), ignored -> true, () -> {
                assertEquals(DISABLED, policyRef.get().getMode(BACK));
                assertFalse(policyRef.get().allowsExtract(BACK));
                notifications.incrementAndGet();
            }
        );
        policyRef.set(result.policy());
        var configuration = result.configuration();
        assertNotNull(configuration);
        assertTrue(configuration.changeMode(BACK, DISABLED, result.policy()));
        assertEquals(1, notifications.get());
    }

    @Test
    void shouldNotNotifyForRejectedOrUnchangedModes() {
        AtomicInteger notifications = new AtomicInteger();
        var result = EnergyPortPolicyFactory.create(
            configurableRules(),
            ignored -> true,
            notifications::incrementAndGet
        );
        var configuration = result.configuration();
        assertNotNull(configuration);
        var policy = result.policy();

        assertFalse(configuration.changeMode(BACK, INPUT, policy));
        assertFalse(configuration.changeMode(BACK, OUTPUT, policy));
        assertFalse(configuration.changeMode(UNKNOWN, OUTPUT, policy));
        assertEquals(0, notifications.get());
        assertEquals(configurableRules().defaultModes(), configuration.snapshot());
    }

    @Test
    void shouldObserveSilentRestorationThroughReturnedConfiguration() {
        AtomicInteger notifications = new AtomicInteger();
        var result = EnergyPortPolicyFactory.create(
            configurableRules(),
            ignored -> true,
            notifications::incrementAndGet
        );
        var configuration = result.configuration();
        assertNotNull(configuration);
        configuration.restoreModes(
            Map.of(
                BACK,
                DISABLED,
                LEFT,
                OUTPUT,
                RIGHT,
                INPUT,
                UNKNOWN,
                OUTPUT
            ), result.policy()
        );
        assertAll(
            () -> assertEquals(DISABLED, result.policy().getMode(BACK)),
            () -> assertFalse(result.policy().allowsExtract(BACK)),
            () -> assertEquals(INPUT, result.policy().getMode(LEFT)),
            () -> assertTrue(result.policy().allowsInsert(RIGHT)),
            () -> assertFalse(configuration.snapshot().containsKey(UNKNOWN)),
            () -> assertEquals(0, notifications.get())
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
            rules,
            id -> available.get() && id.equals(BACK),
            () -> {
            }
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
        var result = EnergyPortPolicyFactory.create(
            configurableRules(), ignored -> false, () -> {
            }
        );
        var configuration = result.configuration();
        assertNotNull(configuration);
        assertTrue(configuration.changeMode(RIGHT, OUTPUT, result.policy()));
        assertEquals(OUTPUT, result.policy().getMode(RIGHT));
        assertFalse(result.policy().isAvailable(RIGHT));
        assertFalse(result.policy().allowsExtract(RIGHT));
    }

    @Test
    void shouldRejectUnknownPortsWithoutConsultingAvailability() {
        for (EnergyPortRules rules : List.of(fixedRules(), configurableRules())) {
            var policy = EnergyPortPolicyFactory.create(
                rules, id -> {
                    fail("Unknown port must not reach availability predicate");
                    return true;
                }, () -> {
                }
            ).policy();
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
        AtomicInteger notifications = new AtomicInteger();
        var result = EnergyPortPolicyFactory.create(
            rules, id -> {
                availabilityCalls.incrementAndGet();
                return true;
            }, notifications::incrementAndGet
        );
        assertNotNull(result.policy());
        assertEquals(0, availabilityCalls.get());
        assertEquals(0, notifications.get());
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
        var result = create(new EnergyPortRules(CONFIGURABLE, List.of()));
        assertInstanceOf(ConfigurableEnergyPortPolicy.class, result.policy());
        var configuration = result.configuration();
        assertNotNull(configuration);
        assertTrue(configuration.snapshot().isEmpty());
        assertFalse(configuration.changeMode(BACK, OUTPUT, result.policy()));
        assertEquals(DISABLED, result.policy().getMode(BACK));
        assertFalse(result.policy().isAvailable(BACK));
        assertTrue(configuration.snapshot().isEmpty());
    }

    @Test
    void shouldCreateIndependentConfigurableComponentsForEachCall() {
        EnergyPortRules rules = configurableRules();
        AtomicInteger firstNotifications = new AtomicInteger();
        AtomicInteger secondNotifications = new AtomicInteger();
        AtomicBoolean firstAvailable = new AtomicBoolean(true);
        var first = EnergyPortPolicyFactory.create(
            rules,
            ignored -> firstAvailable.get(),
            firstNotifications::incrementAndGet
        );
        var second = EnergyPortPolicyFactory.create(
            rules,
            ignored -> true,
            secondNotifications::incrementAndGet
        );
        var firstConfiguration = first.configuration();
        var secondConfiguration = second.configuration();
        assertNotNull(firstConfiguration);
        assertNotNull(secondConfiguration);
        assertNotSame(first.policy(), second.policy());
        assertNotSame(firstConfiguration, secondConfiguration);

        assertTrue(firstConfiguration.changeMode(BACK, DISABLED, first.policy()));
        assertEquals(DISABLED, first.policy().getMode(BACK));
        assertEquals(OUTPUT, second.policy().getMode(BACK));
        assertEquals(1, firstNotifications.get());
        assertEquals(0, secondNotifications.get());
        firstAvailable.set(false);
        assertFalse(first.policy().allowsInsert(LEFT));
        assertTrue(second.policy().allowsInsert(LEFT));
        assertEquals(OUTPUT, rules.defaultModes().get(BACK));
    }

    @Test
    void shouldCreateIndependentFixedPoliciesForEachCall() {
        EnergyPortRules rules = fixedRules();
        var first = EnergyPortPolicyFactory.create(
            rules, ignored -> false, () -> {
            }
        );
        var second = EnergyPortPolicyFactory.create(
            rules, ignored -> true, () -> {
            }
        );
        assertNotSame(first.policy(), second.policy());
        assertNull(first.configuration());
        assertNull(second.configuration());
        assertFalse(first.policy().allowsExtract(BACK));
        assertTrue(second.policy().allowsExtract(BACK));
    }
}
