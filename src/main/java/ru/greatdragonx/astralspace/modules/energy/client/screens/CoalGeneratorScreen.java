package ru.greatdragonx.astralspace.modules.energy.client.screens;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.jspecify.annotations.NonNull;
import ru.greatdragonx.astralspace.AstralSpace;
import ru.greatdragonx.astralspace.client.gui.components.GuiStatisticEntry;
import ru.greatdragonx.astralspace.client.gui.components.GuiStatisticsData;
import ru.greatdragonx.astralspace.client.gui.tabs.GuiSideConfigTab;
import ru.greatdragonx.astralspace.client.gui.tabs.GuiStatisticsTab;
import ru.greatdragonx.astralspace.common.machine.MachineSide;
import ru.greatdragonx.astralspace.common.machine.PortMode;
import ru.greatdragonx.astralspace.modules.energy.blockentities.CoalGeneratorBlockEntity;
import ru.greatdragonx.astralspace.modules.energy.menus.CoalGeneratorMenu;
import ru.greatdragonx.astralspace.modules.energy.network.ToggleCoalGeneratorOutputPayload;

import java.util.List;
import java.util.Map;

public class CoalGeneratorScreen extends AbstractContainerScreen<CoalGeneratorMenu> {
    private static final int MENU_WIDTH = 176;
    private static final int MENU_HEIGHT = 166;

    private static final int ENERGY_BAR_WIDTH = 96;

    private static final int FLAME_WIDTH = 14;
    private static final int FLAME_HEIGHT = 14;

    private static final int FLAME_X = 24;
    private static final int FLAME_Y = 55;

    private static final int STATUS_ICON_SIZE = 8;
    private static final int STATUS_ICON_GAP = 3;

    private static final int SIDE_CONFIG_TAB_Y = 20;

    private static final Identifier STATUS_ICONS = Identifier.fromNamespaceAndPath(
        AstralSpace.MOD_ID,
        "textures/gui/coal_generator/status_icons.png"
    );
    private static final Identifier BACKGROUND = Identifier.fromNamespaceAndPath(
        AstralSpace.MOD_ID,
        "textures/gui/coal_generator.png"
    );
    private static final Identifier FLAME_EMPTY = Identifier.fromNamespaceAndPath(
        AstralSpace.MOD_ID,
        "textures/gui/coal_generator/flame_empty.png"
    );
    private static final Identifier FLAME_FULL = Identifier.fromNamespaceAndPath(
        AstralSpace.MOD_ID,
        "textures/gui/coal_generator/flame_full.png"
    );
    private static final Identifier ENERGY_ICON = Identifier.fromNamespaceAndPath(
        AstralSpace.MOD_ID,
        "textures/gui/common/tabs/energy_statistics.png"
    );
    private static final Identifier SIDE_CONFIG_ICON = Identifier.fromNamespaceAndPath(
        AstralSpace.MOD_ID,
        "textures/gui/common/tabs/side_configuration.png"
    );

    private static final Map<PortMode, Identifier> ENERGY_PORT_TEXTURES = Map.of(
        PortMode.DISABLED,
        Identifier.fromNamespaceAndPath(
            AstralSpace.MOD_ID,
            "textures/gui/common/side_config/energy_port_disabled.png"
        ),
        PortMode.OUTPUT,
        Identifier.fromNamespaceAndPath(
            AstralSpace.MOD_ID,
            "textures/gui/common/side_config/energy_port_output.png"
        )
    );

    private final GuiStatisticsTab statisticsTab;
    private final GuiSideConfigTab sideConfigTab;

    public CoalGeneratorScreen(
        CoalGeneratorMenu menu,
        Inventory playerInventory,
        Component title
    ) {
        super(
            menu,
            playerInventory,
            title
        );

        this.imageWidth = MENU_WIDTH;
        this.imageHeight = MENU_HEIGHT;
        this.statisticsTab = new GuiStatisticsTab(
            this.font,
            ENERGY_ICON
        );
        this.sideConfigTab = new GuiSideConfigTab(
            this.font,
            SIDE_CONFIG_ICON
        );
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        this.statisticsTab.tick();
        this.sideConfigTab.tick();
    }

    @Override
    public void render(
        @NonNull GuiGraphics graphics,
        int mouseX,
        int mouseY,
        float partialTick
    ) {
        super.render(
            graphics,
            mouseX,
            mouseY,
            partialTick
        );

        this.renderTooltip(
            graphics,
            mouseX,
            mouseY
        );

        Component tooltip = Component.translatable(
            "gui.astralspace.coal_generator.panel.tooltip",
            this.menu.getCurrentProductionRate()
        );

        this.statisticsTab.renderTooltip(
            graphics,
            mouseX,
            mouseY,
            this.leftPos,
            this.topPos,
            tooltip
        );

        this.sideConfigTab.renderTooltip(
            graphics,
            mouseX,
            mouseY,
            this.getSideConfigTabX(),
            this.getSideConfigTabY(),
            this.menu.getEnergySideConfiguration()
        );
    }

