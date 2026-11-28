package ru.greatdragonx.astralspace.common.energy.port;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;
import ru.greatdragonx.astralspace.common.energy.port.EnergyPortTopology.WorldPortPoint;

import java.util.*;

import static net.minecraft.core.Direction.*;
import static org.junit.jupiter.api.Assertions.*;

class EnergyPortTopologyTest {
    private static final EnergyPortId A = new EnergyPortId("test.a");
    private static final EnergyPortId B = new EnergyPortId("test.b");
    private static final EnergyPortId C = new EnergyPortId("test.c");
    private static final EnergyPortId UNKNOWN = new EnergyPortId("test.unknown");
    private static final BlockPos ORIGIN = new BlockPos(100, 64, 200);
    private static final Direction[] FACINGS = {NORTH, EAST, SOUTH, WEST};

    @Test
    void shouldAcceptEmptyTopology() {
        EnergyPortTopology topology = topology();

        assertAll(
            () -> assertTrue(topology.points().isEmpty()),
            () -> assertTrue(topology.portIds().isEmpty())
        );
    }

    @Test
    void shouldAcceptDifferentFacesAtSamePosition() {
        EnergyPortPoint a = point(A, BlockPos.ZERO, SOUTH);
        EnergyPortPoint b = point(B, BlockPos.ZERO, EAST);

        assertEquals(List.of(a, b), topology(a, b).points());
    }

    @Test
    void shouldAcceptSameFaceAtDifferentPositions() {
        EnergyPortPoint a = point(A, new BlockPos(-1, 0, 1), SOUTH);
        EnergyPortPoint b = point(B, new BlockPos(1, 0, 1), SOUTH);

        assertEquals(List.of(a, b), topology(a, b).points());
    }

    @Test
    void shouldAcceptDifferentPositionsAndFaces() {
        EnergyPortPoint a = point(A, new BlockPos(-2, -3, 4), WEST);
        EnergyPortPoint b = point(B, new BlockPos(2, 3, -4), UP);

        assertEquals(List.of(a, b), topology(a, b).points());
    }

    @Test
    void shouldRejectNullPointsList() {
        assertThrows(NullPointerException.class, () -> new EnergyPortTopology(null));
    }

    @Test
    void shouldRejectNullPointInList() {
        List<EnergyPortPoint> points = new ArrayList<>();
        points.add(point(A, BlockPos.ZERO, SOUTH));
        points.add(null);

        assertThrows(NullPointerException.class, () -> new EnergyPortTopology(points));
    }

    @Test
    void shouldRejectRepeatedPoint() {
        EnergyPortPoint a = point(A, BlockPos.ZERO, SOUTH);

        assertThrows(IllegalArgumentException.class, () -> topology(a, a));
    }

    @Test
    void shouldRejectDuplicateIdEvenWhenPhysicalPointsDiffer() {
        EnergyPortPoint a = point(A, BlockPos.ZERO, SOUTH);
        EnergyPortPoint b = point(new EnergyPortId(A.value()), new BlockPos(1, 2, 3), EAST);

        assertThrows(IllegalArgumentException.class, () -> topology(a, b));
    }

    @Test
    void shouldRejectDuplicatePhysicalPointWithDifferentIds() {
        EnergyPortPoint a = point(A, new BlockPos(2, 3, 4), UP);
        EnergyPortPoint b = point(B, new BlockPos(2, 3, 4), UP);

        assertThrows(IllegalArgumentException.class, () -> topology(a, b));
    }

    @Test
    void shouldDetectPhysicalConflictAfterOtherValidPoints() {
        EnergyPortPoint a = point(A, BlockPos.ZERO, SOUTH);
        EnergyPortPoint b = point(B, BlockPos.ZERO, EAST);
        EnergyPortPoint c = point(C, BlockPos.ZERO, SOUTH);

        assertThrows(IllegalArgumentException.class, () -> topology(a, b, c));
    }

    @Test
    void shouldDefensivelyCopyPointsList() {
        EnergyPortPoint a = point(A, BlockPos.ZERO, SOUTH);
        List<EnergyPortPoint> source = new ArrayList<>(List.of(a));
        EnergyPortTopology topology = new EnergyPortTopology(source);

        source.clear();
        source.add(point(B, BlockPos.ZERO, EAST));

        assertAll(
            () -> assertEquals(List.of(a), topology.points()),
            () -> assertEquals(List.of(A), topology.portIds())
        );
    }

