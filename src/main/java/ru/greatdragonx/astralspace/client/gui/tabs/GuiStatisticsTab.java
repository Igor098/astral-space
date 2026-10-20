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
import ru.greatdragonx.astralspace.client.gui.components.GuiStatisticEntry;
import ru.greatdragonx.astralspace.client.gui.components.GuiStatisticsData;
import ru.greatdragonx.astralspace.client.gui.render.GuiTextRenderer;
import ru.greatdragonx.astralspace.client.gui.render.NineSliceRenderer;

import java.util.List;

public final class GuiStatisticsTab {
    private static final float ANIMATION_STEP = 0.15f;
    private static final float STATISTIC_TEXT_SCALE = 0.65f;

    private static final int TAB_WIDTH = 20;
    private static final int TAB_HEIGHT = 20;

    private static final int PANEL_WIDTH = 100;
    private static final int BASE_PANEL_HEIGHT = 46;

    private static final int TAB_Y = 20;

    private static final int TEXTURE_SIZE = 20;
    private static final int BORDER_SIZE = 5;

    private static final int ICON_WIDTH = 16;
    private static final int ICON_HEIGHT = 16;

    private static final int FIRST_ENTRY_Y = 27;
    private static final int ENTRY_SPACING = 15;

    private static final Identifier BACKGROUND_TEXTURE = Identifier.fromNamespaceAndPath(
        AstralSpace.MOD_ID,
        "textures/gui/common/tabs/tab_background.png"
    );

    private final GuiTabAnimation animation;
    private final Font font;
    private final Identifier iconTexture;
    private int renderedWidth = TAB_WIDTH;

    public GuiStatisticsTab(
        Font font,
        Identifier iconTexture
    ) {
        this.font = font;
        this.iconTexture = iconTexture;
        this.animation = new GuiTabAnimation(ANIMATION_STEP);
    }

    public void render(
        @NonNull GuiGraphics graphics,
        int screenLeft,
        int screenTop,
        float partialTick,
        @NonNull GuiStatisticsData data
    ) {
        float progress = this.animation.getProgress(partialTick);

        int expandedHeight = this.getExpandedHeight(data);
        int width = this.getAnimatedWidth(progress);
        int height = this.getAnimatedHeight(
            progress,
            expandedHeight
        );

        this.renderedWidth = width;

        int x = this.getX(
            screenLeft,
            width
        );
        int y = this.getY(screenTop);

        NineSliceRenderer.draw(
            graphics,
            BACKGROUND_TEXTURE,
            x,
            y,
            width,
            height,
            TEXTURE_SIZE,
            BORDER_SIZE
        );

        this.drawIcon(
            graphics,
            progress,
            x,
            y
        );

        this.drawContent(
            graphics,
            data,
            x,
            y,
            width,
            height
        );
    }

    public void renderTooltip(
        GuiGraphics graphics,
        int mouseX,
        int mouseY,
        int screenLeft,
        int screenTop,
        Component tooltip
    ) {
        boolean isHovered = this.isHovered(
            mouseX,
            mouseY,
            screenLeft,
            screenTop
        );

        boolean isFullyClosed = this.animation.isFullyClosed();

        if (isFullyClosed && isHovered) {
            graphics.setTooltipForNextFrame(
                this.font,
                tooltip,
                mouseX,
                mouseY
            );
        }
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

    public boolean isHovered(
        double mouseX,
        double mouseY,
        int screenLeft,
        int screenTop
    ) {
        int x = this.getX(
            screenLeft,
            this.renderedWidth
        );
        int y = this.getY(screenTop);
        int width = this.renderedWidth;

        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + TAB_HEIGHT;
    }

    public boolean mouseClicked(
        @NonNull MouseButtonEvent event,
        int screenLeft,
        int screenTop
    ) {
        if (event.button() == 0 && this.isHovered(
            event.x(),
            event.y(),
            screenLeft,
            screenTop
        )) {
            this.toggle();
            return true;
        }

        return false;
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

    private void drawStatisticEntry(
        @NonNull GuiGraphics graphics,
        @NonNull GuiStatisticEntry entry,
        int x,
        int y
    ) {
        Component label = entry.label();
        Component value = entry.value();

        GuiTextRenderer.drawScaledText(
            graphics,
            this.font,
            label,
            x + 6,
            y,
            STATISTIC_TEXT_SCALE,
            0xFFD6DCE1
        );

        float valueWidth = this.font.width(value) * STATISTIC_TEXT_SCALE;

        GuiTextRenderer.drawScaledText(
            graphics,
            this.font,
            value,
            x + PANEL_WIDTH - 6 - valueWidth,
            y,
            STATISTIC_TEXT_SCALE,
            0xFF78D29F
        );
    }

    private void drawStatisticsEntries(
        @NonNull GuiGraphics graphics,
        @NonNull GuiStatisticsData data,
        int x,
        int y
    ) {
        List<GuiStatisticEntry> entries = data.entries();

        for (int i = 0; i < entries.size(); i++) {
            GuiStatisticEntry entry = entries.get(i);

            int entryY = y + FIRST_ENTRY_Y + i * ENTRY_SPACING;
            this.drawStatisticEntry(
                graphics,
                entry,
                x,
                entryY
            );
        }
    }

    private void drawTitle(
        @NonNull GuiGraphics graphics,
        @NonNull GuiStatisticsData data,
        int x,
        int y
    ) {
        Component title = data.title();
        GuiTextRenderer.drawScaledText(
            graphics,
            this.font,
            title,
            x + 24,
            y + 7.5f,
            0.75f,
            0xFFE4E8EC
        );
    }

    private void drawContent(
        @NonNull GuiGraphics graphics,
        @NonNull GuiStatisticsData data,
        int x,
        int y,
        int width,
        int height
    ) {
        graphics.enableScissor(
            x + BORDER_SIZE,
            y + BORDER_SIZE,
            x + width - BORDER_SIZE,
            y + height - BORDER_SIZE
        );

        this.drawTitle(
            graphics,
            data,
            x,
            y
        );

        graphics.fill(
            x + 5,
            y + 19,
            x + width - 5,
            y + 20,
            0xFF626C75
        );

        this.drawStatisticsEntries(
            graphics,
            data,
            x,
            y
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

    private int getAnimatedHeight(
        float progress,
        int expandedHeight
    ) {
        int delta = expandedHeight - TAB_HEIGHT;
        float normalizedProgress = Math.clamp(
            progress,
            0.0f,
            1.0f
        );
        return Math.round(TAB_HEIGHT + delta * normalizedProgress);
    }

    private int getX(
        int screenLeft,
        int animatedWidth
    ) {
        return screenLeft - animatedWidth;
    }

    private int getY(int screenTop) {
        return screenTop + TAB_Y;
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

