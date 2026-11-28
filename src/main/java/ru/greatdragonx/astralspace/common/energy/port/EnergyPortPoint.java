package ru.greatdragonx.astralspace.common.energy.port;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.jspecify.annotations.NonNull;

import java.util.Objects;

/**
 * Неизменяемое описание физической точки
 * подключения энергетического порта.
 *
 * <p>Связывает логический идентификатор порта
 * с локальным смещением относительно опорной
 * позиции механизма и внешней гранью блока.</p>
 *
 * <p>Локальные координаты и направление задаются
 * относительно исходной ориентации механизма
 * {@link Direction#NORTH}.</p>
 *
 * <p>Для одноблочного механизма локальное смещение
 * равно {@link BlockPos#ZERO}. Для мультиблочных
 * механизмов допускаются ненулевые смещения
 * по всем трём координатным осям.</p>
 *
 * <p>Допускаются все шесть направлений внешней
 * грани, включая вертикальные направления.</p>
 *
 * <p>Переданная позиция приводится к неизменяемому
 * представлению, чтобы последующие изменения
 * исходного объекта не влияли на физическую точку.</p>
 *
 * <p>Не определяет режим порта, его доступность
 * или текущую мировую позицию.</p>
 *
 * @param portId           логический идентификатор порта
 * @param localOffset      локальное смещение
 *                         относительно опорной позиции
 * @param localOutwardFace внешняя грань в исходной
 *                         ориентации механизма
 */
public record EnergyPortPoint(
    @NonNull EnergyPortId portId, @NonNull BlockPos localOffset, @NonNull Direction localOutwardFace
)
{
    /**
     * Создаёт неизменяемое описание физической точки
     * энергетического подключения.
     *
     * <p>Локальное смещение сохраняется в неизменяемом
     * представлении посредством {@link BlockPos#immutable()}.</p>
     *
     * @throws NullPointerException если любой
     *                              из аргументов
     *                              равен {@code null}
     */
    public EnergyPortPoint {
        Objects.requireNonNull(portId, "portId cannot be null");
        Objects.requireNonNull(localOffset, "localOffset cannot be null");
        Objects.requireNonNull(localOutwardFace, "localOutwardFace cannot be null");
        localOffset = localOffset.immutable();
    }
}
