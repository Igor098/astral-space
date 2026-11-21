package ru.greatdragonx.astralspace.common.fluid.port;

import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.TransferPreconditions;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jetbrains.annotations.Unmodifiable;
import org.jspecify.annotations.NonNull;
import ru.greatdragonx.astralspace.common.fluid.storage.FluidTankId;
import ru.greatdragonx.astralspace.common.fluid.storage.MachineFluidStorage;

import java.util.*;


/**
 * Обработчик передачи жидкостей через отдельный порт механизма.
 *
 * <p>Реализует {@link ResourceHandler} для предоставления
 * внешним потребителям доступа к внутренним резервуарам
 * {@link MachineFluidStorage}.</p>
 *
 * <p>Обработчик связывает четыре компонента:</p>
 *
 * <ul>
 *     <li>{@link FluidPortId} — идентификатор обслуживаемого порта.</li>
 *     <li>{@link FluidPortPolicy} — правила доступа и текущий режим порта.</li>
 *     <li>{@link FluidPortRouting} — конфигурацию маршрутов к резервуарам.</li>
 *     <li>{@link MachineFluidStorage} — фактическое хранилище жидкости.</li>
 * </ul>
 *
 * <p>При создании обработчика определяется набор доступных
 * через порт резервуаров. Каждому уникальному резервуару
 * назначается фиксированный внешний индекс, сопоставленный
 * с его внутренним индексом в хранилище.</p>
 *
 * <p>Порядок внешних индексов формируется последовательно
 * из входных, выходных и сервисных маршрутов.
 * Повторяющиеся идентификаторы резервуаров исключаются,
 * при этом сохраняется порядок их первого появления.</p>
 *
 * <p>Сопоставление индексов не изменяется при переключении
 * режима порта. Это позволяет внешним потребителям
 * использовать ранее полученный обработчик без изменения
 * структуры доступных ячеек.</p>
 *
 * <p>Возможность вставки и извлечения жидкости определяется
 * динамически при каждой операции. Обработчик учитывает
 * текущий режим порта, его физическую доступность
 * и принадлежность резервуара соответствующему маршруту.</p>
 *
 * <p>Поддерживаются следующие направления передачи:</p>
 *
 * <ul>
 *     <li>{@link FluidPortMode#INPUT} — вставка жидкости
 *         в резервуары входного маршрута.</li>
 *     <li>{@link FluidPortMode#OUTPUT} — извлечение жидкости
 *         из резервуаров выходного маршрута.</li>
 *     <li>{@link FluidPortMode#SERVICE} — извлечение жидкости
 *         из резервуаров сервисного маршрута.</li>
 *     <li>{@link FluidPortMode#DISABLED} — запрет
 *         вставки и извлечения жидкости.</li>
 * </ul>
 *
 * <p>Операции передачи выполняются непосредственно
 * через {@link MachineFluidStorage} в переданном
 * транзакционном контексте.</p>
 *
 * <p>Обработчик не завершает и не отменяет транзакции
 * самостоятельно. Управление транзакциями остаётся
 * ответственностью вызывающего кода.</p>
 *
 * <p>Класс не хранит жидкость самостоятельно,
 * не распределяет её автоматически между резервуарами
 * и не управляет настройками режимов портов.</p>
 *
 * @see FluidPortPolicy
 * @see FluidPortRouting
 * @see FluidPortRoute
 * @see MachineFluidStorage
 * @see ResourceHandler
 */
public final class FluidPortHandler implements ResourceHandler<FluidResource> {
    private final FluidPortId portId;
    private final FluidPortPolicy policy;
    private final MachineFluidStorage storage;
    private final FluidPortRoute route;
    private final List<FluidTankId> tankIds;
    private final int[] tankIndices;


