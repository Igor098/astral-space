package ru.greatdragonx.astralspace.common.energy.blockentity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.NonNull;
import ru.greatdragonx.astralspace.common.energy.component.EnergyComponent;
import ru.greatdragonx.astralspace.common.serialization.StrictNbtReader;

import java.util.Objects;

/**
 * Базовый класс механизмов, содержащих внутреннее
 * энергетическое хранилище.
 *
 * <p>Владеет единственным {@link EnergyComponent},
 * создаваемым при конструировании механизма
 * и сохраняемым в течение всего жизненного цикла
 * данного экземпляра.</p>
 *
 * <p>Предоставляет публичный доступ к текущему запасу
 * энергии и максимальной ёмкости хранилища,
 * а также защищённый доступ к энергетическому
 * компоненту для наследников.</p>
 *
 * <p>При фактическом изменении запаса энергии
 * обеспечивает маркировку изменённого состояния
 * посредством {@link #setChanged()} и уведомляет
 * наследников через {@link #onEnergyChanged()}.</p>
 *
 * <p>Отвечает за сохранение и тихое восстановление
 * энергетического состояния. Количество энергии
 * сохраняется в виде значения {@code long}
 * во вложенной NBT-секции {@code energy}
 * с ключом {@code stored_energy}.</p>
 *
 * <p>Максимальная ёмкость определяется при создании
 * механизма и не является частью его сохраняемого
 * состояния.</p>
 *
 * <p>Класс не управляет производством и потреблением
 * энергии, энергетическими портами, лимитами передачи,
 * распределением энергии, топливными процессами
 * или синхронизацией визуального состояния.</p>
 *
 * <p>Эти обязанности выполняются специализированными
 * наследниками и соответствующими компонентами.</p>
 */
public abstract class AbstractEnergyMachineBlockEntity extends BlockEntity {
    private static final String ENERGY_KEY = "energy";
    private static final String STORED_ENERGY_KEY = "stored_energy";

    private final EnergyComponent energy;

    /**
     * Создаёт энергетический механизм с указанной
     * максимальной ёмкостью внутреннего хранилища.
     *
     * <p>Создаёт единственный {@link EnergyComponent}
     * с пустым энергетическим состоянием и привязывает
     * callback изменения запаса энергии.</p>
     *
     * <p>Конструирование не требует доступа к игровому
     * миру, не вызывает callback изменения энергии
     * и не создаёт внешние энергетические подключения.</p>
     *
     * @param type           тип блок-сущности
     * @param pos            позиция механизма в мире
     * @param blockState     состояние блока механизма
     * @param energyCapacity максимальная ёмкость
     *                       энергетического хранилища в FE;
     *                       должна быть больше нуля
     * @throws IllegalArgumentException если
     *                                  {@code energyCapacity <= 0}
     */
    protected AbstractEnergyMachineBlockEntity(
        @NonNull BlockEntityType<?> type,
        @NonNull BlockPos pos,
        @NonNull BlockState blockState,
        long energyCapacity
    )
    {
        super(type, pos, blockState);
        this.energy = new EnergyComponent(energyCapacity, this::handleEnergyChanged);
    }

    /**
     * Возвращает текущее количество энергии
     * во внутреннем хранилище механизма.
     *
     * <p>Во время незавершённой транзакции может
     * возвращать предварительное состояние,
     * которое впоследствии будет подтверждено
     * или отменено.</p>
     *
     * @return текущее количество энергии в FE
     * @see EnergyComponent#getStoredEnergy()
     */
    public final long getStoredEnergy() {
        return this.energy.getStoredEnergy();
    }

    /**
     * Возвращает максимальную ёмкость внутреннего
     * энергетического хранилища механизма.
     *
     * <p>Ёмкость определяется при конструировании
     * и не изменяется в течение жизненного цикла
     * данного экземпляра.</p>
     *
     * @return максимальная ёмкость хранилища в FE
     * @see EnergyComponent#getCapacity()
     */
    public final long getEnergyCapacity() {
        return this.energy.getCapacity();
    }

    /**
     * Предоставляет наследникам доступ к внутреннему
     * энергетическому компоненту механизма.
     *
     * <p>Возвращает принадлежащий данному механизму
     * экземпляр {@link EnergyComponent}, не создавая
     * его копию или дополнительное хранилище.</p>
     *
     * <p>Предназначен для построения энергетического
     * контура механизма, сохранения произведённой
     * энергии и её потребления для внутренней работы.</p>
     *
     * <p><strong>Важно:</strong> внешняя передача энергии
     * должна выполняться через соответствующие контроллеры
     * с соблюдением политик энергетических портов
     * и установленных лимитов передачи.</p>
     *
     * @return внутренний энергетический компонент механизма
     */
    protected final @NonNull EnergyComponent getEnergyComponent() {
        return this.energy;
    }

