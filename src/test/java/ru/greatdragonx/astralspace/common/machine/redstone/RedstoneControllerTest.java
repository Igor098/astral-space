package ru.greatdragonx.astralspace.common.machine.redstone;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;

import java.util.Arrays;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class RedstoneControllerTest {
    @Test
    void shouldRejectNullRuntimeSupplier() {
        assertThrows(
            NullPointerException.class, () -> new RedstoneController(
                null, () -> false, () -> {
            }
            )
        );
    }

    @Test
    void shouldRejectNullSignalSupplier() {
        assertThrows(
            NullPointerException.class, () -> new RedstoneController(
                () -> true, null, () -> {
            }
            )
        );
    }

    @Test
    void shouldRejectNullCallback() {
        assertThrows(
            NullPointerException.class,
            () -> new RedstoneController(() -> true, () -> false, null)
        );
    }

    @Test
    void shouldInitializeWithIgnoreWithoutCallingDependencies() {
        Fixture f = new Fixture();
        assertSame(RedstoneControlMode.IGNORE, f.controller.getRedstoneControlMode());
        f.assertInteractions(0, 0, 0);
    }

    @ParameterizedTest
    @EnumSource(RedstoneControlMode.class)
    void shouldReadModeRegardlessOfRuntimeAndSignal(RedstoneControlMode mode) {
        Fixture f = new Fixture();
        f.controller.restoreMode(mode);
        for (boolean ready : new boolean[]{false, true}) {
            f.ready = ready;
            for (boolean signal : new boolean[]{false, true}) {
                f.signal = signal;
                assertSame(mode, f.controller.getRedstoneControlMode());
            }
        }
        f.assertInteractions(0, 0, 0);
    }

    @Test
    void shouldKeepIndependentModesAndCallbacks() {
        Fixture first = new Fixture();
        Fixture second = new Fixture();
        assertTrue(first.controller.changeRedstoneControlMode(RedstoneControlMode.WHEN_POWERED));
        assertSame(RedstoneControlMode.WHEN_POWERED, first.controller.getRedstoneControlMode());
        assertSame(RedstoneControlMode.IGNORE, second.controller.getRedstoneControlMode());
        first.assertInteractions(1, 0, 1);
        second.assertInteractions(0, 0, 0);
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @MethodSource("distinctModeTransitions")
    void shouldChangeModeAndNotifyOnceWhenReady(
        RedstoneControlMode previous,
        RedstoneControlMode next
    )
    {
        Fixture f = new Fixture();
        f.controller.restoreMode(previous);
        assertTrue(f.controller.changeRedstoneControlMode(next));
        assertSame(next, f.controller.getRedstoneControlMode());
        assertSame(next, f.modeSeenByCallback);
        f.assertInteractions(1, 0, 1);
    }

    @ParameterizedTest
    @EnumSource(RedstoneControlMode.class)
    void shouldNotNotifyOrChangeWhenModeIsAlreadySelected(RedstoneControlMode mode) {
        Fixture f = new Fixture();
        f.controller.restoreMode(mode);
        assertFalse(f.controller.changeRedstoneControlMode(mode));
        assertSame(mode, f.controller.getRedstoneControlMode());
        f.assertInteractions(1, 0, 0);
    }

    @ParameterizedTest(name = "closed: {0} -> {1}")
    @MethodSource("distinctModeTransitions")
    void shouldRejectModeChangesWhileRuntimeIsClosed(
        RedstoneControlMode previous,
        RedstoneControlMode next
    )
    {
        Fixture f = new Fixture();
        f.controller.restoreMode(previous);
        f.ready = false;
        assertFalse(f.controller.changeRedstoneControlMode(next));
        assertSame(previous, f.controller.getRedstoneControlMode());
        f.assertInteractions(1, 0, 0);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void shouldRejectNullModeBeforeCheckingRuntime(boolean ready) {
        Fixture f = new Fixture();
        f.ready = ready;
        assertThrows(
            NullPointerException.class,
            () -> f.controller.changeRedstoneControlMode(null)
        );
        assertSame(RedstoneControlMode.IGNORE, f.controller.getRedstoneControlMode());
        f.assertInteractions(0, 0, 0);
    }

    @Test
    void shouldPropagateCallbackFailureWithoutRollingBackMode() {
        Fixture f = new Fixture();
        RuntimeException failure = new IllegalStateException("Callback failed");
        f.callbackFailure = failure;
        assertSame(
            failure, assertThrows(
                IllegalStateException.class,
                () -> f.controller.changeRedstoneControlMode(RedstoneControlMode.WHEN_POWERED)
            )
        );
        assertSame(RedstoneControlMode.WHEN_POWERED, f.controller.getRedstoneControlMode());
        assertSame(RedstoneControlMode.WHEN_POWERED, f.modeSeenByCallback);
        f.assertInteractions(1, 0, 1);
        assertFalse(f.controller.changeRedstoneControlMode(RedstoneControlMode.WHEN_POWERED));
        f.assertInteractions(2, 0, 1);
    }

    @ParameterizedTest
    @CsvSource({"true,true,true", "true,false,false", "false,true,false", "false,false,false"})
    void shouldReadSignalOnlyWhenReady(boolean ready, boolean signal, boolean expected) {
        Fixture f = new Fixture();
        f.ready = ready;
        f.signal = signal;
        assertEquals(expected, f.controller.hasRedstoneSignal());
        f.assertInteractions(1, ready ? 1 : 0, 0);
    }

    @Test
    void shouldReadLiveSignalRegardlessOfModeWithoutCaching() {
        Fixture f = new Fixture();
        for (RedstoneControlMode mode : RedstoneControlMode.values()) {
            f.controller.restoreMode(mode);
            f.signal = false;
            assertFalse(f.controller.hasRedstoneSignal());
            f.signal = true;
            assertTrue(f.controller.hasRedstoneSignal());
        }
        int expectedCalls = 2 * RedstoneControlMode.values().length;
        f.assertInteractions(expectedCalls, expectedCalls, 0);
    }

    @ParameterizedTest(name = "{0}, signal={1}, blocked={2}")
    @CsvSource({"IGNORE,false,false", "IGNORE,true,false", "WHEN_POWERED,false,true", "WHEN_POWERED,true,false", "WHEN_UNPOWERED,false,false", "WHEN_UNPOWERED,true,true"})
    void shouldDetermineBlockingWhenRuntimeIsReady(
        RedstoneControlMode mode,
        boolean signal,
        boolean expected
    )
    {
        Fixture f = new Fixture();
        f.controller.restoreMode(mode);
        f.signal = signal;
        assertEquals(expected, f.controller.isRedstoneBlockingOperation());
        f.assertInteractions(1, 1, 0);
    }

    @ParameterizedTest(name = "closed: {0}, signal={1}")
    @CsvSource({"IGNORE,false", "IGNORE,true", "WHEN_POWERED,false", "WHEN_POWERED,true", "WHEN_UNPOWERED,false", "WHEN_UNPOWERED,true"})
    void shouldNotReportRedstoneBlockingWhileRuntimeIsClosed(
        RedstoneControlMode mode,
        boolean signal
    )
    {
        Fixture f = new Fixture();
        f.controller.restoreMode(mode);
        f.ready = false;
        f.signal = signal;
        assertFalse(f.controller.isRedstoneBlockingOperation());
        f.assertInteractions(1, 0, 0);
    }

    @Test
    void shouldUseLiveReadinessAndSignalAcrossCloseAndReopen() {
        Fixture f = new Fixture();
        f.controller.restoreMode(RedstoneControlMode.WHEN_POWERED);
        assertTrue(f.controller.isRedstoneBlockingOperation());
        f.assertInteractions(1, 1, 0);

        f.signal = true;
        assertFalse(f.controller.isRedstoneBlockingOperation());
        f.assertInteractions(2, 2, 0);

        f.ready = false;
        assertFalse(f.controller.hasRedstoneSignal());
        assertFalse(f.controller.isRedstoneBlockingOperation());
        assertFalse(f.controller.changeRedstoneControlMode(RedstoneControlMode.WHEN_UNPOWERED));
        assertSame(RedstoneControlMode.WHEN_POWERED, f.controller.getRedstoneControlMode());
        f.assertInteractions(5, 2, 0);

        f.ready = true;
        assertTrue(f.controller.hasRedstoneSignal());
        assertFalse(f.controller.isRedstoneBlockingOperation());
        f.assertInteractions(7, 4, 0);
        assertTrue(f.controller.changeRedstoneControlMode(RedstoneControlMode.WHEN_UNPOWERED));
        assertTrue(f.controller.isRedstoneBlockingOperation());
        f.assertInteractions(9, 5, 1);
    }

    @ParameterizedTest(name = "restore {0}, ready={1}")
    @CsvSource({"IGNORE,false", "IGNORE,true", "WHEN_POWERED,false", "WHEN_POWERED,true", "WHEN_UNPOWERED,false", "WHEN_UNPOWERED,true"})
    void shouldRestoreModeSilentlyRegardlessOfRuntime(RedstoneControlMode mode, boolean ready) {
        Fixture f = new Fixture();
        RedstoneControlMode previous =
            mode == RedstoneControlMode.IGNORE ? RedstoneControlMode.WHEN_POWERED :
                RedstoneControlMode.IGNORE;
        f.controller.restoreMode(previous);
        f.ready = ready;
        f.signal = true;
        f.controller.restoreMode(mode);
        assertSame(mode, f.controller.getRedstoneControlMode());
        f.controller.restoreMode(mode);
        assertSame(mode, f.controller.getRedstoneControlMode());
        f.assertInteractions(0, 0, 0);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void shouldRejectNullRestoreWithoutChangingStateOrAccessingDependencies(boolean ready) {
        Fixture f = new Fixture();
        f.controller.restoreMode(RedstoneControlMode.WHEN_UNPOWERED);
        f.ready = ready;
        assertThrows(NullPointerException.class, () -> f.controller.restoreMode(null));
        assertSame(RedstoneControlMode.WHEN_UNPOWERED, f.controller.getRedstoneControlMode());
        f.assertInteractions(0, 0, 0);
    }

    private static Stream<Arguments> distinctModeTransitions() {
        return Arrays.stream(RedstoneControlMode.values()).flatMap(previous -> Arrays.stream(
                RedstoneControlMode.values())
            .filter(next -> previous != next)
            .map(next -> Arguments.of(previous, next)));
    }

    private static final class Fixture {
        private boolean ready = true;
        private boolean signal;
        private int runtimeCalls;
        private int signalCalls;
        private int callbackCalls;
        private RedstoneControlMode modeSeenByCallback;
        private RuntimeException callbackFailure;
        private final RedstoneController controller;

        private Fixture() {
            controller = new RedstoneController(
                this::readRuntime,
                this::readSignal,
                this::onModeChanged
            );
        }

        private boolean readRuntime() {
            runtimeCalls++;
            return ready;
        }

        private boolean readSignal() {
            signalCalls++;
            return signal;
        }

        private void onModeChanged() {
            callbackCalls++;
            modeSeenByCallback = controller.getRedstoneControlMode();
            if (callbackFailure != null) {
                throw callbackFailure;
            }
        }

        private void assertInteractions(
            int expectedRuntime,
            int expectedSignal,
            int expectedCallback
        )
        {
            assertAll(
                () -> assertEquals(expectedRuntime, runtimeCalls, "runtimeReady calls"),
                () -> assertEquals(expectedSignal, signalCalls, "signalSupplier calls"),
                () -> assertEquals(expectedCallback, callbackCalls, "onModeChanged calls")
            );
        }
    }
}
