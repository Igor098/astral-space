package ru.greatdragonx.astralspace.client.gui.tabs;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import org.jspecify.annotations.NonNull;
import org.lwjgl.glfw.GLFW;
import ru.greatdragonx.astralspace.AstralSpace;
import ru.greatdragonx.astralspace.client.gui.render.GuiTextRenderer;

import java.util.List;

public final class GuiMachineInfoTab extends GuiAnimatedTab {
    private static final float ANIMATION_STEP = 0.15f;

    private static final int TAB_WIDTH = 20;
    private static final int TAB_HEIGHT = 20;

    private static final int PANEL_WIDTH = 128;
    private static final int PANEL_HEIGHT = 116;

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
    private static final int SCROLLBAR_OFFSET_X = 115;
    private static final int SCROLLBAR_WIDTH = 4;
    private static final int MIN_THUMB_HEIGHT = 12;

    private static final int SCROLLBAR_TRACK_COLOR = 0xFF53616F;
    private static final int SCROLLBAR_THUMB_COLOR = 0xFF98A8B8;

    private static final Identifier ICON_TEXTURE = Identifier.fromNamespaceAndPath(
        AstralSpace.MOD_ID,
        "textures/gui/common/tabs/machine_info.png"
    );

    private final Font font;

    private int scrollOffset = 0;
    private double dragStartMouseY;
    private int dragStartScrollOffset;
    private boolean draggingScrollbar = false;

    public GuiMachineInfoTab(Font font) {
        super(
            true,
            ANIMATION_STEP,
            PANEL_WIDTH,
            PANEL_HEIGHT
        );
        this.font = font;
    }

    public void render(
        @NonNull GuiGraphics graphics,
        @NonNull List<GuiInfoEntry> entries,
        int anchorX,
        int anchorY,
        float partialTick
    )
    {
        float progress = this.renderPanelBackground(
            graphics,
            anchorX,
            anchorY,
            partialTick
        );

        int width = this.getRenderedWidth();
        int height = this.getRenderedHeight();

        int x = this.getPanelX(anchorX);

        this.drawContent(
            graphics,
            entries,
            x,
            anchorY,
            width,
            height
        );

        this.drawTabIcon(
            graphics,
            ICON_TEXTURE,
            x,
            anchorY,
            progress
        );
    }

    public boolean mouseClicked(
        double mouseX,
        double mouseY,
        int button,
        int anchorX,
        int anchorY,
        List<GuiInfoEntry> entries
    )
    {
        int renderedWidth = this.getRenderedWidth();
        int renderedHeight = this.getRenderedHeight();

        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT ||
            !this.isOpen() ||
            renderedWidth < PANEL_WIDTH ||
            renderedHeight < PANEL_HEIGHT)
        {
            return false;
        }

        int maxScroll = getMaxScroll(entries);
        if (maxScroll <= 0) {
            return false;
        }

        int contentHeight = this.getContentHeight(entries);
        int thumbHeight = this.getThumbHeight(contentHeight);
        int thumbTravel = DESCRIPTION_VIEWPORT_HEIGHT - thumbHeight;
        int thumbOffset = Math.round((float) this.scrollOffset / maxScroll * thumbTravel);

        int x = anchorX - renderedWidth;
        int trackX = x + SCROLLBAR_OFFSET_X;
        int trackY = anchorY + DESCRIPTION_START_Y;

        boolean inArea = mouseX >= trackX &&
            mouseX < trackX + SCROLLBAR_WIDTH &&
            mouseY >= trackY + thumbOffset &&
            mouseY < trackY + thumbOffset + thumbHeight;

        if (!inArea) {
            return false;
        }

        this.draggingScrollbar = true;
        this.dragStartMouseY = mouseY;
        this.dragStartScrollOffset = this.scrollOffset;

