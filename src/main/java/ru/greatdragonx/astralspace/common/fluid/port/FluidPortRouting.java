package ru.greatdragonx.astralspace.common.fluid.port;

import org.jspecify.annotations.NonNull;
import ru.greatdragonx.astralspace.common.fluid.storage.FluidTankDefinition;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;


/**
 * Неизменяемая конфигурация маршрутизации жидкостных портов механизма.
 *
 * <p>Определяет соответствие между идентификаторами жидкостных
 * портов {@link FluidPortId} и маршрутами {@link FluidPortRoute},
 * содержащими идентификаторы доступных резервуаров.</p>
 *
 * <p>При создании конфигурации выполняется обязательная проверка
 * всех маршрутов с помощью {@link FluidPortRoutingValidator}.
 * Проверяется существование указанных резервуаров и совместимость
 * их ролей с назначением соответствующих маршрутов.</p>
 *
 * <p>Маршруты сохраняются в неизменяемой карте.
 * Последующие изменения исходной карты не влияют
 * на созданную конфигурацию.</p>
 *
 * <p>Класс не хранит жидкости, не выполняет операции передачи
 * ресурсов и не управляет режимами работы портов.
 * Эти обязанности возлагаются на другие компоненты
 * жидкостной системы.</p>
 *
 * <p>Маршрутизация описывает допустимые связи между портами
 * и резервуарами, но не определяет, какие операции разрешены
 * в текущий момент. Фактический доступ зависит от режима
 * порта и его доступности.</p>
 *
 * @see FluidPortRoute
 * @see FluidPortRoutingValidator
 * @see FluidPortHandler
 */
public final class FluidPortRouting {
    private final Map<FluidPortId, FluidPortRoute> routes;

    /**
     * Создаёт проверенную конфигурацию маршрутизации
     * жидкостных портов механизма.
     *
     * <p>Переданная карта маршрутов копируется в неизменяемую
     * структуру. После этого выполняется проверка совместимости
     * маршрутов с определениями резервуаров.</p>
     *
     * <p>Если хотя бы один маршрут содержит неизвестный резервуар,
     * использует несовместимую роль резервуара либо список
     * определений содержит повторяющиеся идентификаторы,
     * создание конфигурации завершается исключением.</p>
     *
     * <p>Пустая карта маршрутов допускается.
     * Пустой список определений резервуаров также допускается,
     * если маршруты не ссылаются на отсутствующие резервуары.</p>
     *
     * <p>Определения резервуаров используются только
     * для проверки конфигурации и не сохраняются
     * в состоянии создаваемого объекта.</p>
     *
     * @param routes          отображение идентификаторов портов
     *                        на соответствующие маршруты
     * @param tankDefinitions определения резервуаров,
     *                        доступных механизму
     * @throws NullPointerException     если {@code routes}
     *                                  или {@code tankDefinitions}
     *                                  равен {@code null},
     *                                  либо карта содержит
     *                                  {@code null} в ключах
     *                                  или значениях
     * @throws IllegalArgumentException если маршрутизация
     *                                  содержит неизвестные
     *                                  резервуары, несовместимые
     *                                  роли или повторяющиеся
     *                                  идентификаторы резервуаров
     */
    public FluidPortRouting(
        @NonNull Map<FluidPortId, FluidPortRoute> routes,
        @NonNull List<FluidTankDefinition> tankDefinitions
    )
    {
        Objects.requireNonNull(
            routes,
            "routes cannot be null"
        );
        Objects.requireNonNull(
            tankDefinitions,
            "tankDefinitions cannot be null"
        );

        Map<FluidPortId, FluidPortRoute> routesSnapshot = Map.copyOf(routes);

        FluidPortRoutingValidator.validate(
            routesSnapshot,
            tankDefinitions
        );

        this.routes = routesSnapshot;
    }

    /**
     * Возвращает маршрут жидкостного порта
     * по его идентификатору.
     *
     * <p>Поиск выполняется по значению {@link FluidPortId},
     * поэтому для получения маршрута не требуется передавать
     * тот же экземпляр идентификатора, который использовался
     * при создании конфигурации.</p>
     *
     * <p>Если порт отсутствует в конфигурации,
     * возвращается пустой {@link Optional}.</p>
     *
     * <p>Наличие маршрута не означает, что порт доступен
     * для передачи жидкости в текущий момент.</p>
     *
     * @param portId идентификатор искомого жидкостного порта
     * @return {@link Optional}, содержащий маршрут порта,
     * либо пустой {@link Optional}, если маршрут
     * не зарегистрирован
     * @throws NullPointerException если {@code portId}
     *                              равен {@code null}
     */
    public @NonNull Optional<FluidPortRoute> findRoute(
        @NonNull FluidPortId portId
    )
    {
        Objects.requireNonNull(
            portId,
            "portId cannot be null"
        );

        return Optional.ofNullable(this.routes.get(portId));
    }
    
    /**
     * Проверяет наличие маршрута для указанного жидкостного порта.
     *
     * <p>Метод проверяет только регистрацию идентификатора
     * в конфигурации маршрутизации.</p>
     *
     * <p>Порт с пустым {@link FluidPortRoute} считается
     * зарегистрированным, поскольку маршрут существует,
     * даже если не содержит идентификаторов резервуаров.</p>
     *
     * <p>Проверка не зависит от текущего режима работы
     * или физической доступности порта.</p>
     *
     * @param portId идентификатор проверяемого жидкостного порта
     * @return {@code true}, если маршрут зарегистрирован,
     * иначе {@code false}
     * @throws NullPointerException если {@code portId}
     *                              равен {@code null}
     */
    public boolean hasRoute(
        @NonNull FluidPortId portId
    )
    {
        Objects.requireNonNull(
            portId,
            "portId cannot be null"
        );

        return this.routes.containsKey(portId);
    }
}
