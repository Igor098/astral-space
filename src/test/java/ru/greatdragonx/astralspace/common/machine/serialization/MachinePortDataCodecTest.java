package ru.greatdragonx.astralspace.common.machine.serialization;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import ru.greatdragonx.astralspace.common.energy.port.EnergyPortMode;
import ru.greatdragonx.astralspace.common.fluid.port.FluidPortMode;
import ru.greatdragonx.astralspace.common.machine.MachineSide;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class MachinePortDataCodecTest {
    private static Stream<Arguments> sideCases() {
        return Stream.of(
            Arguments.of(MachineSide.FRONT, 0),
            Arguments.of(MachineSide.BACK, 1),
            Arguments.of(MachineSide.LEFT, 2),
            Arguments.of(MachineSide.RIGHT, 3),
            Arguments.of(MachineSide.TOP, 4),
            Arguments.of(MachineSide.BOTTOM, 5)
        );
    }

    private static Stream<Arguments> energyCases() {
        return Stream.of(
            Arguments.of(null, -1),
            Arguments.of(EnergyPortMode.DISABLED, 0),
            Arguments.of(EnergyPortMode.OUTPUT, 1),
            Arguments.of(EnergyPortMode.INPUT, 2)
        );
    }

    private static Stream<Arguments> fluidCases() {
        return Stream.of(
            Arguments.of(null, -1),
            Arguments.of(FluidPortMode.DISABLED, 0),
            Arguments.of(FluidPortMode.INPUT, 1),
            Arguments.of(FluidPortMode.OUTPUT, 2),
            Arguments.of(FluidPortMode.SERVICE, 3)
        );
    }

    @ParameterizedTest(name = "encodeSide({0}) = {1}")
    @MethodSource("sideCases")
    void shouldEncodeSideUsingStableCodes(
        MachineSide side,
        int expectedCode
    )
    {
        assertEquals(expectedCode, MachinePortDataCodec.encodeSide(side));
    }

    @ParameterizedTest(name = "decodeSide({1}) = {0}")
    @MethodSource("sideCases")
    void shouldDecodeSideUsingStableCodes(
        MachineSide expectedSide,
        int code
    )
    {
        assertEquals(expectedSide, MachinePortDataCodec.decodeSide(code));
    }

    @Test
    void shouldRejectNullMachineSide() {
        assertThrows(NullPointerException.class, () -> MachinePortDataCodec.encodeSide(null));
    }

    @ParameterizedTest
    @ValueSource(ints = {Integer.MIN_VALUE, -2, -1, 6, 100, Integer.MAX_VALUE})
    void shouldRejectUnknownSideCodes(int code) {
        assertThrows(IllegalArgumentException.class, () -> MachinePortDataCodec.decodeSide(code));
    }

    @Test
    void shouldRoundTripAllMachineSides() {
        for (MachineSide side : MachineSide.values()) {
            assertEquals(
                side,
                MachinePortDataCodec.decodeSide(MachinePortDataCodec.encodeSide(side)),
                "Round-trip failed for side: " + side
            );
        }
    }

    @ParameterizedTest(name = "encodeEnergy({0}) = {1}")
    @MethodSource("energyCases")
    void shouldEncodeEnergyUsingStableCodes(
        EnergyPortMode mode,
        int expectedCode
    )
    {
        assertEquals(expectedCode, MachinePortDataCodec.encodeEnergy(mode));
    }

    @ParameterizedTest(name = "decodeEnergy({1}) = {0}")
    @MethodSource("energyCases")
    void shouldDecodeEnergyUsingStableCodes(
        EnergyPortMode expectedMode,
        int code
    )
    {
        assertEquals(expectedMode, MachinePortDataCodec.decodeEnergy(code));
    }

    @ParameterizedTest
    @ValueSource(ints = {Integer.MIN_VALUE, -2, 3, 4, Integer.MAX_VALUE})
    void shouldRejectUnknownEnergyCodes(int code) {
        assertThrows(IllegalArgumentException.class, () -> MachinePortDataCodec.decodeEnergy(code));
    }

    @Test
    void shouldRoundTripAllEnergyModesAndAbsentPort() {
        assertNull(MachinePortDataCodec.decodeEnergy(MachinePortDataCodec.encodeEnergy(null)));
        for (EnergyPortMode mode : EnergyPortMode.values()) {
            assertEquals(
                mode,
                MachinePortDataCodec.decodeEnergy(MachinePortDataCodec.encodeEnergy(mode)),
                "Round-trip failed for energy mode: " + mode
            );
        }
    }

    @ParameterizedTest(name = "encodeFluid({0}) = {1}")
    @MethodSource("fluidCases")
    void shouldEncodeFluidUsingStableCodes(
        FluidPortMode mode,
        int expectedCode
    )
    {
        assertEquals(expectedCode, MachinePortDataCodec.encodeFluid(mode));
    }

    @ParameterizedTest(name = "decodeFluid({1}) = {0}")
    @MethodSource("fluidCases")
    void shouldDecodeFluidUsingStableCodes(
        FluidPortMode expectedMode,
        int code
    )
    {
        assertEquals(expectedMode, MachinePortDataCodec.decodeFluid(code));
    }

    @ParameterizedTest
    @ValueSource(ints = {Integer.MIN_VALUE, -2, 4, 5, Integer.MAX_VALUE})
    void shouldRejectUnknownFluidCodes(int code) {
        assertThrows(IllegalArgumentException.class, () -> MachinePortDataCodec.decodeFluid(code));
    }

    @Test
    void shouldRoundTripAllFluidModesAndAbsentPort() {
        assertNull(MachinePortDataCodec.decodeFluid(MachinePortDataCodec.encodeFluid(null)));
        for (FluidPortMode mode : FluidPortMode.values()) {
            assertEquals(
                mode,
                MachinePortDataCodec.decodeFluid(MachinePortDataCodec.encodeFluid(mode)),
                "Round-trip failed for fluid mode: " + mode
            );
        }
    }
}
