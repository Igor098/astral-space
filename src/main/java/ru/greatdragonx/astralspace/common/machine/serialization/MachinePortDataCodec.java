package ru.greatdragonx.astralspace.common.machine.serialization;

import org.jspecify.annotations.Nullable;
import ru.greatdragonx.astralspace.common.machine.MachineSide;

public final class MachinePortDataCodec {

    private MachinePortDataCodec() {
    }

    public static int encodeSide(MachineSide side) {
        return -1;
    }

    public static MachineSide decodeSide(int offset) {
        return MachineSide.FRONT;
    }

    // Энергетические порты
    public static int encodeEnergy(
        @Nullable EnergyPortMode mode
    )
    {

    }

    public static @Nullable EnergyPortMode decodeEnergy(
        int code
    );

    // Жидкостные порты
    public static int encodeFluid(
        @Nullable FluidPortMode mode
    );

    public static @Nullable FluidPortMode decodeFluid(
        int code
    );

    // Предметные и газовые порты добавим позднее.
}
