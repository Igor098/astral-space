package ru.greatdragonx.astralspace.client.gui.components;

import org.jetbrains.annotations.Unmodifiable;
import org.jspecify.annotations.NonNull;
import ru.greatdragonx.astralspace.common.machine.MachineSide;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public record GuiSideConfigData(
    Map<MachineSide, List<GuiPortEntry>> portsBySide
)
{
    public GuiSideConfigData {
        portsBySide = Objects.requireNonNull(portsBySide, "portsBySide cannot be null")
            .entrySet()
            .stream()
            .collect(Collectors.toUnmodifiableMap(
                Map.Entry::getKey,
                entry -> copyPortsBySide(entry.getValue())
            ));
    }

    public @NonNull @Unmodifiable List<GuiPortEntry> portsForSide(
        @NonNull MachineSide side
    )
    {
        Objects.requireNonNull(side, "side cannot be null");

        return this.portsBySide.getOrDefault(side, List.of());
    }

    private static @NonNull @Unmodifiable List<GuiPortEntry> copyPortsBySide(
        @NonNull List<GuiPortEntry> ports
    )
    {
        Objects.requireNonNull(ports, "ports cannot be null");

        return List.copyOf(ports);
    }
}