    @Test
    void shouldExposeUnmodifiablePointsList() {
        EnergyPortPoint a = point(A, BlockPos.ZERO, SOUTH);
        EnergyPortTopology topology = topology(a);
        List<EnergyPortPoint> points = topology.points();

        assertAll(
            () -> assertThrows(
                UnsupportedOperationException.class,
                () -> points.add(point(B, BlockPos.ZERO, EAST))
            ),
            () -> assertThrows(UnsupportedOperationException.class, () -> points.remove(a)),
            () -> assertThrows(UnsupportedOperationException.class, points::clear)
        );
    }

    @Test
    void shouldPreserveDeclarationOrderInPortIds() {
        EnergyPortTopology topology = topology(
            point(C, BlockPos.ZERO, UP),
            point(A, BlockPos.ZERO, SOUTH),
            point(B, BlockPos.ZERO, EAST)
        );

        assertEquals(List.of(C, A, B), topology.portIds());
    }

    @Test
    void shouldExposeUnmodifiablePortIds() {
        List<EnergyPortId> ids = topology(point(A, BlockPos.ZERO, SOUTH)).portIds();

        assertAll(
            () -> assertThrows(UnsupportedOperationException.class, () -> ids.add(B)),
            () -> assertThrows(UnsupportedOperationException.class, () -> ids.remove(A)),
            () -> assertThrows(UnsupportedOperationException.class, ids::clear)
        );
    }

    @Test
    void shouldCompareTopologiesByOrderedPointValues() {
        EnergyPortTopology first = topology(
            point(A, new BlockPos(1, 2, 3), SOUTH),
            point(B, BlockPos.ZERO, EAST)
        );
        EnergyPortTopology equal = topology(
            point(
                new EnergyPortId(A.value()),
                new BlockPos(1, 2, 3),
                SOUTH
            ),
            point(B, BlockPos.ZERO, EAST)
        );
        EnergyPortTopology reordered = topology(first.points().get(1), first.points().get(0));

        assertAll(
            () -> assertEquals(first, equal),
            () -> assertEquals(first.hashCode(), equal.hashCode()),
            () -> assertNotEquals(first, reordered)
        );
    }

    @Test
    void shouldResolveDistinctCoordinatesForAllFourFacings() {
        EnergyPortTopology topology = topology(point(A, new BlockPos(2, 1, 3), SOUTH));

        assertAll(
            () -> assertResolved(topology, A, ORIGIN, NORTH, new BlockPos(102, 65, 203), SOUTH),
            () -> assertResolved(topology, A, ORIGIN, EAST, new BlockPos(97, 65, 202), WEST),
            () -> assertResolved(topology, A, ORIGIN, SOUTH, new BlockPos(98, 65, 197), NORTH),
            () -> assertResolved(topology, A, ORIGIN, WEST, new BlockPos(103, 65, 198), EAST)
        );
    }

    @Test
    void shouldResolveNegativeOffsetsAndNegativeOrigin() {
        BlockPos origin = new BlockPos(-100, 40, -200);
        EnergyPortTopology topology = topology(point(A, new BlockPos(-3, -2, 4), EAST));

        assertAll(
            () -> assertResolved(topology, A, origin, NORTH, new BlockPos(-103, 38, -196), EAST),
            () -> assertResolved(topology, A, origin, EAST, new BlockPos(-104, 38, -203), SOUTH),
            () -> assertResolved(topology, A, origin, SOUTH, new BlockPos(-97, 38, -204), WEST),
            () -> assertResolved(topology, A, origin, WEST, new BlockPos(-96, 38, -197), NORTH)
        );
    }

    @Test
    void shouldPreserveZeroOffsetForAllFacings() {
        EnergyPortTopology topology = topology(point(A, BlockPos.ZERO, UP));

        for (Direction facing : FACINGS) {
            assertResolved(topology, A, ORIGIN, facing, ORIGIN, UP);
        }
    }

    @Test
    void shouldPreserveVerticalOffsetForAllFacings() {
        EnergyPortTopology topology = topology(point(A, new BlockPos(0, -5, 0), DOWN));

        for (Direction facing : FACINGS) {
            assertResolved(topology, A, ORIGIN, facing, new BlockPos(100, 59, 200), DOWN);
        }
    }