    @Override
    public boolean mouseClicked(
        @NonNull MouseButtonEvent event,
        boolean doubled
    ) {
        boolean isStatisticsClicked = this.statisticsTab.mouseClicked(
            event,
            this.leftPos,
            this.topPos
        );
        if (isStatisticsClicked) {
            return true;
        }

        boolean isSideConfigClicked = this.sideConfigTab.mouseClicked(
            event,
            this.getSideConfigTabX(),
            this.getSideConfigTabY()
        );

        if (isSideConfigClicked) {
            return true;
        }

        MachineSide clickedPort = this.sideConfigTab.getClickedPort(
            event,
            this.getSideConfigTabX(),
            this.getSideConfigTabY(),
            this.menu.getEnergySideConfiguration()
        );

        if (clickedPort != null) {
            ClientPacketDistributor.sendToServer(new ToggleCoalGeneratorOutputPayload(clickedPort));
            return true;
        }

        return super.mouseClicked(
            event,
            doubled
        );
    }

    @Override
    protected void renderBg(
        @NonNull GuiGraphics graphics,
        float partialTick,
        int mouseX,
        int mouseY
    ) {
        graphics.blit(
            RenderPipelines.GUI_TEXTURED,
            BACKGROUND,
            this.leftPos,
            this.topPos,
            0,
            0,
            MENU_WIDTH,
            MENU_HEIGHT,
            MENU_WIDTH,
            MENU_HEIGHT
        );
        this.inventoryLabelY = 74;
        this.drawEnergyBar(graphics);
        this.drawBurningFlame(graphics);
        this.drawEnergyText(graphics);
        this.drawGeneratorStatus(graphics);
        this.drawFuelLabel(graphics);
        this.drawEnergyLabel(graphics);
        this.statisticsTab.render(
            graphics,
            this.leftPos,
            this.topPos,
            partialTick,
            this.createStatisticsData()
        );
        this.sideConfigTab.render(
            graphics,
            getSideConfigTabX(),
            getSideConfigTabY(),
            partialTick,
            Component.translatable("gui.astralspace.side_config.title"),
            this.menu.getEnergySideConfiguration(),
            ENERGY_PORT_TEXTURES
        );
    }

    @Override
    protected void renderLabels(
        @NonNull GuiGraphics graphics,
        int mouseX,
        int mouseY
    ) {
        float scale = 0.75f;

        this.drawScaledText(
            graphics,
            this.title,
            this.titleLabelX + 1,
            this.titleLabelY + 2,
            scale,
            0xFFE4E8EC
        );

        this.drawScaledText(
            graphics,
            this.playerInventoryTitle,
            this.inventoryLabelX,
            this.inventoryLabelY + 1,
            scale,
            0xFFD6DCE1
        );
    }

    private @NonNull GuiStatisticsData createStatisticsData() {
        Component title = Component.translatable("gui.astralspace.coal_generator.panel.title");
        List<GuiStatisticEntry> entries = List.of(
            new GuiStatisticEntry(
                Component.translatable("gui.astralspace.coal_generator.panel.production"),
                Component.literal(this.menu.getCurrentProductionRate() + " FE/t")
            ),
            new GuiStatisticEntry(
                Component.translatable("gui.astralspace.coal_generator.panel.maximum"),
                Component.literal(CoalGeneratorBlockEntity.ENERGY_GENERATION_PER_TICK + " FE/t")
            ),
            new GuiStatisticEntry(
                Component.translatable("gui.astralspace.coal_generator.panel.transfer"),
                Component.literal(this.menu.getCurrentTransferRate() + " FE/t")
            )
        );
        return new GuiStatisticsData(
            title,
            entries
        );
    }

    private void drawScaledText(
        @NonNull GuiGraphics graphics,
        Component text,
        float x,
        float y,
        float scale,
        int color
    ) {
        graphics.pose()
            .pushMatrix();

        graphics.pose()
            .translate(
                x,
                y
            );
        graphics.pose()
            .scale(
                scale,
                scale
            );

        graphics.drawString(
            this.font,
            text,
            0,
            0,
            color,
            false
        );

        graphics.pose()
            .popMatrix();
    }

    private void drawEnergyBar(@NonNull GuiGraphics graphics) {
        int barX = this.leftPos + 65;
        int barY = this.topPos + 36;

        graphics.fill(
            barX,
            barY,
            barX + ENERGY_BAR_WIDTH,
            barY + 10,
            0xFF33363B
        );

        int filledWidth = this.getEnergyBarWidth(ENERGY_BAR_WIDTH);
        graphics.fill(
            barX,
            barY,
            barX + filledWidth,
            barY + 10,
            0xFF48A878
        );
    }

