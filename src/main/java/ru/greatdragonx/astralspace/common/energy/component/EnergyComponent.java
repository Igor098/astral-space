package ru.greatdragonx.astralspace.common.energy.component;

import net.neoforged.neoforge.transfer.TransferPreconditions;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

import java.util.Objects;

/**
 * Управляет внутренним энергетическим хранилищем механизма.
 *
 * <p>Компонент отвечает за хранение энергии, добавление произведённой
 * энергии, транзакционное потребление энергии для работы механизма
 * и восстановление сохранённого состояния.</p>
 *
 * <p>Для количества хранимой энергии и ёмкости используется тип
 * {@code long}, позволяющий поддерживать энергетические накопители
 * с объёмом более {@link Integer#MAX_VALUE} FE.</p>
 *
 * <p>Компонент поддерживает следующий инвариант:</p>
 * <pre>
 * 0 <= storedEnergy <= capacity
 * </pre>
 *
 * <p>Компонент не управляет энергетическими портами, направлениями
 * передачи, лимитами ввода и вывода или распределением энергии
 * между потребителями. Эти обязанности выполняют отдельные
 * компоненты энергетической системы.</p>
 *
 * <p><strong>Важно:</strong> нетранзакционные операции не должны
 * изменять хранилище во время незавершённой транзакции,
 * затрагивающей то же энергетическое состояние.</p>
 *
 * <p>Компонент не предназначен для одновременного использования
 * из нескольких потоков.</p>
 */
public final class EnergyComponent {

    private final long capacity;
    private final Runnable onEnergyChanged;

    /**
     * Журнал транзакционных изменений энергетического хранилища.
     *
     * <p>Сохраняет исходное количество энергии перед изменением,
     * восстанавливает его при откате транзакции и уведомляет
     * владельца после окончательного подтверждения изменений.</p>
     */
    private final SnapshotJournal<Long> energyJournal = new SnapshotJournal<>() {
        /**
         * Создаёт снимок текущего энергетического состояния
         * перед транзакционным изменением.
         *
         * @return количество энергии до изменения, в FE
         */
        @Override
        protected Long createSnapshot() {
            return EnergyComponent.this.storedEnergy;
        }

        /**
         * Восстанавливает количество энергии при откате
         * транзакции.
         *
         * <p>Восстановление не вызывает уведомление
         * {@code onEnergyChanged}.</p>
         *
         * @param snapshot ранее сохранённое количество
         *                 энергии, в FE
         */
        @Override
        protected void revertToSnapshot(Long snapshot) {
            EnergyComponent.this.storedEnergy = snapshot;
        }

        /**
         * Обрабатывает окончательное подтверждение
         * корневой транзакции.
         *
         * <p>Уведомляет владельца компонента только
         * в том случае, если итоговое количество энергии
         * отличается от первоначального.</p>
         *
         * @param originalState количество энергии до
         *                      транзакционных изменений, в FE
         */
        @Override
        protected void onRootCommit(Long originalState) {
            if (EnergyComponent.this.storedEnergy != originalState) {
                EnergyComponent.this.onEnergyChanged.run();
            }
        }
    };

    private long storedEnergy;

    /**
     * Создаёт энергетический компонент с указанной ёмкостью.
     *
     * <p>Изначально хранилище пустое. Переданный callback
     * сохраняется для уведомления владельца об изменениях
     * энергетического состояния.</p>
     *
     * <p>При создании компонента callback не вызывается.</p>
     *
     * @param capacity        максимальная ёмкость хранилища в FE;
     *                        должна быть больше нуля
     * @param onEnergyChanged callback, вызываемый при изменении
     *                        запаса энергии; не может быть {@code null}
     * @throws IllegalArgumentException если {@code capacity <= 0}
     * @throws NullPointerException     если {@code onEnergyChanged == null}
     */
    public EnergyComponent(
        long capacity,
        Runnable onEnergyChanged
    )
    {
        if (capacity <= 0) {
            throw new IllegalArgumentException("Capacity must be positive");
        }
        Objects.requireNonNull(
            onEnergyChanged,
            "onEnergyChanged cannot be null"
        );
        this.capacity = capacity;
        this.onEnergyChanged = onEnergyChanged;
    }

