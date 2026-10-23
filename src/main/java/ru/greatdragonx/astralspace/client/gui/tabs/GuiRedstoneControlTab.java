package ru.greatdragonx.astralspace.client.gui.tabs;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import ru.greatdragonx.astralspace.AstralSpace;
import ru.greatdragonx.astralspace.client.gui.animation.GuiTabAnimation;
import ru.greatdragonx.astralspace.client.gui.render.GuiTextRenderer;
import ru.greatdragonx.astralspace.client.gui.render.NineSliceRenderer;
import ru.greatdragonx.astralspace.common.machine.RedstoneControlMode;

import java.util.List;

public final class GuiRedstoneControlTab implements GuiCollapsibleTab {
    private static final float ANIMATION_STEP = 0.15f;

    private static final int TAB_WIDTH = 20;
    private static final int TAB_HEIGHT = 20;

    private static final int PANEL_WIDTH = 100;
    private static final int PANEL_HEIGHT = 96;

    private static final int TEXTURE_SIZE = 20;
    private static final int BORDER_SIZE = 5;

    private static final int ICON_WIDTH = 16;
    private static final int ICON_HEIGHT = 16;

    private static final int MODE_BUTTON_SIZE = 20;
    private static final int MODE_ICON_SIZE = 16;
    private static final int MODE_BUTTON_GAP = 4;
    private static final int MODE_BUTTONS_Y = 24;

    private static final Identifier BACKGROUND_TEXTURE = Identifier.fromNamespaceAndPath(
        AstralSpace.MOD_ID,
        "textures/gui/common/tabs/tab_background.png"
    );

    private static final Identifier MODE_BUTTONS_TEXTURE = Identifier.fromNamespaceAndPath(
        AstralSpace.MOD_ID,
        "textures/gui/common/redstone/redstone_mode_buttons.png"
    );

    private static final Identifier MODE_ICONS_TEXTURE = Identifier.fromNamespaceAndPath(
        AstralSpace.MOD_ID,
        "textures/gui/common/redstone/redstone_mode_icons.png"
    );

    private static final List<RedstoneControlMode> DISPLAY_MODES = List.of(
        RedstoneControlMode.IGNORE,
        RedstoneControlMode.WHEN_UNPOWERED,
        RedstoneControlMode.WHEN_POWERED
    );

    private final Font font;
    private final GuiTabAnimation animation;
    private final Identifier iconTexture;

    private int renderedWidth = TAB_WIDTH;
    private int renderedHeight = TAB_HEIGHT;


    public GuiRedstoneControlTab(
        Font font,
        Identifier iconTexture
    ) {
        this.font = font;
        this.iconTexture = iconTexture;
        this.animation = new GuiTabAnimation(ANIMATION_STEP);
    }

    public void render(
        @NonNull GuiGraphics graphics,
        @NonNull RedstoneControlMode selectedMode,
        boolean redstoneBlocked,
        int anchorX,
        int anchorY,
        float partialTick
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

        this.drawContent(
            graphics,
            selectedMode,
            redstoneBlocked,
            anchorX,
            anchorY,
            width,
            height
        );

        this.drawIcon(
            graphics,
            anchorX,
            anchorY
        );
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
        return mouseX >= anchorX && mouseX < anchorX + this.renderedWidth && mouseY >= anchorY && mouseY < anchorY + TAB_HEIGHT;
    }

    public void tick() {
        this.animation.tick();
    }

    public @Nullable RedstoneControlMode getHoveredMode(
        double mouseX,
        double mouseY,
        int anchorX,
        int anchorY
    ) {
        if (!this.isOpen()) {
            return null;
        }

        if (mouseX < anchorX || mouseX >= anchorX + this.renderedWidth || mouseY < anchorY + TAB_HEIGHT || mouseY >= anchorY + this.renderedHeight) {
            return null;
        }

        int totalWidth =
            DISPLAY_MODES.size() * MODE_BUTTON_SIZE + (DISPLAY_MODES.size() - 1) * MODE_BUTTON_GAP;
        int startX = anchorX + (PANEL_WIDTH - totalWidth) / 2;
        int buttonY = anchorY + MODE_BUTTONS_Y;

        for (int i = 0; i < DISPLAY_MODES.size(); i++) {
            RedstoneControlMode mode = DISPLAY_MODES.get(i);

            int buttonX = startX + i * (MODE_BUTTON_SIZE + MODE_BUTTON_GAP);
            boolean isButtonClicked =
                mouseX >= buttonX && mouseX < buttonX + MODE_BUTTON_SIZE && mouseY >= buttonY && mouseY < buttonY + MODE_BUTTON_SIZE;
            if (isButtonClicked) {
                return mode;
            }
        }

        return null;
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
        @NonNull GuiGraphics graphics,
        int anchorX,
        int anchorY
    ) {
        int x = anchorX + TAB_WIDTH + 2;
        int y = anchorY + (TAB_HEIGHT - this.font.lineHeight) / 2;

        Component title = Component.translatable("gui.astralspace.redstone_control.title");

        GuiTextRenderer.drawScaledText(
            graphics,
            this.font,
            title,
            x,
            y + 3,
            0.75f,
            0xFFE0E5EA
        );
    }

