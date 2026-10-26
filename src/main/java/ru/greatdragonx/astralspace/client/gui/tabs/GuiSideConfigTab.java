package ru.greatdragonx.astralspace.client.gui.tabs;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import ru.greatdragonx.astralspace.AstralSpace;
import ru.greatdragonx.astralspace.common.machine.MachineSide;
import ru.greatdragonx.astralspace.common.machine.PortMode;
import ru.greatdragonx.astralspace.common.machine.SideConfiguration;
import ru.greatdragonx.astralspace.common.machine.SidePortConfig;

import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class GuiSideConfigTab extends GuiAnimatedTab {
    private static final float ANIMATION_STEP = 0.15f;

    private static final int PANEL_WIDTH = 100;
    private static final int PANEL_HEIGHT = 96;

    private static final int PORT_SIZE = 16;

    private static final Identifier MACHINE_CENTER_TEXTURE = Identifier.fromNamespaceAndPath(
        AstralSpace.MOD_ID,
        "textures/gui/common/side_config/machine_center.png"
    );

    private static final List<PortPlacement> PORT_PLACEMENTS = List.of(
        new PortPlacement(
            MachineSide.FRONT,
            42,
            47
        ),
        new PortPlacement(
            MachineSide.BACK,
            22,
            27
        ),
        new PortPlacement(
            MachineSide.TOP,
            42,
            27
        ),
        new PortPlacement(
            MachineSide.LEFT,
            22,
            47
        ),
        new PortPlacement(
            MachineSide.RIGHT,
            62,
            47
        ),
        new PortPlacement(
            MachineSide.BOTTOM,
            42,
            67
        )
    );

    private final Font font;
    private final Identifier iconTexture;

    private record PortPlacement(@NonNull MachineSide side, int offsetX, int offsetY) {}

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
    }

    public void render(
        GuiGraphics graphics,
        int anchorX,
        int anchorY,
        float partialTick,
        Component title,
        SideConfiguration configuration,
        Map<PortMode, Identifier> portTextures
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
            graphics,
            configuration,
            portTextures,
            title,
            x,
            anchorY,
            width,
            height
        );
    }

    public @Nullable MachineSide getClickedPort(
        @NonNull MouseButtonEvent event,
        int anchorX,
        int anchorY,
        SideConfiguration configuration
    )
    {
        if (event.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return null;
        }

        return this.getHoveredPort(
            event.x(),
            event.y(),
            anchorX,
            anchorY,
            configuration
        );
    }

    public void renderTooltip(
        GuiGraphics graphics,
        int mouseX,
        int mouseY,
        int anchorX,
        int anchorY,
        SideConfiguration configuration
    )
    {
        MachineSide hoveredPort = this.getHoveredPort(
            mouseX,
            mouseY,
            anchorX,
            anchorY,
            configuration
        );

        boolean headerHovered = this.isFullyClosed() && this.isHeaderHovered(
            mouseX,
            mouseY,
            anchorX,
            anchorY
        );

        if (hoveredPort == null && !headerHovered) {
            return;
        }

        List<Component> tooltip;

        if (hoveredPort != null) {
            SidePortConfig portConfig = configuration.ports()
                .get(hoveredPort);
            PortMode mode = portConfig.mode();

            Component sideName = Component.translatable("gui.astralspace.side_config.side." +
                hoveredPort.name()
                    .toLowerCase(Locale.ROOT));

            Component modeName = Component.translatable("gui.astralspace.side_config.mode." +
                mode.name()
                    .toLowerCase(Locale.ROOT));

            tooltip = List.of(
                sideName,
                modeName
            );
        } else {
            tooltip = List.of(Component.translatable("gui.astralspace.side_config.tooltip"));
        }

        graphics.setComponentTooltipForNextFrame(
            this.font,
            tooltip,
            mouseX,
            mouseY
        );
    }

    private void drawMachineCenter(
        @NonNull GuiGraphics graphics,
        int anchorX,
        int anchorY
    )
    {
        graphics.blit(
            RenderPipelines.GUI_TEXTURED,
            MACHINE_CENTER_TEXTURE,
            anchorX + 42,
            anchorY + 47,
            0,
            0,
            PORT_SIZE,
            PORT_SIZE,
            PORT_SIZE,
            PORT_SIZE
        );
    }

    private void drawContent(
        @NonNull GuiGraphics graphics,
        @NonNull SideConfiguration configuration,
        @NonNull Map<PortMode, Identifier> portTextures,
        @NonNull Component title,
        int panelX,
        int panelY,
        int width,
        int height
    )
    {
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

        this.drawSidePorts(
            graphics,
            configuration,
            portTextures,
            panelX,
            panelY,
            width,
            height
        );

        graphics.disableScissor();
    }

    private void drawPort(
        @NonNull GuiGraphics graphics,
        @NonNull PortPlacement placement,
        @NonNull Identifier texture,
        int anchorX,
        int anchorY
    )
    {
        int x = anchorX + placement.offsetX();
        int y = anchorY + placement.offsetY();

        graphics.blit(
            RenderPipelines.GUI_TEXTURED,
            texture,
            x,
            y,
            0,
            0,
            PORT_SIZE,
            PORT_SIZE,
            PORT_SIZE,
            PORT_SIZE
        );
    }

    private void drawPorts(
        @NonNull GuiGraphics graphics,
        @NonNull SideConfiguration configuration,
        @NonNull Map<PortMode, Identifier> portTextures,
        int anchorX,
        int anchorY
    )
    {
        for (PortPlacement placement : PORT_PLACEMENTS) {
            MachineSide side = placement.side();

            SidePortConfig portConfig = configuration.ports()
                .get(side);

            if (portConfig == null) {
                continue;
            }

            PortMode mode = portConfig.mode();
            Identifier texture = portTextures.get(mode);

            if (texture == null) {
                continue;
            }

            this.drawPort(
                graphics,
                placement,
                texture,
                anchorX,
                anchorY
            );
        }
    }

    private void drawSidePorts(
        @NonNull GuiGraphics graphics,
        @NonNull SideConfiguration configuration,
        @NonNull Map<PortMode, Identifier> portTextures,
        int anchorX,
        int anchorY,
        int width,
        int height
    )
    {
        int minX = anchorX + BORDER_SIZE;
        int minY = anchorY + TAB_HEIGHT + BORDER_SIZE;

        int maxX = anchorX + width - BORDER_SIZE;
        int maxY = anchorY + height - BORDER_SIZE;

        if (maxX <= minX || maxY <= minY) {
            return;
        }

        boolean isFrontAllowed = configuration.ports()
            .containsKey(MachineSide.FRONT);

        if (!isFrontAllowed) {
            this.drawMachineCenter(
                graphics,
                anchorX,
                anchorY
            );
        }

        this.drawPorts(
            graphics,
            configuration,
            portTextures,
            anchorX,
            anchorY
        );
    }

    private @Nullable MachineSide getHoveredPort(
        double mouseX,
        double mouseY,
        int anchorX,
        int anchorY,
        SideConfiguration configuration
    )
    {
        int panelX = getPanelX(anchorX);
        int renderedWidth = this.getRenderedWidth();
        int renderedHeight = this.getRenderedHeight();

        int minX = panelX + BORDER_SIZE;
        int minY = anchorY + TAB_HEIGHT + BORDER_SIZE;

        int maxX = panelX + renderedWidth - BORDER_SIZE;
        int maxY = anchorY + renderedHeight - BORDER_SIZE;

        if (maxX <= minX || maxY <= minY) {
            return null;
        }

        boolean isXOutOfArea = mouseX < minX || mouseX >= maxX;
        boolean isYOutOfArea = mouseY < minY || mouseY >= maxY;

        if (isXOutOfArea || isYOutOfArea) {
            return null;
        }

        for (PortPlacement placement : PORT_PLACEMENTS) {
            MachineSide side = placement.side();

            SidePortConfig portConfig = configuration.ports()
                .get(side);

            if (portConfig == null) {
                continue;
            }

            int x = panelX + placement.offsetX();
            int y = anchorY + placement.offsetY();

            boolean horizontalHovered = mouseX >= x && mouseX < x + PORT_SIZE;
            boolean verticalHovered = mouseY >= y && mouseY < y + PORT_SIZE;

            if (horizontalHovered && verticalHovered) {
                return side;
            }
        }

        return null;
    }
}
