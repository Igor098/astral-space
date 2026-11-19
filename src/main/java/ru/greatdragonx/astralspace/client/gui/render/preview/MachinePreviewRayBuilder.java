package ru.greatdragonx.astralspace.client.gui.render.preview;

import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector2d;
import org.joml.Vector3f;
import org.jspecify.annotations.NonNull;

public class MachinePreviewRayBuilder {
    public Vector2d toNdc(
        double mouseX,
        double mouseY,
        int x0,
        int y0,
        int x1,
        int y1
    )
    {
        if (x1 <= x0 || y1 <= y0) {
            throw new IllegalArgumentException("Preview dimensions must be positive");
        }

        double ndcX = 2 * ((mouseX - x0) / (x1 - x0)) - 1;
        double ndcY = 1 - (2 * ((mouseY - y0) / (y1 - y0)));

        return new Vector2d(
            ndcX,
            ndcY
        );
    }

    public Vector3f toBlockLocal(
        Vector3f point,
        Matrix4f modelMatrix
    )
    {
        Matrix4f modelCopy = new Matrix4f(modelMatrix).invert();
        Vector3f pointCopy = new Vector3f(point);

        return modelCopy.transformPosition(pointCopy);
    }

    public Vector3f toRenderSpace(
        Vector3f ndcPoint,
        Matrix4f projectionMatrix
    )
    {
        Matrix4f projectionCopy = new Matrix4f(projectionMatrix).invert();
        Vector3f pointCopy = new Vector3f(ndcPoint);

        return projectionCopy.transformProject(pointCopy);
    }

    public Vec3 toBlockRayPoint(
        @NonNull Vector2d ndc,
        float ndcZ,
        Matrix4f projectionMatrix,
        Matrix4f modelMatrix
    )
    {
        Vector3f ndcPoint = new Vector3f(
            (float) ndc.x(),
            (float) ndc.y(),
            ndcZ
        );

        Vector3f point = this.toRenderSpace(
            ndcPoint,
            projectionMatrix
        );
        Vector3f blockLocal = this.toBlockLocal(
            point,
            modelMatrix
        );

        return new Vec3(blockLocal);
    }

    public MachinePreviewRay buildRay(
        Vector2d ndc,
        float nearNdcZ,
        float farNdcZ,
        Matrix4f projectionMatrix,
        Matrix4f modelMatrix
    )
    {
        Vec3 start = this.toBlockRayPoint(
            ndc,
            nearNdcZ,
            projectionMatrix,
            modelMatrix
        );
        Vec3 end = this.toBlockRayPoint(
            ndc,
            farNdcZ,
            projectionMatrix,
            modelMatrix
        );

        return new MachinePreviewRay(
            start,
            end
        );
    }
}
