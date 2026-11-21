package ru.greatdragonx.astralspace.common.fluid.storage;

import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.fluid.FluidStacksResourceHandler;
import org.jetbrains.annotations.Unmodifiable;
import org.jspecify.annotations.NonNull;

import java.util.*;

/**
 * Управляет содержимым нескольких жидкостных
 * резервуаров одного механизма.
 *
 * <p>Хранилище основано на
 * {@link FluidStacksResourceHandler} и использует
 * предоставляемый NeoForge механизм транзакционных
 * операций с жидкостями.</p>
 *
 * <p>Каждый резервуар описывается отдельным
 * {@link FluidTankDefinition}, определяющим
 * его идентификатор, производственную роль,
 * вместимость и допустимые жидкости.</p>
 *
 * <p>Количество резервуаров определяется числом
 * переданных определений. Порядок определений
 * задаёт соответствие между их идентификаторами
 * и внутренними индексами хранилища.</p>
 *
 * <p>Определения проверяются и защитно копируются
 * при создании объекта. Пустая конфигурация,
 * повторяющиеся идентификаторы и значения
 * {@code null} не допускаются.</p>
 *
 * <p>Вместимость и фильтрация жидкости определяются
 * индивидуально для каждого резервуара.
 * Резервуары не смешивают содержимое друг с другом
 * и сохраняют независимое состояние.</p>
 *
 * <p>При изменении содержимого вызывается переданный
 * обработчик уведомления. Момент вызова зависит
 * от правил изменения состояния и завершения
 * транзакций базового обработчика NeoForge.</p>
 *
 * <p>Компонент не определяет маршрутизацию
 * между жидкостными портами и резервуарами,
 * не применяет {@link FluidTankRole} как ограничение
 * внешнего доступа и не управляет настройками
 * {@link ru.greatdragonx.astralspace.common.fluid.port.FluidPortPolicy}.</p>
 *
 * <p>Компонент также не отвечает непосредственно
 * за сериализацию NBT, синхронизацию с клиентом
 * или регистрацию Minecraft capabilities.</p>
 */
public class MachineFluidStorage extends FluidStacksResourceHandler {
    private final List<FluidTankDefinition> definitions;
    private final Runnable changeListener;

    /**
     * Обёртка над предварительно проверенным
     * списком определений резервуаров.
     *
     * <p>Используется для разделения публичного
     * и приватного конструкторов хранилища
     * и исключения повторной валидации.</p>
     *
     * <p>Объект создаётся только после успешного
     * выполнения {@link #validateDefinitions(List)}.</p>
     *
     * @param values проверенный неизменяемый
     *               список определений резервуаров
     */
    private record ValidatedDefinitions(
        List<FluidTankDefinition> values
    ) {}

    /**
     * Создаёт хранилище из предварительно
     * проверенных определений резервуаров.
     *
     * <p>Количество внутренних резервуаров передаётся
     * базовому обработчику NeoForge.</p>
     *
     * <p>Индивидуальная вместимость определяется
     * переопределённым методом
     * {@link #getCapacity(int, FluidResource)}.
     * Значение вместимости, передаваемое базовому
     * конструктору, используется как начальное
     * техническое значение.</p>
     *
     * <p>Проверенный список сохраняется без
     * повторного копирования и валидации.</p>
     *
     * <p>Обработчик изменений сохраняется,
     * но не вызывается при создании объекта.</p>
     *
     * @param validated      проверенные определения
     *                       жидкостных резервуаров
     * @param changeListener обработчик изменения содержимого
     * @throws NullPointerException если
     *                              {@code changeListener}
     *                              равен {@code null}
     */
    private MachineFluidStorage(
        @NonNull ValidatedDefinitions validated,
        @NonNull Runnable changeListener
    )
    {
        super(
            validated.values()
                .size(),
            1
        );

        this.definitions = validated.values();
        this.changeListener = Objects.requireNonNull(
            changeListener,
            "changeListener cannot be null"
        );
    }

    /**
     * Создаёт многорезервуарное жидкостное хранилище.
     *
     * <p>Переданный список определений проверяется
     * и копируется перед инициализацией
     * базового обработчика NeoForge.</p>
     *
     * <p>Каждому определению назначается внутренний
     * индекс, соответствующий его положению
     * в исходном списке.</p>
     *
     * <p>Повторяющиеся идентификаторы резервуаров
     * запрещены, даже если остальные характеристики
     * соответствующих определений различаются.</p>
     *
     * <p>Обработчик изменений сохраняется,
     * но не вызывается при создании хранилища.</p>
     *
     * @param definitions    определения резервуаров
     *                       в порядке внутренних индексов
     * @param changeListener обработчик изменения содержимого
     * @throws NullPointerException     если любой аргумент
     *                                  равен {@code null}
     *                                  либо список содержит
     *                                  {@code null}
     * @throws IllegalArgumentException если список
     *                                  определений пуст
     *                                  или содержит
     *                                  повторяющиеся
     *                                  идентификаторы
     */
    public MachineFluidStorage(
        List<FluidTankDefinition> definitions,
        Runnable changeListener
    )
    {
        this(
            new ValidatedDefinitions(validateDefinitions(definitions)),
            changeListener
        );
    }

