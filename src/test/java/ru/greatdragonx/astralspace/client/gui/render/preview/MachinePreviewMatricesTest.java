package ru.greatdragonx.astralspace.client.gui.render.preview;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.world.level.block.Blocks;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MachinePreviewMatricesTest {
    @Test
    void shouldProjectCenterWithGuiScaleOne() {
        MachinePreviewRenderState state = state(
            100,
            50,
            64,
            64,
            0,
            0
        );

        Matrix4f projection = MachinePreviewMatrices.createProjectionMatrix(
            state,
            1
        );

        Vector3f result = projection.transformProject(new Vector3f(
            32,
            32,
            0
        ));

        assertVector(
            result,
            0,
            0,
            0
        );
    }

    @Test
    void shouldUsePhysicalDimensionsWithGuiScaleTwo() {
        MachinePreviewRenderState state = state(
            0,
            0,
            64,
            64,
            0,
            0
        );

        Matrix4f projection = MachinePreviewMatrices.createProjectionMatrix(
            state,
            2
        );

        Vector3f center = projection.transformProject(new Vector3f(
            64,
            64,
            0
        ));

        Vector3f bottomRight = projection.transformProject(new Vector3f(
            128,
            128,
            0
        ));

        assertVector(
            center,
            0,
            0,
            0
        );
        assertVector(
            bottomRight,
            1,
            -1,
            0
        );
    }

    @Test
    void shouldSupportRectangularPreview() {
        MachinePreviewRenderState state = state(
            0,
            0,
            96,
            64,
            0,
            0
        );

        Matrix4f projection = MachinePreviewMatrices.createProjectionMatrix(
            state,
            2
        );

        Vector3f topLeft = projection.transformProject(new Vector3f(
            0,
            0,
            0
        ));

        Vector3f bottomRight = projection.transformProject(new Vector3f(
            192,
            128,
            0
        ));

        assertVector(
            topLeft,
            -1,
            1,
            0
        );
        assertVector(
            bottomRight,
            1,
            -1,
            0
        );
    }

    @Test
    void shouldInvertYAxis() {
        MachinePreviewRenderState state = state(
            0,
            0,
            64,
            64,
            0,
            0
        );

        Matrix4f projection = MachinePreviewMatrices.createProjectionMatrix(
            state,
            1
        );

        Vector3f top = projection.transformProject(new Vector3f(
            32,
            0,
            0
        ));

        Vector3f bottom = projection.transformProject(new Vector3f(
            32,
            64,
            0
        ));

        assertEquals(
            1.0f,
            top.y,
            1e-5f
        );
        assertEquals(
            -1.0f,
            bottom.y,
            1e-5f
        );
    }

    @Test
    void shouldUseCorrectDepthRange() {
        MachinePreviewRenderState state = state(
            0,
            0,
            64,
            64,
            0,
            0
        );

        Matrix4f projection = MachinePreviewMatrices.createProjectionMatrix(
            state,
            1
        );

        Vector3f near = projection.transformProject(new Vector3f(
            32,
            32,
            1000
        ));

        Vector3f far = projection.transformProject(new Vector3f(
            32,
            32,
            -1000
        ));

        assertEquals(
            -1.0f,
            near.z,
            1e-5f
        );
        assertEquals(
            1.0f,
            far.z,
            1e-5f
        );
    }

    @Test
    void shouldNotDependOnPositionOrRotation() {
        MachinePreviewRenderState first = state(
            100,
            50,
            64,
            64,
            0,
            0
        );

        MachinePreviewRenderState second = state(
            400,
            300,
            64,
            64,
            45,
            90
        );

        Matrix4f firstProjection = MachinePreviewMatrices.createProjectionMatrix(
            first,
            2
        );

        Matrix4f secondProjection = MachinePreviewMatrices.createProjectionMatrix(
            second,
            2
        );

        assertEquals(
            firstProjection,
            secondProjection
        );
    }

    @Test
    void shouldRejectInvalidGuiScale() {
        MachinePreviewRenderState state = state(
            0,
            0,
            64,
            64,
            0,
            0
        );

        assertThrows(
            IllegalArgumentException.class,
            () -> MachinePreviewMatrices.createProjectionMatrix(
                state,
                0
            )
        );

        assertThrows(
            IllegalArgumentException.class,
            () -> MachinePreviewMatrices.createProjectionMatrix(
                state,
                -1
            )
        );
    }


    @Test
    void shouldCenterBlockInPreview() {
        MachinePreviewRenderState state = state(
            100,
            50,
            64,
            64,
            0,
            0
        );

        Matrix4f model = MachinePreviewMatrices.createModelMatrix(
            state,
            1
        );

        Vector3f center = model.transformPosition(new Vector3f(
            0.5f,
            0.5f,
            0.5f
        ));

        assertVector(
            center,
            32,
            32,
            0
        );
    }

    @Test
    void shouldApplyGuiScaleToModel() {
        MachinePreviewRenderState state = state(
            0,
            0,
            64,
            64,
            0,
            0
        );

        Matrix4f model = MachinePreviewMatrices.createModelMatrix(
            state,
            3
        );

        Vector3f center = model.transformPosition(new Vector3f(
            0.5f,
            0.5f,
            0.5f
        ));

        Vector3f right = model.transformPosition(new Vector3f(
            1.0f,
            0.5f,
            0.5f
        ));

        assertVector(
            center,
            96,
            96,
            0
        );
        assertVector(
            right,
            138,
            96,
            0
        );
    }

    @Test
    void shouldSupportRectangularPreviewForModel() {
        MachinePreviewRenderState state = state(
            0,
            0,
            96,
            64,
            0,
            0
        );

        Matrix4f model = MachinePreviewMatrices.createModelMatrix(
            state,
            2
        );

        Vector3f center = model.transformPosition(new Vector3f(
            0.5f,
            0.5f,
            0.5f
        ));

        assertVector(
            center,
            96,
            64,
            0
        );
    }

    @Test
    void shouldPreserveExpectedAxisOrientation() {
        MachinePreviewRenderState state = state(
            0,
            0,
            64,
            64,
            0,
            0
        );

        Matrix4f model = MachinePreviewMatrices.createModelMatrix(
            state,
            1
        );

        Vector3f right = model.transformPosition(new Vector3f(
            1.0f,
            0.5f,
            0.5f
        ));

        Vector3f top = model.transformPosition(new Vector3f(
            0.5f,
            1.0f,
            0.5f
        ));

        Vector3f south = model.transformPosition(new Vector3f(
            0.5f,
            0.5f,
            1.0f
        ));

        assertVector(
            right,
            46,
            32,
            0
        );
        assertVector(
            top,
            32,
            18,
            0
        );
        assertVector(
            south,
            32,
            32,
            -14
        );
    }

    @Test
    void shouldApplyRotationAroundX() {
        MachinePreviewRenderState state = state(
            0,
            0,
            64,
            64,
            90,
            0
        );

        Matrix4f model = MachinePreviewMatrices.createModelMatrix(
            state,
            1
        );

        Vector3f result = model.transformPosition(new Vector3f(
            0.5f,
            1.0f,
            0.5f
        ));

        assertVector(
            result,
            32,
            32,
            -14
        );
    }

    @Test
    void shouldApplyRotationAroundY() {
        MachinePreviewRenderState state = state(
            0,
            0,
            64,
            64,
            0,
            90
        );

        Matrix4f model = MachinePreviewMatrices.createModelMatrix(
            state,
            1
        );

        Vector3f result = model.transformPosition(new Vector3f(
            0.5f,
            0.5f,
            1.0f
        ));

        assertVector(
            result,
            46,
            32,
            0
        );
    }

    @Test
    void shouldRespectRenderStateScale() {
        MachinePreviewRenderState state = new MachinePreviewRenderState(
            0,
            64,
            0,
            64,
            1.5f,
            0,
            0,
            null,
            null,
            Blocks.STONE.defaultBlockState(),
            null
        );

        Matrix4f model = MachinePreviewMatrices.createModelMatrix(
            state,
            1
        );

        Vector3f center = model.transformPosition(new Vector3f(
            0.5f,
            0.5f,
            0.5f
        ));

        assertVector(
            center,
            32,
            16,
            0
        );
    }

    @Test
    void shouldNotDependOnAbsoluteGuiPosition() {
        MachinePreviewRenderState first = state(
            0,
            0,
            64,
            64,
            25,
            35
        );

        MachinePreviewRenderState second = state(
            400,
            250,
            64,
            64,
            25,
            35
        );

        Matrix4f firstModel = MachinePreviewMatrices.createModelMatrix(
            first,
            2
        );

        Matrix4f secondModel = MachinePreviewMatrices.createModelMatrix(
            second,
            2
        );

        assertEquals(
            firstModel,
            secondModel
        );
    }

    @Test
    void shouldRejectInvalidGuiScaleForModel() {
        MachinePreviewRenderState state = state(
            0,
            0,
            64,
            64,
            0,
            0
        );

        assertThrows(
            IllegalArgumentException.class,
            () -> MachinePreviewMatrices.createModelMatrix(
                state,
                0
            )
        );

        assertThrows(
            IllegalArgumentException.class,
            () -> MachinePreviewMatrices.createModelMatrix(
                state,
                -1
            )
        );
    }


    @Test
    void shouldMatchActualPoseStackTransformations() {
        MachinePreviewRenderState state = state(
            10,
            20,
            96,
            64,
            25,
            35
        );

        int guiScale = 2;

        Matrix4f actual = MachinePreviewMatrices.createModelMatrix(
            state,
            guiScale
        );

        int width = (state.x1() - state.x0()) * guiScale;
        int height = (state.y1() - state.y0()) * guiScale;
        float f = guiScale * state.scale();

        PoseStack poseStack = new PoseStack();

        poseStack.translate(
            width / 2.0f,
            height,
            0
        );
        poseStack.scale(
            f,
            f,
            -f
        );

        poseStack.translate(
            0,
            -32,
            0
        );
        poseStack.scale(
            28,
            -28,
            28
        );

        poseStack.mulPose(Axis.XP.rotationDegrees(state.rotationX()));

        poseStack.mulPose(Axis.YP.rotationDegrees(state.rotationY()));

        poseStack.translate(
            -0.5,
            -0.5,
            -0.5
        );

        Matrix4f expected = poseStack.last()
            .pose();

        Vector3f[] points = {new Vector3f(
            0.5f,
            0.5f,
            0.5f
        ), new Vector3f(
            0,
            0,
            0
        ), new Vector3f(
            1,
            1,
            1
        ), new Vector3f(
            0.25f,
            0.75f,
            0.5f
        )};

        for (Vector3f point : points) {
            Vector3f expectedPoint = expected.transformPosition(new Vector3f(point));

            Vector3f actualPoint = actual.transformPosition(new Vector3f(point));

            assertVector(
                actualPoint,
                expectedPoint.x,
                expectedPoint.y,
                expectedPoint.z
            );
        }
    }


    private static MachinePreviewRenderState state(
        int x,
        int y,
        int width,
        int height,
        float rotationX,
        float rotationY
    )
    {
        return new MachinePreviewRenderState(
            x,
            x + width,
            y,
            y + height,
            1.0f,
            rotationX,
            rotationY,
            null,
            null,
            Blocks.STONE.defaultBlockState(),
            null
        );
    }

    private static void assertVector(
        Vector3f actual,
        float x,
        float y,
        float z
    )
    {
        assertEquals(
            x,
            actual.x,
            1e-5f
        );
        assertEquals(
            y,
            actual.y,
            1e-5f
        );
        assertEquals(
            z,
            actual.z,
            1e-5f
        );
    }
}
