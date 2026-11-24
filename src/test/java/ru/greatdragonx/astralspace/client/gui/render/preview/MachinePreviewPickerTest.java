package ru.greatdragonx.astralspace.client.gui.render.preview;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MachinePreviewPickerTest {
    private final MachinePreviewPicker picker = new MachinePreviewPicker();

    @Test
    void shouldDetectSouthFaceWithoutRotation() {
        MachinePreviewRenderState state = state(
            100,
            50,
            64,
            64,
            1.0f,
            0,
            0
        );

        Direction result = picker.detectHitDirection(
            132,
            82,
            state,
            1
        );

        assertEquals(
            Direction.SOUTH,
            result
        );
    }

    @Test
    void shouldDetectWestFaceAfterYRotation() {
        MachinePreviewRenderState state = state(
            0,
            0,
            64,
            64,
            1.0f,
            0,
            90
        );

        Direction result = picker.detectHitDirection(
            32,
            32,
            state,
            1
        );

        assertEquals(
            Direction.WEST,
            result
        );
    }

    @Test
    void shouldDetectEastFaceAfterNegativeYRotation() {
        MachinePreviewRenderState state = state(
            0,
            0,
            64,
            64,
            1.0f,
            0,
            -90
        );

        Direction result = picker.detectHitDirection(
            32,
            32,
            state,
            1
        );

        assertEquals(
            Direction.EAST,
            result
        );
    }

    @Test
    void shouldDetectUpFaceAfterXRotation() {
        MachinePreviewRenderState state = state(
            0,
            0,
            64,
            64,
            1.0f,
            90,
            0
        );

        Direction result = picker.detectHitDirection(
            32,
            32,
            state,
            1
        );

        assertEquals(
            Direction.UP,
            result
        );
    }

    @Test
    void shouldDetectFaceWithCombinedRotations() {
        MachinePreviewRenderState state = state(
            0,
            0,
            64,
            64,
            1.0f,
            25,
            35
        );

        Direction result = picker.detectHitDirection(
            32,
            32,
            state,
            1
        );

        assertEquals(
            Direction.SOUTH,
            result
        );
    }

    @Test
    void shouldReturnNullOutsidePreview() {
        MachinePreviewRenderState state = state(
            100,
            50,
            64,
            64,
            1.0f,
            0,
            0
        );

        assertAll(
            () -> assertNull(picker.detectHitDirection(
                99,
                82,
                state,
                1
            )),
            () -> assertNull(picker.detectHitDirection(
                164,
                82,
                state,
                1
            )),
            () -> assertNull(picker.detectHitDirection(
                132,
                49,
                state,
                1
            )),
            () -> assertNull(picker.detectHitDirection(
                132,
                114,
                state,
                1
            ))
        );
    }

    @Test
    void shouldReturnNullWhenMouseMissesBlock() {
        MachinePreviewRenderState state = state(
            100,
            50,
            64,
            64,
            1.0f,
            0,
            0
        );

        Direction result = picker.detectHitDirection(
            102,
            52,
            state,
            1
        );

        assertNull(result);
    }

    @Test
    void shouldBeIndependentOfGuiScale() {
        MachinePreviewRenderState state = state(
            100,
            50,
            64,
            64,
            1.0f,
            25,
            35
        );

        for (int guiScale : new int[]{1, 2, 3, 4}) {
            Direction result = picker.detectHitDirection(
                132,
                82,
                state,
                guiScale
            );

            assertEquals(
                Direction.SOUTH,
                result,
                "Unexpected face at GUI scale " + guiScale
            );
        }
    }

    @Test
    void shouldRespectRenderStateScale() {
        MachinePreviewRenderState state = state(
            0,
            0,
            64,
            64,
            1.5f,
            0,
            0
        );

        // При scale=1.5 центр блока смещается
        // в координаты GUI (32, 16).
        Direction result = picker.detectHitDirection(
            32,
            16,
            state,
            1
        );

        assertEquals(
            Direction.SOUTH,
            result
        );
    }

    @Test
    void shouldRejectInvalidGuiScale() {
        MachinePreviewRenderState state = state(
            0,
            0,
            64,
            64,
            1.0f,
            0,
            0
        );

        assertThrows(
            IllegalArgumentException.class,
            () -> picker.detectHitDirection(
                32,
                32,
                state,
                0
            )
        );

        assertThrows(
            IllegalArgumentException.class,
            () -> picker.detectHitDirection(
                32,
                32,
                state,
                -1
            )
        );
    }

    @Test
    void shouldDetectSouthFaceAtOffsetPosition() {
        MachinePreviewRenderState state = state(
            0,
            0,
            64,
            64,
            1.0f,
            25,
            35
        );

        Direction result = picker.detectHitDirection(
            40,
            37,
            state,
            1
        );

        assertEquals(
            Direction.SOUTH,
            result
        );
    }

    @Test
    void shouldDetectWestFaceAtOffsetPosition() {
        MachinePreviewRenderState state = state(
            0,
            0,
            64,
            64,
            1.0f,
            25,
            35
        );

        Direction result = picker.detectHitDirection(
            21,
            35,
            state,
            1
        );

        assertEquals(
            Direction.WEST,
            result
        );
    }

    @Test
    void shouldDetectUpFaceAtOffsetPosition() {
        MachinePreviewRenderState state = state(
            0,
            0,
            64,
            64,
            1.0f,
            25,
            35
        );

        Direction result = picker.detectHitDirection(
            32,
            19,
            state,
            1
        );

        assertEquals(
            Direction.UP,
            result
        );
    }

    @Test
    void shouldReturnNullOutsideRotatedBlockSilhouette() {
        MachinePreviewRenderState state = state(
            0,
            0,
            64,
            64,
            1.0f,
            25,
            35
        );

        Direction result = picker.detectHitDirection(
            15,
            15,
            state,
            1
        );

        assertNull(result);
    }

    @Test
    void shouldDetectRotatedFacesAtDifferentGuiScales() {
        MachinePreviewRenderState state = state(
            0,
            0,
            64,
            64,
            1.0f,
            25,
            35
        );

        for (int guiScale : new int[]{1, 2, 3, 4}) {
            assertEquals(
                Direction.SOUTH,
                picker.detectHitDirection(
                    40,
                    37,
                    state,
                    guiScale
                )
            );

            assertEquals(
                Direction.WEST,
                picker.detectHitDirection(
                    21,
                    35,
                    state,
                    guiScale
                )
            );

            assertEquals(
                Direction.UP,
                picker.detectHitDirection(
                    32,
                    19,
                    state,
                    guiScale
                )
            );
        }
    }

    private static MachinePreviewRenderState state(
        int x,
        int y,
        int width,
        int height,
        float scale,
        float rotationX,
        float rotationY
    )
    {
        return new MachinePreviewRenderState(
            x,
            x + width,
            y,
            y + height,
            scale,
            rotationX,
            rotationY,
            null,
            null,
            Blocks.STONE.defaultBlockState(),
            null
        );
    }
}
