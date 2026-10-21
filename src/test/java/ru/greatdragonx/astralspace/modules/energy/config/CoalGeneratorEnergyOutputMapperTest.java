package ru.greatdragonx.astralspace.modules.energy.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ru.greatdragonx.astralspace.common.machine.MachineSide;
import ru.greatdragonx.astralspace.common.machine.PortMode;
import ru.greatdragonx.astralspace.common.machine.SideConfiguration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class CoalGeneratorEnergyOutputMapperTest {
    @Test
    void fromMask_whenMaskIsZero_allOutputsShouldBeDisabled() {
        SideConfiguration configuration = CoalGeneratorEnergyOutputMapper.fromMask(0);

        assertEquals(
            PortMode.DISABLED,
            configuration.ports()
                .get(MachineSide.BACK)
                .mode()
        );

        assertEquals(
            PortMode.DISABLED,
            configuration.ports()
                .get(MachineSide.LEFT)
                .mode()
        );

        assertEquals(
            PortMode.DISABLED,
            configuration.ports()
                .get(MachineSide.RIGHT)
                .mode()
        );

        assertEquals(
            PortMode.DISABLED,
            configuration.ports()
                .get(MachineSide.TOP)
                .mode()
        );

        assertEquals(
            PortMode.DISABLED,
            configuration.ports()
                .get(MachineSide.BOTTOM)
                .mode()
        );

        assertEquals(
            5,
            configuration.ports()
                .size()
        );

        assertFalse(configuration.ports()
            .containsKey(MachineSide.FRONT));
    }

    @ParameterizedTest
    @CsvSource({"1, BACK", "2, LEFT", "4, RIGHT", "8, TOP", "16, BOTTOM"})
    void fromMask_whenSingleBitIsSet_onlySelectedSideShouldBeEnabled(
        int mask,
        MachineSide enabledSide
    ) {
        SideConfiguration configuration = CoalGeneratorEnergyOutputMapper.fromMask(mask);

        for (MachineSide side : MachineSide.values()) {
            if (side == MachineSide.FRONT) {
                continue;
            }
            PortMode expectedMode = side == enabledSide ? PortMode.OUTPUT : PortMode.DISABLED;
            PortMode actualMode = configuration.ports()
                .get(side)
                .mode();
            assertEquals(
                expectedMode,
                actualMode,
                "Incorrect port mode for side: " + side
            );
        }
    }
}
