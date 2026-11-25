package ru.greatdragonx.astralspace.common.energy;

import net.neoforged.neoforge.transfer.TransferPreconditions;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import ru.greatdragonx.astralspace.common.energy.component.EnergyComponent;
import ru.greatdragonx.astralspace.common.energy.port.EnergyPortId;
import ru.greatdragonx.astralspace.common.energy.port.EnergyPortOperations;

import java.util.Objects;

/**
 * Обеспечивает единый доступ к энергетическому состоянию
 * механизма и его контурам приёма и выдачи энергии.
 *
 * <p>Реализует интерфейс {@link EnergyPortOperations},
 * делегируя операции соответствующим компонентам
 * энергетической системы:</p>
 *
 * <ul>
 *   <li>{@link EnergyComponent} — чтение текущего запаса
 *       энергии и максимальной ёмкости хранилища;</li>
 *   <li>{@link EnergyInputController} — внешний приём
 *       энергии;</li>
 *   <li>{@link EnergyOutputController} — внешнее
 *       извлечение энергии по инициативе потребителя.</li>
 * </ul>
 *
 * <p>Входящий и выходящий контроллеры подключаются
 * независимо друг от друга. Допускаются следующие
 * конфигурации:</p>
 *
 * <ul>
 *   <li>только входящий контроллер;</li>
 *   <li>только выходящий контроллер;</li>
 *   <li>оба контроллера;</li>
 *   <li>отсутствие обоих контроллеров.</li>
 * </ul>
 *
 * <p>При отсутствии соответствующего контроллера
 * корректная операция передачи энергии возвращает
 * {@code 0}. Чтение энергетического состояния доступно
 * независимо от наличия контроллеров.</p>
 *
 * <p>Все подключённые контроллеры должны использовать
 * тот же экземпляр энергетического хранилища,
 * что и диспетчер. Соблюдение этого предусловия
 * является ответственностью вызывающего кода.</p>
 *
 * <p>Диспетчер проверяет аргументы операций передачи,
 * но не выполняет самостоятельную проверку политики
 * портов, расчёт бюджетов, распределение энергии
 * или регистрацию статистики.</p>
 *
 * <p>Операции внешнего извлечения делегируются
 * выходящему контроллеру как Pull-запросы.
 * Активная отправка энергии (Push) выполняется
 * отдельно, посредством выходящего контроллера.</p>
 *
 * <p>Диспетчер не создаёт дополнительных энергетических
 * хранилищ, не кэширует энергетическое состояние
 * и не управляет жизненным циклом транзакций.</p>
 *
 * <p>Предназначен для последовательного использования
 * в контексте одного энергетического механизма.
 * Не является потокобезопасным.</p>
 *
 * @see EnergyPortOperations
 * @see EnergyComponent
 * @see EnergyInputController
 * @see EnergyOutputController
 */
public final class EnergyPortDispatcher implements EnergyPortOperations {
    private final EnergyComponent energy;
    private final @Nullable EnergyInputController inputController;
    private final @Nullable EnergyOutputController outputController;

    /**
     * Создаёт диспетчер энергетических операций механизма.
     *
     * <p>Энергетическое хранилище является обязательной
     * зависимостью. Входящий и выходящий контроллеры
     * могут отсутствовать независимо друг от друга.</p>
     *
     * <p>Все подключённые контроллеры должны работать
     * с переданным экземпляром энергетического хранилища.
     * Данное предусловие не проверяется конструктором.</p>
     *
     * <p>При наличии обоих контроллеров они должны
     * использовать независимые бюджеты приёма
     * и выдачи энергии.</p>
     *
     * <p>Состав подключённых компонентов определяется
     * при создании диспетчера и не изменяется
     * в течение его жизненного цикла.</p>
     *
     * <p>Конструктор не выполняет операций передачи
     * энергии, не изменяет состояние хранилища
     * и не обращается к игровому миру.</p>
     *
     * @param energy           общее энергетическое
     *                         хранилище механизма
     * @param inputController  контроллер приёма энергии
     *                         или {@code null}, если
     *                         входящий контур отсутствует
     * @param outputController контроллер выдачи энергии
     *                         или {@code null}, если
     *                         исходящий контур отсутствует
     * @throws NullPointerException если energy равен
     *                              {@code null}
     */
    public EnergyPortDispatcher(
        @NonNull EnergyComponent energy,
        @Nullable EnergyInputController inputController,
        @Nullable EnergyOutputController outputController
    )
    {
        this.energy = Objects.requireNonNull(energy, "energy cannot be null");
        this.inputController = inputController;
        this.outputController = outputController;
    }

    /**
     * Возвращает текущее количество энергии
     * в общем энергетическом хранилище механизма.
     *
     * <p>Значение считывается непосредственно из
     * {@link EnergyComponent} без кэширования
     * и ограничения диапазоном int.</p>
     *
     * <p>Во время незавершённой транзакции может
     * возвращать предварительное состояние,
     * которое впоследствии будет подтверждено
     * или отменено.</p>
     *
     * <p>Наличие входящего или выходящего контроллера
     * не влияет на доступность операции чтения.</p>
     *
     * @return текущее количество энергии, FE
     */
    @Override
    public long getStoredEnergy() {
        return this.energy.getStoredEnergy();
    }

