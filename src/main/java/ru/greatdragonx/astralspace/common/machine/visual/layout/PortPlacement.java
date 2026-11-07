package ru.greatdragonx.astralspace.common.machine.visual.layout;

import ru.greatdragonx.astralspace.common.machine.visual.VisualPort;

import java.util.Objects;

public record PortPlacement(VisualPort port, float offsetU, float offsetV, float scale) {
    public PortPlacement {
        Objects.requireNonNull(
            port,
            "port cannot be null"
        );
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
