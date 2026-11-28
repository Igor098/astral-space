package ru.greatdragonx.astralspace.common.machine.redstone;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class RedstoneControlSerializerTest {
    private static final String KEY = "redstone_control_mode";

    private static TagValueOutput output() {
        return TagValueOutput.createWithoutContext(ProblemReporter.DISCARDING);
    }

    private static ValueInput input(CompoundTag tag) {
        return TagValueInput.create(
            ProblemReporter.DISCARDING,
            HolderLookup.Provider.create(Stream.empty()),
            tag
        );
    }

    private static CompoundTag modeTag(String value) {
        CompoundTag tag = new CompoundTag();
        tag.putString(KEY, value);
        return tag;
    }

    private static CompoundTag save(RedstoneControlMode mode) {
        TagValueOutput output = output();
        RedstoneControlSerializer.save(output, mode);
        return output.buildResult();
    }

    private static void assertLoadedWithoutMutation(CompoundTag tag, RedstoneControlMode expected) {
        CompoundTag before = tag.copy();
        assertSame(expected, RedstoneControlSerializer.load(input(tag)));
        assertEquals(before, tag, "Loading must not modify the source NBT");
    }

    @ParameterizedTest
    @EnumSource(RedstoneControlMode.class)
    void shouldSaveModeAsSingleRootStringTag(RedstoneControlMode mode) {
        CompoundTag tag = save(mode);

        assertInstanceOf(StringTag.class, tag.get(KEY));
        assertEquals(modeTag(mode.name()), tag);
        assertEquals(1, tag.size());
    }

    @ParameterizedTest
    @EnumSource(RedstoneControlMode.class)
    void shouldLoadValidModeWithoutMutation(RedstoneControlMode mode) {
        assertLoadedWithoutMutation(modeTag(mode.name()), mode);
    }

    @ParameterizedTest
    @EnumSource(RedstoneControlMode.class)
    void shouldRoundTripEveryMode(RedstoneControlMode mode) {
        assertLoadedWithoutMutation(save(mode), mode);
    }

    @ParameterizedTest
    @EnumSource(RedstoneControlMode.class)
    void shouldPreserveUnrelatedDataWhenSaving(RedstoneControlMode mode) {
        TagValueOutput output = output();
        output.putInt("unrelated", 42);
        output.child("energy").putLong("stored_energy", 6000L);

        RedstoneControlSerializer.save(output, mode);

        CompoundTag expected = modeTag(mode.name());
        expected.putInt("unrelated", 42);
        CompoundTag energy = new CompoundTag();
        energy.putLong("stored_energy", 6000L);
        expected.put("energy", energy);

        assertEquals(expected, output.buildResult());
    }

    @Test
    void shouldOverwritePreviouslySavedModeIncludingIgnore() {
        TagValueOutput output = output();
        RedstoneControlSerializer.save(output, RedstoneControlMode.WHEN_POWERED);
        RedstoneControlSerializer.save(output, RedstoneControlMode.WHEN_UNPOWERED);
        assertEquals(modeTag("WHEN_UNPOWERED"), output.buildResult());

        TagValueOutput second = output();
        RedstoneControlSerializer.save(second, RedstoneControlMode.WHEN_UNPOWERED);
        RedstoneControlSerializer.save(second, RedstoneControlMode.IGNORE);
        assertEquals(modeTag("IGNORE"), second.buildResult());
    }

    @Test
    void shouldReturnIgnoreForEmptyTag() {
        assertLoadedWithoutMutation(new CompoundTag(), RedstoneControlMode.IGNORE);
    }

    @Test
    void shouldNotSearchForModeInUnrelatedKeysOrNestedSections() {
        CompoundTag root = new CompoundTag();
        root.putString("another_mode", "WHEN_UNPOWERED");
        root.put("nested", modeTag("WHEN_POWERED"));

        assertLoadedWithoutMutation(root, RedstoneControlMode.IGNORE);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "UNKNOWN", "when_powered", "When_Unpowered", " WHEN_POWERED", "WHEN_UNPOWERED ", "\tWHEN_POWERED", "1"})
    void shouldReturnIgnoreForInvalidStringWithoutMutation(String value) {
        assertLoadedWithoutMutation(modeTag(value), RedstoneControlMode.IGNORE);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidNbtTypes")
    void shouldReturnIgnoreForNonStringTagWithoutMutation(String type, CompoundTag tag) {
        assertLoadedWithoutMutation(tag, RedstoneControlMode.IGNORE);
    }

    private static Stream<Arguments> invalidNbtTypes() {
        return Stream.of(
            invalidTag("ByteTag", tag -> tag.putByte(KEY, (byte) 1)),
            invalidTag("ShortTag", tag -> tag.putShort(KEY, (short) 1)),
            invalidTag("IntTag", tag -> tag.putInt(KEY, 1)),
            invalidTag("LongTag", tag -> tag.putLong(KEY, 1L)),
            invalidTag("FloatTag", tag -> tag.putFloat(KEY, 1.0F)),
            invalidTag("DoubleTag", tag -> tag.putDouble(KEY, 1.0D)),
            invalidTag("CompoundTag", tag -> tag.put(KEY, modeTag("WHEN_POWERED"))),
            invalidTag(
                "ListTag", tag -> {
                    ListTag list = new ListTag();
                    list.add(StringTag.valueOf("WHEN_POWERED"));
                    tag.put(KEY, list);
                }
            ),
            invalidTag("ByteArrayTag", tag -> tag.putByteArray(KEY, new byte[]{1})),
            invalidTag("IntArrayTag", tag -> tag.putIntArray(KEY, new int[]{1})),
            invalidTag("LongArrayTag", tag -> tag.putLongArray(KEY, new long[]{1L}))
        );
    }

    private static Arguments invalidTag(String type, Consumer<CompoundTag> writer) {
        CompoundTag tag = new CompoundTag();
        writer.accept(tag);
        return Arguments.of(type, tag);
    }

    @Test
    void shouldRejectNullOutput() {
        assertThrows(
            NullPointerException.class,
            () -> RedstoneControlSerializer.save(null, RedstoneControlMode.IGNORE)
        );
    }

    @Test
    void shouldRejectNullModeWithoutChangingExistingData() {
        TagValueOutput output = output();
        output.putString(KEY, "WHEN_POWERED");
        output.putInt("unrelated", 42);

        assertThrows(
            NullPointerException.class,
            () -> RedstoneControlSerializer.save(output, null)
        );

        CompoundTag expected = modeTag("WHEN_POWERED");
        expected.putInt("unrelated", 42);
        assertEquals(expected, output.buildResult());
    }

    @Test
    void shouldRejectNullInput() {
        assertThrows(NullPointerException.class, () -> RedstoneControlSerializer.load(null));
    }
}
