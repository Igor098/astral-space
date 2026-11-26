package ru.greatdragonx.astralspace.common.energy.generation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GeneratorEnergySpecTest {
    @Test
    void shouldAcceptCoalGeneratorParameters() {
        GeneratorEnergySpec spec = new GeneratorEnergySpec(10_000L, 80, 40);

        assertEquals(10_000L, spec.energyCapacity());
        assertEquals(80, spec.maxOutputPerTick());
        assertEquals(40, spec.generationPerTick());
    }

    @Test
    void shouldAcceptLavaGeneratorParameters() {
        GeneratorEnergySpec spec = new GeneratorEnergySpec(120_000L, 120, 60);

        assertEquals(120_000L, spec.energyCapacity());
        assertEquals(120, spec.maxOutputPerTick());
        assertEquals(60, spec.generationPerTick());
    }

    @Test
    void shouldAcceptMinimumPositiveValues() {
        GeneratorEnergySpec spec = new GeneratorEnergySpec(1L, 1, 1);

        assertEquals(1L, spec.energyCapacity());
        assertEquals(1, spec.maxOutputPerTick());
        assertEquals(1, spec.generationPerTick());
    }

    @Test
    void shouldAllowOutputLimitGreaterThanCapacity() {
        GeneratorEnergySpec spec = new GeneratorEnergySpec(50L, 100, 25);

        assertEquals(50L, spec.energyCapacity());
        assertEquals(100, spec.maxOutputPerTick());
        assertEquals(25, spec.generationPerTick());
    }

    @Test
    void shouldAcceptMaximumRepresentableValues() {
        GeneratorEnergySpec spec = new GeneratorEnergySpec(
            Long.MAX_VALUE,
            Integer.MAX_VALUE,
            Integer.MAX_VALUE
        );

        assertEquals(Long.MAX_VALUE, spec.energyCapacity());
        assertEquals(Integer.MAX_VALUE, spec.maxOutputPerTick());
        assertEquals(Integer.MAX_VALUE, spec.generationPerTick());
    }

    @Test
    void shouldRejectZeroCapacity() {
        assertThrows(IllegalArgumentException.class, () -> new GeneratorEnergySpec(0L, 80, 40));
    }

    @Test
    void shouldRejectNegativeCapacity() {
        assertThrows(IllegalArgumentException.class, () -> new GeneratorEnergySpec(-1L, 80, 40));
        assertThrows(
            IllegalArgumentException.class,
            () -> new GeneratorEnergySpec(Long.MIN_VALUE, 80, 40)
        );
    }

    @Test
    void shouldRejectZeroOutputLimit() {
        assertThrows(IllegalArgumentException.class, () -> new GeneratorEnergySpec(10_000L, 0, 40));
    }

    @Test
    void shouldRejectNegativeOutputLimit() {
        assertThrows(
            IllegalArgumentException.class,
            () -> new GeneratorEnergySpec(10_000L, -1, 40)
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> new GeneratorEnergySpec(10_000L, Integer.MIN_VALUE, 40)
        );
    }

    @Test
    void shouldRejectZeroGeneration() {
        assertThrows(IllegalArgumentException.class, () -> new GeneratorEnergySpec(10_000L, 80, 0));
    }

    @Test
    void shouldRejectNegativeGeneration() {
        assertThrows(
            IllegalArgumentException.class,
            () -> new GeneratorEnergySpec(10_000L, 80, -1)
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> new GeneratorEnergySpec(10_000L, 80, Integer.MIN_VALUE)
        );
    }

    @Test
    void shouldRejectGenerationGreaterThanCapacity() {
        assertThrows(IllegalArgumentException.class, () -> new GeneratorEnergySpec(50L, 100, 51));
        assertThrows(
            IllegalArgumentException.class,
            () -> new GeneratorEnergySpec((long) Integer.MAX_VALUE - 1, 1, Integer.MAX_VALUE)
        );
    }

    @Test
    void shouldAllowGenerationEqualToCapacity() {
        GeneratorEnergySpec spec = new GeneratorEnergySpec(50L, 100, 50);

        assertEquals(spec.energyCapacity(), spec.generationPerTick());
    }

    @Test
    void shouldAllowGenerationGreaterThanOutputLimit() {
        GeneratorEnergySpec spec = new GeneratorEnergySpec(10_000L, 40, 80);

        assertEquals(40, spec.maxOutputPerTick());
        assertEquals(80, spec.generationPerTick());
    }

    @Test
    void shouldAllowGenerationEqualToOutputLimit() {
        GeneratorEnergySpec spec = new GeneratorEnergySpec(10_000L, 40, 40);

        assertEquals(spec.maxOutputPerTick(), spec.generationPerTick());
    }

    @Test
    void shouldCompareGenerationWithLongCapacityWithoutNarrowing() {
        long capacity = (long) Integer.MAX_VALUE + 1;
        GeneratorEnergySpec spec = new GeneratorEnergySpec(capacity, 80, Integer.MAX_VALUE);

        assertEquals(capacity, spec.energyCapacity());
        assertEquals(Integer.MAX_VALUE, spec.generationPerTick());
    }

    @Test
    void shouldCompareSpecificationsByValue() {
        GeneratorEnergySpec first = new GeneratorEnergySpec(10_000L, 80, 40);
        GeneratorEnergySpec same = new GeneratorEnergySpec(10_000L, 80, 40);

        assertEquals(first, same);
        assertEquals(first.hashCode(), same.hashCode());
        assertNotEquals(first, new GeneratorEnergySpec(20_000L, 80, 40));
        assertNotEquals(first, new GeneratorEnergySpec(10_000L, 100, 40));
        assertNotEquals(first, new GeneratorEnergySpec(10_000L, 80, 60));
    }
}
