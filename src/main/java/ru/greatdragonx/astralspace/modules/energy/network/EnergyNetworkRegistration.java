package ru.greatdragonx.astralspace.modules.energy.network;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.jspecify.annotations.NonNull;
import ru.greatdragonx.astralspace.AstralSpace;

@EventBusSubscriber(modid = AstralSpace.MOD_ID)
public final class EnergyNetworkRegistration {

    private EnergyNetworkRegistration() {
    }

    @SubscribeEvent
    public static void register(@NonNull RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");

        registrar.playToServer(
            ToggleCoalGeneratorOutputPayload.TYPE,
            ToggleCoalGeneratorOutputPayload.STREAM_CODEC,
            ToggleCoalGeneratorOutputHandler::handle
        );

        registrar.playToServer(
            SetCoalGeneratorRedstoneModePayload.TYPE,
            SetCoalGeneratorRedstoneModePayload.STREAM_CODEC,
            SetCoalGeneratorRedstoneModeHandler::handle
        );
    }
}
