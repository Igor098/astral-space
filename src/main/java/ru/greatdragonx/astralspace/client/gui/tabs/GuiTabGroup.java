package ru.greatdragonx.astralspace.client.gui.tabs;

import net.minecraft.client.input.MouseButtonEvent;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.List;

public final class GuiTabGroup {
    private final List<GuiCollapsibleTab> tabs;

    public GuiTabGroup(List<GuiCollapsibleTab> tabs) {
        this.tabs = List.copyOf(tabs);
    }

    public void select(GuiCollapsibleTab selectedTab) {
        if (this.tabs.stream()
            .noneMatch(tab -> tab == selectedTab))
        {
            throw new IllegalArgumentException("Selected tab does not belong to this group");
        }
        
        for (GuiCollapsibleTab tab : this.tabs) {
            if (tab == selectedTab) {
                continue;
            }

            tab.close();
        }

        boolean shouldOpen = !selectedTab.isOpen();

        if (shouldOpen) {
            selectedTab.open();
        } else {
            selectedTab.close();
        }
    }

    public int getTabY(
        GuiCollapsibleTab targetTab,
        int startY
    )
    {
        int y = startY;

        for (GuiCollapsibleTab tab : this.tabs) {
            if (targetTab == tab) {
                return y;
            }

            y += tab.getRenderedHeight();
        }

        throw new IllegalArgumentException("Target tab does not belong to this group");
    }

    public @Nullable GuiCollapsibleTab getHoveredTab(
        double mouseX,
        double mouseY,
        int anchorX,
        int startY
    )
    {
        int y = startY;

        for (GuiCollapsibleTab tab : this.tabs) {
            if (tab.isHeaderHovered(
                mouseX,
                mouseY,
                anchorX,
                y
            ))
            {
                return tab;
            }

            y += tab.getRenderedHeight();
        }

        return null;
    }

    public boolean mouseClicked(
        @NonNull MouseButtonEvent event,
        int anchorX,
        int startY
    )
    {
        if (event.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return false;
        }

        GuiCollapsibleTab tab = this.getHoveredTab(
            event.x(),
            event.y(),
            anchorX,
            startY
        );
        if (tab == null) {
            return false;
        }

        this.select(tab);
        return true;
    }
}
