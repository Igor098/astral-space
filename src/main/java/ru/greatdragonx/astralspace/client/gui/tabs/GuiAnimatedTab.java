package ru.greatdragonx.astralspace.client.gui.tabs;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import org.jspecify.annotations.NonNull;
import ru.greatdragonx.astralspace.AstralSpace;
import ru.greatdragonx.astralspace.client.gui.animation.GuiEasing;
import ru.greatdragonx.astralspace.client.gui.animation.GuiTabAnimation;
import ru.greatdragonx.astralspace.client.gui.render.GuiTextRenderer;
import ru.greatdragonx.astralspace.client.gui.render.NineSliceRenderer;

public abstract class GuiAnimatedTab implements GuiCollapsibleTab {
    protected static final int TAB_WIDTH = 28;
    protected static final int TAB_HEIGHT = 28;

    private static final int TEXTURE_SIZE = 160;
    private static final int SOURCE_BORDER = 40;
    protected static final int BORDER_SIZE = 7;

    private static final int DIVIDER_HORIZONTAL_INSET = 5;
    private static final int DIVIDER_THICKNESS = 1;

    private static final int TITLE_COLOR = 0xFFE4E8EC;
    private static final int DIVIDER_COLOR = 0xFF454C56;

    private static final float TITLE_SCALE = 0.75f;
    private static final int TITLE_X_GAP = 0;
    private static final int TITLE_Y_OFFSET = 3;

    private static final int ICON_SIZE = 16;
    private static final int ICON_TEXTURE_SIZE = 64;
    private static final int ICON_OPEN_LEFT_INSET = 5;

    private static final float HEIGHT_ANIMATION_STEP = 0.15f;

    private final int panelWidth;
    private final int panelHeight;

    private int renderedWidth = TAB_WIDTH;
    private int renderedHeight = TAB_HEIGHT;

    private int targetPanelHeight;

    private float heightAnimationStart;
    private float heightAnimationProgress = 1.0f;
    private float previousHeightAnimationProgress = 1.0f;

    private static final Identifier BACKGROUND_TEXTURE =
        Identifier.fromNamespaceAndPath(
            AstralSpace.MOD_ID,
            "textures/gui/common/tabs/tab_background.png"
        );

    private final GuiTabAnimation animation;
    private final boolean opensLeft;

    protected GuiAnimatedTab(
        boolean opensLeft,
        float animationStep,
        int panelWidth,
        int panelHeight
    )
    {
        if (panelWidth < TAB_WIDTH || panelHeight < TAB_HEIGHT) {
            throw new IllegalArgumentException(
                "Panel dimensions must not be smaller than tab dimensions");
        }
        this.panelWidth = panelWidth;
        this.panelHeight = panelHeight;

        this.targetPanelHeight = panelHeight;
        this.heightAnimationStart = panelHeight;

        this.opensLeft = opensLeft;
        this.animation = new GuiTabAnimation(animationStep);
    }

    public void tick() {
        this.animation.tick();

        this.previousHeightAnimationProgress = this.heightAnimationProgress;

        this.heightAnimationProgress = Math.min(
            1.0f,
            this.heightAnimationProgress + HEIGHT_ANIMATION_STEP
        );
    }

    @Override
    public void open() {
        this.animation.open();
    }

    @Override
    public void close() {
        this.animation.close();
    }

    @Override
    public boolean isOpen() {
        return this.animation.isOpen();
    }

    @Override
    public final int getRenderedHeight() {
        return this.renderedHeight;
    }

    @Override
    public boolean isHeaderHovered(
        double mouseX,
        double mouseY,
        int anchorX,
        int anchorY
    )
    {
        int x = this.getPanelX(anchorX);
        return mouseX >= x && mouseX < x + this.renderedWidth && mouseY >= anchorY &&
            mouseY < anchorY + TAB_HEIGHT;
    }

    protected final int getRenderedWidth() {
        return this.renderedWidth;
    }

    protected final boolean isFullyClosed() {
        return this.animation.isFullyClosed();
    }

    protected final int getPanelX(int anchorX) {
        return this.opensLeft ? anchorX - this.renderedWidth : anchorX;
    }

    protected final float renderPanelBackground(
        GuiGraphics graphics,
        int anchorX,
        int anchorY,
        float partialTick
    )
    {
        return this.renderPanelBackground(
            graphics,
            anchorX,
            anchorY,
            partialTick,
            this.panelHeight
        );
    }

