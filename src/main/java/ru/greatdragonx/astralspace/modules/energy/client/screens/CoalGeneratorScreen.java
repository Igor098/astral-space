package ru.greatdragonx.astralspace.modules.energy.client.screens;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import org.jspecify.annotations.NonNull;
import ru.greatdragonx.astralspace.AstralSpace;
import ru.greatdragonx.astralspace.modules.energy.blockentities.CoalGeneratorBlockEntity;
import ru.greatdragonx.astralspace.modules.energy.menus.CoalGeneratorMenu;

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
    private static final int ENERGY_PANEL_WIDTH = 100;
    private static final int ENERGY_PANEL_HEIGHT = 76;
    private static final int ENERGY_PANEL_Y = 20;

    private static final int ENERGY_TAB_WIDTH = 16;
    private static final int ENERGY_TAB_HEIGHT = 20;
    private static final int ENERGY_TAB_Y = 26;
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
    private boolean energyPanelOpen = false;

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

        if (!this.energyPanelOpen && this.isEnergyTabHovered(
            mouseX,
            mouseY
        )) {
            graphics.setTooltipForNextFrame(
                this.font,
                Component.translatable(
                    "gui.astralspace.coal_generator.panel.tooltip",
                    this.menu.getCurrentProductionRate()
                ),
                mouseX,
                mouseY
            );
        }
    }

    @Override
    public boolean mouseClicked(
        MouseButtonEvent event,
        boolean doubled
    ) {
        if (event.button() == 0 && this.isEnergyTabHovered(
            event.x(),
            event.y()
        )) {
            this.energyPanelOpen = !this.energyPanelOpen;
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
        if (this.energyPanelOpen) {
            this.drawEnergyPanel(graphics);
        }

        this.drawEnergyTab(
            graphics,
            mouseX,
            mouseY
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

    private int getEnergyTabX() {
        return this.leftPos - ENERGY_TAB_WIDTH;
    }

    private int getEnergyTabY() {
        return this.topPos + ENERGY_TAB_Y;
    }

    private boolean isEnergyTabHovered(
        double mouseX,
        double mouseY
    ) {
        int x = this.getEnergyTabX();
        int y = this.getEnergyTabY();

        return mouseX >= x && mouseX < x + ENERGY_TAB_WIDTH && mouseY >= y && mouseY < y + ENERGY_TAB_HEIGHT;
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

    private void drawEnergyStatistic(
        @NonNull GuiGraphics graphics,
        String translationKey,
        int value,
        int x,
        int y
    ) {
        float scale = 0.65f;

        Component label = Component.translatable(translationKey);
        Component amount = Component.literal(value + " FE/t");

        this.drawScaledText(
            graphics,
            label,
            x + 6,
            y,
            scale,
            0xFFD6DCE1
        );

        float amountWidth = this.font.width(amount) * scale;

        this.drawScaledText(
            graphics,
            amount,
            x + ENERGY_PANEL_WIDTH - 6 - amountWidth,
            y,
            scale,
            0xFF78D29F
        );
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
            case OFF -> CoalGeneratorGuiStatus.NO_FUEL;
        };
    }

    private void drawEnergyTab(
        @NonNull GuiGraphics graphics,
        int mouseX,
        int mouseY
    ) {
        int x = this.getEnergyTabX();
        int y = this.getEnergyTabY();

        boolean hovered = this.isEnergyTabHovered(
            mouseX,
            mouseY
        );

        graphics.fill(
            x,
            y,
            x + ENERGY_TAB_WIDTH,
            y + ENERGY_TAB_HEIGHT,
            0xFF252A30
        );

        graphics.renderOutline(
            x,
            y,
            ENERGY_TAB_WIDTH,
            ENERGY_TAB_HEIGHT,
            hovered || this.energyPanelOpen ? 0xFFE3AE57 : 0xFF626C75
        );

        int color = 0xFFFFC56A;

        graphics.fill(
            x + 9,
            y + 4,
            x + 12,
            y + 6,
            color
        );
        graphics.fill(
            x + 8,
            y + 6,
            x + 11,
            y + 8,
            color
        );
        graphics.fill(
            x + 7,
            y + 8,
            x + 10,
            y + 10,
            color
        );
        graphics.fill(
            x + 7,
            y + 10,
            x + 12,
            y + 11,
            color
        );
        graphics.fill(
            x + 9,
            y + 11,
            x + 11,
            y + 13,
            color
        );
        graphics.fill(
            x + 8,
            y + 13,
            x + 10,
            y + 16,
            color
        );
    }

    private void drawEnergyPanel(@NonNull GuiGraphics graphics) {
        int x = this.getEnergyTabX() - ENERGY_PANEL_WIDTH;
        int y = this.topPos + ENERGY_PANEL_Y;

        graphics.fill(
            x,
            y,
            x + ENERGY_PANEL_WIDTH,
            y + ENERGY_PANEL_HEIGHT,
            0xFF252A30
        );

        graphics.renderOutline(
            x,
            y,
            ENERGY_PANEL_WIDTH,
            ENERGY_PANEL_HEIGHT,
            0xFF626C75
        );

        this.drawScaledText(
            graphics,
            Component.translatable("gui.astralspace.coal_generator.panel.title"),
            x + 6,
            y + 6,
            0.75f,
            0xFFE4E8EC
        );

        graphics.fill(
            x + 5,
            y + 19,
            x + ENERGY_PANEL_WIDTH - 5,
            y + 20,
            0xFF626C75
        );

        this.drawEnergyStatistic(
            graphics,
            "gui.astralspace.coal_generator.panel.production",
            this.menu.getCurrentProductionRate(),
            x,
            y + 27
        );

        this.drawEnergyStatistic(
            graphics,
            "gui.astralspace.coal_generator.panel.maximum",
            CoalGeneratorBlockEntity.ENERGY_GENERATION_PER_TICK,
            x,
            y + 42
        );

        this.drawEnergyStatistic(
            graphics,
            "gui.astralspace.coal_generator.panel.transfer",
            this.menu.getCurrentTransferRate(),
            x,
            y + 57
        );
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
}
