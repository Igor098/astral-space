package ru.greatdragonx.astralspace.client.gui.render.preview;

import net.minecraft.core.Direction;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class MachinePreviewFaceGeometryTest {
    private static final float EPSILON = 0.000001f;

    @Test
    void shouldRejectNullDirection() {
        assertThrows(
            NullPointerException.class,
            () -> MachinePreviewFaceGeometry.getFaceVertices(null)
        );
    }

    @ParameterizedTest
    @EnumSource(Direction.class)
    void shouldReturnExactlyFourDistinctVertices(Direction direction) {
        List<Vector3f> vertices = MachinePreviewFaceGeometry.getFaceVertices(direction);
        assertNotNull(vertices);
        assertEquals(4, vertices.size());
        vertices.forEach(vertex -> assertNotNull(vertex));
        assertEquals(4, new HashSet<>(vertices).size());
    }

    @ParameterizedTest
    @EnumSource(Direction.class)
    void shouldReturnOnlyUnitCubeCorners(Direction direction) {
        for (Vector3f vertex : MachinePreviewFaceGeometry.getFaceVertices(direction)) {
            assertTrue(vertex.x == 0.0f || vertex.x == 1.0f, "Invalid X: " + vertex);
            assertTrue(vertex.y == 0.0f || vertex.y == 1.0f, "Invalid Y: " + vertex);
            assertTrue(vertex.z == 0.0f || vertex.z == 1.0f, "Invalid Z: " + vertex);
        }
    }

    @ParameterizedTest
    @EnumSource(Direction.class)
    void shouldPlaceAllVerticesOnRequestedFace(Direction direction) {
        for (Vector3f vertex : MachinePreviewFaceGeometry.getFaceVertices(direction)) {
            float actual = switch (direction) {
                case NORTH, SOUTH -> vertex.z;
                case WEST, EAST -> vertex.x;
                case DOWN, UP -> vertex.y;
            };
            float expected = switch (direction) {
                case NORTH, WEST, DOWN -> 0.0f;
                case SOUTH, EAST, UP -> 1.0f;
            };
            assertEquals(expected, actual, EPSILON, "Wrong face plane: " + vertex);
        }
    }

    @ParameterizedTest
    @EnumSource(Direction.class)
    void shouldFormUnitSquareInPerimeterOrder(Direction direction) {
        List<Vector3f> vertices = MachinePreviewFaceGeometry.getFaceVertices(direction);
        for (int i = 0; i < 4; i++) {
            Vector3f a = vertices.get(i);
            Vector3f b = vertices.get((i + 1) % 4);
            Vector3f c = vertices.get((i + 2) % 4);
            Vector3f ab = new Vector3f(b).sub(a);
            Vector3f bc = new Vector3f(c).sub(b);
            assertEquals(1.0f, ab.lengthSquared(), EPSILON, "Edge " + i);
            assertEquals(0.0f, ab.dot(bc), EPSILON, "Corner " + i);
        }
    }

    @ParameterizedTest
    @EnumSource(Direction.class)
    void shouldHaveOutwardNormalAtEveryCorner(Direction direction) {
        List<Vector3f> vertices = MachinePreviewFaceGeometry.getFaceVertices(direction);
        Vector3f expected = switch (direction) {
            case NORTH -> new Vector3f(0, 0, -1);
            case SOUTH -> new Vector3f(0, 0, 1);
            case EAST -> new Vector3f(1, 0, 0);
            case WEST -> new Vector3f(-1, 0, 0);
            case UP -> new Vector3f(0, 1, 0);
            case DOWN -> new Vector3f(0, -1, 0);
        };
        for (int i = 0; i < 4; i++) {
            Vector3f a = vertices.get(i);
            Vector3f b = vertices.get((i + 1) % 4);
            Vector3f c = vertices.get((i + 2) % 4);
            Vector3f u = new Vector3f(b).sub(a);
            Vector3f v = new Vector3f(c).sub(a);
            Vector3f normal = u.cross(v);
            assertVectorEquals(expected, normal);
        }
    }

    @ParameterizedTest
    @CsvSource({"NORTH, 1,0,0, -1,0,0, 0,1,0", "SOUTH, 0,0,1,  1,0,0, 0,1,0", "EAST,  1,0,1,  0,0,-1, 0,1,0", "WEST,  0,0,0,  0,0,1, 0,1,0", "UP,    0,1,1,  1,0,0, 0,0,-1", "DOWN,  0,0,0,  1,0,0, 0,0,1"})
    void shouldFollowAgreedOutsideViewCornerOrder(
        Direction direction,
        float ax,
        float ay,
        float az,
        float rx,
        float ry,
        float rz,
        float ux,
        float uy,
        float uz
    )
    {
        List<Vector3f> vertices = MachinePreviewFaceGeometry.getFaceVertices(direction);
        Vector3f lowerLeft = new Vector3f(ax, ay, az);
        Vector3f right = new Vector3f(rx, ry, rz);
        Vector3f up = new Vector3f(ux, uy, uz);

        assertVectorEquals(lowerLeft, vertices.get(0));
        assertVectorEquals(new Vector3f(lowerLeft).add(right), vertices.get(1));
        assertVectorEquals(
            new Vector3f(lowerLeft).add(right)
                .add(up), vertices.get(2)
        );
        assertVectorEquals(new Vector3f(lowerLeft).add(up), vertices.get(3));
    }

    @ParameterizedTest
    @EnumSource(Direction.class)
    void shouldReturnStructurallyUnmodifiableList(Direction direction) {
        List<Vector3f> vertices = MachinePreviewFaceGeometry.getFaceVertices(direction);
        assertThrows(UnsupportedOperationException.class, () -> vertices.add(new Vector3f()));
        assertThrows(UnsupportedOperationException.class, () -> vertices.remove(0));
        assertThrows(UnsupportedOperationException.class, () -> vertices.set(0, new Vector3f()));
    }

    @ParameterizedTest
    @EnumSource(Direction.class)
    void shouldReturnIndependentVertexInstancesOnEveryCall(Direction direction) {
        List<Vector3f> first = MachinePreviewFaceGeometry.getFaceVertices(direction);
        List<Vector3f> second = MachinePreviewFaceGeometry.getFaceVertices(direction);
        for (int i = 0; i < 4; i++) {
            assertVectorEquals(first.get(i), second.get(i));
            for (Vector3f other : second) {
                assertNotSame(first.get(i), other);
            }
        }
    }

    @ParameterizedTest
    @EnumSource(Direction.class)
    void shouldNotLeakVertexMutationsToOtherCornersOrCalls(Direction direction) {
        List<Vector3f> vertices = MachinePreviewFaceGeometry.getFaceVertices(direction);
        List<Vector3f> original = vertices.stream()
            .map(Vector3f::new)
            .toList();
        List<Vector3f> anotherCall = MachinePreviewFaceGeometry.getFaceVertices(direction);

        vertices.get(0)
            .add(2.0f, 3.0f, 4.0f);
        assertVectorEquals(new Vector3f(original.get(0)).add(2.0f, 3.0f, 4.0f), vertices.get(0));
        for (int i = 1; i < 4; i++) {
            assertVectorEquals(original.get(i), vertices.get(i));
        }
        List<Vector3f> laterCall = MachinePreviewFaceGeometry.getFaceVertices(direction);
        for (int i = 0; i < 4; i++) {
            assertVectorEquals(original.get(i), anotherCall.get(i));
            assertVectorEquals(original.get(i), laterCall.get(i));
        }
    }

    @Test
    void shouldRejectNullDirectionForNormal() {
        assertThrows(
            NullPointerException.class,
            () -> MachinePreviewFaceGeometry.getFaceNormal(null)
        );
    }

    @ParameterizedTest
    @CsvSource({"NORTH, 0, 0, -1", "SOUTH, 0, 0, 1", "EAST, 1, 0, 0", "WEST, -1, 0, 0", "UP, 0, 1, 0", "DOWN, 0, -1, 0"})
    void shouldReturnExpectedUnitNormal(Direction direction, float x, float y, float z) {
        Vector3f normal = MachinePreviewFaceGeometry.getFaceNormal(direction);
        assertNotNull(normal);
        assertVectorEquals(new Vector3f(x, y, z), normal);
        assertEquals(1.0f, normal.lengthSquared(), EPSILON);
    }

    @ParameterizedTest
    @EnumSource(Direction.class)
    void shouldReturnIndependentNormalInstances(Direction direction) {
        Vector3f first = MachinePreviewFaceGeometry.getFaceNormal(direction);
        Vector3f second = MachinePreviewFaceGeometry.getFaceNormal(direction);
        Vector3f original = new Vector3f(first);
        assertNotSame(first, second);
        assertVectorEquals(original, second);
        first.set(4.0f, 5.0f, 6.0f);
        assertVectorEquals(original, second);
        assertVectorEquals(original, MachinePreviewFaceGeometry.getFaceNormal(direction));
    }

    @Test
    void shouldRejectNullDirectionForOffsetVertices() {
        assertThrows(
            NullPointerException.class,
            () -> MachinePreviewFaceGeometry.getOffsetFaceVertices(null, 0.001f)
        );
    }

    @ParameterizedTest
    @ValueSource(floats = {-0.001f, -Float.MIN_VALUE, Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY})
    void shouldRejectInvalidOffsets(float offset) {
        assertThrows(
            IllegalArgumentException.class,
            () -> MachinePreviewFaceGeometry.getOffsetFaceVertices(Direction.NORTH, offset)
        );
    }

    @ParameterizedTest
    @EnumSource(Direction.class)
    void shouldPreserveCoordinatesAtZeroOffset(Direction direction) {
        List<Vector3f> original = MachinePreviewFaceGeometry.getFaceVertices(direction);
        for (float offset : new float[]{0.0f, -0.0f}) {
            List<Vector3f> shifted = MachinePreviewFaceGeometry.getOffsetFaceVertices(
                direction,
                offset
            );
            assertEquals(4, shifted.size());
            for (int i = 0; i < 4; i++) {
                assertVectorEquals(original.get(i), shifted.get(i));
                assertNotSame(original.get(i), shifted.get(i));
            }
        }
    }

    private static Stream<Arguments> directionsAndPositiveOffsets() {
        return Arrays.stream(Direction.values())
            .flatMap(direction -> Stream.of(0.001f, 0.25f, 2.0f)
                .map(offset -> Arguments.of(direction, offset)));
    }

    @ParameterizedTest
    @MethodSource("directionsAndPositiveOffsets")
    void shouldTranslateEveryVertexOutwardWithoutReordering(Direction direction, float offset) {
        List<Vector3f> original = MachinePreviewFaceGeometry.getFaceVertices(direction);
        List<Vector3f> shifted = MachinePreviewFaceGeometry.getOffsetFaceVertices(
            direction,
            offset
        );
        Vector3f expectedDisplacement = expectedOutwardDisplacement(direction, offset);

        assertEquals(4, shifted.size());
        assertEquals(4, new HashSet<>(shifted).size());
        for (int i = 0; i < 4; i++) {
            assertVectorEquals(
                new Vector3f(original.get(i)).add(expectedDisplacement),
                shifted.get(i)
            );
            assertVectorEquals(
                expectedDisplacement,
                new Vector3f(shifted.get(i)).sub(original.get(i))
            );
            assertEquals(
                offset,
                new Vector3f(shifted.get(i)).sub(original.get(i))
                    .length(),
                EPSILON
            );
        }
    }

    @ParameterizedTest
    @EnumSource(Direction.class)
    void shouldPreserveFaceShapeAndOutwardWindingAfterOffset(Direction direction) {
        List<Vector3f> vertices = MachinePreviewFaceGeometry.getOffsetFaceVertices(
            direction,
            0.001f
        );
        Vector3f expectedNormal = expectedOutwardDisplacement(direction, 1.0f);
        for (int i = 0; i < 4; i++) {
            Vector3f a = vertices.get(i);
            Vector3f b = vertices.get((i + 1) % 4);
            Vector3f c = vertices.get((i + 2) % 4);
            Vector3f ab = new Vector3f(b).sub(a);
            Vector3f bc = new Vector3f(c).sub(b);
            assertEquals(1.0f, ab.lengthSquared(), EPSILON);
            assertEquals(0.0f, ab.dot(bc), EPSILON);
            assertVectorEquals(expectedNormal, ab.cross(bc));
        }
        assertEquals(
            2.0f,
            new Vector3f(vertices.get(2)).sub(vertices.get(0))
                .lengthSquared(),
            EPSILON
        );
        assertEquals(
            2.0f,
            new Vector3f(vertices.get(3)).sub(vertices.get(1))
                .lengthSquared(),
            EPSILON
        );
    }

    @ParameterizedTest
    @EnumSource(Direction.class)
    void shouldReturnStructurallyUnmodifiableOffsetList(Direction direction) {
        List<Vector3f> vertices = MachinePreviewFaceGeometry.getOffsetFaceVertices(
            direction,
            0.001f
        );
        assertThrows(UnsupportedOperationException.class, () -> vertices.add(new Vector3f()));
        assertThrows(UnsupportedOperationException.class, () -> vertices.remove(0));
        assertThrows(UnsupportedOperationException.class, () -> vertices.set(0, new Vector3f()));
    }

    @ParameterizedTest
    @EnumSource(Direction.class)
    void shouldNotLeakOffsetOrVertexMutationsAcrossCalls(Direction direction) {
        List<Vector3f> original = MachinePreviewFaceGeometry.getFaceVertices(direction);
        List<Vector3f> snapshot = original.stream()
            .map(Vector3f::new)
            .toList();
        List<Vector3f> first = MachinePreviewFaceGeometry.getOffsetFaceVertices(direction, 0.001f);
        List<Vector3f> second = MachinePreviewFaceGeometry.getOffsetFaceVertices(direction, 0.001f);
        List<Vector3f> firstSnapshot = first.stream()
            .map(Vector3f::new)
            .toList();
        for (int i = 0; i < 4; i++) {
            assertVectorEquals(snapshot.get(i), original.get(i));
            assertVectorEquals(firstSnapshot.get(i), second.get(i));
            for (Vector3f other : second) {
                assertNotSame(first.get(i), other);
            }
            for (Vector3f other : original) {
                assertNotSame(first.get(i), other);
            }
        }
        first.get(0)
            .add(2.0f, 3.0f, 4.0f);
        List<Vector3f> laterOffset = MachinePreviewFaceGeometry.getOffsetFaceVertices(
            direction,
            0.001f
        );
        List<Vector3f> laterOriginal = MachinePreviewFaceGeometry.getFaceVertices(direction);
        for (int i = 0; i < 4; i++) {
            assertVectorEquals(snapshot.get(i), original.get(i));
            assertVectorEquals(snapshot.get(i), laterOriginal.get(i));
            assertVectorEquals(firstSnapshot.get(i), second.get(i));
            assertVectorEquals(firstSnapshot.get(i), laterOffset.get(i));
            if (i != 0) {
                assertVectorEquals(firstSnapshot.get(i), first.get(i));
            }
        }
    }

    private static Vector3f expectedOutwardDisplacement(Direction direction, float offset) {
        return switch (direction) {
            case NORTH -> new Vector3f(0, 0, -offset);
            case SOUTH -> new Vector3f(0, 0, offset);
            case EAST -> new Vector3f(offset, 0, 0);
            case WEST -> new Vector3f(-offset, 0, 0);
            case UP -> new Vector3f(0, offset, 0);
            case DOWN -> new Vector3f(0, -offset, 0);
        };
    }

    private static void assertVectorEquals(Vector3f expected, Vector3f actual) {
        assertEquals(expected.x, actual.x, EPSILON, "X component");
        assertEquals(expected.y, actual.y, EPSILON, "Y component");
        assertEquals(expected.z, actual.z, EPSILON, "Z component");
    }
}