    /**
     * Возвращает текущее количество энергии в хранилище.
     *
     * <p>Во время незавершённой транзакции может возвращать
     * предварительное состояние, которое впоследствии
     * будет подтверждено или отменено.</p>
     *
     * @return текущее количество энергии в FE
     */
    public long getStoredEnergy() {
        return this.storedEnergy;
    }

    /**
     * Возвращает максимальную ёмкость энергетического хранилища.
     *
     * <p>Ёмкость задаётся при создании компонента
     * и не изменяется в течение его жизненного цикла.</p>
     *
     * @return максимальная ёмкость хранилища в FE
     */
    public long getCapacity() {
        return this.capacity;
    }

    /**
     * Сохраняет энергию, произведённую механизмом.
     *
     * <p>Энергия добавляется с учётом свободного пространства
     * в хранилище. Если свободного места недостаточно,
     * сохраняется только допустимое количество энергии.</p>
     *
     * <p>Операция выполняется без участия транзакционной системы.
     * При фактическом изменении запаса энергии callback
     * {@code onEnergyChanged} вызывается немедленно и ровно
     * один раз.</p>
     *
     * <p>Метод не проверяет источник энергии и не управляет
     * процессом генерации. Ответственность за производство
     * энергии лежит на вызывающем механизме.</p>
     *
     * <p><strong>Ограничение:</strong> метод нельзя вызывать
     * во время незавершённой транзакции, изменяющей это же
     * хранилище.</p>
     *
     * @param amount количество произведённой энергии в FE;
     *               должно быть неотрицательным
     * @return фактически сохранённое количество энергии в FE;
     * {@code 0}, если количество равно нулю
     * или хранилище заполнено
     * @throws IllegalArgumentException если {@code amount < 0}
     */
    public int storeProducedEnergy(int amount) {
        TransferPreconditions.checkNonNegative(amount);
        if (amount == 0) {
            return 0;
        }

        long freeSpace = this.capacity - this.storedEnergy;
        if (freeSpace <= 0) {
            return 0;
        }

        int accepted = (int) Math.min(
            amount,
            freeSpace
        );
        this.storedEnergy += accepted;
        this.onEnergyChanged.run();

        return accepted;
    }

    /**
     * Транзакционно добавляет энергию во внутреннее хранилище.
     *
     * <p>Принимает максимально возможное количество энергии
     * в пределах свободной ёмкости. Частичное принятие
     * допускается.</p>
     *
     * <p>Изменения регистрируются в переданном транзакционном
     * контексте и откатываются при отмене транзакции.
     * Метод самостоятельно не подтверждает транзакцию.</p>
     *
     * <p>Ограничения внешних энергетических портов,
     * включая их режимы и бюджеты передачи,
     * на этом уровне пока не применяются.</p>
     *
     * @param amount      запрошенное количество энергии в FE;
     *                    должно быть неотрицательным
     * @param transaction транзакционный контекст операции
     * @return фактически принятое количество энергии в FE
     * @throws IllegalArgumentException если amount отрицателен
     */
    public int insertEnergy(
        int amount,
        TransactionContext transaction
    )
    {
        TransferPreconditions.checkNonNegative(amount);
        if (amount == 0) {
            return 0;
        }

        long freeSpace = this.capacity - this.storedEnergy;
        if (freeSpace <= 0) {
            return 0;
        }

        int accepted = (int) Math.min(
            amount,
            freeSpace
        );

        this.energyJournal.updateSnapshots(transaction);
        this.storedEnergy += accepted;

        return accepted;
    }

