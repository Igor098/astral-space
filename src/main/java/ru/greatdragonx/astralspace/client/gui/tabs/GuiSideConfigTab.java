package ru.greatdragonx.astralspace.client.gui.tabs;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import ru.greatdragonx.astralspace.client.gui.components.*;
import ru.greatdragonx.astralspace.client.gui.render.GuiTextRenderer;
import ru.greatdragonx.astralspace.common.machine.MachineSide;
import ru.greatdragonx.astralspace.common.machine.MachineSideDirections;
import ru.greatdragonx.astralspace.common.machine.visual.VisualPortType;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

public final class GuiSideConfigTab extends GuiAnimatedTab {
    private static final float ANIMATION_STEP = 0.15f;

    private static final int PANEL_WIDTH = 140;
    private static final int PANEL_HEIGHT = 166;

    private static final int PREVIEW_SIZE = 48;

    private static final float INFO_TEXT_SCALE = 0.7f;
    private static final int INFO_TEXT_COLOR = 0xFFFFFFFF;

    private static final int PORT_HORIZONTAL_INSET = 10;
    private static final int PORT_ENTRY_HEIGHT = 29;
    private static final int PORT_MODE_HEIGHT = 16;
    private static final int PORT_MODE_OFFSET_Y = 10;

    private static final float PORT_TEXT_SCALE = 0.7f;

    private static final int PORT_LABEL_COLOR = 0xFF9CA8B4;
    private static final int PORT_TEXT_COLOR = 0xFFE4E8EC;
    private static final int PORT_BACKGROUND_COLOR = 0xFF20252D;
    private static final int PORT_BORDER_COLOR = 0xFF454C56;
    private static final int PORT_ARROW_COLOR = 0xFF9CA8B4;
    private static final int PORT_OPTION_HOVER_COLOR = 0xFF394550;

    private static final int PORT_SCROLL_STEP = 10;
    private static final int PORT_LIST_TOP = TAB_HEIGHT + 8 + PREVIEW_SIZE + 17;
    private static final int PORT_LIST_BOTTOM_INSET = BORDER_SIZE;

    private static final int PORT_SCROLLBAR_WIDTH = 3;
    private static final int PORT_SCROLLBAR_MIN_THUMB_HEIGHT = 12;

    private static final int PORT_SCROLLBAR_TRACK_COLOR = 0xFF53616F;
    private static final int PORT_SCROLLBAR_THUMB_COLOR = 0xFF98A8B8;

    private static final int MIN_PANEL_HEIGHT = PORT_LIST_TOP + BORDER_SIZE;

    private final GuiMachinePreview machinePreview;
    private final Font font;
    private final Identifier iconTexture;
    private final GuiPortModeChangeListener modeChangeListener;
    private @Nullable VisualPortType expandedPortType;

    private @Nullable MachineSide selectedSide;
    private int scrollOffset = 0;
    private boolean draggingScrollbar = false;

    private double scrollbarDragStartY;
    private int scrollbarDragStartOffset;
    private int scrollbarDragThumbTravel;
    private int scrollbarDragMaxScroll;

    private record DropdownLayout(
        GuiPortEntry entry, int x, int y, int width, int height, int triggerY
    ) {}

    public GuiSideConfigTab(
        Font font,
        Identifier iconTexture,
        GuiPortModeChangeListener modeChangeListener
    )
    {
        super(false, ANIMATION_STEP, PANEL_WIDTH, PANEL_HEIGHT);
        this.font = font;
        this.iconTexture = iconTexture;
        this.machinePreview = new GuiMachinePreview();
        this.modeChangeListener = Objects.requireNonNull(
            modeChangeListener,
            "modeChangeListener cannot be null"
        );
    }

    @Override
    public void close() {
        super.close();
        this.machinePreview.cancelInteraction();
        this.expandedPortType = null;
        this.scrollOffset = 0;
        this.draggingScrollbar = false;
    }

