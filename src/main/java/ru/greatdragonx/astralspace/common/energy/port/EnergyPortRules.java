package ru.greatdragonx.astralspace.common.energy.port;

import org.jetbrains.annotations.Unmodifiable;
import org.jspecify.annotations.NonNull;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Неизменяемое упорядоченное описание правил
 * энергетических портов механизма.
 *
 * <p>Определяет общий тип конфигурации механизма
 * и правила каждого логического энергетического порта.</p>
 *
 * <p>Для фиксированной конфигурации каждый порт имеет
 * ровно один разрешённый режим. Для настраиваемой
 * конфигурации каждый порт имеет не менее двух
 * разрешённых режимов.</p>
 *
 * <p>Идентификаторы портов должны быть уникальными.
 * Порядок правил сохраняется. Пустой список
 * правил допускается.</p>
 *
 * <p>Переданный список защитно копируется.
 * Отдельные правила и их множества разрешённых
 * режимов также являются неизменяемыми.</p>
 *
 * <p>Не хранит текущие пользовательские настройки,
 * физическую топологию или runtime-доступность портов.</p>
 *
 * @param configurationType общий тип настройки портов
 * @param rules             упорядоченный список правил портов
 */
public record EnergyPortRules(
    @NonNull ConfigurationType configurationType, @NonNull List<EnergyPortRule> rules
)
{
    /**
     * Определяет возможность пользовательской настройки
     * режимов энергетических портов механизма.
     */
    public enum ConfigurationType {
        /**
         * Фиксированные режимы энергетических портов.
         * Пользовательское изменение режимов не допускается.
         */
        FIXED,
        /**
         * Настраиваемые режимы энергетических портов.
         * Пользователь может выбирать разрешённые режимы.
         */
        CONFIGURABLE
    }

    /**
     * Создаёт неизменяемое описание правил
     * энергетических портов и проверяет его инварианты.
     *
     * @throws NullPointerException     если тип конфигурации,
     *                                  список правил или один
     *                                  из его элементов равен
     *                                  {@code null}
     * @throws IllegalArgumentException если идентификаторы
     *                                  портов повторяются
     *                                  или количество
     *                                  разрешённых режимов
     *                                  не соответствует
     *                                  типу конфигурации
     */
    public EnergyPortRules {
        Objects.requireNonNull(configurationType, "configurationType cannot be null");
        rules = List.copyOf(Objects.requireNonNull(rules, "rules cannot be null"));

        Set<EnergyPortId> knownPorts = new HashSet<>();
        for (EnergyPortRule rule : rules) {
            if (!knownPorts.add(rule.portId())) {
                throw new IllegalArgumentException("Duplicate energy port ID: " +
                    rule.portId().value());
            }

            int allowedModeCount = rule.allowedModes().size();
            switch (configurationType) {
                case FIXED -> {
                    if (allowedModeCount != 1) {
                        throw new IllegalArgumentException(
                            "FIXED energy port must have exactly one allowed mode: " +
                                rule.portId().value() +
                                ", actual: " +
                                allowedModeCount);
                    }
                }
                case CONFIGURABLE -> {
                    if (allowedModeCount < 2) {
                        throw new IllegalArgumentException(
                            "CONFIGURABLE energy port must have at least two allowed modes: " +
                                rule.portId().value() +
                                ", actual: " +
                                allowedModeCount);
                    }
                }
            }
        }
    }

    /**
     * Возвращает идентификаторы всех описанных портов
     * в порядке их объявления.
     *
     * <p>Включает порты, отключённые по умолчанию.
     * Не учитывает текущие режимы и физическую
     * доступность портов.</p>
     *
     * @return неизменяемый упорядоченный список
     * идентификаторов энергетических портов
     */
    public @NonNull @Unmodifiable List<EnergyPortId> portIds() {
        return this.rules.stream().map(EnergyPortRule::portId).toList();
    }

    /**
     * Возвращает отображение идентификаторов портов
     * на их режимы по умолчанию.
     *
     * <p>Предназначено для построения начальной
     * конфигурации портов и фиксированной политики.</p>
     *
     * <p>Порядок обхода возвращаемой карты
     * не гарантируется.</p>
     *
     * @return неизменяемая карта режимов по умолчанию
     */
    public @NonNull @Unmodifiable Map<EnergyPortId, EnergyPortMode> defaultModes() {
        return this.rules.stream().collect(Collectors.toUnmodifiableMap(
            EnergyPortRule::portId,
            EnergyPortRule::defaultMode
        ));
    }

    /**
     * Возвращает отображение идентификаторов портов
     * на множества их разрешённых режимов.
     *
     * <p>Предназначено для построения политики
     * настраиваемых энергетических портов.</p>
     *
     * <p>Возвращаемая карта и содержащиеся в ней
     * множества являются неизменяемыми.
     * Порядок обхода карты не гарантируется.</p>
     *
     * @return неизменяемая карта разрешённых режимов
     */
    public @NonNull @Unmodifiable Map<EnergyPortId, Set<EnergyPortMode>> allowedModes() {
        return this.rules.stream().collect(Collectors.toUnmodifiableMap(
            EnergyPortRule::portId,
            EnergyPortRule::allowedModes
        ));
    }
}
