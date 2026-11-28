package ru.greatdragonx.astralspace.common.machine.redstone;

import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.NonNull;

import java.util.Objects;
import java.util.Optional;

/**
 * Обеспечивает сохранение и восстановление режима
 * управления редстоуном.
 *
 * <p>Сохраняет режим в виде строкового имени
 * константы {@link RedstoneControlMode}
 * по ключу {@code redstone_control_mode}.</p>
 *
 * <p>При отсутствии сохранённого значения,
 * несовместимом типе данных или неизвестном
 * имени режима возвращает
 * {@link RedstoneControlMode#IGNORE}.</p>
 *
 * <p>Сериализатор не владеет состоянием режима,
 * не изменяет состояние контроллера или компонента
 * и не зависит от готовности серверного окружения.</p>
 *
 * <p>Не сохраняет состояние редстоун-сигнала,
 * вычисленное разрешение работы и другие
 * временные данные механизма.</p>
 *
 * <p>Ответственность за применение восстановленного
 * режима лежит на вызывающем коде.</p>
 *
 * @see RedstoneControlMode
 * @see RedstoneController
 */
public final class RedstoneControlSerializer {
    private static final String REDSTONE_CONTROL_MODE_KEY = "redstone_control_mode";

    private RedstoneControlSerializer() {}

    /**
     * Сохраняет указанный режим управления редстоуном.
     *
     * <p>Перед записью проверяет все обязательные
     * аргументы.</p>
     *
     * <p>Режим записывается по ключу
     * {@code redstone_control_mode} в виде строкового
     * имени константы перечисления.</p>
     *
     * <p>Не использует порядковый номер константы,
     * поэтому изменение порядка объявления режимов
     * не влияет на формат сохранённых данных.</p>
     *
     * <p>Каждый режим сохраняется явно, включая
     * {@link RedstoneControlMode#IGNORE}.</p>
     *
     * <p>Если значение по указанному ключу уже существует,
     * оно заменяется новым. Остальные данные
     * не изменяются.</p>
     *
     * @param output получатель сохраняемых данных
     * @param mode   сохраняемый режим управления
     * @throws NullPointerException если output или mode
     *                              равен {@code null}
     */
    public static void save(@NonNull ValueOutput output, @NonNull RedstoneControlMode mode) {
        Objects.requireNonNull(output, "output cannot be null");
        Objects.requireNonNull(mode, "mode cannot be null");

        output.putString(REDSTONE_CONTROL_MODE_KEY, mode.name());
    }

    /**
     * Читает сохранённый режим управления редстоуном.
     *
     * <p>Получает строковое значение по ключу
     * {@code redstone_control_mode} и преобразует
     * его в соответствующую константу
     * {@link RedstoneControlMode}.</p>
     *
     * <p>Имя режима должно точно соответствовать
     * имени константы перечисления. Нормализация
     * регистра и удаление пробельных символов
     * не выполняются.</p>
     *
     * <p>При отсутствии значения, несовместимом
     * типе данных, пустой строке или неизвестном
     * имени режима возвращает
     * {@link RedstoneControlMode#IGNORE}.</p>
     *
     * <p>Восстановление значения по умолчанию
     * не является запретом работы со стороны
     * редстоун-политики.</p>
     *
     * <p>Метод не изменяет исходные данные,
     * не обращается к игровому миру
     * и не вызывает обработчики изменения режима.</p>
     *
     * <p>Возвращённый режим может быть применён
     * посредством тихого восстановления состояния
     * контроллера.</p>
     *
     * @param input источник сохранённых данных
     * @return восстановленный режим управления,
     * никогда не равный {@code null}
     * @throws NullPointerException если input
     *                              равен {@code null}
     * @see RedstoneController#restoreMode(RedstoneControlMode)
     */
    public static @NonNull RedstoneControlMode load(
        @NonNull ValueInput input
    )
    {
        Objects.requireNonNull(input, "input cannot be null");

        Optional<String> valueOptional = input.getString(REDSTONE_CONTROL_MODE_KEY);
        if (valueOptional.isEmpty()) {
            return RedstoneControlMode.IGNORE;
        }

        String value = valueOptional.get();

        try {
            return RedstoneControlMode.valueOf(value);
        } catch (IllegalArgumentException e) {
            return RedstoneControlMode.IGNORE;
        }
    }
}
