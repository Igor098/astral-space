package ru.greatdragonx.astralspace.common.energy.generation;

/**
 * Неизменяемая спецификация энергетических характеристик
 * генератора.
 *
 * <p>Определяет максимальную ёмкость общего энергетического
 * буфера, суммарный лимит внешней выдачи энергии
 * и номинальную производительность генератора.</p>
 *
 * <p>Лимит выдачи является общим для активной отправки
 * энергии (Push) и внешнего извлечения (Pull).</p>
 *
 * <p>Все энергетические характеристики должны быть
 * строго положительными. Номинальная производительность
 * не должна превышать ёмкость энергетического буфера,
 * поскольку генератор производит только полные
 * порции энергии.</p>
 *
 * <p>Лимит внешней выдачи не ограничивается ёмкостью
 * энергетического буфера и не зависит от номинальной
 * производительности генератора.</p>
 *
 * <p>Спецификация не определяет расход топлива,
 * продолжительность производственного цикла,
 * условия запуска процесса или фактическую
 * выработку энергии.</p>
 *
 * @param energyCapacity    максимальная ёмкость
 *                          энергетического буфера, FE
 * @param maxOutputPerTick  максимальный суммарный объём
 *                          внешней выдачи энергии, FE/t
 * @param generationPerTick номинальное количество энергии,
 *                          производимое за один успешный
 *                          производственный тик, FE
 */
public record GeneratorEnergySpec(
    long energyCapacity, int maxOutputPerTick, int generationPerTick
)
{
    /**
     * Создаёт спецификацию энергетических характеристик
     * генератора и проверяет её инварианты.
     *
     * <p>Все параметры должны быть строго положительными.
     * Номинальное количество производимой энергии
     * не должно превышать ёмкость энергетического буфера.</p>
     *
     * @throws IllegalArgumentException если хотя бы один
     *                                  параметр не является
     *                                  положительным либо
     *                                  номинальная генерация
     *                                  превышает ёмкость
     *                                  энергетического буфера
     */
    public GeneratorEnergySpec {
        if (energyCapacity <= 0) {
            throw new IllegalArgumentException("energyCapacity must be positive");
        }
        if (maxOutputPerTick <= 0) {
            throw new IllegalArgumentException("maxOutputPerTick must be positive");
        }
        if (generationPerTick <= 0) {
            throw new IllegalArgumentException("generationPerTick must be positive");
        }
        if (generationPerTick > energyCapacity) {
            throw new IllegalArgumentException("generationPerTick cannot exceed energyCapacity");
        }
    }
}
