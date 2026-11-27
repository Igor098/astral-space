package ru.greatdragonx.astralspace.common.machine;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static ru.greatdragonx.astralspace.common.machine.RedstoneControlMode.*;

class RedstoneControlComponentTest {
    @Test
    void shouldRejectNullCallback() {
        assertThrows(NullPointerException.class, () -> new RedstoneControlComponent(null));
    }

    @Test
    void shouldInitializeWithIgnoreWithoutNotification() {
        AtomicInteger notifications = new AtomicInteger();
        var component = new RedstoneControlComponent(notifications::incrementAndGet);

        assertAll(
            () -> assertEquals(IGNORE, component.getMode()),
            () -> assertEquals(0, notifications.get())
        );
    }

    @Test
    void shouldNotNotifyOnReads() {
        AtomicInteger notifications = new AtomicInteger();
        var component = new RedstoneControlComponent(notifications::incrementAndGet);
        component.restoreMode(WHEN_POWERED);

        assertEquals(WHEN_POWERED, component.getMode());
        assertFalse(component.allowsOperation(false));
        assertTrue(component.allowsOperation(true));
        assertEquals(WHEN_POWERED, component.getMode());
        assertEquals(0, notifications.get());
    }

    @Test
    void shouldChangeBetweenEveryPairOfDistinctModes() {
        for (RedstoneControlMode initial : RedstoneControlMode.values()) {
            for (RedstoneControlMode next : RedstoneControlMode.values()) {
                if (initial == next) {
                    continue;
                }

                AtomicInteger notifications = new AtomicInteger();
                var component = new RedstoneControlComponent(notifications::incrementAndGet);
                component.restoreMode(initial);
                String context = initial + " -> " + next;

                assertTrue(component.changeMode(next), context);
                assertEquals(next, component.getMode(), context);
                assertEquals(1, notifications.get(), context);
            }
        }
    }

    @Test
    void shouldNotNotifyWhenModeIsUnchanged() {
        AtomicInteger notifications = new AtomicInteger();
        var component = new RedstoneControlComponent(notifications::incrementAndGet);

        for (RedstoneControlMode mode : RedstoneControlMode.values()) {
            component.restoreMode(mode);

            assertFalse(component.changeMode(mode), mode.name());
            assertEquals(mode, component.getMode());
            assertEquals(0, notifications.get());
        }
    }

    @Test
    void shouldRejectNullChangeWithoutChangingStateOrNotifying() {
        AtomicInteger notifications = new AtomicInteger();
        var component = new RedstoneControlComponent(notifications::incrementAndGet);
        component.restoreMode(WHEN_POWERED);

        assertThrows(NullPointerException.class, () -> component.changeMode(null));

        assertAll(
            () -> assertEquals(WHEN_POWERED, component.getMode()),
            () -> assertEquals(0, notifications.get())
        );
    }

    @Test
    void shouldNotifyAfterNewModeIsStored() {
        AtomicReference<RedstoneControlComponent> owner = new AtomicReference<>();
        List<RedstoneControlMode> observed = new ArrayList<>();
        var component = new RedstoneControlComponent(() -> {
            var current = owner.get();
            observed.add(current.getMode());
            assertFalse(current.allowsOperation(false));
            assertTrue(current.allowsOperation(true));
        });
        owner.set(component);

        assertTrue(component.changeMode(WHEN_POWERED));
        assertEquals(List.of(WHEN_POWERED), observed);
    }

    @Test
    void shouldNotifyExactlyOncePerActualChangeInOrder() {
        AtomicReference<RedstoneControlComponent> owner = new AtomicReference<>();
        List<RedstoneControlMode> observed = new ArrayList<>();
        var component = new RedstoneControlComponent(() -> observed.add(owner.get().getMode()));
        owner.set(component);

        assertTrue(component.changeMode(WHEN_POWERED));
        assertFalse(component.changeMode(WHEN_POWERED));
        assertTrue(component.changeMode(WHEN_UNPOWERED));
        assertTrue(component.changeMode(IGNORE));
        assertFalse(component.changeMode(IGNORE));

        assertEquals(List.of(WHEN_POWERED, WHEN_UNPOWERED, IGNORE), observed);
        assertEquals(IGNORE, component.getMode());
    }

