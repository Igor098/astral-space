package ru.greatdragonx.astralspace.client.gui.components;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import ru.greatdragonx.astralspace.client.gui.render.preview.MachinePreviewPicker;
import ru.greatdragonx.astralspace.client.gui.render.preview.MachinePreviewRenderState;
import ru.greatdragonx.astralspace.common.machine.MachineSide;

public final class GuiMachinePreview {
    private float rotationX = 25.0f;
    private float rotationY = 215.0f;

    private boolean dragging = false;
    private boolean wasDragged = false;

    private double dragStartX;
    private double dragStartY;
    private int x;
    private int y;
    private int size;

    private @Nullable BlockState blockState;
    private @Nullable MachineSide selectedSide;
    private final MachinePreviewPicker previewPicker;

    public record PreviewReleaseResult(
        boolean consumed, boolean clicked, @Nullable Direction hitDirection
    ) {}

    public GuiMachinePreview() {
        this.previewPicker = new MachinePreviewPicker();
    }

    public void render(
        int x,
        int y,
        int size,
        @NonNull GuiGraphics graphics,
        @NonNull BlockState blockState,
        @Nullable MachineSide selectedSide
    )
    {
        this.x = x;
        this.y = y;
        this.size = size;
        this.blockState = blockState;
        this.selectedSide = selectedSide;

        MachinePreviewRenderState state = this.createRenderState(graphics.peekScissorStack());
        graphics.submitPictureInPictureRenderState(state);
    }

    public boolean mouseClicked(
        @NonNull MouseButtonEvent event,
        int x,
        int y,
        int size
    )
    {
        if (event.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return false;
        }

        boolean inPreviewArea =
            event.x() >= x && event.x() < x + size && event.y() >= y && event.y() < y + size;
        if (!inPreviewArea) {
            return false;
        }

        this.dragging = true;
        this.wasDragged = false;
        this.dragStartX = event.x();
        this.dragStartY = event.y();

        return true;
    }

    public boolean mouseDragged(
        @NonNull MouseButtonEvent event,
        double dragX,
        double dragY
    )
    {
        if (event.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT || !this.dragging) {
            return false;
        }

        double distance = Math.hypot(
            event.x() - this.dragStartX,
            event.y() - this.dragStartY
        );

        if (distance > 4.0) {
            this.wasDragged = true;
        }

        if (this.wasDragged) {
            this.rotationX = Math.clamp(
                this.rotationX + (float) (dragY * 0.8f),
                -110.0f,
                110.0f
            );

            this.rotationY += (float) (dragX * 0.8f);
        }

        return true;
    }

    public @NonNull PreviewReleaseResult mouseReleased(
        @NonNull MouseButtonEvent event,
        int guiScale
    )
    {
        if (event.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT || !this.dragging) {
            return new PreviewReleaseResult(
                false,
                false,
                null
            );
        }

        boolean wasDragged = this.wasDragged;

        this.dragging = false;
        this.wasDragged = false;

        if (wasDragged || !this.isInsidePreviewArea(
            event.x(),
            event.y()
        ))
        {
            return new PreviewReleaseResult(
                true,
                false,
                null
            );
        }

        MachinePreviewRenderState state = this.createRenderState(null);

        Direction hitDirection = previewPicker.detectHitDirection(
            event.x(),
            event.y(),
            state,
            guiScale
        );

        return new PreviewReleaseResult(
            true,
            true,
            hitDirection
        );
    }

    public void cancelInteraction() {
        this.dragging = false;
        this.wasDragged = false;
    }

    private @NonNull MachinePreviewRenderState createRenderState(
        @Nullable ScreenRectangle scissorArea
    )
    {
        if (this.blockState == null) {
            throw new IllegalStateException("Machine preview blockState is not initialized");
        }

        return new MachinePreviewRenderState(
            this.x,
            this.y,
            this.size,
            this.size,
            this.rotationX,
            this.rotationY,
            scissorArea,
            this.blockState,
            this.selectedSide
        );
    }

    private boolean isInsidePreviewArea(
        double mouseX,
        double mouseY
    )
    {
        return mouseX >= this.x &&
            mouseX < this.x + this.size &&
            mouseY >= this.y &&
            mouseY < this.y + this.size;
    }
}
