package ru.greatdragonx.astralspace.client.gui.render.preview;

import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.state.pip.PictureInPictureRenderState;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import ru.greatdragonx.astralspace.common.machine.MachineSide;

import java.util.Objects;

public record MachinePreviewRenderState(
    int x0,
    int x1,
    int y0,
    int y1,
    float scale,
    float rotationX,
    float rotationY,
    @Nullable ScreenRectangle scissorArea,
    @Nullable ScreenRectangle bounds,
    @NonNull BlockState blockState,
    @Nullable MachineSide selectedSide
) implements PictureInPictureRenderState
{

    public MachinePreviewRenderState {
        Objects.requireNonNull(
            blockState,
            "blockState cannot be null"
        );
        if (x1 <= x0 || y1 <= y0) {
            throw new IllegalArgumentException("Preview dimensions must be positive");
        }

        if (!Float.isFinite(scale) || scale <= 0.0f) {
            throw new IllegalArgumentException("scale must be finite and positive");
        }

        if (!Float.isFinite(rotationX)) {
            throw new IllegalArgumentException("rotationX must be finite");
        }

        if (!Float.isFinite(rotationY)) {
            throw new IllegalArgumentException("rotationY must be finite");
        }
    }

    public MachinePreviewRenderState(
        int x,
        int y,
        int width,
        int height,
        float rotationX,
        float rotationY,
        @Nullable ScreenRectangle scissorArea,
        @NonNull BlockState blockState,
        @Nullable MachineSide selectedSide
    )
    {
        this(
            x,
            x + width,
            y,
            y + height,
            1.0f,
            rotationX,
            rotationY,
            scissorArea,
            PictureInPictureRenderState.getBounds(
                x,
                y,
                x + width,
                y + height,
                scissorArea
            ),
            blockState,
            selectedSide
        );
    }
}
