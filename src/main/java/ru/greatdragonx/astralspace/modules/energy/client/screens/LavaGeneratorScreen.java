package ru.greatdragonx.astralspace.modules.energy.client.screens;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Unmodifiable;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import ru.greatdragonx.astralspace.AstralSpace;
import ru.greatdragonx.astralspace.client.gui.components.GuiPortEntry;
import ru.greatdragonx.astralspace.client.gui.components.GuiSideConfigData;
import ru.greatdragonx.astralspace.client.gui.components.GuiStatisticEntry;
import ru.greatdragonx.astralspace.client.gui.components.GuiStatisticsData;
import ru.greatdragonx.astralspace.client.gui.render.GuiTextRenderer;
import ru.greatdragonx.astralspace.client.gui.render.preview.MachinePreviewPicker;
import ru.greatdragonx.astralspace.client.gui.render.preview.MachinePreviewRenderState;
import ru.greatdragonx.astralspace.client.gui.tabs.*;
import ru.greatdragonx.astralspace.common.energy.port.EnergyPortMode;
import ru.greatdragonx.astralspace.common.fluid.port.FluidPortMode;
import ru.greatdragonx.astralspace.common.machine.MachineSide;
import ru.greatdragonx.astralspace.common.machine.visual.VisualPortType;
import ru.greatdragonx.astralspace.modules.energy.EnergyBlocks;
import ru.greatdragonx.astralspace.modules.energy.blockentities.LavaGeneratorBlockEntity;
import ru.greatdragonx.astralspace.modules.energy.blocks.LavaGeneratorBlock;
import ru.greatdragonx.astralspace.modules.energy.menus.LavaGeneratorMenu;

import java.util.*;

public class LavaGeneratorScreen extends AbstractContainerScreen<LavaGeneratorMenu> {
    private static final int MENU_WIDTH = 176;
    private static final int MENU_HEIGHT = 196;

    private static final int LAVA_MAX_HEIGHT = 44;
    private static final int LAVA_BOTTOM_Y = 71;
    private static final int LAVA_LEFT_X = 16;
    private static final int LAVA_RIGHT_X = 41;

    private static final int ENERGY_MAX_HEIGHT = 44;
    private static final int ENERGY_BOTTOM_Y = 71;
    private static final int ENERGY_LEFT_X = 135;
    private static final int ENERGY_RIGHT_X = 160;

    private static final int COMBUSTION_MAX_HEIGHT = 39;
    private static final int COMBUSTION_BOTTOM_Y = 71;
    private static final int COMBUSTION_LEFT_X = 55;
    private static final int COMBUSTION_RIGHT_X = 120;

    private static final int STATUS_ICON_SIZE = 6;
    private static final int STATUS_ICON_SOURCE_SIZE = 32;
    private static final int STATUS_ICON_GAP = 2;
    private static final float STATUS_PANEL_WIDTH = 62.0f;

    private static final int LAVA_FRAME_TICKS = 4;
    private static final int COMBUSTION_FRAME_COUNT = 16;
    private static final int COMBUSTION_FRAME_TICKS = 4;

    private static final Identifier STATUS_ICONS =
        Identifier.fromNamespaceAndPath(
            AstralSpace.MOD_ID,
            "textures/gui/lava_generator/status_icons.png"
        );

