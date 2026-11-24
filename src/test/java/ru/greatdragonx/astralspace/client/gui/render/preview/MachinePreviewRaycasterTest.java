package ru.greatdragonx.astralspace.client.gui.render.preview;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class MachinePreviewRaycasterTest {

    @ParameterizedTest(name = "{0}")
    @MethodSource("faceHitCases")
    void detectHitDirection_returnsExpectedFace(
        String description,
        Vec3 rayStart,
        Vec3 rayEnd,
        Direction expected
    )
    {
        Direction actual = MachinePreviewRaycaster.detectHitDirection(
            rayStart,
            rayEnd
        );

        assertEquals(
            expected,
            actual,
            description
        );
    }

    private static Stream<Arguments> faceHitCases() {
        return Stream.of(
            Arguments.of(
                "South face (+Z)",
                new Vec3(
                    0.5,
                    0.5,
                    2.0
                ),
                new Vec3(
                    0.5,
                    0.5,
                    -2.0
                ),
                Direction.SOUTH
            ),
            Arguments.of(
                "North face (-Z)",
                new Vec3(
                    0.5,
                    0.5,
                    -2.0
                ),
                new Vec3(
                    0.5,
                    0.5,
                    2.0
                ),
                Direction.NORTH
            ),
            Arguments.of(
                "East face (+X)",
                new Vec3(
                    2.0,
                    0.5,
                    0.5
                ),
                new Vec3(
                    -2.0,
                    0.5,
                    0.5
                ),
                Direction.EAST
            ),
            Arguments.of(
                "West face (-X)",
                new Vec3(
                    -2.0,
                    0.5,
                    0.5
                ),
                new Vec3(
                    2.0,
                    0.5,
                    0.5
                ),
                Direction.WEST
            ),
            Arguments.of(
                "Top face (+Y)",
                new Vec3(
                    0.5,
                    2.0,
                    0.5
                ),
                new Vec3(
                    0.5,
                    -2.0,
                    0.5
                ),
                Direction.UP
            ),
            Arguments.of(
                "Bottom face (-Y)",
                new Vec3(
                    0.5,
                    -2.0,
                    0.5
                ),
                new Vec3(
                    0.5,
                    2.0,
                    0.5
                ),
                Direction.DOWN
            )
        );
    }

    @Test
    void detectHitDirection_returnsNullWhenRayMissesBlock() {
        Vec3 rayStart = new Vec3(
            2.0,
            2.0,
            2.0
        );
        Vec3 rayEnd = new Vec3(
            3.0,
            3.0,
            3.0
        );

        assertNull(MachinePreviewRaycaster.detectHitDirection(
            rayStart,
            rayEnd
        ));
    }

    @Test
    void detectHitDirection_returnsNullWhenRayPassesBesideBlock() {
        Vec3 rayStart = new Vec3(
            1.01,
            0.5,
            2.0
        );
        Vec3 rayEnd = new Vec3(
            1.01,
            0.5,
            -2.0
        );

        assertNull(MachinePreviewRaycaster.detectHitDirection(
            rayStart,
            rayEnd
        ));
    }

    @Test
    void detectHitDirection_rejectsNullRayStart() {
        Vec3 rayEnd = new Vec3(
            0.5,
            0.5,
            0.5
        );

        assertThrows(
            NullPointerException.class,
            () -> MachinePreviewRaycaster.detectHitDirection(
                null,
                rayEnd
            )
        );
    }

    @Test
    void detectHitDirection_rejectsNullRayEnd() {
        Vec3 rayStart = new Vec3(
            0.5,
            0.5,
            2.0
        );

        assertThrows(
            NullPointerException.class,
            () -> MachinePreviewRaycaster.detectHitDirection(
                rayStart,
                null
            )
        );
    }
}
