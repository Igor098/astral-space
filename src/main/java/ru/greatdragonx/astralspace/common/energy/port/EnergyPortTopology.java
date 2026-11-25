package ru.greatdragonx.astralspace.common.energy.port;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.jetbrains.annotations.Unmodifiable;
import org.jspecify.annotations.NonNull;

import java.util.*;

public record EnergyPortTopology(
    @NonNull List<EnergyPortPoint> points
)
{
    public EnergyPortTopology {
        points = List.copyOf(Objects.requireNonNull(points, "points cannot be null"));

        Set<EnergyPortId> knownPorts = new HashSet<>();
        Map<BlockPos, Set<Direction>> occupiedFaces = new HashMap<>();
        for (EnergyPortPoint point : points) {
            if (!knownPorts.add(point.portId())) {
                throw new IllegalArgumentException("Duplicate energy port ID: " +
                    point.portId().value());
            }

            BlockPos offset = point.localOffset();
            Set<Direction> faces = occupiedFaces.computeIfAbsent(
                offset,
                ignored -> EnumSet.noneOf(Direction.class)
            );

            if (!faces.add(point.localOutwardFace())) {
                throw new IllegalArgumentException("Duplicate physical energy port point: offset=" +
                    offset +
                    ", face=" +
                    point.localOutwardFace() +
                    ", portId=" +
                    point.portId().value());
            }
        }
    }

    public @NonNull @Unmodifiable List<EnergyPortId> portIds() {
        return this.points.stream().map(EnergyPortPoint::portId).toList();
    }
}