    public void render(
        int anchorX,
        int anchorY,
        float partialTick,
        @NonNull GuiGraphics graphics,
        @NonNull Component title,
        @NonNull BlockState blockState,
        @NonNull GuiSideConfigData data
    )
    {
        int targetHeight = this.getExpandedHeight(data);
        float progress = this.renderPanelBackground(
            graphics,
            anchorX,
            anchorY,
            partialTick,
            targetHeight
        );

        int width = this.getRenderedWidth();
        int height = this.getRenderedHeight();

        int x = this.getPanelX(anchorX);

        this.drawTabIcon(graphics, this.iconTexture, x, anchorY, progress);
        this.drawContent(x, anchorY, width, height, graphics, title, blockState, data);
    }

    public void renderDropdownOverlay(
        @NonNull GuiGraphics graphics,
        int mouseX,
        int mouseY,
        int anchorX,
        int anchorY,
        int screenHeight,
        @NonNull GuiSideConfigData data
    )
    {
        DropdownLayout layout = this.getDropdownLayout(anchorX, anchorY, screenHeight, data);
        if (layout == null) {
            return;
        }

        int dropdownX = layout.x();
        int dropdownY = layout.y();
        int dropdownWidth = layout.width();
        int dropdownHeight = layout.height();

        graphics.fill(
            dropdownX,
            dropdownY,
            dropdownX + dropdownWidth,
            dropdownY + dropdownHeight,
            PORT_BORDER_COLOR
        );

        graphics.fill(
            dropdownX + 1,
            dropdownY + 1,
            dropdownX + dropdownWidth - 1,
            dropdownY + dropdownHeight - 1,
            PORT_BACKGROUND_COLOR
        );

        List<GuiPortModeOption> options = layout.entry().availableModes();
        for (int i = 0; i < options.size(); i++) {
            GuiPortModeOption option = options.get(i);
            int optionY = dropdownY + 1 + i * PORT_MODE_HEIGHT;

            boolean hovered = mouseX >= dropdownX + 1 &&
                mouseX < dropdownX + dropdownWidth - 1 &&
                mouseY >= optionY &&
                mouseY < optionY + PORT_MODE_HEIGHT;
            if (hovered) {
                graphics.fill(
                    dropdownX + 1,
                    optionY,
                    dropdownX + dropdownWidth - 1,
                    optionY + PORT_MODE_HEIGHT,
                    PORT_OPTION_HOVER_COLOR
                );
            }

            float textY =
                optionY + (PORT_MODE_HEIGHT - this.font.lineHeight * PORT_TEXT_SCALE) / 2.0f;

            GuiTextRenderer.drawScaledText(
                graphics,
                this.font,
                option.label(),
                dropdownX + 5,
                textY,
                PORT_TEXT_SCALE,
                PORT_TEXT_COLOR
            );
        }
    }

    public boolean mouseClicked(
        @NonNull MouseButtonEvent event,
        int anchorX,
        int anchorY,
        @NonNull GuiSideConfigData data
    )
    {
        boolean fullyExpanded = this.isOpen() && this.getRenderedWidth() == PANEL_WIDTH;

        if (!fullyExpanded) {
            return false;
        }

        if (this.handlePortSelectClick(event, anchorX, anchorY, data)) {
            return true;
        }

        if (this.handleScrollbarClick(event, anchorX, anchorY, data)) {
            return true;
        }

        int panelX = this.getPanelX(anchorX);

        int previewX = panelX + (PANEL_WIDTH - PREVIEW_SIZE) / 2;
        int previewY = anchorY + TAB_HEIGHT + 8;

        return this.machinePreview.mouseClicked(event, previewX, previewY, PREVIEW_SIZE);
    }