    private static final Identifier BACKGROUND = Identifier.fromNamespaceAndPath(
        AstralSpace.MOD_ID,
        "textures/gui/lava_generator/shell.png"
    );
    private static final Identifier WELLS = Identifier.fromNamespaceAndPath(
        AstralSpace.MOD_ID,
        "textures/gui/lava_generator/wells.png"
    );
    private static final Identifier GRILLE = Identifier.fromNamespaceAndPath(
        AstralSpace.MOD_ID,
        "textures/gui/lava_generator/grille.png"
    );
    private static final Identifier[] LAVA_FRAMES =
        {Identifier.fromNamespaceAndPath(
            AstralSpace.MOD_ID,
            "textures/gui/lava_generator/animated/lava_00.png"
        ), Identifier.fromNamespaceAndPath(
            AstralSpace.MOD_ID,
            "textures/gui/lava_generator/animated/lava_01.png"
        ), Identifier.fromNamespaceAndPath(
            AstralSpace.MOD_ID,
            "textures/gui/lava_generator/animated/lava_02.png"
        ), Identifier.fromNamespaceAndPath(
            AstralSpace.MOD_ID,
            "textures/gui/lava_generator/animated/lava_03.png"
        ), Identifier.fromNamespaceAndPath(
            AstralSpace.MOD_ID,
            "textures/gui/lava_generator/animated/lava_04.png"
        ), Identifier.fromNamespaceAndPath(
            AstralSpace.MOD_ID,
            "textures/gui/lava_generator/animated/lava_05.png"
        ), Identifier.fromNamespaceAndPath(
            AstralSpace.MOD_ID,
            "textures/gui/lava_generator/animated/lava_06.png"
        ), Identifier.fromNamespaceAndPath(
            AstralSpace.MOD_ID,
            "textures/gui/lava_generator/animated/lava_07.png"
        )};
    private static final Identifier[] COMBUSTION_FRAMES = createCombustionFrames();
    private static final Identifier ENERGY_FILL =
        Identifier.fromNamespaceAndPath(
            AstralSpace.MOD_ID,
            "textures/gui/lava_generator/energy_fill.png"
        );
    private static final Identifier ENERGY_TAB_ICON =
        Identifier.fromNamespaceAndPath(
            AstralSpace.MOD_ID,
            "textures/gui/common/tabs/energy_statistics.png"
        );
    private static final Identifier SIDE_CONFIG_ICON =
        Identifier.fromNamespaceAndPath(
            AstralSpace.MOD_ID,
            "textures/gui/common/tabs/side_configuration.png"
        );

    private final GuiMachineInfoTab machineInfoTab;
    private final GuiStatisticsTab statisticsTab;
    private final GuiSideConfigTab sideConfigTab;

    private final GuiTabGroup leftTabs;
    private final GuiTabGroup rightTabs;

    private final List<GuiInfoEntry> infoEntries;

    private final float previewRotationX = -70.0f;
    private final float previewRotationY = 0.0f;

    private double previewDragStartX;
    private double previewDragStartY;
    private final boolean previewWasDragged = false;
    private final boolean previewDragging = false;
    private final MachinePreviewPicker previewPicker = new MachinePreviewPicker();

    public LavaGeneratorScreen(
        @NonNull LavaGeneratorMenu menu,
        @NonNull Inventory playerInventory,
        @NonNull Component title
    )
    {
        super(menu, playerInventory, title);

        this.imageWidth = MENU_WIDTH;
        this.imageHeight = MENU_HEIGHT;

        this.infoEntries = this.createInfoEntries();

        this.machineInfoTab = new GuiMachineInfoTab(this.font);
        this.statisticsTab = new GuiStatisticsTab(this.font, ENERGY_TAB_ICON);
        this.leftTabs = new GuiTabGroup(List.of(this.machineInfoTab, this.statisticsTab));

        this.sideConfigTab = new GuiSideConfigTab(this.font, SIDE_CONFIG_ICON);
        this.rightTabs = new GuiTabGroup(List.of(this.sideConfigTab));
    }

