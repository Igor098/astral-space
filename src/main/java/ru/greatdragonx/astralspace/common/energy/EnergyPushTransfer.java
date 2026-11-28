package ru.greatdragonx.astralspace.common.energy;

import net.neoforged.neoforge.transfer.TransferPreconditions;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandlerUtil;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jspecify.annotations.NonNull;
import ru.greatdragonx.astralspace.common.energy.component.EnergyComponent;
import ru.greatdragonx.astralspace.common.energy.port.EnergyPortId;
import ru.greatdragonx.astralspace.common.energy.port.EnergyPortPolicy;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Координирует активную передачу энергии через
 * выходящие энергетические порты механизма (Push).
 *
 * <p>Использует {@link EnergyHandlerUtil} для согласования
 * транзакционной передачи энергии между внутренним
 * источником и внешним получателем.</p>
 *
 * <p>Извлечение энергии выполняется непосредственно через
 * {@link EnergyOutputController} с указанием происхождения
 * {@link EnergyOutputController.ExtractionOrigin#PUSH}.
 * Активная передача не регистрируется как Pull-запрос
 * внешнего потребителя.</p>
 *
 * <p>Push и Pull используют один выходящий контроллер,
 * общий исходящий бюджет передачи, адаптивное
 * распределение и статистику подтверждённых операций.</p>
 *
 * <p>Для чтения текущего запаса энергии и максимальной
 * ёмкости используется непосредственно
 * {@link EnergyComponent}.</p>
 *
 * <p>Внутренние обработчики источника создаются лениво
 * и кэшируются по идентификаторам энергетических портов.
 * Каждый обработчик привязан к конкретному порту,
 * но использует общие компоненты выходящего контура.</p>
 *
 * <p>Актуальное разрешение на выдачу энергии проверяется
 * перед началом передачи и непосредственно при
 * извлечении энергии внутренним обработчиком.
 * Дополнительные проверки выполняются выходящим
 * контроллером.</p>
 *
 * <p>Кэширование обработчиков не фиксирует результаты
 * проверки доступности портов или их текущие режимы.</p>
 *
 * <p>Компонент не управляет поиском соседних механизмов,
 * получением внешних capability, выбором портов
 * или жизненным циклом BlockEntity.</p>
 *
 * <p>Не открывает собственную корневую транзакцию
 * и не подтверждает предоставленный вызывающим
 * кодом транзакционный контекст самостоятельно.</p>
 *
 * <p>Предназначен для последовательного использования
 * в контексте одного энергетического механизма.
 * Не является потокобезопасным.</p>
 *
 * @see EnergyOutputController
 * @see EnergyComponent
 * @see EnergyPortPolicy
 * @see EnergyHandlerUtil
 */
public final class EnergyPushTransfer {
    private final EnergyComponent energy;
    private final EnergyOutputController outputController;
    private final EnergyPortPolicy policy;
    private final Map<EnergyPortId, EnergyHandler> sourceHandlers = new HashMap<>();

    /**
     * Создаёт компонент активной передачи энергии
     * через выходящие энергетические порты механизма.
     *
     * <p>Все переданные зависимости являются обязательными
     * и сохраняются на протяжении всего жизненного цикла
     * компонента.</p>
     *
     * <p>Энергетическое хранилище должно совпадать
     * с хранилищем, используемым выходящим контроллером.
     * Политика портов также должна быть согласована
     * с политикой выходящего контроллера.</p>
     *
     * <p>Соблюдение этих предусловий является
     * ответственностью вызывающего кода.
     * Конструктор не проверяет идентичность компонентов.</p>
     *
     * <p>Выходящий контроллер должен быть общим
     * для активной отправки энергии (Push)
     * и внешнего извлечения (Pull).</p>
     *
     * <p>При создании компонента внутренний кэш
     * обработчиков пуст. Обработчики создаются
     * по мере выполнения допустимых операций передачи.</p>
     *
     * <p>Конструктор не проверяет политику портов,
     * не считывает игровое время, не изменяет
     * энергетическое состояние и не открывает
     * транзакции.</p>
     *
     * @param energy           энергетическое хранилище
     *                         механизма
     * @param outputController общий контроллер выдачи
     *                         энергии
     * @param policy           актуальная политика
     *                         энергетических портов
     * @throws NullPointerException если одна из переданных
     *                              зависимостей равна
     *                              {@code null}
     */
    public EnergyPushTransfer(
        @NonNull EnergyComponent energy,
        @NonNull EnergyOutputController outputController,
        @NonNull EnergyPortPolicy policy
    )
    {
        this.energy = Objects.requireNonNull(energy, "energy cannot be null");
        this.outputController = Objects.requireNonNull(
            outputController,
            "outputController cannot be null"
        );
        this.policy = Objects.requireNonNull(policy, "policy cannot be null");
    }

    /**
     * Выполняет активную транзакционную передачу энергии
     * через указанный выходящий порт внешнему получателю.
     *
     * <p>Перед началом операции проверяются обязательные
     * аргументы и неотрицательность максимального
     * количества передаваемой энергии.</p>
     *
     * <p>При нулевом запросе или запрете выдачи энергии
     * актуальной политикой метод возвращает {@code 0}
     * без создания внутреннего обработчика
     * и обращения к получателю.</p>
     *
     * <p>Для допустимой операции внутренний обработчик
     * источника извлекается из кэша или создаётся
     * при первом обращении к соответствующему порту.</p>
     *
     * <p>Согласование передачи между источником
     * и получателем выполняется посредством
     * {@link EnergyHandlerUtil}.</p>
     *
     * <p>Фактически передаваемое количество энергии
     * ограничивается следующими факторами:</p>
     *
     * <ul>
     *   <li>максимальным количеством, указанным
     *       вызывающим кодом;</li>
     *   <li>текущим запасом энергии источника;</li>
     *   <li>оставшимся исходящим бюджетом;</li>
     *   <li>адаптивными квотами выходящих портов;</li>
     *   <li>актуальной политикой энергетических портов;</li>
     *   <li>возможностью получателя принять энергию.</li>
     * </ul>
     *
     * <p>Допускается частичная передача энергии.</p>
     *
     * <p>Извлечение выполняется как Push-операция
     * и не регистрирует Pull-спрос.</p>
     *
     * <p>Изменения источника, получателя и транзакционных
     * компонентов выходящего контура выполняются
     * в рамках предоставленного транзакционного контекста
     * и связанных с ним вложенных транзакций.</p>
     *
     * <p>При соблюдении получателем транзакционного
     * контракта откат восстанавливает энергетическое
     * состояние обеих сторон передачи.</p>
     *
     * <p>Статистика выдачи энергии обновляется
     * выходящим контроллером только после подтверждения
     * корневой транзакции.</p>
     *
     * <p>Метод самостоятельно не подтверждает
     * и не закрывает предоставленную транзакцию.
     * Возвращённое количество энергии не означает,
     * что передача уже окончательно подтверждена.</p>
     *
     * @param portId      идентификатор выходящего
     *                    энергетического порта
     * @param target      энергетический обработчик
     *                    внешнего получателя
     * @param maxAmount   максимальное количество
     *                    передаваемой энергии, FE
     * @param transaction контекст транзакции передачи
     * @return фактически переданное количество энергии,
     * FE, в диапазоне от {@code 0}
     * до {@code maxAmount}
     * @throws NullPointerException     если portId, target
     *                                  или transaction
     *                                  равен {@code null}
     * @throws IllegalArgumentException если maxAmount
     *                                  отрицателен
     */
    public int push(
        @NonNull EnergyPortId portId,
        @NonNull EnergyHandler target,
        int maxAmount,
        @NonNull TransactionContext transaction
    )
    {
        Objects.requireNonNull(portId, "portId cannot be null");
        Objects.requireNonNull(target, "target cannot be null");
        Objects.requireNonNull(transaction, "transaction cannot be null");
        TransferPreconditions.checkNonNegative(maxAmount);

        if (maxAmount == 0) {
            return 0;
        }

        if (!this.policy.allowsExtract(portId)) {
            return 0;
        }

        EnergyHandler source = this.sourceHandlers.computeIfAbsent(
            portId,
            this::createSourceHandler
        );

        return EnergyHandlerUtil.move(source, target, maxAmount, transaction);
    }

    /**
     * Создаёт внутренний энергетический обработчик
     * источника, привязанный к конкретному порту.
     *
     * <p>Адаптирует выходящий энергетический контур
     * механизма к интерфейсу {@link EnergyHandler}
     * для использования при активной передаче энергии.</p>
     *
     * <p>Созданный обработчик выполняет следующие
     * операции:</p>
     *
     * <ul>
     *   <li>чтение текущего запаса энергии
     *       непосредственно из энергетического
     *       хранилища;</li>
     *   <li>чтение максимальной ёмкости
     *       энергетического хранилища;</li>
     *   <li>отказ от вставки энергии;</li>
     *   <li>извлечение энергии через выходящий
     *       контроллер с происхождением PUSH.</li>
     * </ul>
     *
     * <p>Перед каждым извлечением обработчик повторно
     * проверяет актуальное разрешение на выдачу
     * энергии через связанный порт.</p>
     *
     * <p>Обработчик не хранит собственный запас энергии,
     * не рассчитывает бюджеты или адаптивные квоты
     * и не регистрирует статистику самостоятельно.</p>
     *
     * <p>Идентификатор порта захватывается анонимным
     * классом при создании обработчика и остаётся
     * неизменным.</p>
     *
     * <p>Создание обработчика не изменяет энергетическое
     * состояние и не выполняет передачу энергии.</p>
     *
     * @param portId идентификатор энергетического порта
     * @return внутренний обработчик источника энергии
     */
    private @NonNull EnergyHandler createSourceHandler(
        @NonNull EnergyPortId portId
    )
    {
        return new EnergyHandler() {
            @Override
            public long getAmountAsLong() {
                return EnergyPushTransfer.this.energy.getStoredEnergy();
            }

            @Override
            public long getCapacityAsLong() {
                return EnergyPushTransfer.this.energy.getCapacity();
            }

            @Override
            public int insert(int amount, @NonNull TransactionContext transaction)
            {
                return 0;
            }

            @Override
            public int extract(int amount, @NonNull TransactionContext transaction)
            {
                if (!EnergyPushTransfer.this.policy.allowsExtract(portId)) {
                    return 0;
                }

                return EnergyPushTransfer.this.outputController.extract(
                    portId,
                    amount,
                    transaction,
                    EnergyOutputController.ExtractionOrigin.PUSH
                );
            }
        };
    }
}
