package ru.greatdragonx.astralspace.modules.energy.client.screens;

import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NonNull;

public enum LavaGeneratorGuiStatus {
    RUNNING(
        "gui.astralspace.lava_generator.status.running",
        0xFF388A54,
        0
    ),
    LOW_LAVA(
        "gui.astralspace.lava_generator.status.low_lava",
        0xFFE99142,
        32
    ),
    BUFFER_FULL(
        "gui.astralspace.lava_generator.status.buffer_full",
        0xFFB8C0C8,
        64
    ),
    NO_LAVA(
        "gui.astralspace.lava_generator.status.no_lava",
        0xFFB84444,
        96
    ),
    REDSTONE_DISABLED(
        "gui.astralspace.lava_generator.status.redstone_disabled",
        0xFF8A98A6,
        128
    );

    private final String translationKey;
    private final int color;
    private final int iconU;

    LavaGeneratorGuiStatus(
        String translationKey,
        int color,
        int iconU
    )
    {
        this.translationKey = translationKey;
        this.color = color;
        this.iconU = iconU;
    }

    public @NonNull Component getLabel() {
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
