package ru.greatdragonx.astralspace.common.network;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jspecify.annotations.NonNull;
import ru.greatdragonx.astralspace.common.machine.menu.RedstoneConfigurableMenu;

/**
 * Серверный обработчик запросов на изменение
 * режима редстоун-управления механизмом.
 *
 * <p>Передаёт запрос открытому меню механизма
 * после проверки допустимости его обработки.</p>
 *
 * @see SetMachineRedstoneModePayload
 * @see RedstoneConfigurableMenu
 */
public final class SetMachineRedstoneModeHandler {
    private SetMachineRedstoneModeHandler() {
    }

    /**
     * Обрабатывает запрос на изменение режима
     * редстоун-управления механизмом.
     *
     * <p>Запрос игнорируется, если:</p>
     *
     * <ul>
     *     <li>отправитель не является серверным игроком;</li>
     *     <li>открытое меню не реализует
     *         {@link RedstoneConfigurableMenu};</li>
     *     <li>открытое меню больше не является
     *         допустимым для игрока.</li>
     * </ul>
     *
     * <p>При успешном прохождении проверок запрос
     * передаётся соответствующему меню механизма.</p>
     *
     * @param payload пакет с запрошенным режимом
     * @param context контекст обработки сетевого пакета
     */
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
