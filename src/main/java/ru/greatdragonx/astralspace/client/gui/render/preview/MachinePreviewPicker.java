package ru.greatdragonx.astralspace.client.gui.render.preview;

import net.minecraft.core.Direction;
import org.joml.Matrix4f;
import org.joml.Vector2d;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

public final class MachinePreviewPicker {
    private final MachinePreviewRayBuilder rayBuilder = new MachinePreviewRayBuilder();

    public @Nullable Direction detectHitDirection(
        double mouseX,
        double mouseY,
        @NonNull MachinePreviewRenderState state,
        int guiScale
    )
    {
        Objects.requireNonNull(
            state,
            "state cannot be null"
        );
        if (guiScale <= 0) {
            throw new IllegalArgumentException("guiScale must be positive");
        }

        boolean inArea = mouseX >= state.x0() &&
            mouseX < state.x1() &&
            mouseY >= state.y0() &&
            mouseY < state.y1();

        if (!inArea) {
            return null;
        }

        Vector2d ndc = rayBuilder.toNdc(
            mouseX,
            mouseY,
            state.x0(),
            state.y0(),
            state.x1(),
            state.y1()
        );

        Matrix4f projectionMatrix = MachinePreviewMatrices.createProjectionMatrix(
            state,
            guiScale
        );
        Matrix4f modelMatrix = MachinePreviewMatrices.createModelMatrix(
            state,
            guiScale
        );

        MachinePreviewRay ray = rayBuilder.buildRay(
            ndc,
            1.0f,
            -1.0f,
            projectionMatrix,
            modelMatrix
        );

        return MachinePreviewRaycaster.detectHitDirection(
            ray.start(),
            ray.end()
        );
    }
}