    protected final float renderPanelBackground(
        GuiGraphics graphics,
        int anchorX,
        int anchorY,
        float partialTick,
        int targetHeight
    )
    {
        float progress = this.updateRenderState(partialTick, targetHeight);
        int x = getPanelX(anchorX);

        NineSliceRenderer.drawScaled(
            graphics,
            BACKGROUND_TEXTURE,
            x,
            anchorY,
            this.renderedWidth,
            this.renderedHeight,
            TEXTURE_SIZE,
            SOURCE_BORDER,
            BORDER_SIZE
        );

        return progress;
    }

    protected final void drawTabIcon(
        @NonNull GuiGraphics graphics,
        @NonNull Identifier iconTexture,
        int panelX,
        int panelY,
        float progress
    )
    {
        int baseInset = (TAB_WIDTH - ICON_SIZE) / 2;

        int iconInset = this.opensLeft ? Math.round(
            baseInset + (ICON_OPEN_LEFT_INSET - baseInset) * progress) : baseInset;

        int iconX = panelX + iconInset;
        int iconY = panelY + (TAB_HEIGHT - ICON_SIZE) / 2;

        graphics.blit(
            RenderPipelines.GUI_TEXTURED,
            iconTexture,
            iconX,
            iconY,
            0,
            0,
            ICON_SIZE,
            ICON_SIZE,
            ICON_TEXTURE_SIZE,
            ICON_TEXTURE_SIZE,
            ICON_TEXTURE_SIZE,
            ICON_TEXTURE_SIZE
        );
    }

    protected final void drawTitle(
        @NonNull GuiGraphics graphics,
        @NonNull Font font,
        @NonNull Component title,
        int panelX,
        int panelY
    )
    {
        int x = panelX + TAB_WIDTH + TITLE_X_GAP;
        int y = panelY + (TAB_HEIGHT - font.lineHeight) / 2;

        GuiTextRenderer.drawScaledText(
            graphics,
            font,
            title,
            x,
            y + TITLE_Y_OFFSET,
            TITLE_SCALE,
            TITLE_COLOR
        );
    }

    protected final void drawDivider(
        @NonNull GuiGraphics graphics,
        int x,
        int y,
        int width
    )
    {
        graphics.fill(
            x + DIVIDER_HORIZONTAL_INSET,
            y + TAB_HEIGHT - DIVIDER_THICKNESS,
            x + width - DIVIDER_HORIZONTAL_INSET,
            y + TAB_HEIGHT,
            DIVIDER_COLOR
        );
    }

    private int getAnimatedWidth(float progress) {
        int delta = this.panelWidth - TAB_WIDTH;
        float normalizedProgress = Math.clamp(progress, 0.0f, 1.0f);
        return Math.round(TAB_WIDTH + delta * normalizedProgress);
    }

    private int getAnimatedHeight(
        float progress,
        int targetHeight
    )
    {
        int delta = targetHeight - TAB_HEIGHT;
        float normalizedProgress = Math.clamp(progress, 0.0f, 1.0f);
        return Math.round(TAB_HEIGHT + delta * normalizedProgress);
    }

    private float getInterpolatedPanelHeight(float partialTick) {
        float progress = Mth.lerp(
            partialTick,
            this.previousHeightAnimationProgress,
            this.heightAnimationProgress
        );

        float easedProgress = GuiEasing.smoothstep(progress);

        return Mth.lerp(easedProgress, this.heightAnimationStart, this.targetPanelHeight);
    }

    private float updateRenderState(
        float partialTick,
        int targetHeight
    )
    {
        if (targetHeight != this.targetPanelHeight) {
            float currentHeight = this.getInterpolatedPanelHeight(partialTick);
            this.targetPanelHeight = targetHeight;

            if (!this.animation.isOpen()) {
                this.heightAnimationStart = targetHeight;
                this.heightAnimationProgress = 1.0f;
                this.previousHeightAnimationProgress = 1.0f;
            } else {
                this.heightAnimationStart = currentHeight;
                this.heightAnimationProgress = 0.0f;
                this.previousHeightAnimationProgress = 0.0f;
            }
        }

        float progress = this.animation.getProgress(partialTick);
        float animatedHeight = this.getInterpolatedPanelHeight(partialTick);

        this.renderedWidth = this.getAnimatedWidth(progress);
        this.renderedHeight = Math.round(TAB_HEIGHT + (animatedHeight - TAB_HEIGHT) * progress);

        return progress;
    }
}
