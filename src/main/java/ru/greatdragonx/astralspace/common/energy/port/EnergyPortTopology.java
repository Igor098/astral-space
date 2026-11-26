package ru.greatdragonx.astralspace.common.energy.port;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.jetbrains.annotations.Unmodifiable;
import org.jspecify.annotations.NonNull;

import java.util.*;

/**
 * Неизменяемое описание физической топологии
 * энергетических портов механизма.
 *
 * <p>Связывает логические идентификаторы портов
 * с их локальными физическими точками подключения.
 * Локальная геометрия задаётся относительно
 * исходной ориентации {@link Direction#NORTH}.</p>
 *
 * <p>Поддерживает преобразование локальных точек
 * в мировые координаты с учётом опорной позиции
 * и горизонтальной ориентации механизма,
 * а также обратный поиск логических портов.</p>
 *
 * <p>Идентификаторы портов и пары локального смещения
 * с внешней гранью должны быть уникальными.
 * Порядок объявления точек сохраняется.</p>
 *
 * <p>Поддерживает как одноблочные, так и
 * мультиблочные механизмы.</p>
 *
 * <p>Не обращается к игровому миру, не проверяет
 * физическую доступность портов и не выполняет
 * передачу энергии.</p>
 *
 * @param points упорядоченный список локальных
 *               физических точек подключения
 */