    /**
     * Создаёт обработчик жидкостного порта механизма.
     *
     * <p>Сохраняет идентификатор порта, политику доступа,
     * конфигурацию маршрутизации и ссылку на внутреннее
     * хранилище жидкости.</p>
     *
     * <p>Находит маршрут указанного порта и формирует
     * упорядоченный список уникальных идентификаторов
     * резервуаров, доступных через данный порт.</p>
     *
     * <p>Для каждого идентификатора определяется внутренний
     * индекс резервуара в {@link MachineFluidStorage}.
     * Полученное сопоставление внешних и внутренних индексов
     * фиксируется на всё время существования обработчика.</p>
     *
     * <p>Если маршрут порта отсутствует или один из указанных
     * резервуаров не найден в хранилище, создание обработчика
     * завершается исключением.</p>
     *
     * <p>Конструктор не выполняет передачу жидкости,
     * не изменяет содержимое резервуаров и не проверяет
     * текущий режим доступа к ним.</p>
     *
     * @param portId  идентификатор обслуживаемого
     *                жидкостного порта
     * @param policy  политика режимов работы
     *                и доступности порта
     * @param routing проверенная конфигурация
     *                маршрутов жидкостных портов
     * @param storage внутреннее хранилище жидкости механизма
     * @throws NullPointerException     если любой из аргументов
     *                                  равен {@code null}
     * @throws IllegalArgumentException если для указанного
     *                                  порта отсутствует маршрут
     *                                  или маршрут ссылается
     *                                  на резервуар, отсутствующий
     *                                  в переданном хранилище
     */
    public FluidPortHandler(
        @NonNull FluidPortId portId,
        @NonNull FluidPortPolicy policy,
        @NonNull FluidPortRouting routing,
        @NonNull MachineFluidStorage storage
    )
    {
        this.portId = Objects.requireNonNull(
            portId,
            "portId cannot be null"
        );
        this.policy = Objects.requireNonNull(
            policy,
            "policy cannot be null"
        );
        FluidPortRouting portRouting = Objects.requireNonNull(
            routing,
            "routing cannot be null"
        );
        this.storage = Objects.requireNonNull(
            storage,
            "storage cannot be null"
        );

        this.route = portRouting.findRoute(this.portId)
            .orElseThrow(() -> new IllegalArgumentException("Fluid port '%s' has no route".formatted(this.portId.value())));

        this.tankIds = collectTankIds(this.route);
        this.tankIndices = resolveTankIndices(this.tankIds);
    }

    /**
     * Возвращает количество уникальных резервуаров,
     * доступных через данный жидкостный порт.
     *
     * <p>В результате учитываются все резервуары,
     * перечисленные во входных, выходных и сервисных
     * маршрутах, без повторяющихся идентификаторов.</p>
     *
     * <p>Количество резервуаров определяется при создании
     * обработчика и не изменяется при переключении
     * режима порта или изменении его доступности.</p>
     *
     * @return количество внешних ячеек обработчика
     */
    @Override
    public int size()
    {
        return this.tankIndices.length;
    }

    /**
     * Возвращает тип жидкости, находящейся
     * в резервуаре с указанным внешним индексом.
     *
     * <p>Внешний индекс преобразуется во внутренний индекс
     * резервуара, после чего запрос передаётся
     * в {@link MachineFluidStorage}.</p>
     *
     * <p>Если резервуар пуст, возвращается значение
     * {@link FluidResource#EMPTY}.</p>
     *
     * <p>Метод не зависит от текущего режима порта
     * и не изменяет содержимое резервуара.</p>
     *
     * @param index внешний индекс резервуара
     * @return ресурс жидкости, содержащейся в резервуаре,
     * либо {@link FluidResource#EMPTY}
     * для пустого резервуара
     * @throws IndexOutOfBoundsException если {@code index}
     *                                   выходит за границы
     *                                   внешних ячеек
     */
    @Override
    public FluidResource getResource(
        int index
    )
    {
        int storageIndex = this.toStorageIndex(index);
        return this.storage.getResource(storageIndex);
    }

    /**
     * Возвращает количество жидкости в указанном резервуаре.
     *
     * <p>Использует фиксированное сопоставление внешнего
     * индекса с внутренним индексом хранилища.</p>
     *
     * <p>Количество возвращается в единицах жидкости,
     * используемых системой передачи NeoForge.</p>
     *
     * <p>Для пустого резервуара возвращается {@code 0}.</p>
     *
     * <p>Метод не проверяет разрешения на передачу
     * и не изменяет содержимое резервуара.</p>
     *
     * @param index внешний индекс резервуара
     * @return текущее количество жидкости в резервуаре
     * @throws IndexOutOfBoundsException если {@code index}
     *                                   выходит за границы
     *                                   внешних ячеек
     */
    @Override
    public long getAmountAsLong(
        int index
    )
    {
        int storageIndex = this.toStorageIndex(index);
        return this.storage.getAmountAsLong(storageIndex);
    }

