package ru.greatdragonx.astralspace.modules.energy.config;

import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ru.greatdragonx.astralspace.common.machine.MachineSide;
import ru.greatdragonx.astralspace.common.machine.PortMode;
import ru.greatdragonx.astralspace.common.machine.SideConfiguration;
import ru.greatdragonx.astralspace.modules.energy.EnergyBlocks;
import ru.greatdragonx.astralspace.modules.energy.blocks.CoalGeneratorBlock;

import static org.junit.jupiter.api.Assertions.*;

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

    @Test
    void toggleOutput_whenBackEnabled_shouldDisableBack() {
        BlockState initialState = EnergyBlocks.COAL_GENERATOR.get()
            .defaultBlockState();

        BlockState updatedState = CoalGeneratorEnergyOutputMapper.toggleOutput(
            initialState,
            MachineSide.BACK
        );

        assertTrue(initialState.getValue(CoalGeneratorBlock.OUTPUT_BACK));
        assertFalse(updatedState.getValue(CoalGeneratorBlock.OUTPUT_BACK));
        assertFalse(updatedState.getValue(CoalGeneratorBlock.OUTPUT_LEFT));
    }

    @ParameterizedTest
    @CsvSource({"BACK, 0", "LEFT, 3", "RIGHT, 5", "TOP, 9", "BOTTOM, 17"})
    void toggleOutput_whenSideIsSupported_shouldToggleOnlySelectedSide(
        MachineSide side,
        int expectedMask
    ) {
        BlockState initialState = EnergyBlocks.COAL_GENERATOR.get()
            .defaultBlockState();

        BlockState updatedState = CoalGeneratorEnergyOutputMapper.toggleOutput(
            initialState,
            side
        );

        int newMask = CoalGeneratorEnergyOutputMapper.toMask(updatedState);

        assertEquals(
            expectedMask,
            newMask,
            "Incorrect output mask after toggling side: " + side
        );
    }

    @Test
    void toggleOutput_whenSideIsUnsupported_shouldReturnOriginalState() {
        BlockState initialState = EnergyBlocks.COAL_GENERATOR.get()
            .defaultBlockState();

        BlockState updatedState = CoalGeneratorEnergyOutputMapper.toggleOutput(
            initialState,
            MachineSide.FRONT
        );
        assertSame(
            initialState,
            updatedState
        );
    }

    @Test
    void toggleOutput_whenToggledTwice_shouldRestoreOriginalState() {
        BlockState initialState = EnergyBlocks.COAL_GENERATOR.get()
            .defaultBlockState();

        BlockState firstState = CoalGeneratorEnergyOutputMapper.toggleOutput(
            initialState,
            MachineSide.BACK
        );

        BlockState secondState = CoalGeneratorEnergyOutputMapper.toggleOutput(
            firstState,
            MachineSide.BACK
        );

        assertFalse(firstState.getValue(CoalGeneratorBlock.OUTPUT_BACK));
        assertTrue(secondState.getValue(CoalGeneratorBlock.OUTPUT_BACK));
        assertSame(
            initialState,
            secondState
        );
    }

    @Test
    void toMask_whenMultipleOutputsEnabled_shouldReturnCorrectMask() {
        BlockState initialState = EnergyBlocks.COAL_GENERATOR.get()
            .defaultBlockState();

        BlockState updatedState = initialState.setValue(
                CoalGeneratorBlock.OUTPUT_RIGHT,
                true
            )
            .setValue(
                CoalGeneratorBlock.OUTPUT_BOTTOM,
                true
            );

        int mask = CoalGeneratorEnergyOutputMapper.toMask(updatedState);
        assertEquals(
            21,
            mask
        );
    }

    @ParameterizedTest
    @CsvSource({"BACK, true", "LEFT, false", "RIGHT, false", "TOP, false", "BOTTOM, false", "FRONT, false"})
    void isOutputEnabled_whenUsingDefaultState_shouldReturnCorrectValue(
        MachineSide side,
        boolean expected
    ) {
        BlockState state = EnergyBlocks.COAL_GENERATOR.get()
            .defaultBlockState();

        boolean actual = CoalGeneratorEnergyOutputMapper.isOutputEnabled(
            state,
            side
        );
        assertEquals(
            expected,
            actual,
            "Incorrect output state for side: " + side
        );
    }

    @Test
    void isOutputEnabled_whenStateIsNull_shouldThrowException() {
        assertThrows(
            NullPointerException.class,
            () -> CoalGeneratorEnergyOutputMapper.isOutputEnabled(
                null,
                MachineSide.BACK
            )
        );
    }

    @Test
    void isOutputEnabled_whenSideIsNull_shouldThrowException() {
        BlockState state = EnergyBlocks.COAL_GENERATOR.get()
            .defaultBlockState();
        assertThrows(
            NullPointerException.class,
            () -> CoalGeneratorEnergyOutputMapper.isOutputEnabled(
                state,
                null
            )
        );
    }

    @Test
    void toggleOutput_whenStateIsNull_shouldThrowException() {
        assertThrows(
            NullPointerException.class,
            () -> CoalGeneratorEnergyOutputMapper.toggleOutput(
                null,
                MachineSide.BACK
            )
        );
    }

    @Test
    void toggleOutput_whenSideIsNull_shouldThrowException() {
        BlockState state = EnergyBlocks.COAL_GENERATOR.get()
            .defaultBlockState();
        assertThrows(
            NullPointerException.class,
            () -> CoalGeneratorEnergyOutputMapper.toggleOutput(
                state,
                null
            )
        );
    }
}
