package ru.greatdragonx.astralspace.modules.energy.blocks.state;

import net.minecraft.util.StringRepresentable;
import org.jspecify.annotations.NonNull;

public enum CoalGeneratorFrontState implements StringRepresentable {
    OFF("off"),
    RUNNING_NORMAL("running_normal"),
    RUNNING_LOW_FUEL("running_low_fuel");

    private final String serializedName;

    CoalGeneratorFrontState(String serializedName) {
        this.serializedName = serializedName;
    }

    @Override
    public @NonNull String getSerializedName() {
        return this.serializedName;
    }
}
