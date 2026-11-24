package ru.greatdragonx.astralspace.common.machine.menu;

import net.minecraft.server.level.ServerPlayer;
import ru.greatdragonx.astralspace.common.machine.RedstoneControlMode;

public interface RedstoneConfigurableMenu {
    void setRedstoneControlMode(ServerPlayer player, RedstoneControlMode mode);
}
