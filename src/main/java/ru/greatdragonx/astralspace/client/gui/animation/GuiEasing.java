package ru.greatdragonx.astralspace.client.gui.animation;

public final class GuiEasing {
    private GuiEasing() {
    }

    public static float smoothstep(float progress) {
        float normalizedProgress = Math.clamp(
            progress,
            0.0f,
            1.0f
        );
        return normalizedProgress * normalizedProgress * (3.0f - 2.0f * normalizedProgress);
    }
}
