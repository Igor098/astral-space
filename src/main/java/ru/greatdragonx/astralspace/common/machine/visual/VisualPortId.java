package ru.greatdragonx.astralspace.common.machine.visual;

import java.util.Objects;

/**
 * Неизменяемый логический идентификатор
 * визуального порта механизма.
 *
 * <p>Используется для идентификации визуальных
 * портов независимо от их типа, расположения
 * и мировой ориентации механизма.</p>
 *
 * <p>Идентификатор представлен непустой строкой,
 * которая не должна состоять только из пробельных
 * символов или содержать пробельные символы
 * в начале и конце.</p>
 *
 * <p>Регистр символов имеет значение.
 * Автоматическая нормализация значения
 * не выполняется.</p>
 *
 * <p>Не определяет тип порта, его физическое
 * расположение и режим работы.</p>
 *
 * @param value строковое значение идентификатора
 * @see VisualPort
 */
public record VisualPortId(String value) {
    /**
     * Создаёт идентификатор визуального порта
     * и проверяет допустимость его значения.
     *
     * <p>Значение не должно быть пустым,
     * состоять только из пробельных символов
     * или содержать пробельные символы
     * в начале и конце строки.</p>
     *
     * <p>Проверка начальных и конечных пробельных
     * символов выполняется посредством
     * {@link String#strip()}.</p>
     *
     * <p>Допустимое значение сохраняется
     * без изменений.</p>
     *
     * @param value строковое значение идентификатора
     * @throws NullPointerException     если value
     *                                  равен {@code null}
     * @throws IllegalArgumentException если value
     *                                  является пустым,
     *                                  состоит только
     *                                  из пробельных символов
     *                                  или содержит их
     *                                  в начале или конце
     */
    public VisualPortId {
        Objects.requireNonNull(value, "Visual port value cannot be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("Visual port value cannot be blank");
        }
        if (!value.equals(value.strip())) {
            throw new IllegalArgumentException(
                "Visual port value must not contain leading or trailing whitespace");
        }
    }
}
