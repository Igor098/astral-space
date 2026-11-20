package ru.greatdragonx.astralspace.client.gui.tabs;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.NonNull;
import ru.greatdragonx.astralspace.AstralSpace;
import ru.greatdragonx.astralspace.client.gui.components.GuiMachinePreview;
import ru.greatdragonx.astralspace.common.machine.MachineSide;
import ru.greatdragonx.astralspace.common.machine.MachineSideDirections;

public final class GuiSideConfigTab extends GuiAnimatedTab {
    private static final float ANIMATION_STEP = 0.15f;

    private static final int PANEL_WIDTH = 140;
    private static final int PANEL_HEIGHT = 140;

    private static final int PREVIEW_SIZE = 64;

    private final GuiMachinePreview machinePreview;
    private final Font font;
    private final Identifier iconTexture;

    public GuiSideConfigTab(
        Font font,
        Identifier iconTexture
    )
    {
        super(
            false,
            ANIMATION_STEP,
            PANEL_WIDTH,
            PANEL_HEIGHT
        );
        this.font = font;
        this.iconTexture = iconTexture;
        this.machinePreview = new GuiMachinePreview();
    }

    @Override
    public void close() {
        super.close();
        this.machinePreview.cancelInteraction();
    }

    public void render(
        int anchorX,
        int anchorY,
        float partialTick,
        @NonNull GuiGraphics graphics,
        @NonNull Component title,
        @NonNull BlockState blockState
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

        this.drawTabIcon(
            graphics,
            this.iconTexture,
            x,
            anchorY,
            progress
        );

        this.drawContent(
            x,
            anchorY,
            width,
            height,
            graphics,
            title,
            blockState
        );
    }

    public boolean mouseClicked(
        @NonNull MouseButtonEvent event,
        int anchorX,
        int anchorY
    )
    {
        boolean fullyExpanded = this.isOpen() &&
            this.getRenderedWidth() == PANEL_WIDTH &&
            this.getRenderedHeight() == PANEL_HEIGHT;

        if (!fullyExpanded) {
            return false;
        }

        int panelX = this.getPanelX(anchorX);

        int previewX = panelX + (PANEL_WIDTH - PREVIEW_SIZE) / 2;
        int previewY = anchorY + TAB_HEIGHT + 8;

        return this.machinePreview.mouseClicked(
            event,
            previewX,
            previewY,
            PREVIEW_SIZE
        );
    }

    public boolean mouseDragged(
        @NonNull MouseButtonEvent event,
        double dragX,
        double dragY
    )
    {
        boolean fullyExpanded = this.isOpen() &&
            this.getRenderedWidth() == PANEL_WIDTH &&
            this.getRenderedHeight() == PANEL_HEIGHT;

        if (!fullyExpanded) {
            return false;
        }

        return this.machinePreview.mouseDragged(
            event,
            dragX,
            dragY
        );
    }

    public boolean mouseReleased(
        @NonNull MouseButtonEvent event,
        int guiScale,
        @NonNull Direction facing
    )
    {
        boolean fullyExpanded = this.isOpen() &&
            this.getRenderedWidth() == PANEL_WIDTH &&
            this.getRenderedHeight() == PANEL_HEIGHT;

        if (!fullyExpanded) {
            this.machinePreview.cancelInteraction();
            return false;
        }

        GuiMachinePreview.PreviewReleaseResult result = this.machinePreview.mouseReleased(
            event,
            guiScale
        );

        if (result.hitDirection() != null) {
            MachineSide side = MachineSideDirections.fromWorld(
                result.hitDirection(),
                facing
            );
            AstralSpace.LOGGER.debug(
                "Machine side clicked: {}",
                side
            );
        }

        return result.consumed();
    }

    private void drawContent(
        int panelX,
        int panelY,
        int width,
        int height,
        @NonNull GuiGraphics graphics,
        @NonNull Component title,
        @NonNull BlockState blockState
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

        boolean fullyExpanded = this.isOpen() && width == PANEL_WIDTH && height == PANEL_HEIGHT;
        if (fullyExpanded) {
            this.machinePreview.render(
                previewX,
                previewY,
                PREVIEW_SIZE,
                graphics,
                blockState
            );
        }

        graphics.disableScissor();
    }
}