    @Override
    public void render(
        @NonNull GuiGraphics graphics,
        int mouseX,
        int mouseY,
        float partialTick
    )
    {
        super.render(graphics, mouseX, mouseY, partialTick);

        this.renderTooltip(graphics, mouseX, mouseY);

        this.renderLavaTooltip(graphics, mouseX, mouseY);

        this.renderEnergyTooltip(graphics, mouseX, mouseY);

        int infoTabY = this.getLeftTabsY();

        this.machineInfoTab.renderTooltip(graphics, mouseX, mouseY, this.leftPos, infoTabY);

        Component tooltip = Component.translatable(
            "gui.astralspace.energy_statistics.rate.tooltip",
            this.menu.getCurrentProductionRate()
        );

        int statisticsTabY = this.leftTabs.getTabY(this.statisticsTab, this.getLeftTabsY());

        this.statisticsTab.renderTooltip(
            graphics,
            mouseX,
            mouseY,
            this.leftPos,
            statisticsTabY,
            tooltip
        );
    }

    @Override
    protected void renderBg(
        @NonNull GuiGraphics graphics,
        float partialTick,
        int mouseX,
        int mouseY
    )
    {
        graphics.blit(
            RenderPipelines.GUI_TEXTURED,
            WELLS,
            this.leftPos,
            this.topPos,
            0.0f,
            0.0f,
            MENU_WIDTH,
            MENU_HEIGHT,
            704,
            784,
            704,
            784
        );

        this.drawLavaFill(graphics);
        this.drawEnergyFill(graphics);
        this.drawCombustionFill(graphics);

        graphics.blit(
            RenderPipelines.GUI_TEXTURED,
            BACKGROUND,
            this.leftPos,
            this.topPos,
            0.0f,
            0.0f,
            MENU_WIDTH,
            MENU_HEIGHT,
            704,
            784,
            704,
            784
        );

        graphics.blit(
            RenderPipelines.GUI_TEXTURED,
            GRILLE,
            this.leftPos,
            this.topPos,
            0.0f,
            0.0f,
            MENU_WIDTH,
            MENU_HEIGHT,
            704,
            784,
            704,
            784
        );

        this.drawLavaAmount(graphics);
        this.drawEnergyAmount(graphics);
        this.drawGeneratorStatus(graphics);

        int infoTabY = this.getLeftTabsY();

        this.machineInfoTab.render(graphics, this.infoEntries, this.leftPos, infoTabY, partialTick);

        int energyTabY = this.leftTabs.getTabY(this.statisticsTab, infoTabY);

        this.statisticsTab.render(
            graphics,
            this.leftPos,
            energyTabY,
            partialTick,
            this.createStatisticsData()
        );

        BlockState blockState = this.getPreviewBlockState();
//        this.sideConfigTab.render(
//            getRightTabsX(),
//            getRightTabsY(),
//            partialTick,
//            graphics,
//            Component.translatable("gui.astralspace.side_config.title"),
//            blockState
//        );

//        MachinePreviewRenderState state = this.createPreviewState(graphics.peekScissorStack());
//        graphics.submitPictureInPictureRenderState(state);
    }

    @Override
    protected void renderLabels(
        @NonNull GuiGraphics graphics,
        int mouseX,
        int mouseY
    )
    {
        float scale = Math.min(0.65f, 112.0f / Math.max(1, this.font.width(this.title)));

        float textWidth = this.font.width(this.title) * scale;

        float x = (MENU_WIDTH - textWidth) / 2.0f;
        float y = 6.0f;

        GuiTextRenderer.drawScaledText(graphics, this.font, this.title, x, y, scale, 0xFFE4E8EC);
    }