    /**
     * Возвращает максимальную ёмкость общего
     * энергетического хранилища механизма.
     *
     * <p>Значение считывается непосредственно из
     * {@link EnergyComponent} без преобразования
     * в int или дополнительных ограничений.</p>
     *
     * <p>Наличие входящего или выходящего контроллера
     * не влияет на доступность операции чтения.</p>
     *
     * @return максимальная ёмкость хранилища, FE
     */
    @Override
    public long getCapacity() {
        return this.energy.getCapacity();
    }

    /**
     * Выполняет внешний приём энергии через указанный
     * энергетический порт.
     *
     * <p>Перед выполнением операции проверяются
     * обязательные аргументы и неотрицательность
     * запрошенного количества энергии.</p>
     *
     * <p>Проверка аргументов выполняется независимо
     * от наличия входящего контроллера. Ошибки
     * программного контракта не скрываются штатным
     * отказом в приёме энергии.</p>
     *
     * <p>При отсутствии входящего контроллера корректный
     * запрос возвращает {@code 0} без изменения
     * энергетического состояния.</p>
     *
     * <p>При наличии входящего контроллера операция
     * делегируется методу
     * {@link EnergyInputController#insert(EnergyPortId, int, TransactionContext)}
     * с сохранением всех переданных аргументов.</p>
     *
     * <p>Проверка принадлежности порта, актуальной
     * политики, входящего бюджета и свободной
     * ёмкости выполняется подключённым контроллером
     * и его зависимостями.</p>
     *
     * <p>Допускается частичный приём энергии.
     * Диспетчер не открывает, не подтверждает
     * и не закрывает транзакцию самостоятельно.</p>
     *
     * @param portId      идентификатор энергетического порта
     * @param amount      запрошенное количество энергии, FE
     * @param transaction контекст транзакции вставки
     * @return фактически принятое количество энергии,
     * FE, в диапазоне от {@code 0} до {@code amount}
     * @throws NullPointerException     если portId или transaction
     *                                  равен {@code null}
     * @throws IllegalArgumentException если amount отрицателен
     */
    @Override
    public int insert(
        @NonNull EnergyPortId portId,
        int amount,
        @NonNull TransactionContext transaction
    )
    {
        validateTransferArguments(portId, amount, transaction);

        if (this.inputController == null) {
            return 0;
        }

        return this.inputController.insert(portId, amount, transaction);
    }

    /**
     * Выполняет внешнее извлечение энергии через
     * указанный энергетический порт.
     *
     * <p>Перед выполнением операции проверяются
     * обязательные аргументы и неотрицательность
     * запрошенного количества энергии.</p>
     *
     * <p>Проверка аргументов выполняется независимо
     * от наличия выходящего контроллера. Ошибки
     * программного контракта не скрываются штатным
     * отказом в выдаче энергии.</p>
     *
     * <p>При отсутствии выходящего контроллера корректный
     * запрос возвращает {@code 0} без изменения
     * энергетического состояния.</p>
     *
     * <p>При наличии выходящего контроллера операция
     * делегируется методу
     * {@link EnergyOutputController#extract(EnergyPortId, int, TransactionContext)}
     * с сохранением всех переданных аргументов.</p>
     *
     * <p>Операция рассматривается выходящим контроллером
     * как извлечение по инициативе внешнего потребителя
     * (Pull).</p>
     *
     * <p>Проверка принадлежности порта, актуальной
     * политики, исходящего бюджета, адаптивных квот
     * и доступного запаса энергии выполняется
     * подключённым контроллером и его зависимостями.</p>
     *
     * <p>Допускается частичное извлечение энергии.
     * Диспетчер не открывает, не подтверждает
     * и не закрывает транзакцию самостоятельно.</p>
     *
     * @param portId      идентификатор энергетического порта
     * @param amount      запрошенное количество энергии, FE
     * @param transaction контекст транзакции извлечения
     * @return фактически извлечённое количество энергии,
     * FE, в диапазоне от {@code 0} до {@code amount}
     * @throws NullPointerException     если portId или transaction
     *                                  равен {@code null}
     * @throws IllegalArgumentException если amount отрицателен
     */
    @Override
    public int extract(
        @NonNull EnergyPortId portId,
        int amount,
        @NonNull TransactionContext transaction
    )
    {
        validateTransferArguments(portId, amount, transaction);

        if (this.outputController == null) {
            return 0;
        }

        return this.outputController.extract(portId, amount, transaction);
    }

    /**
     * Проверяет общие аргументы операций приёма
     * и извлечения энергии.
     *
     * <p>Обеспечивает единый контракт операций передачи
     * независимо от наличия соответствующего контроллера.</p>
     *
     * <p>Должен вызываться до проверки наличия
     * энергетического контура, чтобы некорректные
     * аргументы не скрывались возвратом {@code 0}.</p>
     *
     * @param portId      идентификатор энергетического порта
     * @param amount      запрошенное количество энергии, FE
     * @param transaction контекст транзакции передачи
     * @throws NullPointerException     если portId или transaction
     *                                  равен {@code null}
     * @throws IllegalArgumentException если amount отрицателен
     */
    private static void validateTransferArguments(
        EnergyPortId portId,
        int amount,
        TransactionContext transaction
    )
    {
        Objects.requireNonNull(portId, "portId cannot be null");
        Objects.requireNonNull(transaction, "transaction cannot be null");
        TransferPreconditions.checkNonNegative(amount);
    }
}