    @Test
    void shouldPropagateCallbackFailureWithoutRollingBackMode() {
        var failure = new IllegalStateException("Callback failure");
        AtomicInteger notifications = new AtomicInteger();
        var component = new RedstoneControlComponent(() -> {
            notifications.incrementAndGet();
            throw failure;
        });

        assertSame(
            failure,
            assertThrows(IllegalStateException.class, () -> component.changeMode(WHEN_UNPOWERED))
        );

        assertAll(
            () -> assertEquals(WHEN_UNPOWERED, component.getMode()),
            () -> assertEquals(1, notifications.get()),
            () -> assertTrue(component.allowsOperation(false)),
            () -> assertFalse(component.allowsOperation(true))
        );

        assertFalse(component.changeMode(WHEN_UNPOWERED));
        assertEquals(1, notifications.get());
    }

    @Test
    void shouldAllowOperationInIgnoreModeRegardlessOfSignal() {
        var component = new RedstoneControlComponent(() -> fail("Unexpected notification"));

        assertAll(
            () -> assertTrue(component.allowsOperation(false)),
            () -> assertTrue(component.allowsOperation(true))
        );
    }

    @Test
    void shouldAllowOperationInWhenPoweredModeOnlyWithSignal() {
        var component = new RedstoneControlComponent(() -> fail("Unexpected notification"));
        component.restoreMode(WHEN_POWERED);

        assertAll(
            () -> assertFalse(component.allowsOperation(false)),
            () -> assertTrue(component.allowsOperation(true))
        );
    }

    @Test
    void shouldAllowOperationInWhenUnpoweredModeOnlyWithoutSignal() {
        var component = new RedstoneControlComponent(() -> fail("Unexpected notification"));
        component.restoreMode(WHEN_UNPOWERED);

        assertAll(
            () -> assertTrue(component.allowsOperation(false)),
            () -> assertFalse(component.allowsOperation(true))
        );
    }

    @Test
    void shouldRestoreAllModesWithoutNotification() {
        AtomicInteger notifications = new AtomicInteger();
        var component = new RedstoneControlComponent(notifications::incrementAndGet);

        for (RedstoneControlMode mode : List.of(WHEN_POWERED, WHEN_UNPOWERED, IGNORE)) {
            component.restoreMode(mode);

            assertEquals(mode, component.getMode());
            assertEquals(0, notifications.get());
        }
    }

    @Test
    void shouldRestoreUnchangedModeWithoutNotification() {
        var component = new RedstoneControlComponent(() -> fail("Unexpected notification"));

        for (RedstoneControlMode mode : RedstoneControlMode.values()) {
            component.restoreMode(mode);
            component.restoreMode(mode);
            assertEquals(mode, component.getMode());
        }
    }

    @Test
    void shouldRejectNullRestoreWithoutChangingStateOrNotifying() {
        AtomicInteger notifications = new AtomicInteger();
        var component = new RedstoneControlComponent(notifications::incrementAndGet);
        component.restoreMode(WHEN_UNPOWERED);

        assertThrows(NullPointerException.class, () -> component.restoreMode(null));

        assertAll(
            () -> assertEquals(WHEN_UNPOWERED, component.getMode()),
            () -> assertEquals(0, notifications.get())
        );
    }

    @Test
    void shouldUseRestoredModeForSubsequentChanges() {
        AtomicInteger notifications = new AtomicInteger();
        var component = new RedstoneControlComponent(notifications::incrementAndGet);

        component.restoreMode(WHEN_POWERED);
        assertFalse(component.changeMode(WHEN_POWERED));
        assertEquals(0, notifications.get());

        assertTrue(component.changeMode(WHEN_UNPOWERED));
        assertEquals(WHEN_UNPOWERED, component.getMode());
        assertEquals(1, notifications.get());

        component.restoreMode(IGNORE);
        assertEquals(IGNORE, component.getMode());
        assertEquals(1, notifications.get());
    }

    @Test
    void shouldKeepInstancesAndCallbacksIndependent() {
        AtomicInteger firstNotifications = new AtomicInteger();
        AtomicInteger secondNotifications = new AtomicInteger();
        var first = new RedstoneControlComponent(firstNotifications::incrementAndGet);
        var second = new RedstoneControlComponent(secondNotifications::incrementAndGet);

        assertTrue(first.changeMode(WHEN_POWERED));
        assertEquals(IGNORE, second.getMode());
        assertEquals(1, firstNotifications.get());
        assertEquals(0, secondNotifications.get());

        second.restoreMode(WHEN_UNPOWERED);
        assertEquals(WHEN_POWERED, first.getMode());
        assertEquals(0, secondNotifications.get());
        assertFalse(first.allowsOperation(false));
        assertTrue(second.allowsOperation(false));

        assertTrue(second.changeMode(IGNORE));
        assertEquals(WHEN_POWERED, first.getMode());
        assertEquals(IGNORE, second.getMode());
        assertEquals(1, firstNotifications.get());
        assertEquals(1, secondNotifications.get());
    }
}
