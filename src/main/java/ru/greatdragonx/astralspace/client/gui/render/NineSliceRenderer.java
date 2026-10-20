package ru.greatdragonx.astralspace.client.gui.render;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

public final class NineSliceRenderer {
    private NineSliceRenderer() {
    }

    public static void draw(
        GuiGraphics graphics,
        Identifier texture,
        int x,
        int y,
        int width,
        int height,
        int textureSize,
        int borderSize
    ) {
        int innerWidth = width - 2 * borderSize;
        int innerHeight = height - 2 * borderSize;

        int tileWidth = textureSize - 2 * borderSize;
        int tileHeight = textureSize - 2 * borderSize;

        if (innerWidth <= 0 || innerHeight <= 0 || tileWidth <= 0 || tileHeight <= 0) {
            return;
        }

        for (int offsetX = 0; offsetX < innerWidth; offsetX += tileWidth) {
            for (int offsetY = 0; offsetY < innerHeight; offsetY += tileHeight) {
                int fragmentWidth = Math.min(
                    tileWidth,
                    innerWidth - offsetX
                );

                int fragmentHeight = Math.min(
                    tileHeight,
                    innerHeight - offsetY
                );

                graphics.blit(
                    RenderPipelines.GUI_TEXTURED,
                    texture,
                    x + borderSize + offsetX,
                    y + borderSize + offsetY,
                    borderSize,
                    borderSize,
                    fragmentWidth,
                    fragmentHeight,
                    textureSize,
                    textureSize
                );
            }
        }

        for (int offset = 0; offset < innerWidth; offset += tileWidth) {
            int fragmentWidth = Math.min(
                tileWidth,
                innerWidth - offset
            );

            graphics.blit(
                RenderPipelines.GUI_TEXTURED,
                texture,
                x + borderSize + offset,
                y,
                borderSize,
                0,
                fragmentWidth,
                borderSize,
                textureSize,
                textureSize
            );
        }

        for (int offset = 0; offset < innerWidth; offset += tileWidth) {
            int fragmentWidth = Math.min(
                tileWidth,
                innerWidth - offset
            );

            graphics.blit(
                RenderPipelines.GUI_TEXTURED,
                texture,
                x + borderSize + offset,
                y + height - borderSize,
                borderSize,
                textureSize - borderSize,
                fragmentWidth,
                borderSize,
                textureSize,
                textureSize
            );
        }

        for (int offset = 0; offset < innerHeight; offset += tileHeight) {
            int fragmentHeight = Math.min(
                tileHeight,
                innerHeight - offset
            );
            graphics.blit(
                RenderPipelines.GUI_TEXTURED,
                texture,
                x,
                y + borderSize + offset,
                0,
                borderSize,
                borderSize,
                fragmentHeight,
                textureSize,
                textureSize
            );
        }

        for (int offset = 0; offset < innerHeight; offset += tileHeight) {
            int fragmentHeight = Math.min(
                tileHeight,
                innerHeight - offset
            );
            graphics.blit(
                RenderPipelines.GUI_TEXTURED,
                texture,
                x + width - borderSize,
                y + borderSize + offset,
                textureSize - borderSize,
                borderSize,
                borderSize,
                fragmentHeight,
                textureSize,
                textureSize
            );
        }

        graphics.blit(
            RenderPipelines.GUI_TEXTURED,
            texture,
            x,
            y,
            0,
            0,
            borderSize,
            borderSize,
            textureSize,
            textureSize
        );

        graphics.blit(
            RenderPipelines.GUI_TEXTURED,
            texture,
            x + width - borderSize,
            y,
            textureSize - borderSize,
            0,
            borderSize,
            borderSize,
            textureSize,
            textureSize
        );

        graphics.blit(
            RenderPipelines.GUI_TEXTURED,
            texture,
            x,
            y + height - borderSize,
            0,
            textureSize - borderSize,
            borderSize,
            borderSize,
            textureSize,
            textureSize
        );

        graphics.blit(
            RenderPipelines.GUI_TEXTURED,
            texture,
            x + width - borderSize,
            y + height - borderSize,
            textureSize - borderSize,
            textureSize - borderSize,
            borderSize,
            borderSize,
            textureSize,
            textureSize
        );
    }
}
