package ru.greatdragonx.astralspace.common.network;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.jspecify.annotations.NonNull;
import ru.greatdragonx.astralspace.AstralSpace;

/**
 * Регистрация сетевых пакетов механизмов Astral Space.
 *
 * <p>Определяет поддерживаемые сетевые пакеты,
 * направления их передачи, кодеки и обработчики.</p>
 */
@EventBusSubscriber(modid = AstralSpace.MOD_ID)
public final class MachineNetworkRegistration {
    private MachineNetworkRegistration() {}

    /**
     * Регистрирует сетевые пакеты механизмов
     * для версии протокола {@code "1"}.
     *
     * <p>Регистрирует пакет изменения режима
     * редстоун-управления, передаваемый
     * от клиента к серверу.</p>
     *
     * @param event событие регистрации сетевых пакетов
     */
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