    /**
     * Возвращает максимальную вместимость
     * указанного жидкостного резервуара.
     *
     * <p>Вместимость определяется соответствующим
     * {@link FluidTankDefinition} и не зависит
     * от типа запрашиваемой жидкости.</p>
     *
     * <p>Метод не проверяет допустимость ресурса,
     * не изменяет содержимое резервуара
     * и не вызывает обработчик уведомления.</p>
     *
     * @param index    внутренний индекс резервуара
     * @param resource жидкостный ресурс, для которого
     *                 запрашивается вместимость
     * @return максимальная вместимость резервуара
     * @throws IndexOutOfBoundsException если
     *                                   {@code index}
     *                                   находится за пределами
     *                                   списка определений
     */
    @Override
    protected int getCapacity(
        int index,
        @NonNull FluidResource resource
    )
    {
        return this.definitions.get(index)
            .capacity();
    }

    /**
     * Обрабатывает уведомление об изменении
     * содержимого жидкостного резервуара.
     *
     * <p>Вызывается базовым обработчиком NeoForge
     * в соответствии с его механизмом отслеживания
     * изменений содержимого.</p>
     *
     * <p>Делегирует уведомление сохранённому
     * {@code changeListener} без дополнительной
     * модификации состояния.</p>
     *
     * <p>Метод не выполняет операции вставки
     * или извлечения жидкости, не инициирует
     * новые транзакции и не управляет
     * непосредственно синхронизацией BlockEntity.</p>
     *
     * <p>При изменении нескольких резервуаров
     * обработчик может быть вызван отдельно
     * для каждого изменённого индекса.</p>
     *
     * @param index            индекс изменённого резервуара
     * @param previousContents содержимое резервуара
     *                         до изменения
     */
    @Override
    protected void onContentsChanged(
        int index,
        FluidStack previousContents
    )
    {
        this.changeListener.run();
    }

    /**
     * Проверяет допустимость жидкостного ресурса
     * для указанного резервуара.
     *
     * <p>Допустимость определяется индивидуальным
     * предикатом {@link FluidTankDefinition#acceptedFluid()}.</p>
     *
     * <p>Для несуществующих индексов возвращается
     * {@code false} без вызова предиката.</p>
     *
     * <p>Метод не проверяет свободную вместимость,
     * текущее количество жидкости, производственную
     * роль резервуара или ограничения внешних портов.</p>
     *
     * <p>Метод не изменяет содержимое хранилища
     * и не вызывает обработчик уведомления.</p>
     *
     * @param index    внутренний индекс резервуара
     * @param resource проверяемый жидкостный ресурс
     * @return {@code true}, если ресурс разрешён
     * для указанного резервуара;
     * иначе {@code false}
     */
    @Override
    public boolean isValid(
        int index,
        @NonNull FluidResource resource
    )
    {
        if (index < 0 || index >= this.definitions.size()) {
            return false;
        }

        FluidTankDefinition definition = this.definitions.get(index);
        return definition.acceptedFluid()
            .test(resource);
    }

    public OptionalInt findTankIndex(
        @NonNull FluidTankId tankId
    )
    {
        Objects.requireNonNull(
            tankId,
            "tankId cannot be null"
        );

        for (int index = 0; index < this.definitions.size(); index++) {
            FluidTankDefinition definition = this.definitions.get(index);
            if (definition.id()
                .equals(tankId))
            {
                return OptionalInt.of(index);
            }
        }
        return OptionalInt.empty();
    }

    /**
     * Проверяет и копирует определения
     * жидкостных резервуаров.
     *
     * <p>Список должен содержать хотя бы одно
     * определение. Значения {@code null}
     * и повторяющиеся идентификаторы запрещены.</p>
     *
     * <p>Уникальность определяется исключительно
     * по {@link FluidTankId}, независимо от других
     * характеристик резервуаров.</p>
     *
     * <p>Возвращаемая коллекция является
     * неизменяемой независимой копией
     * с сохранением исходного порядка.</p>
     *
     * <p>Метод не создаёт резервуары
     * и не изменяет содержимое хранилища.</p>
     *
     * @param definitions исходные определения резервуаров
     * @return проверенная неизменяемая копия
     * списка определений
     * @throws NullPointerException     если список
     *                                  равен {@code null}
     *                                  или содержит
     *                                  {@code null}
     * @throws IllegalArgumentException если список пуст
     *                                  или содержит
     *                                  повторяющиеся
     *                                  идентификаторы
     */
    private static @NonNull @Unmodifiable List<FluidTankDefinition> validateDefinitions(
        @NonNull List<FluidTankDefinition> definitions
    )
    {
        Objects.requireNonNull(
            definitions,
            "definitions cannot be null"
        );

        if (definitions.isEmpty()) {
            throw new IllegalArgumentException("definitions cannot be empty");
        }

        List<FluidTankDefinition> validatedDefinitions = List.copyOf(definitions);
        Set<FluidTankId> uniqueTankIds = new HashSet<>();
        for (FluidTankDefinition definition : validatedDefinitions) {
            if (!uniqueTankIds.add(definition.id())) {
                throw new IllegalArgumentException("Duplicate fluid tank id: " +
                    definition.id()
                        .value());
            }
        }

        return validatedDefinitions;
    }
}
