package ru.greatdragonx.astralspace.modules.energy.network;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jspecify.annotations.NonNull;
import ru.greatdragonx.astralspace.modules.energy.menus.CoalGeneratorMenu;

public final class ToggleCoalGeneratorOutputHandler {
    private ToggleCoalGeneratorOutputHandler() {
    }

    public static void handle(
        ToggleCoalGeneratorOutputPayload payload,
        @NonNull IPayloadContext context
    ) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }

        if (!(player.containerMenu instanceof CoalGeneratorMenu menu)) {
            return;
        }

        if (!menu.stillValid(player)) {
            return;
        }

        menu.withGenerator(entity -> entity.toggleEnergyOutput(payload.side()));
    }
}
