package ru.greatdragonx.astralspace.common.machine;

import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MachineSideDirectionsTest {

    @Test
    void shouldConvertBackToSouthWhenFacingNorth() {
        Direction result = MachineSideDirections.toWorld(
            MachineSide.BACK,
            Direction.NORTH
        );

        assertEquals(
            Direction.SOUTH,
            result
        );
    }

    @Test
    void shouldConvertRightToWestWhenFacingNorth() {
        Direction result = MachineSideDirections.toWorld(
            MachineSide.RIGHT,
            Direction.NORTH
        );

        assertEquals(
            Direction.WEST,
            result
        );
    }

    @Test
    void shouldConvertWestToRightWhenFacingNorth() {
        MachineSide result = MachineSideDirections.fromWorld(
            Direction.WEST,
            Direction.NORTH
        );

        assertEquals(
            MachineSide.RIGHT,
            result
        );
    }

    @Test
    void shouldRejectNullMachineSide() {
        NullPointerException exception = assertThrows(
            NullPointerException.class,
            () -> MachineSideDirections.toWorld(
                null,
                Direction.NORTH
            )
        );

        assertEquals(
            "Machine side cannot be null",
            exception.getMessage()
        );
    }

    @Test
    void shouldRejectNullFacing() {
        NullPointerException exception = assertThrows(
            NullPointerException.class,
            () -> MachineSideDirections.toWorld(
                MachineSide.FRONT,
                null
            )
        );

        assertEquals(
            "Facing direction cannot be null",
            exception.getMessage()
        );
    }

    @Test
    void shouldRejectNullWorldDirection() {
        NullPointerException exception = assertThrows(
            NullPointerException.class,
            () -> MachineSideDirections.fromWorld(
                null,
                Direction.NORTH
            )
        );

        assertEquals(
            "World direction cannot be null",
            exception.getMessage()
        );
    }

    @Test
    void shouldRejectNullFacingInFromWorld() {
        NullPointerException exception = assertThrows(
            NullPointerException.class,
            () -> MachineSideDirections.fromWorld(
                Direction.NORTH,
                null
            )
        );

        assertEquals(
            "Facing direction cannot be null",
            exception.getMessage()
        );
    }

    @ParameterizedTest
    @EnumSource(MachineSide.class)
    void shouldRestoreOriginalSide(MachineSide side) {
        Direction[] facings = {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};

        for (Direction facing : facings) {
            Direction result = MachineSideDirections.toWorld(
                side,
                facing
            );

            MachineSide reversedResult = MachineSideDirections.fromWorld(
                result,
                facing
            );

            assertEquals(
                side,
                reversedResult
            );
        }
    }

    @ParameterizedTest
    @EnumSource(value = Direction.class, names = {"UP", "DOWN"})
    void shouldRejectVerticalFacing(Direction facing) {
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> MachineSideDirections.toWorld(
                MachineSide.FRONT,
                facing
            )
        );

        assertEquals(
            "Facing direction must be horizontal: " + facing,
            exception.getMessage()
        );
    }

    @ParameterizedTest
    @EnumSource(value = Direction.class, names = {"UP", "DOWN"})
    void shouldRejectVerticalFacingInFromWorld(Direction facing) {
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> MachineSideDirections.fromWorld(
                Direction.NORTH,
                facing
            )
        );

        assertEquals(
            "Facing direction must be horizontal: " + facing,
            exception.getMessage()
        );
    }
}