    public boolean mouseDragged(@NonNull MouseButtonEvent event, double dragX, double dragY)
    {
        boolean fullyExpanded = this.isOpen() && this.getRenderedWidth() == PANEL_WIDTH;

        if (!fullyExpanded) {
            return false;
        }

        if (this.handleScrollbarDrag(event)) {
            return true;
        }

        return this.machinePreview.mouseDragged(event, dragX, dragY);
    }

    public boolean mouseReleased(
        @NonNull MouseButtonEvent event,
        int guiScale,
        @NonNull Direction facing
    )
    {
        boolean fullyExpanded = this.isOpen() && this.getRenderedWidth() == PANEL_WIDTH;

        if (!fullyExpanded) {
            this.machinePreview.cancelInteraction();
            return false;
        }

        if (this.draggingScrollbar) {
            this.draggingScrollbar = false;
            return true;
        }

        GuiMachinePreview.PreviewReleaseResult result = this.machinePreview.mouseReleased(
            event,
            guiScale
        );
        if (result.clicked()) {
            this.expandedPortType = null;
            if (result.hitDirection() == null) {
                this.selectedSide = null;
                this.scrollOffset = 0;
                this.draggingScrollbar = false;
            } else {
                this.selectedSide = MachineSideDirections.fromWorld(result.hitDirection(), facing);
            }
        }

        return result.consumed();
    }

    public boolean mouseScrolled(
        double mouseX,
        double mouseY,
        double scrollY,
        int anchorX,
        int anchorY,
        @NonNull GuiSideConfigData data
    )
    {
        if (!this.isOpen() || this.getRenderedWidth() != PANEL_WIDTH) {
            return false;
        }

        int maxScroll = this.getMaxScroll(data);
        if (maxScroll <= 0) {
            return false;
        }

        int viewportX = this.getPanelX(anchorX) + PORT_HORIZONTAL_INSET;
        int viewportY = anchorY + PORT_LIST_TOP;

        int viewportWidth = PANEL_WIDTH - 2 * PORT_HORIZONTAL_INSET;

        int viewportHeight = Math.clamp(
            this.getPortViewportHeight(data),
            0,
            this.getRenderedHeight() - BORDER_SIZE - PORT_LIST_TOP
        );

        boolean insideViewport = mouseX >= viewportX &&
            mouseX < viewportX + viewportWidth &&
            mouseY >= viewportY &&
            mouseY < viewportY + viewportHeight;

        if (!insideViewport) {
            return false;
        }

        int delta = (int) Math.round(scrollY * PORT_SCROLL_STEP);

        this.scrollOffset = Math.clamp(this.scrollOffset - delta, 0, maxScroll);
        this.expandedPortType = null;

        return true;
    }

    public boolean handleDropdownClick(
        @NonNull MouseButtonEvent event,
        int anchorX,
        int anchorY,
        int screenHeight,
        @NonNull GuiSideConfigData data
    )
    {
        if (event.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return false;
        }

        if (this.expandedPortType == null) {
            return false;
        }

        DropdownLayout layout = this.getDropdownLayout(anchorX, anchorY, screenHeight, data);
        if (layout == null) {
            this.expandedPortType = null;
            return false;
        }

        boolean clickedTrigger = event.x() >= layout.x() &&
            event.x() < layout.x() + layout.width() &&
            event.y() >= layout.triggerY() &&
            event.y() < layout.triggerY() + PORT_MODE_HEIGHT;

        if (clickedTrigger) {
            this.expandedPortType = null;
            return true;
        }

        boolean inside = event.x() >= layout.x() &&
            event.x() < layout.x() + layout.width() &&
            event.y() >= layout.y() &&
            event.y() < layout.y() + layout.height();
        if (!inside) {
            this.expandedPortType = null;
            return false;
        }

        List<GuiPortModeOption> options = layout.entry().availableModes();

        int optionsTop = layout.y() + 1;
        int optionsBottom = optionsTop + options.size() * PORT_MODE_HEIGHT;
        if (event.y() >= optionsTop && event.y() < optionsBottom) {
            int optionIndex = (int) (event.y() - optionsTop) / PORT_MODE_HEIGHT;

            GuiPortModeOption option = options.get(optionIndex);

            this.modeChangeListener.onModeSelected(
                this.selectedSide,
                layout.entry().type(),
                option.id()
            );
        }

        this.expandedPortType = null;
        return true;
    }

