package ru.greatdragonx.astralspace.client.gui.render;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;

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
    )
    {
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

    public static void drawScaled(
        GuiGraphics graphics,
        Identifier texture,
        int x,
        int y,
        int width,
        int height,
        int textureSize,
        int sourceBorder,
        int screenBorder
    )
    {
        int sourceTile = textureSize - 2 * sourceBorder;

        if (sourceBorder <= 0 ||
            screenBorder <= 0 ||
            sourceTile <= 0 ||
            width < screenBorder * 2 ||
            height < screenBorder * 2)
        {
            return;
        }

        int screenTile = Math.max(
            1,
            Math.round(sourceTile * (screenBorder / (float) sourceBorder))
        );

        int innerWidth = width - screenBorder * 2;
        int innerHeight = height - screenBorder * 2;

        for (int dy = 0; dy < innerHeight; dy += screenTile) {
            int h = Math.min(
                screenTile,
                innerHeight - dy
            );
            int srcH = Math.max(
                1,
                Math.round(h * sourceTile / (float) screenTile)
            );

            for (int dx = 0; dx < innerWidth; dx += screenTile) {
                int w = Math.min(
                    screenTile,
                    innerWidth - dx
                );
                int srcW = Math.max(
                    1,
                    Math.round(w * sourceTile / (float) screenTile)
                );

                blitScaled(
                    graphics,
                    texture,
                    x + screenBorder + dx,
                    y + screenBorder + dy,
                    sourceBorder,
                    sourceBorder,
                    w,
                    h,
                    srcW,
                    srcH,
                    textureSize
                );
            }
        }

        for (int dx = 0; dx < innerWidth; dx += screenTile) {
            int w = Math.min(
                screenTile,
                innerWidth - dx
            );
            int srcW = Math.max(
                1,
                Math.round(w * sourceTile / (float) screenTile)
            );

            blitScaled(
                graphics,
                texture,
                x + screenBorder + dx,
                y,
                sourceBorder,
                0,
                w,
                screenBorder,
                srcW,
                sourceBorder,
                textureSize
            );

            blitScaled(
                graphics,
                texture,
                x + screenBorder + dx,
                y + height - screenBorder,
                sourceBorder,
                textureSize - sourceBorder,
                w,
                screenBorder,
                srcW,
                sourceBorder,
                textureSize
            );
        }
        
        for (int dy = 0; dy < innerHeight; dy += screenTile) {
            int h = Math.min(
                screenTile,
                innerHeight - dy
            );
            int srcH = Math.max(
                1,
                Math.round(h * sourceTile / (float) screenTile)
            );

            blitScaled(
                graphics,
                texture,
                x,
                y + screenBorder + dy,
                0,
                sourceBorder,
                screenBorder,
                h,
                sourceBorder,
                srcH,
                textureSize
            );

            blitScaled(
                graphics,
                texture,
                x + width - screenBorder,
                y + screenBorder + dy,
                textureSize - sourceBorder,
                sourceBorder,
                screenBorder,
                h,
                sourceBorder,
                srcH,
                textureSize
            );
        }

        int last = textureSize - sourceBorder;
        int right = x + width - screenBorder;
        int bottom = y + height - screenBorder;

        blitScaled(
            graphics,
            texture,
            x,
            y,
            0,
            0,
            screenBorder,
            screenBorder,
            sourceBorder,
            sourceBorder,
            textureSize
        );

        blitScaled(
            graphics,
            texture,
            right,
            y,
            last,
            0,
            screenBorder,
            screenBorder,
            sourceBorder,
            sourceBorder,
            textureSize
        );

        blitScaled(
            graphics,
            texture,
            x,
            bottom,
            0,
            last,
            screenBorder,
            screenBorder,
            sourceBorder,
            sourceBorder,
            textureSize
        );

        blitScaled(
            graphics,
            texture,
            right,
            bottom,
            last,
            last,
            screenBorder,
            screenBorder,
            sourceBorder,
            sourceBorder,
            textureSize
        );
    }

    private static void blitScaled(
        @NonNull GuiGraphics graphics,
        @NonNull Identifier texture,
        int x,
        int y,
        int u,
        int v,
        int drawWidth,
        int drawHeight,
        int sourceWidth,
        int sourceHeight,
        int textureSize
    )
    {
        graphics.blit(
            RenderPipelines.GUI_TEXTURED,
            texture,
            x,
            y,
            (float) u,
            (float) v,
            drawWidth,
            drawHeight,
            sourceWidth,
            sourceHeight,
            textureSize,
            textureSize
        );
    }
}
