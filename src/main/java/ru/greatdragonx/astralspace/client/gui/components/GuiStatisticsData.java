package ru.greatdragonx.astralspace.client.gui.components;

import net.minecraft.network.chat.Component;

import java.util.List;

public record GuiStatisticsData(Component title, List<GuiStatisticEntry> entries) {
}
