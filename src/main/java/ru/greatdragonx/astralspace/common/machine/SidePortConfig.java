package ru.greatdragonx.astralspace.common.machine;

import java.util.Objects;
import java.util.Set;

/**
 * Неизменяемая конфигурация режима работы
 * отдельного порта механизма.
 *
 * <p>Содержит текущий режим порта и множество
 * допустимых режимов работы.</p>
 *
 * <p>Множество допустимых режимов должно быть
 * непустым и обязательно содержать текущий
 * режим порта.</p>
 *
 * <p>Изменение режима требует создания нового
 * экземпляра конфигурации.</p>
 *
 * <p>Не определяет физическое расположение порта,
 * тип передаваемого ресурса, доступность порта
 * и ограничения передачи ресурсов.</p>
 *
 * <p>Не выполняет переключение режима
 * и передачу ресурсов самостоятельно.</p>
 *
 * @param mode         текущий режим работы порта
 * @param allowedModes неизменяемое множество
 *                     допустимых режимов работы
 * @see PortMode
 * @see SideConfiguration
 */
public record SidePortConfig(PortMode mode, Set<PortMode> allowedModes) {
    /**
     * Создаёт неизменяемую конфигурацию порта
     * и проверяет её инварианты.
     *
     * <p>Текущий режим должен входить в непустое
     * множество допустимых режимов.</p>
     *
     * <p>Переданное множество допустимых режимов
     * защитно копируется. Последующие изменения
     * исходного множества не влияют на созданную
     * конфигурацию.</p>
     *
     * <p>Допускается наличие только одного
     * разрешённого режима.</p>
     *
     * <p>Порядок элементов возвращаемого
     * множества не гарантируется.</p>
     *
     * @param mode         текущий режим работы порта
     * @param allowedModes исходное множество
     *                     допустимых режимов
     * @throws NullPointerException     если mode или
     *                                  allowedModes равен
     *                                  {@code null} либо
     *                                  множество содержит
     *                                  {@code null} и проверка
     *                                  дошла до его копирования
     * @throws IllegalArgumentException если множество
     *                                  допустимых режимов
     *                                  пусто или не содержит
     *                                  текущий режим
     */
    public SidePortConfig {
        Objects.requireNonNull(mode, "Port mode cannot be null");
        Objects.requireNonNull(allowedModes, "Allowed modes cannot be null");
        if (allowedModes.isEmpty()) {
            throw new IllegalArgumentException("Allowed modes cannot be empty");
        }
        if (!allowedModes.contains(mode)) {
            throw new IllegalArgumentException("Current mode '" +
                mode +
                "' is not in allowed modes");
        }
        allowedModes = Set.copyOf(allowedModes);
    }
}
