package ru.greatdragonx.astralspace.common.machine.visual.layout;

import org.jetbrains.annotations.Unmodifiable;
import org.jspecify.annotations.NonNull;
import ru.greatdragonx.astralspace.common.machine.MachineSide;
import ru.greatdragonx.astralspace.common.machine.visual.VisualPort;
import ru.greatdragonx.astralspace.common.machine.visual.VisualPortId;

import java.util.*;

/**
 * Алгоритм размещения визуальных портов
 * на одной логической стороне механизма.
 *
 * <p>Преобразует описания {@link VisualPort}
 * в набор рассчитанных размещений
 * {@link PortPlacement} с использованием
 * предопределённых шаблонов {@link PortLayoutPreset}.</p>
 *
 * <p>Поддерживает следующие варианты размещения:</p>
 *
 * <ul>
 *     <li>отсутствие визуальных портов;</li>
 *     <li>один порт в центре стороны;</li>
 *     <li>два порта, расположенных вертикально
 *         друг относительно друга.</li>
 * </ul>
 *
 * <p>При размещении двух портов используется
 * детерминированная сортировка по типу порта
 * и строковому значению его идентификатора.</p>
 *
 * <p>Все размещаемые порты должны принадлежать
 * одной логической стороне механизма
 * и иметь уникальные идентификаторы.</p>
 *
 * <p>Класс не хранит изменяемое состояние,
 * не обращается к игровому миру и не выполняет
 * непосредственную отрисовку портов.</p>
 *
 * @see VisualPort
 * @see PortPlacement
 * @see PortLayoutPreset
 */
public final class PortLayout {
    /**
     * Шаблон размещения одиночного визуального
     * порта в центре стороны механизма.
     *
     * <p>Оба смещения равны нулю,
     * масштаб соответствует исходному размеру порта.</p>
     */
    private static final PortLayoutPreset CENTER = new PortLayoutPreset(0.0f, 0.0f, 1.0f);
    /**
     * Шаблон размещения верхнего визуального порта
     * при вертикальном расположении двух портов.
     *
     * <p>Горизонтальное смещение равно нулю,
     * вертикальное смещение составляет {@code 0.1375},
     * масштаб — {@code 0.6}.</p>
     */
    private static final PortLayoutPreset UPPER = new PortLayoutPreset(0.0f, 0.1375f, 0.6f);
    /**
     * Шаблон размещения нижнего визуального порта
     * при вертикальном расположении двух портов.
     *
     * <p>Горизонтальное смещение равно нулю,
     * вертикальное смещение составляет {@code -0.1375},
     * масштаб — {@code 0.6}.</p>
     */
    private static final PortLayoutPreset LOWER = new PortLayoutPreset(0.0f, -0.1375f, 0.6f);

    /**
     * Рассчитывает размещение визуальных портов
     * на одной логической стороне механизма.
     *
     * <p>Перед расчётом проверяется принадлежность
     * всех портов одной логической стороне
     * и уникальность их идентификаторов.</p>
     *
     * <p>Размещение определяется количеством портов:</p>
     *
     * <ul>
     *     <li>ноль портов — возвращается пустой список;</li>
     *     <li>один порт — используется центральный
     *         шаблон размещения;</li>
     *     <li>два порта — используются верхний
     *         и нижний шаблоны размещения.</li>
     * </ul>
     *
     * <p>При наличии двух портов они предварительно
     * сортируются по естественному порядку
     * их типов, а при совпадении типов —
     * по строковым значениям идентификаторов.</p>
     *
     * <p>Первый порт после сортировки получает
     * верхнее размещение, второй — нижнее.</p>
     *
     * <p>Размещение более двух портов на одной
     * стороне в текущей реализации не поддерживается.</p>
     *
     * <p>Возвращаемый список является неизменяемым.
     * Исходный список портов не изменяется.</p>
     *
     * @param ports визуальные порты одной логической
     *              стороны механизма
     * @return неизменяемый список рассчитанных
     * размещений визуальных портов
     * @throws NullPointerException          если ports равен
     *                                       {@code null} или
     *                                       содержит {@code null}
     * @throws IllegalArgumentException      если порты
     *                                       принадлежат разным
     *                                       сторонам или имеют
     *                                       повторяющиеся
     *                                       идентификаторы
     * @throws UnsupportedOperationException если
     *                                       корректный список
     *                                       содержит более
     *                                       двух портов
     */
    public List<PortPlacement> arrange(@NonNull List<VisualPort> ports) {
        Objects.requireNonNull(ports, "Visual ports must not be null");

        if (ports.isEmpty()) {
            return Collections.emptyList();
        }

        Set<VisualPortId> usedIds = new HashSet<>();
        VisualPort firstPort = Objects.requireNonNull(
            ports.getFirst(),
            "Visual port must not be null"
        );
        MachineSide expectedSide = firstPort.side();

        for (VisualPort port : ports) {
            Objects.requireNonNull(port, "Visual port must not be null");
            if (port.side() != expectedSide) {
                throw new IllegalArgumentException(
                    "All visual ports must belong to the same machine side");
            }
            if (!usedIds.add(port.id())) {
                throw new IllegalArgumentException("Duplicate visual port id: " +
                    port.id().value());
            }
        }

        int count = ports.size();
        if (count == 1) {
            return List.of(new PortPlacement(
                firstPort,
                CENTER.offsetU(),
                CENTER.offsetV(),
                CENTER.scale()
            ));
        }

        if (count == 2) {
            List<PortPlacement> placements = new ArrayList<>();
            List<VisualPort> sortedPorts = new ArrayList<>(ports);
            sortedPorts.sort(Comparator.comparing(VisualPort::type)
                .thenComparing(port -> port.id().value()));

            for (int i = 0; i < sortedPorts.size(); i++) {
                VisualPort port = sortedPorts.get(i);
                PortLayoutPreset preset = i == 0 ? UPPER : LOWER;

                placements.add(new PortPlacement(
                    port,
                    preset.offsetU(),
                    preset.offsetV(),
                    preset.scale()
                ));
            }

            return List.copyOf(placements);
        }

        throw new UnsupportedOperationException("Multiple port layout is not implemented yet");
    }

    /**
     * Возвращает поддерживаемые шаблоны
     * размещения визуальных портов.
     *
     * <p>Шаблоны возвращаются в следующем порядке:</p>
     *
     * <ol>
     *     <li>центральное размещение;</li>
     *     <li>верхнее размещение;</li>
     *     <li>нижнее размещение.</li>
     * </ol>
     *
     * <p>Возвращаемый список является неизменяемым
     * и не зависит от ранее выполненных
     * расчётов размещения.</p>
     *
     * @return неизменяемый список поддерживаемых
     * шаблонов размещения
     */
    public @NonNull @Unmodifiable List<PortLayoutPreset> supportedPresets() {
        return List.of(CENTER, UPPER, LOWER);
    }
}