    private void drawContent(
        int panelX,
        int panelY,
        int width,
        int height,
        @NonNull GuiGraphics graphics,
        @NonNull Component title,
        @NonNull BlockState blockState,
        @NonNull GuiSideConfigData data
    )
    {
        int previewX = panelX + (PANEL_WIDTH - PREVIEW_SIZE) / 2;
        int previewY = panelY + TAB_HEIGHT + 8;

        graphics.enableScissor(
            panelX + BORDER_SIZE,
            panelY + BORDER_SIZE,
            panelX + width - BORDER_SIZE,
            panelY + height - BORDER_SIZE
        );

        this.drawTitle(graphics, this.font, title, panelX, panelY);

        this.drawDivider(graphics, panelX, panelY, width);

        int clipLeft = panelX + BORDER_SIZE;
        int clipTop = panelY + BORDER_SIZE;
        int clipRight = panelX + width - BORDER_SIZE;
        int clipBottom = panelY + height - BORDER_SIZE;

        boolean previewVisible = previewX < clipRight &&
            previewX + PREVIEW_SIZE > clipLeft &&
            previewY < clipBottom &&
            previewY + PREVIEW_SIZE > clipTop;

        if (previewVisible) {
            this.machinePreview.render(
                previewX,
                previewY,
                PREVIEW_SIZE,
                graphics,
                blockState,
                this.selectedSide
            );
        }

        this.drawSelectedSideInfo(graphics, panelX, previewY);
        this.drawScrollablePortEntries(panelX, panelY, height, previewY, graphics, data);

        if (height > PORT_LIST_TOP) {
            this.drawScrollbar(panelX, panelY, height, graphics, data);
        }

        graphics.disableScissor();
    }

    private void drawSelectedSideInfo(@NonNull GuiGraphics graphics, int panelX, int previewY)
    {
        int textY = previewY + PREVIEW_SIZE + 8;

        MachineSide side = this.selectedSide;
        Component text = side == null ? Component.translatable(
            "gui.astralspace.side_config.select_side") : Component.translatable(
            "gui.astralspace.side_config.side." + side.name().toLowerCase(Locale.ROOT));

        float textWidth = this.font.width(text) * INFO_TEXT_SCALE;
        float textX = panelX + PANEL_WIDTH / 2.0f - textWidth / 2.0f;

        GuiTextRenderer.drawScaledText(
            graphics,
            this.font,
            text,
            textX,
            textY,
            INFO_TEXT_SCALE,
            INFO_TEXT_COLOR
        );
    }