    /**
     * Возвращает доступную вместимость резервуара
     * для указанного типа жидкости.
     *
     * <p>Запрос передаётся во внутреннее хранилище
     * с преобразованием внешнего индекса резервуара.</p>
     *
     * <p>Результат учитывает ограничения хранилища
     * на допустимые типы жидкостей. Если резервуар
     * не принимает указанный ресурс, вместимость
     * для него может составлять {@code 0},
     * даже если физическая вместимость резервуара
     * больше нуля.</p>
     *
     * <p>Метод не учитывает текущий режим порта.
     * Запрет операций через порт не изменяет
     * физические характеристики резервуаров.</p>
     *
     * @param index    внешний индекс резервуара
     * @param resource тип жидкости, для которого
     *                 запрашивается вместимость
     * @return вместимость резервуара для указанного ресурса
     * @throws IndexOutOfBoundsException если {@code index}
     *                                   выходит за границы
     *                                   внешних ячеек
     */
    @Override
    public long getCapacityAsLong(
        int index,
        @NonNull FluidResource resource
    )
    {
        int storageIndex = this.toStorageIndex(index);
        return this.storage.getCapacityAsLong(
            storageIndex,
            resource
        );
    }

    /**
     * Проверяет, допускает ли указанный резервуар
     * хранение заданного типа жидкости.
     *
     * <p>Проверка делегируется {@link MachineFluidStorage}
     * с использованием соответствующего внутреннего индекса.</p>
     *
     * <p>Метод проверяет исключительно ограничения
     * самого резервуара на допустимые ресурсы.</p>
     *
     * <p>Текущий режим порта, его физическая доступность
     * и направление маршрутизации не учитываются.</p>
     *
     * <p>Положительный результат не гарантирует,
     * что вставка жидкости разрешена через данный порт
     * или что в резервуаре достаточно свободного места.</p>
     *
     * @param index    внешний индекс резервуара
     * @param resource проверяемый тип жидкости
     * @return {@code true}, если резервуар допускает
     * указанный ресурс, иначе {@code false}
     * @throws IndexOutOfBoundsException если {@code index}
     *                                   выходит за границы
     *                                   внешних ячеек
     */
    @Override
    public boolean isValid(
        int index,
        @NonNull FluidResource resource
    )
    {
        int storageIndex = this.toStorageIndex(index);
        return this.storage.isValid(
            storageIndex,
            resource
        );
    }

    /**
     * Пытается вставить указанное количество жидкости
     * в резервуар через текущий порт.
     *
     * <p>Операция выполняется для резервуара, определяемого
     * внешним индексом {@code index}.</p>
     *
     * <p>Перед передачей жидкости выполняется проверка
     * корректности аргументов и разрешения на вставку
     * через {@link #canInsert(int)}.</p>
     *
     * <p>Если вставка запрещена политикой порта
     * или резервуар отсутствует во входном маршруте,
     * возвращается {@code 0} без изменения хранилища.</p>
     *
     * <p>При разрешённой операции внешний индекс
     * преобразуется во внутренний, после чего вставка
     * делегируется {@link MachineFluidStorage}.</p>
     *
     * <p>Хранилище самостоятельно проверяет допустимость
     * типа жидкости, существующее содержимое резервуара
     * и доступную вместимость.</p>
     *
     * <p>Допускается частичная вставка. Возвращаемое
     * количество может быть меньше запрошенного,
     * если резервуар не способен принять всю жидкость.</p>
     *
     * <p>Если {@code amount} равен {@code 0},
     * операция не выполняется и возвращается {@code 0}.</p>
     *
     * <p>Все изменения выполняются в переданном
     * {@link TransactionContext}. Метод не вызывает
     * фиксацию или отмену транзакции самостоятельно.</p>
     *
     * <p>Окончательное сохранение изменений зависит
     * от решения вызывающего кода о завершении транзакции.</p>
     *
     * @param index       внешний индекс целевого резервуара
     * @param resource    тип вставляемой жидкости
     * @param amount      запрашиваемое количество жидкости,
     *                    неотрицательное целое число
     * @param transaction контекст транзакции передачи ресурсов
     * @return количество жидкости, принятое хранилищем
     * в рамках переданной транзакции,
     * либо {@code 0}, если вставка невозможна
     * @throws IndexOutOfBoundsException если {@code index}
     *                                   выходит за границы
     *                                   внешних ячеек
     * @throws IllegalArgumentException  если {@code amount}
     *                                   отрицательный
     * @throws NullPointerException      если {@code resource}
     *                                   или {@code transaction}
     *                                   равен {@code null}
     */
    @Override
    public int insert(
        int index,
        @NonNull FluidResource resource,
        int amount,
        @NonNull TransactionContext transaction
    )
    {
        TransferPreconditions.checkNonNegative(amount);
        Objects.requireNonNull(
            resource,
            "resource cannot be null"
        );
        Objects.requireNonNull(
            transaction,
            "transaction cannot be null"
        );
        int storageIndex = this.toStorageIndex(index);

        if (amount == 0) {
            return 0;
        }

        if (!this.canInsert(index)) {
            return 0;
        }

        return this.storage.insert(
            storageIndex,
            resource,
            amount,
            transaction
        );
    }

