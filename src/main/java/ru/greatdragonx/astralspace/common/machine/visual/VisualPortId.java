package ru.greatdragonx.astralspace.common.machine.visual;

import java.util.Objects;

public record VisualPortId(String value) {
    public VisualPortId {
        Objects.requireNonNull(
            value,
            "Visual port value cannot be null"
        );
        if (value.isBlank()) {
            throw new IllegalArgumentException("Visual port value cannot be blank");
        }
        if (!value.equals(value.strip())) {
            throw new IllegalArgumentException("Visual port value does not contain before and after whitespace");
        }
    }
}
