package ru.greatdragonx.astralspace.common.energy;

import org.jspecify.annotations.NonNull;
import ru.greatdragonx.astralspace.common.energy.component.EnergyComponent;
import ru.greatdragonx.astralspace.common.energy.port.EnergyPortId;
import ru.greatdragonx.astralspace.common.energy.port.EnergyPortPolicy;

import java.util.List;
import java.util.Objects;
import java.util.function.LongSupplier;

/**
 * Фабрика компонентов выходного энергетического контура.
 *
 * <p>Создаёт согласованный набор компонентов,
 * обеспечивающих распределение, ограничение,
 * учёт и внешнюю выдачу энергии.</p>
 *
 * <p>Все созданные компоненты используют единое
 * энергетическое хранилище, общую политику портов
 * и единый исходящий бюджет передачи энергии.</p>
 *
 * <p>Активная отправка энергии (Push) и внешнее
 * извлечение (Pull) выполняются через один
 * {@link EnergyOutputController}.</p>
 *
 * <p>Каждый вызов фабрики создаёт новый независимый
 * набор выходных компонентов. Переданные энергетическое
 * хранилище, политика и поставщик игрового времени
 * используются без создания их копий.</p>
 *
 * <p>Фабрика не зависит от конкретного типа механизма,
 * игрового мира и способа производства энергии.
 * Может использоваться генераторами и энергетическими
 * накопителями.</p>
 *
 * <p>Не создаёт входной энергетический контур,
 * диспетчер операций и обработчики энергетических
 * портов.</p>
 */
public final class EnergyOutputFactory {
    private EnergyOutputFactory() {}

    /**
     * Результат сборки выходного энергетического контура.
     *
     * <p>Содержит компоненты распределения, ограничения,
     * учёта и внешней передачи энергии.</p>
     *
     * <p>При создании посредством фабрики все компоненты
     * согласованы между собой и используют общий
     * исходящий лимит передачи энергии.</p>
     *
     * @param distribution     менеджер распределения энергии
     * @param outputBudget     общий исходящий бюджет передачи
     * @param transferTracker  компонент учёта фактической
     *                         передачи энергии
     * @param outputController контроллер внешнего извлечения
     *                         энергии
     * @param pushTransfer     компонент активной отправки
     *                         энергии
     */
    public record Result(
        @NonNull EnergyDistributionManager distribution,
        @NonNull EnergyTransferBudget outputBudget,
        @NonNull EnergyTransferTracker transferTracker,
        @NonNull EnergyOutputController outputController,
        @NonNull EnergyPushTransfer pushTransfer
    )
    {
        /**
         * Создаёт результат сборки выходного
         * энергетического контура.
         *
         * <p>Проверяет наличие всех обязательных компонентов,
         * но не проверяет согласованность их внутренних
         * зависимостей. Согласованность компонентов
         * обеспечивается фабричным методом.</p>
         *
         * @throws NullPointerException если хотя бы один
         *                              компонент равен {@code null}
         */
        public Result {
            Objects.requireNonNull(distribution, "distribution cannot be null");
            Objects.requireNonNull(outputBudget, "outputBudget cannot be null");
            Objects.requireNonNull(transferTracker, "transferTracker cannot be null");
            Objects.requireNonNull(outputController, "outputController cannot be null");
            Objects.requireNonNull(pushTransfer, "pushTransfer cannot be null");
        }
    }

    /**
     * Создаёт согласованный выходной энергетический контур.
     *
     * <p>Создаёт менеджер распределения энергии,
     * исходящий бюджет, компонент учёта передачи,
     * выходной контроллер и компонент активной
     * отправки энергии.</p>
     *
     * <p>Менеджер распределения и исходящий бюджет
     * используют одинаковый максимальный лимит
     * передачи энергии за игровой тик.</p>
     *
     * <p>Выходной контроллер и компонент активной
     * отправки используют переданные экземпляры
     * энергетического хранилища и политики портов.
     * Активная отправка выполняется через созданный
     * выходной контроллер.</p>
     *
     * <p>Порядок известных энергетических портов
     * сохраняется. Пустой список портов допускается.
     * Защитное копирование списка и проверка его
     * содержимого выполняются выходным контроллером.</p>
     *
     * <p>Максимальный исходящий лимит должен быть
     * строго положительным и не ограничивается
     * ёмкостью энергетического хранилища.</p>
     *
     * <p>При создании компонентов не выполняются
     * энергетические операции, согласование доступных
     * портов, проверки политики или обращения
     * к поставщику игрового времени.</p>
     *
     * <p>Каждый вызов создаёт новые экземпляры
     * выходных компонентов без их кэширования.</p>
     *
     * @param energy              существующее энергетическое
     *                            хранилище механизма
     * @param ports               упорядоченный список известных
     *                            энергетических портов
     * @param policy              актуальная политика
     *                            энергетических портов
     * @param maxOutputPerTick    максимальный суммарный объём
     *                            внешней выдачи энергии
     *                            за игровой тик, FE/t
     * @param currentTickSupplier поставщик текущего
     *                            игрового времени
     * @return результат сборки выходного
     * энергетического контура
     * @throws NullPointerException     если хотя бы один ссылочный
     *                                  аргумент или элемент списка
     *                                  портов равен {@code null}
     * @throws IllegalArgumentException если исходящий лимит
     *                                  не является положительным
     *                                  или список портов содержит
     *                                  повторяющиеся идентификаторы
     */
    public static @NonNull Result create(
        @NonNull EnergyComponent energy,
        @NonNull List<EnergyPortId> ports,
        @NonNull EnergyPortPolicy policy,
        int maxOutputPerTick,
        @NonNull LongSupplier currentTickSupplier
    )
    {
        Objects.requireNonNull(energy, "energy cannot be null");
        Objects.requireNonNull(ports, "ports cannot be null");
        Objects.requireNonNull(policy, "policy cannot be null");
        Objects.requireNonNull(currentTickSupplier, "currentTickSupplier cannot be null");
        if (maxOutputPerTick <= 0) {
            throw new IllegalArgumentException("maxOutputPerTick must be positive");
        }

        EnergyDistributionManager manager = new EnergyDistributionManager(maxOutputPerTick);
        EnergyTransferBudget budget = new EnergyTransferBudget(maxOutputPerTick);
        EnergyTransferTracker tracker = new EnergyTransferTracker();
        EnergyOutputController controller = new EnergyOutputController(
            energy,
            ports,
            policy,
            manager,
            budget,
            tracker,
            currentTickSupplier
        );
        EnergyPushTransfer transfer = new EnergyPushTransfer(energy, controller, policy);

        return new Result(manager, budget, tracker, controller, transfer);
    }
}
