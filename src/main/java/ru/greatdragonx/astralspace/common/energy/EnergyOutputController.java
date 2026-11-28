package ru.greatdragonx.astralspace.common.energy;

import net.neoforged.neoforge.transfer.TransferPreconditions;
import net.neoforged.neoforge.transfer.transaction.RootCommitJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jspecify.annotations.NonNull;
import ru.greatdragonx.astralspace.common.energy.component.EnergyComponent;
import ru.greatdragonx.astralspace.common.energy.port.EnergyPortId;
import ru.greatdragonx.astralspace.common.energy.port.EnergyPortPolicy;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.LongSupplier;

/**
 * Координирует выдачу энергии через энергетические порты механизма.
 *
 * <p>Объединяет операции извлечения энергии, выполняемые
 * по инициативе внешнего потребителя (Pull) и самого
 * механизма (Push), в рамках единого исходящего
 * бюджета передачи энергии за игровой тик.</p>
 *
 * <p>Контроллер использует следующие компоненты:</p>
 *
 * <ul>
 *   <li>{@link EnergyComponent} — хранение и извлечение энергии;</li>
 *   <li>{@link EnergyPortPolicy} — проверка актуальных разрешений
 *       и физической доступности портов;</li>
 *   <li>{@link EnergyDistributionManager} — адаптивное распределение
 *       энергии между выходными портами;</li>
 *   <li>{@link EnergyTransferBudget} — ограничение общего объёма
 *       выдачи энергии за игровой тик;</li>
 *   <li>{@link EnergyTransferTracker} — учёт подтверждённой
 *       передачи энергии.</li>
 * </ul>
 *
 * <p>Контроллер обслуживает неизменяемый набор известных
 * энергетических портов. Текущая доступность и разрешённые
 * направления передачи определяются живой политикой
 * непосредственно при выполнении операции.</p>
 *
 * <p>Список доступных выходных портов распределителя
 * согласуется отдельно посредством
 * {@link #refreshAvailablePorts()}.</p>
 *
 * <p>Операции извлечения выполняются в предоставленном
 * вызывающим кодом транзакционном контексте.
 * Изменения энергетического буфера, бюджета, истории спроса,
 * фактического извлечения и кредитов справедливости
 * поддерживают транзакционный откат.</p>
 *
 * <p>Статистика передачи обновляется только после
 * подтверждения корневой транзакции.</p>
 *
 * <p>Контроллер не управляет приёмом энергии, производственными
 * процессами, жизненным циклом BlockEntity и непосредственной
 * передачей энергии соседним механизмам.</p>
 *
 * <p>Предназначен для последовательного использования
 * в контексте одного энергетического механизма.
 * Не является потокобезопасным.</p>
 *
 * @see EnergyComponent
 * @see EnergyPortPolicy
 * @see EnergyDistributionManager
 * @see EnergyTransferBudget
 * @see EnergyTransferTracker
 */
public final class EnergyOutputController {
    private final EnergyComponent energy;

    private final List<EnergyPortId> ports;
    private final Set<EnergyPortId> knownPorts;

    private final EnergyPortPolicy policy;
    private final EnergyDistributionManager distribution;
    private final EnergyTransferBudget outputBudget;
    private final EnergyTransferTracker tracker;
    private final LongSupplier currentTickSupplier;

    /**
     * Определяет происхождение операции извлечения энергии.
     *
     * <p>Происхождение операции влияет на регистрацию
     * спроса, но не изменяет общие ограничения бюджета,
     * правила распределения и транзакционные гарантии.</p>
     */
    public enum ExtractionOrigin {
        /**
         * Извлечение энергии по инициативе внешнего
         * потребителя.
         *
         * <p>Первоначально запрошенное количество энергии
         * регистрируется распределителем как Pull-спрос.</p>
         */
        PULL,
        /**
         * Извлечение энергии по инициативе самого механизма
         * для последующей передачи получателю.
         *
         * <p>Операция не регистрирует дополнительный
         * Pull-спрос. Push-доступность учитывается
         * отдельным механизмом наблюдения.</p>
         */
        PUSH
    }

