package ru.greatdragonx.astralspace.common.energy.generation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GeneratorEnergySpecTest {
    @Test
    void shouldAcceptCoalGeneratorParameters() {
        GeneratorEnergySpec spec = new GeneratorEnergySpec(10_000L, 80);

        assertEquals(10_000L, spec.energyCapacity());
        assertEquals(80, spec.maxOutputPerTick());
    }

    @Test
    void shouldAcceptLavaGeneratorParameters() {
        GeneratorEnergySpec spec = new GeneratorEnergySpec(120_000L, 120);

        assertEquals(120_000L, spec.energyCapacity());
        assertEquals(120, spec.maxOutputPerTick());
    }

    @Test
    void shouldAcceptMinimumPositiveValues() {
        GeneratorEnergySpec spec = new GeneratorEnergySpec(1L, 1);

        assertEquals(1L, spec.energyCapacity());
        assertEquals(1, spec.maxOutputPerTick());
    }

    @Test
    void shouldAllowOutputLimitGreaterThanCapacity() {
        GeneratorEnergySpec spec = new GeneratorEnergySpec(50L, 100);

        assertEquals(50L, spec.energyCapacity());
        assertEquals(100, spec.maxOutputPerTick());
    }

    @Test
    void shouldAcceptMaximumRepresentableValues() {
        GeneratorEnergySpec spec = new GeneratorEnergySpec(Long.MAX_VALUE, Integer.MAX_VALUE);

        assertEquals(Long.MAX_VALUE, spec.energyCapacity());
        assertEquals(Integer.MAX_VALUE, spec.maxOutputPerTick());
    }

    @Test
    void shouldRejectZeroCapacity() {
        assertThrows(IllegalArgumentException.class, () -> new GeneratorEnergySpec(0L, 80));
    }

    @Test
    void shouldRejectNegativeCapacity() {
        assertThrows(IllegalArgumentException.class, () -> new GeneratorEnergySpec(-1L, 80));
        assertThrows(
            IllegalArgumentException.class,
            () -> new GeneratorEnergySpec(Long.MIN_VALUE, 80)
        );
    }

    @Test
    void shouldRejectZeroOutputLimit() {
        assertThrows(IllegalArgumentException.class, () -> new GeneratorEnergySpec(10_000L, 0));
    }

    @Test
    void shouldRejectNegativeOutputLimit() {
        assertThrows(IllegalArgumentException.class, () -> new GeneratorEnergySpec(10_000L, -1));
        assertThrows(
            IllegalArgumentException.class,
            () -> new GeneratorEnergySpec(10_000L, Integer.MIN_VALUE)
        );
    }

    @Test
    void shouldCompareSpecificationsByValue() {
        GeneratorEnergySpec first = new GeneratorEnergySpec(10_000L, 80);
        GeneratorEnergySpec same = new GeneratorEnergySpec(10_000L, 80);

        assertEquals(first, same);
        assertEquals(first.hashCode(), same.hashCode());
        assertNotEquals(first, new GeneratorEnergySpec(20_000L, 80));
        assertNotEquals(first, new GeneratorEnergySpec(10_000L, 100));
    }
}
