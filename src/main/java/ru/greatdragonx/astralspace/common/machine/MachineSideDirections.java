package ru.greatdragonx.astralspace.common.machine;

import net.minecraft.core.Direction;

import java.util.Objects;

public final class MachineSideDirections {
    private MachineSideDirections() {
    }

    public static Direction toWorld(
        MachineSide side,
        Direction facing
    ) {
        Objects.requireNonNull(
            side,
            "Machine side cannot be null"
        );
        MachineSideDirections.assertFacing(facing);
        return switch (side) {
            case FRONT -> facing;
            case BACK -> facing.getOpposite();
            case LEFT -> facing.getClockWise();
            case RIGHT -> facing.getCounterClockWise();
            case TOP -> Direction.UP;
            case BOTTOM -> Direction.DOWN;
        };
    }

    public static MachineSide fromWorld(
        Direction direction,
        Direction facing
    ) {
        Objects.requireNonNull(
            direction,
            "World direction cannot be null"
        );
        MachineSideDirections.assertFacing(facing);

        for (MachineSide side : MachineSide.values()) {
            Direction worldDirection = toWorld(
                side,
                facing
            );

            if (worldDirection == direction) {
                return side;
            }
        }

        throw new IllegalStateException("Cannot resolve machine side for world direction: " + direction + ", facing: " + facing);
    }

    private static void assertFacing(Direction facing) {
        Objects.requireNonNull(
            facing,
            "Facing direction cannot be null"
        );
        if (!facing.getAxis()
            .isHorizontal()) {
            throw new IllegalArgumentException("Facing direction must be horizontal: " + facing);
        }
    }
}
