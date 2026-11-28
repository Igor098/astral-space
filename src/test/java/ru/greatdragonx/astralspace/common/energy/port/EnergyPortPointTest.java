package ru.greatdragonx.astralspace.common.energy.port;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class EnergyPortPointTest {
    private static final EnergyPortId PORT = new EnergyPortId("test.port");
    private static final EnergyPortId OTHER_PORT = new EnergyPortId("test.other");

    @Test
    void shouldCreatePointWithProvidedValues() {
        BlockPos offset = new BlockPos(2, 3, 4);
        EnergyPortPoint point = new EnergyPortPoint(PORT, offset, Direction.SOUTH);

        assertAll(
            () -> assertEquals(PORT, point.portId()),
            () -> assertEquals(offset, point.localOffset()),
            () -> assertEquals(Direction.SOUTH, point.localOutwardFace())
        );
    }

    @Test
    void shouldAcceptZeroOffsetForSingleBlockMachine() {
        EnergyPortPoint point = new EnergyPortPoint(PORT, BlockPos.ZERO, Direction.SOUTH);

        assertEquals(BlockPos.ZERO, point.localOffset());
    }

    @Test
    void shouldAcceptNegativeCoordinates() {
        BlockPos offset = new BlockPos(-2, -3, -4);
        EnergyPortPoint point = new EnergyPortPoint(PORT, offset, Direction.WEST);

        assertEquals(offset, point.localOffset());
    }

    @Test
    void shouldAcceptNonZeroVerticalOffset() {
        BlockPos offset = new BlockPos(0, 5, 0);
        EnergyPortPoint point = new EnergyPortPoint(PORT, offset, Direction.UP);

        assertEquals(offset, point.localOffset());
        assertEquals(5, point.localOffset().getY());
    }

    @Test
    void shouldAcceptAllSixOutwardFaces() {
        for (Direction face : Direction.values()) {
            EnergyPortPoint point = new EnergyPortPoint(PORT, BlockPos.ZERO, face);

            assertEquals(face, point.localOutwardFace(), "Face: " + face);
        }
    }

    @Test
    void shouldRejectNullPortId() {
        assertThrows(
            NullPointerException.class,
            () -> new EnergyPortPoint(null, BlockPos.ZERO, Direction.SOUTH)
        );
    }

    @Test
    void shouldRejectNullLocalOffset() {
        assertThrows(
            NullPointerException.class,
            () -> new EnergyPortPoint(PORT, null, Direction.SOUTH)
        );
    }

    @Test
    void shouldRejectNullLocalOutwardFace() {
        assertThrows(
            NullPointerException.class,
            () -> new EnergyPortPoint(PORT, BlockPos.ZERO, null)
        );
    }

    @Test
    void shouldDefensivelyCopyMutableBlockPos() {
        BlockPos.MutableBlockPos offset = new BlockPos.MutableBlockPos(1, 0, 2);
        EnergyPortPoint point = new EnergyPortPoint(PORT, offset, Direction.SOUTH);

        offset.set(6, 3, -4);

        assertAll(
            () -> assertEquals(new BlockPos(1, 0, 2), point.localOffset()),
            () -> assertEquals(new BlockPos(6, 3, -4), offset),
            () -> assertNotSame(offset, point.localOffset())
        );
    }

    @Test
    void shouldExposeImmutableOffset() {
        BlockPos.MutableBlockPos offset = new BlockPos.MutableBlockPos(1, 0, 2);
        EnergyPortPoint point = new EnergyPortPoint(PORT, offset, Direction.SOUTH);

        assertFalse(point.localOffset() instanceof BlockPos.MutableBlockPos);
    }

    @Test
    void shouldPreserveHashAndSetMembershipAfterOriginalOffsetChanges() {
        BlockPos.MutableBlockPos offset = new BlockPos.MutableBlockPos(-1, 0, 2);
        EnergyPortPoint point = new EnergyPortPoint(PORT, offset, Direction.SOUTH);
        EnergyPortPoint expected = new EnergyPortPoint(
            PORT,
            new BlockPos(-1, 0, 2),
            Direction.SOUTH
        );
        Set<EnergyPortPoint> points = new HashSet<>();
        points.add(point);
        int originalHash = point.hashCode();

        offset.set(5, 1, -7);

        assertAll(
            () -> assertEquals(expected, point),
            () -> assertEquals(originalHash, point.hashCode()),
            () -> assertTrue(points.contains(point)),
            () -> assertTrue(points.contains(expected))
        );
    }

    @Test
    void shouldCompareEquivalentPointsByValue() {
        EnergyPortPoint first = new EnergyPortPoint(PORT, new BlockPos(1, 2, 3), Direction.NORTH);
        EnergyPortPoint second = new EnergyPortPoint(
            new EnergyPortId("test.port"),
            new BlockPos(1, 2, 3),
            Direction.NORTH
        );

        assertAll(
            () -> assertEquals(first, second),
            () -> assertEquals(first.hashCode(), second.hashCode())
        );
    }

    @Test
    void shouldDistinguishDifferentPortIdsAtSamePhysicalPoint() {
        EnergyPortPoint first = new EnergyPortPoint(PORT, BlockPos.ZERO, Direction.SOUTH);
        EnergyPortPoint second = new EnergyPortPoint(OTHER_PORT, BlockPos.ZERO, Direction.SOUTH);

        // Проверка уникальности физической точки принадлежит топологии, а не record.
        assertNotEquals(first, second);
    }

    @Test
    void shouldDistinguishDifferentOffsetsWithSameOutwardFace() {
        EnergyPortPoint first = new EnergyPortPoint(PORT, new BlockPos(-1, 0, 2), Direction.SOUTH);
        EnergyPortPoint second = new EnergyPortPoint(PORT, new BlockPos(1, 0, 2), Direction.SOUTH);

        assertNotEquals(first, second);
    }

    @Test
    void shouldDistinguishDifferentFacesAtSameOffset() {
        EnergyPortPoint first = new EnergyPortPoint(PORT, BlockPos.ZERO, Direction.SOUTH);
        EnergyPortPoint second = new EnergyPortPoint(PORT, BlockPos.ZERO, Direction.EAST);

        assertNotEquals(first, second);
    }
}
