package ru.greatdragonx.astralspace.client.gui.tabs;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;
import ru.greatdragonx.astralspace.client.gui.components.GuiStatisticEntry;
import ru.greatdragonx.astralspace.client.gui.components.GuiStatisticsData;
import ru.greatdragonx.astralspace.client.gui.render.GuiTextRenderer;

import java.util.List;

public final class GuiStatisticsTab extends GuiAnimatedTab {
    private static final float ANIMATION_STEP = 0.15f;
    private static final float STATISTIC_TEXT_SCALE = 0.65f;

    private static final int PANEL_WIDTH = 100;
    private static final int BASE_PANEL_HEIGHT = 46;

    private static final int FIRST_ENTRY_Y = 27;
    private static final int ENTRY_SPACING = 15;

    private final Font font;
    private final Identifier iconTexture;

    public GuiStatisticsTab(
        Font font,
        Identifier iconTexture
    )
    {
        super(
            true,
            ANIMATION_STEP,
            PANEL_WIDTH,
            BASE_PANEL_HEIGHT
        );
        this.font = font;
        this.iconTexture = iconTexture;
    }

    public void render(
        @NonNull GuiGraphics graphics,
        int anchorX,
        int anchorY,
        float partialTick,
        @NonNull GuiStatisticsData data
    )
    {
        int expandedHeight = this.getExpandedHeight(data);

        float progress = this.renderPanelBackground(
            graphics,
            anchorX,
            anchorY,
            partialTick,
            expandedHeight
        );

        int width = this.getRenderedWidth();
        int height = this.getRenderedHeight();

        int x = this.getPanelX(anchorX);

        this.drawTabIcon(
            graphics,
            this.iconTexture,
            x,
            anchorY,
            progress
        );

        this.drawContent(
            graphics,
            data,
            x,
            anchorY,
            width,
            height
        );
    }

    public void renderTooltip(
        GuiGraphics graphics,
        int mouseX,
        int mouseY,
        int anchorX,
        int anchorY,
        Component tooltip
    )
    {
        boolean isHovered = this.isHeaderHovered(
            mouseX,
            mouseY,
            anchorX,
            anchorY
        );

        boolean isFullyClosed = this.isFullyClosed();

        if (isFullyClosed && isHovered) {
            graphics.setTooltipForNextFrame(
                this.font,
                tooltip,
                mouseX,
                mouseY
            );
        }
    }

    private void drawStatisticEntry(
        @NonNull GuiGraphics graphics,
        @NonNull GuiStatisticEntry entry,
        int anchorX,
        int anchorY
    )
    {
        Component label = entry.label();
        Component value = entry.value();

        GuiTextRenderer.drawScaledText(
            graphics,
            this.font,
            label,
            anchorX + 6,
            anchorY,
            STATISTIC_TEXT_SCALE,
            0xFFD6DCE1
        );

        float valueWidth = this.font.width(value) * STATISTIC_TEXT_SCALE;

        GuiTextRenderer.drawScaledText(
            graphics,
            this.font,
            value,
            anchorX + PANEL_WIDTH - 6 - valueWidth,
            anchorY,
            STATISTIC_TEXT_SCALE,
            0xFF78D29F
        );
    }

    private void drawStatisticsEntries(
        @NonNull GuiGraphics graphics,
        @NonNull GuiStatisticsData data,
        int anchorX,
        int anchorY
    )
    {
        List<GuiStatisticEntry> entries = data.entries();

        for (int i = 0; i < entries.size(); i++) {
            GuiStatisticEntry entry = entries.get(i);

            int entryY = anchorY + FIRST_ENTRY_Y + i * ENTRY_SPACING;
            this.drawStatisticEntry(
                graphics,
                entry,
                anchorX,
                entryY
            );
        }
    }

    private void drawContent(
        @NonNull GuiGraphics graphics,
        @NonNull GuiStatisticsData data,
        int panelX,
        int panelY,
        int width,
        int height
    )
    {
        Component title = data.title();

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

        this.drawStatisticsEntries(
            graphics,
            data,
            panelX,
            panelY
        );

        graphics.disableScissor();
    }

    private int getExpandedHeight(@NonNull GuiStatisticsData data) {
        int entryCount = Math.max(
            data.entries()
                .size(),
            1
        );

        return BASE_PANEL_HEIGHT + (entryCount - 1) * ENTRY_SPACING;
    }
}
