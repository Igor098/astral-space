package ru.greatdragonx.astralspace.client.gui.components;

import ru.greatdragonx.astralspace.common.machine.MachineSide;
import ru.greatdragonx.astralspace.common.machine.visual.VisualPortType;

@FunctionalInterface
public interface GuiPortModeChangeListener {
    void onModeSelected(
        MachineSide side,
        VisualPortType portType,
        String modeId
    );
}
