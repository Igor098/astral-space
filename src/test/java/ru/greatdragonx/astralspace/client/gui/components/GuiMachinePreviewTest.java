package ru.greatdragonx.astralspace.client.gui.components;

import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;

class GuiMachinePreviewTest {
    private static final int X = 100;
    private static final int Y = 50;
    private static final int SIZE = 64;
    private static final double CENTER_X = X + SIZE / 2.0;
    private static final double CENTER_Y = Y + SIZE / 2.0;
    private static final float EPSILON = 0.0001f;

    private GuiMachinePreview preview;

    @BeforeEach
    void setUp() {
        this.preview = new GuiMachinePreview();
    }

    @ParameterizedTest
    @CsvSource({"100.0, 50.0", "163.999, 113.999", "100.0, 113.999", "163.999, 50.0", "132.0, 82.0"})
    void shouldAcceptLeftClickInsidePreview(
        double mouseX,
        double mouseY
    )
    {
        assertTrue(preview.mouseClicked(
            mouseEvent(
                mouseX,
                mouseY,
                GLFW.GLFW_MOUSE_BUTTON_LEFT
            ),
            X,
            Y,
            SIZE
        ));
    }

    @ParameterizedTest
    @CsvSource({"99.999, 82.0", "164.0, 82.0", "132.0, 49.999", "132.0, 114.0"})
    void shouldRejectClickOutsidePreview(
        double mouseX,
        double mouseY
    )
    {
        assertFalse(preview.mouseClicked(
            mouseEvent(
                mouseX,
                mouseY,
                GLFW.GLFW_MOUSE_BUTTON_LEFT
            ),
            X,
            Y,
            SIZE
        ));
    }

    @ParameterizedTest
    @ValueSource(ints = {GLFW.GLFW_MOUSE_BUTTON_RIGHT, GLFW.GLFW_MOUSE_BUTTON_MIDDLE})
    void shouldRejectNonLeftMouseButton(int button) {
        assertFalse(preview.mouseClicked(
            mouseEvent(
                CENTER_X,
                CENTER_Y,
                button
            ),
            X,
            Y,
            SIZE
        ));
    }

    @Test
    void shouldNotDragWithoutPreviousClick() {
        assertFalse(preview.mouseDragged(
            mouseEvent(
                CENTER_X + 10,
                CENTER_Y,
                GLFW.GLFW_MOUSE_BUTTON_LEFT
            ),
            10.0,
            0.0
        ));
    }

    @Test
    void shouldRejectNonLeftDragWithoutInterruptingLeftInteraction() {
        beginClick();

        assertFalse(preview.mouseDragged(
            mouseEvent(
                CENTER_X + 10,
                CENTER_Y,
                GLFW.GLFW_MOUSE_BUTTON_RIGHT
            ),
            10.0,
            0.0
        ));

        assertTrue(preview.mouseDragged(
            mouseEvent(
                CENTER_X + 10,
                CENTER_Y,
                GLFW.GLFW_MOUSE_BUTTON_LEFT
            ),
            10.0,
            0.0
        ));

        assertTrue(preview.mouseReleased(
                mouseEvent(
                    CENTER_X + 10,
                    CENTER_Y,
                    GLFW.GLFW_MOUSE_BUTTON_LEFT
                ),
                1
            )
            .consumed());
    }

    @Test
    void shouldNotRotateBelowDragThreshold() {
        beginClick();

        assertTrue(preview.mouseDragged(
            mouseEvent(
                CENTER_X + 3,
                CENTER_Y,
                GLFW.GLFW_MOUSE_BUTTON_LEFT
            ),
            3.0,
            0.0
        ));

        assertEquals(
            25.0f,
            rotation("rotationX"),
            EPSILON
        );
        assertEquals(
            35.0f,
            rotation("rotationY"),
            EPSILON
        );
    }

