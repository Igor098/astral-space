package ru.greatdragonx.astralspace.client.gui.tabs;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;
import ru.greatdragonx.astralspace.AstralSpace;
import ru.greatdragonx.astralspace.client.gui.animation.GuiTabAnimation;
import ru.greatdragonx.astralspace.client.gui.render.NineSliceRenderer;

public final class GuiRedstoneControlTab implements GuiCollapsibleTab {
    private static final float ANIMATION_STEP = 0.15f;

    private static final int TAB_WIDTH = 20;
    private static final int TAB_HEIGHT = 20;

    private static final int PANEL_WIDTH = 100;
    private static final int PANEL_HEIGHT = 96;

    private static final int TEXTURE_SIZE = 20;
    private static final int BORDER_SIZE = 5;

    private static final int ICON_WIDTH = 16;
    private static final int ICON_HEIGHT = 16;

    private static final Identifier BACKGROUND_TEXTURE = Identifier.fromNamespaceAndPath(
        AstralSpace.MOD_ID,
        "textures/gui/common/tabs/tab_background.png"
    );

    private final GuiTabAnimation animation;
    private final Font font;
    private final Identifier iconTexture;

    private int renderedWidth = TAB_WIDTH;
    private int renderedHeight = TAB_HEIGHT;

    public GuiRedstoneControlTab(
        Font font,
        Identifier iconTexture
    ) {
        this.font = font;
        this.iconTexture = iconTexture;
        this.animation = new GuiTabAnimation(ANIMATION_STEP);
    }

    public void render(
        @NonNull GuiGraphics graphics,
        int anchorX,
        int anchorY,
        float partialTick
    ) {
        float progress = this.animation.getProgress(partialTick);

        int width = this.getAnimatedWidth(progress);
        int height = this.getAnimatedHeight(progress);
        this.renderedWidth = width;
        this.renderedHeight = height;

        NineSliceRenderer.draw(
            graphics,
            BACKGROUND_TEXTURE,
            anchorX,
            anchorY,
            width,
            height,
            TEXTURE_SIZE,
            BORDER_SIZE
        );

        this.drawIcon(
            graphics,
            anchorX,
            anchorY
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
    public int getRenderedHeight() {
        return this.renderedHeight;
    }

    @Override
    public boolean isHeaderHovered(
        double mouseX,
        double mouseY,
        int anchorX,
        int anchorY
    ) {
        return mouseX >= anchorX && mouseX < anchorX + this.renderedWidth && mouseY >= anchorY && mouseY < anchorY + TAB_HEIGHT;
    }

    public void tick() {
        this.animation.tick();
    }

    private void drawIcon(
        @NonNull GuiGraphics graphics,
        int anchorX,
        int anchorY
    ) {
        int iconXMargin = (TAB_WIDTH - ICON_WIDTH) / 2;
        int iconYMargin = (TAB_HEIGHT - ICON_HEIGHT) / 2;

        int iconX = anchorX + iconXMargin;
        int iconY = anchorY + iconYMargin;

        graphics.blit(
            RenderPipelines.GUI_TEXTURED,
            this.iconTexture,
            iconX,
            iconY,
            0,
            0,
            ICON_WIDTH,
            ICON_HEIGHT,
            ICON_WIDTH,
            ICON_HEIGHT
        );
    }

    private int getAnimatedWidth(float progress) {
        int delta = PANEL_WIDTH - TAB_WIDTH;
        float normalizedProgress = Math.clamp(
            progress,
            0.0f,
            1.0f
        );
        return Math.round(TAB_WIDTH + delta * normalizedProgress);
    }

    private int getAnimatedHeight(float progress) {
        int delta = PANEL_HEIGHT - TAB_HEIGHT;
        float normalizedProgress = Math.clamp(
            progress,
            0.0f,
            1.0f
        );
        return Math.round(TAB_HEIGHT + delta * normalizedProgress);
    }
}
