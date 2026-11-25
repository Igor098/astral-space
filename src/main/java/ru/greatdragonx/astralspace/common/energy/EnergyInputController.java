package ru.greatdragonx.astralspace.common.energy;

import net.neoforged.neoforge.transfer.TransferPreconditions;
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
 * Координирует приём энергии через энергетические порты механизма.
 *
 * <p>Обеспечивает внешний приём энергии с учётом актуальной
 * политики портов, общего входящего бюджета передачи
 * за игровой тик и свободной ёмкости энергетического
 * хранилища.</p>
 *
 * <p>Контроллер использует следующие компоненты:</p>
 *
 * <ul>
 *   <li>{@link EnergyComponent} — хранение и транзакционная
 *       вставка энергии;</li>
 *   <li>{@link EnergyPortPolicy} — проверка актуальных
 *       разрешений и физической доступности портов;</li>
 *   <li>{@link EnergyTransferBudget} — ограничение общего
 *       объёма принимаемой энергии за игровой тик.</li>
 * </ul>
 *
 * <p>Контроллер обслуживает неизменяемое множество известных
 * энергетических портов. Возможность приёма энергии через
 * конкретный порт определяется актуальной политикой
 * непосредственно при выполнении операции.</p>
 *
 * <p>Все входящие порты используют один общий бюджет
 * передачи энергии. Распределение входящего бюджета
 * между отдельными портами не выполняется.</p>
 *
 * <p>Допускается частичный приём энергии в пределах
 * оставшегося бюджета и свободной ёмкости хранилища.
 * Бюджет расходуется только на фактически принятое
 * количество энергии.</p>
 *
 * <p>Изменения энергетического хранилища и входящего
 * бюджета выполняются в предоставленном вызывающим
 * кодом транзакционном контексте и поддерживают откат.</p>
 *
 * <p>Контроллер не управляет выдачей энергии, внутренним
 * потреблением энергии для работы механизма,
 * производственными процессами, статистикой передачи
 * и жизненным циклом BlockEntity.</p>
 *
 * <p>Предназначен для последовательного использования
 * в контексте одного энергетического механизма.
 * Не является потокобезопасным.</p>
 *
 * @see EnergyComponent
 * @see EnergyPortPolicy
 * @see EnergyTransferBudget
 * @see EnergyOutputController
 */
public final class EnergyInputController {
    private final EnergyComponent energy;

    private final Set<EnergyPortId> knownPorts;

    private final EnergyPortPolicy policy;
    private final EnergyTransferBudget inputBudget;
    private final LongSupplier currentTickSupplier;

    /**
     * Создаёт контроллер входящего энергетического контура.
     *
     * <p>Все необходимые компоненты предоставляются вызывающим
     * кодом и используются на протяжении всего времени
     * существования контроллера.</p>
     *
     * <p>Из переданного списка портов создаётся неизменяемое
     * множество идентификаторов. Последующие изменения
     * исходного списка не влияют на набор известных портов
     * контроллера.</p>
     *
     * <p>Список может быть пустым, но не должен содержать
     * null-элементы или повторяющиеся идентификаторы.</p>
     *
     * <p>Порядок портов не имеет значения, поскольку
     * контроллер не выполняет распределение входящего
     * бюджета между портами.</p>
     *
     * <p>Конструктор не проверяет текущую доступность портов,
     * не обращается к поставщику игрового времени,
     * не изменяет энергетическое хранилище
     * и не расходует входящий бюджет.</p>
     *
     * @param energy              энергетическое хранилище
     *                            механизма
     * @param ports               список всех известных
     *                            энергетических портов
     * @param policy              актуальная политика доступа
     *                            к энергетическим портам
     * @param inputBudget         общий бюджет приёма энергии
     *                            через входящие порты
     * @param currentTickSupplier поставщик текущего
     *                            игрового времени
     * @throws NullPointerException     если одна из зависимостей
     *                                  или элемент списка портов
     *                                  равен {@code null}
     * @throws IllegalArgumentException если список содержит
     *                                  повторяющиеся
     *                                  идентификаторы портов
     */
    public EnergyInputController(
        @NonNull EnergyComponent energy,
        @NonNull List<EnergyPortId> ports,
        @NonNull EnergyPortPolicy policy,
        @NonNull EnergyTransferBudget inputBudget,
        @NonNull LongSupplier currentTickSupplier
    )
    {
        this.energy = Objects.requireNonNull(energy, "energy cannot be null");

        this.knownPorts = Set.copyOf(Objects.requireNonNull(ports, "ports cannot be null"));
        if (this.knownPorts.size() != ports.size()) {
            throw new IllegalArgumentException("ports cannot contain duplicates");
        }

        this.policy = Objects.requireNonNull(policy, "policy cannot be null");
        this.inputBudget = Objects.requireNonNull(inputBudget, "inputBudget cannot be null");
        this.currentTickSupplier = Objects.requireNonNull(
            currentTickSupplier,
            "currentTickSupplier cannot be null"
        );
    }

