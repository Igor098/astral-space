package ru.greatdragonx.astralspace.modules.energy.network;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jspecify.annotations.NonNull;
import ru.greatdragonx.astralspace.common.energy.port.EnergyPortMode;
import ru.greatdragonx.astralspace.common.fluid.port.FluidPortMode;
import ru.greatdragonx.astralspace.modules.energy.menus.LavaGeneratorMenu;

public final class SetLavaGeneratorPortModeHandler {

    private SetLavaGeneratorPortModeHandler() {}

    public static void handle(
        @NonNull SetLavaGeneratorPortModePayload payload,
        @NonNull IPayloadContext context
    )
    {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }

        if (!(player.containerMenu instanceof LavaGeneratorMenu menu)) {
            return;
        }

        if (!menu.stillValid(player)) {
            return;
        }

        switch (payload.portType()) {
            case ENERGY -> handleEnergy(payload, menu);
            case FLUID -> handleFluid(payload, menu);
            case ITEM, GAS -> {}
        }
    }

    private static void handleEnergy(
        @NonNull SetLavaGeneratorPortModePayload payload,
        LavaGeneratorMenu menu
    )
    {
        EnergyPortMode mode = switch (payload.modeId()) {
            case "disabled" -> EnergyPortMode.DISABLED;
            case "output" -> EnergyPortMode.OUTPUT;
            default -> null;
        };

        if (mode == null) {
            return;
        }

        menu.withGenerator(entity -> entity.changeEnergyPortMode(payload.side(), mode));
    }

    private static void handleFluid(
        @NonNull SetLavaGeneratorPortModePayload payload,
        LavaGeneratorMenu menu
    )
    {
        FluidPortMode mode = switch (payload.modeId()) {
            case "disabled" -> FluidPortMode.DISABLED;
            case "input" -> FluidPortMode.INPUT;
            case "service" -> FluidPortMode.SERVICE;
            default -> null;
        };

        if (mode == null) {
            return;
        }

        menu.withGenerator(entity -> entity.changeFluidPortMode(payload.side(), mode));
    }
}
