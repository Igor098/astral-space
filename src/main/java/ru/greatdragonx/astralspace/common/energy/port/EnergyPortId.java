package ru.greatdragonx.astralspace.common.energy.port;

import java.util.Objects;

/**
 * Уникальный идентификатор энергетического порта
 * в пределах одного энергетического механизма.
 *
 * <p>Используется для независимого учёта портов,
 * распределения энергии и применения ограничений
 * передачи.</p>
 *
 * <p>Идентификатор не определяет физическое направление
 * порта и не является глобальным идентификатором
 * в пределах Minecraft-мира.</p>
 *
 * @param value стабильное непустое строковое
 *              обозначение энергетического порта
 */
public record EnergyPortId(String value) {
    public EnergyPortId {
        Objects.requireNonNull(
            value,
            "Energy port value cannot be null"
        );
        if (value.isBlank()) {
            throw new IllegalArgumentException("Energy port value cannot be blank");
        }
        if (!value.equals(value.strip())) {
            throw new IllegalArgumentException("Energy port value must not have leading or trailing whitespace");
        }
    }
}
