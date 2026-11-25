package ru.greatdragonx.astralspace.common.energy.generation;

/**
 * Неизменяемая спецификация энергетического хранилища
 * и внешней выдачи энергии генератора.
 *
 * <p>Определяет максимальную ёмкость общего энергетического
 * буфера и суммарный лимит внешней передачи энергии
 * за игровой тик.</p>
 *
 * <p>Лимит выдачи является общим для активной отправки
 * энергии (Push) и внешнего извлечения (Pull).</p>
 *
 * <p>Ёмкость хранилища и максимальная скорость выдачи
 * являются независимыми характеристиками.</p>
 *
 * <p>Спецификация не определяет скорость производства
 * энергии, расход топлива или продолжительность
 * производственного цикла.</p>
 *
 * @param energyCapacity   максимальная ёмкость
 *                         энергетического буфера, FE
 * @param maxOutputPerTick максимальный суммарный объём
 *                         внешней выдачи энергии, FE/t
 */
public record GeneratorEnergySpec(
    long energyCapacity, int maxOutputPerTick
)
{
    /**
     * Создаёт спецификацию энергетических характеристик
     * генератора и проверяет её инварианты.
     *
     * @throws IllegalArgumentException если ёмкость
     *                                  или лимит выдачи
     *                                  не являются положительными
     */
    public GeneratorEnergySpec {
        if (energyCapacity <= 0) {
            throw new IllegalArgumentException("energyCapacity must be positive");
        }
        if (maxOutputPerTick <= 0) {
            throw new IllegalArgumentException("maxOutputPerTick must be positive");
        }
    }
}
