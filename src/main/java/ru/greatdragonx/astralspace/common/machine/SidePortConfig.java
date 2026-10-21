package ru.greatdragonx.astralspace.common.machine;

import java.util.Objects;
import java.util.Set;

public record SidePortConfig(PortMode mode, Set<PortMode> allowedModes) {
    public SidePortConfig {
        Objects.requireNonNull(
            mode,
            "Port mode cannot be null"
        );
        Objects.requireNonNull(
            allowedModes,
            "Allowed modes cannot be null"
        );
        if (allowedModes.isEmpty()) {
            throw new IllegalArgumentException("Allowed modes cannot be empty");
        }
        if (!allowedModes.contains(mode)) {
            throw new IllegalArgumentException("Current mode '" + mode + "' is not in allowed modes");
        }
        allowedModes = Set.copyOf(allowedModes);
    }
}
