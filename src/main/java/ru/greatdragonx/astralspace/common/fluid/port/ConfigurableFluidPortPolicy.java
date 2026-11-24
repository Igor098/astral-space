package ru.greatdragonx.astralspace.common.fluid.port;

import org.jetbrains.annotations.Unmodifiable;
import org.jspecify.annotations.NonNull;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Реализация {@link FluidPortPolicy} для жидкостных портов
 * с настраиваемыми режимами работы.
 *
 * <p>Каждый зарегистрированный порт имеет неизменяемый
 * набор допустимых режимов {@link FluidPortMode}.
 * Текущий режим получается динамически через переданный
 * {@link Function}.</p>
 *
 * <p>Физическая доступность порта определяется отдельно
 * через {@link Predicate} и не зависит от его режима.</p>
 *
 * <p>Если текущий режим отсутствует или не входит в набор
 * допустимых режимов, порт считается отключённым.</p>
 *
 * <p>Для неизвестных идентификаторов портов возвращается
 * {@link FluidPortMode#DISABLED}, а любые операции
 * передачи жидкости запрещаются.</p>
 *
 * <p>Конфигурация допустимых режимов копируется
 * при создании политики. Последующие изменения
 * исходных коллекций не влияют на зарегистрированные
 * ограничения.</p>
 *
 * <p>Политика не изменяет пользовательские настройки,
 * не хранит жидкость, не выполняет её передачу
 * и не управляет Minecraft capabilities.</p>
 *
 * <p>Политика также не выбирает внутренние резервуары.
 * Разграничение доступа к входным и выходным
 * резервуарам выполняется обработчиками механизма
 * с учётом текущего режима порта.</p>
 */
public final class ConfigurableFluidPortPolicy implements FluidPortPolicy {
    private final Map<FluidPortId, Set<FluidPortMode>> allowedModes;
    private final Function<FluidPortId, FluidPortMode> modeProvider;
    private final Predicate<FluidPortId> availability;

    /**
     * Создаёт политику настраиваемых жидкостных портов.
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
     * @param allowedModes допустимые режимы для каждого
     *                     зарегистрированного порта
     * @param modeProvider функция получения текущего
     *                     режима жидкостного порта
     * @param availability функция проверки физической
     *                     доступности порта
     * @throws NullPointerException     если любой аргумент
     *                                  равен {@code null},
     *                                  либо конфигурация содержит
     *                                  {@code null} в ключах,
     *                                  наборах или режимах
     * @throws IllegalArgumentException если хотя бы один
     *                                  набор допустимых
     *                                  режимов пуст
     */
    public ConfigurableFluidPortPolicy(
        Map<FluidPortId, Set<FluidPortMode>> allowedModes,
        Function<FluidPortId, FluidPortMode> modeProvider,
        Predicate<FluidPortId> availability
    )
    {
        Objects.requireNonNull(
            modeProvider,
            "modeProvider cannot be null"
        );

        Objects.requireNonNull(
            availability,
            "availability cannot be null"
        );
        this.allowedModes = Objects.requireNonNull(
                allowedModes,
                "allowedModes cannot be null"
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
     * <p>Возвращает {@link FluidPortMode#DISABLED},
     * если текущий режим отсутствует либо не разрешён
     * конфигурацией порта.</p>
     *
     * @throws NullPointerException если {@code portId}
     *                              равен {@code null}
     */
    @Override
    public FluidPortMode getMode(@NonNull FluidPortId portId) {
        Objects.requireNonNull(
            portId,
            "portId cannot be null"
        );

        Set<FluidPortMode> permittedModes = this.allowedModes.get(portId);
        if (permittedModes == null) {
            return FluidPortMode.DISABLED;
        }

        FluidPortMode currentMode = this.modeProvider.apply(portId);
        if (currentMode == null) {
            return FluidPortMode.DISABLED;
        }

        if (!permittedModes.contains(currentMode)) {
            return FluidPortMode.DISABLED;
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
    public boolean isAvailable(@NonNull FluidPortId portId) {
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
        @NonNull FluidPortId portId,
        @NonNull FluidPortMode newMode
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

        Set<FluidPortMode> permittedModes = this.allowedModes.get(portId);
        if (permittedModes == null) {
            return false;
        }

        return permittedModes.contains(newMode);
    }

    private static @NonNull @Unmodifiable Set<FluidPortMode> copyAllowedModes(
        Set<FluidPortMode> modes
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