    @Test
    void shouldRotateAllTwentyFourFacingAndOutwardFaceCombinations() {
        Direction[] localFaces = {NORTH, EAST, SOUTH, WEST, UP, DOWN};
        Direction[][] expectedFaces =
            {{NORTH, EAST, SOUTH, WEST, UP, DOWN}, {EAST, SOUTH, WEST, NORTH, UP, DOWN}, {SOUTH, WEST, NORTH, EAST, UP, DOWN}, {WEST, NORTH, EAST, SOUTH, UP, DOWN}};

        for (int i = 0; i < FACINGS.length; i++) {
            for (int j = 0; j < localFaces.length; j++) {
                EnergyPortTopology topology = topology(point(A, BlockPos.ZERO, localFaces[j]));
                String context = "Facing: " + FACINGS[i] + ", local face: " + localFaces[j];

                assertEquals(
                    Optional.of(new WorldPortPoint(ORIGIN, expectedFaces[i][j])),
                    topology.resolvePort(A, ORIGIN, FACINGS[i]),
                    context
                );
                assertEquals(
                    Optional.of(A),
                    topology.findPort(ORIGIN, expectedFaces[i][j], ORIGIN, FACINGS[i]),
                    context
                );
            }
        }
    }

    @Test
    void shouldResolveTwoPortsWithSameLocalFaceIndependently() {
        EnergyPortTopology topology = twoOutputs();

        assertAll(
            () -> assertResolved(topology, A, ORIGIN, NORTH, new BlockPos(99, 64, 201), SOUTH),
            () -> assertResolved(topology, B, ORIGIN, NORTH, new BlockPos(101, 64, 201), SOUTH),
            () -> assertResolved(topology, A, ORIGIN, EAST, new BlockPos(99, 64, 199), WEST),
            () -> assertResolved(topology, B, ORIGIN, EAST, new BlockPos(99, 64, 201), WEST),
            () -> assertResolved(topology, A, ORIGIN, SOUTH, new BlockPos(101, 64, 199), NORTH),
            () -> assertResolved(topology, B, ORIGIN, SOUTH, new BlockPos(99, 64, 199), NORTH),
            () -> assertResolved(topology, A, ORIGIN, WEST, new BlockPos(101, 64, 201), EAST),
            () -> assertResolved(topology, B, ORIGIN, WEST, new BlockPos(101, 64, 199), EAST)
        );
    }

    @Test
    void shouldResolvePortByEqualIdRatherThanReference() {
        EnergyPortTopology topology = twoOutputs();
        EnergyPortId requested = new EnergyPortId(B.value());

        assertNotSame(B, requested);
        assertResolved(topology, requested, ORIGIN, EAST, new BlockPos(99, 64, 201), WEST);
    }

    @Test
    void shouldResolveRelativeToEachRequestedOriginWithoutChangingTopology() {
        EnergyPortTopology topology = topology(point(A, new BlockPos(2, 1, 3), SOUTH));
        List<EnergyPortPoint> original = topology.points();

        assertResolved(topology, A, ORIGIN, EAST, new BlockPos(97, 65, 202), WEST);
        assertResolved(topology, A, new BlockPos(10, 20, 30), EAST, new BlockPos(7, 21, 32), WEST);
        assertResolved(topology, A, ORIGIN, EAST, new BlockPos(97, 65, 202), WEST);
        assertEquals(original, topology.points());
        assertEquals(new BlockPos(2, 1, 3), topology.points().getFirst().localOffset());
        assertEquals(SOUTH, topology.points().getFirst().localOutwardFace());
    }

    @Test
    void shouldNotAliasMutableOriginEvenForZeroOffset() {
        BlockPos.MutableBlockPos origin = new BlockPos.MutableBlockPos(100, 64, 200);
        EnergyPortTopology topology = topology(point(A, BlockPos.ZERO, SOUTH));
        WorldPortPoint resolved = topology.resolvePort(A, origin, NORTH).orElseThrow();

        origin.set(10, 20, 30);

        assertAll(
            () -> assertEquals(ORIGIN, resolved.worldPos()),
            () -> assertFalse(resolved.worldPos() instanceof BlockPos.MutableBlockPos),
            () -> assertResolved(topology, A, origin, NORTH, new BlockPos(10, 20, 30), SOUTH)
        );
    }

