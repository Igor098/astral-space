package ru.greatdragonx.astralspace.common.energy;

import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jspecify.annotations.NonNull;

/**
 * Транзакционный бюджет передачи энергии
 * в пределах одного игрового тика.
 *
 * <p>Ограничивает суммарное количество энергии,
 * передаваемой за тик, независимо от количества
 * отдельных операций передачи.</p>
 *
 * <p>Может использоваться как для внешнего приёма,
 * так и для внешней выдачи энергии. Независимые
 * направления передачи должны использовать
 * отдельные экземпляры бюджета.</p>
 *
 * <p>При переходе к новому игровому тику
 * становится доступен полный лимит передачи.
 * Обновление внутреннего состояния выполняется
 * лениво при регистрации фактической передачи.</p>
 *
 * <p>Учёт переданной энергии является транзакционным.
 * При откате транзакции восстанавливаются
 * предыдущий учтённый тик и количество
 * переданной энергии.</p>
 *
 * <p>Не хранит энергию, не управляет режимами
 * портов и не распределяет лимит между ними.</p>
 */
public final class EnergyTransferBudget {
    private final int maxPerTick;

    private long lastTransferTick = -1L;
    private int transferredThisTick = 0;

    /**
     * Снимок состояния бюджета передачи энергии,
     * используемый для транзакционного восстановления.
     *
     * @param tick        последний учтённый игровой тик
     * @param transferred количество энергии,
     *                    переданной за этот тик
     */
    private record BudgetSnapshot(long tick, int transferred) {}

    private final SnapshotJournal<BudgetSnapshot> journal = new SnapshotJournal<>() {
        /**
         * {@inheritDoc}
         *
         * <p>Сохраняет последний учтённый игровой тик
         * и количество переданной за него энергии.</p>
         */
        @Override
        protected @NonNull BudgetSnapshot createSnapshot() {
            return new BudgetSnapshot(
                EnergyTransferBudget.this.lastTransferTick,
                EnergyTransferBudget.this.transferredThisTick
            );
        }

        /**
         * {@inheritDoc}
         *
         * <p>Восстанавливает как количество переданной
         * энергии, так и последний учтённый игровой тик.</p>
         */
        @Override
        protected void revertToSnapshot(@NonNull BudgetSnapshot snapshot) {
            EnergyTransferBudget.this.lastTransferTick = snapshot.tick();
            EnergyTransferBudget.this.transferredThisTick = snapshot.transferred();
        }
    };

    /**
     * Создаёт бюджет передачи энергии
     * с заданным максимальным лимитом на тик.
     *
     * <p>Изначально бюджет полностью доступен.</p>
     *
     * @param maxPerTick максимальное суммарное
     *                   количество энергии,
     *                   передаваемой за один тик
     * @throws IllegalArgumentException если лимит
     *                                  не является
     *                                  положительным
     */
    public EnergyTransferBudget(int maxPerTick) {
        if (maxPerTick <= 0) {
            throw new IllegalArgumentException("Maximum transfer per tick must be positive");
        }
        this.maxPerTick = maxPerTick;
    }

    /**
     * Регистрирует фактически переданное количество
     * энергии в рамках указанного игрового тика.
     *
     * <p>Если указанный тик отличается от последнего
     * учтённого тика, накопленное количество
     * переданной энергии сбрасывается перед
     * регистрацией новой передачи.</p>
     *
     * <p>Нулевые и отрицательные значения
     * игнорируются без изменения состояния.</p>
     *
     * <p>Перед изменением состояния создаётся
     * транзакционный снимок, позволяющий восстановить
     * бюджет при откате транзакции.</p>
     *
     * <p>Метод не выполняет фактическую передачу
     * энергии и должен вызываться для учёта
     * уже выполненной операции в рамках той же
     * транзакции.</p>
     *
     * @param transferred фактически переданное
     *                    количество энергии
     * @param currentTick текущий игровой тик
     * @param transaction контекст транзакции
     * @throws IllegalArgumentException если количество
     *                                  переданной энергии
     *                                  превышает остаток
     *                                  бюджета
     */
    public void recordTransfer(int transferred, long currentTick, TransactionContext transaction)
    {
        if (transferred <= 0) {
            return;
        }

        int remaining = this.getRemaining(currentTick);
        if (transferred > remaining) {
            throw new IllegalArgumentException("Transfer exceeds remaining energy budget");
        }

        this.journal.updateSnapshots(transaction);

        if (this.lastTransferTick != currentTick) {
            this.lastTransferTick = currentTick;
            this.transferredThisTick = 0;
        }

        this.transferredThisTick += transferred;
    }

    /**
     * Возвращает оставшийся бюджет передачи энергии
     * для указанного игрового тика.
     *
     * <p>Если указанный тик совпадает с последним
     * учтённым тиком, из максимального лимита
     * вычитается уже переданное количество энергии.</p>
     *
     * <p>Для другого игрового тика возвращается
     * полный лимит передачи.</p>
     *
     * <p>Метод не изменяет внутреннее состояние
     * бюджета.</p>
     *
     * @param currentTick текущий игровой тик
     * @return оставшееся допустимое количество
     * передаваемой энергии
     */
    public int getRemaining(long currentTick) {
        if (this.lastTransferTick == currentTick) {
            return this.maxPerTick - this.transferredThisTick;
        }

        return maxPerTick;
    }

    /**
     * Определяет допустимое количество энергии
     * для запрашиваемой операции передачи
     * в пределах оставшегося бюджета.
     *
     * <p>Возвращает минимальное значение между
     * запрошенным количеством энергии и остатком
     * бюджета текущего тика.</p>
     *
     * <p>Для нулевого или отрицательного запроса
     * возвращает {@code 0}.</p>
     *
     * <p>Метод не резервирует бюджет, не регистрирует
     * передачу и не изменяет внутреннее состояние.</p>
     *
     * @param requested   запрашиваемое количество энергии
     * @param currentTick текущий игровой тик
     * @return допустимое количество энергии
     * для операции передачи
     */
    public int getAllowedAmount(int requested, long currentTick)
    {
        if (requested <= 0) {
            return 0;
        }

        int remaining = this.getRemaining(currentTick);

        return Math.min(remaining, requested);
    }
}
