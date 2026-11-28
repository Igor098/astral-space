package ru.greatdragonx.astralspace.common.energy.blockentity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.jetbrains.annotations.Unmodifiable;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import ru.greatdragonx.astralspace.common.energy.*;
import ru.greatdragonx.astralspace.common.energy.component.EnergyComponent;
import ru.greatdragonx.astralspace.common.energy.generation.GenerationProcess;
import ru.greatdragonx.astralspace.common.energy.generation.GeneratorConfiguration;
import ru.greatdragonx.astralspace.common.energy.generation.PersistentGenerationProcess;
import ru.greatdragonx.astralspace.common.energy.port.*;
import ru.greatdragonx.astralspace.common.machine.redstone.RedstoneControlMode;
import ru.greatdragonx.astralspace.common.machine.redstone.RedstoneControlSerializer;
import ru.greatdragonx.astralspace.common.machine.redstone.RedstoneControllable;
import ru.greatdragonx.astralspace.common.machine.redstone.RedstoneController;

import java.util.*;

/**
 * Базовая реализация блок-сущности генератора энергии.
 *
 * <p>Объединяет общие механизмы производства,
 * хранения и передачи энергии, независимые
 * от конкретного способа её получения.</p>
 *
 * <p>Класс отвечает за:</p>
 * <ul>
 *     <li>управление жизненным циклом серверной
 *     энергетической подсистемы;</li>
 *     <li>выполнение производственного тика;</li>
 *     <li>управление режимами и доступностью
 *     энергетических портов;</li>
 *     <li>разрешение логических портов
 *     в физические точки подключения;</li>
 *     <li>управление актуальностью физических
 *     привязок энергетических обработчиков;</li>
 *     <li>активную и пассивную выдачу энергии
 *     в рамках единого бюджета передачи;</li>
 *     <li>учёт фактических показателей
 *     производства и передачи энергии;</li>
 *     <li>управление редстоун-контролем;</li>
 *     <li>сохранение и восстановление
 *     общего состояния генератора.</li>
 * </ul>
 *
 * <h2>Производственный процесс</h2>
 *
 * <p>Конкретный способ производства энергии
 * определяется реализацией {@link GenerationProcess}.
 * Наследник обязан однократно привязать
 * производственный процесс после инициализации
 * необходимых ему компонентов.</p>
 *
 * <p>Привязка должна быть выполнена до загрузки,
 * сохранения или начала серверной работы
 * блок-сущности.</p>
 *
 * <p>Базовый генератор самостоятельно проверяет
 * общие условия производства, сохраняет полную
 * порцию произведённой энергии и уведомляет
 * производственный процесс об успешном
 * завершении производственного тика.</p>
 *
 * <h2>Энергетические порты</h2>
 *
 * <p>Логические порты определяются конфигурацией
 * генератора. Каждый порт обладает стабильным
 * идентификатором, набором разрешённых режимов
 * и физической точкой подключения.</p>
 *
 * <p>Все энергетические порты используют единое
 * энергетическое хранилище, бюджет передачи,
 * распределитель и контроллер выдачи энергии.</p>
 *
 * <p>Генератор поддерживает только выдачу энергии.
 * Внешний приём энергии через энергетические
 * порты не поддерживается.</p>
 *
 * <p>Логические обработчики портов существуют
 * на протяжении жизненного цикла экземпляра
 * блок-сущности. Внешним потребителям предоставляются
 * обработчики, дополнительно контролирующие
 * актуальность физической привязки.</p>
 *
 * <h2>Жизненный цикл</h2>
 *
 * <p>Серверная энергетическая подсистема становится
 * доступной после успешного выполнения
 * {@link #onLoad()} в серверном мире.</p>
 *
 * <p>До открытия и после закрытия энергетической
 * подсистемы производство и передача энергии
 * не выполняются, а внешние энергетические
 * обработчики не предоставляются.</p>
 *
 * <p>При удалении блок-сущности или выгрузке
 * её чанка физические привязки энергетических
 * обработчиков отзываются.</p>
 *
 * <p>Готовность серверной энергетической подсистемы
 * не означает готовность конструкции генератора.
 * Эти состояния проверяются независимо.</p>
 *
 * <h2>Готовность конструкции</h2>
 *
 * <p>По умолчанию генератор считается
 * одноблочным и всегда готовым к работе
 * со стороны своей конструкции.</p>
 *
 * <p>Наследники могут определять дополнительные
 * условия готовности конструкции и физической
 * доступности отдельных энергетических портов.</p>
 *
 * <p>Потеря готовности конструкции блокирует
 * производство и передачу энергии, но сама
 * по себе не закрывает серверную энергетическую
 * подсистему, не сбрасывает накопленную энергию,
 * настройки портов и состояние
 * производственного процесса.</p>
 *
 * <h2>Редстоун-контроль</h2>
 *
 * <p>Редстоун-контроль ограничивает исключительно
 * производство энергии. При запрете производства
 * накопленная энергия остаётся доступной
 * для активной и пассивной выдачи через
 * разрешённые энергетические порты.</p>
 *
 * <h2>Сохранение состояния</h2>
 *
 * <p>Сохраняются энергетическое хранилище,
 * настраиваемые режимы энергетических портов,
 * режим редстоун-контроля и состояние
 * производственного процесса, если он реализует
 * {@link PersistentGenerationProcess}.</p>
 *
 * <p>Обработчики, физические привязки, текущие
 * бюджеты передачи, статистика, состояние
 * распределителя и готовность серверной
 * энергетической подсистемы не сериализуются.</p>
 *
 * <p>Восстановление состояния не выполняет
 * синхронизацию серверной энергетической
 * подсистемы и предназначено для штатной
 * загрузки блок-сущности и восстановления
 * сериализуемых данных.</p>
 *
 * <p>Изменение сериализуемого состояния
 * работающего генератора посредством внешней
 * модификации NBT не является поддерживаемым
 * способом управления генератором.</p>
 *
 * <h2>Ответственность наследников</h2>
 *
 * <p>Наследники определяют конкретный производственный
 * процесс, его ресурсы, дополнительные состояния,
 * визуальное представление, пользовательский
 * интерфейс и особенности конструкции.</p>
 *
 * <p>Регистрация блока, блок-сущности,
 * энергетических capabilities и серверного
 * ticker выполняется за пределами данного класса.</p>
 *
 * <p>При переопределении методов жизненного цикла
 * и сериализации наследники обязаны вызывать
 * соответствующую реализацию родительского класса.</p>
 *
 * <p>Серверные операции рассчитаны на выполнение
 * в игровом потоке. Независимый многопоточный
 * доступ и реентерабельное разрушение генератора
 * из выполняемых энергетических операций
 * не поддерживаются.</p>
 *
 * @see AbstractEnergyMachineBlockEntity
 * @see GeneratorConfiguration
 * @see GenerationProcess
 * @see PersistentGenerationProcess
 * @see EnergyPortPolicy
 * @see EnergyEndpointHandler
 * @see RedstoneControllable
 */
