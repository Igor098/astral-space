package ru.greatdragonx.astralspace.client.gui.tabs;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;
import ru.greatdragonx.astralspace.AstralSpace;
import ru.greatdragonx.astralspace.client.gui.animation.GuiTabAnimation;
import ru.greatdragonx.astralspace.client.gui.render.GuiTextRenderer;
import ru.greatdragonx.astralspace.client.gui.render.NineSliceRenderer;

public abstract class GuiAnimatedTab implements GuiCollapsibleTab {
    protected static final int TAB_WIDTH = 20;
    protected static final int TAB_HEIGHT = 20;

    private static final int TEXTURE_SIZE = 20;
    private static final int BORDER_SIZE = 5;

    private static final int DIVIDER_HORIZONTAL_INSET = 5;
    private static final int DIVIDER_THICKNESS = 1;

    private static final int TITLE_COLOR = 0xFFE4E8EC;
    private static final int DIVIDER_COLOR = 0xFF626C75;

    private static final float TITLE_SCALE = 0.75f;
    private static final int TITLE_X_GAP = 4;
    private static final int TITLE_Y_OFFSET = 3;

    private static final int ICON_SIZE = 16;
    private static final int ICON_OPEN_LEFT_INSET = 5;

    private final int panelWidth;
    private final int panelHeight;

    private int renderedWidth = TAB_WIDTH;
    private int renderedHeight = TAB_HEIGHT;

    private static final Identifier BACKGROUND_TEXTURE = Identifier.fromNamespaceAndPath(
        AstralSpace.MOD_ID,
        "textures/gui/common/tabs/tab_background.png"
    );

    protected final GuiTabAnimation animation;
    private final boolean opensLeft;

    protected GuiAnimatedTab(
        boolean opensLeft,
        float animationStep,
        int panelWidth,
        int panelHeight
    )
    {
        this.opensLeft = opensLeft;
        this.animation = new GuiTabAnimation(animationStep);
        this.panelWidth = panelWidth;
        this.panelHeight = panelHeight;
    }

    public void tick() {
        this.animation.tick();
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
    )
    {
        int x = this.getPanelX(anchorX);
        return mouseX >= x &&
            mouseX < x + this.renderedWidth &&
            mouseY >= anchorY &&
            mouseY < anchorY + TAB_HEIGHT;
    }

    protected int getRenderedWidth() {
        return this.renderedWidth;
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
        float progress = this.updateRenderState(partialTick);
        int x = getPanelX(anchorX);

        NineSliceRenderer.draw(
            graphics,
            BACKGROUND_TEXTURE,
            x,
            anchorY,
            this.renderedWidth,
            this.renderedHeight,
            TEXTURE_SIZE,
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

        int iconInset = this.opensLeft ? Math.round(baseInset +
            (ICON_OPEN_LEFT_INSET - baseInset) * progress) : baseInset;

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
            ICON_SIZE,
            ICON_SIZE
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
        float normalizedProgress = Math.clamp(
            progress,
            0.0f,
            1.0f
        );
        return Math.round(TAB_WIDTH + delta * normalizedProgress);
    }

    private int getAnimatedHeight(float progress) {
        int delta = this.panelHeight - TAB_HEIGHT;
        float normalizedProgress = Math.clamp(
            progress,
            0.0f,
            1.0f
        );
        return Math.round(TAB_HEIGHT + delta * normalizedProgress);
    }

    private float updateRenderState(float partialTick) {
        float progress = this.animation.getProgress(partialTick);

        this.renderedWidth = this.getAnimatedWidth(progress);
        this.renderedHeight = this.getAnimatedHeight(progress);

        return progress;
    }
}