    /**
     * Создаёт контроллер исходящего энергетического контура.
     *
     * <p>Все необходимые компоненты предоставляются
     * вызывающим кодом и используются на протяжении
     * всего времени существования контроллера.</p>
     *
     * <p>Из списка портов создаётся неизменяемая защитная
     * копия с сохранением первоначального порядка.
     * Дополнительно формируется неизменяемое множество
     * идентификаторов для проверки принадлежности порта.</p>
     *
     * <p>Список может быть пустым, но не должен содержать
     * null-элементы или повторяющиеся идентификаторы.</p>
     *
     * <p>Конструктор не обращается к поставщику игрового
     * времени, не проверяет текущую доступность портов,
     * не изменяет энергетический буфер и не выполняет
     * согласование портов распределителя.</p>
     *
     * <p>Первоначальное согласование доступных портов
     * выполняется владельцем контроллера после готовности
     * необходимых runtime-зависимостей.</p>
     *
     * @param energy              энергетическое хранилище
     *                            механизма
     * @param ports               упорядоченный список всех
     *                            известных энергетических портов
     * @param policy              актуальная политика доступа
     *                            к энергетическим портам
     * @param distribution        менеджер распределения
     *                            исходящей энергии
     * @param outputBudget        общий бюджет выдачи энергии
     *                            для Pull и Push
     * @param tracker             трекер подтверждённой
     *                            передачи энергии
     * @param currentTickSupplier поставщик текущего
     *                            игрового времени
     * @throws NullPointerException     если одна из зависимостей
     *                                  или элемент списка портов
     *                                  равен {@code null}
     * @throws IllegalArgumentException если список содержит
     *                                  повторяющиеся
     *                                  идентификаторы портов
     */
    public EnergyOutputController(
        @NonNull EnergyComponent energy,
        @NonNull List<EnergyPortId> ports,
        @NonNull EnergyPortPolicy policy,
        @NonNull EnergyDistributionManager distribution,
        @NonNull EnergyTransferBudget outputBudget,
        @NonNull EnergyTransferTracker tracker,
        @NonNull LongSupplier currentTickSupplier
    )
    {
        this.energy = Objects.requireNonNull(energy, "energy cannot be null");

        this.ports = List.copyOf(Objects.requireNonNull(ports, "ports cannot be null"));
        this.knownPorts = Set.copyOf(this.ports);
        if (this.ports.size() != this.knownPorts.size()) {
            throw new IllegalArgumentException("ports cannot contain duplicates");
        }

        this.policy = Objects.requireNonNull(policy, "policy cannot be null");
        this.distribution = Objects.requireNonNull(distribution, "distribution cannot be null");
        this.outputBudget = Objects.requireNonNull(outputBudget, "outputBudget cannot be null");
        this.tracker = Objects.requireNonNull(tracker, "tracker cannot be null");
        this.currentTickSupplier = Objects.requireNonNull(
            currentTickSupplier,
            "currentTickSupplier cannot be null"
        );
    }

