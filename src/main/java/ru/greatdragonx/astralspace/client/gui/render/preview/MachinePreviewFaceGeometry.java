package ru.greatdragonx.astralspace.client.gui.render.preview;

import net.minecraft.core.Direction;
import org.jetbrains.annotations.Unmodifiable;
import org.joml.Vector3f;
import org.jspecify.annotations.NonNull;

import java.util.List;
import java.util.Objects;

public final class MachinePreviewFaceGeometry {
    private MachinePreviewFaceGeometry() {
    }

    public static @NonNull @Unmodifiable List<Vector3f> getFaceVertices(
        @NonNull Direction direction
    )
    {
        Objects.requireNonNull(direction, "direction cannot be null");

        return switch (direction) {
            case NORTH -> List.of(
                new Vector3f(1, 0, 0),
                new Vector3f(0, 0, 0),
                new Vector3f(0, 1, 0),
                new Vector3f(1, 1, 0)
            );
            case SOUTH -> List.of(
                new Vector3f(0, 0, 1),
                new Vector3f(1, 0, 1),
                new Vector3f(1, 1, 1),
                new Vector3f(0, 1, 1)
            );
            case EAST -> List.of(
                new Vector3f(1, 0, 1),
                new Vector3f(1, 0, 0),
                new Vector3f(1, 1, 0),
                new Vector3f(1, 1, 1)
            );
            case WEST -> List.of(
                new Vector3f(0, 0, 0),
                new Vector3f(0, 0, 1),
                new Vector3f(0, 1, 1),
                new Vector3f(0, 1, 0)
            );
            case UP -> List.of(
                new Vector3f(0, 1, 1),
                new Vector3f(1, 1, 1),
                new Vector3f(1, 1, 0),
                new Vector3f(0, 1, 0)
            );
            case DOWN -> List.of(
                new Vector3f(0, 0, 0),
                new Vector3f(1, 0, 0),
                new Vector3f(1, 0, 1),
                new Vector3f(0, 0, 1)
            );
        };
    }

    public static @NonNull Vector3f getFaceNormal(
        @NonNull Direction direction
    )
    {
        Objects.requireNonNull(direction, "direction cannot be null");

        return new Vector3f(direction.getStepX(), direction.getStepY(), direction.getStepZ());
    }

    public static @NonNull @Unmodifiable List<Vector3f> getOffsetFaceVertices(
        @NonNull Direction direction,
        float offset
    )
    {
        Objects.requireNonNull(direction, "direction cannot be null");
        if (!Float.isFinite(offset) || offset < 0.0f) {
            throw new IllegalArgumentException("offset must be finite and non-negative");
        }

        List<Vector3f> vertices = getFaceVertices(direction);
        Vector3f displacement = getFaceNormal(direction).mul(offset);
        for (Vector3f vertex : vertices) {
            vertex.add(displacement);
        }

        return vertices;
    }
}
