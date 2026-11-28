package ru.greatdragonx.astralspace.common.serialization;

import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.Tag;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.level.storage.ValueInput;
import org.jspecify.annotations.NonNull;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;

/**
 * Предоставляет операции строгого чтения числовых NBT-значений.
 *
 * <p>В отличие от стандартных методов числового чтения
 * {@link ValueInput}, не допускает автоматического
 * преобразования между различными числовыми NBT-типами.</p>
 *
 * <p>Для чтения исходного NBT-тега используется
 * {@link ExtraCodecs#NBT}. Значение принимается только
 * при точном соответствии ожидаемому типу тега.</p>
 *
 * <p>Отсутствующее или имеющее неподходящий тип значение
 * представляется пустым {@link OptionalInt}
 * или {@link OptionalLong}.</p>
 *
 * <p>Класс не проверяет допустимость числовых значений
 * с точки зрения бизнес-логики, не ограничивает их
 * диапазон и не устанавливает значения по умолчанию.
 * Эти обязанности остаются у вызывающего кода.</p>
 *
 * <p>Операции чтения не изменяют исходные данные.</p>
 */
public final class StrictNbtReader {
    private StrictNbtReader() {}

    /**
     * Читает целочисленное значение без автоматического
     * преобразования числовых NBT-типов.
     *
     * <p>Значение принимается только в том случае,
     * если исходный NBT-тег является экземпляром
     * {@link IntTag}.</p>
     *
     * <p>Другие числовые типы отклоняются независимо
     * от того, может ли их значение быть представлено
     * типом {@code int} без потери данных.</p>
     *
     * <p>Поддерживается весь диапазон значений {@code int},
     * включая отрицательные значения и ноль.</p>
     *
     * @param input входной объект с сохранёнными данными
     * @param key   название читаемого NBT-поля
     * @return прочитанное целочисленное значение либо
     * {@link OptionalInt#empty()}, если поле отсутствует,
     * не может быть прочитано или имеет неподходящий NBT-тип
     * @throws NullPointerException если input или key
     *                              равен {@code null}
     */
    public static @NonNull OptionalInt readStrictInt(@NonNull ValueInput input, @NonNull String key)
    {
        Objects.requireNonNull(input, "input cannot be null");
        Objects.requireNonNull(key, "key cannot be null");

        Optional<Tag> value = input.read(key, ExtraCodecs.NBT);
        if (value.isEmpty()) {
            return OptionalInt.empty();
        }

        Tag tag = value.get();
        if (tag instanceof IntTag intTag) {
            return OptionalInt.of(intTag.intValue());
        }

        return OptionalInt.empty();
    }

    /**
     * Читает длинное целочисленное значение без автоматического
     * преобразования числовых NBT-типов.
     *
     * <p>Значение принимается только в том случае,
     * если исходный NBT-тег является экземпляром
     * {@link LongTag}.</p>
     *
     * <p>Другие числовые типы отклоняются независимо
     * от того, может ли их значение быть представлено
     * типом {@code long} без потери данных.</p>
     *
     * <p>Поддерживается весь диапазон значений {@code long},
     * включая отрицательные значения, ноль и значения,
     * выходящие за пределы диапазона {@code int}.</p>
     *
     * @param input входной объект с сохранёнными данными
     * @param key   название читаемого NBT-поля
     * @return прочитанное длинное целочисленное значение либо
     * {@link OptionalLong#empty()}, если поле отсутствует,
     * не может быть прочитано или имеет неподходящий NBT-тип
     * @throws NullPointerException если input или key
     *                              равен {@code null}
     */
    public static @NonNull OptionalLong readStrictLong(
        @NonNull ValueInput input,
        @NonNull String key
    )
    {
        Objects.requireNonNull(input, "input cannot be null");
        Objects.requireNonNull(key, "key cannot be null");

        Optional<Tag> value = input.read(key, ExtraCodecs.NBT);
        if (value.isEmpty()) {
            return OptionalLong.empty();
        }

        Tag tag = value.get();
        if (tag instanceof LongTag longTag) {
            return OptionalLong.of(longTag.longValue());
        }

        return OptionalLong.empty();
    }
}
