package ru.greatdragonx.astralspace.client.gui.components;

import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NonNull;
import ru.greatdragonx.astralspace.common.machine.visual.VisualPortType;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public record GuiPortEntry(
    VisualPortType type, String currentModeId, List<GuiPortModeOption> availableModes
)
{
    public GuiPortEntry {
        Objects.requireNonNull(type, "type cannot be null");
        Objects.requireNonNull(availableModes, "availableModes cannot be null");
        Objects.requireNonNull(currentModeId, "currentModeId cannot be null");
        if (currentModeId.isBlank()) {
            throw new IllegalArgumentException("currentModeId cannot be blank");
        }
        if (!currentModeId.equals(currentModeId.strip())) {
            throw new IllegalArgumentException(
                "currentModeId must not have leading or trailing whitespace");
        }
        if (availableModes.isEmpty()) {
            throw new IllegalArgumentException("availableModes cannot be empty");
        }

        Set<String> modeIds = new HashSet<>();
        for (GuiPortModeOption option : availableModes) {
            Objects.requireNonNull(option, "option cannot be null");
            if (!modeIds.add(option.id())) {
                throw new IllegalArgumentException("Duplicate port mode id: " + option.id());
            }
        }
        if (!modeIds.contains(currentModeId)) {
            throw new IllegalArgumentException(
                "Current port mode is not available: " + currentModeId);
        }

        availableModes = List.copyOf(availableModes);
    }

    public @NonNull Component modeName() {
        return this.availableModes.stream()
            .filter(option -> option.id()
                .equals(this.currentModeId))
            .map(GuiPortModeOption::label)
            .findFirst()
            .orElseThrow(() -> new IllegalStateException(
                "Current port mode not found: " + this.currentModeId));
    }
}
