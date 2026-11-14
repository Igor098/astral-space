package ru.greatdragonx.astralspace.common.machine.visual.layout;

public record PortLayoutPreset(float offsetU, float offsetV, float scale) {
    public PortLayoutPreset {
        if (!Float.isFinite(offsetU)) {
            throw new IllegalArgumentException("offsetU must be finite");
        }
        if (!Float.isFinite(offsetV)) {
            throw new IllegalArgumentException("offsetV must be finite");
        }
        if (!Float.isFinite(scale) || scale <= 0.0f) {
            throw new IllegalArgumentException("scale must be finite and positive");
        }
    }
}