    private void drawBurningFlame(@NonNull GuiGraphics graphics) {
        int x = this.leftPos + FLAME_X;
        int y = this.topPos + FLAME_Y;

        graphics.blit(
            RenderPipelines.GUI_TEXTURED,
            FLAME_EMPTY,
            x,
            y,
            0,
            0,
            FLAME_WIDTH,
            FLAME_HEIGHT,
            FLAME_WIDTH,
            FLAME_HEIGHT
        );

        int filledHeight = this.getBurnFillHeight(FLAME_HEIGHT);
        if (filledHeight <= 0) {
            return;
        }

        int offset = FLAME_HEIGHT - filledHeight;

        graphics.blit(
            RenderPipelines.GUI_TEXTURED,
            FLAME_FULL,
            x,
            y + offset,
            0,
            offset,
            FLAME_WIDTH,
            filledHeight,
            FLAME_WIDTH,
            FLAME_HEIGHT
        );
    }

    private void drawEnergyText(@NonNull GuiGraphics graphics) {
        int amount = this.menu.getEnergyStored();
        int capacity = this.menu.getEnergyCapacity();

        Component label = Component.literal(amount + " / " + capacity + " FE");

        float scale = 0.5f;

        float textWidth = this.font.width(label) * scale;
        float textX = this.leftPos + 65 + (ENERGY_BAR_WIDTH - textWidth) / 2;

        float textY = this.topPos + 49;

        this.drawScaledText(
            graphics,
            label,
            textX,
            textY,
            scale,
            0xFFE4E8EC
        );
    }

    private void drawFuelLabel(@NonNull GuiGraphics graphics) {
        Component label = Component.translatable("gui.astralspace.coal_generator.fuel");

        float scale = 0.75f;

        float textWidth = this.font.width(label) * scale;
        float textX = this.leftPos + 8 + (46 - textWidth) / 2;
        float textY = this.topPos + 25;

        this.drawScaledText(
            graphics,
            label,
            textX,
            textY,
            scale,
            0xFFE4E8EC
        );
    }

    private void drawEnergyLabel(@NonNull GuiGraphics graphics) {
        Component label = Component.translatable("gui.astralspace.coal_generator.energy");

        float scale = 0.75f;

        float textX = this.leftPos + 65;
        float textY = this.topPos + 25;

        this.drawScaledText(
            graphics,
            label,
            textX,
            textY,
            scale,
            0xFFE4E8EC
        );
    }

    private void drawGeneratorStatus(@NonNull GuiGraphics graphics) {
        CoalGeneratorGuiStatus status = this.getGeneratorStatus();
        Component label = status.getLabel();
        int color = status.getColor();

        float scale = 0.6f;

        float textWidth = this.font.width(label) * scale;
        float totalWidth = STATUS_ICON_SIZE + STATUS_ICON_GAP + textWidth;

        float startX = this.leftPos + 60 + (108 - totalWidth) / 2;
        float textY = this.topPos + 61;

        int iconX = (int) startX;
        int iconY = this.topPos + 59;

        graphics.blit(
            RenderPipelines.GUI_TEXTURED,
            STATUS_ICONS,
            iconX,
            iconY,
            status.getIconU(),
            status.getIconV(),
            8,
            8,
            8,
            8,
            32,
            8
        );

        float textX = startX + STATUS_ICON_SIZE + STATUS_ICON_GAP;

        this.drawScaledText(
            graphics,
            label,
            textX,
            textY,
            scale,
            color
        );
    }

    private CoalGeneratorGuiStatus getGeneratorStatus() {
        if (this.menu.getEnergyStored() >= this.menu.getEnergyCapacity()) {
            return CoalGeneratorGuiStatus.BUFFER_FULL;
        }

        return switch (this.menu.getFrontState()) {
            case RUNNING_NORMAL -> CoalGeneratorGuiStatus.RUNNING;
            case RUNNING_LOW_FUEL -> CoalGeneratorGuiStatus.LOW_FUEL;
            case OFF -> this.menu.getBurnTime() > 0 ? CoalGeneratorGuiStatus.RUNNING :
                CoalGeneratorGuiStatus.NO_FUEL;
        };
    }

    private int getEnergyBarWidth(int maxWidth) {
        int amount = this.menu.getEnergyStored();
        int capacity = this.menu.getEnergyCapacity();

        if (capacity <= 0) {
            return 0;
        }

        int width = amount * maxWidth / capacity;
        return Math.clamp(
            width,
            0,
            maxWidth
        );
    }

    private int getBurnFillHeight(int maxHeight) {
        int burnTime = this.menu.getBurnTime();
        int burnDuration = this.menu.getBurnDuration();

        if (burnDuration <= 0) {
            return 0;
        }

        int height = burnTime * maxHeight / burnDuration;
        return Math.clamp(
            height,
            0,
            maxHeight
        );
    }

    private int getSideConfigTabX() {
        return this.leftPos + this.imageWidth;
    }

    private int getSideConfigTabY() {
        return this.topPos + SIDE_CONFIG_TAB_Y;
    }
}