    @Test
    void shouldNotRotateAtExactlyFourPixels() {
        beginClick();

        assertTrue(preview.mouseDragged(
            mouseEvent(
                CENTER_X + 4,
                CENTER_Y,
                GLFW.GLFW_MOUSE_BUTTON_LEFT
            ),
            4.0,
            0.0
        ));

        assertEquals(
            25.0f,
            rotation("rotationX"),
            EPSILON
        );
        assertEquals(
            35.0f,
            rotation("rotationY"),
            EPSILON
        );
    }

    @Test
    void shouldStartRotationAfterThresholdIsExceeded() {
        beginClick();

        preview.mouseDragged(
            mouseEvent(
                CENTER_X + 4,
                CENTER_Y,
                GLFW.GLFW_MOUSE_BUTTON_LEFT
            ),
            4.0,
            0.0
        );
        preview.mouseDragged(
            mouseEvent(
                CENTER_X + 6,
                CENTER_Y,
                GLFW.GLFW_MOUSE_BUTTON_LEFT
            ),
            2.0,
            0.0
        );

        assertEquals(
            25.0f,
            rotation("rotationX"),
            EPSILON
        );
        assertEquals(
            35.0f + 2.0f * 0.8f,
            rotation("rotationY"),
            EPSILON
        );
    }

    @Test
    void shouldKeepRotatingAfterCursorReturnsNearStart() {
        beginClick();

        preview.mouseDragged(
            mouseEvent(
                CENTER_X + 10,
                CENTER_Y,
                GLFW.GLFW_MOUSE_BUTTON_LEFT
            ),
            10.0,
            0.0
        );
        float rotationAfterFirstDrag = rotation("rotationY");

        // The cursor returns to the click position, but the drag is still active.
        preview.mouseDragged(
            mouseEvent(
                CENTER_X,
                CENTER_Y,
                GLFW.GLFW_MOUSE_BUTTON_LEFT
            ),
            -10.0,
            0.0
        );

        assertEquals(
            rotationAfterFirstDrag - 8.0f,
            rotation("rotationY"),
            EPSILON
        );

        GuiMachinePreview.PreviewReleaseResult result = preview.mouseReleased(
            mouseEvent(
                CENTER_X,
                CENTER_Y,
                GLFW.GLFW_MOUSE_BUTTON_LEFT
            ),
            1
        );
        assertTrue(result.consumed());
        assertNull(result.hitDirection());
    }

    @Test
    void shouldClampVerticalRotationToBothLimits() {
        beginClick();

        preview.mouseDragged(
            mouseEvent(
                CENTER_X,
                CENTER_Y + 200,
                GLFW.GLFW_MOUSE_BUTTON_LEFT
            ),
            0.0,
            200.0
        );
        assertEquals(
            110.0f,
            rotation("rotationX"),
            EPSILON
        );

        preview.mouseDragged(
            mouseEvent(
                CENTER_X,
                CENTER_Y - 200,
                GLFW.GLFW_MOUSE_BUTTON_LEFT
            ),
            0.0,
            -400.0
        );
        assertEquals(
            -110.0f,
            rotation("rotationX"),
            EPSILON
        );
    }

    @Test
    void shouldNotLimitHorizontalRotation() {
        beginClick();

        preview.mouseDragged(
            mouseEvent(
                CENTER_X + 500,
                CENTER_Y,
                GLFW.GLFW_MOUSE_BUTTON_LEFT
            ),
            500.0,
            0.0
        );

        assertEquals(
            35.0f + 500.0f * 0.8f,
            rotation("rotationY"),
            EPSILON
        );
    }

    @Test
    void shouldConsumeReleaseAfterDragWithoutSelectingFace() {
        beginClick();
        preview.mouseDragged(
            mouseEvent(
                CENTER_X + 12,
                CENTER_Y,
                GLFW.GLFW_MOUSE_BUTTON_LEFT
            ),
            12.0,
            0.0
        );

        GuiMachinePreview.PreviewReleaseResult result = preview.mouseReleased(
            mouseEvent(
                CENTER_X + 12,
                CENTER_Y,
                GLFW.GLFW_MOUSE_BUTTON_LEFT
            ),
            1
        );

        assertTrue(result.consumed());
        assertNull(result.hitDirection());
    }

