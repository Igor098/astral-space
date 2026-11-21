package ru.greatdragonx.astralspace.client.gui.components;

import net.minecraft.network.chat.Component;
import ru.greatdragonx.astralspace.common.machine.visual.VisualPortType;

import java.util.Objects;

public record GuiPortEntry(
    VisualPortType type, Component modeName
)
{
    public GuiPortEntry {
        Objects.requireNonNull(type, "type cannot be null");
        Objects.requireNonNull(modeName, "modeName cannot be null");
    }
}