    /**
     * Пытается извлечь указанное количество жидкости
     * из резервуара через текущий порт.
     *
     * <p>Операция выполняется для резервуара,
     * соответствующего внешнему индексу {@code index}.</p>
     *
     * <p>Перед извлечением проверяется корректность
     * аргументов и разрешение на операцию
     * через {@link #canExtract(int)}.</p>
     *
     * <p>В режиме {@link FluidPortMode#OUTPUT} извлечение
     * допускается только из резервуаров выходного маршрута.</p>
     *
     * <p>В режиме {@link FluidPortMode#SERVICE} извлечение
     * допускается только из резервуаров сервисного маршрута,
     * предназначенных для обслуживания механизма.</p>
     *
     * <p>В режимах {@link FluidPortMode#INPUT}
     * и {@link FluidPortMode#DISABLED} извлечение запрещено.</p>
     *
     * <p>Если операция не разрешена, возвращается {@code 0}
     * без изменения содержимого резервуара.</p>
     *
     * <p>При разрешённом извлечении запрос делегируется
     * {@link MachineFluidStorage}, которое самостоятельно
     * проверяет тип находящейся жидкости и фактически
     * доступное для извлечения количество.</p>
     *
     * <p>Допускается частичное извлечение.
     * Если резервуар содержит меньше жидкости,
     * чем запрошено, может быть извлечено
     * только доступное количество.</p>
     *
     * <p>При {@code amount}, равном {@code 0},
     * операция не выполняется.</p>
     *
     * <p>Изменение содержимого резервуара производится
     * в переданном {@link TransactionContext}.</p>
     *
     * <p>Метод не фиксирует и не отменяет транзакцию.
     * При отмене транзакции изменения хранилища
     * должны быть восстановлены его транзакционным
     * механизмом.</p>
     *
     * @param index       внешний индекс исходного резервуара
     * @param resource    тип извлекаемой жидкости
     * @param amount      запрашиваемое количество жидкости,
     *                    неотрицательное целое число
     * @param transaction контекст транзакции передачи ресурсов
     * @return количество жидкости, извлечённое из хранилища
     * в рамках переданной транзакции,
     * либо {@code 0}, если извлечение невозможно
     * @throws IndexOutOfBoundsException если {@code index}
     *                                   выходит за границы
     *                                   внешних ячеек
     * @throws IllegalArgumentException  если {@code amount}
     *                                   отрицательный
     * @throws NullPointerException      если {@code resource}
     *                                   или {@code transaction}
     *                                   равен {@code null}
     */
    @Override
    public int extract(
        int index,
        @NonNull FluidResource resource,
        int amount,
        @NonNull TransactionContext transaction
    )
    {
        TransferPreconditions.checkNonNegative(amount);
        Objects.requireNonNull(
            resource,
            "resource cannot be null"
        );
        Objects.requireNonNull(
            transaction,
            "transaction cannot be null"
        );
        int storageIndex = this.toStorageIndex(index);

        if (amount == 0) {
            return 0;
        }

        if (!this.canExtract(index)) {
            return 0;
        }

        return this.storage.extract(
            storageIndex,
            resource,
            amount,
            transaction
        );
    }