    @Test
    void shouldReturnEmptyWhenResolvingUnknownId() {
        assertEquals(Optional.empty(), twoOutputs().resolvePort(UNKNOWN, ORIGIN, NORTH));
    }

    @Test
    void shouldReturnEmptyWhenResolvingInEmptyTopology() {
        assertEquals(Optional.empty(), topology().resolvePort(A, ORIGIN, NORTH));
    }

    @Test
    void shouldRejectNullResolveArgumentsBeforeSearching() {
        for (EnergyPortTopology topology : List.of(topology(), twoOutputs())) {
            assertAll(
                () -> assertThrows(
                    NullPointerException.class,
                    () -> topology.resolvePort(null, ORIGIN, NORTH)
                ),
                () -> assertThrows(
                    NullPointerException.class,
                    () -> topology.resolvePort(UNKNOWN, null, NORTH)
                ),
                () -> assertThrows(
                    NullPointerException.class,
                    () -> topology.resolvePort(UNKNOWN, ORIGIN, null)
                )
            );
        }
    }

    @Test
    void shouldRejectVerticalResolveFacingEvenWhenPortIsMissing() {
        for (EnergyPortTopology topology : List.of(
            topology(),
            twoOutputs(),
            topology(point(A, BlockPos.ZERO, UP))
        )) {
            for (EnergyPortId id : List.of(A, UNKNOWN)) {
                assertAll(
                    () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> topology.resolvePort(id, ORIGIN, UP)
                    ),
                    () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> topology.resolvePort(id, ORIGIN, DOWN)
                    )
                );
            }
        }
    }


    @Test
    void shouldFindTwoOutputsForAllFourFacings() {
        EnergyPortTopology topology = twoOutputs();

        assertAll(
            () -> assertFound(topology, A, new BlockPos(99, 64, 201), SOUTH, ORIGIN, NORTH),
            () -> assertFound(topology, B, new BlockPos(101, 64, 201), SOUTH, ORIGIN, NORTH),
            () -> assertFound(topology, A, new BlockPos(99, 64, 199), WEST, ORIGIN, EAST),
            () -> assertFound(topology, B, new BlockPos(99, 64, 201), WEST, ORIGIN, EAST),
            () -> assertFound(topology, A, new BlockPos(101, 64, 199), NORTH, ORIGIN, SOUTH),
            () -> assertFound(topology, B, new BlockPos(99, 64, 199), NORTH, ORIGIN, SOUTH),
            () -> assertFound(topology, A, new BlockPos(101, 64, 201), EAST, ORIGIN, WEST),
            () -> assertFound(topology, B, new BlockPos(101, 64, 199), EAST, ORIGIN, WEST)
        );
    }

    @Test
    void shouldDistinguishDifferentFacesAtSameWorldPosition() {
        EnergyPortTopology topology = topology(
            point(A, new BlockPos(2, 1, 3), SOUTH),
            point(B, new BlockPos(2, 1, 3), EAST),
            point(C, new BlockPos(2, 1, 3), UP)
        );
        BlockPos worldPos = new BlockPos(97, 65, 202);

        assertAll(
            () -> assertFound(topology, A, worldPos, WEST, ORIGIN, EAST),
            () -> assertFound(topology, B, worldPos, SOUTH, ORIGIN, EAST),
            () -> assertFound(topology, C, worldPos, UP, ORIGIN, EAST),
            () -> assertEquals(Optional.empty(), topology.findPort(worldPos, NORTH, ORIGIN, EAST))
        );
    }

    @Test
    void shouldDistinguishPortsAtDifferentHeights() {
        EnergyPortTopology topology = topology(
            point(A, new BlockPos(0, -1, 0), UP),
            point(B, new BlockPos(0, 2, 0), UP)
        );

        for (Direction facing : FACINGS) {
            assertFound(topology, A, new BlockPos(100, 63, 200), UP, ORIGIN, facing);
            assertFound(topology, B, new BlockPos(100, 66, 200), UP, ORIGIN, facing);
            assertEquals(Optional.empty(), topology.findPort(ORIGIN, UP, ORIGIN, facing));
        }
    }

    @Test
    void shouldReturnEmptyWhenOnlyPositionMatches() {
        assertEquals(
            Optional.empty(),
            twoOutputs().findPort(new BlockPos(99, 64, 199), EAST, ORIGIN, EAST)
        );
    }

    @Test
    void shouldReturnEmptyWhenOnlyFaceMatches() {
        assertEquals(
            Optional.empty(),
            twoOutputs().findPort(new BlockPos(100, 64, 200), WEST, ORIGIN, EAST)
        );
    }

    @Test
    void shouldNotTreatNeighborPositionAsPortPosition() {
        EnergyPortTopology topology = topology(point(A, BlockPos.ZERO, SOUTH));

        assertEquals(
            Optional.empty(),
            topology.findPort(new BlockPos(100, 64, 201), SOUTH, ORIGIN, NORTH)
        );
    }

    @Test
    void shouldUseRequestedFacingInsteadOfPreviousFacing() {
        EnergyPortTopology topology = topology(point(A, new BlockPos(2, 1, 3), SOUTH));
        BlockPos northPosition = new BlockPos(102, 65, 203);
        BlockPos eastPosition = new BlockPos(97, 65, 202);

        assertFound(topology, A, northPosition, SOUTH, ORIGIN, NORTH);
        assertEquals(Optional.empty(), topology.findPort(northPosition, SOUTH, ORIGIN, EAST));
        assertFound(topology, A, eastPosition, WEST, ORIGIN, EAST);
        assertFound(topology, A, northPosition, SOUTH, ORIGIN, NORTH);
    }

    @Test
    void shouldUseRequestedOriginInsteadOfPreviousOrigin() {
        EnergyPortTopology topology = topology(point(A, new BlockPos(2, 1, 3), SOUTH));
        BlockPos otherOrigin = new BlockPos(-10, 30, -20);
        BlockPos originalPosition = new BlockPos(97, 65, 202);
        BlockPos newPosition = new BlockPos(-13, 31, -18);

        assertFound(topology, A, originalPosition, WEST, ORIGIN, EAST);
        assertEquals(
            Optional.empty(),
            topology.findPort(originalPosition, WEST, otherOrigin, EAST)
        );
        assertFound(topology, A, newPosition, WEST, otherOrigin, EAST);
    }

    @Test
    void shouldReturnEmptyWhenFindingInEmptyTopology() {
        assertEquals(Optional.empty(), topology().findPort(ORIGIN, UP, ORIGIN, NORTH));
    }

    @Test
    void shouldRejectNullFindArgumentsBeforeSearching() {
        for (EnergyPortTopology topology : List.of(topology(), twoOutputs())) {
            assertAll(
                () -> assertThrows(
                    NullPointerException.class,
                    () -> topology.findPort(null, SOUTH, ORIGIN, NORTH)
                ), () -> assertThrows(
                    NullPointerException.class,
                    () -> topology.findPort(ORIGIN, null, ORIGIN, NORTH)
                ), () -> assertThrows(
                    NullPointerException.class,
                    () -> topology.findPort(ORIGIN, SOUTH, null, NORTH)
                ), () -> assertThrows(
                    NullPointerException.class,
                    () -> topology.findPort(ORIGIN, SOUTH, ORIGIN, null)
                )
            );
        }
    }

    @Test
    void shouldRejectVerticalFindFacingEvenWithVerticalOutwardFace() {
        for (EnergyPortTopology topology : List.of(
            topology(),
            twoOutputs(),
            topology(point(A, BlockPos.ZERO, UP))
        )) {
            assertAll(
                () -> assertThrows(
                    IllegalArgumentException.class,
                    () -> topology.findPort(ORIGIN, UP, ORIGIN, UP)
                ), () -> assertThrows(
                    IllegalArgumentException.class,
                    () -> topology.findPort(ORIGIN, DOWN, ORIGIN, DOWN)
                )
            );
        }
    }

    @Test
    void shouldRoundTripEveryPortAcrossAllFacingsAndMultipleOrigins() {
        EnergyPortTopology topology = topology(
            point(A, new BlockPos(-1, 0, 1), SOUTH),
            point(B, new BlockPos(1, 0, 1), SOUTH),
            point(C, new BlockPos(-1, 0, 1), UP),
            point(new EnergyPortId("test.d"), new BlockPos(2, -3, 4), DOWN)
        );

        for (BlockPos origin : List.of(ORIGIN, new BlockPos(-50, 30, -80))) {
            for (Direction facing : FACINGS) {
                Set<WorldPortPoint> resolvedPoints = new HashSet<>();
                for (EnergyPortId id : topology.portIds()) {
                    WorldPortPoint resolved = topology.resolvePort(id, origin, facing)
                        .orElseThrow();
                    String context = "ID: " + id + ", origin: " + origin + ", facing: " + facing;

                    assertTrue(resolvedPoints.add(resolved), context);
                    assertEquals(
                        Optional.of(id),
                        topology.findPort(
                            resolved.worldPos(),
                            resolved.outwardFace(),
                            origin,
                            facing
                        ),
                        context
                    );
                }
            }
        }
    }

    @Test
    void shouldCreateWorldPortPointForEveryDirection() {
        for (Direction face : Direction.values()) {
            WorldPortPoint point = new WorldPortPoint(ORIGIN, face);
            assertEquals(ORIGIN, point.worldPos());
            assertEquals(face, point.outwardFace());
        }
    }

    @Test
    void shouldRejectNullWorldPortPointPosition() {
        assertThrows(NullPointerException.class, () -> new WorldPortPoint(null, SOUTH));
    }

    @Test
    void shouldRejectNullWorldPortPointFace() {
        assertThrows(NullPointerException.class, () -> new WorldPortPoint(ORIGIN, null));
    }

    @Test
    void shouldDefensivelyCopyMutableWorldPortPointPosition() {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(100, 64, 200);
        WorldPortPoint point = new WorldPortPoint(pos, SOUTH);
        int originalHash = point.hashCode();
        Set<WorldPortPoint> points = new HashSet<>(Set.of(point));

        pos.set(2, 3, 4);

        assertAll(
            () -> assertEquals(ORIGIN, point.worldPos()),
            () -> assertNotSame(pos, point.worldPos()),
            () -> assertFalse(point.worldPos() instanceof BlockPos.MutableBlockPos),
            () -> assertEquals(originalHash, point.hashCode()),
            () -> assertTrue(points.contains(point)),
            () -> assertTrue(points.contains(new WorldPortPoint(ORIGIN, SOUTH)))
        );
    }

    @Test
    void shouldCompareWorldPortPointsByPositionAndFace() {
        WorldPortPoint a = new WorldPortPoint(ORIGIN, SOUTH);
        WorldPortPoint equal = new WorldPortPoint(new BlockPos(100, 64, 200), SOUTH);

        assertAll(
            () -> assertEquals(a, equal),
            () -> assertEquals(a.hashCode(), equal.hashCode()),
            () -> assertNotEquals(a, new WorldPortPoint(ORIGIN, NORTH)),
            () -> assertNotEquals(a, new WorldPortPoint(new BlockPos(100, 65, 200), SOUTH))
        );
    }

    private static EnergyPortPoint point(EnergyPortId id, BlockPos offset, Direction face) {
        return new EnergyPortPoint(id, offset, face);
    }

    private static EnergyPortTopology topology(EnergyPortPoint... points) {
        return new EnergyPortTopology(List.of(points));
    }

    private static EnergyPortTopology twoOutputs() {
        return topology(
            point(A, new BlockPos(-1, 0, 1), SOUTH),
            point(B, new BlockPos(1, 0, 1), SOUTH)
        );
    }

    private static void assertResolved(
        EnergyPortTopology topology,
        EnergyPortId id,
        BlockPos origin,
        Direction facing,
        BlockPos expectedPosition,
        Direction expectedFace
    )
    {
        WorldPortPoint resolved = topology.resolvePort(id, origin, facing).orElseThrow();
        String context = "ID: " + id + ", facing: " + facing;

        assertAll(
            () -> assertEquals(expectedPosition, resolved.worldPos(), context),
            () -> assertEquals(expectedFace, resolved.outwardFace(), context)
        );
    }

    private static void assertFound(
        EnergyPortTopology topology,
        EnergyPortId expectedId,
        BlockPos worldPos,
        Direction outwardFace,
        BlockPos origin,
        Direction facing
    )
    {
        assertEquals(
            Optional.of(expectedId),
            topology.findPort(worldPos, outwardFace, origin, facing),
            "World position: " + worldPos + ", face: " + outwardFace + ", facing: " + facing
        );
    }
}
