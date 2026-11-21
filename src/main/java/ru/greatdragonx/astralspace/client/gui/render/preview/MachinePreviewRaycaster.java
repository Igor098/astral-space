package ru.greatdragonx.astralspace.client.gui.render.preview;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

public final class MachinePreviewRaycaster {
    private MachinePreviewRaycaster() {}

    public static @Nullable Direction detectHitDirection(
        @NonNull Vec3 rayStart,
        @NonNull Vec3 rayEnd
    )
    {
        Objects.requireNonNull(
            rayStart,
            "rayStart cannot be null"
        );
        Objects.requireNonNull(
            rayEnd,
            "rayEnd cannot be null"
        );

        VoxelShape shape = Shapes.block();
        BlockHitResult hit = shape.clip(
            rayStart,
            rayEnd,
            BlockPos.ZERO
        );

        if (hit == null) {
            return null;
        }

        return hit.getDirection();
    }
}
