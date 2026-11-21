package ru.greatdragonx.astralspace.common.fluid.storage;

import net.neoforged.neoforge.transfer.fluid.FluidResource;
import org.jspecify.annotations.NonNull;

import java.util.Objects;
import java.util.function.Predicate;

/**
 * Описывает неизменяемую конфигурацию одного
 * жидкостного резервуара механизма.
 *
 * <p>Определение содержит стабильный идентификатор,
 * производственную роль, максимальную вместимость
 * и правило допустимости жидкостей.</p>
 *
 * <p>Конфигурация отделена от текущего содержимого.
 * Количество и тип хранимой жидкости находятся
 * в соответствующем {@link MachineFluidStorage}.</p>
 *
 * <p>Вместимость должна быть строго положительной.
 * Правило {@code acceptedFluid} определяет,
 * может ли резервуар хранить указанный тип жидкости,
 * но не проверяет наличие свободного места.</p>
 *
 * <p>Производственная роль резервуара не является
 * ограничением внешнего порта. Маршрутизация
 * и разрешения внешних операций определяются
 * отдельно.</p>
 *
 * <p>Переданный предикат сохраняется по ссылке.
 * Определение не создаёт его защитную копию
 * и не гарантирует неизменность внешнего состояния,
 * от которого может зависеть результат проверки.</p>
 *
 * @param id            уникальный идентификатор резервуара
 * @param role          производственная роль резервуара
 * @param capacity      максимальная вместимость резервуара
 * @param acceptedFluid правило допустимости жидкости
 */
public record FluidTankDefinition(
    @NonNull FluidTankId id,
    @NonNull FluidTankRole role,
    int capacity,
    @NonNull Predicate<FluidResource> acceptedFluid
)
{
    /**
     * Создаёт определение жидкостного резервуара.
     *
     * <p>Проверяет обязательные параметры
     * и положительность максимальной вместимости.</p>
     *
     * <p>Предикат допустимости жидкости сохраняется,
     * но не вызывается при создании определения.</p>
     *
     * <p>Конструктор не создаёт физическое хранилище
     * и не выделяет место для содержимого резервуара.</p>
     *
     * @throws NullPointerException     если {@code id},
     *                                  {@code role} или
     *                                  {@code acceptedFluid}
     *                                  равен {@code null}
     * @throws IllegalArgumentException если
     *                                  {@code capacity}
     *                                  меньше либо равна нулю
     */
    public FluidTankDefinition {
        Objects.requireNonNull(
            id,
            "id cannot be null"
        );
        Objects.requireNonNull(
            role,
            "role cannot be null"
        );
        Objects.requireNonNull(
            acceptedFluid,
            "acceptedFluid cannot be null"
        );
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be positive");
        }
    }
}
