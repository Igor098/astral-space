package ru.greatdragonx.astralspace.common.machine.serialization;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import ru.greatdragonx.astralspace.common.energy.port.EnergyPortMode;
import ru.greatdragonx.astralspace.common.fluid.port.FluidPortMode;
import ru.greatdragonx.astralspace.common.machine.MachineSide;

import java.util.Objects;

/**
 * Утилитарный класс кодирования логических сторон
 * и режимов ресурсных портов механизма
 * в целочисленные значения.
 *
 * <p>Поддерживает независимое кодирование
 * логических сторон, энергетических режимов
 * и жидкостных режимов.</p>
 *
 * <p>Числовые коды определяются явно
 * и не зависят от порядковых номеров
 * констант перечислений.</p>
 *
 * <p>Для энергетических и жидкостных портов
 * поддерживается специальный код {@code -1},
 * обозначающий отсутствие значения режима.</p>
 *
 * <p>Отсутствующее значение отличается
 * от явно заданного отключённого режима.</p>
 *
 * <p>Неизвестные числовые коды отклоняются
 * исключением при декодировании.</p>
 *
 * <p>Класс не хранит состояние, не изменяет
 * конфигурацию портов и не выполняет передачу
 * ресурсов.</p>
 *
 * @see MachineSide
 * @see EnergyPortMode
 * @see FluidPortMode
 */
public final class MachinePortDataCodec {
    private MachinePortDataCodec() {
    }

    /**
     * Кодирует логическую сторону механизма
     * в соответствующее целочисленное значение.
     *
     * <p>Использует фиксированное соответствие
     * между логическими сторонами и кодами
     * в диапазоне от {@code 0} до {@code 5}.</p>
     *
     * <p>Результат не зависит от мировой
     * ориентации механизма и порядкового
     * номера константы перечисления.</p>
     *
     * @param side кодируемая логическая сторона
     * @return числовой код логической стороны
     * @throws NullPointerException если side
     *                              равен {@code null}
     */
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

    /**
     * Восстанавливает логическую сторону механизма
     * по её целочисленному коду.
     *
     * <p>Допускаются коды в диапазоне
     * от {@code 0} до {@code 5} включительно.</p>
     *
     * <p>Выполняет обратное преобразование
     * относительно {@link #encodeSide(MachineSide)}.</p>
     *
     * @param offset числовой код логической стороны
     * @return соответствующая логическая сторона
     * @throws IllegalArgumentException если передан
     *                                  неизвестный код
     */
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

    /**
     * Кодирует режим энергетического порта
     * в соответствующее целочисленное значение.
     *
     * <p>Использует следующие числовые коды:</p>
     *
     * <ul>
     *     <li>{@code -1} — отсутствие значения;</li>
     *     <li>{@code 0} — DISABLED;</li>
     *     <li>{@code 1} — OUTPUT;</li>
     *     <li>{@code 2} — INPUT.</li>
     * </ul>
     *
     * <p>Кодирование не зависит от порядкового
     * номера константы перечисления.</p>
     *
     * @param mode кодируемый энергетический режим
     *             или {@code null}, если значение
     *             отсутствует
     * @return числовой код энергетического режима
     */
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

    /**
     * Восстанавливает режим энергетического порта
     * по его целочисленному коду.
     *
     * <p>Выполняет обратное преобразование
     * относительно {@link #encodeEnergy(EnergyPortMode)}.</p>
     *
     * <p>Код {@code -1} преобразуется в {@code null},
     * а код {@code 0} — в явно заданный режим
     * {@link EnergyPortMode#DISABLED}.</p>
     *
     * @param code числовой код энергетического режима
     * @return соответствующий энергетический режим
     * или {@code null}, если значение отсутствует
     * @throws IllegalArgumentException если передан
     *                                  неизвестный код
     */
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

    /**
     * Кодирует режим жидкостного порта
     * в соответствующее целочисленное значение.
     *
     * <p>Использует следующие числовые коды:</p>
     *
     * <ul>
     *     <li>{@code -1} — отсутствие значения;</li>
     *     <li>{@code 0} — DISABLED;</li>
     *     <li>{@code 1} — INPUT;</li>
     *     <li>{@code 2} — OUTPUT;</li>
     *     <li>{@code 3} — SERVICE.</li>
     * </ul>
     *
     * <p>Кодирование не зависит от порядкового
     * номера константы перечисления.</p>
     *
     * @param mode кодируемый жидкостный режим
     *             или {@code null}, если значение
     *             отсутствует
     * @return числовой код жидкостного режима
     */
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

    /**
     * Восстанавливает режим жидкостного порта
     * по его целочисленному коду.
     *
     * <p>Выполняет обратное преобразование
     * относительно {@link #encodeFluid(FluidPortMode)}.</p>
     *
     * <p>Код {@code -1} преобразуется в {@code null},
     * а код {@code 0} — в явно заданный режим
     * {@link FluidPortMode#DISABLED}.</p>
     *
     * @param code числовой код жидкостного режима
     * @return соответствующий жидкостный режим
     * или {@code null}, если значение отсутствует
     * @throws IllegalArgumentException если передан
     *                                  неизвестный код
     */
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
