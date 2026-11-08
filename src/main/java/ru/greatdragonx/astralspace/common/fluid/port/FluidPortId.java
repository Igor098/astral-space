package ru.greatdragonx.astralspace.common.fluid.port;

import java.util.Objects;

/**
 * Уникальный идентификатор жидкостного порта
 * в пределах одного механизма.
 *
 * <p>Используется для идентификации портов,
 * управления их конфигурацией и применения
 * ограничений доступа к жидкостным резервуарам.</p>
 *
 * <p>Идентификатор не определяет физическое направление
 * порта, его режим работы или тип жидкости.
 * Не является глобальным идентификатором
 * в пределах Minecraft-мира.</p>
 *
 * @param value стабильное непустое строковое
 *              обозначение жидкостного порта
 */
public record FluidPortId(String value) {
    public FluidPortId {
        Objects.requireNonNull(
            value,
            "Fluid port value cannot be null"
        );
        if (value.isBlank()) {
            throw new IllegalArgumentException("Fluid port value cannot be blank");
        }
        if (!value.equals(value.strip())) {
            throw new IllegalArgumentException("Fluid port value must not have leading or trailing whitespace");
        }
    }
}