    /**
     * Формирует упорядоченный список уникальных резервуаров,
     * указанных в маршруте жидкостного порта.
     *
     * <p>Идентификаторы собираются последовательно
     * из трёх списков маршрута:</p>
     *
     * <ol>
     *     <li>Входные резервуары {@code inputTanks}.</li>
     *     <li>Выходные резервуары {@code outputTanks}.</li>
     *     <li>Сервисные резервуары {@code serviceTanks}.</li>
     * </ol>
     *
     * <p>Повторяющиеся идентификаторы исключаются.
     * Порядок первого появления каждого идентификатора
     * сохраняется с помощью {@link LinkedHashSet}.</p>
     *
     * <p>Результат возвращается в виде неизменяемого списка
     * и используется для назначения внешних индексов
     * резервуарам порта.</p>
     *
     * <p>Метод не зависит от текущего режима порта.
     * Все резервуары маршрута включаются в список
     * независимо от того, доступны ли они для передачи
     * жидкости в данный момент.</p>
     *
     * @param route маршрут жидкостного порта
     * @return неизменяемый список уникальных идентификаторов
     * резервуаров в порядке их первого появления
     * @throws NullPointerException если {@code route}
     *                              равен {@code null}
     */
    private static @NonNull @Unmodifiable List<FluidTankId> collectTankIds(
        @NonNull FluidPortRoute route
    )
    {
        Objects.requireNonNull(
            route,
            "route cannot be null"
        );

        Set<FluidTankId> tankIds = new LinkedHashSet<>();
        tankIds.addAll(route.inputTanks());
        tankIds.addAll(route.outputTanks());
        tankIds.addAll(route.serviceTanks());

        return List.copyOf(tankIds);
    }

    /**
     * Преобразует идентификаторы резервуаров во внутренние
     * индексы хранилища жидкости.
     *
     * <p>Для каждого элемента переданного списка выполняется
     * поиск соответствующего резервуара в
     * {@link MachineFluidStorage}.</p>
     *
     * <p>Результат сохраняет порядок исходного списка:
     * позиция элемента в возвращаемом массиве соответствует
     * внешнему индексу резервуара, а значение элемента
     * является его внутренним индексом в хранилище.</p>
     *
     * <p>Например, если внешним индексам {@code 0} и {@code 1}
     * соответствуют внутренние индексы {@code 0} и {@code 2},
     * возвращается массив {@code [0, 2]}.</p>
     *
     * <p>Если идентификатор отсутствует в хранилище,
     * преобразование завершается исключением.</p>
     *
     * <p>Метод не изменяет содержимое резервуаров.</p>
     *
     * @param tankIds упорядоченный список идентификаторов
     *                резервуаров жидкостного порта
     * @return массив внутренних индексов резервуаров,
     * соответствующих внешним индексам обработчика
     * @throws NullPointerException     если {@code tankIds}
     *                                  равен {@code null}
     * @throws IllegalArgumentException если хотя бы один
     *                                  резервуар отсутствует
     *                                  в хранилище
     */
    private int @NonNull [] resolveTankIndices(
        @NonNull List<FluidTankId> tankIds
    )
    {
        Objects.requireNonNull(
            tankIds,
            "tankIds cannot be null"
        );
        int[] indices = new int[tankIds.size()];

        for (int index = 0; index < tankIds.size(); index++) {
            OptionalInt tankIndex = this.storage.findTankIndex(tankIds.get(index));
            if (tankIndex.isEmpty()) {
                throw new IllegalArgumentException("Unknown fluid tank '%s'".formatted(tankIds.get(index)
                    .value()));
            }
            indices[index] = tankIndex.getAsInt();
        }

        return indices;
    }

