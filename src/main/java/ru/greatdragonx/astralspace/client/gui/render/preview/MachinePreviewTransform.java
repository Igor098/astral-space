package ru.greatdragonx.astralspace.client.gui.render.preview;

public final class MachinePreviewTransform {
    private MachinePreviewTransform() {
    }

    public static final float MODEL_TRANSLATION_X = 0.0f;
    public static final float MODEL_TRANSLATION_Y = -32.0f;
    public static final float MODEL_TRANSLATION_Z = 0.0f;
    public static final float MODEL_SCALE_X = 28.0f;
    public static final float MODEL_SCALE_Y = -28.0f;
    public static final float MODEL_SCALE_Z = -28.0f;
    public static final float BLOCK_CENTER_OFFSET = -0.5f;

    public static float viewportFactor(
        int width,
        int height
    )
    {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Preview dimensions must be positive");
        }

        return Math.min(width, height) / 64.0f;
    }
}
