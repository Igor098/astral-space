package ru.greatdragonx.astralspace.common.machine.visual.layout;

import ru.greatdragonx.astralspace.common.machine.visual.VisualPort;

import java.util.Objects;

/**
 * Неизменяемое описание размещения конкретного
 * визуального порта на стороне механизма.
 *
 * <p>Объединяет описание визуального порта
 * с координатными смещениями относительно
 * центра стороны и коэффициентом масштабирования.</p>
 *
 * <p>Может использоваться в качестве результата
 * расчёта размещения посредством {@link PortLayout}.</p>
 *
 * <p>В отличие от {@link PortLayoutPreset},
 * содержит ссылку на конкретный визуальный порт,
 * для которого определены параметры размещения.</p>
 *
 * <p>Координатные смещения задаются в локальной
 * системе координат стороны механизма.</p>
 *
 * <p>Не выполняет непосредственную отрисовку,
 * преобразование координат в мировую систему
 * и управление передачей ресурсов.</p>
 *
 * @param port    размещаемый визуальный порт
 * @param offsetU горизонтальное смещение
 *                относительно центра стороны
 * @param offsetV вертикальное смещение
 *                относительно центра стороны
 * @param scale   коэффициент масштабирования
 *                визуального порта
 * @see VisualPort
 * @see PortLayout
 * @see PortLayoutPreset
 */
public record PortPlacement(VisualPort port, float offsetU, float offsetV, float scale) {
    /**
     * Создаёт описание размещения визуального порта
     * и проверяет допустимость его параметров.
     *
     * <p>Визуальный порт является обязательным.</p>
     *
     * <p>Оба координатных смещения должны
     * быть конечными числами.</p>
     *
     * <p>Коэффициент масштабирования должен
     * быть конечным и строго положительным.</p>
     *
     * <p>Допускаются отрицательные, положительные
     * и нулевые значения координатных смещений.</p>
     *
     * <p>Проверка нахождения порта в пределах
     * стороны механизма, пересечения с другими
     * портами и верхней границы масштабирования
     * не выполняется.</p>
     *
     * @param port    размещаемый визуальный порт
     * @param offsetU горизонтальное смещение
     * @param offsetV вертикальное смещение
     * @param scale   коэффициент масштабирования
     * @throws NullPointerException     если port
     *                                  равен {@code null}
     * @throws IllegalArgumentException если любое
     *                                  смещение не является
     *                                  конечным числом либо
     *                                  масштаб не является
     *                                  конечным положительным
     *                                  числом
     */
    public PortPlacement {
        Objects.requireNonNull(port, "port cannot be null");
        if (!Float.isFinite(offsetU)) {
            throw new IllegalArgumentException("offsetU must be finite");
        }
        if (!Float.isFinite(offsetV)) {
            throw new IllegalArgumentException("offsetV must be finite");
        }
        if (!Float.isFinite(scale) || scale <= 0.0f) {
            throw new IllegalArgumentException("scale must be finite and positive");
        }
    }
}
