package ru.greatdragonx.astralspace.common.machine.visual;

import ru.greatdragonx.astralspace.common.machine.MachineSide;

import java.util.Objects;

public record VisualPort(VisualPortId id, VisualPortType type, MachineSide side) {
    public VisualPort {
        Objects.requireNonNull(
            id,
            "Visual port id cannot be null"
        );
        Objects.requireNonNull(
            type,
            "Visual port type cannot be null"
        );
        Objects.requireNonNull(
            side,
            "Machine side cannot be null"
        );
    }
}
