package ru.greatdragonx.astralspace.common.fluid.port;

/**
 * Определяет режим внешнего доступа к жидкости
 * через жидкостный порт механизма.
 */
public enum FluidPortMode {
    /**
     * Разрешает подачу жидкости во входной резервуар.
     */
    INPUT,

    /**
     * Разрешает извлечение жидкости из выходного резервуара.
     */
    OUTPUT,

    /**
     * Запрещает передачу жидкости через порт.
     */
    DISABLED,

    /**
     * Разрешает сервисное извлечение жидкости
     * из специально разрешённого входного резервуара.
     */
    SERVICE;

    /**
     * Проверяет, разрешена ли подача жидкости через порт.
     *
     * @return {@code true}, если выбран режим {@link #INPUT}
     */
    public boolean allowsInsert() {
        return this == INPUT;
    }

    /**
     * Проверяет, разрешено ли извлечение жидкости через порт.
     *
     * @return {@code true}, если выбран режим
     * {@link #OUTPUT} или {@link #SERVICE}
     */
    public boolean allowsExtract() {
        return this == OUTPUT || this == SERVICE;
    }
}
