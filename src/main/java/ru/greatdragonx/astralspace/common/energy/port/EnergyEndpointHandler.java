package ru.greatdragonx.astralspace.common.energy.port;

import net.neoforged.neoforge.transfer.TransferPreconditions;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jspecify.annotations.NonNull;

import java.util.Objects;
import java.util.function.BooleanSupplier;

/**
 * Декоратор энергетического обработчика,
 * контролирующий актуальность физической
 * точки подключения механизма.
 *
 * <p>Перед обращением к делегату проверяет
 * действительность физической привязки
 * через переданный {@link BooleanSupplier}.</p>
 *
 * <p>При недействительной привязке возвращает
 * нулевые значения запаса, ёмкости и количества
 * переданной энергии без обращения к делегату.</p>
 *
 * <p>Не кэширует результат проверки актуальности.
 * Управление жизненным циклом физической привязки
 * является ответственностью владельца обработчика.</p>
 *
 * <p>Не содержит собственного энергетического
 * хранилища, политики портов, бюджета передачи
 * и логики управления транзакциями.</p>
 *
 * @see EnergyHandler
 * @see EnergyPortHandler
 */
public final class EnergyEndpointHandler implements EnergyHandler {
    private final EnergyHandler delegate;
    private final BooleanSupplier validity;

    /**
     * Создаёт декоратор энергетического обработчика
     * с динамической проверкой физической привязки.
     *
     * <p>Конструктор не обращается к делегату
     * и не выполняет проверку актуальности.</p>
     *
     * @param delegate обработчик, которому делегируются
     *                 энергетические операции
     * @param validity источник актуального состояния
     *                 физической привязки
     * @throws NullPointerException если любой аргумент
     *                              равен {@code null}
     */
    public EnergyEndpointHandler(@NonNull EnergyHandler delegate, @NonNull BooleanSupplier validity)
    {
        this.delegate = Objects.requireNonNull(delegate, "delegate cannot be null");
        this.validity = Objects.requireNonNull(validity, "validity cannot be null");
    }

    /**
     * {@inheritDoc}
     *
     * <p>При действительной физической привязке
     * возвращает текущий запас энергии делегата.</p>
     *
     * <p>При недействительной привязке возвращает
     * {@code 0L} без обращения к делегату.</p>
     */
    @Override
    public long getAmountAsLong() {
        if (!this.validity.getAsBoolean()) {
            return 0L;
        }

        return this.delegate.getAmountAsLong();
    }

    /**
     * {@inheritDoc}
     *
     * <p>При действительной физической привязке
     * возвращает ёмкость энергетического
     * хранилища делегата.</p>
     *
     * <p>При недействительной привязке возвращает
     * {@code 0L} без обращения к делегату.</p>
     */
    @Override
    public long getCapacityAsLong() {
        if (!this.validity.getAsBoolean()) {
            return 0L;
        }

        return this.delegate.getCapacityAsLong();
    }

    /**
     * {@inheritDoc}
     *
     * <p>Проверяет корректность аргументов независимо
     * от действительности физической привязки.</p>
     *
     * <p>Нулевой запрос возвращает {@code 0}
     * без проверки актуальности и обращения
     * к делегату.</p>
     *
     * <p>При недействительной физической привязке
     * возвращает {@code 0} без делегирования.</p>
     *
     * <p>При действительной привязке передаёт
     * делегату исходное количество энергии
     * и транзакционный контекст, возвращая
     * полученный результат без изменений.</p>
     *
     * @throws NullPointerException     если транзакционный
     *                                  контекст равен {@code null}
     * @throws IllegalArgumentException если количество
     *                                  энергии отрицательное
     */
    @Override
    public int insert(int amount, @NonNull TransactionContext transaction) {
        Objects.requireNonNull(transaction, "transaction cannot be null");
        TransferPreconditions.checkNonNegative(amount);

        if (amount == 0) {
            return 0;
        }

        if (!this.validity.getAsBoolean()) {
            return 0;
        }

        return this.delegate.insert(amount, transaction);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Проверяет корректность аргументов независимо
     * от действительности физической привязки.</p>
     *
     * <p>Нулевой запрос возвращает {@code 0}
     * без проверки актуальности и обращения
     * к делегату.</p>
     *
     * <p>При недействительной физической привязке
     * возвращает {@code 0} без делегирования.</p>
     *
     * <p>При действительной привязке передаёт
     * делегату исходное количество энергии
     * и транзакционный контекст, возвращая
     * полученный результат без изменений.</p>
     *
     * @throws NullPointerException     если транзакционный
     *                                  контекст равен {@code null}
     * @throws IllegalArgumentException если количество
     *                                  энергии отрицательное
     */
    @Override
    public int extract(int amount, @NonNull TransactionContext transaction) {
        Objects.requireNonNull(transaction, "transaction cannot be null");
        TransferPreconditions.checkNonNegative(amount);

        if (amount == 0) {
            return 0;
        }

        if (!this.validity.getAsBoolean()) {
            return 0;
        }

        return this.delegate.extract(amount, transaction);
    }
}