    @Override
    public boolean mouseClicked(
        @NonNull MouseButtonEvent event,
        boolean doubled
    )
    {
        if (this.leftTabs.mouseClicked(event, this.leftPos, this.getLeftTabsY())) {
            return true;
        }

        if (this.rightTabs.mouseClicked(event, this.getRightTabsX(), this.getRightTabsY())) {
            return true;
        }

        int infoTabY = this.getLeftTabsY();

        if (this.machineInfoTab.mouseClicked(
            event.x(),
            event.y(),
            event.button(),
            this.leftPos,
            infoTabY,
            this.infoEntries
        ))
        {
            return true;
        }

        if (this.sideConfigTab.mouseClicked(event, this.getRightTabsX(), this.getRightTabsY())) {
            return true;
        }

//        int previewX = this.leftPos + this.imageWidth + 8;
//        int previewY = this.topPos + 32;
//        int previewSize = 64;
//
//        boolean isPreviewHovered = event.x() >= previewX &&
//            event.x() < previewX + previewSize &&
//            event.y() >= previewY &&
//            event.y() < previewY + previewSize;
//
//        if (event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT && isPreviewHovered) {
//            this.previewDragging = true;
//            this.previewDragStartX = event.x();
//            this.previewDragStartY = event.y();
//            this.previewWasDragged = false;
//            return true;
//        }
        return super.mouseClicked(event, doubled);
    }