    private void drawModeButtons(
        GuiGraphics graphics,
        int anchorX,
        int anchorY,
        RedstoneControlMode selectedMode
    ) {
        int totalWidth =
            DISPLAY_MODES.size() * MODE_BUTTON_SIZE + (DISPLAY_MODES.size() - 1) * MODE_BUTTON_GAP;
        int startX = anchorX + (PANEL_WIDTH - totalWidth) / 2;
        int buttonY = anchorY + MODE_BUTTONS_Y;
        int iconOffset = (MODE_BUTTON_SIZE - MODE_ICON_SIZE) / 2;

        for (int i = 0; i < DISPLAY_MODES.size(); i++) {
            RedstoneControlMode mode = DISPLAY_MODES.get(i);

            int buttonX = startX + i * (MODE_BUTTON_SIZE + MODE_BUTTON_GAP);

            int buttonU = mode == selectedMode ? MODE_BUTTON_SIZE : 0;
            int iconU = i * MODE_ICON_SIZE;

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
                MODE_BUTTON_SIZE * 2,
                MODE_BUTTON_SIZE
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
                MODE_ICON_SIZE * DISPLAY_MODES.size(),
                MODE_ICON_SIZE
            );
        }
    }

    private void drawStatus(
        GuiGraphics graphics,
        int anchorX,
        int anchorY,
        boolean redstoneBlocked
    ) {
        int textX = anchorX + 6;
        int titleY = anchorY + 52;
        int statusValueY = anchorY + 60;

        Component title = Component.translatable("gui.astralspace.redstone_control.status");
        Component statusValue =
            redstoneBlocked ? Component.translatable("gui.astralspace.redstone_control.blocked") :
                Component.translatable("gui.astralspace.redstone_control.allowed");

        GuiTextRenderer.drawScaledText(
            graphics,
            this.font,
            title,
            textX,
            titleY,
            0.7f,
            0xFFA9B9CA
        );
        GuiTextRenderer.drawScaledText(
            graphics,
            this.font,
            statusValue,
            textX,
            statusValueY,
            0.6f,
            0xFFE0E5EA
        );
    }

    private void drawSignal(
        @NonNull GuiGraphics graphics,
        @NonNull RedstoneControlMode selectedMode,
        int anchorX,
        int anchorY
    ) {
        int textX = anchorX + 6;
        int titleY = anchorY + 72;
        int signalValueY = anchorY + 80;
        String translationKey = switch (selectedMode) {
            case IGNORE -> "gui.astralspace.redstone_control.signal_ignored";
            case WHEN_POWERED -> "gui.astralspace.redstone_control.signal_present";
            case WHEN_UNPOWERED -> "gui.astralspace.redstone_control.signal_absent";
        };

        Component title = Component.translatable("gui.astralspace.redstone_control.signal");
        Component signalValue = Component.translatable(translationKey);

        GuiTextRenderer.drawScaledText(
            graphics,
            this.font,
            title,
            textX,
            titleY,
            0.7f,
            0xFFA9B9CA
        );
        GuiTextRenderer.drawScaledText(
            graphics,
            this.font,
            signalValue,
            textX,
            signalValueY,
            0.6f,
            0xFFE0E5EA
        );
    }

    private void drawContent(
        @NonNull GuiGraphics graphics,
        @NonNull RedstoneControlMode selectedMode,
        boolean redstoneBlocked,
        int anchorX,
        int anchorY,
        int width,
        int height
    ) {
        graphics.enableScissor(
            anchorX,
            anchorY,
            anchorX + width,
            anchorY + height
        );

        this.drawTitle(
            graphics,
            anchorX,
            anchorY
        );

        this.drawModeButtons(
            graphics,
            anchorX,
            anchorY,
            selectedMode
        );

        this.drawStatus(
            graphics,
            anchorX,
            anchorY,
            redstoneBlocked
        );

        this.drawSignal(
            graphics,
            selectedMode,
            anchorX,
            anchorY
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

    private int getAnimatedHeight(float progress) {
        int delta = PANEL_HEIGHT - TAB_HEIGHT;
        float normalizedProgress = Math.clamp(
            progress,
            0.0f,
            1.0f
        );
        return Math.round(TAB_HEIGHT + delta * normalizedProgress);
    }
}
