package ru.greatdragonx.astralspace.common.network;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.jspecify.annotations.NonNull;
import ru.greatdragonx.astralspace.AstralSpace;

@EventBusSubscriber(modid = AstralSpace.MOD_ID)
public final class MachineNetworkRegistration {
    private MachineNetworkRegistration() {}

    @SubscribeEvent
    public static void register(@NonNull RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");

        registrar.playToServer(
            SetMachineRedstoneModePayload.TYPE,
            SetMachineRedstoneModePayload.STREAM_CODEC,
            SetMachineRedstoneModeHandler::handle
        );
    }
}
