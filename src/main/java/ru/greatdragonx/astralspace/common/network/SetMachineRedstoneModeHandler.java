package ru.greatdragonx.astralspace.common.network;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jspecify.annotations.NonNull;
import ru.greatdragonx.astralspace.common.machine.menu.RedstoneConfigurableMenu;

public final class SetMachineRedstoneModeHandler {
    private SetMachineRedstoneModeHandler() {
    }

    public static void handle(
        @NonNull SetMachineRedstoneModePayload payload,
        @NonNull IPayloadContext context
    )
    {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }

        if (!(player.containerMenu instanceof RedstoneConfigurableMenu menu)) {
            return;
        }

        if (!player.containerMenu.stillValid(player)) {
            return;
        }

        menu.setRedstoneControlMode(player, payload.mode());
    }
}
