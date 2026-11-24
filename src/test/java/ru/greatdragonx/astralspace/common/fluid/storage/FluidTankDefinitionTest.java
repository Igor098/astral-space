package ru.greatdragonx.astralspace.common.fluid.storage;

import net.neoforged.neoforge.transfer.fluid.FluidResource;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;

class FluidTankDefinitionTest {

    private static final FluidTankId LAVA = new FluidTankId("lava_input");

    @Test
    void shouldStoreAllDefinitionProperties() {
        Predicate<FluidResource> filter = resource -> true;

        FluidTankDefinition definition = new FluidTankDefinition(
            LAVA,
            FluidTankRole.INPUT,
            8_000,
            filter
        );

        assertAll(
            () -> assertSame(
                LAVA,
                definition.id()
            ),
            () -> assertEquals(
                FluidTankRole.INPUT,
                definition.role()
            ),
            () -> assertEquals(
                8_000,
                definition.capacity()
            ),
            () -> assertSame(
                filter,
                definition.acceptedFluid()
            )
        );
    }

    @Test
    void shouldSupportAllTankRoles() {
        for (FluidTankRole role : FluidTankRole.values()) {
            FluidTankDefinition definition = new FluidTankDefinition(
                LAVA,
                role,
                1_000,
                resource -> true
            );

            assertEquals(
                role,
                definition.role()
            );
        }

        assertEquals(
            3,
            FluidTankRole.values().length
        );
    }

    @Test
    void shouldRejectNullIdentifier() {
        assertThrows(
            NullPointerException.class,
            () -> new FluidTankDefinition(
                null,
                FluidTankRole.INPUT,
                1_000,
                resource -> true
            )
        );
    }

    @Test
    void shouldRejectNullRole() {
        assertThrows(
            NullPointerException.class,
            () -> new FluidTankDefinition(
                LAVA,
                null,
                1_000,
                resource -> true
            )
        );
    }

    @Test
    void shouldRejectNullAcceptedFluidPredicate() {
        assertThrows(
            NullPointerException.class,
            () -> new FluidTankDefinition(
                LAVA,
                FluidTankRole.INPUT,
                1_000,
                null
            )
        );
    }

    @Test
    void shouldRejectZeroCapacity() {
        assertThrows(
            IllegalArgumentException.class,
            () -> new FluidTankDefinition(
                LAVA,
                FluidTankRole.INPUT,
                0,
                resource -> true
            )
        );
    }

    @Test
    void shouldRejectNegativeCapacity() {
        assertAll(
            () -> assertThrows(
                IllegalArgumentException.class,
                () -> new FluidTankDefinition(
                    LAVA,
                    FluidTankRole.INPUT,
                    -1,
                    resource -> true
                )
            ),
            () -> assertThrows(
                IllegalArgumentException.class,
                () -> new FluidTankDefinition(
                    LAVA,
                    FluidTankRole.INPUT,
                    Integer.MIN_VALUE,
                    resource -> true
                )
            )
        );
    }

    @Test
    void shouldAcceptSmallestAndLargestPositiveCapacities() {
        FluidTankDefinition smallest = new FluidTankDefinition(
            LAVA,
            FluidTankRole.INPUT,
            1,
            resource -> true
        );
        FluidTankDefinition largest = new FluidTankDefinition(
            LAVA,
            FluidTankRole.INPUT,
            Integer.MAX_VALUE,
            resource -> true
        );

        assertAll(
            () -> assertEquals(
                1,
                smallest.capacity()
            ),
            () -> assertEquals(
                Integer.MAX_VALUE,
                largest.capacity()
            )
        );
    }

    @Test
    void shouldNotEvaluatePredicateDuringConstruction() {
        AtomicInteger evaluations = new AtomicInteger();
        Predicate<FluidResource> filter = resource -> {
            evaluations.incrementAndGet();
            return false;
        };

        FluidTankDefinition definition = new FluidTankDefinition(
            LAVA,
            FluidTankRole.INPUT,
            1_000,
            filter
        );

        assertAll(
            () -> assertSame(
                filter,
                definition.acceptedFluid()
            ),
            () -> assertEquals(
                0,
                evaluations.get()
            )
        );
    }

    @Test
    void shouldCompareDefinitionsByRecordComponents() {
        Predicate<FluidResource> sharedFilter = resource -> true;
        FluidTankDefinition first = new FluidTankDefinition(
            new FluidTankId("lava_input"),
            FluidTankRole.INPUT,
            8_000,
            sharedFilter
        );
        FluidTankDefinition second = new FluidTankDefinition(
            new FluidTankId("lava_input"),
            FluidTankRole.INPUT,
            8_000,
            sharedFilter
        );

        assertAll(
            () -> assertEquals(
                first,
                second
            ),
            () -> assertEquals(
                first.hashCode(),
                second.hashCode()
            )
        );
    }
}