    private void drawPortEntries(
        int panelX,
        int previewY,
        @NonNull GuiGraphics graphics,
        @NonNull GuiSideConfigData data
    )
    {
        if (this.selectedSide == null) {
            return;
        }

        List<GuiPortEntry> entries = data.portsForSide(this.selectedSide);
        if (entries.isEmpty()) {
            return;
        }

        int startY = previewY + PREVIEW_SIZE + 17 - this.scrollOffset;

        for (GuiPortEntry entry : entries) {
            int labelY = startY;
            int modeY = startY + PORT_MODE_OFFSET_Y;

            int modeX = panelX + PORT_HORIZONTAL_INSET;
            int modeWidth = PANEL_WIDTH - PORT_HORIZONTAL_INSET - this.getPortModeRightInset(data);

            Component typeName = Component.translatable("gui.astralspace.port_type." +
                entry.type().name().toLowerCase(Locale.ROOT));

            GuiTextRenderer.drawScaledText(
                graphics,
                this.font,
                typeName,
                modeX,
                labelY,
                PORT_TEXT_SCALE,
                PORT_LABEL_COLOR
            );

            graphics.fill(
                modeX,
                modeY,
                modeX + modeWidth,
                modeY + PORT_MODE_HEIGHT,
                PORT_BORDER_COLOR
            );

            graphics.fill(
                modeX + 1,
                modeY + 1,
                modeX + modeWidth - 1,
                modeY + PORT_MODE_HEIGHT - 1,
                PORT_BACKGROUND_COLOR
            );

            Component modeName = entry.modeName();

            float availableTextWidth = modeWidth - 32;

            float scale = Math.min(
                PORT_TEXT_SCALE,
                availableTextWidth / Math.max(1, this.font.width(modeName))
            );

            float textY = modeY + (PORT_MODE_HEIGHT - this.font.lineHeight * scale) / 2.0f;

            GuiTextRenderer.drawScaledText(
                graphics,
                this.font,
                modeName,
                modeX + 5,
                textY,
                scale,
                PORT_TEXT_COLOR
            );

            int arrowX = modeX + modeWidth - 13;
            int arrowY = modeY + (PORT_MODE_HEIGHT - 4) / 2;

            graphics.fill(arrowX, arrowY, arrowX + 7, arrowY + 1, PORT_ARROW_COLOR);
            graphics.fill(arrowX + 1, arrowY + 1, arrowX + 6, arrowY + 2, PORT_ARROW_COLOR);
            graphics.fill(arrowX + 2, arrowY + 2, arrowX + 5, arrowY + 3, PORT_ARROW_COLOR);
            graphics.fill(arrowX + 3, arrowY + 3, arrowX + 4, arrowY + 4, PORT_ARROW_COLOR);

            startY += PORT_ENTRY_HEIGHT;
        }
    }

    private void drawScrollablePortEntries(
        int panelX,
        int panelY,
        int height,
        int previewY,
        @NonNull GuiGraphics graphics,
        @NonNull GuiSideConfigData data
    )
    {
        this.scrollOffset = Math.clamp(this.scrollOffset, 0, this.getMaxScroll(data));

        int viewportTop = panelY + PORT_LIST_TOP;

        int viewportBottom = Math.min(
            panelY + height - BORDER_SIZE,
            viewportTop + this.getPortViewportHeight(data)
        );

        if (viewportBottom > viewportTop) {
            graphics.enableScissor(
                panelX + PORT_HORIZONTAL_INSET,
                viewportTop,
                panelX + PANEL_WIDTH - PORT_HORIZONTAL_INSET,
                viewportBottom
            );

            this.drawPortEntries(panelX, previewY, graphics, data);

            graphics.disableScissor();
        }
    }

    private void drawScrollbar(
        int panelX,
        int panelY,
        int renderedHeight,
        @NonNull GuiGraphics graphics,
        @NonNull GuiSideConfigData data
    )
    {
        int maxScroll = this.getMaxScroll(data);
        if (maxScroll <= 0 || this.selectedSide == null) {
            return;
        }

        int viewportHeight = Math.clamp(
            this.getPortViewportHeight(data),
            0,
            renderedHeight - BORDER_SIZE - PORT_LIST_TOP
        );

        if (viewportHeight <= 0) {
            return;
        }

        int contentHeight = data.portsForSide(this.selectedSide).size() * PORT_ENTRY_HEIGHT;

        int thumbHeight = Math.clamp(
            Math.round((float) viewportHeight / contentHeight * viewportHeight),
            Math.min(PORT_SCROLLBAR_MIN_THUMB_HEIGHT, viewportHeight),
            viewportHeight
        );

        int thumbTravel = viewportHeight - thumbHeight;

        int thumbOffset = Math.round((float) this.scrollOffset / maxScroll * thumbTravel);

        int trackX = panelX + PANEL_WIDTH - BORDER_SIZE - PORT_SCROLLBAR_WIDTH;
        int trackY = panelY + PORT_LIST_TOP;

        graphics.fill(
            trackX,
            trackY,
            trackX + PORT_SCROLLBAR_WIDTH,
            trackY + viewportHeight,
            PORT_SCROLLBAR_TRACK_COLOR
        );

        graphics.fill(
            trackX,
            trackY + thumbOffset,
            trackX + PORT_SCROLLBAR_WIDTH,
            trackY + thumbOffset + thumbHeight,
            PORT_SCROLLBAR_THUMB_COLOR
        );
    }

