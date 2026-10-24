package ru.greatdragonx.astralspace.client.gui.tabs;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import org.jspecify.annotations.NonNull;
import ru.greatdragonx.astralspace.AstralSpace;
import ru.greatdragonx.astralspace.client.gui.animation.GuiTabAnimation;
import ru.greatdragonx.astralspace.client.gui.render.GuiTextRenderer;
import ru.greatdragonx.astralspace.client.gui.render.NineSliceRenderer;

import java.util.List;

public final class GuiMachineInfoTab implements GuiCollapsibleTab {
    private static final float ANIMATION_STEP = 0.15f;

    private static final int TAB_WIDTH = 20;
    private static final int TAB_HEIGHT = 20;

    private static final int PANEL_WIDTH = 128;
    private static final int PANEL_HEIGHT = 116;

    private static final int TEXTURE_SIZE = 20;
    private static final int BORDER_SIZE = 5;

    private static final int ICON_WIDTH = 16;
    private static final int ICON_HEIGHT = 16;

    private static final int DESCRIPTION_TEXT_WIDTH = 104;

    private static final int DESCRIPTION_START_X = 8;
    private static final int DESCRIPTION_START_Y = 29;
    private static final int DESCRIPTION_VIEWPORT_HEIGHT = 76;

    private static final int LINE_SPACING = 2;
    private static final int ENTRY_SPACING = 4;

    private static final int SCROLL_STEP = 10;

    private static final Identifier BACKGROUND_TEXTURE = Identifier.fromNamespaceAndPath(
        AstralSpace.MOD_ID,
        "textures/gui/common/tabs/tab_background.png"
    );

    private static final Identifier ICON_TEXTURE = Identifier.fromNamespaceAndPath(
        AstralSpace.MOD_ID,
        "textures/gui/common/tabs/machine_info.png"
    );

    private final GuiTabAnimation animation;
    private final Font font;

    private int renderedWidth = TAB_WIDTH;
    private int renderedHeight = TAB_HEIGHT;
    private int scrollOffset = 0;

    public GuiMachineInfoTab(Font font) {
        this.font = font;
        this.animation = new GuiTabAnimation(ANIMATION_STEP);
    }

    public void render(
        @NonNull GuiGraphics graphics,
        @NonNull List<GuiInfoEntry> entries,
        int anchorX,
        int anchorY,
        float partialTick
    ) {
        float progress = this.animation.getProgress(partialTick);

        int width = this.getAnimatedWidth(progress);
        int height = this.getAnimatedHeight(progress);

        this.renderedWidth = width;
        this.renderedHeight = height;

        int x = this.getX(
            anchorX,
            width
        );

        NineSliceRenderer.draw(
            graphics,
            BACKGROUND_TEXTURE,
            x,
            anchorY,
            width,
            height,
            TEXTURE_SIZE,
            BORDER_SIZE
        );

        this.drawContent(
            graphics,
            entries,
            x,
            anchorY,
            width,
            height
        );

        this.drawIcon(
            graphics,
            progress,
            x,
            anchorY
        );
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
    ) {
        int x = this.getX(
            anchorX,
            this.renderedWidth
        );
        return mouseX >= x && mouseX < x + this.renderedWidth && mouseY >= anchorY && mouseY < anchorY + TAB_HEIGHT;
    }

    public boolean mouseScrolled(
        double mouseX,
        double mouseY,
        double scrollY,
        int anchorX,
        int anchorY,
        List<GuiInfoEntry> entries
    ) {
        if (!this.isOpen() || this.renderedWidth < PANEL_WIDTH || this.renderedHeight < PANEL_HEIGHT) {
            return false;
        }

        int x = anchorX - this.renderedWidth;

        int viewportX = x + DESCRIPTION_START_X;
        int viewportY = anchorY + DESCRIPTION_START_Y;

        boolean canScroll =
            mouseX >= viewportX && mouseX < viewportX + DESCRIPTION_TEXT_WIDTH && mouseY >= viewportY && mouseY < viewportY + DESCRIPTION_VIEWPORT_HEIGHT;

        if (!canScroll) {
            return false;
        }

        int maxScroll = this.getMaxScroll(entries);

        if (maxScroll == 0) {
            return false;
        }

        int delta = (int) Math.round(scrollY * SCROLL_STEP);

        this.scrollOffset = Math.clamp(
            this.scrollOffset - delta,
            0,
            maxScroll
        );

        return true;
    }

