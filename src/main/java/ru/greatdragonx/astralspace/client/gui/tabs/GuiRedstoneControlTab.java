package ru.greatdragonx.astralspace.client.gui.tabs;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import ru.greatdragonx.astralspace.AstralSpace;
import ru.greatdragonx.astralspace.client.gui.render.GuiTextRenderer;
import ru.greatdragonx.astralspace.common.machine.redstone.RedstoneControlMode;

import java.util.List;

public final class GuiRedstoneControlTab extends GuiAnimatedTab {
    private static final float ANIMATION_STEP = 0.15f;

    private static final int PANEL_WIDTH = 100;
    private static final int PANEL_HEIGHT = 96;

    private static final int MODE_BUTTON_SIZE = 20;
    private static final int MODE_ICON_SIZE = 12;
    private static final int MODE_BUTTON_GAP = 4;
    private static final int MODE_BUTTONS_Y = 31;
    private static final int MODE_BUTTON_SOURCE_SIZE = 80;
    private static final int MODE_ICON_SOURCE_SIZE = 48;

    private static final int CONTENT_HORIZONTAL_INSET = 8;

    private static final int STATUS_ROW_Y = 64;
    private static final int SIGNAL_ROW_Y = 76;

    private static final float INFO_LABEL_SCALE = 0.7f;
    private static final float INFO_VALUE_SCALE = 0.6f;

    private static final int INFO_LABEL_COLOR = 0xFFA9B9CA;

    private static final Identifier MODE_BUTTONS_TEXTURE = Identifier.fromNamespaceAndPath(AstralSpace.MOD_ID,
        "textures/gui/common/redstone/redstone_mode_buttons.png"
    );

    private static final Identifier MODE_ICONS_TEXTURE =
        Identifier.fromNamespaceAndPath(
            AstralSpace.MOD_ID,
            "textures/gui/common/redstone/redstone_mode_icons.png"
        );

    private static final Identifier ICON_TEXTURE =
        Identifier.fromNamespaceAndPath(
            AstralSpace.MOD_ID,
            "textures/gui/common/tabs/redstone_control.png"
        );

    private static final List<RedstoneControlMode> DISPLAY_MODES =
        List.of(
            RedstoneControlMode.IGNORE,
            RedstoneControlMode.WHEN_UNPOWERED,
            RedstoneControlMode.WHEN_POWERED
        );

    private final Font font;

    public GuiRedstoneControlTab(
        Font font
    )
    {
        super(false, ANIMATION_STEP, PANEL_WIDTH, PANEL_HEIGHT);
        this.font = font;
    }

    public void render(
        @NonNull GuiGraphics graphics,
        @NonNull RedstoneControlMode selectedMode,
        boolean redstoneBlocked,
        boolean hasSignal,
        int anchorX,
        int anchorY,
        float partialTick
    )
    {
        float progress = this.renderPanelBackground(graphics, anchorX, anchorY, partialTick);

        int width = this.getRenderedWidth();
        int height = this.getRenderedHeight();

        int x = this.getPanelX(anchorX);

        this.drawContent(
            graphics,
            selectedMode,
            redstoneBlocked,
            hasSignal,
            x,
            anchorY,
            width,
            height
        );

        this.drawTabIcon(graphics, ICON_TEXTURE, x, anchorY, progress);
    }

    public @Nullable RedstoneControlMode getHoveredMode(
        double mouseX,
        double mouseY,
        int anchorX,
        int anchorY
    )
    {
        int renderedWidth = this.getRenderedWidth();
        int renderedHeight = this.getRenderedHeight();

        if (!this.isOpen() || renderedWidth < PANEL_WIDTH || renderedHeight < PANEL_HEIGHT) {
            return null;
        }

        int panelX = this.getPanelX(anchorX);

        if (mouseX < panelX ||
            mouseX >= panelX + renderedWidth ||
            mouseY < anchorY + TAB_HEIGHT ||
            mouseY >= anchorY + renderedHeight)
        {
            return null;
        }

        int totalWidth =
            DISPLAY_MODES.size() * MODE_BUTTON_SIZE + (DISPLAY_MODES.size() - 1) * MODE_BUTTON_GAP;
        int startX = panelX + (PANEL_WIDTH - totalWidth) / 2;
        int buttonY = anchorY + MODE_BUTTONS_Y;

        for (int i = 0; i < DISPLAY_MODES.size(); i++) {
            RedstoneControlMode mode = DISPLAY_MODES.get(i);

            int buttonX = startX + i * (MODE_BUTTON_SIZE + MODE_BUTTON_GAP);
            boolean isButtonClicked = mouseX >= buttonX &&
                mouseX < buttonX + MODE_BUTTON_SIZE &&
                mouseY >= buttonY &&
                mouseY < buttonY + MODE_BUTTON_SIZE;
            if (isButtonClicked) {
                return mode;
            }
        }

        return null;
    }

    public void renderTooltip(
        GuiGraphics graphics,
        int mouseX,
        int mouseY,
        int anchorX,
        int anchorY,
        RedstoneControlMode selectedMode
    )
    {
        RedstoneControlMode hoveredMode = this.getHoveredMode(mouseX, mouseY, anchorX, anchorY);

        boolean headerHovered = this.isFullyClosed() && this.isHeaderHovered(
            mouseX,
            mouseY,
            anchorX,
            anchorY
        );

        if (hoveredMode == null && !headerHovered) {
            return;
        }

        RedstoneControlMode mode = hoveredMode != null ? hoveredMode : selectedMode;

        Component tooltip = switch (mode) {
            case IGNORE ->
                Component.translatable("gui.astralspace.redstone_control.tooltip.ignore");
            case WHEN_UNPOWERED -> Component.translatable(
                "gui.astralspace.redstone_control.tooltip.when_unpowered");
            case WHEN_POWERED -> Component.translatable(
                "gui.astralspace.redstone_control.tooltip.when_powered");
        };

        if (headerHovered) {
            tooltip =
                Component.translatable(
                    "gui.astralspace.redstone_control.tooltip.current_mode",
                    tooltip
                );
        }

        graphics.setTooltipForNextFrame(this.font, tooltip, mouseX, mouseY);
    }