    @Override
    public boolean mouseScrolled(
        double mouseX,
        double mouseY,
        double scrollX,
        double scrollY
    )
    {
        if (this.machineInfoTab.mouseScrolled(
            mouseX,
            mouseY,
            scrollY,
            this.leftPos,
            this.getLeftTabsY(),
            this.infoEntries
        ))
        {
            return true;
        }

        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean mouseDragged(
        @NonNull MouseButtonEvent event,
        double dragX,
        double dragY
    )
    {
//        if (event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT && this.previewDragging) {
//            double distance = Math.hypot(
//                event.x() - this.previewDragStartX,
//                event.y() - this.previewDragStartY
//            );
//
//            if (distance > 4.0) {
//                this.previewWasDragged = true;
//            }
//            if (this.previewWasDragged) {
//                this.previewRotationX = Math.clamp(
//                    this.previewRotationX + (float) (dragY * 0.8f),
//                    -110.0f,
//                    110.0f
//                );
//                this.previewRotationY += (float) (dragX * 0.8f);
//            }
//            return true;
//        }

        if (this.machineInfoTab.mouseDragged(event.y(), event.button(), this.infoEntries)) {
            return true;
        }

        if (this.sideConfigTab.mouseDragged(event, dragX, dragY)) {
            return true;
        }

        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(
        @NonNull MouseButtonEvent event
    )
    {
//        if (event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT && this.previewDragging) {
//            boolean wasDragged = this.previewWasDragged;
//
//            this.previewDragging = false;
//            this.previewWasDragged = false;
//
//            if (!wasDragged) {
//                MachinePreviewRenderState state = this.createPreviewState(null);
//                int guiScale = this.minecraft.getWindow()
//                    .getGuiScale();
//                Direction hitDirection = previewPicker.detectHitDirection(
//                    event.x(),
//                    event.y(),
//                    state,
//                    guiScale
//                );
//                AstralSpace.LOGGER.debug(
//                    "Machine preview clicked: {}",
//                    hitDirection
//                );
//            }
//
//            return true;
//        }

        if (this.machineInfoTab.mouseReleased(event.button())) {
            return true;
        }

        BlockState blockState = this.getPreviewBlockState();
        Direction facing = blockState.getValue(LavaGeneratorBlock.FACING);
        if (this.sideConfigTab.mouseReleased(
            event,
            this.minecraft.getWindow()
                .getGuiScale(),
            facing
        ))
        {
            return true;
        }

        return super.mouseReleased(event);
    }

    @Override
    protected void containerTick() {
        super.containerTick();

        this.machineInfoTab.tick();
        this.statisticsTab.tick();
        this.sideConfigTab.tick();
    }

    private void drawLavaFill(@NonNull GuiGraphics graphics) {
        int lavaStored = this.menu.getLavaStored();

        int height = (int) (
            (long) lavaStored * LAVA_MAX_HEIGHT / LavaGeneratorBlockEntity.LAVA_CAPACITY
        );

        if (height <= 0) {
            return;
        }

        height = Math.min(height, LAVA_MAX_HEIGHT);

        int x1 = this.leftPos + LAVA_LEFT_X;
        int x2 = this.leftPos + LAVA_RIGHT_X;

        int y1 = this.topPos + LAVA_BOTTOM_Y - height;
        int y2 = this.topPos + LAVA_BOTTOM_Y;

        graphics.enableScissor(x1, y1, x2, y2);

        graphics.blit(
            RenderPipelines.GUI_TEXTURED,
            this.getLavaFrameTexture(),
            this.leftPos,
            this.topPos,
            0.0f,
            0.0f,
            MENU_WIDTH,
            MENU_HEIGHT,
            704,
            784,
            704,
            784
        );

        graphics.disableScissor();
    }

    private void drawCombustionFill(@NonNull GuiGraphics graphics) {
        int burnTime = this.menu.getBurnTime();
        int burnDuration = this.menu.getBurnDuration();

        if (burnDuration <= 0 || burnTime <= 0) {
            return;
        }

        int height = (int) (
            (long) burnTime * COMBUSTION_MAX_HEIGHT / burnDuration
        );

        height = Math.clamp(height, 0, COMBUSTION_MAX_HEIGHT);

        if (height == 0) {
            return;
        }

        int x1 = this.leftPos + COMBUSTION_LEFT_X;
        int x2 = this.leftPos + COMBUSTION_RIGHT_X;

        int y1 = this.topPos + COMBUSTION_BOTTOM_Y - height;
        int y2 = this.topPos + COMBUSTION_BOTTOM_Y;

        graphics.enableScissor(x1, y1, x2, y2);

        graphics.blit(
            RenderPipelines.GUI_TEXTURED,
            this.getCombustionFrameTexture(),
            this.leftPos,
            this.topPos,
            0.0f,
            0.0f,
            MENU_WIDTH,
            MENU_HEIGHT,
            704,
            784,
            704,
            784
        );

        graphics.disableScissor();
    }

    private void drawEnergyFill(@NonNull GuiGraphics graphics) {
        long energyStored = this.menu.getEnergyStored();

        int height = (int) (
            energyStored * ENERGY_MAX_HEIGHT / LavaGeneratorBlockEntity.ENERGY_CAPACITY
        );

        if (height <= 0) {
            return;
        }

        height = Math.min(height, ENERGY_MAX_HEIGHT);

        int x1 = this.leftPos + ENERGY_LEFT_X;
        int x2 = this.leftPos + ENERGY_RIGHT_X;

        int y1 = this.topPos + ENERGY_BOTTOM_Y - height;
        int y2 = this.topPos + ENERGY_BOTTOM_Y;

        graphics.enableScissor(x1, y1, x2, y2);

        graphics.blit(
            RenderPipelines.GUI_TEXTURED,
            ENERGY_FILL,
            this.leftPos,
            this.topPos,
            0.0f,
            0.0f,
            MENU_WIDTH,
            MENU_HEIGHT,
            704,
            784,
            704,
            784
        );

        graphics.disableScissor();
    }

    private void drawLavaAmount(@NonNull GuiGraphics graphics) {
        int lavaStored = this.menu.getLavaStored();

        Component label = Component.literal(String.format(
            Locale.ROOT,
            "%.1fk / 8k",
            lavaStored / 1000.0
        ));

        float scale = Math.min(0.55f, 19.0f / this.font.width(label));

        float textWidth = this.font.width(label) * scale;

        float x = this.leftPos + 30 - textWidth / 2;
        float y = this.topPos + 80;

        GuiTextRenderer.drawScaledText(graphics, this.font, label, x, y, scale, 0xFFE4E8EC);
    }

    private void drawEnergyAmount(@NonNull GuiGraphics graphics) {
        long energyStored = this.menu.getEnergyStored();
        long energyCapacity = this.menu.getEnergyCapacity();

        Component label = Component.literal(String.format(
            Locale.ROOT,
            "%.0fk / %.0fk",
            energyStored / 1000.0,
            energyCapacity / 1000.0
        ));

        float scale = Math.min(0.65f, 24.0f / this.font.width(label));

        float textWidth = this.font.width(label) * scale;

        float x = this.leftPos + 147 - textWidth / 2;
        float y = this.topPos + 80;

        GuiTextRenderer.drawScaledText(graphics, this.font, label, x, y, scale, 0xFFE4E8EC);
    }

    private Identifier getLavaFrameTexture() {
        long gameTime = this.minecraft.level == null ? 0L : this.minecraft.level.getGameTime();

        int frameIndex = (int) Math.floorMod(
            gameTime / LAVA_FRAME_TICKS,
            (long) LAVA_FRAMES.length
        );

        return LAVA_FRAMES[frameIndex];
    }

    private static Identifier @NonNull [] createCombustionFrames() {
        Identifier[] frames = new Identifier[COMBUSTION_FRAME_COUNT];

        for (int index = 0; index < frames.length; index++) {
            frames[index] = Identifier.fromNamespaceAndPath(
                AstralSpace.MOD_ID, String.format(
                    java.util.Locale.ROOT,
                    "textures/gui/lava_generator/animated/combustion_%02d.png",
                    index
                )
            );
        }

        return frames;
    }

    private static @NonNull Component getEnergyModeName(
        @NonNull EnergyPortMode mode
    )
    {
        return Component.translatable("gui.astralspace.port_mode." + mode.name()
            .toLowerCase(Locale.ROOT));
    }

    private static @NonNull Component getFluidModeName(
        @NonNull FluidPortMode mode
    )
    {
        return Component.translatable("gui.astralspace.port_mode." + mode.name()
            .toLowerCase(Locale.ROOT));
    }

    private Identifier getCombustionFrameTexture() {
        long gameTime = this.minecraft.level == null ? 0L : this.minecraft.level.getGameTime();

        int frameIndex = (int) Math.floorMod(
            gameTime / COMBUSTION_FRAME_TICKS,
            (long) COMBUSTION_FRAMES.length
        );

        return COMBUSTION_FRAMES[frameIndex];
    }

    private void drawGeneratorStatus(@NonNull GuiGraphics graphics) {
        LavaGeneratorGuiStatus status = this.getGeneratorStatus();
        Component label = status.getLabel();

        float scale = Math.min(
            0.50f,
            (STATUS_PANEL_WIDTH - STATUS_ICON_SIZE - STATUS_ICON_GAP) /
                Math.max(1, this.font.width(label))
        );

        float textWidth = this.font.width(label) * scale;

        float totalWidth = STATUS_ICON_SIZE + STATUS_ICON_GAP + textWidth;

        float startX = this.leftPos + (MENU_WIDTH - totalWidth) / 2.0f;

        int iconX = Math.round(startX);
        int iconY = this.topPos + 79;

        graphics.blit(
            RenderPipelines.GUI_TEXTURED,
            STATUS_ICONS,
            iconX,
            iconY,
            status.getIconU(),
            status.getIconV(),
            STATUS_ICON_SIZE,
            STATUS_ICON_SIZE,
            STATUS_ICON_SOURCE_SIZE,
            STATUS_ICON_SOURCE_SIZE,
            160,
            32
        );

        GuiTextRenderer.drawScaledText(
            graphics,
            this.font,
            label,
            startX + STATUS_ICON_SIZE + STATUS_ICON_GAP,
            this.topPos + 80f,
            scale,
            status.getColor()
        );
    }

    private boolean isLavaTankHovered(
        int mouseX,
        int mouseY
    )
    {
        int x1 = this.leftPos + 16;
        int y1 = this.topPos + 27;
        int x2 = this.leftPos + 41;
        int y2 = this.topPos + 71;

        return mouseX >= x1 && mouseX < x2 && mouseY >= y1 && mouseY < y2;
    }

    private boolean isEnergyTankHovered(
        int mouseX,
        int mouseY
    )
    {
        int x1 = this.leftPos + ENERGY_LEFT_X;
        int y1 = this.topPos + 27;
        int x2 = this.leftPos + ENERGY_RIGHT_X;
        int y2 = this.topPos + ENERGY_BOTTOM_Y;

        return mouseX >= x1 && mouseX < x2 && mouseY >= y1 && mouseY < y2;
    }

    private LavaGeneratorGuiStatus getGeneratorStatus() {
        long freeSpace = this.menu.getEnergyCapacity() - this.menu.getEnergyStored();

        if (freeSpace < LavaGeneratorBlockEntity.ENERGY_GENERATION_PER_TICK) {
            return LavaGeneratorGuiStatus.BUFFER_FULL;
        }

        return switch (this.menu.getFrontState()) {
            case RUNNING -> LavaGeneratorGuiStatus.RUNNING;

            case LOW_LAVA -> LavaGeneratorGuiStatus.LOW_LAVA;

            case OFF -> {
                if (this.menu.getBurnTime() > 0) {
                    yield this.menu.getLavaStored() > 1_000 ? LavaGeneratorGuiStatus.RUNNING :
                        LavaGeneratorGuiStatus.LOW_LAVA;
                }

                yield LavaGeneratorGuiStatus.NO_LAVA;
            }
        };
    }

    private void renderLavaTooltip(
        @NonNull GuiGraphics graphics,
        int mouseX,
        int mouseY
    )
    {
        if (!this.isLavaTankHovered(mouseX, mouseY)) {
            return;
        }

        Component tooltip = Component.translatable(
            "gui.astralspace.lava_generator.lava.tooltip",
            this.menu.getLavaStored(),
            LavaGeneratorBlockEntity.LAVA_CAPACITY
        );

        graphics.setTooltipForNextFrame(this.font, tooltip, mouseX, mouseY);
    }

    private void renderEnergyTooltip(
        @NonNull GuiGraphics graphics,
        int mouseX,
        int mouseY
    )
    {
        if (!this.isEnergyTankHovered(mouseX, mouseY)) {
            return;
        }

        Component tooltip = Component.translatable(
            "gui.astralspace.lava_generator.energy.tooltip",
            this.menu.getEnergyStored(),
            this.menu.getEnergyCapacity()
        );

        graphics.setTooltipForNextFrame(this.font, tooltip, mouseX, mouseY);
    }


    private @NonNull @Unmodifiable List<GuiInfoEntry> createInfoEntries() {
        return List.of(
            new GuiInfoEntry(
                Component.translatable("gui.astralspace.lava_generator.info.name"),
                true
            ),
            new GuiInfoEntry(
                Component.translatable(
                    "gui.astralspace.lava_generator.info.description"), false
            ),
            new GuiInfoEntry(
                Component.translatable("gui.astralspace.lava_generator.info.fuel.title"),
                true
            ),
            new GuiInfoEntry(
                Component.translatable(
                    "gui.astralspace.lava_generator.info.fuel.description",
                    LavaGeneratorBlockEntity.LAVA_PER_CYCLE,
                    LavaGeneratorBlockEntity.BURN_DURATION_TICKS
                ), false
            ),
            new GuiInfoEntry(
                Component.translatable(
                    "gui.astralspace.lava_generator.info.specs.title"), true
            ),
            new GuiInfoEntry(
                Component.translatable(
                    "gui.astralspace.lava_generator.info.specs.generation",
                    LavaGeneratorBlockEntity.ENERGY_GENERATION_PER_TICK
                ), false
            ),
            new GuiInfoEntry(
                Component.translatable(
                    "gui.astralspace.lava_generator.info.specs.capacity",
                    LavaGeneratorBlockEntity.ENERGY_CAPACITY
                ), false
            ),
            new GuiInfoEntry(
                Component.translatable(
                    "gui.astralspace.lava_generator.info.specs.transfer",
                    LavaGeneratorBlockEntity.MAX_EXTRACT
                ), false
            ),
            new GuiInfoEntry(
                Component.translatable(
                    "gui.astralspace.lava_generator.info.specs.tank",
                    LavaGeneratorBlockEntity.LAVA_CAPACITY
                ), false
            ),
            new GuiInfoEntry(
                Component.translatable(
                    "gui.astralspace.lava_generator.info.features.title"), true
            ),
            new GuiInfoEntry(
                Component.translatable(
                    "gui.astralspace.lava_generator.info.features.pause",
                    LavaGeneratorBlockEntity.ENERGY_GENERATION_PER_TICK
                ), false
            ),
            new GuiInfoEntry(
                Component.translatable("gui.astralspace.lava_generator.info.features.cycle"),
                false
            ),
            new GuiInfoEntry(
                Component.translatable("gui.astralspace.lava_generator.info.features.input"),
                false
            ),
            new GuiInfoEntry(
                Component.translatable("gui.astralspace.lava_generator.info.features.outputs"),
                false
            )
        );
    }


    private @NonNull GuiStatisticsData createStatisticsData() {
        Component title = Component.translatable("gui.astralspace.energy_statistics.title");

        List<GuiStatisticEntry> entries = List.of(
            new GuiStatisticEntry(
                Component.translatable(
                    "gui.astralspace.energy_statistics.generation"),
                Component.literal(this.menu.getCurrentProductionRate() + " FE/t")
            ),
            new GuiStatisticEntry(
                Component.translatable("gui.astralspace.energy_statistics.max_generation"),
                Component.literal(LavaGeneratorBlockEntity.ENERGY_GENERATION_PER_TICK + " FE/t")
            ),
            new GuiStatisticEntry(
                Component.translatable("gui.astralspace.energy_statistics.transfer"),
                Component.literal(this.menu.getCurrentTransferRate() + " FE/t")
            )
        );

        return new GuiStatisticsData(title, entries);
    }

    private @NonNull MachinePreviewRenderState createPreviewState(
        @Nullable ScreenRectangle scissorArea
    )
    {
        int previewSize = 64;
        int previewX = this.leftPos + this.imageWidth + 8;
        int previewY = this.topPos + 32;

        BlockState blockState = EnergyBlocks.LAVA_GENERATOR.get()
            .defaultBlockState()
            .setValue(LavaGeneratorBlock.FACING, Direction.SOUTH);

        return new MachinePreviewRenderState(
            previewX,
            previewY,
            previewSize,
            previewSize,
            this.previewRotationX,
            this.previewRotationY,
            scissorArea,
            blockState,
            null
        );
    }

    private @NonNull BlockState getPreviewBlockState() {
        return EnergyBlocks.LAVA_GENERATOR.get()
            .defaultBlockState();
    }

    private @NonNull GuiSideConfigData buildSideConfigData() {
        Map<MachineSide, List<GuiPortEntry>> data = new HashMap<>();

        for (MachineSide side : MachineSide.values()) {
            List<GuiPortEntry> entries = new ArrayList<>();

            EnergyPortMode energyMode = this.menu.getEnergyPortMode(side);
            if (energyMode != null) {
                entries.add(new GuiPortEntry(VisualPortType.ENERGY, getEnergyModeName(energyMode)));
            }

            FluidPortMode fluidMode = this.menu.getFluidPortMode(side);
            if (fluidMode != null) {
                entries.add(new GuiPortEntry(VisualPortType.FLUID, getFluidModeName(fluidMode)));
            }

            data.put(side, entries);
        }

        return new GuiSideConfigData(data);
    }

    private int getRightTabsX() {
        return this.leftPos + this.imageWidth;
    }

    private int getRightTabsY() {
        return this.topPos + 2;
    }

    private int getLeftTabsY() {
        return this.topPos + 2;
    }
}