    private void drawIcon(
        @NonNull GuiGraphics graphics,
        float progress,
        int x,
        int y
    ) {
        int iconXMargin = (TAB_WIDTH - ICON_WIDTH) / 2;
        int iconYMargin = (TAB_HEIGHT - ICON_HEIGHT) / 2;
        int animatedIconXMargin = Math.round(iconXMargin + (5 - iconXMargin) * progress);
        int iconX = x + animatedIconXMargin;
        int iconY = y + iconYMargin;

        graphics.blit(
            RenderPipelines.GUI_TEXTURED,
            ICON_TEXTURE,
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
        @NonNull GuiGraphics graphics,
        int anchorX,
        int anchorY
    ) {
        int x = anchorX + TAB_WIDTH + 2;
        int y = anchorY + (TAB_HEIGHT - this.font.lineHeight) / 2;
        Component title = Component.translatable("gui.astralspace.machine_info.title");
        GuiTextRenderer.drawScaledText(
            graphics,
            this.font,
            title,
            x,
            y + 3,
            0.75f,
            0xFFE4E8EC
        );
    }

    private void drawDivider(
        @NonNull GuiGraphics graphics,
        int x,
        int y,
        int width
    ) {
        graphics.fill(
            x + 5,
            y + 19,
            x + width - 5,
            y + 20,
            0xFF626C75
        );
    }

    private void drawDescription(
        @NonNull GuiGraphics graphics,
        @NonNull List<GuiInfoEntry> entries,
        int x,
        int y
    ) {
        int textX = x + DESCRIPTION_START_X;
        int cursorY = y + DESCRIPTION_START_Y - this.scrollOffset;

        for (GuiInfoEntry entry : entries) {
            int color = entry.heading() ? 0xFF9FBAD4 : 0xFFD3DAE1;
            float scale = entry.heading() ? 0.75f : 0.6f;

            int wrapWidth = (int) (DESCRIPTION_TEXT_WIDTH / scale);
            int scaledLineHeight = (int) Math.ceil(this.font.lineHeight * scale);

            List<FormattedCharSequence> lines = this.font.split(
                entry.text(),
                wrapWidth
            );

            for (FormattedCharSequence line : lines) {
                GuiTextRenderer.drawScaledText(
                    graphics,
                    this.font,
                    line,
                    textX,
                    cursorY,
                    scale,
                    color
                );

                cursorY += scaledLineHeight + LINE_SPACING;
            }

            cursorY += ENTRY_SPACING;
        }
    }

    private void drawContent(
        @NonNull GuiGraphics graphics,
        @NonNull List<GuiInfoEntry> entries,
        int anchorX,
        int anchorY,
        int width,
        int height
    ) {
        graphics.enableScissor(
            anchorX + BORDER_SIZE,
            anchorY + BORDER_SIZE,
            anchorX + width - BORDER_SIZE,
            anchorY + height - BORDER_SIZE
        );
        this.drawTitle(
            graphics,
            anchorX,
            anchorY
        );
        this.drawDivider(
            graphics,
            anchorX,
            anchorY,
            width
        );
        graphics.enableScissor(
            anchorX + DESCRIPTION_START_X,
            anchorY + DESCRIPTION_START_Y,
            anchorX + DESCRIPTION_START_X + DESCRIPTION_TEXT_WIDTH,
            anchorY + DESCRIPTION_START_Y + DESCRIPTION_VIEWPORT_HEIGHT
        );
        this.drawDescription(
            graphics,
            entries,
            anchorX,
            anchorY
        );
        graphics.disableScissor();
        graphics.disableScissor();
    }

    private int getX(
        int screenLeft,
        int animatedWidth
    ) {
        return screenLeft - animatedWidth;
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

    private int getContentHeight(@NonNull List<GuiInfoEntry> entries) {
        int contentHeight = 0;

        for (GuiInfoEntry entry : entries) {
            float scale = entry.heading() ? 0.75f : 0.6f;
            int wrapWidth = (int) (DESCRIPTION_TEXT_WIDTH / scale);
            int scaledLineHeight = (int) Math.ceil(this.font.lineHeight * scale);

            List<FormattedCharSequence> lines = this.font.split(
                entry.text(),
                wrapWidth
            );

            contentHeight += lines.size() * (scaledLineHeight + LINE_SPACING) + ENTRY_SPACING;
        }

        return contentHeight;
    }

    private int getMaxScroll(@NonNull List<GuiInfoEntry> entries) {
        int contentHeight = this.getContentHeight(entries);
        int scrollableHeight = contentHeight - DESCRIPTION_VIEWPORT_HEIGHT;

        return Math.max(
            0,
            scrollableHeight
        );
    }
}
