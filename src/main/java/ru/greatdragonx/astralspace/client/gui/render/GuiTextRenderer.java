package ru.greatdragonx.astralspace.client.gui.render;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.jspecify.annotations.NonNull;

public final class GuiTextRenderer {
    private GuiTextRenderer() {
    }

    public static void drawScaledText(
        @NonNull GuiGraphics graphics,
        @NonNull Font font,
        @NonNull Component text,
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
            font,
            text,
            0,
            0,
            color,
            false
        );

        graphics.pose()
            .popMatrix();
    }

    public static void drawScaledText(
        @NonNull GuiGraphics graphics,
        @NonNull Font font,
        @NonNull FormattedCharSequence text,
        int x,
        int y,
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
            font,
            text,
            0,
            0,
            color,
            false
        );

        graphics.pose()
            .popMatrix();
    }
}
