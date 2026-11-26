package ru.greatdragonx.astralspace.common.serialization;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.ValueInput;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.function.BiConsumer;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class StrictNbtReaderTest {
    private static final String KEY = "value";

    private static ValueInput input(CompoundTag tag) {
        return TagValueInput.create(
            ProblemReporter.DISCARDING,
            HolderLookup.Provider.create(Stream.empty()),
            tag
        );
    }

    private static CompoundTag intTag(int value) {
        CompoundTag tag = new CompoundTag();
        tag.putInt(KEY, value);
        return tag;
    }

    private static CompoundTag longTag(long value) {
        CompoundTag tag = new CompoundTag();
        tag.putLong(KEY, value);
        return tag;
    }

    @Test
    void shouldReadPositiveInt() {
        assertEquals(OptionalInt.of(6000), StrictNbtReader.readStrictInt(input(intTag(6000)), KEY));
    }

    @Test
    void shouldReadZeroIntAsPresentValue() {
        assertEquals(OptionalInt.of(0), StrictNbtReader.readStrictInt(input(intTag(0)), KEY));
    }

    @Test
    void shouldReadNegativeIntWithoutClamping() {
        assertEquals(
            OptionalInt.of(-6000),
            StrictNbtReader.readStrictInt(input(intTag(-6000)), KEY)
        );
    }

    @Test
    void shouldReadIntBoundaries() {
        for (int value : new int[]{Integer.MIN_VALUE, Integer.MAX_VALUE}) {
            assertEquals(
                OptionalInt.of(value),
                StrictNbtReader.readStrictInt(input(intTag(value)), KEY)
            );
        }
    }

    @Test
    void shouldReadPositiveLong() {
        assertEquals(
            OptionalLong.of(6000L),
            StrictNbtReader.readStrictLong(input(longTag(6000L)), KEY)
        );
    }

    @Test
    void shouldReadZeroLongAsPresentValue() {
        assertEquals(OptionalLong.of(0L), StrictNbtReader.readStrictLong(input(longTag(0L)), KEY));
    }

    @Test
    void shouldReadNegativeLongWithoutClamping() {
        assertEquals(
            OptionalLong.of(-6000L),
            StrictNbtReader.readStrictLong(input(longTag(-6000L)), KEY)
        );
    }

    @Test
    void shouldReadLongBoundaries() {
        for (long value : new long[]{Long.MIN_VALUE, Long.MAX_VALUE}) {
            assertEquals(
                OptionalLong.of(value),
                StrictNbtReader.readStrictLong(input(longTag(value)), KEY)
            );
        }
    }

    @Test
    void shouldReadLongValuesOutsideIntRangeWithoutTruncation() {
        long[] values = {(long) Integer.MIN_VALUE - 1L, (long) Integer.MAX_VALUE +
            1L, 3_000_000_000L, 4_294_967_296L, 4_294_967_297L, -4_294_967_297L};

        for (long value : values) {
            assertEquals(
                OptionalLong.of(value),
                StrictNbtReader.readStrictLong(input(longTag(value)), KEY),
                "Long value: " + value
            );
        }
    }

    @Test
    void shouldReturnEmptyIntForMissingKey() {
        assertEquals(
            OptionalInt.empty(),
            StrictNbtReader.readStrictInt(input(new CompoundTag()), KEY)
        );
    }

    @Test
    void shouldReturnEmptyLongForMissingKey() {
        assertEquals(
            OptionalLong.empty(),
            StrictNbtReader.readStrictLong(input(new CompoundTag()), KEY)
        );
    }

    @Test
    void shouldRejectLongTagsWhenReadingIntEvenIfValuesFit() {
        for (long value : new long[]{0L, -1L, 6000L, Integer.MAX_VALUE, 4_294_967_297L}) {
            assertEquals(
                OptionalInt.empty(),
                StrictNbtReader.readStrictInt(input(longTag(value)), KEY)
            );
        }
    }

    @Test
    void shouldRejectIntTagsWhenReadingLong() {
        for (int value : new int[]{0, -1, 6000, Integer.MAX_VALUE}) {
            assertEquals(
                OptionalLong.empty(),
                StrictNbtReader.readStrictLong(input(intTag(value)), KEY)
            );
        }
    }

    @Test
    void shouldRejectOtherNumericTagsForBothReaders() {
        List<BiConsumer<CompoundTag, String>> writers = List.of(
            (tag, key) -> tag.putByte(key, (byte) 42),
            (tag, key) -> tag.putShort(key, (short) 42),
            (tag, key) -> tag.putFloat(key, 42.0F),
            (tag, key) -> tag.putFloat(key, 42.5F),
            (tag, key) -> tag.putDouble(key, 42.0D),
            (tag, key) -> tag.putDouble(key, 42.5D)
        );

        for (BiConsumer<CompoundTag, String> writer : writers) {
            CompoundTag tag = new CompoundTag();
            writer.accept(tag, KEY);
            assertRejectedByBothReaders(tag);
        }
    }

    @Test
    void shouldRejectStringsForBothReaders() {
        for (String value : List.of("42", "-42", "0", "3000000000", "")) {
            CompoundTag tag = new CompoundTag();
            tag.putString(KEY, value);
            assertRejectedByBothReaders(tag);
        }
    }

    @Test
    void shouldRejectCompoundAndListTagsForBothReaders() {
        CompoundTag compound = new CompoundTag();
        compound.put(KEY, intTag(42));
        CompoundTag list = new CompoundTag();
        list.put(KEY, new ListTag());

        assertAll(
            () -> assertRejectedByBothReaders(compound),
            () -> assertRejectedByBothReaders(list)
        );
    }

    @Test
    void shouldRejectArrayTagsForBothReaders() {
        CompoundTag bytes = new CompoundTag();
        bytes.putByteArray(KEY, new byte[]{42});
        CompoundTag ints = new CompoundTag();
        ints.putIntArray(KEY, new int[]{42});
        CompoundTag longs = new CompoundTag();
        longs.putLongArray(KEY, new long[]{42L});

        assertAll(
            () -> assertRejectedByBothReaders(bytes),
            () -> assertRejectedByBothReaders(ints),
            () -> assertRejectedByBothReaders(longs)
        );
    }

    @Test
    void shouldReadOnlyRequestedKey() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("first", 123);
        tag.putInt("second", 456);
        tag.putLong("third", 7_000_000_000L);
        tag.putLong("fourth", 8_000_000_000L);
        ValueInput input = input(tag);

        assertAll(
            () -> assertEquals(OptionalInt.of(123), StrictNbtReader.readStrictInt(input, "first")),
            () -> assertEquals(OptionalInt.of(456), StrictNbtReader.readStrictInt(input, "second")),
            () -> assertEquals(
                OptionalLong.of(7_000_000_000L),
                StrictNbtReader.readStrictLong(input, "third")
            ),
            () -> assertEquals(
                OptionalLong.of(8_000_000_000L),
                StrictNbtReader.readStrictLong(input, "fourth")
            ),
            () -> assertEquals(
                OptionalInt.empty(),
                StrictNbtReader.readStrictInt(input, "missing")
            ),
            () -> assertEquals(
                OptionalLong.empty(),
                StrictNbtReader.readStrictLong(input, "missing")
            )
        );
    }

    @Test
    void shouldReadFromSelectedChildWithoutSearchingOtherSections() {
        CompoundTag root = new CompoundTag();
        root.putInt("int_value", 11);
        root.putLong("long_value", 22L);
        CompoundTag child = new CompoundTag();
        child.putInt("int_value", 33);
        child.putLong("long_value", 4_000_000_000L);
        root.put("child", child);
        ValueInput rootInput = input(root);
        ValueInput childInput = rootInput.childOrEmpty("child");
        ValueInput missingInput = rootInput.childOrEmpty("missing");

        assertAll(
            () -> assertEquals(
                OptionalInt.of(11),
                StrictNbtReader.readStrictInt(rootInput, "int_value")
            ),
            () -> assertEquals(
                OptionalLong.of(22L),
                StrictNbtReader.readStrictLong(rootInput, "long_value")
            ),
            () -> assertEquals(
                OptionalInt.of(33),
                StrictNbtReader.readStrictInt(childInput, "int_value")
            ),
            () -> assertEquals(
                OptionalLong.of(4_000_000_000L),
                StrictNbtReader.readStrictLong(childInput, "long_value")
            ),
            () -> assertEquals(
                OptionalInt.empty(),
                StrictNbtReader.readStrictInt(missingInput, "int_value")
            ),
            () -> assertEquals(
                OptionalLong.empty(),
                StrictNbtReader.readStrictLong(missingInput, "long_value")
            )
        );
    }

    @Test
    void shouldAllowEmptyAndWhitespaceKeys() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("", 13);
        tag.putLong(" ", 27L);
        ValueInput input = input(tag);

        assertAll(
            () -> assertEquals(OptionalInt.of(13), StrictNbtReader.readStrictInt(input, "")),
            () -> assertEquals(OptionalLong.of(27L), StrictNbtReader.readStrictLong(input, " "))
        );
    }

    @Test
    void shouldAllowRepeatedReadsWithoutChangingInput() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("int_value", -123);
        tag.putLong("long_value", 4_294_967_297L);
        CompoundTag original = tag.copy();
        ValueInput input = input(tag);

        for (int i = 0; i < 3; i++) {
            assertAll(
                () -> assertEquals(
                    OptionalInt.of(-123),
                    StrictNbtReader.readStrictInt(input, "int_value")
                ),
                () -> assertEquals(
                    OptionalLong.of(4_294_967_297L),
                    StrictNbtReader.readStrictLong(input, "long_value")
                ),
                () -> assertEquals(
                    OptionalLong.empty(),
                    StrictNbtReader.readStrictLong(input, "int_value")
                ),
                () -> assertEquals(
                    OptionalInt.empty(),
                    StrictNbtReader.readStrictInt(input, "long_value")
                )
            );
        }
        assertEquals(original, tag);
    }

    @Test
    void shouldRejectNullInputWhenReadingInt() {
        assertThrows(NullPointerException.class, () -> StrictNbtReader.readStrictInt(null, KEY));
    }

    @Test
    void shouldRejectNullKeyWhenReadingInt() {
        assertThrows(
            NullPointerException.class,
            () -> StrictNbtReader.readStrictInt(input(intTag(42)), null)
        );
    }

    @Test
    void shouldRejectNullInputWhenReadingLong() {
        assertThrows(NullPointerException.class, () -> StrictNbtReader.readStrictLong(null, KEY));
    }

    @Test
    void shouldRejectNullKeyWhenReadingLong() {
        assertThrows(
            NullPointerException.class,
            () -> StrictNbtReader.readStrictLong(input(longTag(42L)), null)
        );
    }

    private static void assertRejectedByBothReaders(CompoundTag tag) {
        ValueInput input = input(tag);
        assertAll(
            () -> assertEquals(
                OptionalInt.empty(),
                StrictNbtReader.readStrictInt(input, KEY),
                tag.toString()
            ),
            () -> assertEquals(
                OptionalLong.empty(),
                StrictNbtReader.readStrictLong(input, KEY),
                tag.toString()
            )
        );
    }
}
