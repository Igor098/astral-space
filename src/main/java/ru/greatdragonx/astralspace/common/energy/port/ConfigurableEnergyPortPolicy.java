package ru.greatdragonx.astralspace.common.energy.port;

import org.jetbrains.annotations.Unmodifiable;
import org.jspecify.annotations.NonNull;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Реализация {@link EnergyPortPolicy} для энергетических портов
 * с настраиваемыми режимами работы.
 *
 * <p>Каждый зарегистрированный порт имеет неизменяемый
 * набор допустимых режимов {@link EnergyPortMode}.
 * Текущий режим получается динамически через переданный
 * {@link Function}.</p>
 *
 * <p>Физическая доступность порта определяется отдельно
 * через {@link Predicate} и не зависит от его режима.</p>
 *
 * <p>Если текущий режим отсутствует или не входит в набор
 * допустимых режимов, порт считается отключённым.</p>
 *
 * <p>Конфигурация допустимых режимов копируется
 * при создании политики. Изменение исходных коллекций
 * не влияет на зарегистрированные ограничения.</p>
 *
 * <p>Политика не изменяет пользовательские настройки,
 * не сохраняет их и не управляет Minecraft capabilities.
 * За эти операции отвечает владелец механизма.</p>
 */
public final class ConfigurableEnergyPortPolicy implements EnergyPortPolicy {
    private final Map<EnergyPortId, Set<EnergyPortMode>> allowedModes;
    private final Function<EnergyPortId, EnergyPortMode> modeProvider;
    private final Predicate<EnergyPortId> availability;

    /**
     * Создаёт политику настраиваемых энергетических портов.
     *
     * <p>Каждому зарегистрированному порту назначается
     * неизменяемый набор допустимых режимов работы.</p>
     *
     * <p>Переданная конфигурация копируется
     * в неизменяемую карту с неизменяемыми наборами
     * режимов. Изменение исходных коллекций
     * после создания политики не влияет на её состояние.</p>
     *
     * <p>Пустая конфигурация допускается. В этом случае
     * все идентификаторы портов считаются неизвестными.</p>
     *
     * <p>Провайдер текущих режимов и функция проверки
     * доступности сохраняются для динамического
     * обращения во время работы механизма.
     * При создании политики они не вызываются.</p>
     *
     * @param allowedModes допустимые режимы для каждого порта
     * @param modeProvider функция получения текущего режима порта
     * @param availability функция проверки физической доступности порта
     * @throws NullPointerException     если любой аргумент равен null,
     *                                  либо конфигурация содержит
     *                                  null в ключах, наборах или режимах
     * @throws IllegalArgumentException если хотя бы один набор
     *                                  допустимых режимов пуст
     */
    public ConfigurableEnergyPortPolicy(
        Map<EnergyPortId, Set<EnergyPortMode>> allowedModes,
        Function<EnergyPortId, EnergyPortMode> modeProvider,
        Predicate<EnergyPortId> availability
    )
    {
        Objects.requireNonNull(
            modeProvider,
            "Energy modeProvider cannot be null"
        );

        Objects.requireNonNull(
            availability,
            "Energy availability cannot be null"
        );

        this.allowedModes = Objects.requireNonNull(
                allowedModes,
                "Energy allowedModes cannot be null"
            )
            .entrySet()
            .stream()
            .collect(Collectors.toUnmodifiableMap(
                Map.Entry::getKey,
                entry -> copyAllowedModes(entry.getValue())
            ));
        this.modeProvider = modeProvider;
        this.availability = availability;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Возвращает {@link EnergyPortMode#DISABLED},
     * если текущий режим отсутствует либо не разрешён
     * конфигурацией порта.</p>
     *
     * @throws NullPointerException если {@code portId}
     *                              равен {@code null}
     */
    @Override
    public EnergyPortMode getMode(@NonNull EnergyPortId portId) {
        Objects.requireNonNull(
            portId,
            "portId cannot be null"
        );

        Set<EnergyPortMode> permittedModes = this.allowedModes.get(portId);

        if (permittedModes == null) {
            return EnergyPortMode.DISABLED;
        }

        EnergyPortMode currentMode = this.modeProvider.apply(portId);
        if (currentMode == null) {
            return EnergyPortMode.DISABLED;
        }

        if (!permittedModes.contains(currentMode)) {
            return EnergyPortMode.DISABLED;
        }

        return currentMode;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Доступность определяется динамически через
     * переданную функцию проверки.</p>
     *
     * <p>Для неизвестного порта возвращается
     * {@code false} без вызова функции доступности.</p>
     *
     * @throws NullPointerException если {@code portId}
     *                              равен {@code null}
     */
    @Override
    public boolean isAvailable(@NonNull EnergyPortId portId) {
        Objects.requireNonNull(
            portId,
            "portId cannot be null"
        );

        if (!this.allowedModes.containsKey(portId)) {
            return false;
        }

        return this.availability.test(portId);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Проверяет наличие запрошенного режима
     * в неизменяемом наборе допустимых режимов порта.</p>
     *
     * <p>Метод не изменяет текущий режим и не зависит
     * от физической доступности порта.</p>
     *
     * @throws NullPointerException если {@code portId}
     *                              или {@code newMode}
     *                              равен {@code null}
     */
    @Override
    public boolean canChangeMode(
        @NonNull EnergyPortId portId,
        @NonNull EnergyPortMode newMode
    )
    {
        Objects.requireNonNull(
            portId,
            "portId cannot be null"
        );

        Objects.requireNonNull(
            newMode,
            "newMode cannot be null"
        );

        Set<EnergyPortMode> permittedModes = this.allowedModes.get(portId);
        if (permittedModes == null) {
            return false;
        }

        return permittedModes.contains(newMode);
    }

    private static @NonNull @Unmodifiable Set<EnergyPortMode> copyAllowedModes(
        Set<EnergyPortMode> modes
    )
    {
        Objects.requireNonNull(
            modes,
            "modes cannot be null"
        );

        if (modes.isEmpty()) {
            throw new IllegalArgumentException("Allowed modes cannot be empty");
        }

        return Set.copyOf(modes);
    }
}