public abstract class AbstractGeneratorBlockEntity extends
    AbstractEnergyMachineBlockEntity implements RedstoneControllable
{
    /**
     * Ключ секции сохранённых режимов
     * настраиваемых энергетических портов.
     */
    private static final String ENERGY_PORTS_KEY = "energy_ports";
    /**
     * Ключ секции сериализуемого состояния
     * производственного процесса.
     */
    private static final String GENERATION_PROCESS_KEY = "generation_process";

    /**
     * Неизменяемая конфигурация генератора,
     * определяющая энергетические характеристики,
     * правила и топологию энергетических портов.
     */
    private final GeneratorConfiguration configuration;
    /**
     * Политика энергетических портов, определяющая
     * текущие режимы, допустимость их изменения
     * и возможность выполнения энергетических операций
     * с учётом актуальной доступности портов.
     */
    private final EnergyPortPolicy energyPortPolicy;

    /**
     * Изменяемая конфигурация режимов
     * энергетических портов генератора.
     *
     * <p>Равна {@code null}, если генератор
     * использует фиксированные режимы портов,
     * не допускающие изменения.</p>
     */
    private final @Nullable EnergyPortConfiguration energyPortConfiguration;
    /**
     * Менеджер распределения общего объёма
     * выдаваемой энергии между доступными
     * энергетическими портами генератора.
     */
    private final EnergyDistributionManager distribution;
    /**
     * Единый бюджет выдачи энергии за игровой тик.
     *
     * <p>Используется всеми энергетическими портами
     * независимо от способа передачи энергии:
     * активной выдачи (Push) или пассивного
     * извлечения (Pull).</p>
     */
    private final EnergyTransferBudget outputBudget;
    /**
     * Компонент учёта фактически переданной энергии.
     *
     * <p>Предоставляет итоговую скорость передачи
     * за предыдущий завершённый игровой тик.</p>
     */
    private final EnergyTransferTracker transferTracker;
    /**
     * Контроллер выдачи энергии, объединяющий
     * энергетическое хранилище, политику портов,
     * распределение энергии, бюджет передачи
     * и учёт фактически выполненных операций.
     */
    private final EnergyOutputController outputController;
    /**
     * Компонент транзакционной активной выдачи
     * энергии во внешние энергетические хранилища.
     */
    private final EnergyPushTransfer pushTransfer;

    /**
     * Неизменяемая карта логических обработчиков
     * всех объявленных энергетических портов.
     *
     * <p>Обработчики создаются при инициализации
     * генератора и сохраняются независимо
     * от изменения режимов, физической доступности
     * и мирового положения портов.</p>
     *
     * <p>Не предоставляются внешним потребителям
     * напрямую.</p>
     */
    private final Map<EnergyPortId, EnergyPortHandler> energyPortHandlers;
    /**
     * Карта актуальных физических привязок
     * энергетических обработчиков.
     *
     * <p>Каждому логическому порту соответствует
     * не более одной актуальной физической привязки.</p>
     *
     * <p>Удаление или замена записи делает
     * соответствующую ранее выданную физическую
     * привязку недействительной.</p>
     */
    private final Map<EnergyPortId, EnergyEndpointBinding> energyEndpointBindings = new HashMap<>();
    /**
     * Последний синхронизированный снимок
     * физически представленных энергетических портов.
     *
     * <p>Содержит мировые точки подключения портов,
     * для которых была разрешена выдача энергии
     * на момент последней синхронизации.</p>
     *
     * <p>Используется для определения мировых позиций,
     * требующих инвалидации энергетических capabilities
     * при изменении доступности или расположения портов.</p>
     *
     * <p>Не является источником истины для проверки
     * разрешения энергетических операций.</p>
     */
    private Map<EnergyPortId, EnergyPortTopology.WorldPortPoint> exposedEnergyPorts =
        Collections.emptyMap();

    /**
     * Контроллер редстоун-управления генератором,
     * хранящий выбранный режим и определяющий
     * возможность производства энергии
     * по актуальному редстоун-сигналу.
     */
    private final RedstoneController redstoneController;

    /**
     * Производственный процесс конкретного генератора.
     *
     * <p>До выполнения однократной привязки
     * имеет значение {@code null}.</p>
     *
     * <p>После привязки не допускает замены
     * другим производственным процессом.</p>
     */
    private @Nullable GenerationProcess generationProcess;
    /**
     * Внутренний признак открытия серверной
     * энергетической подсистемы генератора.
     *
     * <p>Самостоятельно не гарантирует возможность
     * выполнения серверных энергетических операций.
     * Дополнительно проверяются наличие серверного
     * мира и состояние блок-сущности.</p>
     *
     * @see #isEnergyRuntimeReady()
     */
    private boolean energyRuntimeReady = false;
    /**
     * Количество энергии, фактически сохранённой
     * в рамках последнего выполненного
     * серверного производственного тика, в FE/t.
     *
     * <p>Сбрасывается перед обработкой
     * очередного серверного тика.</p>
     *
     * <p>Не является накопительным счётчиком
     * и не сериализуется.</p>
     */
    private int currentProductionRate = 0;

    /**
     * Представляет актуальную физическую привязку
     * логического энергетического порта генератора.
     *
     * <p>Объединяет стабильный идентификатор порта,
     * конкретную мировую точку подключения
     * и внешний энергетический обработчик.</p>
     *
     * <p>Физическая привязка считается действительной
     * только пока она является текущей привязкой
     * соответствующего порта и удовлетворяет
     * условиям актуальности генератора.</p>
     *
     * <p>Удаление или замена привязки в карте
     * владельца приводит к отзыву ранее выданного
     * внешнего обработчика.</p>
     *
     * <p>Возврат порта в прежнюю мировую точку
     * не восстанавливает ранее отозванную привязку.</p>
     *
     * <p>Временная недоступность порта не обязательно
     * приводит к отзыву его физической привязки.
     * Разрешение энергетических операций дополнительно
     * определяется политикой логического порта.</p>
     *
     * @see EnergyEndpointHandler
     * @see EnergyPortHandler
     * @see AbstractGeneratorBlockEntity#isEnergyEndpointValid(EnergyEndpointBinding)
     */
    private final class EnergyEndpointBinding {
        /**
         * Стабильный идентификатор логического
         * энергетического порта.
         */
        private final EnergyPortId portId;
        /**
         * Мировая позиция и внешняя сторона порта,
         * к которым относится данная физическая привязка.
         */
        private final EnergyPortTopology.WorldPortPoint worldPoint;
        /**
         * Внешний энергетический обработчик,
         * проверяющий актуальность данной привязки
         * перед делегированием энергетических операций.
         */
        private final EnergyEndpointHandler handler;

        /**
         * Создаёт физическую привязку
         * энергетического порта.
         *
         * <p>Формирует внешний обработчик, делегирующий
         * энергетические операции логическому обработчику
         * порта после проверки актуальности
         * создаваемой привязки.</p>
         *
         * <p>Создание объекта самостоятельно
         * не регистрирует привязку в карте владельца
         * и не делает её действительной.</p>
         *
         * @param portId     идентификатор логического порта
         * @param worldPoint мировая точка подключения порта
         * @param delegate   логический энергетический
         *                   обработчик порта
         * @throws NullPointerException если любой аргумент
         *                              равен {@code null}
         */
        private EnergyEndpointBinding(
            @NonNull EnergyPortId portId,
            EnergyPortTopology.@NonNull WorldPortPoint worldPoint,
            @NonNull EnergyPortHandler delegate
        )
        {
            this.portId = Objects.requireNonNull(portId, "portId cannot be null");
            this.worldPoint = Objects.requireNonNull(worldPoint, "worldPoint cannot be null");
            this.handler = new EnergyEndpointHandler(
                delegate,
                () -> AbstractGeneratorBlockEntity.this.isEnergyEndpointValid(this)
            );
        }
    }

    /**
     * Инициализирует общие энергетические
     * компоненты генератора.
     *
     * <p>Создаёт энергетическое хранилище заданной
     * ёмкости посредством родительского класса.</p>
     *
     * <p>На основании переданной конфигурации
     * инициализирует политику энергетических портов,
     * изменяемую конфигурацию режимов при необходимости,
     * менеджер распределения энергии, общий бюджет
     * передачи, трекер, контроллер выдачи энергии
     * и компонент активной передачи энергии.</p>
     *
     * <p>Создаёт логические обработчики
     * всех объявленных энергетических портов
     * и контроллер редстоун-управления.</p>
     *
     * <p>Конструктор не открывает серверную
     * энергетическую подсистему, не выполняет
     * поиск внешних энергетических capabilities
     * и не запускает производственный процесс.</p>
     *
     * <p>Наследник обязан самостоятельно создать
     * конкретный производственный процесс
     * и однократно привязать его через
     * {@link #bindGenerationProcess(GenerationProcess)}
     * после инициализации необходимых компонентов.</p>
     *
     * @param type          тип блок-сущности
     * @param pos           мировая позиция блок-сущности
     * @param blockState    начальное состояние блока
     * @param configuration конфигурация генератора
     * @throws NullPointerException если конфигурация
     *                              равна {@code null}
     */
    protected AbstractGeneratorBlockEntity(
        @NonNull BlockEntityType<?> type,
        @NonNull BlockPos pos,
        @NonNull BlockState blockState,
        @NonNull GeneratorConfiguration configuration
    )
    {
        super(
            type, pos, blockState, Objects.requireNonNull(
                configuration,
                "configuration cannot be null"
            ).energy().energyCapacity()
        );

        this.configuration = configuration;

        EnergyPortPolicyFactory.Result policyConfig = EnergyPortPolicyFactory.create(
            configuration.portRules(),
            this::isEnergyPortAvailable,
            this::handleEnergyPortConfigurationChanged
        );
        this.energyPortPolicy = policyConfig.policy();
        this.energyPortConfiguration = policyConfig.configuration();

        EnergyOutputFactory.Result outputConfig = EnergyOutputFactory.create(
            this.getEnergyComponent(),
            this.configuration.portRules().portIds(),
            this.energyPortPolicy,
            this.configuration.energy().maxOutputPerTick(),
            this::getCurrentGameTime
        );
        this.distribution = outputConfig.distribution();
        this.outputBudget = outputConfig.outputBudget();
        this.transferTracker = outputConfig.transferTracker();
        this.outputController = outputConfig.outputController();
        this.pushTransfer = outputConfig.pushTransfer();

        EnergyPortDispatcher dispatcher = new EnergyPortDispatcher(
            this.getEnergyComponent(),
            null,
            this.outputController
        );
        this.energyPortHandlers = this.createEnergyPortHandlers(dispatcher);

        this.redstoneController = new RedstoneController(
            this::isEnergyRuntimeReady,
            this::readRedstoneSignal,
            this::setChanged
        );
    }

    /**
     * {@inheritDoc}
     *
     * <p>Проверяет наличие обязательного
     * производственного процесса независимо
     * от стороны выполнения.</p>
     *
     * <p>В серверном мире открывает энергетическую
     * подсистему генератора, если блок-сущность
     * не помечена как удалённая.</p>
     *
     * <p>После открытия синхронизирует набор
     * доступных энергетических портов
     * и представленные внешним потребителям
     * физические точки подключения.</p>
     *
     * <p>Повторный вызов при уже открытой
     * серверной энергетической подсистеме
     * не выполняет её повторную инициализацию.</p>
     *
     * <p>В клиентском мире или при отсутствии
     * корректного серверного контекста
     * энергетическая подсистема не открывается.</p>
     *
     * <p>Если во время первоначальной синхронизации
     * возникает исключение или ошибка, энергетическая
     * подсистема закрывается, после чего ошибка
     * передаётся вызывающему коду.</p>
     *
     * @throws IllegalStateException если производственный
     *                               процесс не привязан
     */
    @Override
    public final void onLoad() {
        super.onLoad();
        this.requireGenerationProcess();

        if (!this.hasServerContext()) {
            return;
        }

        if (this.energyRuntimeReady) {
            return;
        }

        this.energyRuntimeReady = true;

        try {
            this.synchronizeAvailableEnergyPorts();
            this.synchronizeExposedEnergyPorts(Collections.emptySet());
        } catch (RuntimeException | Error e) {
            this.closeEnergyRuntime();
            throw e;
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>Перед выполнением родительской реализации
     * закрывает серверную энергетическую подсистему
     * генератора и отзывает все текущие
     * физические привязки энергетических портов.</p>
     *
     * <p>Ранее полученные внешние энергетические
     * обработчики перестают быть действительными.</p>
     *
     * <p>Закрытие энергетической подсистемы
     * не сбрасывает сериализуемое состояние
     * генератора.</p>
     */
    @Override
    public void setRemoved() {
        this.closeEnergyRuntime();
        super.setRemoved();
    }

    /**
     * {@inheritDoc}
     *
     * <p>Закрывает серверную энергетическую
     * подсистему при выгрузке чанка,
     * содержащего блок-сущность генератора.</p>
     *
     * <p>Отзывает физические привязки
     * энергетических портов и очищает снимок
     * представленных внешних точек подключения.</p>
     *
     * <p>Не изменяет накопленную энергию,
     * настройки портов, режим редстоун-контроля
     * и состояние производственного процесса.</p>
     *
     * <p>Допускает последующее штатное открытие
     * энергетической подсистемы через
     * {@link #onLoad()} при наличии корректного
     * серверного контекста.</p>
     */
    @Override
    public void onChunkUnloaded() {
        this.closeEnergyRuntime();
        super.onChunkUnloaded();
    }

    /**
     * {@inheritDoc}
     *
     * <p>Возвращает текущий сохранённый режим
     * редстоун-управления генератором.</p>
     *
     * <p>Доступен независимо от готовности
     * серверной энергетической подсистемы
     * и конструкции генератора.</p>
     *
     * @return текущий режим редстоун-контроля
     */
    @Override
    public final @NonNull RedstoneControlMode getRedstoneControlMode() {
        return this.redstoneController.getRedstoneControlMode();
    }

    /**
     * {@inheritDoc}
     *
     * <p>Сравнивает горизонтальную ориентацию
     * генератора до и после изменения состояния блока.</p>
     *
     * <p>Если ориентация не изменилась,
     * дополнительная синхронизация энергетических
     * портов не выполняется.</p>
     *
     * <p>При изменении ориентации и открытой
     * серверной энергетической подсистеме:</p>
     * <ol>
     *     <li>отзывает физические привязки портов,
     *     мировые точки подключения которых изменились;</li>
     *     <li>синхронизирует набор доступных
     *     энергетических портов;</li>
     *     <li>обновляет представленные физические
     *     точки подключения и инвалидирует
     *     затронутые capabilities.</li>
     * </ol>
     *
     * <p>Физические привязки портов, мировые точки
     * подключения которых остались прежними,
     * сохраняются.</p>
     *
     * <p>Изменение ориентации не сбрасывает
     * энергетическое хранилище, режимы портов,
     * состояние производственного процесса
     * и общий бюджет передачи энергии.</p>
     *
     * @param blockState новое состояние блока
     * @throws NullPointerException если {@code blockState}
     *                              равен {@code null}
     */
    @Override
    @SuppressWarnings("deprecation")
    public void setBlockState(@NonNull BlockState blockState) {
        Objects.requireNonNull(blockState, "blockState cannot be null");

        Direction prevFacing = this.getFacing();
        super.setBlockState(blockState);

        Direction currentFacing = this.getFacing();
        if (prevFacing == currentFacing) {
            return;
        }

        if (!this.isEnergyRuntimeReady()) {
            return;
        }

        this.revokeMovedEnergyEndpointBindings();
        this.synchronizeAvailableEnergyPorts();
        this.synchronizeExposedEnergyPorts(Collections.emptySet());
    }

    /**
     * {@inheritDoc}
     *
     * <p>Делегирует изменение режима
     * контроллеру редстоун-управления.</p>
     *
     * <p>Изменение допускается только при готовой
     * серверной энергетической подсистеме.</p>
     *
     * <p>При фактическом изменении режима
     * блок-сущность помечается как изменённая.
     * Повторная установка текущего режима
     * не вызывает дополнительного уведомления.</p>
     *
     * <p>Изменение режима не влияет на настройки
     * и физические привязки энергетических портов,
     * накопленную энергию и бюджет её передачи.</p>
     *
     * @param mode запрашиваемый режим редстоун-контроля
     * @return {@code true}, если режим был изменён;
     * {@code false}, если изменение не выполнено
     * @throws NullPointerException если {@code mode}
     *                              равен {@code null}
     */
    @Override
    public final boolean changeRedstoneControlMode(
        @NonNull RedstoneControlMode mode
    )
    {
        return this.redstoneController.changeRedstoneControlMode(mode);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Определяет наличие актуального
     * редстоун-сигнала через контроллер
     * редстоун-управления.</p>
     *
     * <p>Результат не кэшируется между вызовами.</p>
     *
     * <p>При закрытой серверной энергетической
     * подсистеме возвращает {@code false}
     * без чтения редстоун-сигнала из мира.</p>
     *
     * @return {@code true}, если доступен
     * активный редстоун-сигнал; иначе {@code false}
     */
    @Override
    public final boolean hasRedstoneSignal() {
        return this.redstoneController.hasRedstoneSignal();
    }

    /**
     * {@inheritDoc}
     *
     * <p>Определяет, запрещает ли текущий режим
     * редстоун-контроля производство энергии
     * с учётом актуального редстоун-сигнала.</p>
     *
     * <p>Запрет производства не ограничивает
     * активную и пассивную выдачу
     * ранее накопленной энергии.</p>
     *
     * <p>Метод не определяет общую готовность
     * генератора к производству энергии.</p>
     *
     * <p>При закрытой серверной энергетической
     * подсистеме возвращает {@code false}.</p>
     *
     * @return {@code true}, если производство
     * запрещено редстоун-контролем;
     * иначе {@code false}
     */
    @Override
    public final boolean isRedstoneBlockingOperation() {
        return this.redstoneController.isRedstoneBlockingOperation();
    }

    /**
     * Возвращает текущий режим указанного
     * логического энергетического порта.
     *
     * <p>Для фиксированных портов возвращает
     * режим, определённый конфигурацией генератора.</p>
     *
     * <p>Для настраиваемых портов возвращает
     * текущий сохранённый режим.</p>
     *
     * <p>Если указанный идентификатор отсутствует
     * в конфигурации генератора, возвращает
     * {@link EnergyPortMode#DISABLED}.</p>
     *
     * <p>Возвращаемый режим не учитывает
     * текущую готовность серверной подсистемы,
     * готовность конструкции и физическую
     * доступность порта.</p>
     *
     * @param portId идентификатор энергетического порта
     * @return текущий режим порта
     * @throws NullPointerException если {@code portId}
     *                              равен {@code null}
     */
    public final @NonNull EnergyPortMode getEnergyPortMode(
        @NonNull EnergyPortId portId
    )
    {
        Objects.requireNonNull(portId, "portId cannot be null");
        return this.energyPortPolicy.getMode(portId);
    }

    /**
     * Возвращает внешний энергетический
     * обработчик для указанной мировой стороны
     * блока генератора.
     *
     * <p>Является специализированным вариантом
     * {@link #getEnergyHandlerAt(BlockPos, Direction)},
     * использующим позицию данной блок-сущности.</p>
     *
     * <p>Предназначен преимущественно для регистрации
     * энергетических capabilities одноблочных
     * генераторов.</p>
     *
     * <p>Порты, расположенные в других мировых
     * позициях, должны запрашиваться посредством
     * {@link #getEnergyHandlerAt(BlockPos, Direction)}.</p>
     *
     * @param side мировая внешняя сторона блока
     * @return актуальный внешний энергетический
     * обработчик или {@code null}, если на указанной
     * стороне выдача энергии недоступна
     */
    public final @Nullable EnergyHandler getEnergyHandlerForSide(
        @Nullable Direction side
    )
    {
        return this.getEnergyHandlerAt(this.getBlockPos(), side);
    }

    /**
     * Возвращает внешний энергетический обработчик
     * для указанной мировой точки подключения.
     *
     * <p>Мировая точка подключения определяется
     * позицией блока и его внешней стороной.</p>
     *
     * <p>На основании топологии и текущей ориентации
     * генератора разрешает мировую точку подключения
     * в идентификатор логического энергетического порта.</p>
     *
     * <p>Обработчик предоставляется только при готовой
     * серверной энергетической подсистеме
     * и разрешённой выдаче энергии через
     * соответствующий логический порт.</p>
     *
     * <p>Возвращает {@code null}, если:</p>
     * <ul>
     *     <li>серверная энергетическая подсистема
     *     не готова;</li>
     *     <li>сторона не указана;</li>
     *     <li>мировая точка подключения
     *     не соответствует объявленному порту;</li>
     *     <li>выдача энергии через порт запрещена
     *     его текущим режимом;</li>
     *     <li>конструкция или физический порт
     *     недоступны.</li>
     * </ul>
     *
     * <p>При наличии неизменной актуальной
     * физической привязки возвращает ранее
     * созданный внешний обработчик.</p>
     *
     * <p>При необходимости создаёт новую
     * физическую привязку логического порта.</p>
     *
     * <p>Получение обработчика не означает
     * гарантированную возможность передачи энергии.
     * Каждая операция дополнительно учитывает
     * актуальность привязки, политику порта,
     * запас энергии и доступный бюджет передачи.</p>
     *
     * @param worldPos мировая позиция точки подключения
     * @param side     мировая внешняя сторона порта
     * @return внешний энергетический обработчик
     * или {@code null}, если выдача энергии
     * через указанную точку недоступна
     * @throws NullPointerException  если {@code worldPos}
     *                               равен {@code null}
     * @throws IllegalStateException если для объявленного
     *                               порта отсутствует
     *                               логический обработчик
     */
    public final @Nullable EnergyHandler getEnergyHandlerAt(
        @NonNull BlockPos worldPos,
        @Nullable Direction side
    )
    {
        Objects.requireNonNull(worldPos, "worldPos cannot be null");

        if (!isEnergyRuntimeReady()) {
            return null;
        }

        if (side == null) {
            return null;
        }

        EnergyPortTopology topology = this.configuration.topology();
        Optional<EnergyPortId> portIdOptional = topology.findPort(
            worldPos,
            side,
            this.getBlockPos(),
            this.getFacing()
        );

        if (portIdOptional.isEmpty()) {
            return null;
        }

        EnergyPortId portId = portIdOptional.get();

        if (!this.energyPortPolicy.allowsExtract(portId)) {
            return null;
        }

        EnergyPortTopology.WorldPortPoint worldPoint = new EnergyPortTopology.WorldPortPoint(
            worldPos,
            side
        );

        return this.getOrCreateEnergyEndpointHandler(portId, worldPoint);
    }

    /**
     * Изменяет режим указанного энергетического
     * порта генератора.
     *
     * <p>Изменение допускается только при готовой
     * серверной энергетической подсистеме
     * и наличии изменяемой конфигурации портов.</p>
     *
     * <p>Допустимость нового режима проверяется
     * через политику энергетических портов.</p>
     *
     * <p>При фактическом изменении режима
     * синхронизирует набор доступных портов,
     * помечает блок-сущность как изменённую,
     * обновляет представленные физические точки
     * подключения и инвалидирует затронутые
     * энергетические capabilities.</p>
     *
     * <p>После выполнения внутренних обновлений
     * вызывает {@link #onEnergyPortConfigurationChanged(
     *EnergyPortId)} с идентификатором
     * изменённого порта.</p>
     *
     * <p>Изменение режима допускается независимо
     * от готовности конструкции, редстоун-контроля
     * и физической доступности порта.</p>
     *
     * <p>Не пересоздаёт логический обработчик
     * и самостоятельно не отзывает существующую
     * физическую привязку порта.</p>
     *
     * <p>Если уведомление об изменении завершается
     * исключением, сохранённый режим автоматически
     * не восстанавливается.</p>
     *
     * @param portId  идентификатор настраиваемого порта
     * @param newMode запрашиваемый режим порта
     * @return {@code true}, если режим был изменён;
     * {@code false}, если изменение недоступно,
     * запрещено либо запрашиваемый режим уже установлен
     * @throws NullPointerException если любой аргумент
     *                              равен {@code null}
     */
    public final boolean changeEnergyPortMode(
        @NonNull EnergyPortId portId,
        @NonNull EnergyPortMode newMode
    )
    {
        Objects.requireNonNull(portId, "portId cannot be null");
        Objects.requireNonNull(newMode, "newMode cannot be null");

        if (!this.isEnergyRuntimeReady()) {
            return false;
        }

        if (this.energyPortConfiguration == null) {
            return false;
        }

        return this.energyPortConfiguration.changeMode(portId, newMode, this.energyPortPolicy);
    }

    /**
     * Проверяет, была ли произведена энергия
     * в последнем обработанном серверном тике.
     *
     * <p>Результат определяется на основании
     * текущего показателя производства энергии.</p>
     *
     * <p>Не выполняет повторную проверку условий
     * производства, готовности конструкции,
     * редстоун-сигнала и производственного процесса.</p>
     *
     * <p>При закрытой серверной энергетической
     * подсистеме возвращает {@code false}.</p>
     *
     * @return {@code true}, если текущий показатель
     * производства энергии положительный;
     * иначе {@code false}
     * @see #getCurrentProductionRate()
     */
    public final boolean isProducingEnergy() {
        return this.getCurrentProductionRate() > 0;
    }

    /**
     * Возвращает текущую скорость производства
     * энергии генератором.
     *
     * <p>Показатель соответствует количеству
     * энергии, фактически сохранённой
     * в энергетическом хранилище в рамках
     * последнего обработанного серверного тика.</p>
     *
     * <p>Сбрасывается перед обработкой очередного
     * серверного тика и обновляется после
     * успешного сохранения полной порции
     * произведённой энергии.</p>
     *
     * <p>При изменении готовности конструкции
     * или режима редстоун-контроля показатель
     * самостоятельно не пересчитывается
     * и может сохранять предыдущее значение
     * до следующего серверного тика.</p>
     *
     * <p>При закрытой серверной энергетической
     * подсистеме возвращает {@code 0}.</p>
     *
     * @return текущая скорость производства
     * энергии в FE/t
     */
    public final int getCurrentProductionRate() {
        if (!this.isEnergyRuntimeReady()) {
            return 0;
        }

        return this.currentProductionRate;
    }

    /**
     * Возвращает фактическую скорость
     * выдачи энергии генератором.
     *
     * <p>Показатель определяется как суммарное
     * количество энергии, переданной
     * через все энергетические порты
     * за предыдущий завершённый игровой тик.</p>
     *
     * <p>Учитывает подтверждённые операции
     * активной выдачи (Push) и пассивного
     * извлечения (Pull).</p>
     *
     * <p>Пробные и отменённые транзакции,
     * а также передачи текущего игрового тика
     * не учитываются в возвращаемом значении.</p>
     *
     * <p>Если в предыдущем игровом тике
     * подтверждённые передачи отсутствовали,
     * возвращает {@code 0}.</p>
     *
     * <p>При закрытой серверной энергетической
     * подсистеме возвращает {@code 0}
     * без обращения к игровому времени.</p>
     *
     * @return фактическая скорость выдачи энергии
     * за предыдущий игровой тик в FE/t
     */
    public final int getCurrentTransferRate() {
        if (!this.isEnergyRuntimeReady()) {
            return 0;
        }

        long currentTick = this.getCurrentGameTime();
        return this.transferTracker.getCurrentTransferRate(currentTick);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Сохраняет общее сериализуемое
     * состояние генератора.</p>
     *
     * <p>Последовательно сохраняет:</p>
     *
     * <ol>
     *     <li>состояние энергетического хранилища
     *     посредством родительского класса;</li>
     *     <li>режимы настраиваемых
     *     энергетических портов;</li>
     *     <li>текущий режим редстоун-контроля;</li>
     *     <li>состояние производственного процесса,
     *     если он реализует
     *     {@link PersistentGenerationProcess}.</li>
     * </ol>
     *
     * <p>Состояние производственного процесса
     * сохраняется в отдельной дочерней секции.</p>
     *
     * <p>Для генераторов с фиксированными
     * энергетическими портами их режимы
     * не сохраняются.</p>
     *
     * <p>Для производственных процессов,
     * не реализующих контракт сериализации,
     * отдельная секция состояния
     * не создаётся.</p>
     *
     * <p>Серверная энергетическая подсистема,
     * логические и внешние обработчики,
     * физические привязки, снимки представленных
     * портов, бюджеты передачи, состояние
     * распределителя и текущая статистика
     * не сериализуются.</p>
     *
     * <p>Готовность конструкции не сохраняется
     * базовым классом и должна определяться
     * соответствующим механизмом конструкции.</p>
     *
     * <p>Метод не требует открытия серверной
     * энергетической подсистемы, но требует
     * предварительной привязки
     * производственного процесса.</p>
     *
     * <p>Не выполняет производство и передачу
     * энергии, открытие энергетической
     * подсистемы и изменение конфигурации.</p>
     *
     * <p>Наследники, сохраняющие дополнительное
     * состояние генератора, обязаны вызывать
     * данную реализацию.</p>
     *
     * @param output контекст сохранения
     *               сериализуемого состояния
     * @throws NullPointerException  если {@code output}
     *                               равен {@code null}
     * @throws IllegalStateException если производственный
     *                               процесс не привязан
     */
    @Override
    protected void saveAdditional(@NonNull ValueOutput output) {
        Objects.requireNonNull(output, "output cannot be null");
        super.saveAdditional(output);

        GenerationProcess process = this.requireGenerationProcess();

        this.saveEnergyPorts(output);
        RedstoneControlSerializer.save(output, this.redstoneController.getRedstoneControlMode());

        if (process instanceof PersistentGenerationProcess persistentProcess) {
            ValueOutput processOutput = output.child(GENERATION_PROCESS_KEY);
            persistentProcess.saveState(processOutput);
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>Восстанавливает общее сериализуемое
     * состояние генератора.</p>
     *
     * <p>Последовательно восстанавливает:</p>
     *
     * <ol>
     *     <li>состояние энергетического хранилища
     *     посредством родительского класса;</li>
     *     <li>режимы настраиваемых
     *     энергетических портов;</li>
     *     <li>режим редстоун-контроля;</li>
     *     <li>состояние производственного процесса,
     *     если он реализует
     *     {@link PersistentGenerationProcess}.</li>
     * </ol>
     *
     * <p>Для сериализуемого производственного
     * процесса восстановление выполняется
     * независимо от наличия соответствующей
     * дочерней секции сохранённых данных.</p>
     *
     * <p>При отсутствии корректной дочерней
     * секции процессу предоставляется
     * пустой контекст восстановления,
     * позволяющий применить предусмотренное
     * им начальное состояние.</p>
     *
     * <p>Для генераторов с фиксированными
     * энергетическими портами сохранённые
     * режимы портов не используются.</p>
     *
     * <p>Восстановление выполняется без
     * обычных уведомлений об изменении
     * энергетических настроек, запуска
     * производственного процесса и передачи
     * энергии.</p>
     *
     * <p>Метод не открывает серверную
     * энергетическую подсистему, не создаёт
     * физические привязки портов,
     * не синхронизирует набор доступных
     * портов и не инвалидирует
     * энергетические capabilities.</p>
     *
     * <p>При штатной серверной загрузке
     * первоначальная синхронизация
     * энергетической подсистемы выполняется
     * посредством {@link #onLoad()}.</p>
     *
     * <p>Предназначен для штатного восстановления
     * сериализуемого состояния блок-сущности,
     * включая загрузку сохранённых данных
     * и клиентское получение сериализуемого
     * состояния.</p>
     *
     * <p>Горячее восстановление состояния
     * работающего серверного генератора
     * посредством внешней модификации NBT
     * не поддерживается. Для изменения
     * настроек работающего генератора
     * необходимо использовать соответствующий
     * публичный API.</p>
     *
     * <p>Наследники, восстанавливающие
     * дополнительное состояние генератора,
     * обязаны вызывать данную реализацию.</p>
     *
     * @param input контекст восстановления
     *              сериализуемого состояния
     * @throws NullPointerException  если {@code input}
     *                               равен {@code null}
     * @throws IllegalStateException если производственный
     *                               процесс не привязан
     */
    @Override
    protected void loadAdditional(@NonNull ValueInput input) {
        Objects.requireNonNull(input, "input cannot be null");
        super.loadAdditional(input);

        GenerationProcess process = this.requireGenerationProcess();

        this.loadEnergyPorts(input);

        RedstoneControlMode mode = RedstoneControlSerializer.load(input);
        this.redstoneController.restoreMode(mode);

        if (process instanceof PersistentGenerationProcess persistentProcess) {
            ValueInput processInput = input.childOrEmpty(GENERATION_PROCESS_KEY);
            persistentProcess.restoreState(processInput);
        }
    }

    /**
     * Определяет готовность конструкции
     * генератора к работе.
     *
     * <p>Базовая реализация предназначена
     * для одноблочных генераторов и всегда
     * возвращает {@code true}.</p>
     *
     * <p>Наследники могут переопределить метод
     * для проверки готовности многоблочной
     * или иной составной конструкции.</p>
     *
     * <p>При неготовой конструкции блокируется
     * производство энергии и становятся
     * недоступными энергетические порты.</p>
     *
     * <p>Готовность конструкции не определяет
     * готовность серверной энергетической
     * подсистемы, не учитывает редстоун-контроль,
     * наличие топлива и свободного места
     * в энергетическом хранилище.</p>
     *
     * <p>Изменение готовности конструкции
     * само по себе не должно приводить
     * к сбросу накопленной энергии,
     * настроек генератора и состояния
     * производственного процесса.</p>
     *
     * <p>Метод должен предоставлять актуальное
     * состояние конструкции без побочных эффектов
     * и принудительной загрузки чанков.</p>
     *
     * <p>При изменении состояния готовности
     * наследник должен уведомить базовый класс
     * через {@link #onEnergyPortAvailabilityChanged()}.</p>
     *
     * @return {@code true}, если конструкция
     * готова к работе; иначе {@code false}
     */
    protected boolean isStructureReady() {
        return true;
    }

    /**
     * Читает актуальный редстоун-сигнал,
     * поступающий на генератор.
     *
     * <p>Базовая реализация проверяет наличие
     * сигнала от соседних блоков относительно
     * позиции блок-сущности генератора.</p>
     *
     * <p>При отсутствии серверного мира
     * возвращает {@code false}.</p>
     *
     * <p>Наследники могут переопределить метод
     * для использования других источников
     * редстоун-сигнала, например специального
     * управляющего порта многоблочной конструкции.</p>
     *
     * <p>Реализация должна предоставлять актуальное
     * состояние сигнала и не изменять режим
     * редстоун-контроля, энергетическое хранилище
     * и состояние производственного процесса.</p>
     *
     * @return {@code true}, если присутствует
     * активный редстоун-сигнал; иначе {@code false}
     */
    protected boolean readRedstoneSignal() {
        if (!(this.level instanceof ServerLevel serverLevel)) {
            return false;
        }

        return serverLevel.hasNeighborSignal(this.getBlockPos());
    }

    /**
     * Проверяет физическую доступность
     * указанной точки энергетического порта.
     *
     * <p>Базовая реализация поддерживает порты,
     * расположенные непосредственно на блоке
     * генератора, то есть имеющие нулевое
     * локальное смещение.</p>
     *
     * <p>Наследники могут переопределить метод
     * для проверки доступности удалённых
     * физических точек подключения, например
     * энергетических портов многоблочной
     * конструкции.</p>
     *
     * <p>Метод не определяет режим порта,
     * готовность серверной энергетической
     * подсистемы, общую готовность конструкции
     * и доступный бюджет передачи энергии.</p>
     *
     * <p>Реализация должна выполнять проверку
     * без изменения состояния генератора
     * и принудительной загрузки чанков.</p>
     *
     * <p>При изменении физической доступности
     * порта наследник должен уведомить
     * базовый класс посредством
     * {@link #onEnergyPortAvailabilityChanged()}.</p>
     *
     * <p>Если изменилась идентичность физической
     * точки подключения, а не только
     * её доступность, необходимо использовать
     * {@link #onEnergyPortEndpointChanged(EnergyPortId)}.</p>
     *
     * @param point локальное описание
     *              энергетического порта
     * @return {@code true}, если физическая точка
     * подключения доступна; иначе {@code false}
     * @throws NullPointerException если {@code point}
     *                              равен {@code null}
     */
    protected boolean isEnergyPortPhysicallyAvailable(
        @NonNull EnergyPortPoint point
    )
    {
        Objects.requireNonNull(point, "point cannot be null");

        return point.localOffset().equals(BlockPos.ZERO);
    }

    /**
     * Уведомляет наследника о фактическом
     * изменении режима энергетического порта.
     *
     * <p>Вызывается после сохранения нового
     * режима, синхронизации доступных портов,
     * пометки блок-сущности как изменённой
     * и обновления представленных внешним
     * потребителям точек подключения.</p>
     *
     * <p>Новый режим доступен для чтения
     * через {@link #getEnergyPortMode(EnergyPortId)}.</p>
     *
     * <p>Не вызывается при повторной установке
     * текущего режима, отклонённом изменении
     * или восстановлении сериализуемых настроек.</p>
     *
     * <p>Базовая реализация не выполняет
     * дополнительных операций.</p>
     *
     * <p>Наследники могут использовать метод
     * для обновления дополнительного состояния,
     * визуального представления и организации
     * необходимой клиентской синхронизации.</p>
     *
     * @param portId идентификатор энергетического
     *               порта, режим которого изменился
     */
    protected void onEnergyPortConfigurationChanged(
        @NonNull EnergyPortId portId
    )
    {}

    /**
     * Уведомляет наследника о результате
     * производства энергии в текущем
     * серверном тике.
     *
     * <p>Вызывается после штатного завершения
     * попытки производства независимо от того,
     * была произведена энергия или нет.</p>
     *
     * <p>При успешном производстве вызывается
     * после сохранения полной порции энергии,
     * обновления показателя производства
     * и уведомления производственного процесса
     * о завершении производственного тика.</p>
     *
     * <p>Уведомление выполняется до возможного
     * второго прохода активной выдачи энергии.</p>
     *
     * <p>Метод может вызываться несколько
     * последовательных игровых тиков
     * с одним и тем же значением параметра.</p>
     *
     * <p>Не является уведомлением исключительно
     * об изменении состояния производства.</p>
     *
     * <p>Базовая реализация не выполняет
     * дополнительных операций.</p>
     *
     * <p>Наследники могут использовать метод
     * для обновления производственного
     * и визуального состояния генератора,
     * не изменяя общий алгоритм производства
     * и передачи энергии.</p>
     *
     * @param producing {@code true}, если в текущем
     *                  серверном тике успешно произведена
     *                  энергия; иначе {@code false}
     */
    protected void onProductionStateUpdated(boolean producing) {}

    /**
     * Выполняет один серверный тик генератора.
     *
     * <p>При закрытой серверной энергетической
     * подсистеме завершает выполнение без изменения
     * состояния и вызова производственного процесса.</p>
     *
     * <p>При готовой энергетической подсистеме
     * выполняет следующие операции:</p>
     *
     * <ol>
     *     <li>проверяет наличие обязательного
     *     производственного процесса;</li>
     *     <li>сбрасывает показатель текущей
     *     скорости производства энергии;</li>
     *     <li>выполняет первый проход активной
     *     выдачи накопленной энергии;</li>
     *     <li>выполняет попытку производства
     *     полной порции энергии;</li>
     *     <li>уведомляет наследника о результате
     *     производства;</li>
     *     <li>при успешном производстве и наличии
     *     остатка общего бюджета выполняет
     *     второй проход активной выдачи энергии.</li>
     * </ol>
     *
     * <p>Первый проход активной выдачи позволяет
     * освободить место в энергетическом хранилище
     * до проверки возможности производства.</p>
     *
     * <p>Второй проход позволяет передать
     * потребителям только что произведённую
     * энергию в пределах оставшегося бюджета.</p>
     *
     * <p>Оба прохода активной выдачи и внешние
     * операции пассивного извлечения используют
     * единый бюджет передачи текущего игрового тика.</p>
     *
     * <p>Запрет производства редстоун-контролем
     * не препятствует первому проходу выдачи
     * ранее накопленной энергии.</p>
     *
     * <p>При неготовой конструкции производство
     * и передача энергии блокируются соответствующими
     * проверками готовности и доступности портов.</p>
     *
     * <p>Метод должен вызываться серверным ticker
     * один раз за игровой тик. Самостоятельная
     * регистрация ticker и защита от повторного
     * вызова в пределах одного игрового тика
     * не выполняются.</p>
     *
     * <p>Выполнение всего серверного тика
     * не является единой транзакцией.
     * Исключения производственного процесса
     * и других компонентов передаются
     * вызывающему коду.</p>
     *
     * @throws IllegalStateException если производственный
     *                               процесс не привязан
     *                               либо нарушен инвариант
     *                               производства энергии
     */
    protected final void serverTick() {
        if (!this.isEnergyRuntimeReady()) {
            return;
        }
        this.requireGenerationProcess();
        this.currentProductionRate = 0;

        this.pushEnergyToNeighbors();

        boolean producing = this.tryProduceEnergy();
        this.onProductionStateUpdated(producing);

        if (producing) {
            long currentGameTime = this.getCurrentGameTime();
            int remainingBudget = this.outputBudget.getRemaining(currentGameTime);

            if (remainingBudget > 0) {
                this.pushEnergyToNeighbors();
            }
        }
    }

    /**
     * Уведомляет базовый генератор об изменении
     * доступности энергетических портов.
     *
     * <p>Предназначен для вызова наследниками
     * после изменения готовности конструкции
     * или физической доступности отдельных
     * энергетических портов.</p>
     *
     * <p>При готовой серверной энергетической
     * подсистеме синхронизирует набор
     * доступных портов, обновляет снимок
     * представленных физических точек подключения
     * и инвалидирует затронутые capabilities.</p>
     *
     * <p>При закрытой серверной энергетической
     * подсистеме завершает выполнение
     * без дополнительных операций.</p>
     *
     * <p>Не изменяет сохранённые режимы портов
     * и самостоятельно не отзывает существующие
     * физические привязки.</p>
     *
     * <p>Должен использоваться в случаях,
     * когда идентичность физической точки
     * подключения сохраняется, но изменяется
     * возможность её использования.</p>
     *
     * @see #onEnergyPortEndpointChanged(EnergyPortId)
     */
    protected final void onEnergyPortAvailabilityChanged() {
        if (!isEnergyRuntimeReady()) {
            return;
        }

        this.synchronizeAvailableEnergyPorts();
        this.synchronizeExposedEnergyPorts(Collections.emptySet());
    }

    /**
     * Уведомляет базовый генератор об изменении
     * физической привязки энергетического порта.
     *
     * <p>Предназначен для случаев, когда прежняя
     * физическая точка подключения больше
     * не должна считаться действительной,
     * даже если её мировая позиция
     * и внешняя сторона остались прежними.</p>
     *
     * <p>Например, при замене физического блока
     * энергетического порта многоблочной
     * конструкции.</p>
     *
     * <p>При готовой серверной энергетической
     * подсистеме и известном идентификаторе
     * порта отзывает его текущую физическую
     * привязку.</p>
     *
     * <p>После отзыва синхронизирует набор
     * доступных портов и представленные
     * физические точки подключения.</p>
     *
     * <p>Если порт ранее был представлен
     * внешним потребителям, его прежняя
     * мировая позиция дополнительно
     * инвалидируется независимо от совпадения
     * предыдущей и новой точки подключения.</p>
     *
     * <p>Ранее выданный внешний обработчик
     * остаётся недействительным после создания
     * новой физической привязки.</p>
     *
     * <p>При закрытой серверной энергетической
     * подсистеме или неизвестном идентификаторе
     * порта завершает выполнение
     * без дополнительных операций.</p>
     *
     * <p>Не изменяет логический обработчик,
     * режим порта, накопленную энергию
     * и общий бюджет передачи.</p>
     *
     * @param portId идентификатор энергетического порта,
     *               физическая привязка которого изменилась
     * @throws NullPointerException если {@code portId}
     *                              равен {@code null}
     * @see #onEnergyPortAvailabilityChanged()
     */
    protected final void onEnergyPortEndpointChanged(
        @NonNull EnergyPortId portId
    )
    {
        Objects.requireNonNull(portId, "portId cannot be null");
        if (!this.isEnergyRuntimeReady()) {
            return;
        }

        if (!this.energyPortHandlers.containsKey(portId)) {
            return;
        }

        this.energyEndpointBindings.remove(portId);

        EnergyPortTopology.WorldPortPoint previousPoint = this.exposedEnergyPorts.get(portId);
        Set<BlockPos> additionalPositions = previousPoint == null ? Collections.emptySet() : Set.of(
            previousPoint.worldPos());

        this.synchronizeAvailableEnergyPorts();
        this.synchronizeExposedEnergyPorts(additionalPositions);
    }

    /**
     * Однократно привязывает конкретный
     * производственный процесс к генератору.
     *
     * <p>Метод предназначен для вызова наследником
     * после создания всех компонентов, необходимых
     * производственному процессу.</p>
     *
     * <p>Привязка должна быть выполнена до вызова
     * методов загрузки, сохранения и открытия
     * серверной энергетической подсистемы.</p>
     *
     * <p>Повторная привязка запрещена независимо
     * от того, передаётся тот же экземпляр
     * производственного процесса или другой.</p>
     *
     * <p>Метод не запускает производственный процесс,
     * не выполняет восстановление его состояния
     * и не открывает энергетическую подсистему.</p>
     *
     * @param generationProcess производственный процесс
     *                          конкретного генератора
     * @throws NullPointerException  если переданный
     *                               процесс равен {@code null}
     * @throws IllegalStateException если производственный
     *                               процесс уже привязан
     */
    protected final void bindGenerationProcess(
        @NonNull GenerationProcess generationProcess
    )
    {
        Objects.requireNonNull(generationProcess, "generationProcess cannot be null");
        if (this.generationProcess != null) {
            throw new IllegalStateException("Generation process is already bound");
        }

        this.generationProcess = generationProcess;
    }

    /**
     * Возвращает текущую горизонтальную
     * ориентацию генератора.
     *
     * <p>Если состояние блока содержит свойство
     * {@link BlockStateProperties#HORIZONTAL_FACING},
     * возвращает значение данного свойства.</p>
     *
     * <p>Для блоков без свойства горизонтальной
     * ориентации используется каноническое
     * направление {@link Direction#NORTH}.</p>
     *
     * <p>Полученное направление используется
     * при преобразовании локальных точек
     * энергетических портов в мировые.</p>
     *
     * @return текущая горизонтальная
     * ориентация генератора
     */
    protected final @NonNull Direction getFacing() {
        BlockState state = this.getBlockState();
        if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            return state.getValue(BlockStateProperties.HORIZONTAL_FACING);
        }

        return Direction.NORTH;
    }

    /**
     * Преобразует описание логического
     * энергетического порта в мировую
     * точку подключения.
     *
     * <p>При разрешении учитываются топология
     * генератора, текущая позиция блок-сущности
     * и её горизонтальная ориентация.</p>
     *
     * <p>Мировая точка подключения содержит
     * абсолютную позицию порта и его мировую
     * внешнюю сторону.</p>
     *
     * <p>Не проверяет текущий режим порта,
     * готовность конструкции, физическую
     * доступность и наличие энергетических
     * capabilities в мире.</p>
     *
     * @param portId идентификатор энергетического порта
     * @return мировая точка подключения порта
     * или пустой {@link Optional},
     * если порт отсутствует в топологии
     * @throws NullPointerException если {@code portId}
     *                              равен {@code null}
     */
    private @NonNull Optional<EnergyPortTopology.WorldPortPoint> resolveEnergyPort(
        @NonNull EnergyPortId portId
    )
    {
        EnergyPortTopology topology = this.configuration.topology();

        return topology.resolvePort(portId, this.getBlockPos(), this.getFacing());
    }

    /**
     * Возвращает обязательный производственный
     * процесс генератора.
     *
     * <p>Используется внутренними операциями,
     * выполнение которых требует предварительной
     * инициализации производственного процесса.</p>
     *
     * <p>Отсутствие производственного процесса
     * рассматривается как нарушение контракта
     * инициализации генератора.</p>
     *
     * @return привязанный производственный процесс
     * @throws IllegalStateException если производственный
     *                               процесс не привязан
     */
    private @NonNull GenerationProcess requireGenerationProcess() {
        if (this.generationProcess == null) {
            throw new IllegalStateException("Generation process is not bound");
        }

        return this.generationProcess;
    }

    /**
     * Выполняет попытку производства энергии
     * за один производственный тик.
     *
     * <p>Перед подготовкой производственного
     * процесса последовательно проверяет:</p>
     *
     * <ol>
     *     <li>готовность конструкции генератора;</li>
     *     <li>наличие свободного места
     *     для полной порции производимой энергии;</li>
     *     <li>разрешение производства
     *     редстоун-контролем.</li>
     * </ol>
     *
     * <p>Если любое из общих условий не выполнено,
     * производственный процесс не подготавливается
     * и энергия не производится.</p>
     *
     * <p>После успешной проверки общих условий
     * вызывает {@link GenerationProcess#prepareProductionTick()}.</p>
     *
     * <p>Если производственный процесс не готов
     * к производству, завершает выполнение
     * без записи произведённой энергии.</p>
     *
     * <p>При успешной подготовке сохраняет
     * полную порцию производимой энергии
     * в общем энергетическом хранилище.</p>
     *
     * <p>Фактическое количество сохранённой энергии
     * должно совпадать с заданной производительностью.
     * Частичное производство рассматривается
     * как нарушение внутреннего инварианта
     * генератора.</p>
     *
     * <p>После успешного сохранения полной порции
     * обновляет показатель текущего производства
     * и однократно вызывает
     * {@link GenerationProcess#onProductionTickCompleted()}.</p>
     *
     * <p>Не проверяет остаток бюджета выдачи энергии.
     * Производство может выполняться при полностью
     * исчерпанном бюджете передачи, если
     * в хранилище достаточно свободного места.</p>
     *
     * <p>Подготовка, сохранение энергии
     * и завершение производственного тика
     * не объединяются в общую транзакцию.
     * При нарушении инварианта или возникновении
     * исключения ранее выполненные изменения
     * автоматически не откатываются.</p>
     *
     * <p>Предназначен для внутреннего вызова
     * при обработке серверного тика
     * с готовой энергетической подсистемой.</p>
     *
     * @return {@code true}, если производственный
     * тик успешно завершён; {@code false},
     * если производство было пропущено
     * @throws IllegalStateException если производственный
     *                               процесс не привязан
     *                               либо фактически сохранено
     *                               непредусмотренное
     *                               количество энергии
     */
    private boolean tryProduceEnergy() {
        if (!this.isStructureReady()) {
            return false;
        }

        int generationPerTick = this.configuration.energy().generationPerTick();

        long capacity = this.getEnergyCapacity();
        long storedEnergy = this.getStoredEnergy();
        long freeSpace = capacity - storedEnergy;

        if (freeSpace < generationPerTick) {
            return false;
        }

        if (this.isRedstoneBlockingOperation()) {
            return false;
        }

        GenerationProcess process = this.requireGenerationProcess();
        if (!process.prepareProductionTick()) {
            return false;
        }

        EnergyComponent component = this.getEnergyComponent();
        int producedEnergy = component.storeProducedEnergy(generationPerTick);

        if (producedEnergy != generationPerTick) {
            throw new IllegalStateException(
                "Unexpected energy production amount: expected %d FE, but stored %d FE".formatted(
                    generationPerTick,
                    producedEnergy
                ));
        }

        this.currentProductionRate = producedEnergy;
        process.onProductionTickCompleted();

        return true;
    }

    /**
     * Проверяет наличие корректного серверного
     * контекста блок-сущности.
     *
     * <p>Серверный контекст считается корректным,
     * если блок-сущность связана с серверным миром
     * и не помечена как удалённая.</p>
     *
     * <p>Метод не проверяет открытие энергетической
     * подсистемы, готовность конструкции, наличие
     * блок-сущности в чанке и актуальность
     * физических привязок портов.</p>
     *
     * @return {@code true}, если доступен корректный
     * серверный контекст; иначе {@code false}
     */
    private boolean hasServerContext() {
        return this.level instanceof ServerLevel && !this.isRemoved();
    }

    /**
     * Проверяет готовность серверной
     * энергетической подсистемы генератора.
     *
     * <p>Подсистема считается готовой, если
     * присутствует корректный серверный контекст
     * и выполнено её открытие.</p>
     *
     * <p>Не учитывает готовность конструкции,
     * физическую доступность отдельных портов,
     * редстоун-сигнал и наличие ресурсов
     * для производства энергии.</p>
     *
     * @return {@code true}, если серверная
     * энергетическая подсистема открыта
     * и доступна для работы; иначе {@code false}
     */
    private boolean isEnergyRuntimeReady() {
        return this.hasServerContext() && this.energyRuntimeReady;
    }

    /**
     * Определяет общую доступность указанного
     * логического энергетического порта.
     *
     * <p>Порт считается доступным при одновременном
     * выполнении следующих условий:</p>
     *
     * <ul>
     *     <li>серверная энергетическая подсистема
     *     генератора готова;</li>
     *     <li>конструкция генератора готова;</li>
     *     <li>порт присутствует в топологии
     *     генератора;</li>
     *     <li>соответствующая физическая точка
     *     подключения доступна.</li>
     * </ul>
     *
     * <p>Не учитывает текущий режим энергетического
     * порта, поскольку проверка доступности
     * является отдельной составляющей
     * политики энергетических портов.</p>
     *
     * <p>Не учитывает редстоун-контроль,
     * накопленную энергию, внешний спрос
     * и остаток бюджета передачи.</p>
     *
     * @param portId идентификатор энергетического порта
     * @return {@code true}, если порт доступен;
     * иначе {@code false}
     * @throws NullPointerException если {@code portId}
     *                              равен {@code null}
     */
    private boolean isEnergyPortAvailable(
        @NonNull EnergyPortId portId
    )
    {
        Objects.requireNonNull(portId, "portId cannot be null");

        if (!isEnergyRuntimeReady()) {
            return false;
        }

        if (!this.isStructureReady()) {
            return false;
        }

        EnergyPortTopology topology = this.configuration.topology();
        Optional<EnergyPortPoint> point = topology.points().stream().filter(p -> p.portId()
            .equals(portId)).findFirst();

        return point.filter(this::isEnergyPortPhysicallyAvailable).isPresent();
    }

    /**
     * Проверяет актуальность конкретной
     * физической привязки энергетического порта.
     *
     * <p>Привязка считается действительной
     * при одновременном выполнении
     * следующих условий:</p>
     *
     * <ul>
     *     <li>серверная энергетическая подсистема
     *     генератора готова;</li>
     *     <li>проверяемый объект является текущей
     *     физической привязкой данного порта;</li>
     *     <li>генератор находится в серверном мире;</li>
     *     <li>чанк, содержащий блок-сущность
     *     генератора, загружен;</li>
     *     <li>в позиции генератора находится
     *     данный экземпляр блок-сущности;</li>
     *     <li>текущая мировая точка подключения
     *     порта совпадает с точкой,
     *     сохранённой в привязке.</li>
     * </ul>
     *
     * <p>Актуальность привязки проверяется
     * по идентичности объекта, а не только
     * по идентификатору порта и мировой
     * точке подключения.</p>
     *
     * <p>Это предотвращает восстановление
     * ранее отозванных обработчиков после
     * создания новой привязки в той же позиции.</p>
     *
     * <p>Проверка не инициирует принудительную
     * загрузку чанка генератора.</p>
     *
     * <p>Не проверяет текущий режим порта,
     * готовность конструкции и возможность
     * конкретной энергетической операции.
     * Эти условия контролируются политикой
     * логического порта.</p>
     *
     * <p>Для удалённых физических портов
     * дополнительная проверка физического
     * состояния точки подключения является
     * ответственностью соответствующего наследника
     * и механизма уведомления об изменении endpoint.</p>
     *
     * @param binding проверяемая физическая привязка
     * @return {@code true}, если привязка актуальна;
     * иначе {@code false}
     * @throws NullPointerException если {@code binding}
     *                              равен {@code null}
     */
    private boolean isEnergyEndpointValid(
        @NonNull EnergyEndpointBinding binding
    )
    {
        Objects.requireNonNull(binding, "binding cannot be null");

        if (!this.isEnergyRuntimeReady()) {
            return false;
        }

        if (this.energyEndpointBindings.get(binding.portId) != binding) {
            return false;
        }

        if (!(this.level instanceof ServerLevel serverLevel)) {
            return false;
        }

        BlockPos pos = this.getBlockPos();
        int chunkX = pos.getX() >> 4;
        int chunkZ = pos.getZ() >> 4;

        if (!serverLevel.getChunkSource().hasChunk(chunkX, chunkZ)) {
            return false;
        }

        BlockEntity blockEntity = serverLevel.getBlockEntity(pos);
        if (blockEntity != this) {
            return false;
        }

        Optional<EnergyPortTopology.WorldPortPoint> currentPoint =
            this.resolveEnergyPort(binding.portId);

        return currentPoint.filter(binding.worldPoint::equals).isPresent();
    }

    /**
     * Обрабатывает фактическое изменение
     * режима энергетического порта.
     *
     * <p>Вызывается изменяемой конфигурацией
     * после успешного сохранения нового
     * режима порта.</p>
     *
     * <p>Последовательно выполняет:</p>
     *
     * <ol>
     *     <li>синхронизацию набора доступных
     *     энергетических портов;</li>
     *     <li>пометку блок-сущности как изменённой;</li>
     *     <li>синхронизацию представленных
     *     физических точек подключения
     *     и инвалидацию затронутых capabilities;</li>
     *     <li>уведомление наследника об изменении
     *     режима энергетического порта.</li>
     * </ol>
     *
     * <p>На момент вызова уведомления наследника
     * новый режим уже сохранён, а внутренние
     * энергетические компоненты синхронизированы.</p>
     *
     * <p>Не вызывается при восстановлении
     * сериализуемых настроек генератора.</p>
     *
     * <p>Не пересоздаёт логические обработчики
     * и самостоятельно не отзывает
     * физические привязки энергетических портов.</p>
     *
     * @param portId идентификатор изменённого порта
     * @throws NullPointerException если {@code portId}
     *                              равен {@code null}
     */
    private void handleEnergyPortConfigurationChanged(
        @NonNull EnergyPortId portId
    )
    {
        Objects.requireNonNull(portId, "portId cannot be null");

        this.synchronizeAvailableEnergyPorts();
        this.setChanged();
        this.synchronizeExposedEnergyPorts(Collections.emptySet());
        this.onEnergyPortConfigurationChanged(portId);
    }

    /**
     * Возвращает текущий игровой тик
     * серверного мира генератора.
     *
     * <p>Используется энергетическими компонентами
     * для определения общего бюджета передачи,
     * распределения энергии и учёта статистики.</p>
     *
     * <p>Доступен только при открытой серверной
     * энергетической подсистеме.</p>
     *
     * @return текущий игровой тик серверного мира
     * @throws IllegalStateException если серверная
     *                               энергетическая
     *                               подсистема не готова
     */
    private long getCurrentGameTime() {
        if (!this.isEnergyRuntimeReady()) {
            throw new IllegalStateException("Generator energy runtime is not ready");
        }

        return Objects.requireNonNull(this.level, "level cannot be null").getGameTime();
    }

    /**
     * Определяет наблюдаемую возможность
     * приёма энергии внешним энергетическим
     * обработчиком.
     *
     * <p>Выполняет пробную вставку энергии
     * в количестве, равном максимальному
     * общему бюджету выдачи генератора
     * за игровой тик.</p>
     *
     * <p>Пробная вставка выполняется внутри
     * отдельной корневой транзакции,
     * которая не подтверждается.</p>
     *
     * <p>При закрытии транзакции изменения
     * транзакционного состояния внешнего
     * энергетического обработчика откатываются.</p>
     *
     * <p>Полученное количество используется
     * как наблюдение потенциального
     * спроса внешнего потребителя,
     * а не как фактически переданная энергия.</p>
     *
     * <p>Не извлекает энергию из хранилища
     * генератора, не расходует его бюджет
     * передачи и не регистрирует фактическую
     * выдачу энергии.</p>
     *
     * <p>Проба не ограничивается текущим
     * запасом энергии генератора
     * или остатком его бюджета передачи.</p>
     *
     * <p>Результат не гарантирует возможность
     * передачи того же количества энергии
     * при последующей фактической операции.</p>
     *
     * <p>Корректность отката зависит от соблюдения
     * внешним энергетическим обработчиком
     * транзакционного контракта NeoForge.</p>
     *
     * @param target внешний энергетический обработчик
     * @return количество энергии, принятое
     * в рамках пробной транзакции
     * @throws NullPointerException если {@code target}
     *                              равен {@code null}
     */
    private int probeEnergyAcceptance(
        @NonNull EnergyHandler target
    )
    {
        Objects.requireNonNull(target, "target cannot be null");

        int maxOutputPerTick = this.configuration.energy().maxOutputPerTick();

        try (Transaction transaction = Transaction.openRoot()) {
            return target.insert(maxOutputPerTick, transaction);
        }
    }

    /**
     * Создаёт логические энергетические
     * обработчики всех объявленных портов
     * генератора.
     *
     * <p>Каждому идентификатору энергетического
     * порта соответствует отдельный
     * логический обработчик.</p>
     *
     * <p>Все обработчики используют общую
     * политику энергетических портов
     * и единый диспетчер энергетических операций.</p>
     *
     * <p>Обработчики создаются независимо
     * от начального режима порта,
     * его физической доступности
     * и готовности серверной подсистемы.</p>
     *
     * <p>Создание логических обработчиков
     * не приводит к созданию физических
     * привязок и не предоставляет
     * энергетические capabilities.</p>
     *
     * <p>Возвращаемая карта является
     * неизменяемой.</p>
     *
     * @param dispatcher общий диспетчер
     *                   энергетических операций
     * @return неизменяемая карта логических
     * обработчиков энергетических портов
     */
    private @NonNull @Unmodifiable Map<EnergyPortId, EnergyPortHandler> createEnergyPortHandlers(
        @NonNull EnergyPortDispatcher dispatcher
    )
    {
        Map<EnergyPortId, EnergyPortHandler> handlers = new HashMap<>();

        for (EnergyPortId portId : this.configuration.portRules().portIds()) {
            handlers.put(portId, new EnergyPortHandler(portId, this.energyPortPolicy, dispatcher));
        }

        return Map.copyOf(handlers);
    }

    /**
     * Формирует актуальный снимок энергетических
     * портов, представляемых внешним потребителям.
     *
     * <p>В снимок включаются только объявленные
     * логические порты, для которых политика
     * разрешает выдачу энергии.</p>
     *
     * <p>Каждый включённый порт разрешается
     * в мировую точку подключения с учётом
     * текущей позиции и ориентации генератора.</p>
     *
     * <p>Если серверная энергетическая подсистема
     * не готова, возвращает пустую карту.</p>
     *
     * <p>Отсутствие мировой точки подключения
     * для доступного порта рассматривается
     * как нарушение согласованности конфигурации
     * и топологии генератора.</p>
     *
     * <p>Метод не создаёт внешние энергетические
     * обработчики, не выполняет поиск capabilities
     * и не изменяет сохранённый снимок
     * представленных портов.</p>
     *
     * @return неизменяемый актуальный снимок
     * представленных энергетических портов
     * @throws IllegalStateException если доступный
     *                               порт невозможно
     *                               разрешить в мировую
     *                               точку подключения
     */
    private @NonNull @Unmodifiable Map<EnergyPortId, EnergyPortTopology.WorldPortPoint> collectExposedEnergyPorts() {
        if (!this.isEnergyRuntimeReady()) {
            return Collections.emptyMap();
        }

        Map<EnergyPortId, EnergyPortTopology.WorldPortPoint> points = new HashMap<>();

        for (EnergyPortId portId : this.configuration.portRules().portIds()) {
            if (!this.energyPortPolicy.allowsExtract(portId)) {
                continue;
            }

            EnergyPortTopology.WorldPortPoint worldPoint =
                this.resolveEnergyPort(portId).orElseThrow(() -> new IllegalStateException(
                    "Unable to resolve world point for energy port: " + portId));

            points.put(portId, worldPoint);
        }

        return Map.copyOf(points);
    }

    /**
     * Определяет мировые позиции, затронутые
     * изменением представленных энергетических
     * портов генератора.
     *
     * <p>Сравнивает предыдущий и текущий снимки
     * мировых точек подключения по идентификаторам
     * логических энергетических портов.</p>
     *
     * <p>В результирующее множество включаются:</p>
     *
     * <ul>
     *     <li>предыдущие позиции исчезнувших портов;</li>
     *     <li>текущие позиции появившихся портов;</li>
     *     <li>предыдущие и текущие позиции портов,
     *     мировые точки подключения которых
     *     изменились.</li>
     * </ul>
     *
     * <p>Изменением мировой точки подключения
     * считается изменение мировой позиции
     * или внешней стороны порта.</p>
     *
     * <p>Если несколько изменений относятся
     * к одной мировой позиции, она включается
     * в результат только один раз.</p>
     *
     * <p>Метод не изменяет переданные снимки,
     * не обращается к игровому миру
     * и самостоятельно не выполняет
     * инвалидацию capabilities.</p>
     *
     * @param previous предыдущий снимок
     *                 представленных энергетических портов
     * @param current  текущий снимок
     *                 представленных энергетических портов
     * @return неизменяемое множество затронутых
     * мировых позиций
     * @throws NullPointerException если любой аргумент
     *                              равен {@code null}
     */
    private static @NonNull @Unmodifiable Set<BlockPos> collectChangedEnergyPortPositions(
        @NonNull Map<EnergyPortId, EnergyPortTopology.WorldPortPoint> previous,
        @NonNull Map<EnergyPortId, EnergyPortTopology.WorldPortPoint> current
    )
    {
        Objects.requireNonNull(previous, "previous cannot be null");
        Objects.requireNonNull(current, "current cannot be null");

        Set<EnergyPortId> energyPortIds = new HashSet<>(previous.keySet());
        energyPortIds.addAll(current.keySet());

        Set<BlockPos> changedPositions = new HashSet<>();

        for (EnergyPortId portId : energyPortIds) {
            EnergyPortTopology.WorldPortPoint previousPoint = previous.get(portId);
            EnergyPortTopology.WorldPortPoint currentPoint = current.get(portId);

            if (Objects.equals(previousPoint, currentPoint)) {
                continue;
            }

            if (previousPoint != null) {
                changedPositions.add(previousPoint.worldPos());
            }

            if (currentPoint != null) {
                changedPositions.add(currentPoint.worldPos());
            }
        }

        return Set.copyOf(changedPositions);
    }

    /**
     * Возвращает существующий или создаёт
     * новый внешний энергетический обработчик
     * указанной физической точки подключения.
     *
     * <p>Если для логического порта уже существует
     * текущая физическая привязка с совпадающей
     * мировой точкой подключения, возвращает
     * обработчик данной привязки.</p>
     *
     * <p>В противном случае создаёт новую
     * физическую привязку на основании
     * логического обработчика порта
     * и сохраняет её в карте текущих привязок.</p>
     *
     * <p>Замена записи отзывает предыдущую
     * физическую привязку соответствующего порта.</p>
     *
     * <p>Не пересоздаёт логический обработчик
     * и не создаёт отдельное энергетическое
     * хранилище или бюджет передачи.</p>
     *
     * <p>Метод предназначен для внутреннего
     * использования после проверки допустимости
     * предоставления энергетического обработчика
     * для указанной точки подключения.</p>
     *
     * @param portId     идентификатор логического порта
     * @param worldPoint мировая точка подключения
     * @return внешний энергетический обработчик
     * текущей физической привязки
     * @throws NullPointerException  если любой аргумент
     *                               равен {@code null}
     * @throws IllegalStateException если отсутствует
     *                               логический обработчик
     *                               указанного порта
     */
    private @NonNull EnergyEndpointHandler getOrCreateEnergyEndpointHandler(
        @NonNull EnergyPortId portId,
        EnergyPortTopology.@NonNull WorldPortPoint worldPoint
    )
    {
        Objects.requireNonNull(portId, "portId cannot be null");
        Objects.requireNonNull(worldPoint, "worldPoint cannot be null");

        EnergyEndpointBinding binding = this.energyEndpointBindings.get(portId);
        if (binding != null && binding.worldPoint.equals(worldPoint)) {
            return binding.handler;
        }

        EnergyPortHandler handler = this.energyPortHandlers.get(portId);
        if (handler == null) {
            throw new IllegalStateException("Energy port handler is missing for port: " + portId);
        }

        EnergyEndpointBinding newBinding = new EnergyEndpointBinding(portId, worldPoint, handler);
        this.energyEndpointBindings.put(portId, newBinding);

        return newBinding.handler;
    }

    /**
     * Формирует упорядоченный список энергетических
     * портов, доступных для выдачи энергии
     * в текущем игровом тике.
     *
     * <p>Исходный порядок определяется порядком
     * объявления портов в конфигурации генератора.</p>
     *
     * <p>В результирующий список включаются
     * только порты, для которых текущая
     * политика разрешает извлечение энергии.</p>
     *
     * <p>Начальная позиция обхода циклически
     * смещается на значение, вычисляемое
     * по номеру текущего игрового тика
     * и количеству доступных портов.</p>
     *
     * <p>Ротация позволяет изменять порядок
     * обслуживания портов между игровыми тиками
     * и не отдавать постоянный приоритет
     * одному и тому же порту.</p>
     *
     * <p>Метод не распределяет энергетические
     * квоты и не изменяет общий бюджет
     * передачи энергии.</p>
     *
     * <p>Возвращаемый список является
     * отдельной изменяемой коллекцией.</p>
     *
     * @param currentTick текущий игровой тик
     * @return список доступных для выдачи энергии
     * портов с циклически смещённым порядком обхода
     */
    private @NonNull List<EnergyPortId> getRotatedOutputPorts(
        long currentTick
    )
    {
        List<EnergyPortId> ports = new ArrayList<>();
        for (EnergyPortId portId : this.configuration.portRules().portIds()) {
            if (this.energyPortPolicy.allowsExtract(portId)) {
                ports.add(portId);
            }
        }

        if (ports.isEmpty()) {
            return ports;
        }

        int shift = Math.floorMod(currentTick, ports.size());
        Collections.rotate(ports, -shift);

        return ports;
    }

    /**
     * Выполняет поиск энергетического
     * обработчика соседнего потребителя
     * для указанного энергетического порта.
     *
     * <p>Перед поиском проверяет готовность
     * серверной энергетической подсистемы
     * и разрешение выдачи энергии
     * через указанный порт.</p>
     *
     * <p>Разрешает логический порт в мировую
     * точку подключения с учётом текущей
     * ориентации генератора.</p>
     *
     * <p>Позиция соседнего потребителя определяется
     * смещением мировой позиции порта
     * в направлении его внешней стороны.</p>
     *
     * <p>Энергетическая capability запрашивается
     * со стороны соседнего блока, обращённой
     * к физической точке подключения
     * генератора.</p>
     *
     * <p>Если чанк соседнего блока не загружен,
     * возвращает {@code null} без его
     * принудительной загрузки.</p>
     *
     * <p>Результат поиска не кэшируется.
     * Каждый вызов выполняет новый запрос
     * энергетической capability, что позволяет
     * учитывать изменения состояния соседнего
     * потребителя между пробной и фактической
     * передачей энергии.</p>
     *
     * <p>Получение обработчика не гарантирует,
     * что соседний потребитель способен
     * принять положительное количество энергии.</p>
     *
     * @param portId идентификатор энергетического порта
     * @return энергетический обработчик
     * соседнего потребителя или {@code null},
     * если передача через данный порт недоступна
     * @throws NullPointerException если {@code portId}
     *                              равен {@code null}
     */
    private @Nullable EnergyHandler getNeighborEnergyHandler(
        @NonNull EnergyPortId portId
    )
    {
        Objects.requireNonNull(portId, "portId cannot be null");

        if (!this.isEnergyRuntimeReady()) {
            return null;
        }

        if (!this.energyPortPolicy.allowsExtract(portId)) {
            return null;
        }

        if (!(this.level instanceof ServerLevel serverLevel)) {
            return null;
        }

        Optional<EnergyPortTopology.WorldPortPoint> pointOptional = this.resolveEnergyPort(portId);
        if (pointOptional.isEmpty()) {
            return null;
        }

        EnergyPortTopology.WorldPortPoint point = pointOptional.get();

        BlockPos neighborPos = point.worldPos().relative(point.outwardFace());

        if (!serverLevel.getChunkSource().hasChunk(
            neighborPos.getX() >> 4,
            neighborPos.getZ() >> 4
        ))
        {
            return null;
        }

        Direction neighborSide = point.outwardFace().getOpposite();

        return serverLevel.getCapability(Capabilities.Energy.BLOCK, neighborPos, neighborSide);
    }

    /**
     * Инвалидирует энергетические capabilities
     * в указанных мировых позициях.
     *
     * <p>Выполняется только при готовой
     * серверной энергетической подсистеме
     * генератора.</p>
     *
     * <p>Перед инвалидацией каждой позиции
     * проверяет наличие соответствующего
     * загруженного чанка.</p>
     *
     * <p>Позиции незагруженных чанков
     * пропускаются без их принудительной
     * загрузки.</p>
     *
     * <p>Инвалидация выполняется для мировой
     * позиции целиком посредством механизма
     * capabilities NeoForge, а не отдельно
     * для конкретной стороны блока.</p>
     *
     * <p>Позволяет внешним потребителям
     * повторно запросить актуальные обработчики,
     * включая случаи появления capability
     * после ранее полученного {@code null}.</p>
     *
     * <p>Метод самостоятельно не отзывает
     * физические привязки энергетических
     * обработчиков и не изменяет
     * состояние энергетических портов.</p>
     *
     * @param positions мировые позиции,
     *                  требующие инвалидации capabilities
     * @throws NullPointerException если {@code positions}
     *                              равен {@code null}
     */
    private void invalidateEnergyPortCapabilities(
        @NonNull Set<BlockPos> positions
    )
    {
        Objects.requireNonNull(positions, "positions cannot be null");
        if (!this.isEnergyRuntimeReady()) {
            return;
        }

        if (!(this.level instanceof ServerLevel serverLevel)) {
            return;
        }

        for (BlockPos pos : positions) {
            if (!serverLevel.getChunkSource().hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) {
                continue;
            }

            serverLevel.invalidateCapabilities(pos);
        }
    }

    /**
     * Выполняет один проход активной выдачи
     * энергии соседним потребителям.
     *
     * <p>При закрытой серверной энергетической
     * подсистеме завершает выполнение
     * без дополнительных операций.</p>
     *
     * <p>Проход состоит из двух последовательных
     * этапов: обнаружения потенциального
     * спроса и фактической передачи энергии.</p>
     *
     * <h4>Обнаружение спроса</h4>
     *
     * <p>Формирует список доступных выходных
     * портов с учётом ротации порядка обхода
     * в текущем игровом тике.</p>
     *
     * <p>Для каждого порта запрашивает
     * энергетический обработчик соседнего
     * потребителя и выполняет пробную
     * транзакционную вставку энергии.</p>
     *
     * <p>Положительные результаты пробных
     * вставок передаются менеджеру распределения
     * энергии в качестве актуального
     * наблюдения Push-доступности портов.</p>
     *
     * <p>Обнаружение спроса выполняется
     * независимо от текущего запаса энергии
     * и остатка общего бюджета передачи.</p>
     *
     * <p>Отсутствие доступных потребителей
     * также передаётся менеджеру распределения
     * посредством пустого набора наблюдений.</p>
     *
     * <h4>Фактическая передача энергии</h4>
     *
     * <p>Последовательно обходит доступные
     * выходные порты в сформированном порядке.</p>
     *
     * <p>Перед каждой операцией повторно
     * проверяет готовность серверной
     * энергетической подсистемы, наличие
     * накопленной энергии, остаток бюджета
     * передачи и разрешение выдачи энергии
     * через соответствующий порт.</p>
     *
     * <p>Энергетический обработчик соседнего
     * потребителя запрашивается повторно,
     * независимо от результата предыдущей
     * пробной операции.</p>
     *
     * <p>Фактическая передача выполняется
     * посредством компонента активной выдачи
     * энергии в отдельной корневой транзакции
     * для каждого порта.</p>
     *
     * <p>Транзакция подтверждается только
     * при положительном количестве
     * переданной энергии.</p>
     *
     * <p>При исчерпании запаса энергии
     * или общего бюджета передачи
     * дальнейший обход прекращается.</p>
     *
     * <h4>Ограничения</h4>
     *
     * <p>Все операции активной выдачи
     * используют единый энергетический
     * буфер и общий бюджет передачи,
     * разделяемый с пассивным извлечением.</p>
     *
     * <p>Один проход не представляет собой
     * общую транзакцию для всех портов.
     * Ранее подтверждённые операции передачи
     * не откатываются при возникновении
     * исключения в последующей операции.</p>
     *
     * <p>Не выполняет принудительную
     * загрузку чанков соседних потребителей.</p>
     *
     * <p>Не проверяет редстоун-контроль,
     * поскольку редстоун ограничивает
     * производство, а не выдачу ранее
     * накопленной энергии.</p>
     */
    private void pushEnergyToNeighbors() {
        if (!this.isEnergyRuntimeReady()) {
            return;
        }
        long currentTick = this.getCurrentGameTime();
        List<EnergyPortId> rotatedPorts = this.getRotatedOutputPorts(currentTick);

        Map<EnergyPortId, Integer> pushAvailability = new HashMap<>();

        for (EnergyPortId portId : rotatedPorts) {
            EnergyHandler handler = this.getNeighborEnergyHandler(portId);
            if (handler == null) {
                continue;
            }

            int accepted = this.probeEnergyAcceptance(handler);
            if (accepted > 0) {
                pushAvailability.put(portId, accepted);
            }
        }

        this.distribution.updatePushAvailability(pushAvailability, currentTick);

        for (EnergyPortId portId : rotatedPorts) {
            if (!this.isEnergyRuntimeReady()) {
                return;
            }

            long storedEnergy = this.getStoredEnergy();
            int budgetRemaining = this.outputBudget.getRemaining(currentTick);
            if (storedEnergy <= 0 || budgetRemaining <= 0) {
                return;
            }

            if (!this.energyPortPolicy.allowsExtract(portId)) {
                continue;
            }

            EnergyHandler handler = this.getNeighborEnergyHandler(portId);
            if (handler == null) {
                continue;
            }

            try (Transaction transaction = Transaction.openRoot()) {
                int pushed = this.pushTransfer.push(
                    portId,
                    handler,
                    this.configuration.energy().maxOutputPerTick(),
                    transaction
                );
                if (pushed > 0) {
                    transaction.commit();
                }
            }
        }
    }

    /**
     * Синхронизирует представленные внешним
     * потребителям физические точки подключения
     * энергетических портов.
     *
     * <p>Формирует актуальный снимок
     * представленных портов и сравнивает
     * его с последним сохранённым снимком.</p>
     *
     * <p>Определяет мировые позиции,
     * в которых произошло появление,
     * исчезновение или изменение
     * энергетических портов.</p>
     *
     * <p>Дополнительно включает позиции,
     * явно переданные вызывающим кодом,
     * даже если соответствующие мировые
     * точки подключения не изменились.</p>
     *
     * <p>Сохраняет новый снимок до выполнения
     * инвалидации capabilities в затронутых
     * мировых позициях.</p>
     *
     * <p>Дополнительные позиции используются,
     * в частности, при отзыве физической
     * привязки без изменения мировой
     * точки подключения.</p>
     *
     * <p>При закрытой серверной энергетической
     * подсистеме завершает выполнение
     * без изменения сохранённого снимка
     * и инвалидации capabilities.</p>
     *
     * <p>Метод самостоятельно не отзывает
     * физические привязки и не обновляет
     * набор доступных портов контроллера
     * выдачи энергии.</p>
     *
     * @param additionalPositions дополнительные
     *                            мировые позиции,
     *                            требующие инвалидации
     * @throws NullPointerException если
     *                              {@code additionalPositions}
     *                              равен {@code null}
     */
    private void synchronizeExposedEnergyPorts(
        @NonNull Set<BlockPos> additionalPositions
    )
    {
        Objects.requireNonNull(additionalPositions, "additionalPositions cannot be null");
        if (!this.isEnergyRuntimeReady()) {
            return;
        }

        Map<EnergyPortId, EnergyPortTopology.WorldPortPoint> current =
            this.collectExposedEnergyPorts();

        Set<BlockPos> changedPositions =
            new HashSet<>(collectChangedEnergyPortPositions(this.exposedEnergyPorts, current));
        changedPositions.addAll(additionalPositions);

        this.exposedEnergyPorts = current;
        this.invalidateEnergyPortCapabilities(changedPositions);
    }

    /**
     * Синхронизирует набор доступных
     * для выдачи энергии портов генератора.
     *
     * <p>Передаёт контроллеру выдачи энергии
     * запрос на обновление доступных портов
     * в соответствии с их актуальной политикой.</p>
     *
     * <p>Используется при открытии и закрытии
     * энергетической подсистемы, изменении
     * режимов портов, готовности конструкции
     * и физической доступности портов.</p>
     *
     * <p>Обновление не пересоздаёт энергетические
     * компоненты генератора и не сбрасывает
     * общий бюджет передачи энергии.</p>
     *
     * <p>Метод самостоятельно не выполняет
     * инвалидацию энергетических capabilities.</p>
     */
    private void synchronizeAvailableEnergyPorts() {
        this.outputController.refreshAvailablePorts();
    }

    /**
     * Отзывает физические привязки энергетических
     * портов, мировые точки подключения
     * которых изменились.
     *
     * <p>Для каждой текущей привязки повторно
     * разрешает соответствующий логический
     * порт в мировую точку подключения.</p>
     *
     * <p>Привязка удаляется, если порт больше
     * не разрешается в мировую точку подключения
     * либо полученная точка отличается
     * от сохранённой в привязке.</p>
     *
     * <p>При сравнении учитываются как мировая
     * позиция, так и внешняя сторона порта.</p>
     *
     * <p>Привязки портов с неизменившимися
     * мировыми точками подключения сохраняются.</p>
     *
     * <p>Отозванные привязки не восстанавливаются
     * при последующем возвращении генератора
     * в прежнюю ориентацию.</p>
     *
     * <p>Метод самостоятельно не создаёт
     * новые привязки, не изменяет настройки
     * портов и не выполняет инвалидацию
     * энергетических capabilities.</p>
     */
    private void revokeMovedEnergyEndpointBindings() {
        this.energyEndpointBindings.entrySet().removeIf(entry -> {
            EnergyPortId portId = entry.getKey();
            EnergyEndpointBinding binding = entry.getValue();

            Optional<EnergyPortTopology.WorldPortPoint> currentPoint =
                this.resolveEnergyPort(portId);

            return currentPoint.filter(binding.worldPoint::equals).isEmpty();
        });
    }

    /**
     * Сохраняет текущие режимы настраиваемых
     * энергетических портов генератора.
     *
     * <p>Для генераторов с фиксированными
     * режимами портов завершает выполнение
     * без создания секции сохранённых
     * настроек.</p>
     *
     * <p>Для изменяемой конфигурации создаёт
     * дочернюю секцию {@code energy_ports},
     * содержащую текущие сохранённые
     * режимы энергетических портов.</p>
     *
     * <p>Ключом каждой записи является
     * строковое значение стабильного
     * идентификатора порта, а значением —
     * строковое имя текущего режима.</p>
     *
     * <p>Сохраняются именно настройки портов,
     * независимо от их физической доступности
     * и готовности серверной подсистемы.</p>
     *
     * <p>Не сохраняет логические обработчики,
     * физические привязки, мировые точки
     * подключения и временное состояние
     * энергетической подсистемы.</p>
     *
     * <p>Не изменяет текущую конфигурацию
     * энергетических портов.</p>
     *
     * @param output контекст сохранения
     *               состояния генератора
     * @throws NullPointerException если {@code output}
     *                              равен {@code null}
     */
    private void saveEnergyPorts(@NonNull ValueOutput output) {
        Objects.requireNonNull(output, "output cannot be null");

        if (this.energyPortConfiguration == null) {
            return;
        }

        ValueOutput portsOutput = output.child(ENERGY_PORTS_KEY);

        Map<EnergyPortId, EnergyPortMode> currentModes = this.energyPortConfiguration.snapshot();
        for (Map.Entry<EnergyPortId, EnergyPortMode> entry : currentModes.entrySet()) {
            portsOutput.putString(entry.getKey().value(), entry.getValue().name());
        }
    }

    /**
     * Восстанавливает сохранённые режимы
     * настраиваемых энергетических портов.
     *
     * <p>Для генераторов с фиксированными
     * режимами портов завершает выполнение
     * без чтения сохранённых настроек.</p>
     *
     * <p>Восстановление начинается с независимой
     * копии режимов по умолчанию, определённых
     * конфигурацией генератора.</p>
     *
     * <p>Затем выполняется чтение секции
     * {@code energy_ports} и проверка
     * сохранённых значений.</p>
     *
     * <h4>Правила восстановления</h4>
     *
     * <ul>
     *     <li>при отсутствии секции используются
     *     режимы по умолчанию;</li>
     *
     *     <li>при отсутствии записи конкретного
     *     порта используется его режим
     *     по умолчанию;</li>
     *
     *     <li>при некорректном типе секции
     *     для всех известных настраиваемых
     *     портов выбирается режим
     *     {@link EnergyPortMode#DISABLED};</li>
     *
     *     <li>при некорректном типе значения
     *     конкретного порта для него выбирается
     *     режим {@link EnergyPortMode#DISABLED};</li>
     *
     *     <li>при неизвестном строковом имени
     *     режима для соответствующего порта
     *     выбирается режим
     *     {@link EnergyPortMode#DISABLED};</li>
     *
     *     <li>если сохранённый режим существует,
     *     но запрещён политикой конкретного
     *     порта, для него выбирается
     *     режим {@link EnergyPortMode#DISABLED};</li>
     *
     *     <li>записи неизвестных энергетических
     *     портов игнорируются.</li>
     * </ul>
     *
     * <p>Строковые имена режимов интерпретируются
     * в соответствии с точными именами
     * значений {@link EnergyPortMode},
     * без преобразования регистра
     * и удаления пробельных символов.</p>
     *
     * <p>После обработки сохранённых данных
     * восстановленные режимы передаются
     * изменяемой конфигурации и проходят
     * окончательную проверку политикой портов.</p>
     *
     * <p>Восстановление выполняется без вызова
     * обычного обработчика изменения конфигурации,
     * пометки блок-сущности как изменённой,
     * синхронизации энергетической подсистемы
     * и инвалидации capabilities.</p>
     *
     * <p>Повторное восстановление при отсутствии
     * сохранённых настроек возвращает
     * настраиваемые порты к режимам
     * по умолчанию, а не сохраняет ранее
     * установленные значения.</p>
     *
     * @param input контекст восстановления
     *              состояния генератора
     * @throws NullPointerException если {@code input}
     *                              равен {@code null}
     */
    private void loadEnergyPorts(@NonNull ValueInput input) {
        Objects.requireNonNull(input, "input cannot be null");

        if (this.energyPortConfiguration == null) {
            return;
        }

        Map<EnergyPortId, EnergyPortMode> restoredModes =
            new HashMap<>(this.configuration.portRules().defaultModes());

        Optional<Tag> sectionOptional = input.read(ENERGY_PORTS_KEY, ExtraCodecs.NBT);
        if (sectionOptional.isPresent()) {
            if (!(sectionOptional.get() instanceof CompoundTag compoundTag)) {
                restoredModes.replaceAll((portId, mode) -> EnergyPortMode.DISABLED);
            } else {
                for (Map.Entry<EnergyPortId, EnergyPortMode> entry : restoredModes.entrySet()) {
                    EnergyPortId portId = entry.getKey();
                    Tag modeTag = compoundTag.get(portId.value());

                    if (modeTag == null) {
                        continue;
                    }

                    if (modeTag instanceof StringTag(String value)) {
                        EnergyPortMode mode;

                        try {
                            mode = EnergyPortMode.valueOf(value);
                        } catch (IllegalArgumentException e) {
                            mode = EnergyPortMode.DISABLED;
                        }

                        if (!this.energyPortPolicy.canChangeMode(portId, mode)) {
                            mode = EnergyPortMode.DISABLED;
                        }

                        entry.setValue(mode);
                    } else {
                        entry.setValue(EnergyPortMode.DISABLED);
                    }
                }
            }
        }

        this.energyPortConfiguration.restoreModes(restoredModes, this.energyPortPolicy);
    }

    /**
     * Закрывает серверную энергетическую
     * подсистему генератора.
     *
     * <p>Сбрасывает внутренний признак открытия,
     * отзывает все физические привязки
     * энергетических портов, очищает снимок
     * представленных точек подключения
     * и синхронизирует набор доступных портов.</p>
     *
     * <p>После закрытия ранее полученные
     * внешние энергетические обработчики
     * становятся недействительными.</p>
     *
     * <p>Метод допускает повторный вызов
     * без повторного открытия подсистемы.</p>
     *
     * <p>Не выполняет сброс энергетического
     * хранилища, сериализуемых настроек,
     * производственного процесса, бюджета
     * передачи и накопленной статистики.</p>
     *
     * <p>Инвалидация capabilities при удалении
     * или выгрузке блок-сущности обеспечивается
     * соответствующими механизмами жизненного
     * цикла Minecraft и NeoForge.</p>
     */
    private void closeEnergyRuntime() {
        this.energyRuntimeReady = false;
        this.energyEndpointBindings.clear();
        this.exposedEnergyPorts = Collections.emptyMap();
        this.synchronizeAvailableEnergyPorts();
    }
}
