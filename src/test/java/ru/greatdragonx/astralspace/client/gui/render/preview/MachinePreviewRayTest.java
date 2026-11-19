package ru.greatdragonx.astralspace.client.gui.render.preview;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MachinePreviewRayTest {
    @Test
    void shouldCreateRayWithValidPoints() {
        Vec3 start = new Vec3(
            0.5,
            0.5,
            -1
        );
        Vec3 end = new Vec3(
            0.5,
            0.5,
            2
        );

        MachinePreviewRay ray = new MachinePreviewRay(
            start,
            end
        );

        assertEquals(
            start,
            ray.start()
        );
        assertEquals(
            end,
            ray.end()
        );
    }

    @Test
    void shouldRejectNullStart() {
        Vec3 end = new Vec3(
            0.5,
            0.5,
            2
        );

        assertThrows(
            NullPointerException.class,
            () -> new MachinePreviewRay(
                null,
                end
            )
        );
    }

    @Test
    void shouldRejectNullEnd() {
        Vec3 start = new Vec3(
            0.5,
            0.5,
            -1
        );

        assertThrows(
            NullPointerException.class,
            () -> new MachinePreviewRay(
                start,
                null
            )
        );
    }
}
