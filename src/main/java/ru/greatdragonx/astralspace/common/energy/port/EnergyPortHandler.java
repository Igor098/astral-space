package ru.greatdragonx.astralspace.common.energy.port;

import net.neoforged.neoforge.transfer.TransferPreconditions;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jspecify.annotations.NonNull;

import java.util.Objects;

/**
 * Адаптер энергетического порта механизма
 * к интерфейсу передачи энергии NeoForge.
 *
 * <p>Связывает логический идентификатор порта
 * с энергетическими операциями механизма
 * и актуальной политикой доступа.</p>
 *
 * <p>Перед каждой операцией приёма или извлечения
 * энергии проверяет разрешение соответствующей
 * операции через {@link EnergyPortPolicy}.</p>
 *
 * <p>Не кэширует текущий режим и доступность порта.
 * Изменения политики учитываются при последующих
 * операциях передачи энергии.</p>
 *
 * <p>Чтение энергетического состояния не зависит
 * от режима и доступности порта.</p>
 *
 * <p>Не содержит собственного энергетического
 * хранилища, не рассчитывает бюджеты передачи
 * и не распределяет энергию между портами.
 * Фактические энергетические операции делегируются
 * {@link EnergyPortOperations}.</p>
 *
 * @see EnergyHandler
 * @see EnergyPortPolicy
 * @see EnergyPortOperations
 */
public final class EnergyPortHandler implements EnergyHandler {
    private final EnergyPortId portId;
    private final EnergyPortPolicy policy;
    private final EnergyPortOperations operations;

    /**
     * Создаёт обработчик логического
     * энергетического порта механизма.
     *
     * <p>Идентификатор порта и зависимости
     * определяются при создании обработчика
     * и не изменяются в течение его жизненного цикла.</p>
     *
     * <p>Конструктор не проверяет текущий режим,
     * физическую доступность или принадлежность
     * порта механизму.</p>
     *
     * @param portId     идентификатор обслуживаемого порта
     * @param policy     политика доступа к энергетическому порту
     * @param operations энергетические операции механизма
     * @throws NullPointerException если любой аргумент
     *                              равен {@code null}
     */
    public EnergyPortHandler(
        EnergyPortId portId,
        EnergyPortPolicy policy,
        EnergyPortOperations operations
    )
    {
        this.portId = Objects.requireNonNull(portId, "portId cannot be null");
        this.policy = Objects.requireNonNull(policy, "policy cannot be null");
        this.operations = Objects.requireNonNull(operations, "operations cannot be null");
    }

    /**
     * {@inheritDoc}
     *
     * <p>Возвращает текущий запас энергии общего
     * хранилища механизма независимо от режима
     * и доступности обслуживаемого порта.</p>
     *
     * <p>Чтение делегируется методу
     * {@link EnergyPortOperations#getStoredEnergy()}.</p>
     */
    @Override
    public long getAmountAsLong() {
        return this.operations.getStoredEnergy();
    }

    /**
     * {@inheritDoc}
     *
     * <p>Возвращает максимальную ёмкость общего
     * энергетического хранилища механизма,
     * а не индивидуальную ёмкость порта.</p>
     *
     * <p>Чтение делегируется методу
     * {@link EnergyPortOperations#getCapacity()}
     * независимо от режима и доступности порта.</p>
     */
    @Override
    public long getCapacityAsLong() {
        return this.operations.getCapacity();
    }

    /**
     * {@inheritDoc}
     *
     * <p>Отрицательное количество энергии отклоняется
     * до проверки политики порта. Нулевой запрос
     * возвращает {@code 0} без обращения к политике
     * и энергетическим операциям механизма.</p>
     *
     * <p>Приём энергии разрешается только при
     * положительном результате проверки
     * {@link EnergyPortPolicy#allowsInsert(EnergyPortId)}.</p>
     *
     * <p>Если политика запрещает приём энергии,
     * возвращает {@code 0} без делегирования операции.</p>
     *
     * <p>Разрешённая операция делегируется методу
     * {@link EnergyPortOperations#insert(EnergyPortId, int, TransactionContext)}
     * с идентификатором обслуживаемого порта,
     * исходным количеством энергии и переданным
     * транзакционным контекстом.</p>
     */
    @Override
    public int insert(int amount, @NonNull TransactionContext transaction)
    {
        TransferPreconditions.checkNonNegative(amount);
        if (amount == 0) {
            return 0;
        }

        if (!this.policy.allowsInsert(this.portId)) {
            return 0;
        }

        return this.operations.insert(this.portId, amount, transaction);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Отрицательное количество энергии отклоняется
     * до проверки политики порта. Нулевой запрос
     * возвращает {@code 0} без обращения к политике
     * и энергетическим операциям механизма.</p>
     *
     * <p>Извлечение энергии разрешается только при
     * положительном результате проверки
     * {@link EnergyPortPolicy#allowsExtract(EnergyPortId)}.</p>
     *
     * <p>Если политика запрещает извлечение энергии,
     * возвращает {@code 0} без делегирования операции.</p>
     *
     * <p>Разрешённая операция делегируется методу
     * {@link EnergyPortOperations#extract(EnergyPortId, int, TransactionContext)}
     * с идентификатором обслуживаемого порта,
     * исходным количеством энергии и переданным
     * транзакционным контекстом.</p>
     */
    @Override
    public int extract(int amount, @NonNull TransactionContext transaction)
    {
        TransferPreconditions.checkNonNegative(amount);
        if (amount == 0) {
            return 0;
        }

        if (!this.policy.allowsExtract(this.portId)) {
            return 0;
        }

        return this.operations.extract(this.portId, amount, transaction);
    }
}
