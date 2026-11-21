package ru.greatdragonx.astralspace.client.gui.render.preview;

import org.joml.Matrix4f;
import org.jspecify.annotations.NonNull;

public final class MachinePreviewMatrices {
    private MachinePreviewMatrices() {}

    public static @NonNull Matrix4f createProjectionMatrix(
        @NonNull MachinePreviewRenderState state,
        int guiScale
    )
    {
        if (guiScale <= 0) {
            throw new IllegalArgumentException("guiScale must be positive");
        }

        int width = (state.x1() - state.x0()) * guiScale;
        int height = (state.y1() - state.y0()) * guiScale;

        return new Matrix4f().setOrtho(0.0f, width, height, 0.0f, -1000.0f, 1000.0f);
    }

    public static @NonNull Matrix4f createModelMatrix(
        @NonNull MachinePreviewRenderState state,
        int guiScale
    )
    {
        if (guiScale <= 0) {
            throw new IllegalArgumentException("guiScale must be positive");
        }

        int width = (state.x1() - state.x0()) * guiScale;
        int height = (state.y1() - state.y0()) * guiScale;
        Matrix4f matrix = new Matrix4f();

        float f = guiScale * state.scale();

        matrix.translate(width / 2.0f, height, 0.0f);
        matrix.scale(f, f, -f);

        matrix.translate(
            MachinePreviewTransform.MODEL_TRANSLATION_X,
            MachinePreviewTransform.MODEL_TRANSLATION_Y,
            MachinePreviewTransform.MODEL_TRANSLATION_Z
        );
        matrix.scale(
            MachinePreviewTransform.MODEL_SCALE_X,
            MachinePreviewTransform.MODEL_SCALE_Y,
            MachinePreviewTransform.MODEL_SCALE_Z
        );

        matrix.rotateX((float) Math.toRadians(state.rotationX()));
        matrix.rotateY((float) Math.toRadians(state.rotationY()));

        matrix.translate(
            MachinePreviewTransform.BLOCK_CENTER_OFFSET,
            MachinePreviewTransform.BLOCK_CENTER_OFFSET,
            MachinePreviewTransform.BLOCK_CENTER_OFFSET
        );

        return matrix;
    }
}