    /**
     * Согласует список доступных выходных портов
     * с менеджером распределения энергии.
     *
     * <p>Последовательно обходит известные энергетические
     * порты и отбирает только те, через которые актуальная
     * политика разрешает выдачу энергии посредством
     * {@link EnergyPortPolicy#allowsExtract(EnergyPortId)}.</p>
     *
     * <p>Первоначальный порядок идентификаторов сохраняется.
     * Полученный список передаётся в
     * {@link EnergyDistributionManager#updateAvailablePorts(List)}.
     * При отсутствии разрешённых выходных портов
     * передаётся пустой список.</p>
     *
     * <p>Метод не изменяет набор известных портов
     * контроллера, не пересоздаёт распределитель,
     * не сбрасывает бюджет передачи и не обращается
     * к поставщику игрового времени.</p>
     *
     * <p>Сохранение истории оставшихся портов и удаление
     * истории исключённых портов выполняются менеджером
     * распределения энергии.</p>
     *
     * <p>Метод предназначен для первоначального согласования
     * портов и последующего обновления при изменении
     * конфигурации или физической доступности.</p>
     *
     * <p>Должен вызываться вне открытых транзакций
     * передачи энергии. Выбор безопасного момента
     * выполнения является ответственностью
     * вызывающего кода.</p>
     *
     * <p>Метод не инициирует сохранение BlockEntity,
     * инвалидацию capabilities, сетевые уведомления
     * или визуальную синхронизацию.</p>
     *
     * <p>Актуальная политика применяется непосредственно
     * при извлечении энергии независимо от момента
     * последнего согласования портов.</p>
     */
    public void refreshAvailablePorts() {
        List<EnergyPortId> portIds =
            this.ports.stream().filter(this.policy::allowsExtract).toList();

        this.distribution.updateAvailablePorts(portIds);
    }

    /**
     * Извлекает энергию через указанный энергетический порт
     * по инициативе внешнего потребителя.
     *
     * <p>Делегирует операцию основной перегрузке
     * {@link #extract(EnergyPortId, int, TransactionContext, ExtractionOrigin)}
     * с происхождением {@link ExtractionOrigin#PULL}.</p>
     *
     * <p>Операция подчиняется актуальной политике портов,
     * общему исходящему бюджету и правилам адаптивного
     * распределения энергии.</p>
     *
     * @param portId      идентификатор энергетического порта
     * @param amount      запрошенное количество энергии, FE
     * @param transaction контекст транзакции извлечения
     * @return фактически извлечённое количество энергии,
     * FE; {@code 0}, если извлечение невозможно
     * @throws NullPointerException     если portId или transaction
     *                                  равен {@code null}
     * @throws IllegalArgumentException если amount отрицателен
     */
    public int extract(EnergyPortId portId, int amount, TransactionContext transaction) {
        return this.extract(portId, amount, transaction, ExtractionOrigin.PULL);
    }

