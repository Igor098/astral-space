package ru.greatdragonx.astralspace.common.machine;

import net.minecraft.core.Direction;

import java.util.Objects;

/**
 * Утилитарный класс преобразования логических сторон
 * механизма в мировые направления и обратно.
 *
 * <p>Преобразования выполняются относительно
 * горизонтального направления ориентации механизма.</p>
 *
 * <p>Каждой логической стороне соответствует
 * ровно одно мировое направление. Прямое и обратное
 * преобразования взаимно однозначны при одинаковой
 * ориентации механизма.</p>
 *
 * <p>Поддерживаются четыре горизонтальные ориентации:
 * {@link Direction#NORTH},
 * {@link Direction#EAST},
 * {@link Direction#SOUTH} и
 * {@link Direction#WEST}.</p>
 *
 * <p>Вертикальные направления могут использоваться
 * в качестве направлений сторон, но не в качестве
 * ориентации самого механизма.</p>
 *
 * <p>Класс не хранит состояние, не обращается
 * к игровому миру и не изменяет ориентацию
 * механизма самостоятельно.</p>
 *
 * @see MachineSide
 * @see Direction
 */
public final class MachineSideDirections {
    private MachineSideDirections() {
    }

    /**
     * Преобразует логическую сторону механизма
     * в соответствующее мировое направление.
     *
     * <p>Преобразование выполняется согласно
     * следующим правилам:</p>
     *
     * <ul>
     *     <li>FRONT — направление ориентации;</li>
     *     <li>BACK — противоположное направление;</li>
     *     <li>LEFT — поворот направления ориентации
     *         на 90 градусов по часовой стрелке;</li>
     *     <li>RIGHT — поворот направления ориентации
     *         на 90 градусов против часовой стрелки;</li>
     *     <li>TOP — {@link Direction#UP};</li>
     *     <li>BOTTOM — {@link Direction#DOWN}.</li>
     * </ul>
     *
     * <p>Горизонтальная ориентация проверяется
     * независимо от преобразуемой стороны,
     * включая TOP и BOTTOM.</p>
     *
     * @param side   логическая сторона механизма
     * @param facing горизонтальная ориентация механизма
     * @return соответствующее мировое направление
     * @throws NullPointerException     если side или facing
     *                                  равен {@code null}
     * @throws IllegalArgumentException если facing
     *                                  не является
     *                                  горизонтальным
     */
    public static Direction toWorld(MachineSide side, Direction facing) {
        Objects.requireNonNull(side, "Machine side cannot be null");
        MachineSideDirections.assertFacing(facing);
        return switch (side) {
            case FRONT -> facing;
            case BACK -> facing.getOpposite();
            case LEFT -> facing.getClockWise();
            case RIGHT -> facing.getCounterClockWise();
            case TOP -> Direction.UP;
            case BOTTOM -> Direction.DOWN;
        };
    }

    /**
     * Определяет логическую сторону механизма
     * по указанному мировому направлению.
     *
     * <p>Выполняет обратное преобразование
     * относительно заданной горизонтальной
     * ориентации механизма.</p>
     *
     * <p>Поиск выполняется среди всех значений
     * {@link MachineSide} с использованием
     * {@link #toWorld(MachineSide, Direction)}.</p>
     *
     * <p>Для корректной горизонтальной ориентации
     * каждому мировому направлению соответствует
     * ровно одна логическая сторона.</p>
     *
     * @param direction преобразуемое мировое направление
     * @param facing    горизонтальная ориентация механизма
     * @return соответствующая логическая сторона
     * @throws NullPointerException     если direction
     *                                  или facing равен
     *                                  {@code null}
     * @throws IllegalArgumentException если facing
     *                                  не является
     *                                  горизонтальным
     * @throws IllegalStateException    если соответствующую
     *                                  логическую сторону
     *                                  невозможно определить
     */
    public static MachineSide fromWorld(Direction direction, Direction facing) {
        Objects.requireNonNull(direction, "World direction cannot be null");
        MachineSideDirections.assertFacing(facing);

        for (MachineSide side : MachineSide.values()) {
            Direction worldDirection = toWorld(side, facing);

            if (worldDirection == direction) {
                return side;
            }
        }

        throw new IllegalStateException("Cannot resolve machine side for world direction: " +
            direction +
            ", facing: " +
            facing);
    }

    /**
     * Проверяет допустимость направления
     * ориентации механизма.
     *
     * <p>Допускаются только горизонтальные
     * направления. Вертикальные направления
     * UP и DOWN отклоняются.</p>
     *
     * @param facing проверяемое направление ориентации
     * @throws NullPointerException     если facing
     *                                  равен {@code null}
     * @throws IllegalArgumentException если facing
     *                                  не является
     *                                  горизонтальным
     */
    private static void assertFacing(Direction facing) {
        Objects.requireNonNull(facing, "Facing direction cannot be null");
        if (!facing.getAxis().isHorizontal()) {
            throw new IllegalArgumentException("Facing direction must be horizontal: " + facing);
        }
    }
}
