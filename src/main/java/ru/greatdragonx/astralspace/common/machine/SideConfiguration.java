package ru.greatdragonx.astralspace.common.machine;

import java.util.Map;
import java.util.Objects;

public record SideConfiguration(Map<MachineSide, SidePortConfig> ports) {
    public SideConfiguration {
        Objects.requireNonNull(
            ports,
            "Ports configuration cannot be null"
        );
        ports = Map.copyOf(ports);
    }
}
