package ru.greatdragonx.astralspace.common.fluid.port;

import org.jspecify.annotations.NonNull;
import ru.greatdragonx.astralspace.common.fluid.storage.FluidTankDefinition;
import ru.greatdragonx.astralspace.common.fluid.storage.FluidTankId;
import ru.greatdragonx.astralspace.common.fluid.storage.FluidTankRole;

import java.util.*;


/**
 * Валидатор конфигурации маршрутов жидкостных портов механизма.
 *
 * <p>Проверяет соответствие маршрутов {@link FluidPortRoute}
 * определениям резервуаров {@link FluidTankDefinition}.</p>
 *
 * <p>Для каждого идентификатора резервуара, указанного
 * в маршрутах, проверяется его существование и совместимость
 * роли с назначением маршрута.</p>
 *
 * <p>Применяются следующие правила:</p>
 *
 * <ul>
 *     <li>Входной маршрут допускает резервуары
 *         с ролями {@link FluidTankRole#INPUT}
 *         и {@link FluidTankRole#BIDIRECTIONAL}.</li>
 *     <li>Выходной маршрут допускает резервуары
 *         с ролями {@link FluidTankRole#OUTPUT}
 *         и {@link FluidTankRole#BIDIRECTIONAL}.</li>
 *     <li>Сервисный маршрут допускает только резервуары
 *         с ролью {@link FluidTankRole#INPUT}.</li>
 * </ul>
 *
 * <p>Также проверяется отсутствие повторяющихся
 * идентификаторов среди определений резервуаров.</p>
 *
 * <p>Валидатор не изменяет переданные данные,
 * не создаёт резервуары и не выполняет передачу жидкостей.</p>
 *
 * @see FluidPortRouting
 * @see FluidPortRoute
 * @see FluidTankDefinition
 * @see FluidTankRole
 */
public final class FluidPortRoutingValidator {
    private FluidPortRoutingValidator() {
    }

    /**
     * Проверяет корректность маршрутов относительно
     * определений резервуаров механизма.
     *
     * <p>Сначала формируется отображение идентификаторов
     * резервуаров на их определения. Если обнаруживается
     * повторяющийся идентификатор, проверка завершается
     * исключением.</p>
     *
     * <p>Затем проверяется каждый зарегистрированный маршрут.
     * Все резервуары, перечисленные во входных, выходных
     * и сервисных маршрутах, должны существовать
     * и иметь допустимую для соответствующего
     * направления роль.</p>
     *
     * <p>Если проверка завершилась без исключения,
     * конфигурация считается корректной.</p>
     *
     * <p>Метод не возвращает результат и не изменяет
     * переданные коллекции.</p>
     *
     * @param routes          маршруты жидкостных портов,
     *                        которые необходимо проверить
     * @param tankDefinitions определения резервуаров,
     *                        относительно которых
     *                        выполняется проверка
     * @throws NullPointerException     если обязательные
     *                                  входные данные или
     *                                  их элементы равны {@code null}
     * @throws IllegalArgumentException если обнаружен
     *                                  неизвестный резервуар,
     *                                  несовместимая роль
     *                                  или повторяющийся
     *                                  идентификатор резервуара
     */
    public static void validate(
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
            "definitions cannot be null"
        );

        Map<FluidTankId, FluidTankDefinition> definitions = new HashMap<>();

        for (FluidTankDefinition definition : tankDefinitions) {
            FluidTankDefinition previous = definitions.putIfAbsent(
                definition.id(),
                definition
            );

            if (previous != null) {
                throw new IllegalArgumentException("Duplicate fluid tank id '%s'".formatted(definition.id()
                    .value()));
            }
        }

        for (Map.Entry<FluidPortId, FluidPortRoute> entry : routes.entrySet()) {
            FluidPortId portId = entry.getKey();
            FluidPortRoute route = entry.getValue();

            validateTankIds(
                portId,
                route.inputTanks(),
                definitions,
                EnumSet.of(
                    FluidTankRole.INPUT,
                    FluidTankRole.BIDIRECTIONAL
                )
            );
            validateTankIds(
                portId,
                route.outputTanks(),
                definitions,
                EnumSet.of(
                    FluidTankRole.OUTPUT,
                    FluidTankRole.BIDIRECTIONAL
                )
            );
            validateTankIds(
                portId,
                route.serviceTanks(),
                definitions,
                EnumSet.of(FluidTankRole.INPUT)
            );
        }
    }

    private static void validateTankIds(
        @NonNull FluidPortId portId,
        @NonNull List<FluidTankId> tankIds,
        @NonNull Map<FluidTankId, FluidTankDefinition> tankDefinitions,
        @NonNull Set<FluidTankRole> allowedRoles
    )
    {
        for (FluidTankId tankId : tankIds) {
            FluidTankDefinition definition = tankDefinitions.get(tankId);
            if (definition == null) {
                throw new IllegalArgumentException("Unknown fluid tank '%s' in port '%s'".formatted(
                    tankId.value(),
                    portId.value()
                ));
            }

            FluidTankRole role = definition.role();
            if (!allowedRoles.contains(role)) {
                throw new IllegalArgumentException("Fluid tank '%s' with role '%s' is not allowed in port '%s'".formatted(
                    tankId.value(),
                    role.name(),
                    portId.value()
                ));
            }
        }
    }
}
