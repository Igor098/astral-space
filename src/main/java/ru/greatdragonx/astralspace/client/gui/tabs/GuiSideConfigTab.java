package ru.greatdragonx.astralspace.client.gui.tabs;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;
import ru.greatdragonx.astralspace.AstralSpace;
import ru.greatdragonx.astralspace.client.gui.animation.GuiTabAnimation;
import ru.greatdragonx.astralspace.client.gui.render.GuiTextRenderer;
import ru.greatdragonx.astralspace.client.gui.render.NineSliceRenderer;
import ru.greatdragonx.astralspace.common.machine.SideConfiguration;

public final class GuiSideConfigTab {
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

    private int renderedWidth = TAB_WIDTH;

    private final GuiTabAnimation animation;
    private final Font font;
    private final Identifier iconTexture;

    public GuiSideConfigTab(
        Font font,
        Identifier iconTexture
    ) {
        this.font = font;
        this.iconTexture = iconTexture;
        this.animation = new GuiTabAnimation(ANIMATION_STEP);
    }

    public void render(
        GuiGraphics graphics,
        int anchorX,
        int anchorY,
        float partialTick,
        Component title,
        SideConfiguration configuration
    ) {
        float progress = this.animation.getProgress(partialTick);

        int width = this.getAnimatedWidth(progress);
        int height = this.getAnimatedHeight(progress);
        this.renderedWidth = width;

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

        this.drawClippedTitle(
            graphics,
            title,
            anchorX,
            anchorY,
            width
        );
    }

    public void tick() {
        this.animation.tick();
    }

    public void toggle() {
        this.animation.toggle();
    }

    public boolean isOpen() {
        return this.animation.isOpen();
    }

    public boolean isToggleHovered(
        double mouseX,
        double mouseY,
        int anchorX,
        int anchorY
    ) {
        int width = this.renderedWidth;
        return mouseX >= anchorX && mouseX < anchorX + width && mouseY >= anchorY && mouseY < anchorY + TAB_HEIGHT;
    }

    public boolean mouseClicked(
        @NonNull MouseButtonEvent event,
        int anchorX,
        int anchorY
    ) {
        if (event.button() == 0 && this.isToggleHovered(
            event.x(),
            event.y(),
            anchorX,
            anchorY
        )) {
            this.toggle();
            return true;
        }

        return false;
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

    private void drawTitle(
        GuiGraphics graphics,
        Component title,
        int anchorX,
        int anchorY
    ) {
        GuiTextRenderer.drawScaledText(
            graphics,
            this.font,
            title,
            anchorX + 21,
            anchorY + 7.5f,
            0.75f,
            0xFFE4E8EC
        );
    }

    private void drawClippedTitle(
        GuiGraphics graphics,
        Component title,
        int anchorX,
        int anchorY,
        int width
    ) {
        int minX = anchorX + TAB_WIDTH;
        int maxX = anchorX + width - BORDER_SIZE;

        if (maxX <= minX) {
            return;
        }

        graphics.enableScissor(
            minX,
            anchorY + BORDER_SIZE,
            maxX,
            anchorY + TAB_HEIGHT - BORDER_SIZE
        );

        this.drawTitle(
            graphics,
            title,
            anchorX,
            anchorY
        );

        graphics.disableScissor();
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
