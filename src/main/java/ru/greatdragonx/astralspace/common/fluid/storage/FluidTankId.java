package ru.greatdragonx.astralspace.common.fluid.storage;

import java.util.Objects;

/**
 * Уникальный идентификатор жидкостного резервуара
 * в пределах одного механизма.
 *
 * <p>Используется для независимой идентификации
 * резервуаров, сопоставления их с внутренними
 * индексами хранилища и последующего сохранения
 * и восстановления содержимого.</p>
 *
 * <p>Идентификатор не зависит от индекса резервуара,
 * его вместимости, производственной роли
 * или текущего содержимого.</p>
 *
 * <p>Значение должно быть непустым и не должно
 * содержать начальных или конечных пробельных
 * символов. Регистр символов сохраняется.</p>
 *
 * <p>Идентификатор не является глобальным
 * идентификатором в пределах Minecraft-мира.</p>
 *
 * @param value стабильное строковое обозначение
 *              жидкостного резервуара
 */
public record FluidTankId(String value) {
    /**
     * Создаёт идентификатор жидкостного резервуара.
     *
     * <p>Значение проверяется на отсутствие
     * {@code null}, пустой строки и пробельных
     * символов по краям.</p>
     *
     * <p>Исходная строка не изменяется
     * и не нормализуется.</p>
     *
     * @throws NullPointerException     если
     *                                  {@code value}
     *                                  равен {@code null}
     * @throws IllegalArgumentException если значение
     *                                  пустое, состоит
     *                                  только из пробельных
     *                                  символов либо
     *                                  содержит их
     *                                  по краям
     */
    public FluidTankId {
        Objects.requireNonNull(
            value,
            "Fluid tank value cannot be null"
        );
        if (value.isBlank()) {
            throw new IllegalArgumentException("Fluid tank value cannot be blank");
        }
        if (!value.equals(value.strip())) {
            throw new IllegalArgumentException("Fluid tank value must not have leading or trailing whitespace");
        }
    }
}