    @Test
    void shouldIgnoreReleaseWhenNoClickIsActive() {
        GuiMachinePreview.PreviewReleaseResult result = preview.mouseReleased(
            mouseEvent(
                CENTER_X,
                CENTER_Y,
                GLFW.GLFW_MOUSE_BUTTON_LEFT
            ),
            1
        );

        assertFalse(result.consumed());
        assertNull(result.hitDirection());
    }

    @Test
    void shouldIgnoreRightReleaseAndKeepLeftInteractionActive() {
        beginClick();

        GuiMachinePreview.PreviewReleaseResult rightRelease = preview.mouseReleased(
            mouseEvent(
                CENTER_X,
                CENTER_Y,
                GLFW.GLFW_MOUSE_BUTTON_RIGHT
            ),
            1
        );
        assertFalse(rightRelease.consumed());
        assertNull(rightRelease.hitDirection());

        preview.mouseDragged(
            mouseEvent(
                CENTER_X + 10,
                CENTER_Y,
                GLFW.GLFW_MOUSE_BUTTON_LEFT
            ),
            10.0,
            0.0
        );
        GuiMachinePreview.PreviewReleaseResult leftRelease = preview.mouseReleased(
            mouseEvent(
                CENTER_X + 10,
                CENTER_Y,
                GLFW.GLFW_MOUSE_BUTTON_LEFT
            ),
            1
        );
        assertTrue(leftRelease.consumed());
        assertNull(leftRelease.hitDirection());
    }

    @Test
    void shouldIgnoreRepeatedReleaseAfterDrag() {
        beginClick();
        preview.mouseDragged(
            mouseEvent(
                CENTER_X + 10,
                CENTER_Y,
                GLFW.GLFW_MOUSE_BUTTON_LEFT
            ),
            10.0,
            0.0
        );

        assertTrue(preview.mouseReleased(
                mouseEvent(
                    CENTER_X + 10,
                    CENTER_Y,
                    GLFW.GLFW_MOUSE_BUTTON_LEFT
                ),
                1
            )
            .consumed());

        GuiMachinePreview.PreviewReleaseResult secondRelease = preview.mouseReleased(
            mouseEvent(
                CENTER_X + 10,
                CENTER_Y,
                GLFW.GLFW_MOUSE_BUTTON_LEFT
            ),
            1
        );
        assertFalse(secondRelease.consumed());
        assertNull(secondRelease.hitDirection());
    }

    @Test
    void shouldResetDragStateAfterRelease() {
        beginClick();
        preview.mouseDragged(
            mouseEvent(
                CENTER_X + 10,
                CENTER_Y,
                GLFW.GLFW_MOUSE_BUTTON_LEFT
            ),
            10.0,
            0.0
        );
        preview.mouseReleased(
            mouseEvent(
                CENTER_X + 10,
                CENTER_Y,
                GLFW.GLFW_MOUSE_BUTTON_LEFT
            ),
            1
        );

        assertFalse(preview.mouseDragged(
            mouseEvent(
                CENTER_X + 20,
                CENTER_Y,
                GLFW.GLFW_MOUSE_BUTTON_LEFT
            ),
            10.0,
            0.0
        ));

        // A fresh click must start a new interaction.
        assertTrue(preview.mouseClicked(
            mouseEvent(
                CENTER_X,
                CENTER_Y,
                GLFW.GLFW_MOUSE_BUTTON_LEFT
            ),
            X,
            Y,
            SIZE
        ));
        assertTrue(preview.mouseDragged(
            mouseEvent(
                CENTER_X + 10,
                CENTER_Y,
                GLFW.GLFW_MOUSE_BUTTON_LEFT
            ),
            10.0,
            0.0
        ));
        assertTrue(preview.mouseReleased(
                mouseEvent(
                    CENTER_X + 10,
                    CENTER_Y,
                    GLFW.GLFW_MOUSE_BUTTON_LEFT
                ),
                1
            )
            .consumed());
    }

