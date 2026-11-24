package ru.greatdragonx.astralspace.client.gui.components;

import net.minecraft.network.chat.Component;

import java.util.Objects;

public record GuiPortModeOption(
    String id, Component label
)
{
    public GuiPortModeOption {
        Objects.requireNonNull(label, "label cannot be null");
        Objects.requireNonNull(id, "id cannot be null");
        if (id.isBlank()) {
            throw new IllegalArgumentException("id cannot be blank");
        }
        if (!id.equals(id.strip())) {
            throw new IllegalArgumentException("id must not have leading or trailing whitespace");
        }
    }
}
