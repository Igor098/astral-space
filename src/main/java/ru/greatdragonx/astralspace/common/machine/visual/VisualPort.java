package ru.greatdragonx.astralspace.common.machine.visual;

import ru.greatdragonx.astralspace.common.machine.MachineSide;

import java.util.Objects;

/**
 * Неизменяемое описание визуального порта механизма.
 *
 * <p>Определяет идентификатор визуального порта,
 * его тип и логическую сторону механизма,
 * на которой он располагается.</p>
 *
 * <p>Используется для построения визуального
 * представления ресурсных подключений механизма.</p>
 *
 * <p>Расположение задаётся относительно логической
 * стороны и не зависит от текущей мировой
 * ориентации механизма.</p>
 *
 * <p>Не содержит текущий режим работы порта,
 * его физическую доступность, координаты
 * визуального размещения и масштаб.</p>
 *
 * <p>Не управляет передачей ресурсов
 * и не предоставляет Minecraft capabilities.</p>
 *
 * @param id   идентификатор визуального порта
 * @param type тип визуального порта
 * @param side логическая сторона механизма
 * @see VisualPortId
 * @see VisualPortType
 * @see MachineSide
 */
public record VisualPort(VisualPortId id, VisualPortType type, MachineSide side) {
    /**
     * Создаёт неизменяемое описание визуального порта.
     *
     * <p>Все компоненты описания являются обязательными.</p>
     *
     * <p>Уникальность идентификатора среди других
     * визуальных портов и допустимость совместного
     * размещения нескольких портов на одной стороне
     * не проверяются.</p>
     *
     * @param id   идентификатор визуального порта
     * @param type тип визуального порта
     * @param side логическая сторона механизма
     * @throws NullPointerException если любой
     *                              аргумент равен
     *                              {@code null}
     */
    public VisualPort {
        Objects.requireNonNull(id, "Visual port id cannot be null");
        Objects.requireNonNull(type, "Visual port type cannot be null");
        Objects.requireNonNull(side, "Machine side cannot be null");
    }
}