    @Test
    void shouldSelectSouthFaceOnSimpleCenterClick() {
        preparePickingState();
        setField(
            "rotationX",
            0.0f
        );
        setField(
            "rotationY",
            0.0f
        );
        beginClick();

        GuiMachinePreview.PreviewReleaseResult result = preview.mouseReleased(
            mouseEvent(
                CENTER_X,
                CENTER_Y,
                GLFW.GLFW_MOUSE_BUTTON_LEFT
            ),
            1
        );

        assertTrue(result.consumed());
        assertEquals(
            Direction.SOUTH,
            result.hitDirection()
        );
    }

    @Test
    void shouldReturnNullOnEmptyAreaInsidePreview() {
        preparePickingState();
        setField(
            "rotationX",
            0.0f
        );
        setField(
            "rotationY",
            0.0f
        );

        double mouseX = X + 1;
        double mouseY = Y + 1;
        assertTrue(preview.mouseClicked(
            mouseEvent(
                mouseX,
                mouseY,
                GLFW.GLFW_MOUSE_BUTTON_LEFT
            ),
            X,
            Y,
            SIZE
        ));

        GuiMachinePreview.PreviewReleaseResult result = preview.mouseReleased(
            mouseEvent(
                mouseX,
                mouseY,
                GLFW.GLFW_MOUSE_BUTTON_LEFT
            ),
            1
        );

        assertTrue(result.consumed());
        assertNull(result.hitDirection());
    }

    @Test
    void shouldConsumeClickReleasedOutsidePreviewWithoutSelectingFace() {
        preparePickingState();
        beginClick();

        GuiMachinePreview.PreviewReleaseResult result = preview.mouseReleased(
            mouseEvent(
                X + SIZE + 5,
                CENTER_Y,
                GLFW.GLFW_MOUSE_BUTTON_LEFT
            ),
            1
        );

        assertTrue(result.consumed());
        assertNull(result.hitDirection());
    }

    @Test
    void shouldSelectSameFaceAcrossGuiScales() {
        preparePickingState();
        setField(
            "rotationX",
            0.0f
        );
        setField(
            "rotationY",
            0.0f
        );

        for (int guiScale = 1; guiScale <= 4; guiScale++) {
            beginClick();
            GuiMachinePreview.PreviewReleaseResult result = preview.mouseReleased(
                mouseEvent(
                    CENTER_X,
                    CENTER_Y,
                    GLFW.GLFW_MOUSE_BUTTON_LEFT
                ),
                guiScale
            );
            assertTrue(
                result.consumed(),
                "GUI scale " + guiScale
            );
            assertEquals(
                Direction.SOUTH,
                result.hitDirection(),
                "GUI scale " + guiScale
            );
        }
    }

    private void beginClick() {
        assertTrue(preview.mouseClicked(
            mouseEvent(
                CENTER_X,
                CENTER_Y,
                GLFW.GLFW_MOUSE_BUTTON_LEFT
            ),
            X,
            Y,
            SIZE
        ));
    }

    private void preparePickingState() {
        // Normally these fields are set by render(), which needs GuiGraphics.
        // Initialize them directly so the release/picker logic can be unit tested.
        setField(
            "x",
            X
        );
        setField(
            "y",
            Y
        );
        setField(
            "size",
            SIZE
        );
        setField(
            "blockState",
            Blocks.STONE.defaultBlockState()
        );
    }

    private static MouseButtonEvent mouseEvent(
        double mouseX,
        double mouseY,
        int button
    )
    {
        return new MouseButtonEvent(
            mouseX,
            mouseY,
            new MouseButtonInfo(
                button,
                0
            )
        );
    }

    private float rotation(String fieldName) {
        try {
            Field field = GuiMachinePreview.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.getFloat(preview);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(
                "Cannot read GuiMachinePreview." + fieldName,
                exception
            );
        }
    }

    private void setField(
        String fieldName,
        Object value
    )
    {
        try {
            Field field = GuiMachinePreview.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(
                preview,
                value
            );
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(
                "Cannot configure GuiMachinePreview." + fieldName,
                exception
            );
        }
    }
}
