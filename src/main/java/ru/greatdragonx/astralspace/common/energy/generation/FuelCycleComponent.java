package ru.greatdragonx.astralspace.common.energy.generation;

import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.Tag;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jspecify.annotations.NonNull;

import java.util.Objects;
import java.util.Optional;

/**
 * Управляет состоянием оплаченного топливного цикла генератора.
 *
 * <p>Компонент отвечает за транзакционный запуск топливного
 * цикла, учёт оставшихся производственных тиков, сохранение
 * и восстановление состояния.</p>
 *
 * <p>Компонент поддерживает следующий инвариант:</p>
 *
 * <pre>{@code
 * 0 <= remainingTicks <= totalTicks
 * }</pre>
 *
 * <p>Топливный цикл считается активным, пока количество
 * оставшихся производственных тиков больше нуля.
 * После завершения цикла первоначальная длительность
 * сохраняется для последующего использования, например
 * при отображении прогресса в пользовательском интерфейсе.</p>
 *
 * <p>Компонент не управляет списанием конкретного топлива,
 * производством и хранением энергии, энергетическими портами,
 * редстоуном или взаимодействием с игровым миром.
 * Эти обязанности выполняются другими компонентами
 * и вызывающим механизмом.</p>
 *
 * <p>Запуск топливного цикла выполняется транзакционно,
 * что позволяет объединить изменение его состояния
 * со списанием топлива в рамках одной транзакции.</p>
 *
 * <p><strong>Важно:</strong> нетранзакционные операции
 * изменения состояния не должны выполняться во время
 * незавершённой транзакции, затрагивающей этот компонент.</p>
 *
 * <p>Компонент не предназначен для одновременного
 * использования из нескольких потоков.</p>
 */
public final class FuelCycleComponent {
    private static final String REMAINING_TICKS_KEY = "remaining_ticks";
    private static final String TOTAL_TICKS_KEY = "total_ticks";

    private int remainingTicks = 0;
    private int totalTicks = 0;
    private final Runnable onCycleChanged;

    /**
     * Неизменяемый снимок состояния топливного цикла.
     *
     * <p>Объединяет оставшуюся и первоначальную длительности
     * цикла для их совместного восстановления при откате
     * транзакции.</p>
     *
     * @param remainingTicks количество оставшихся
     *                       производственных тиков
     * @param totalTicks     первоначальная длительность
     *                       топливного цикла
     */
    private record FuelCycleSnapshot(
        int remainingTicks, int totalTicks
    ) {}

    /**
     * Журнал транзакционных изменений топливного цикла.
     *
     * <p>Сохраняет исходное состояние обоих счётчиков,
     * восстанавливает его при откате транзакции и уведомляет
     * владельца компонента после окончательного подтверждения
     * изменений.</p>
     */
    private final SnapshotJournal<FuelCycleSnapshot> cycleJournal = new SnapshotJournal<>() {
        /**
         * Создаёт снимок текущего состояния топливного цикла
         * перед транзакционным изменением.
         *
         * @return неизменяемый снимок обоих счётчиков
         */
        @Override
        protected @NonNull FuelCycleSnapshot createSnapshot() {
            return new FuelCycleSnapshot(
                FuelCycleComponent.this.remainingTicks,
                FuelCycleComponent.this.totalTicks
            );
        }

        /**
         * Восстанавливает состояние топливного цикла
         * при откате транзакции.
         *
         * <p>Оба счётчика восстанавливаются совместно
         * из ранее созданного снимка. Callback изменения
         * состояния не вызывается.</p>
         *
         * @param fuelCycleSnapshot ранее сохранённое
         *                          состояние топливного цикла
         */
        @Override
        protected void revertToSnapshot(@NonNull FuelCycleSnapshot fuelCycleSnapshot) {
            FuelCycleComponent.this.remainingTicks = fuelCycleSnapshot.remainingTicks();
            FuelCycleComponent.this.totalTicks = fuelCycleSnapshot.totalTicks();
        }

        /**
         * Обрабатывает окончательное подтверждение
         * корневой транзакции.
         *
         * <p>Уведомляет владельца компонента только в том
         * случае, если итоговое состояние топливного цикла
         * отличается от исходного хотя бы одним счётчиком.</p>
         *
         * <p>Подтверждение вложенной транзакции само по себе
         * не приводит к вызову callback.</p>
         *
         * @param originalState состояние топливного цикла
         *                      до транзакционных изменений
         */
        @Override
        protected void onRootCommit(
            @NonNull FuelCycleSnapshot originalState
        )
        {
            if (FuelCycleComponent.this.totalTicks != originalState.totalTicks() ||
                FuelCycleComponent.this.remainingTicks != originalState.remainingTicks())
            {
                FuelCycleComponent.this.onCycleChanged.run();
            }
        }
    };