    /**
     * Обрабатывает фактическое изменение запаса энергии.
     *
     * <p>Вызывается после обязательной маркировки
     * изменённого состояния посредством {@link #setChanged()}.</p>
     *
     * <p>При нетранзакционном добавлении произведённой
     * энергии вызывается непосредственно после изменения
     * запаса. При транзакционном изменении вызывается
     * после окончательного подтверждения корневой
     * транзакции, если итоговый запас отличается
     * от исходного.</p>
     *
     * <p>Не вызывается при конструировании механизма,
     * откате транзакции или тихом восстановлении
     * сохранённого энергетического состояния.</p>
     *
     * <p>Реализация по умолчанию не выполняет
     * дополнительных действий.</p>
     *
     * <p>Наследники могут переопределять метод для
     * выполнения специфических реакций, например
     * обновления визуального индикатора уровня заряда.</p>
     *
     * <p>Наследникам не требуется вызывать родительскую
     * реализацию для маркировки изменённого состояния.</p>
     *
     * <p><strong>Важно:</strong> вызов метода сам по себе
     * не гарантирует наличия игрового мира или готовности
     * серверного окружения. Ответственность за проверку
     * необходимых условий лежит на наследнике.</p>
     *
     * <p>Реализация метода не должна повторно изменять
     * энергетическое хранилище и инициировать рекурсивный
     * вызов уведомления.</p>
     */
    protected void onEnergyChanged() {}

    /**
     * {@inheritDoc}
     *
     * <p>После сохранения состояния родительского класса
     * записывает текущее количество энергии во вложенную
     * NBT-секцию {@code energy} с ключом
     * {@code stored_energy} в виде значения {@code long}.</p>
     *
     * <p>Максимальная ёмкость хранилища не сохраняется,
     * поскольку определяется при создании механизма.</p>
     *
     * <p>Операция не изменяет энергетическое состояние
     * и не вызывает уведомления об его изменении.</p>
     *
     * <p>Для постоянного сохранения должно использоваться
     * подтверждённое энергетическое состояние, а не
     * предварительное состояние незавершённой транзакции.</p>
     *
     * @throws NullPointerException если output равен
     *                              {@code null}
     */
    @Override
    protected void saveAdditional(
        @NonNull ValueOutput output
    )
    {
        Objects.requireNonNull(output, "output cannot be null");
        super.saveAdditional(output);

        ValueOutput energyOutput = output.child(ENERGY_KEY);
        energyOutput.putLong(STORED_ENERGY_KEY, this.getStoredEnergy());
    }

    /**
     * {@inheritDoc}
     *
     * <p>После восстановления состояния родительского
     * класса читает количество энергии из вложенной
     * NBT-секции {@code energy} с ключом
     * {@code stored_energy}.</p>
     *
     * <p>Принимается только исходный NBT-тип
     * {@code LongTag}. При отсутствии секции, поля
     * или несовместимом типе данных восстанавливается
     * нулевой запас энергии.</p>
     *
     * <p>Корректно прочитанное числовое значение
     * ограничивается диапазоном от нуля до текущей
     * максимальной ёмкости посредством
     * {@link EnergyComponent#restoreStoredEnergy(long)}.</p>
     *
     * <p>Восстановление энергетического состояния
     * выполняется без участия транзакционной системы,
     * вызова callback изменения энергии, дополнительной
     * маркировки изменённого состояния и синхронизации
     * визуального представления.</p>
     *
     * <p><strong>Важно:</strong> восстановление должно
     * выполняться при отсутствии незавершённых транзакций,
     * затрагивающих внутреннее энергетическое хранилище.</p>
     *
     * @throws NullPointerException если input равен
     *                              {@code null}
     * @see StrictNbtReader#readStrictLong(ValueInput, String)
     */
    @Override
    protected void loadAdditional(
        @NonNull ValueInput input
    )
    {
        Objects.requireNonNull(input, "input cannot be null");
        super.loadAdditional(input);

        ValueInput energyInput = input.childOrEmpty(ENERGY_KEY);
        long amount = StrictNbtReader.readStrictLong(energyInput, STORED_ENERGY_KEY).orElse(0L);

        this.energy.restoreStoredEnergy(amount);
    }

    /**
     * Координирует обязательные и дополнительные реакции
     * на фактическое изменение энергетического состояния.
     *
     * <p>Сначала маркирует состояние механизма изменённым
     * посредством {@link #setChanged()}, после чего
     * вызывает защищённый метод {@link #onEnergyChanged()}.</p>
     *
     * <p>Такое разделение обеспечивает обязательную
     * маркировку изменённого состояния независимо
     * от реализации дополнительной реакции наследника.</p>
     *
     * <p>Используется в качестве callback внутреннего
     * энергетического компонента.</p>
     */
    private void handleEnergyChanged() {
        this.setChanged();
        this.onEnergyChanged();
    }
}