    private boolean handleScrollbarClick(
        @NonNull MouseButtonEvent event,
        int anchorX,
        int anchorY,
        @NonNull GuiSideConfigData data
    )
    {
        if (event.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT || this.selectedSide == null) {
            return false;
        }

        int maxScroll = this.getMaxScroll(data);

        if (maxScroll <= 0) {
            return false;
        }

        int viewportHeight = Math.clamp(
            this.getPortViewportHeight(data),
            0,
            this.getRenderedHeight() - BORDER_SIZE - PORT_LIST_TOP
        );

        if (viewportHeight < PORT_SCROLLBAR_MIN_THUMB_HEIGHT) {
            return false;
        }

        int contentHeight = data.portsForSide(this.selectedSide).size() * PORT_ENTRY_HEIGHT;

        int thumbHeight = Math.clamp(
            Math.round((float) viewportHeight / contentHeight * viewportHeight),
            Math.min(PORT_SCROLLBAR_MIN_THUMB_HEIGHT, viewportHeight),
            viewportHeight
        );

        int thumbTravel = viewportHeight - thumbHeight;

        if (thumbTravel <= 0) {
            return false;
        }

        int thumbOffset = Math.round((float) this.scrollOffset / maxScroll * thumbTravel);

        int trackX = this.getPanelX(anchorX) + PANEL_WIDTH - BORDER_SIZE - PORT_SCROLLBAR_WIDTH;

        int thumbY = anchorY + PORT_LIST_TOP + thumbOffset;

        boolean hovered = event.x() >= trackX &&
            event.x() < trackX + PORT_SCROLLBAR_WIDTH &&
            event.y() >= thumbY &&
            event.y() < thumbY + thumbHeight;

        if (!hovered) {
            return false;
        }

        this.expandedPortType = null;
        this.draggingScrollbar = true;
        this.scrollbarDragStartY = event.y();
        this.scrollbarDragStartOffset = this.scrollOffset;
        this.scrollbarDragThumbTravel = thumbTravel;
        this.scrollbarDragMaxScroll = maxScroll;

        return true;
    }

    private boolean handlePortSelectClick(
        @NonNull MouseButtonEvent event,
        int anchorX,
        int anchorY,
        @NonNull GuiSideConfigData data
    )
    {
        if (event.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT || this.selectedSide == null) {
            return false;
        }

        int panelX = this.getPanelX(anchorX);
        int modeX = panelX + PORT_HORIZONTAL_INSET;

        int modeWidth = PANEL_WIDTH - PORT_HORIZONTAL_INSET - this.getPortModeRightInset(data);

        int startY = anchorY + PORT_LIST_TOP - this.scrollOffset;

        List<GuiPortEntry> entries = data.portsForSide(this.selectedSide);

        for (int i = 0; i < entries.size(); i++) {
            GuiPortEntry entry = entries.get(i);

            int modeY = startY + i * PORT_ENTRY_HEIGHT + PORT_MODE_OFFSET_Y;

            int visibleTop = anchorY + PORT_LIST_TOP;
            int visibleBottom = Math.min(
                anchorY + this.getRenderedHeight() - BORDER_SIZE,
                visibleTop + this.getPortViewportHeight(data)
            );

            boolean hovered =
                event.x() >= modeX && event.x() < modeX + modeWidth && event.y() >= Math.max(
                    modeY,
                    visibleTop
                ) && event.y() < Math.min(modeY + PORT_MODE_HEIGHT, visibleBottom);

            if (!hovered) {
                continue;
            }

            this.expandedPortType = this.expandedPortType == entry.type() ? null : entry.type();

            return true;
        }

        return false;
    }