    /**
     * Создаёт компонент с пустым топливным циклом.
     *
     * <p>Первоначальная и оставшаяся длительности цикла
     * равны нулю. Переданный callback сохраняется
     * для последующего уведомления владельца
     * об изменениях состояния.</p>
     *
     * <p>При создании компонента callback не вызывается.</p>
     *
     * @param onCycleChanged callback, вызываемый при изменении
     *                       состояния топливного цикла;
     *                       не может быть {@code null}
     * @throws NullPointerException если callback равен
     *                              {@code null}
     */
    public FuelCycleComponent(Runnable onCycleChanged) {
        this.onCycleChanged = Objects.requireNonNull(
            onCycleChanged,
            "onCycleChanged cannot be null"
        );
    }

    /**
     * Проверяет наличие активного топливного цикла.
     *
     * <p>Активность определяется исключительно количеством
     * оставшихся производственных тиков и не зависит
     * от первоначальной длительности цикла.</p>
     *
     * <p>Наличие активного цикла не означает, что генератор
     * в данный момент производит энергию. Производство
     * может быть временно приостановлено вызывающим
     * механизмом.</p>
     *
     * @return {@code true}, если количество оставшихся
     * производственных тиков больше нуля;
     * иначе {@code false}
     */
    public boolean isActive() {
        return this.remainingTicks > 0;
    }

    /**
     * Возвращает количество оставшихся производственных
     * тиков текущего топливного цикла.
     *
     * <p>Во время незавершённой транзакции может возвращать
     * предварительное состояние, которое впоследствии
     * будет подтверждено или отменено.</p>
     *
     * @return количество оставшихся производственных тиков
     */
    public int getRemainingTicks() {
        return this.remainingTicks;
    }

    /**
     * Возвращает первоначальную длительность
     * последнего запущенного топливного цикла.
     *
     * <p>Значение сохраняется после завершения цикла
     * и может использоваться для вычисления прогресса
     * в пользовательском интерфейсе.</p>
     *
     * <p>Во время незавершённой транзакции может возвращать
     * предварительное состояние.</p>
     *
     * @return первоначальная длительность цикла в тиках
     * либо {@code 0} при отсутствии сохранённой
     * длительности
     */
    public int getTotalTicks() {
        return this.totalTicks;
    }

    /**
     * Транзакционно запускает новый топливный цикл
     * указанной длительности.
     *
     * <p>Первоначальная и оставшаяся длительности
     * устанавливаются равными переданному значению.
     * Запуск нового цикла поверх активного запрещён.</p>
     *
     * <p>Перед изменением состояния регистрируется
     * совместный снимок обоих счётчиков в переданном
     * транзакционном контексте. При отмене транзакции
     * исходное состояние восстанавливается автоматически.</p>
     *
     * <p>Метод самостоятельно не открывает и не подтверждает
     * транзакцию, не списывает топливо и не производит
     * энергию. Ответственность за эти операции лежит
     * на вызывающем коде.</p>
     *
     * <p>Callback вызывается только после окончательного
     * подтверждения корневой транзакции, если итоговое
     * состояние отличается от исходного.</p>
     *
     * @param durationTicks первоначальная длительность
     *                      нового цикла в производственных
     *                      тиках; должна быть больше нуля
     * @param transaction   транзакционный контекст,
     *                      в котором выполняется запуск
     * @throws NullPointerException     если transaction
     *                                  равен {@code null}
     * @throws IllegalArgumentException если durationTicks
     *                                  меньше либо равен нулю
     * @throws IllegalStateException    если текущий топливный
     *                                  цикл ещё активен
     */
    public void start(int durationTicks, TransactionContext transaction) {
        Objects.requireNonNull(transaction, "transaction cannot be null");

        if (durationTicks <= 0) {
            throw new IllegalArgumentException("durationTicks must be positive");
        }

        if (this.isActive()) {
            throw new IllegalStateException(
                "Cannot start a new fuel cycle while the current cycle is active");
        }

        this.cycleJournal.updateSnapshots(transaction);

        this.totalTicks = durationTicks;
        this.remainingTicks = durationTicks;
    }

    /**
     * Завершает один оплаченный производственный тик.
     *
     * <p>Уменьшает количество оставшихся производственных
     * тиков ровно на единицу, не изменяя первоначальную
     * длительность топливного цикла.</p>
     *
     * <p>Метод должен вызываться только после успешного
     * выполнения производственного тика. Проверка условий
     * производства и фактического результата генерации
     * выполняется вызывающим механизмом.</p>
     *
     * <p>Операция выполняется без участия транзакционной
     * системы. После изменения состояния callback
     * вызывается немедленно и ровно один раз.</p>
     *
     * <p>При достижении нулевого остатка цикл становится
     * неактивным. Первоначальная длительность сохраняется.
     * Новый топливный цикл автоматически не запускается.</p>
     *
     * <p><strong>Ограничение:</strong> метод нельзя вызывать
     * во время незавершённой транзакции, изменяющей
     * состояние этого топливного цикла.</p>
     *
     * @throws IllegalStateException если активный топливный
     *                               цикл отсутствует
     */
    public void completeProductionTick() {
        if (!this.isActive()) {
            throw new IllegalStateException(
                "Cannot complete a production tick without an active fuel cycle");
        }

        this.remainingTicks--;
        this.onCycleChanged.run();
    }

