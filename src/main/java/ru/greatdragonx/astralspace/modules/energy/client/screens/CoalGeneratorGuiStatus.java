package ru.greatdragonx.astralspace.modules.energy.client.screens;

import net.minecraft.network.chat.Component;

public enum CoalGeneratorGuiStatus {
    RUNNING(
        "gui.astralspace.coal_generator.status.running",
        0xFF388A54,
        0
    ),
    LOW_FUEL(
        "gui.astralspace.coal_generator.status.low_fuel",
        0xFFE99142,
        8
    ),
    BUFFER_FULL(
        "gui.astralspace.coal_generator.status.buffer_full",
        0xFFB8C0C8,
        16
    ),
    NO_FUEL(
        "gui.astralspace.coal_generator.status.no_fuel",
        0xFFB84444,
        24
    );

    private final String translationKey;
    private final int color;
    private final int iconU;

    CoalGeneratorGuiStatus(
        String translationKey,
        int color,
        int iconU
    ) {
        this.translationKey = translationKey;
        this.color = color;
        this.iconU = iconU;
    }

    public Component getLabel() {
        return Component.translatable(this.translationKey);
    }

    public int getColor() {
        return this.color;
    }

    public int getIconU() {
        return this.iconU;
    }

    public int getIconV() {
        return 0;
    }
}
