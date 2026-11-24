package ru.greatdragonx.astralspace.client.gui.render.preview;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.joml.Vector3f;
import org.jspecify.annotations.NonNull;
import ru.greatdragonx.astralspace.common.machine.MachineSide;
import ru.greatdragonx.astralspace.common.machine.MachineSideDirections;

import java.util.List;
import java.util.Objects;

public class MachinePreviewRenderer extends PictureInPictureRenderer<MachinePreviewRenderState> {
    private static final float HIGHLIGHT_OFFSET = 0.001f;
    private static final int HIGHLIGHT_COLOR = 0x6440B4FF;

    public MachinePreviewRenderer(MultiBufferSource.BufferSource bufferSource) {
        super(bufferSource);
    }

    @Override
    public @NonNull Class<MachinePreviewRenderState> getRenderStateClass() {
        return MachinePreviewRenderState.class;
    }

    @Override
    protected void renderToTexture(
        @NonNull MachinePreviewRenderState state,
        @NonNull PoseStack poseStack
    )
    {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) {
            return;
        }

        BlockRenderDispatcher blockRenderer = minecraft.getBlockRenderer();
        BlockState blockState = state.blockState();

        poseStack.pushPose();

        float factor = MachinePreviewTransform.viewportFactor(
            state.x1() - state.x0(),
            state.y1() - state.y0()
        );
        float scaleFactor = Math.min(1.0f, factor * 1.2f);

        poseStack.translate(
            MachinePreviewTransform.MODEL_TRANSLATION_X * factor,
            MachinePreviewTransform.MODEL_TRANSLATION_Y * factor,
            MachinePreviewTransform.MODEL_TRANSLATION_Z * factor
        );

        poseStack.scale(
            MachinePreviewTransform.MODEL_SCALE_X * scaleFactor,
            MachinePreviewTransform.MODEL_SCALE_Y * scaleFactor,
            MachinePreviewTransform.MODEL_SCALE_Z * scaleFactor
        );

        poseStack.mulPose(Axis.XP.rotationDegrees(state.rotationX()));
        poseStack.mulPose(Axis.YP.rotationDegrees(state.rotationY()));

        poseStack.translate(
            MachinePreviewTransform.BLOCK_CENTER_OFFSET,
            MachinePreviewTransform.BLOCK_CENTER_OFFSET,
            MachinePreviewTransform.BLOCK_CENTER_OFFSET
        );

        blockRenderer.renderSingleBlock(
            blockState,
            poseStack,
            this.bufferSource,
            LightTexture.FULL_BRIGHT,
            OverlayTexture.NO_OVERLAY,
            level,
            BlockPos.ZERO
        );

        MachineSide selectedSide = state.selectedSide();
        if (selectedSide != null) {
            Direction facing = state.blockState().getValue(BlockStateProperties.HORIZONTAL_FACING);

            Direction direction = MachineSideDirections.toWorld(selectedSide, facing);
            bufferSource.endBatch();
            this.renderFaceHighlight(poseStack, direction);
        }
        poseStack.popPose();
    }

    @Override
    protected @NonNull String getTextureLabel() {
        return "astralspace:machine_preview";
    }

    private void renderFaceHighlight(
        @NonNull PoseStack poseStack,
        @NonNull Direction direction
    )
    {
        Objects.requireNonNull(poseStack, "poseStack cannot be null");
        Objects.requireNonNull(direction, "direction cannot be null");

        List<Vector3f> vertices = MachinePreviewFaceGeometry.getOffsetFaceVertices(
            direction,
            HIGHLIGHT_OFFSET
        );
        VertexConsumer consumer = bufferSource.getBuffer(RenderTypes.debugQuads());
        PoseStack.Pose pose = poseStack.last();

        for (Vector3f vertex : vertices) {
            consumer.addVertex(pose, vertex).setColor(HIGHLIGHT_COLOR);
        }
    }
}
