package ru.greatdragonx.astralspace.common.machine.redstone;

import org.jspecify.annotations.NonNull;

import java.util.Objects;
import java.util.function.BooleanSupplier;

/**
 * Координирует управление работой механизма
 * посредством сигнала редстоуна.
 *
 * <p>Владеет единственным экземпляром
 * {@link RedstoneControlComponent}, отвечающим
 * за хранение пользовательского режима и определение
 * разрешения работы при заданном состоянии сигнала.</p>
 *
 * <p>Получает актуальную готовность серверного окружения
 * и состояние редстоун-сигнала посредством переданных
 * поставщиков значений.</p>
 *
 * <p>Не имеет собственного состояния готовности,
 * не управляет жизненным циклом механизма
 * и не кэширует результаты получения сигнала.</p>
 *
 * <p>При неготовности серверного окружения
 * изменение пользовательского режима запрещено,
 * а источник сигнала не опрашивается.</p>
 *
 * <p>Получение сохранённого режима и его тихое
 * восстановление не зависят от готовности окружения.</p>
 *
 * <p>При фактическом пользовательском изменении режима
 * внутренний компонент уведомляет владельца
 * посредством переданного callback.</p>
 *
 * <p>Контроллер не зависит от игрового мира,
 * энергетической системы, способа выполнения
 * рабочих операций и механизмов сериализации.</p>
 *
 * <p>Предназначен для использования посредством
 * композиции внутри механизма. Не является
 * потокобезопасным.</p>
 *
 * @see RedstoneControlComponent
 * @see RedstoneControlMode
 * @see RedstoneControllable
 */
public final class RedstoneController {
    private final RedstoneControlComponent component;
    private final BooleanSupplier runtimeReady;
    private final BooleanSupplier signalSupplier;

    /**
     * Создаёт контроллер управления редстоуном
     * с указанными инфраструктурными зависимостями.
     *
     * <p>Создаёт единственный внутренний компонент
     * с первоначальным режимом
     * {@link RedstoneControlMode#IGNORE}.</p>
     *
     * <p>Переданные поставщики значений сохраняются,
     * но не вызываются при создании контроллера.</p>
     *
     * <p>Обработчик изменения режима передаётся
     * внутреннему компоненту и не вызывается
     * в процессе конструирования.</p>
     *
     * @param runtimeReady   поставщик актуальной готовности
     *                       серверного окружения механизма
     * @param signalSupplier поставщик актуального состояния
     *                       сигнала редстоуна
     * @param onModeChanged  обработчик фактического изменения
     *                       пользовательского режима
     * @throws NullPointerException если одна из переданных
     *                              зависимостей равна {@code null}
     */
    public RedstoneController(
        @NonNull BooleanSupplier runtimeReady,
        @NonNull BooleanSupplier signalSupplier,
        @NonNull Runnable onModeChanged
    )
    {
        this.runtimeReady = Objects.requireNonNull(runtimeReady, "runtimeReady cannot be null");
        this.signalSupplier = Objects.requireNonNull(
            signalSupplier,
            "signalSupplier cannot be null"
        );
        this.component = new RedstoneControlComponent(Objects.requireNonNull(
            onModeChanged,
            "onModeChanged cannot be null"
        ));
    }

    /**
     * Возвращает текущий режим управления редстоуном.
     *
     * <p>Делегирует получение режима внутреннему компоненту.</p>
     *
     * <p>Не проверяет готовность серверного окружения,
     * не обращается к источнику сигнала
     * и не изменяет состояние контроллера.</p>
     *
     * @return текущий режим управления,
     * никогда не равный {@code null}
     */
    public @NonNull RedstoneControlMode getRedstoneControlMode() {
        return this.component.getMode();
    }

