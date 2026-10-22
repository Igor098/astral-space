package ru.greatdragonx.astralspace.modules.energy.config;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import org.jspecify.annotations.NonNull;
import ru.greatdragonx.astralspace.common.machine.MachineSide;
import ru.greatdragonx.astralspace.common.machine.PortMode;
import ru.greatdragonx.astralspace.common.machine.SideConfiguration;
import ru.greatdragonx.astralspace.common.machine.SidePortConfig;
import ru.greatdragonx.astralspace.modules.energy.blocks.CoalGeneratorBlock;

import java.util.*;

public final class CoalGeneratorEnergyOutputMapper {
    private static final int BACK_BIT = 0;
    private static final int LEFT_BIT = 1;
    private static final int RIGHT_BIT = 2;
    private static final int TOP_BIT = 3;
    private static final int BOTTOM_BIT = 4;
    private static final List<OutputDefinition> OUTPUTS = List.of(
        new OutputDefinition(
            MachineSide.BACK,
            CoalGeneratorBlock.OUTPUT_BACK,
            BACK_BIT
        ),
        new OutputDefinition(
            MachineSide.LEFT,
            CoalGeneratorBlock.OUTPUT_LEFT,
            LEFT_BIT
        ),
        new OutputDefinition(
            MachineSide.RIGHT,
            CoalGeneratorBlock.OUTPUT_RIGHT,
            RIGHT_BIT
        ),
        new OutputDefinition(
            MachineSide.TOP,
            CoalGeneratorBlock.OUTPUT_TOP,
            TOP_BIT
        ),
        new OutputDefinition(
            MachineSide.BOTTOM,
            CoalGeneratorBlock.OUTPUT_BOTTOM,
            BOTTOM_BIT
        )
    );

    private CoalGeneratorEnergyOutputMapper() {
    }

    private record OutputDefinition(@NonNull MachineSide side,
                                    @NonNull BooleanProperty property,
                                    int bitIndex) {
    }

    public static @NonNull SideConfiguration fromMask(int mask) {
        Map<MachineSide, SidePortConfig> configuration = new EnumMap<>(MachineSide.class);

        Set<PortMode> allowedModes = Set.of(
            PortMode.DISABLED,
            PortMode.OUTPUT
        );

        for (OutputDefinition output : OUTPUTS) {
            MachineSide side = output.side();

            boolean isOutputEnabled = (mask & (1 << output.bitIndex())) != 0;

            PortMode sideMode = isOutputEnabled ? PortMode.OUTPUT : PortMode.DISABLED;

            SidePortConfig sideConfig = new SidePortConfig(
                sideMode,
                allowedModes
            );

            configuration.put(
                side,
                sideConfig
            );
        }

        return new SideConfiguration(configuration);
    }

    public static int toMask(@NonNull BlockState state) {
        Objects.requireNonNull(
            state,
            "Block state cannot be null"
        );

        int mask = 0;

        for (OutputDefinition output : OUTPUTS) {
            BooleanProperty sideProperty = output.property();
            if (state.getValue(sideProperty)) {
                mask |= 1 << output.bitIndex();
            }
        }

        return mask;
    }

    public static boolean isOutputEnabled(
        @NonNull BlockState state,
        @NonNull MachineSide side
    ) {
        Objects.requireNonNull(
            state,
            "Block state cannot be null"
        );
        Objects.requireNonNull(
            side,
            "Machine side cannot be null"
        );

        for (OutputDefinition output : OUTPUTS) {
            MachineSide machineSide = output.side();
            BooleanProperty sideProperty = output.property();

            if (machineSide == side) {
                return state.getValue(sideProperty);
            }
        }

        return false;
    }

    public static BlockState toggleOutput(
        @NonNull BlockState state,
        @NonNull MachineSide side
    ) {
        Objects.requireNonNull(
            state,
            "Block state cannot be null"
        );
        Objects.requireNonNull(
            side,
            "Machine side cannot be null"
        );

        for (OutputDefinition definition : OUTPUTS) {
            MachineSide machineSide = definition.side();
            if (side == machineSide) {
                BooleanProperty property = definition.property();
                boolean currentValue = state.getValue(property);

                return state.setValue(
                    property,
                    !currentValue
                );
            }
        }

        return state;
    }
}
