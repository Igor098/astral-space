package ru.greatdragonx.astralspace.modules.energy.client.screens;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import org.jspecify.annotations.NonNull;
import ru.greatdragonx.astralspace.AstralSpace;
import ru.greatdragonx.astralspace.client.gui.render.GuiTextRenderer;
import ru.greatdragonx.astralspace.modules.energy.blockentities.LavaGeneratorBlockEntity;
import ru.greatdragonx.astralspace.modules.energy.menus.LavaGeneratorMenu;

import java.util.Locale;

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
    private static final Identifier LAVA_FILL = Identifier.fromNamespaceAndPath(
        AstralSpace.MOD_ID,
        "textures/gui/lava_generator/lava_fill.png"
    );
    private static final Identifier COMBUSTION_FILL = Identifier.fromNamespaceAndPath(
        AstralSpace.MOD_ID,
        "textures/gui/lava_generator/combustion_fill.png"
    );
    private static final Identifier ENERGY_FILL = Identifier.fromNamespaceAndPath(
        AstralSpace.MOD_ID,
        "textures/gui/lava_generator/energy_fill.png"
    );

    public LavaGeneratorScreen(
        @NonNull LavaGeneratorMenu menu,
        @NonNull Inventory playerInventory,
        @NonNull Component title
    )
    {
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
    )
    {
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

        this.renderLavaTooltip(
            graphics,
            mouseX,
            mouseY
        );

        this.renderEnergyTooltip(
            graphics,
            mouseX,
            mouseY
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
    }

    @Override
    protected void renderLabels(
        @NonNull GuiGraphics graphics,
        int mouseX,
        int mouseY
    )
    {
        float scale = Math.min(
            0.65f,
            112.0f / Math.max(
                1,
                this.font.width(this.title)
            )
        );

        float textWidth = this.font.width(this.title) * scale;

        float x = (MENU_WIDTH - textWidth) / 2.0f;
        float y = 6.0f;

        GuiTextRenderer.drawScaledText(
            graphics,
            this.font,
            this.title,
            x,
            y,
            scale,
            0xFFE4E8EC
        );
    }

    private void drawLavaFill(@NonNull GuiGraphics graphics) {
        int lavaStored = this.menu.getLavaStored();

        int height = (int) (
            (long) lavaStored * LAVA_MAX_HEIGHT / LavaGeneratorBlockEntity.LAVA_CAPACITY
        );

        if (height <= 0) {
            return;
        }

        height = Math.min(
            height,
            LAVA_MAX_HEIGHT
        );

        int x1 = this.leftPos + LAVA_LEFT_X;
        int x2 = this.leftPos + LAVA_RIGHT_X;

        int y1 = this.topPos + LAVA_BOTTOM_Y - height;
        int y2 = this.topPos + LAVA_BOTTOM_Y;

        graphics.enableScissor(
            x1,
            y1,
            x2,
            y2
        );

        graphics.blit(
            RenderPipelines.GUI_TEXTURED,
            LAVA_FILL,
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

        height = Math.clamp(
            height,
            0,
            COMBUSTION_MAX_HEIGHT
        );

        if (height == 0) {
            return;
        }

        int x1 = this.leftPos + COMBUSTION_LEFT_X;
        int x2 = this.leftPos + COMBUSTION_RIGHT_X;

        int y1 = this.topPos + COMBUSTION_BOTTOM_Y - height;
        int y2 = this.topPos + COMBUSTION_BOTTOM_Y;

        graphics.enableScissor(
            x1,
            y1,
            x2,
            y2
        );

        graphics.blit(
            RenderPipelines.GUI_TEXTURED,
            COMBUSTION_FILL,
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

        height = Math.min(
            height,
            ENERGY_MAX_HEIGHT
        );

        int x1 = this.leftPos + ENERGY_LEFT_X;
        int x2 = this.leftPos + ENERGY_RIGHT_X;

        int y1 = this.topPos + ENERGY_BOTTOM_Y - height;
        int y2 = this.topPos + ENERGY_BOTTOM_Y;

        graphics.enableScissor(
            x1,
            y1,
            x2,
            y2
        );

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

        float scale = Math.min(
            0.55f,
            19.0f / this.font.width(label)
        );

        float textWidth = this.font.width(label) * scale;

        float x = this.leftPos + 30 - textWidth / 2;
        float y = this.topPos + 80;

        GuiTextRenderer.drawScaledText(
            graphics,
            this.font,
            label,
            x,
            y,
            scale,
            0xFFE4E8EC
        );
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

        float scale = Math.min(
            0.65f,
            24.0f / this.font.width(label)
        );

        float textWidth = this.font.width(label) * scale;

        float x = this.leftPos + 147 - textWidth / 2;
        float y = this.topPos + 80;

        GuiTextRenderer.drawScaledText(
            graphics,
            this.font,
            label,
            x,
            y,
            scale,
            0xFFE4E8EC
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

    private void renderLavaTooltip(
        @NonNull GuiGraphics graphics,
        int mouseX,
        int mouseY
    )
    {
        if (!this.isLavaTankHovered(
            mouseX,
            mouseY
        ))
        {
            return;
        }

        Component tooltip = Component.translatable(
            "gui.astralspace.lava_generator.lava.tooltip",
            this.menu.getLavaStored(),
            LavaGeneratorBlockEntity.LAVA_CAPACITY
        );

        graphics.setTooltipForNextFrame(
            this.font,
            tooltip,
            mouseX,
            mouseY
        );
    }

    private void renderEnergyTooltip(
        @NonNull GuiGraphics graphics,
        int mouseX,
        int mouseY
    )
    {
        if (!this.isEnergyTankHovered(
            mouseX,
            mouseY
        ))
        {
            return;
        }

        Component tooltip = Component.translatable(
            "gui.astralspace.lava_generator.energy.tooltip",
            this.menu.getEnergyStored(),
            this.menu.getEnergyCapacity()
        );

        graphics.setTooltipForNextFrame(
            this.font,
            tooltip,
            mouseX,
            mouseY
        );
    }
}
