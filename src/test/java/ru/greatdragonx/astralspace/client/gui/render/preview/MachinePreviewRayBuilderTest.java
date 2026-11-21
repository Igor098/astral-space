package ru.greatdragonx.astralspace.client.gui.render.preview;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector2d;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MachinePreviewRayBuilderTest {
    private final MachinePreviewRayBuilder builder = new MachinePreviewRayBuilder();

    @Test
    void shouldConvertCenterToZero() {
        Vector2d result = builder.toNdc(
            150,
            100,
            100,
            50,
            200,
            150
        );

        assertEquals(
            0.0,
            result.x,
            1e-9
        );
        assertEquals(
            0.0,
            result.y,
            1e-9
        );
    }

    @Test
    void shouldConvertTopLeftCorner() {
        Vector2d result = builder.toNdc(
            100,
            50,
            100,
            50,
            200,
            150
        );

        assertEquals(
            -1.0,
            result.x,
            1e-9
        );
        assertEquals(
            1.0,
            result.y,
            1e-9
        );
    }

    @Test
    void shouldConvertBottomRightCorner() {
        Vector2d result = builder.toNdc(
            200,
            150,
            100,
            50,
            200,
            150
        );

        assertEquals(
            1.0,
            result.x,
            1e-9
        );
        assertEquals(
            -1.0,
            result.y,
            1e-9
        );
    }

    @Test
    void shouldConvertArbitraryPosition() {
        Vector2d result = builder.toNdc(
            125,
            75,
            100,
            50,
            200,
            150
        );

        assertEquals(
            -0.5,
            result.x,
            1e-9
        );
        assertEquals(
            0.5,
            result.y,
            1e-9
        );
    }

    @Test
    void shouldRejectInvalidPreviewDimensions() {
        assertThrows(
            IllegalArgumentException.class,
            () -> builder.toNdc(
                100,
                100,
                100,
                50,
                100,
                150
            )
        );

        assertThrows(
            IllegalArgumentException.class,
            () -> builder.toNdc(
                100,
                100,
                100,
                50,
                200,
                50
            )
        );
    }

    @Test
    void shouldConvertPointToLocalCoordinates() {
        Matrix4f matrix = new Matrix4f().translate(
                10.0f,
                0.0f,
                0.0f
            )
            .scale(2.0f);

        Vector3f point = new Vector3f(
            12.0f,
            2.0f,
            2.0f
        );

        Vector3f result = builder.toBlockLocal(
            point,
            matrix
        );

        assertEquals(
            1.0f,
            result.x,
            1e-5f
        );
        assertEquals(
            1.0f,
            result.y,
            1e-5f
        );
        assertEquals(
            1.0f,
            result.z,
            1e-5f
        );
    }

    @Test
    void shouldHandleRotation() {
        Matrix4f matrix = new Matrix4f().rotateY((float) Math.toRadians(90));

        Vector3f point = new Vector3f(
            0.0f,
            0.0f,
            -1.0f
        );

        Vector3f result = builder.toBlockLocal(
            point,
            matrix
        );

        assertEquals(
            1.0f,
            result.x,
            1e-5f
        );
        assertEquals(
            0.0f,
            result.y,
            1e-5f
        );
        assertEquals(
            0.0f,
            result.z,
            1e-5f
        );
    }

    @Test
    void shouldHandleCombinedTransformations() {
        Matrix4f matrix = new Matrix4f().translate(
                10.0f,
                -32.0f,
                0.0f
            )
            .scale(
                28.0f,
                -28.0f,
                28.0f
            )
            .rotateX((float) Math.toRadians(30))
            .rotateY((float) Math.toRadians(45))
            .translate(
                -0.5f,
                -0.5f,
                -0.5f
            );

        Vector3f originalPoint = new Vector3f(
            0.25f,
            0.75f,
            1.0f
        );

        Vector3f transformedPoint = matrix.transformPosition(new Vector3f(originalPoint));

        Vector3f result = builder.toBlockLocal(
            transformedPoint,
            matrix
        );

        assertEquals(
            originalPoint.x,
            result.x,
            1e-4f
        );
        assertEquals(
            originalPoint.y,
            result.y,
            1e-4f
        );
        assertEquals(
            originalPoint.z,
            result.z,
            1e-4f
        );
    }

    @Test
    void shouldNotModifyInputObjects() {
        Matrix4f matrix = new Matrix4f().translate(
                10.0f,
                5.0f,
                -3.0f
            )
            .scale(2.0f);

        Vector3f point = new Vector3f(
            12.0f,
            7.0f,
            -1.0f
        );

        Matrix4f originalMatrix = new Matrix4f(matrix);
        Vector3f originalPoint = new Vector3f(point);

        builder.toBlockLocal(
            point,
            matrix
        );

        assertEquals(
            originalMatrix,
            matrix
        );
        assertEquals(
            originalPoint,
            point
        );
    }

    @Test
    void shouldConvertNdcToRenderSpace() {
        Matrix4f projection = new Matrix4f().ortho(
            -4.0f,
            4.0f,
            -2.0f,
            2.0f,
            1.0f,
            11.0f
        );

        Vector3f ndcPoint = new Vector3f(
            0.5f,
            -0.5f,
            0.0f
        );

        Vector3f result = builder.toRenderSpace(
            ndcPoint,
            projection
        );

        assertEquals(
            2.0f,
            result.x,
            1e-5f
        );
        assertEquals(
            -1.0f,
            result.y,
            1e-5f
        );
        assertEquals(
            -6.0f,
            result.z,
            1e-5f
        );
    }

    @Test
    void shouldConvertNearAndFarPlanes() {
        Matrix4f projection = new Matrix4f().ortho(
            -4.0f,
            4.0f,
            -2.0f,
            2.0f,
            1.0f,
            11.0f
        );

        Vector3f near = builder.toRenderSpace(
            new Vector3f(
                0.0f,
                0.0f,
                -1.0f
            ),
            projection
        );

        Vector3f far = builder.toRenderSpace(
            new Vector3f(
                0.0f,
                0.0f,
                1.0f
            ),
            projection
        );

        assertEquals(
            -1.0f,
            near.z,
            1e-5f
        );
        assertEquals(
            -11.0f,
            far.z,
            1e-5f
        );
    }

    @Test
    void shouldHandleOffCenterProjection() {
        Matrix4f projection = new Matrix4f().ortho(
            -2.0f,
            6.0f,
            -4.0f,
            2.0f,
            1.0f,
            9.0f
        );

        Vector3f result = builder.toRenderSpace(
            new Vector3f(
                0.0f,
                0.0f,
                -1.0f
            ),
            projection
        );

        assertEquals(
            2.0f,
            result.x,
            1e-5f
        );
        assertEquals(
            -1.0f,
            result.y,
            1e-5f
        );
        assertEquals(
            -1.0f,
            result.z,
            1e-5f
        );
    }

    @Test
    void shouldRestoreOriginalPointAfterProjection() {
        Matrix4f projection = new Matrix4f().ortho(
            -4.0f,
            4.0f,
            -2.0f,
            2.0f,
            1.0f,
            11.0f
        );

        Vector3f original = new Vector3f(
            2.0f,
            -1.0f,
            -4.0f
        );

        Vector3f ndc = projection.transformProject(new Vector3f(original));

        Vector3f restored = builder.toRenderSpace(
            ndc,
            projection
        );

        assertEquals(
            original.x,
            restored.x,
            1e-5f
        );
        assertEquals(
            original.y,
            restored.y,
            1e-5f
        );
        assertEquals(
            original.z,
            restored.z,
            1e-5f
        );
    }

    @Test
    void shouldNotModifyProjectionInputs() {
        Matrix4f projection = new Matrix4f().ortho(
            -4.0f,
            4.0f,
            -2.0f,
            2.0f,
            1.0f,
            11.0f
        );

        Vector3f point = new Vector3f(
            0.5f,
            -0.5f,
            0.0f
        );

        Matrix4f originalProjection = new Matrix4f(projection);
        Vector3f originalPoint = new Vector3f(point);

        builder.toRenderSpace(
            point,
            projection
        );

        assertEquals(
            originalProjection,
            projection
        );
        assertEquals(
            originalPoint,
            point
        );
    }

    @Test
    void shouldConvertNearPointWithIdentityMatrices() {
        Matrix4f projection = new Matrix4f();
        Matrix4f model = new Matrix4f();

        Vec3 result = builder.toBlockRayPoint(
            new Vector2d(
                0.5,
                -0.25
            ),
            -1.0f,
            projection,
            model
        );

        assertEquals(
            0.5,
            result.x,
            1e-6
        );
        assertEquals(
            -0.25,
            result.y,
            1e-6
        );
        assertEquals(
            -1.0,
            result.z,
            1e-6
        );
    }

    @Test
    void shouldConvertFarPointWithIdentityMatrices() {
        Matrix4f projection = new Matrix4f();
        Matrix4f model = new Matrix4f();

        Vec3 result = builder.toBlockRayPoint(
            new Vector2d(
                0.5,
                -0.25
            ),
            1.0f,
            projection,
            model
        );

        assertEquals(
            0.5,
            result.x,
            1e-6
        );
        assertEquals(
            -0.25,
            result.y,
            1e-6
        );
        assertEquals(
            1.0,
            result.z,
            1e-6
        );
    }

    @Test
    void shouldApplyInverseProjectionAndModelMatrix() {
        Matrix4f projection = new Matrix4f().ortho(
            -4.0f,
            4.0f,
            -2.0f,
            2.0f,
            1.0f,
            11.0f
        );

        Matrix4f model = new Matrix4f().translate(
                1.0f,
                -2.0f,
                -3.0f
            )
            .scale(2.0f);

        Vec3 result = builder.toBlockRayPoint(
            new Vector2d(
                0.5,
                -0.5
            ),
            0.0f,
            projection,
            model
        );

        assertEquals(
            0.5,
            result.x,
            1e-5
        );
        assertEquals(
            0.5,
            result.y,
            1e-5
        );
        assertEquals(
            -1.5,
            result.z,
            1e-5
        );
    }

    @Test
    void shouldRestorePointWithRotatedModel() {
        Matrix4f projection = new Matrix4f().ortho(
            -60.0f,
            60.0f,
            -60.0f,
            60.0f,
            -100.0f,
            100.0f
        );

        Matrix4f model = new Matrix4f().translate(
                0.0f,
                -32.0f,
                0.0f
            )
            .scale(
                28.0f,
                -28.0f,
                28.0f
            )
            .rotateX((float) Math.toRadians(30))
            .rotateY((float) Math.toRadians(45))
            .translate(
                -0.5f,
                -0.5f,
                -0.5f
            );

        Vector3f original = new Vector3f(
            0.25f,
            0.75f,
            0.5f
        );

        Vector3f renderPoint = model.transformPosition(new Vector3f(original));

        Vector3f ndcPoint = projection.transformProject(new Vector3f(renderPoint));

        Vec3 result = builder.toBlockRayPoint(
            new Vector2d(
                ndcPoint.x,
                ndcPoint.y
            ),
            ndcPoint.z,
            projection,
            model
        );

        assertEquals(
            original.x,
            result.x,
            1e-4
        );
        assertEquals(
            original.y,
            result.y,
            1e-4
        );
        assertEquals(
            original.z,
            result.z,
            1e-4
        );
    }

    @Test
    void shouldNotModifyInputsWhenBuildingRayPoint() {
        Vector2d ndc = new Vector2d(
            0.25,
            -0.75
        );

        Matrix4f projection = new Matrix4f().ortho(
            -4.0f,
            4.0f,
            -2.0f,
            2.0f,
            1.0f,
            11.0f
        );

        Matrix4f model = new Matrix4f().translate(
                2.0f,
                3.0f,
                4.0f
            )
            .scale(2.0f);

        Vector2d originalNdc = new Vector2d(ndc);
        Matrix4f originalProjection = new Matrix4f(projection);
        Matrix4f originalModel = new Matrix4f(model);

        builder.toBlockRayPoint(
            ndc,
            -1.0f,
            projection,
            model
        );

        assertEquals(
            originalNdc,
            ndc
        );
        assertEquals(
            originalProjection,
            projection
        );
        assertEquals(
            originalModel,
            model
        );
    }

    @Test
    void shouldBuildRayWithIdentityMatrices() {
        MachinePreviewRay ray = builder.buildRay(
            new Vector2d(
                0.25,
                -0.5
            ),
            -1.0f,
            1.0f,
            new Matrix4f(),
            new Matrix4f()
        );

        assertEquals(
            0.25,
            ray.start().x,
            1e-6
        );
        assertEquals(
            -0.5,
            ray.start().y,
            1e-6
        );
        assertEquals(
            -1.0,
            ray.start().z,
            1e-6
        );

        assertEquals(
            0.25,
            ray.end().x,
            1e-6
        );
        assertEquals(
            -0.5,
            ray.end().y,
            1e-6
        );
        assertEquals(
            1.0,
            ray.end().z,
            1e-6
        );
    }

    @Test
    void shouldBuildRayWithProjectionAndModelMatrix() {
        Matrix4f projection = new Matrix4f().ortho(
            -4,
            4,
            -2,
            2,
            1,
            11
        );

        Matrix4f model = new Matrix4f().translate(
                1,
                -2,
                -3
            )
            .scale(2);

        MachinePreviewRay ray = builder.buildRay(
            new Vector2d(
                0.5,
                -0.5
            ),
            -1.0f,
            1.0f,
            projection,
            model
        );

        assertEquals(
            0.5,
            ray.start().x,
            1e-5
        );
        assertEquals(
            0.5,
            ray.start().y,
            1e-5
        );
        assertEquals(
            1.0,
            ray.start().z,
            1e-5
        );

        assertEquals(
            0.5,
            ray.end().x,
            1e-5
        );
        assertEquals(
            0.5,
            ray.end().y,
            1e-5
        );
        assertEquals(
            -4.0,
            ray.end().z,
            1e-5
        );
    }

    @Test
    void shouldNotModifyInputsWhenBuildingRay() {
        Vector2d ndc = new Vector2d(
            0.25,
            -0.5
        );

        Matrix4f projection = new Matrix4f().ortho(
            -4,
            4,
            -2,
            2,
            1,
            11
        );

        Matrix4f model = new Matrix4f().translate(
                1,
                2,
                3
            )
            .scale(2);

        Vector2d originalNdc = new Vector2d(ndc);
        Matrix4f originalProjection = new Matrix4f(projection);
        Matrix4f originalModel = new Matrix4f(model);

        builder.buildRay(
            ndc,
            -1.0f,
            1.0f,
            projection,
            model
        );

        assertEquals(
            originalNdc,
            ndc
        );
        assertEquals(
            originalProjection,
            projection
        );
        assertEquals(
            originalModel,
            model
        );
    }

    @Test
    void shouldDetectNorthFaceUsingBuiltRay() {
        MachinePreviewRay ray = builder.buildRay(
            new Vector2d(
                0.5,
                0.5
            ),
            -1.0f,
            1.0f,
            new Matrix4f(),
            new Matrix4f()
        );

        Direction hit = MachinePreviewRaycaster.detectHitDirection(
            ray.start(),
            ray.end()
        );

        assertEquals(
            Direction.NORTH,
            hit
        );
    }

    @Test
    void shouldDetectWestFaceWithRotatedModel() {
        Matrix4f projection = new Matrix4f().ortho(
            -4,
            4,
            -4,
            4,
            1,
            11
        );

        Matrix4f model = new Matrix4f().translate(
                0,
                0,
                -6
            )
            .rotateY((float) Math.toRadians(90))
            .translate(
                -0.5f,
                -0.5f,
                -0.5f
            );

        MachinePreviewRay ray = builder.buildRay(
            new Vector2d(
                0,
                0
            ),
            -1.0f,
            1.0f,
            projection,
            model
        );

        Direction hit = MachinePreviewRaycaster.detectHitDirection(
            ray.start(),
            ray.end()
        );

        assertEquals(
            Direction.WEST,
            hit
        );
    }
}
