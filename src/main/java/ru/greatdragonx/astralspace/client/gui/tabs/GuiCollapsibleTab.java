package ru.greatdragonx.astralspace.client.gui.tabs;

public interface GuiCollapsibleTab {
    void open();

    void close();

    boolean isOpen();

    int getRenderedHeight();

    boolean isHeaderHovered(
        double mouseX,
        double mouseY,
        int anchorX,
        int anchorY
    );
}
