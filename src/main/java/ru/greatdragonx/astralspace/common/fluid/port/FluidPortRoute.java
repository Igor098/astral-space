package ru.greatdragonx.astralspace.common.fluid.port;

import org.jspecify.annotations.NonNull;
import ru.greatdragonx.astralspace.common.fluid.storage.FluidTankId;

import java.util.List;
import java.util.Objects;

/**
 * Неизменяемое описание маршрутов жидкостного порта
 * к резервуарам механизма.
 *
 * <p>Определяет три независимых направления доступа
 * к резервуарам, представленных идентификаторами
 * {@link FluidTankId}:</p>
 *
 * <ul>
 *     <li>{@code inputTanks} — резервуары, в которые
 *         разрешается вставка жидкости при работе
 *         порта в режиме {@link FluidPortMode#INPUT}.</li>
 *     <li>{@code outputTanks} — резервуары, из которых
 *         разрешается извлечение жидкости при работе
 *         порта в режиме {@link FluidPortMode#OUTPUT}.</li>
 *     <li>{@code serviceTanks} — резервуары, из которых
 *         разрешается сервисное извлечение жидкости
 *         при работе порта в режиме
 *         {@link FluidPortMode#SERVICE}.</li>
 * </ul>
 *
 * <p>Один резервуар может присутствовать в нескольких
 * списках, если это предусмотрено конфигурацией механизма.
 * Например, входной резервуар может одновременно
 * использоваться для обычного заполнения и сервисного
 * извлечения жидкости.</p>
 *
 * <p>Все списки копируются в неизменяемые коллекции.
 * Порядок идентификаторов внутри каждого списка сохраняется.
 * Последующие изменения исходных коллекций
 * не влияют на созданный маршрут.</p>
 *
 * <p>Запись не проверяет существование указанных
 * резервуаров и совместимость их ролей с направлениями
 * маршрутизации. Эти проверки выполняются отдельно
 * через {@link FluidPortRoutingValidator}.</p>
 *
 * <p>Маршрут не хранит жидкости, не определяет текущий
 * режим порта и не выполняет операции передачи ресурсов.
 * Фактическое разрешение операций определяется
 * обработчиком {@link FluidPortHandler} с учётом
 * {@link FluidPortPolicy}.</p>
 *
 * @param inputTanks   идентификаторы резервуаров,
 *                     доступных для обычной вставки жидкости
 * @param outputTanks  идентификаторы резервуаров,
 *                     доступных для обычного извлечения жидкости
 * @param serviceTanks идентификаторы резервуаров,
 *                     доступных для сервисного извлечения
 * @see FluidPortRouting
 * @see FluidPortRoutingValidator
 * @see FluidPortHandler
 * @see FluidPortMode
 */
public record FluidPortRoute(
    @NonNull List<FluidTankId> inputTanks,
    @NonNull List<FluidTankId> outputTanks,
    @NonNull List<FluidTankId> serviceTanks
)
{

    /**
     * Создаёт неизменяемое описание маршрутов
     * жидкостного порта.
     *
     * <p>Каждый переданный список копируется с помощью
     * {@link List#copyOf(java.util.Collection)}.
     * Полученные коллекции не допускают изменений
     * и сохраняют исходный порядок элементов.</p>
     *
     * <p>Пустые списки допускаются. Это позволяет
     * создавать маршруты, не поддерживающие отдельные
     * направления передачи жидкости.</p>
     *
     * <p>Один идентификатор может присутствовать
     * в нескольких списках. Удаление повторяющихся
     * идентификаторов конструктором не выполняется.</p>
     *
     * <p>Проверка существования резервуаров
     * и совместимости их ролей не выполняется.</p>
     *
     * @param inputTanks   список входных резервуаров
     * @param outputTanks  список выходных резервуаров
     * @param serviceTanks список резервуаров
     *                     для сервисного извлечения
     * @throws NullPointerException если любой из списков
     *                              равен {@code null}
     *                              или содержит
     *                              {@code null} в качестве
     *                              идентификатора резервуара
     */

    public FluidPortRoute {
        inputTanks = List.copyOf(Objects.requireNonNull(
            inputTanks,
            "inputTanks cannot be null"
        ));

        outputTanks = List.copyOf(Objects.requireNonNull(
            outputTanks,
            "outputTanks cannot be null"
        ));

        serviceTanks = List.copyOf(Objects.requireNonNull(
            serviceTanks,
            "serviceTanks cannot be null"
        ));
    }
}
