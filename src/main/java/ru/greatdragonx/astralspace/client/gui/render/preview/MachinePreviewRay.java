package ru.greatdragonx.astralspace.client.gui.render.preview;

import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NonNull;

import java.util.Objects;

public record MachinePreviewRay(
    @NonNull Vec3 start, @NonNull Vec3 end
)
{
    public MachinePreviewRay {
        Objects.requireNonNull(
            start,
            "start cannot be null"
        );
        Objects.requireNonNull(
            end,
            "end cannot be null"
        );
    }
}