public record EnergyPortTopology(
    @NonNull List<EnergyPortPoint> points
)
{
    /**
     * Неизменяемое описание физической точки
     * энергетического подключения в мировых координатах.
     *
     * <p>Содержит позицию блока, в котором расположен
     * порт, и направление его внешней грани.</p>
     *
     * <p>Не гарантирует наличие блока или энергетического
     * capability в указанной позиции.</p>
     *
     * @param worldPos    мировая позиция блока
     * @param outwardFace внешняя грань порта
     */
    public record WorldPortPoint(
        @NonNull BlockPos worldPos, @NonNull Direction outwardFace
    )
    {
        /**
         * Создаёт неизменяемую мировую физическую точку.
         *
         * <p>Переданная позиция приводится к неизменяемому
         * представлению через {@link BlockPos#immutable()}.</p>
         *
         * @throws NullPointerException если любой аргумент
         *                              равен {@code null}
         */
        public WorldPortPoint {
            Objects.requireNonNull(worldPos, "worldPos cannot be null");
            Objects.requireNonNull(outwardFace, "outwardFace cannot be null");
            worldPos = worldPos.immutable();
        }
    }

    /**
     * Создаёт неизменяемую физическую топологию
     * и проверяет её инварианты.
     *
     * <p>Переданный список защитно копируется.
     * Пустая топология допускается.</p>
     *
     * @throws NullPointerException     если список
     *                                  или один из его
     *                                  элементов равен
     *                                  {@code null}
     * @throws IllegalArgumentException если повторяются
     *                                  идентификаторы портов
     *                                  или физические
     *                                  точки подключения
     */
    public EnergyPortTopology {
        points = List.copyOf(Objects.requireNonNull(points, "points cannot be null"));

        Set<EnergyPortId> knownPorts = new HashSet<>();
        Map<BlockPos, Set<Direction>> occupiedFaces = new HashMap<>();
        for (EnergyPortPoint point : points) {
            if (!knownPorts.add(point.portId())) {
                throw new IllegalArgumentException("Duplicate energy port ID: " +
                    point.portId().value());
            }

            BlockPos offset = point.localOffset();
            Set<Direction> faces = occupiedFaces.computeIfAbsent(
                offset,
                ignored -> EnumSet.noneOf(Direction.class)
            );

            if (!faces.add(point.localOutwardFace())) {
                throw new IllegalArgumentException("Duplicate physical energy port point: offset=" +
                    offset +
                    ", face=" +
                    point.localOutwardFace() +
                    ", portId=" +
                    point.portId().value());
            }
        }
    }

    /**
     * Возвращает идентификаторы всех портов
     * в порядке объявления физических точек.
     *
     * @return неизменяемый упорядоченный список
     * идентификаторов портов
     */
    public @NonNull @Unmodifiable List<EnergyPortId> portIds() {
        return this.points.stream().map(EnergyPortPoint::portId).toList();
    }

    /**
     * Определяет мировую физическую точку подключения
     * по логическому идентификатору порта.
     *
     * <p>Локальное смещение и внешняя грань
     * преобразуются с учётом ориентации механизма.
     * Повёрнутое смещение прибавляется к его
     * опорной мировой позиции.</p>
     *
     * <p>Возвращаемая позиция соответствует блоку,
     * содержащему порт, а не соседнему блоку,
     * подключённому к порту.</p>
     *
     * @param portId идентификатор искомого порта
     * @param origin опорная мировая позиция механизма
     * @param facing горизонтальная ориентация механизма
     * @return мировая физическая точка подключения
     * или пустой {@link Optional},
     * если порт отсутствует
     * @throws NullPointerException     если любой аргумент
     *                                  равен {@code null}
     * @throws IllegalArgumentException если ориентация
     *                                  не является
     *                                  горизонтальной
     */
    public @NonNull Optional<WorldPortPoint> resolvePort(
        @NonNull EnergyPortId portId,
        @NonNull BlockPos origin,
        @NonNull Direction facing
    )
    {
        Objects.requireNonNull(portId, "portId cannot be null");
        Objects.requireNonNull(origin, "origin cannot be null");
        validateFacing(facing);

        return this.points.stream().filter(point -> point.portId().equals(portId)).findFirst().map(
            point -> toWorldPoint(point, origin, facing));
    }

    /**
     * Выполняет обратный поиск логического порта
     * по его мировой позиции и внешней грани.
     *
     * <p>Порт считается найденным только при
     * одновременном совпадении позиции блока
     * и направления внешней грани.</p>
     *
     * <p>Поиск выполняется с учётом опорной позиции
     * и текущей ориентации механизма.</p>
     *
     * @param worldPos    мировая позиция искомого порта
     * @param outwardFace внешняя грань искомого порта
     * @param origin      опорная мировая позиция механизма
     * @param facing      горизонтальная ориентация механизма
     * @return логический идентификатор найденного порта
     * или пустой {@link Optional},
     * если соответствующая точка отсутствует
     * @throws NullPointerException     если любой аргумент
     *                                  равен {@code null}
     * @throws IllegalArgumentException если ориентация
     *                                  не является
     *                                  горизонтальной
     */
    public @NonNull Optional<EnergyPortId> findPort(
        @NonNull BlockPos worldPos,
        @NonNull Direction outwardFace,
        @NonNull BlockPos origin,
        @NonNull Direction facing
    )
    {
        Objects.requireNonNull(worldPos, "worldPos cannot be null");
        Objects.requireNonNull(outwardFace, "outwardFace cannot be null");
        Objects.requireNonNull(origin, "origin cannot be null");
        validateFacing(facing);

        return this.points.stream().filter(point -> {
            WorldPortPoint worldPortPoint = toWorldPoint(point, origin, facing);

            return worldPortPoint.worldPos().equals(worldPos) &&
                worldPortPoint.outwardFace() == outwardFace;
        }).map(EnergyPortPoint::portId).findFirst();
    }

    /**
     * Проверяет допустимость горизонтальной
     * ориентации механизма.
     *
     * @param facing проверяемая ориентация
     * @throws NullPointerException     если facing
     *                                  равен {@code null}
     * @throws IllegalArgumentException если facing
     *                                  является вертикальным
     */
    private static void validateFacing(@NonNull Direction facing) {
        Objects.requireNonNull(facing, "facing cannot be null");
        if (!facing.getAxis().isHorizontal()) {
            throw new IllegalArgumentException("Facing direction must be horizontal: " + facing);
        }
    }

    /**
     * Поворачивает локальное смещение из исходной
     * ориентации NORTH в указанную ориентацию.
     *
     * <p>Поворот выполняется вокруг вертикальной
     * оси Y. Вертикальная координата не изменяется.</p>
     *
     * @param localOffset исходное локальное смещение
     * @param facing      результирующая ориентация механизма
     * @return неизменяемое повёрнутое смещение
     */
    private static @NonNull BlockPos rotateOffset(
        @NonNull BlockPos localOffset,
        @NonNull Direction facing
    )
    {
        Objects.requireNonNull(localOffset, "localOffset cannot be null");
        validateFacing(facing);

        int x = localOffset.getX();
        int y = localOffset.getY();
        int z = localOffset.getZ();

        return switch (facing) {
            case NORTH -> new BlockPos(x, y, z);
            case EAST -> new BlockPos(-z, y, x);
            case SOUTH -> new BlockPos(-x, y, -z);
            case WEST -> new BlockPos(z, y, -x);
            case UP, DOWN -> throw new IllegalArgumentException(
                "Facing direction must be horizontal: " + facing);
        };
    }

    /**
     * Поворачивает локальную внешнюю грань
     * в соответствии с ориентацией механизма.
     *
     * <p>Вертикальные направления UP и DOWN
     * остаются неизменными.</p>
     *
     * @param localOutwardFace исходная внешняя грань
     * @param facing           результирующая ориентация механизма
     * @return повёрнутая внешняя грань
     */
    private static @NonNull Direction rotateFace(
        @NonNull Direction localOutwardFace,
        @NonNull Direction facing
    )
    {
        Objects.requireNonNull(localOutwardFace, "localOutwardFace cannot be null");
        validateFacing(facing);

        if (localOutwardFace.getAxis().isVertical()) {
            return localOutwardFace;
        }

        return switch (facing) {
            case NORTH -> localOutwardFace;
            case SOUTH -> localOutwardFace.getOpposite();
            case EAST -> localOutwardFace.getClockWise();
            case WEST -> localOutwardFace.getCounterClockWise();
            case UP, DOWN -> throw new IllegalArgumentException(
                "Facing direction must be horizontal: " + facing);
        };
    }

    /**
     * Преобразует локальную физическую точку
     * в мировую систему координат.
     *
     * @param point  локальная физическая точка
     * @param origin опорная мировая позиция механизма
     * @param facing горизонтальная ориентация механизма
     * @return мировая физическая точка подключения
     */
    private static @NonNull WorldPortPoint toWorldPoint(
        @NonNull EnergyPortPoint point,
        @NonNull BlockPos origin,
        @NonNull Direction facing
    )
    {
        BlockPos rotatedOffset = rotateOffset(point.localOffset(), facing);
        BlockPos worldPos = origin.offset(rotatedOffset);
        Direction outwardFace = rotateFace(point.localOutwardFace(), facing);

        return new WorldPortPoint(worldPos, outwardFace);
    }
}