    private boolean handleScrollbarDrag(
        @NonNull MouseButtonEvent event
    )
    {
        if (!this.draggingScrollbar) {
            return false;
        }

        double deltaY = event.y() - this.scrollbarDragStartY;

        int deltaScroll = Math.round((float) deltaY / this.scrollbarDragThumbTravel *
            this.scrollbarDragMaxScroll);

        this.scrollOffset = Math.clamp(
            this.scrollbarDragStartOffset + deltaScroll,
            0,
            this.scrollbarDragMaxScroll
        );

        return true;
    }

    private @Nullable DropdownLayout getDropdownLayout(
        int anchorX,
        int anchorY,
        int screenHeight,
        @NonNull GuiSideConfigData data
    )
    {
        if (!this.isOpen() ||
            this.getRenderedWidth() != PANEL_WIDTH ||
            this.selectedSide == null ||
            this.expandedPortType == null)
        {
            return null;
        }

        List<GuiPortEntry> entries = data.portsForSide(this.selectedSide);

        for (int i = 0; i < entries.size(); i++) {
            GuiPortEntry entry = entries.get(i);

            if (entry.type() != this.expandedPortType) {
                continue;
            }

            int modeY = anchorY + PORT_LIST_TOP + i * PORT_ENTRY_HEIGHT + PORT_MODE_OFFSET_Y -
                this.scrollOffset;

            int visibleTop = anchorY + PORT_LIST_TOP;

            int visibleBottom = Math.min(
                anchorY + this.getRenderedHeight() - BORDER_SIZE,
                visibleTop + this.getPortViewportHeight(data)
            );

            if (modeY + PORT_MODE_HEIGHT <= visibleTop || modeY >= visibleBottom) {
                return null;
            }

            int dropdownHeight = entry.availableModes().size() * PORT_MODE_HEIGHT + 2;

            int dropdownY = modeY + PORT_MODE_HEIGHT;

            if (dropdownY + dropdownHeight > screenHeight) {
                dropdownY = modeY - dropdownHeight;
            }

            dropdownY = Math.clamp(dropdownY, 0, Math.max(0, screenHeight - dropdownHeight));

            return new DropdownLayout(
                entry,
                this.getPanelX(anchorX) + PORT_HORIZONTAL_INSET,
                dropdownY,
                PANEL_WIDTH - PORT_HORIZONTAL_INSET - this.getPortModeRightInset(data),
                dropdownHeight,
                modeY
            );
        }

        return null;
    }

    private int getExpandedHeight(@NonNull GuiSideConfigData data) {
        int entryCount =
            this.selectedSide == null ? 0 : data.portsForSide(this.selectedSide).size();

        return Math.min(PANEL_HEIGHT, MIN_PANEL_HEIGHT + entryCount * PORT_ENTRY_HEIGHT);
    }

    private int getPortViewportHeight(@NonNull GuiSideConfigData data) {
        return Math.max(0, this.getExpandedHeight(data) - PORT_LIST_TOP - PORT_LIST_BOTTOM_INSET);
    }

    private int getMaxScroll(@NonNull GuiSideConfigData data) {
        if (this.selectedSide == null) {
            return 0;
        }

        int entryCount = data.portsForSide(this.selectedSide).size();
        int contentHeight = entryCount * PORT_ENTRY_HEIGHT;

        return Math.max(0, contentHeight - this.getPortViewportHeight(data));
    }

    private int getPortModeRightInset(@NonNull GuiSideConfigData data) {
        return this.getMaxScroll(data) > 0 ? 14 : 10;
    }
}
