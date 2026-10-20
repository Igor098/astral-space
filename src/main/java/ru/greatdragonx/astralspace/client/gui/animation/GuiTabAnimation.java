package ru.greatdragonx.astralspace.client.gui.animation;

import net.minecraft.util.Mth;

public final class GuiTabAnimation {
    private boolean open;
    private float progress;
    private float previousProgress;
    private final float step;

    public GuiTabAnimation(float step) {
        this.step = step;
    }

    public void tick() {
        this.previousProgress = this.progress;
        float progress = this.progress;

        if (this.open) {
            progress += step;
        } else {
            progress -= step;
        }

        this.progress = Math.clamp(
            progress,
            0.0f,
            1.0f
        );
    }

    public void toggle() {
        this.open = !this.open;
    }

    public boolean isOpen() {
        return this.open;
    }

    public boolean isFullyClosed() {
        return !isOpen() && this.progress <= 0.0f && this.previousProgress <= 0.0f;
    }

    public float getProgress(float partialTick) {
        float progress = Mth.lerp(
            partialTick,
            this.previousProgress,
            this.progress
        );

        return GuiEasing.smoothstep(progress);
    }
}