    /**
     * Транзакционно извлекает энергию из внутреннего хранилища.
     *
     * <p>Извлекает максимально возможное количество энергии
     * в пределах текущего запаса. Частичное извлечение
     * допускается.</p>
     *
     * <p>Изменения регистрируются в переданном транзакционном
     * контексте и автоматически откатываются при отмене
     * транзакции. Метод самостоятельно не подтверждает
     * транзакцию.</p>
     *
     * <p>После окончательного подтверждения корневой транзакции
     * владелец уведомляется об изменении запаса энергии,
     * если итоговое состояние отличается от исходного.</p>
     *
     * <p>Метод не применяет ограничения энергетических портов,
     * включая режимы сторон, бюджеты FE/t и распределение
     * энергии. Эти проверки должны выполняться на уровне
     * внешней передачи.</p>
     *
     * @param amount      запрошенное количество энергии в FE;
     *                    должно быть неотрицательным
     * @param transaction транзакционный контекст операции
     * @return фактически извлечённое количество энергии в FE
     * @throws IllegalArgumentException если {@code amount < 0}
     */
    public int extractEnergy(
        int amount,
        TransactionContext transaction
    )
    {
        TransferPreconditions.checkNonNegative(amount);
        if (amount == 0) {
            return 0;
        }

        if (this.storedEnergy <= 0) {
            return 0;
        }

        int extracted = (int) Math.min(
            amount,
            this.storedEnergy
        );

        this.energyJournal.updateSnapshots(transaction);
        this.storedEnergy -= extracted;

        return extracted;
    }

    /**
     * Пытается списать энергию для внутренней работы механизма.
     *
     * <p>Операция выполняется по принципу «всё или ничего».
     * Если энергии недостаточно, состояние хранилища
     * не изменяется.</p>
     *
     * <p>Списание выполняется в переданном транзакционном
     * контексте. При отмене транзакции исходное количество
     * энергии восстанавливается автоматически.</p>
     *
     * <p>Метод не подтверждает транзакцию самостоятельно.
     * Ответственность за её подтверждение или отмену
     * лежит на вызывающем коде.</p>
     *
     * <p>При успешном подтверждении корневой транзакции
     * callback {@code onEnergyChanged} вызывается только
     * в том случае, если итоговый запас энергии отличается
     * от исходного.</p>
     *
     * <p>Внутреннее потребление энергии не зависит от лимитов
     * внешней передачи через энергетические порты.</p>
     *
     * @param amount      количество энергии, необходимое
     *                    для выполнения операции, в FE;
     *                    должно быть неотрицательным
     * @param transaction транзакционный контекст, в котором
     *                    выполняется списание энергии
     * @return {@code true}, если необходимое количество энергии
     * доступно и списано в рамках транзакции,
     * либо {@code amount == 0};
     * {@code false}, если энергии недостаточно
     * @throws IllegalArgumentException если {@code amount < 0}
     */
    public boolean tryConsumeForWork(
        int amount,
        TransactionContext transaction
    )
    {
        TransferPreconditions.checkNonNegative(amount);

        if (amount == 0) {
            return true;
        }

        if (amount > this.storedEnergy) {
            return false;
        }

        this.energyJournal.updateSnapshots(transaction);
        this.storedEnergy -= amount;

        return true;
    }

    /**
     * Восстанавливает количество энергии из сохранённого состояния.
     *
     * <p>Переданное значение ограничивается диапазоном
     * от {@code 0} до {@code capacity} включительно.</p>
     *
     * <p>Метод предназначен исключительно для восстановления
     * состояния механизма при загрузке сохранённых данных.
     * Он не используется для генерации, потребления
     * или передачи энергии.</p>
     *
     * <p>Операция не использует транзакционный журнал
     * и не вызывает callback {@code onEnergyChanged}.</p>
     *
     * <p><strong>Важно:</strong> метод должен вызываться
     * до начала обычной работы механизма, при отсутствии
     * незавершённых транзакций над данным компонентом.</p>
     *
     * @param amount количество энергии из сохранённых данных
     *               в FE; значения за пределами допустимого
     *               диапазона автоматически ограничиваются
     */
    public void restoreStoredEnergy(long amount) {
        this.storedEnergy = Math.clamp(
            amount,
            0,
            this.capacity
        );
    }
}