    private void drawModeButtons(
        GuiGraphics graphics,
        int anchorX,
        int anchorY,
        RedstoneControlMode selectedMode
    )
    {
        int totalWidth =
            DISPLAY_MODES.size() * MODE_BUTTON_SIZE + (DISPLAY_MODES.size() - 1) * MODE_BUTTON_GAP;

        int startX = anchorX + (PANEL_WIDTH - totalWidth) / 2;
        int buttonY = anchorY + MODE_BUTTONS_Y;

        int iconOffset = (MODE_BUTTON_SIZE - MODE_ICON_SIZE) / 2;

        for (int i = 0; i < DISPLAY_MODES.size(); i++) {
            RedstoneControlMode mode = DISPLAY_MODES.get(i);

            int buttonX = startX + i * (MODE_BUTTON_SIZE + MODE_BUTTON_GAP);

            int buttonU = mode == selectedMode ? MODE_BUTTON_SOURCE_SIZE : 0;

            int iconU = i * MODE_ICON_SOURCE_SIZE;

            int iconX = buttonX + iconOffset;
            int iconY = buttonY + iconOffset;

            graphics.blit(
                RenderPipelines.GUI_TEXTURED,
                MODE_BUTTONS_TEXTURE,
                buttonX,
                buttonY,
                buttonU,
                0,
                MODE_BUTTON_SIZE,
                MODE_BUTTON_SIZE,
                MODE_BUTTON_SOURCE_SIZE,
                MODE_BUTTON_SOURCE_SIZE,
                MODE_BUTTON_SOURCE_SIZE * 2,
                MODE_BUTTON_SOURCE_SIZE
            );

            graphics.blit(
                RenderPipelines.GUI_TEXTURED,
                MODE_ICONS_TEXTURE,
                iconX,
                iconY,
                iconU,
                0,
                MODE_ICON_SIZE,
                MODE_ICON_SIZE,
                MODE_ICON_SOURCE_SIZE,
                MODE_ICON_SOURCE_SIZE,
                MODE_ICON_SOURCE_SIZE * DISPLAY_MODES.size(),
                MODE_ICON_SOURCE_SIZE
            );
        }
    }

    private void drawInfoRow(
        @NonNull GuiGraphics graphics,
        int panelX,
        int panelY,
        int rowCenterY,
        @NonNull Component label,
        @NonNull Component value,
        int valueColor
    )
    {
        float leftX = panelX + CONTENT_HORIZONTAL_INSET;
        float rightX = panelX + PANEL_WIDTH - CONTENT_HORIZONTAL_INSET;

        float labelWidth = this.font.width(label) * INFO_LABEL_SCALE;

        float availableWidth = Math.max(1.0f, rightX - leftX - labelWidth - 4.0f);

        float valueScale = Math.min(
            INFO_VALUE_SCALE,
            availableWidth / Math.max(1, this.font.width(value))
        );

        float valueWidth = this.font.width(value) * valueScale;

        float centerY = panelY + rowCenterY;

        float labelY = centerY - (this.font.lineHeight * INFO_LABEL_SCALE) / 2.0f;

        float valueY = centerY - (this.font.lineHeight * valueScale) / 2.0f;

        GuiTextRenderer.drawScaledText(
            graphics,
            this.font,
            label,
            leftX,
            labelY,
            INFO_LABEL_SCALE,
            INFO_LABEL_COLOR
        );

        GuiTextRenderer.drawScaledText(
            graphics,
            this.font,
            value,
            rightX - valueWidth,
            valueY,
            valueScale,
            valueColor
        );
    }

    private void drawContent(
        @NonNull GuiGraphics graphics,
        @NonNull RedstoneControlMode selectedMode,
        boolean redstoneBlocked,
        boolean hasSignal,
        int panelX,
        int panelY,
        int width,
        int height
    )
    {
        Component title = Component.translatable("gui.astralspace.redstone_control.title");

        graphics.enableScissor(
            panelX + BORDER_SIZE,
            panelY + BORDER_SIZE,
            panelX + width - BORDER_SIZE,
            panelY + height - BORDER_SIZE
        );

        this.drawTitle(graphics, this.font, title, panelX, panelY);
        this.drawDivider(graphics, panelX, panelY, width);
        this.drawModeButtons(graphics, panelX, panelY, selectedMode);

        this.drawInfoRow(
            graphics,
            panelX,
            panelY,
            STATUS_ROW_Y,
            Component.translatable("gui.astralspace.redstone_control.work"),
            Component.translatable(
                redstoneBlocked ? "gui.astralspace.redstone_control.work_blocked" :
                    "gui.astralspace.redstone_control.work_allowed"),
            redstoneBlocked ? 0xFFEE8A7A : 0xFF87CDAA
        );

        this.drawInfoRow(
            graphics,
            panelX,
            panelY,
            SIGNAL_ROW_Y,
            Component.translatable("gui.astralspace.redstone_control.signal"),
            Component.translatable(hasSignal ? "gui.astralspace.redstone_control.signal_on" :
                "gui.astralspace.redstone_control.signal_off"),
            hasSignal ? 0xFFFFB267 : 0xFFBAC4CF
        );

        graphics.disableScissor();
    }
}