    /**
     * Выполняет транзакционное извлечение энергии
     * через указанный энергетический порт.
     *
     * <p>Поддерживает операции с происхождением
     * {@link ExtractionOrigin#PULL} и
     * {@link ExtractionOrigin#PUSH}, использующие единый
     * исходящий бюджет и общий алгоритм адаптивного
     * распределения энергии.</p>
     *
     * <p>Перед выполнением операции проверяются обязательные
     * аргументы и неотрицательность запрошенного количества
     * энергии. Ошибки аргументов не скрываются штатным
     * отказом в выдаче энергии.</p>
     *
     * <p>При нулевом запросе, неизвестном идентификаторе
     * порта или запрете выдачи актуальной политикой
     * метод возвращает {@code 0} без обращения
     * к поставщику игрового времени и изменения
     * состояния энергетического контура.</p>
     *
     * <p>Для допустимой операции текущее игровое время
     * считывается один раз и используется во всех
     * последующих расчётах и операциях учёта.</p>
     *
     * <p>Извлечение выполняется в следующем порядке:</p>
     *
     * <ol>
     *   <li>Регистрация первоначального запроса энергии
     *       в менеджере распределения для Pull-операции.</li>
     *   <li>Определение доступного запаса энергии
     *       и остатка исходящего бюджета.</li>
     *   <li>Расчёт адаптивного ограничения передачи
     *       через указанный порт.</li>
     *   <li>Окончательное ограничение выдачи
     *       общим исходящим бюджетом.</li>
     *   <li>Извлечение разрешённого количества энергии
     *       из энергетического хранилища.</li>
     *   <li>Регистрация фактически извлечённого количества
     *       в бюджете и менеджере распределения.</li>
     *   <li>Регистрация отложенного действия для учёта
     *       подтверждённой передачи энергии.</li>
     * </ol>
     *
     * <p>При расчёте доступного запаса значение энергии
     * ограничивается {@link Integer#MAX_VALUE} перед
     * преобразованием в int. Исходное состояние
     * энергетического хранилища при этом не изменяется.</p>
     *
     * <p>Pull-спрос регистрируется до расчёта квот
     * и бюджета. Поэтому допустимый Pull-запрос может
     * быть зарегистрирован даже при отсутствии
     * фактически извлечённой энергии.</p>
     *
     * <p>Push-операция не регистрирует Pull-спрос,
     * но участвует в общем распределении энергии
     * и расходует тот же исходящий бюджет.</p>
     *
     * <p>Расход бюджета и учёт фактического извлечения
     * выполняются только при положительном количестве
     * извлечённой энергии.</p>
     *
     * <p>Все транзакционные изменения выполняются
     * в предоставленном контексте. Контроллер
     * не открывает, не подтверждает и не закрывает
     * транзакцию самостоятельно.</p>
     *
     * <p>При откате транзакции восстанавливаются
     * затронутые состояния энергетического хранилища,
     * бюджета и менеджера распределения.</p>
     *
     * <p>Учёт подтверждённой передачи посредством
     * {@link EnergyTransferTracker} откладывается до
     * подтверждения корневой транзакции через
     * {@link RootCommitJournal}. Подтверждение
     * дочерней транзакции само по себе не приводит
     * к обновлению статистики.</p>
     *
     * <p>Статистика регистрируется за игровой тик,
     * в котором выполнялась операция извлечения,
     * без повторного обращения к поставщику времени.</p>
     *
     * <p>Возвращённое количество энергии представляет
     * результат извлечения в рамках текущей транзакции
     * и не означает, что транзакция уже подтверждена
     * или энергия доставлена конечному получателю.</p>
     *
     * @param portId      идентификатор энергетического порта
     * @param amount      первоначально запрошенное
     *                    количество энергии, FE
     * @param transaction контекст транзакции извлечения
     * @param origin      происхождение операции извлечения
     * @return фактически извлечённое количество энергии,
     * FE, в диапазоне от {@code 0} до {@code amount}
     * @throws NullPointerException     если portId, transaction
     *                                  или origin равен
     *                                  {@code null}
     * @throws IllegalArgumentException если amount отрицателен
     */
    public int extract(
        EnergyPortId portId,
        int amount,
        TransactionContext transaction,
        ExtractionOrigin origin
    )
    {
        Objects.requireNonNull(origin, "origin cannot be null");
        Objects.requireNonNull(portId, "portId cannot be null");
        Objects.requireNonNull(transaction, "transaction cannot be null");
        TransferPreconditions.checkNonNegative(amount);

        if (amount == 0) {
            return 0;
        }

        if (!this.knownPorts.contains(portId) || !this.policy.allowsExtract(portId)) {
            return 0;
        }

        long currentTick = this.currentTickSupplier.getAsLong();

        if (origin == ExtractionOrigin.PULL) {
            this.distribution.recordRequest(portId, amount, currentTick, transaction);
        }

        int availableEnergy = (int) Math.min(this.energy.getStoredEnergy(), Integer.MAX_VALUE);
        int remainingBudget = this.outputBudget.getRemaining(currentTick);
        int adaptiveLimit = this.distribution.getAllowedAdaptiveAmount(
            portId,
            amount,
            remainingBudget,
            availableEnergy,
            currentTick,
            transaction
        );

        int allowed = this.outputBudget.getAllowedAmount(adaptiveLimit, currentTick);
        if (allowed == 0) {
            return 0;
        }

        int extracted = this.energy.extractEnergy(allowed, transaction);
        if (extracted > 0) {
            this.outputBudget.recordTransfer(extracted, currentTick, transaction);
            this.distribution.recordExtraction(portId, extracted, currentTick, transaction);
            new RootCommitJournal(() -> this.tracker.recordTransfer(
                extracted,
                currentTick
            )).updateSnapshots(transaction);
        }

        return extracted;
    }
}