        return true;
    }

    public boolean mouseScrolled(
        double mouseX,
        double mouseY,
        double scrollY,
        int anchorX,
        int anchorY,
        List<GuiInfoEntry> entries
    )
    {
        int renderedWidth = this.getRenderedWidth();
        int renderedHeight = this.getRenderedHeight();
        if (!this.isOpen() || renderedWidth < PANEL_WIDTH || renderedHeight < PANEL_HEIGHT) {
            return false;
        }

        int x = anchorX - renderedWidth;

        int viewportX = x + DESCRIPTION_START_X;
        int viewportY = anchorY + DESCRIPTION_START_Y;

        boolean canScroll = mouseX >= viewportX &&
            mouseX < viewportX + DESCRIPTION_TEXT_WIDTH &&
            mouseY >= viewportY &&
            mouseY < viewportY + DESCRIPTION_VIEWPORT_HEIGHT;

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

    public boolean mouseDragged(
        double mouseY,
        int button,
        List<GuiInfoEntry> entries
    )
    {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT || !this.draggingScrollbar) {
            return false;
        }

        int contentHeight = this.getContentHeight(entries);
        int thumbHeight = this.getThumbHeight(contentHeight);
        int maxScroll = Math.max(
            0,
            contentHeight - DESCRIPTION_VIEWPORT_HEIGHT
        );

        if (maxScroll == 0) {
            return false;
        }

        int thumbTravel = DESCRIPTION_VIEWPORT_HEIGHT - thumbHeight;

        double deltaY = mouseY - this.dragStartMouseY;
        int deltaTextY = Math.round((float) deltaY / thumbTravel * maxScroll);
        int totalOffset = this.dragStartScrollOffset + deltaTextY;

        this.scrollOffset = Math.clamp(
            totalOffset,
            0,
            maxScroll
        );
        return true;
    }

    public boolean mouseReleased(int button) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT || !this.draggingScrollbar) {
            return false;
        }
        this.draggingScrollbar = false;
        return true;
    }

    private void drawDescription(
        @NonNull GuiGraphics graphics,
        @NonNull List<GuiInfoEntry> entries,
        int x,
        int y
    )
    {
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

    private void drawScrollbar(
        GuiGraphics graphics,
        List<GuiInfoEntry> entries,
        int x,
        int y
    )
    {
        int contentHeight = this.getContentHeight(entries);
        int thumbHeight = this.getThumbHeight(contentHeight);
        int maxScroll = Math.max(
            0,
            contentHeight - DESCRIPTION_VIEWPORT_HEIGHT
        );

        if (maxScroll == 0) {
            return;
        }

        int thumbTravel = DESCRIPTION_VIEWPORT_HEIGHT - thumbHeight;
        int thumbOffset = Math.round((float) this.scrollOffset / maxScroll * thumbTravel);

        int trackX = x + SCROLLBAR_OFFSET_X;
        int trackY = y + DESCRIPTION_START_Y;

        int thumbY = trackY + thumbOffset;

        graphics.fill(
            trackX,
            trackY,
            trackX + SCROLLBAR_WIDTH,
            trackY + DESCRIPTION_VIEWPORT_HEIGHT,
            SCROLLBAR_TRACK_COLOR
        );

        graphics.fill(
            trackX,
            thumbY,
            trackX + SCROLLBAR_WIDTH,
            thumbY + thumbHeight,
            SCROLLBAR_THUMB_COLOR
        );
    }

    private void drawContent(
        @NonNull GuiGraphics graphics,
        @NonNull List<GuiInfoEntry> entries,
        int panelX,
        int panelY,
        int width,
        int height
    )
    {
        Component title = Component.translatable("gui.astralspace.machine_info.title");

        graphics.enableScissor(
            panelX + BORDER_SIZE,
            panelY + BORDER_SIZE,
            panelX + width - BORDER_SIZE,
            panelY + height - BORDER_SIZE
        );

        this.drawTitle(
            graphics,
            this.font,
            title,
            panelX,
            panelY
        );

        this.drawDivider(
            graphics,
            panelX,
            panelY,
            width
        );

        graphics.enableScissor(
            panelX + DESCRIPTION_START_X,
            panelY + DESCRIPTION_START_Y,
            panelX + DESCRIPTION_START_X + DESCRIPTION_TEXT_WIDTH,
            panelY + DESCRIPTION_START_Y + DESCRIPTION_VIEWPORT_HEIGHT
        );

        this.drawDescription(
            graphics,
            entries,
            panelX,
            panelY
        );

        graphics.disableScissor();

        this.drawScrollbar(
            graphics,
            entries,
            panelX,
            panelY
        );

        graphics.disableScissor();
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

    private int getThumbHeight(int contentHeight) {
        float visibleFraction = (float) DESCRIPTION_VIEWPORT_HEIGHT / contentHeight;

        return Math.max(
            MIN_THUMB_HEIGHT,
            Math.round(visibleFraction * DESCRIPTION_VIEWPORT_HEIGHT)
        );
    }
}
