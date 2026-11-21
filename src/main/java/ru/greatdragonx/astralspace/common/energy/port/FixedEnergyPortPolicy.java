package ru.greatdragonx.astralspace.common.energy.port;

import org.jspecify.annotations.NonNull;

import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * Реализация {@link EnergyPortPolicy} для механизмов
 * с фиксированными режимами энергетических портов.
 *
 * <p>Каждому зарегистрированному порту назначается
 * неизменяемый {@link EnergyPortMode}. Изменение
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
 * {@link EnergyPortMode#DISABLED}, а любые
 * энергетические операции запрещаются.</p>
 *
 * <p>Конфигурация портов копируется при создании
 * политики. Последующие изменения исходного словаря
 * не влияют на зарегистрированные режимы.</p>
 *
 * <p>Политика не выполняет передачу энергии,
 * не управляет энергетическим хранилищем
 * и не изменяет конфигурацию портов.</p>
 */
public final class FixedEnergyPortPolicy implements EnergyPortPolicy {
    private final Map<EnergyPortId, EnergyPortMode> modes;
    private final Predicate<EnergyPortId> availability;

    /**
     * Создаёт политику с фиксированными режимами
     * энергетических портов.
     *
     * <p>Переданная конфигурация копируется в неизменяемый
     * словарь. Каждый зарегистрированный порт сохраняет
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
    public FixedEnergyPortPolicy(
        Map<EnergyPortId, EnergyPortMode> modes,
        Predicate<EnergyPortId> availability
    )
    {
        this.modes = Map.copyOf(Objects.requireNonNull(
            modes,
            "Energy modes cannot be null"
        ));
        this.availability = Objects.requireNonNull(
            availability,
            "Energy availability cannot be null"
        );
    }

    /**
     * {@inheritDoc}
     *
     * <p>Возвращает фиксированный режим зарегистрированного
     * энергетического порта.</p>
     *
     * <p>Для неизвестного порта возвращается
     * {@link EnergyPortMode#DISABLED}.</p>
     *
     * <p>Результат не зависит от текущей физической
     * доступности порта.</p>
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

        return this.modes.getOrDefault(
            portId,
            EnergyPortMode.DISABLED
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
     * Порт в режиме {@link EnergyPortMode#DISABLED}
     * может оставаться физически доступным.</p>
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
     * от существования порта, его доступности и
     * запрошенного режима.</p>
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

        return false;
    }
}
