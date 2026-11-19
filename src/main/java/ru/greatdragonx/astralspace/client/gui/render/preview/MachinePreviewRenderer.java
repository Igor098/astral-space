package ru.greatdragonx.astralspace.client.gui.render.preview;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import ru.greatdragonx.astralspace.common.machine.MachineSide;

public class MachinePreviewRenderer extends PictureInPictureRenderer<MachinePreviewRenderState> {
    public MachinePreviewRenderer(MultiBufferSource.BufferSource bufferSource) {
        super(bufferSource);
    }

    @Override
    public @NonNull Class<MachinePreviewRenderState> getRenderStateClass() {
        return MachinePreviewRenderState.class;
    }

    @Override
    protected void renderToTexture(
        MachinePreviewRenderState state,
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

        int width = state.x1() - state.x0();
        int height = state.y1() - state.y0();

        poseStack.pushPose();

        poseStack.translate(
            0.0,
            -32.0,
            0.0
        );

        poseStack.scale(
            28.0f,
            -28.0f,
            28.0f
        );

        poseStack.mulPose(Axis.XP.rotationDegrees(state.rotationX()));
        poseStack.mulPose(Axis.YP.rotationDegrees(state.rotationY()));

        poseStack.translate(
            -0.5,
            -0.5,
            -0.5
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

        poseStack.popPose();
    }

    @Override
    protected @NonNull String getTextureLabel() {
        return "astralspace:machine_preview";
    }

    private float getYRotation(@Nullable MachineSide side) {
        if (side == null) {
            return 0.0f;
        }

        return switch (side) {
            case FRONT -> 0.0f;
            case RIGHT -> 90.0f;
            case BACK -> 180.0f;
            case LEFT -> 270.0f;
            default -> 0.0f;
        };
    }
}
