package ru.greatdragonx.astralspace.common.machine.serialization;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import ru.greatdragonx.astralspace.common.energy.port.EnergyPortMode;
import ru.greatdragonx.astralspace.common.fluid.port.FluidPortMode;
import ru.greatdragonx.astralspace.common.machine.MachineSide;

import java.util.Objects;

public final class MachinePortDataCodec {

    private MachinePortDataCodec() {
    }

    public static int encodeSide(@NonNull MachineSide side) {
        Objects.requireNonNull(side, "side cannot be null");

        return switch (side) {
            case FRONT -> 0;
            case BACK -> 1;
            case LEFT -> 2;
            case RIGHT -> 3;
            case TOP -> 4;
            case BOTTOM -> 5;
        };
    }

    public static MachineSide decodeSide(int offset) {
        return switch (offset) {
            case 0 -> MachineSide.FRONT;
            case 1 -> MachineSide.BACK;
            case 2 -> MachineSide.LEFT;
            case 3 -> MachineSide.RIGHT;
            case 4 -> MachineSide.TOP;
            case 5 -> MachineSide.BOTTOM;
            default ->
                throw new IllegalArgumentException("Unknown machine side offset: %d".formatted(
                    offset));
        };
    }

    public static int encodeEnergy(
        @Nullable EnergyPortMode mode
    )
    {
        return switch (mode) {
            case null -> -1;
            case DISABLED -> 0;
            case OUTPUT -> 1;
            case INPUT -> 2;
        };
    }

    public static @Nullable EnergyPortMode decodeEnergy(
        int code
    )
    {
        return switch (code) {
            case -1 -> null;
            case 0 -> EnergyPortMode.DISABLED;
            case 1 -> EnergyPortMode.OUTPUT;
            case 2 -> EnergyPortMode.INPUT;
            default -> throw new IllegalArgumentException("Unknown energy port mode code: " + code);
        };
    }

    public static int encodeFluid(
        @Nullable FluidPortMode mode
    )
    {
        return switch (mode) {
            case null -> -1;
            case DISABLED -> 0;
            case INPUT -> 1;
            case OUTPUT -> 2;
            case SERVICE -> 3;
        };
    }

    public static @Nullable FluidPortMode decodeFluid(
        int code
    )
    {
        return switch (code) {
            case -1 -> null;
            case 0 -> FluidPortMode.DISABLED;
            case 1 -> FluidPortMode.INPUT;
            case 2 -> FluidPortMode.OUTPUT;
            case 3 -> FluidPortMode.SERVICE;
            default -> throw new IllegalArgumentException("Unknown fluid port mode code: " + code);
        };
    }
}
