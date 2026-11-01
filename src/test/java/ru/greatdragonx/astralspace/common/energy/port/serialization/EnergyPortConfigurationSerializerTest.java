package ru.greatdragonx.astralspace.common.energy.port.serialization;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.Test;
import ru.greatdragonx.astralspace.common.energy.port.ConfigurableEnergyPortPolicy;
import ru.greatdragonx.astralspace.common.energy.port.EnergyPortConfiguration;
import ru.greatdragonx.astralspace.common.energy.port.EnergyPortId;
import ru.greatdragonx.astralspace.common.energy.port.EnergyPortMode;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class EnergyPortConfigurationSerializerTest {

    private static final EnergyPortId NORTH = new EnergyPortId("north");

    private static final EnergyPortId SOUTH = new EnergyPortId("south");

    private static final EnergyPortId TOP = new EnergyPortId("top");

    private @NonNull TagValueOutput createOutput() {
        return TagValueOutput.createWithoutContext(ProblemReporter.DISCARDING);
    }


    private @NonNull ValueInput createInput(CompoundTag tag) {
        return TagValueInput.create(
            ProblemReporter.DISCARDING,
            HolderLookup.Provider.create(Stream.empty()),
            tag
        );
    }

    private @NonNull CompoundTag createPort(
        String id,
        String mode
    )
    {
        CompoundTag port = new CompoundTag();
        port.putString(
            "id",
            id
        );
        port.putString(
            "mode",
            mode
        );
        return port;
    }

    private @NonNull CompoundTag createRoot(
        Integer version,
        CompoundTag... portEntries
    )
    {
        CompoundTag root = new CompoundTag();
        CompoundTag energy = new CompoundTag();
        ListTag ports = new ListTag();

        if (version != null) {
            energy.putInt(
                "version",
                version
            );
        }

        Collections.addAll(
            ports,
            portEntries
        );

        energy.put(
            "ports",
            ports
        );
        root.put(
            "astralspace_energy_ports",
            energy
        );

        return root;
    }

    @Test
    void shouldWriteVersionAndSinglePort() {
        TagValueOutput output = createOutput();

        EnergyPortConfigurationSerializer.write(
            output,
            Map.of(
                NORTH,
                EnergyPortMode.OUTPUT
            )
        );

        CompoundTag root = output.buildResult();

        CompoundTag energy = root.getCompoundOrEmpty("astralspace_energy_ports");

        ListTag ports = energy.getListOrEmpty("ports");
        CompoundTag port = ports.getCompoundOrEmpty(0);

        assertAll(
            () -> assertTrue(root.contains("astralspace_energy_ports")),
            () -> assertEquals(
                1,
                energy.getIntOr(
                    "version",
                    -1
                )
            ),
            () -> assertEquals(
                1,
                ports.size()
            ),
            () -> assertEquals(
                "north",
                port.getStringOr(
                    "id",
                    ""
                )
            ),
            () -> assertEquals(
                "OUTPUT",
                port.getStringOr(
                    "mode",
                    ""
                )
            )
        );
    }

    @Test
    void shouldWriteMultiplePortsRegardlessOfOrder() {
        TagValueOutput output = createOutput();

        EnergyPortConfigurationSerializer.write(
            output,
            Map.of(
                NORTH,
                EnergyPortMode.INPUT,
                SOUTH,
                EnergyPortMode.OUTPUT,
                TOP,
                EnergyPortMode.DISABLED
            )
        );

        CompoundTag energy = output.buildResult()
            .getCompoundOrEmpty("astralspace_energy_ports");

        ListTag ports = energy.getListOrEmpty("ports");

        Map<String, String> actual = new HashMap<>();

        for (int i = 0; i < ports.size(); i++) {
            CompoundTag port = ports.getCompoundOrEmpty(i);

            actual.put(
                port.getStringOr(
                    "id",
                    ""
                ),
                port.getStringOr(
                    "mode",
                    ""
                )
            );
        }

        assertAll(
            () -> assertEquals(
                3,
                ports.size()
            ),
            () -> assertEquals(
                Map.of(
                    "north",
                    "INPUT",
                    "south",
                    "OUTPUT",
                    "top",
                    "DISABLED"
                ),
                actual
            )
        );
    }

    @Test
    void shouldHandleEmptyConfiguration() {
        TagValueOutput output = createOutput();

        EnergyPortConfigurationSerializer.write(
            output,
            Map.of()
        );

        CompoundTag energy = output.buildResult()
            .getCompoundOrEmpty("astralspace_energy_ports");

        assertAll(
            () -> assertEquals(
                1,
                energy.getIntOr(
                    "version",
                    -1
                )
            ),
            () -> assertTrue(energy.getListOrEmpty("ports")
                .isEmpty())
        );
    }

    @Test
    void shouldRejectNullArguments() {
        TagValueOutput output = createOutput();

        assertAll(
            () -> assertThrows(
                NullPointerException.class,
                () -> EnergyPortConfigurationSerializer.write(
                    null,
                    Map.of()
                )
            ),
            () -> assertThrows(
                NullPointerException.class,
                () -> EnergyPortConfigurationSerializer.write(
                    output,
                    null
                )
            )
        );
    }

    @Test
    void shouldRejectMalformedMapBeforeWriting() {
        TagValueOutput output = createOutput();

        Map<EnergyPortId, EnergyPortMode> invalid = new HashMap<>();

        invalid.put(
            NORTH,
            EnergyPortMode.OUTPUT
        );
        invalid.put(
            SOUTH,
            null
        );

        assertThrows(
            NullPointerException.class,
            () -> EnergyPortConfigurationSerializer.write(
                output,
                invalid
            )
        );

        assertFalse(output.buildResult()
            .contains("astralspace_energy_ports"));

        Map<EnergyPortId, EnergyPortMode> nullKey = new HashMap<>();

        nullKey.put(
            null,
            EnergyPortMode.INPUT
        );

        assertThrows(
            NullPointerException.class,
            () -> EnergyPortConfigurationSerializer.write(
                createOutput(),
                nullKey
            )
        );
    }

    @Test
    void shouldPreserveUnrelatedDataAndInputMap() {
        TagValueOutput output = createOutput();

        output.putString(
            "other_data",
            "preserved"
        );

        Map<EnergyPortId, EnergyPortMode> modes = new HashMap<>();

        modes.put(
            NORTH,
            EnergyPortMode.INPUT
        );

        EnergyPortConfigurationSerializer.write(
            output,
            modes
        );

        CompoundTag root = output.buildResult();

        assertAll(
            () -> assertEquals(
                "preserved",
                root.getStringOr(
                    "other_data",
                    ""
                )
            ),
            () -> assertEquals(
                Map.of(
                    NORTH,
                    EnergyPortMode.INPUT
                ),
                modes
            )
        );
    }


    @Test
    void shouldReadPreviouslyWrittenPorts() {
        TagValueOutput output = createOutput();

        Map<EnergyPortId, EnergyPortMode> expected = Map.of(
            NORTH,
            EnergyPortMode.INPUT,
            SOUTH,
            EnergyPortMode.OUTPUT,
            TOP,
            EnergyPortMode.DISABLED
        );

        EnergyPortConfigurationSerializer.write(
            output,
            expected
        );

        Map<EnergyPortId, EnergyPortMode> actual =
            EnergyPortConfigurationSerializer.read(createInput(output.buildResult()));

        assertEquals(
            expected,
            actual
        );
    }

    @Test
    void shouldReturnEmptyWhenSectionIsMissing() {
        Map<EnergyPortId, EnergyPortMode> actual =
            EnergyPortConfigurationSerializer.read(createInput(new CompoundTag()));

        assertTrue(actual.isEmpty());
    }

    @Test
    void shouldRejectUnsupportedOrMissingVersion() {
        CompoundTag unsupported = createRoot(
            2,
            createPort(
                "north",
                "INPUT"
            )
        );

        CompoundTag missingVersion = createRoot(
            null,
            createPort(
                "north",
                "OUTPUT"
            )
        );

        assertAll(
            () -> assertTrue(EnergyPortConfigurationSerializer.read(createInput(unsupported))
                .isEmpty()),
            () -> assertTrue(EnergyPortConfigurationSerializer.read(createInput(missingVersion))
                .isEmpty())
        );
    }

    @Test
    void shouldSkipInvalidModesAndPortIds() {
        CompoundTag root = createRoot(
            1,
            createPort(
                "north",
                "INPUT"
            ),
            createPort(
                "south",
                "BOTH"
            ),
            createPort(
                "top",
                "output"
            ),
            createPort(
                "",
                "OUTPUT"
            ),
            createPort(
                " left ",
                "INPUT"
            ),
            createPort(
                "bottom",
                "DISABLED"
            )
        );

        Map<EnergyPortId, EnergyPortMode> actual =
            EnergyPortConfigurationSerializer.read(createInput(root));

        assertEquals(
            Map.of(
                NORTH,
                EnergyPortMode.INPUT,
                new EnergyPortId("bottom"),
                EnergyPortMode.DISABLED
            ),
            actual
        );
    }

    @Test
    void shouldPreserveFirstValidDuplicate() {
        CompoundTag root = createRoot(
            1,
            createPort(
                "north",
                "OUTPUT"
            ),
            createPort(
                "north",
                "INPUT"
            ),
            createPort(
                "south",
                "INPUT"
            )
        );

        Map<EnergyPortId, EnergyPortMode> actual =
            EnergyPortConfigurationSerializer.read(createInput(root));

        assertEquals(
            Map.of(
                NORTH,
                EnergyPortMode.OUTPUT,
                SOUTH,
                EnergyPortMode.INPUT
            ),
            actual
        );
    }

    @Test
    void shouldSkipMissingAndWrongTypedFields() {
        CompoundTag missingMode = new CompoundTag();
        missingMode.putString(
            "id",
            "north"
        );

        CompoundTag wrongIdType = new CompoundTag();
        wrongIdType.putInt(
            "id",
            123
        );
        wrongIdType.putString(
            "mode",
            "OUTPUT"
        );

        CompoundTag wrongModeType = new CompoundTag();
        wrongModeType.putString(
            "id",
            "south"
        );
        wrongModeType.putInt(
            "mode",
            42
        );

        CompoundTag root = createRoot(
            1,
            missingMode,
            wrongIdType,
            wrongModeType,
            createPort(
                "top",
                "OUTPUT"
            )
        );

        Map<EnergyPortId, EnergyPortMode> actual =
            EnergyPortConfigurationSerializer.read(createInput(root));

        assertEquals(
            Map.of(
                TOP,
                EnergyPortMode.OUTPUT
            ),
            actual
        );
    }

    @Test
    void shouldReturnImmutableResult() {
        CompoundTag root = createRoot(
            1,
            createPort(
                "north",
                "INPUT"
            )
        );

        Map<EnergyPortId, EnergyPortMode> result =
            EnergyPortConfigurationSerializer.read(createInput(root));

        assertAll(
            () -> assertEquals(
                EnergyPortMode.INPUT,
                result.get(NORTH)
            ),
            () -> assertThrows(
                UnsupportedOperationException.class,
                () -> result.put(
                    SOUTH,
                    EnergyPortMode.OUTPUT
                )
            )
        );
    }

    @Test
    void shouldRejectNullInput() {
        assertThrows(
            NullPointerException.class,
            () -> EnergyPortConfigurationSerializer.read(null)
        );
    }


    @Test
    void shouldSaveReadAndRestoreOnlyAllowedPortModes() {
        var original = new EnergyPortConfiguration(
            Map.of(
                NORTH,
                EnergyPortMode.OUTPUT,
                SOUTH,
                EnergyPortMode.INPUT,
                TOP,
                EnergyPortMode.DISABLED
            ),
            () -> {
            }
        );

        TagValueOutput output = createOutput();

        EnergyPortConfigurationSerializer.write(
            output,
            original.snapshot()
        );

        Map<EnergyPortId, EnergyPortMode> restored =
            EnergyPortConfigurationSerializer.read(createInput(output.buildResult()));

        AtomicInteger notifications = new AtomicInteger();

        var loaded = new EnergyPortConfiguration(
            Map.of(
                NORTH,
                EnergyPortMode.INPUT,
                SOUTH,
                EnergyPortMode.DISABLED
            ),
            notifications::incrementAndGet
        );

        var policy = new ConfigurableEnergyPortPolicy(
            Map.of(
                NORTH,
                Set.of(
                    EnergyPortMode.INPUT,
                    EnergyPortMode.OUTPUT
                ),
                SOUTH,
                Set.of(
                    EnergyPortMode.OUTPUT,
                    EnergyPortMode.DISABLED
                )
            ),
            loaded::getMode,
            id -> true
        );

        loaded.restoreModes(
            restored,
            policy
        );

        assertAll(
            () -> assertEquals(
                EnergyPortMode.OUTPUT,
                loaded.getMode(NORTH)
            ),

            () -> assertEquals(
                EnergyPortMode.DISABLED,
                loaded.getMode(SOUTH)
            ),

            () -> assertFalse(loaded.snapshot()
                .containsKey(TOP)),

            () -> assertEquals(
                0,
                notifications.get()
            )
        );
    }

    @Test
    void shouldReturnEmptyWhenPortsListIsMissing() {
        CompoundTag root = new CompoundTag();
        CompoundTag energy = new CompoundTag();

        energy.putInt(
            "version",
            1
        );
        root.put(
            "astralspace_energy_ports",
            energy
        );

        Map<EnergyPortId, EnergyPortMode> result =
            EnergyPortConfigurationSerializer.read(createInput(root));

        assertTrue(result.isEmpty());
    }

    @Test
    void shouldAcceptFirstValidEntryAfterInvalidDuplicate() {
        CompoundTag root = createRoot(
            1,
            createPort(
                "north",
                "BOTH"
            ),
            createPort(
                "north",
                "OUTPUT"
            ),
            createPort(
                "north",
                "INPUT"
            )
        );

        Map<EnergyPortId, EnergyPortMode> result =
            EnergyPortConfigurationSerializer.read(createInput(root));

        assertEquals(
            Map.of(
                NORTH,
                EnergyPortMode.OUTPUT
            ),
            result
        );
    }
}

