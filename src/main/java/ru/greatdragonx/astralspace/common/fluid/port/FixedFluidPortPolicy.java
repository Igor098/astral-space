package ru.greatdragonx.astralspace.common.fluid.port;

import org.jspecify.annotations.NonNull;

import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * Реализация {@link FluidPortPolicy} для механизмов
 * с фиксированными режимами жидкостных портов.
 *
 * <p>Каждому зарегистрированному порту назначается
 * неизменяемый {@link FluidPortMode}. Изменение
 * режима после создания политики не допускается.</p>
 *
 * <p>Физическая доступность портов определяется
 * динамически через переданный {@link Predicate}.
 * Это позволяет использовать политику для механизмов,
 * доступность разъёмов которых зависит от текущего
 * состояния, например от формирования мультиблочной
 * структуры.</p>
 *
 * <p>Для неизвестных портов возвращается
 * {@link FluidPortMode#DISABLED}, а любые
 * операции передачи жидкости запрещаются.</p>
 *
 * <p>Конфигурация портов копируется при создании
 * политики. Последующие изменения исходной карты
 * не влияют на зарегистрированные режимы.</p>
 *
 * <p>Политика не выполняет передачу жидкости,
 * не управляет жидкостными резервуарами
 * и не изменяет конфигурацию портов.</p>
 */
public final class FixedFluidPortPolicy implements FluidPortPolicy {

    private final Map<FluidPortId, FluidPortMode> modes;
    private final Predicate<FluidPortId> availability;

    /**
     * Создаёт политику с фиксированными режимами
     * жидкостных портов.
     *
     * <p>Переданная конфигурация копируется в неизменяемую
     * карту. Каждый зарегистрированный порт сохраняет
     * назначенный режим на протяжении всего времени
     * существования политики.</p>
     *
     * <p>Функция проверки доступности не вызывается
     * при создании политики. Она сохраняется и вызывается
     * непосредственно при проверке доступности
     * конкретного порта.</p>
     *
     * <p>Пустая конфигурация допускается. В этом случае
     * все идентификаторы считаются неизвестными.</p>
     *
     * @param modes        отображение идентификаторов портов
     *                     на их фиксированные режимы
     * @param availability функция динамической проверки
     *                     физической доступности портов
     * @throws NullPointerException если {@code modes}
     *                              или {@code availability}
     *                              равен {@code null},
     *                              либо конфигурация содержит
     *                              {@code null} в ключах
     *                              или значениях
     */
    public FixedFluidPortPolicy(
        @NonNull Map<FluidPortId, FluidPortMode> modes,
        @NonNull Predicate<FluidPortId> availability
    )
    {
        this.modes = Map.copyOf(Objects.requireNonNull(
            modes,
            "Fluid modes cannot be null"
        ));
        this.availability = Objects.requireNonNull(
            availability,
            "Fluid availability cannot be null"
        );
    }

    /**
     * {@inheritDoc}
     *
     * <p>Возвращает фиксированный режим зарегистрированного
     * жидкостного порта.</p>
     *
     * <p>Для неизвестного порта возвращается
     * {@link FluidPortMode#DISABLED}.</p>
     *
     * <p>Результат не зависит от текущей физической
     * доступности порта.</p>
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

        return this.modes.getOrDefault(
            portId,
            FluidPortMode.DISABLED
        );
    }

    /**
     * {@inheritDoc}
     *
     * <p>Для зарегистрированного порта вызывает
     * переданную при создании политики функцию
     * проверки доступности.</p>
     *
     * <p>Проверка выполняется при каждом вызове метода,
     * поэтому изменения состояния механизма учитываются
     * без пересоздания политики.</p>
     *
     * <p>Для неизвестного порта возвращается
     * {@code false} без вызова функции доступности.</p>
     *
     * <p>Проверка не зависит от режима порта.
     * Порт в режиме {@link FluidPortMode#DISABLED}
     * может оставаться физически доступным.</p>
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

        if (!this.modes.containsKey(portId)) {
            return false;
        }

        return this.availability.test(portId);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Для политики фиксированных портов изменение
     * режимов всегда запрещено.</p>
     *
     * <p>Метод возвращает {@code false} независимо
     * от существования порта, его доступности
     * и запрошенного режима.</p>
     *
     * <p>Попытка повторно установить текущий режим
     * также считается недопустимой операцией изменения.</p>
     *
     * @return всегда {@code false}
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

        return false;
    }
}