    /**
     * Преобразует внешний индекс резервуара порта
     * во внутренний индекс хранилища жидкости.
     *
     * <p>Внешний индекс должен находиться в диапазоне
     * от {@code 0} включительно до количества доступных
     * через порт резервуаров исключительно.</p>
     *
     * <p>Для преобразования используется сопоставление
     * индексов, сформированное при создании обработчика.</p>
     *
     * <p>Метод не зависит от текущего режима порта
     * и не проверяет разрешения на передачу жидкости.</p>
     *
     * @param externalIndex внешний индекс резервуара
     *                      в обработчике порта
     * @return соответствующий внутренний индекс
     * резервуара в {@link MachineFluidStorage}
     * @throws IndexOutOfBoundsException если внешний индекс
     *                                   выходит за допустимые
     *                                   границы
     */
    private int toStorageIndex(
        int externalIndex
    )
    {
        if (externalIndex < 0 || externalIndex >= this.tankIndices.length) {
            throw new IndexOutOfBoundsException("Invalid external fluid tank index: " +
                externalIndex);
        }

        return this.tankIndices[externalIndex];
    }

    /**
     * Проверяет разрешение на вставку жидкости
     * в резервуар через текущий порт.
     *
     * <p>Для разрешения операции должны одновременно
     * выполняться следующие условия:</p>
     *
     * <ul>
     *     <li>Политика порта разрешает вставку жидкости.</li>
     *     <li>Резервуар присутствует во входном маршруте
     *         {@link FluidPortRoute#inputTanks()}.</li>
     * </ul>
     *
     * <p>Политика учитывает текущий режим работы
     * и физическую доступность порта.</p>
     *
     * <p>Проверка выполняется при каждом вызове метода,
     * поэтому изменения режима учитываются без пересоздания
     * обработчика.</p>
     *
     * <p>Метод не проверяет тип передаваемой жидкости,
     * свободную вместимость резервуара и не выполняет
     * операцию вставки.</p>
     *
     * @param externalIndex внешний индекс резервуара
     * @return {@code true}, если вставка через порт разрешена,
     * иначе {@code false}
     * @throws IndexOutOfBoundsException если внешний индекс
     *                                   выходит за допустимые
     *                                   границы
     */
    private boolean canInsert(
        int externalIndex
    )
    {
        FluidTankId tankId = this.tankIds.get(externalIndex);

        return this.policy.allowsInsert(this.portId) &&
            this.route.inputTanks()
                .contains(tankId);
    }

    /**
     * Проверяет разрешение на извлечение жидкости
     * из резервуара через текущий порт.
     *
     * <p>Сначала проверяется, разрешает ли политика
     * порта операцию извлечения с учётом текущего
     * режима и физической доступности.</p>
     *
     * <p>Затем применяется правило соответствующего режима:</p>
     *
     * <ul>
     *     <li>{@link FluidPortMode#OUTPUT} — резервуар должен
     *         присутствовать в {@link FluidPortRoute#outputTanks()}.</li>
     *     <li>{@link FluidPortMode#SERVICE} — резервуар должен
     *         присутствовать в {@link FluidPortRoute#serviceTanks()}.</li>
     *     <li>{@link FluidPortMode#INPUT} — извлечение запрещено.</li>
     *     <li>{@link FluidPortMode#DISABLED} — извлечение запрещено.</li>
     * </ul>
     *
     * <p>Обычный выходной маршрут и сервисный маршрут
     * проверяются независимо. Наличие резервуара
     * в сервисном маршруте не предоставляет к нему
     * доступ в режиме {@code OUTPUT}, и наоборот.</p>
     *
     * <p>Метод использует текущую конфигурацию политики
     * при каждом вызове. Переключение режима учитывается
     * без пересоздания обработчика.</p>
     *
     * <p>Фактическое количество жидкости и возможность
     * её извлечения из хранилища здесь не проверяются.</p>
     *
     * @param externalIndex внешний индекс резервуара
     * @return {@code true}, если извлечение через порт
     * разрешено, иначе {@code false}
     * @throws IndexOutOfBoundsException если внешний индекс
     *                                   выходит за допустимые
     *                                   границы
     */
    private boolean canExtract(
        int externalIndex
    )
    {
        FluidTankId tankId = this.tankIds.get(externalIndex);

        if (!this.policy.allowsExtract(this.portId)) {
            return false;
        }

        FluidPortMode mode = this.policy.getMode(this.portId);
        if (mode == FluidPortMode.OUTPUT) {
            return this.route.outputTanks()
                .contains(tankId);
        }
        if (mode == FluidPortMode.SERVICE) {
            return this.route.serviceTanks()
                .contains(tankId);
        }

        return false;
    }
}