    /**
     * Выполняет пользовательское изменение режима
     * управления редстоуном.
     *
     * <p>Перед проверкой готовности серверного окружения
     * проверяет обязательный аргумент.</p>
     *
     * <p>При неготовности окружения возвращает
     * {@code false} без изменения состояния компонента.</p>
     *
     * <p>При готовом окружении делегирует изменение
     * режима внутреннему компоненту.</p>
     *
     * <p>Фактическое изменение сопровождается однократным
     * вызовом обработчика изменения режима.
     * Повторная установка текущего режима
     * не вызывает обработчик.</p>
     *
     * <p>Если обработчик выбрасывает исключение,
     * оно распространяется вызывающему коду.
     * Установленный режим автоматически не откатывается.</p>
     *
     * <p>Метод не обращается к источнику редстоун-сигнала.</p>
     *
     * @param mode новый режим управления
     * @return {@code true}, если режим успешно изменён,
     * {@code false}, если окружение не готово
     * или переданный режим уже установлен
     * @throws NullPointerException если mode равен {@code null}
     */
    public boolean changeRedstoneControlMode(
        @NonNull RedstoneControlMode mode
    )
    {
        Objects.requireNonNull(mode, "mode cannot be null");

        if (!this.runtimeReady.getAsBoolean()) {
            return false;
        }

        return this.component.changeMode(mode);
    }

    /**
     * Возвращает актуальное состояние сигнала редстоуна.
     *
     * <p>При каждом вызове проверяет готовность
     * серверного окружения механизма.</p>
     *
     * <p>Если окружение не готово, возвращает
     * {@code false} без обращения к поставщику сигнала.</p>
     *
     * <p>В противном случае возвращает значение,
     * полученное непосредственно от поставщика сигнала.</p>
     *
     * <p>Результат не кэшируется и не зависит
     * от текущего режима управления.</p>
     *
     * @return {@code true}, если при готовом окружении
     * присутствует сигнал редстоуна,
     * иначе {@code false}
     */
    public boolean hasRedstoneSignal() {
        if (!this.runtimeReady.getAsBoolean()) {
            return false;
        }

        return this.signalSupplier.getAsBoolean();
    }

    /**
     * Определяет наличие запрета выполнения операции
     * со стороны текущей редстоун-политики.
     *
     * <p>При неготовности серверного окружения
     * возвращает {@code false} без обращения
     * к поставщику сигнала.</p>
     *
     * <p>При готовом окружении получает актуальное
     * состояние сигнала и делегирует проверку
     * разрешения работы внутреннему компоненту.</p>
     *
     * <p>Возвращает результат, противоположный
     * разрешению работы компонента.</p>
     *
     * <p>Не проверяет остальные условия выполнения
     * рабочей операции механизма.</p>
     *
     * <p>Отсутствие запрета со стороны редстоуна
     * не означает готовность механизма к работе.</p>
     *
     * @return {@code true}, если операция запрещена
     * редстоун-политикой, иначе {@code false}
     */
    public boolean isRedstoneBlockingOperation() {
        if (!this.runtimeReady.getAsBoolean()) {
            return false;
        }

        return !this.component.allowsOperation(this.signalSupplier.getAsBoolean());
    }

    /**
     * Восстанавливает сохранённый режим управления
     * редстоуном без уведомления владельца.
     *
     * <p>Делегирует тихое восстановление состояния
     * внутреннему компоненту.</p>
     *
     * <p>Не проверяет готовность серверного окружения
     * и не обращается к поставщику сигнала.</p>
     *
     * <p>Обработчик изменения режима не вызывается,
     * даже если восстанавливаемый режим отличается
     * от текущего.</p>
     *
     * <p>Предназначен для восстановления сохранённого
     * состояния и не должен использоваться вместо
     * штатного пользовательского изменения режима.</p>
     *
     * @param mode восстанавливаемый режим управления
     * @throws NullPointerException если mode равен {@code null}
     * @see #changeRedstoneControlMode(RedstoneControlMode)
     */
    public void restoreMode(
        @NonNull RedstoneControlMode mode
    )
    {
        Objects.requireNonNull(mode, "mode cannot be null");
        this.component.restoreMode(mode);
    }
}
