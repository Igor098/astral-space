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
import ru.greatdragonx.astralspace.client.gui.animation.GuiTabAnimation;
import ru.greatdragonx.astralspace.client.gui.render.GuiTextRenderer;
import ru.greatdragonx.astralspace.client.gui.render.NineSliceRenderer;
import ru.greatdragonx.astralspace.common.machine.MachineSide;
import ru.greatdragonx.astralspace.common.machine.PortMode;
import ru.greatdragonx.astralspace.common.machine.SideConfiguration;
import ru.greatdragonx.astralspace.common.machine.SidePortConfig;

import java.util.List;
import java.util.Locale;
import java.util.Map;

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

    private static final int PORT_SIZE = 16;

    private static final Identifier BACKGROUND_TEXTURE = Identifier.fromNamespaceAndPath(
        AstralSpace.MOD_ID,
        "textures/gui/common/tabs/tab_background.png"
    );
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

    private int renderedWidth = TAB_WIDTH;
    private int renderedHeight = TAB_HEIGHT;

    private final GuiTabAnimation animation;
    private final Font font;
    private final Identifier iconTexture;

    private record PortPlacement(@NonNull MachineSide side, int offsetX, int offsetY) {
    }

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
        SideConfiguration configuration,
        Map<PortMode, Identifier> portTextures
    ) {
        float progress = this.animation.getProgress(partialTick);

        int width = this.getAnimatedWidth(progress);
        int height = this.getAnimatedHeight(progress);
        this.renderedWidth = width;
        this.renderedHeight = height;

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

        this.drawClippedContent(
            graphics,
            configuration,
            portTextures,
            anchorX,
            anchorY,
            width,
            height
        );
    }

    public void tick() {
        this.animation.tick();
    }

    public void toggle() {
        this.animation.toggle();
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

    public @Nullable MachineSide getClickedPort(
        @NonNull MouseButtonEvent event,
        int anchorX,
        int anchorY,
        SideConfiguration configuration
    ) {
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
    ) {
        MachineSide hoveredPort = this.getHoveredPort(
            mouseX,
            mouseY,
            anchorX,
            anchorY,
            configuration
        );

        if (hoveredPort == null) {
            return;
        }

        SidePortConfig portConfig = configuration.ports()
            .get(hoveredPort);
        PortMode mode = portConfig.mode();

        Component sideName =
            Component.translatable("gui.astralspace.side_config.side." + hoveredPort.name()
                .toLowerCase(Locale.ROOT));

        Component modeName =
            Component.translatable("gui.astralspace.side_config.mode." + mode.name()
                .toLowerCase(Locale.ROOT));

        graphics.setComponentTooltipForNextFrame(
            this.font,
            List.of(
                sideName,
                modeName
            ),
            mouseX,
            mouseY
        );
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

    private void drawMachineCenter(
        @NonNull GuiGraphics graphics,
        int anchorX,
        int anchorY
    ) {
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

    private void drawClippedContent(
        @NonNull GuiGraphics graphics,
        @NonNull SideConfiguration configuration,
        @NonNull Map<PortMode, Identifier> portTextures,
        int anchorX,
        int anchorY,
        int width,
        int height
    ) {
        int minX = anchorX + BORDER_SIZE;
        int minY = anchorY + TAB_HEIGHT + BORDER_SIZE;

        int maxX = anchorX + width - BORDER_SIZE;
        int maxY = anchorY + height - BORDER_SIZE;

        if (maxX <= minX || maxY <= minY) {
            return;
        }

        boolean isFrontAllowed = configuration.ports()
            .containsKey(MachineSide.FRONT);

        graphics.enableScissor(
            minX,
            minY,
            maxX,
            maxY
        );
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

        graphics.disableScissor();
    }

    private void drawPort(
        @NonNull GuiGraphics graphics,
        @NonNull PortPlacement placement,
        @NonNull Identifier texture,
        int anchorX,
        int anchorY
    ) {
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
    ) {
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

    private @Nullable MachineSide getHoveredPort(
        double mouseX,
        double mouseY,
        int anchorX,
        int anchorY,
        SideConfiguration configuration
    ) {
        int minX = anchorX + BORDER_SIZE;
        int minY = anchorY + TAB_HEIGHT + BORDER_SIZE;

        int maxX = anchorX + this.renderedWidth - BORDER_SIZE;
        int maxY = anchorY + this.renderedHeight - BORDER_SIZE;

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

            int x = anchorX + placement.offsetX();
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