    /**
     * Выполняет транзакционную вставку энергии через
     * указанный энергетический порт.
     *
     * <p>Перед выполнением операции проверяются обязательные
     * аргументы и неотрицательность запрошенного количества
     * энергии. Ошибки аргументов не скрываются штатным
     * отказом в приёме энергии.</p>
     *
     * <p>При нулевом запросе, неизвестном идентификаторе
     * порта или запрете приёма актуальной политикой
     * метод возвращает {@code 0} без обращения
     * к поставщику игрового времени и изменения
     * состояния энергетического контура.</p>
     *
     * <p>Для допустимой операции текущее игровое время
     * считывается один раз и используется при расчёте
     * доступного бюджета и регистрации его расхода.</p>
     *
     * <p>Вставка выполняется в следующем порядке:</p>
     *
     * <ol>
     *   <li>Определение разрешённого количества энергии
     *       с учётом оставшегося входящего бюджета.</li>
     *   <li>Транзакционная вставка разрешённого количества
     *       энергии в энергетическое хранилище.</li>
     *   <li>Регистрация фактически принятого количества
     *       энергии во входящем бюджете.</li>
     * </ol>
     *
     * <p>Если входящий бюджет исчерпан, операция завершается
     * без обращения к энергетическому хранилищу.</p>
     *
     * <p>Частичная вставка допускается. Фактически принятое
     * количество энергии может быть меньше разрешённого
     * бюджетом при недостаточной свободной ёмкости
     * хранилища.</p>
     *
     * <p>Операция поддерживает следующий инвариант:</p>
     *
     * <pre>{@code
     * 0 <= inserted <= allowed <= amount
     * }</pre>
     *
     * <p>Входящий бюджет расходуется только при положительном
     * количестве фактически принятой энергии.
     * Неиспользованный остаток бюджета сохраняется
     * для последующих операций текущего игрового тика.</p>
     *
     * <p>Все транзакционные изменения выполняются
     * в предоставленном контексте. Контроллер не открывает,
     * не подтверждает и не закрывает транзакцию
     * самостоятельно.</p>
     *
     * <p>При откате транзакции восстанавливаются затронутые
     * состояния энергетического хранилища и входящего
     * бюджета.</p>
     *
     * <p>Уведомление владельца об изменении запаса энергии
     * выполняется энергетическим компонентом после
     * подтверждения корневой транзакции, если итоговый
     * запас отличается от первоначального.</p>
     *
     * <p>Возвращённое количество энергии представляет
     * результат вставки в рамках текущей транзакции
     * и не означает, что транзакция уже подтверждена.</p>
     *
     * @param portId      идентификатор энергетического порта
     * @param amount      первоначально запрошенное количество
     *                    энергии для вставки, FE
     * @param transaction контекст транзакции вставки
     * @return фактически принятое количество энергии,
     * FE, в диапазоне от {@code 0} до {@code amount}
     * @throws NullPointerException     если portId или transaction
     *                                  равен {@code null}
     * @throws IllegalArgumentException если amount отрицателен
     */
    public int insert(EnergyPortId portId, int amount, TransactionContext transaction) {
        Objects.requireNonNull(portId, "portId cannot be null");
        Objects.requireNonNull(transaction, "transaction cannot be null");
        TransferPreconditions.checkNonNegative(amount);

        if (amount == 0) {
            return 0;
        }

        if (!this.knownPorts.contains(portId) || !this.policy.allowsInsert(portId)) {
            return 0;
        }

        long currentTick = this.currentTickSupplier.getAsLong();

        int allowed = this.inputBudget.getAllowedAmount(amount, currentTick);
        if (allowed == 0) {
            return 0;
        }

        int inserted = this.energy.insertEnergy(allowed, transaction);
        if (inserted > 0) {
            this.inputBudget.recordTransfer(inserted, currentTick, transaction);
        }

        return inserted;
    }
}