    /**
     * Сохраняет текущее состояние топливного цикла
     * в предоставленный {@link ValueOutput}.
     *
     * <p>Записывает оставшуюся и первоначальную длительности
     * в виде двух целочисленных NBT-полей:</p>
     *
     * <ul>
     *     <li>{@code remaining_ticks}</li>
     *     <li>{@code total_ticks}</li>
     * </ul>
     *
     * <p>Оба поля записываются независимо от активности
     * топливного цикла, включая пустое состояние
     * и завершённый цикл.</p>
     *
     * <p>Метод не создаёт дополнительную вложенную секцию.
     * Ответственность за структуру внешних разделов
     * сохранения лежит на владельце компонента.</p>
     *
     * <p>Операция не изменяет состояние компонента
     * и не вызывает callback.</p>
     *
     * <p>Для постоянного сохранения должно использоваться
     * подтверждённое состояние топливного цикла, а не
     * предварительное состояние незавершённой транзакции.</p>
     *
     * @param output выходной объект для записи данных
     * @throws NullPointerException если output равен
     *                              {@code null}
     */
    public void serialize(@NonNull ValueOutput output) {
        Objects.requireNonNull(output, "output cannot be null");

        output.putInt(REMAINING_TICKS_KEY, this.remainingTicks);
        output.putInt(TOTAL_TICKS_KEY, this.totalTicks);
    }

    /**
     * Восстанавливает состояние топливного цикла
     * из предоставленного {@link ValueInput}.
     *
     * <p>Оба обязательных поля должны присутствовать,
     * иметь исходный NBT-тип {@link IntTag} и удовлетворять
     * следующему инварианту:</p>
     *
     * <pre>{@code
     * 0 <= remainingTicks <= totalTicks
     * }</pre>
     *
     * <p>При отсутствии хотя бы одного поля, несовместимом
     * NBT-типе или нарушении инварианта состояние
     * топливного цикла полностью сбрасывается в ноль.</p>
     *
     * <p>Частичное восстановление повреждённого состояния
     * и автоматическое ограничение отдельных значений
     * допустимым диапазоном не выполняются.</p>
     *
     * <p>Корректное состояние завершённого цикла
     * с нулевым остатком и положительной первоначальной
     * длительностью сохраняется.</p>
     *
     * <p>Операция выполняется без участия транзакционной
     * системы и не вызывает callback независимо
     * от результата восстановления.</p>
     *
     * <p><strong>Важно:</strong> метод предназначен
     * для восстановления сохранённого состояния
     * и должен вызываться при отсутствии незавершённых
     * транзакций над данным компонентом.</p>
     *
     * @param input входной объект с сохранёнными данными
     * @throws NullPointerException если input равен
     *                              {@code null}
     */
    public void deserialize(@NonNull ValueInput input) {
        Objects.requireNonNull(input, "input cannot be null");

        int remaining = readStrictInt(input, REMAINING_TICKS_KEY).orElse(-1);
        int total = readStrictInt(input, TOTAL_TICKS_KEY).orElse(-1);

        if (remaining < 0 || total < remaining) {
            this.remainingTicks = 0;
            this.totalTicks = 0;

            return;
        }

        this.remainingTicks = remaining;
        this.totalTicks = total;
    }

    /**
     * Читает целочисленное значение без автоматического
     * преобразования числовых NBT-типов.
     *
     * <p>Для получения исходного NBT-тега используется
     * {@link ExtraCodecs#NBT}. Значение принимается только
     * в том случае, если полученный тег является
     * экземпляром {@link IntTag}.</p>
     *
     * <p>Обычное чтение через
     * {@link ValueInput#getInt(String)} допускает
     * преобразование других числовых NBT-типов
     * в целочисленное значение, что не позволяет
     * гарантировать соответствие сохранённых данных
     * ожидаемой схеме.</p>
     *
     * <p>Метод проверяет исключительно наличие значения
     * и его исходный тип. Проверка допустимости самого
     * числа с точки зрения топливного цикла выполняется
     * вызывающим методом.</p>
     *
     * @param input входной объект с сохранёнными данными
     * @param key   название читаемого NBT-поля
     * @return прочитанное целочисленное значение либо
     * {@link Optional#empty()}, если поле отсутствует,
     * не может быть прочитано или имеет
     * неподходящий NBT-тип
     */
    private static @NonNull Optional<Integer> readStrictInt(
        @NonNull ValueInput input,
        @NonNull String key
    )
    {
        Optional<Tag> value = input.read(key, ExtraCodecs.NBT);
        if (value.isEmpty()) {
            return Optional.empty();
        }

        Tag tag = value.get();
        if (tag instanceof IntTag intTag) {
            int number = intTag.intValue();
            return Optional.of(number);
        }

        return Optional.empty();
    }
}
