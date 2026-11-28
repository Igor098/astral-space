package ru.greatdragonx.astralspace.common.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.codec.NeoForgeStreamCodecs;
import org.jspecify.annotations.NonNull;
import ru.greatdragonx.astralspace.AstralSpace;
import ru.greatdragonx.astralspace.common.machine.redstone.RedstoneControlMode;

/**
 * Сетевой пакет запроса на изменение режима
 * редстоун-управления механизмом.
 *
 * <p>Передаётся от клиента к серверу.
 * Запрос применяется к механизму,
 * обслуживаемому текущим открытым меню игрока.</p>
 *
 * <p>Пакет содержит запрошенный режим,
 * но не выполняет его изменение самостоятельно.</p>
 *
 * @param mode запрошенный режим редстоун-управления
 * @see SetMachineRedstoneModeHandler
 * @see RedstoneControlMode
 */
public record SetMachineRedstoneModePayload(RedstoneControlMode mode) implements
    CustomPacketPayload
{

    /**
     * Тип сетевого пакета изменения режима
     * редстоун-управления механизмом.
     */
    public static final Type<SetMachineRedstoneModePayload> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath(
            AstralSpace.MOD_ID,
            "set_machine_redstone_mode"
        ));

    /**
     * Сетевой кодек пакета изменения режима
     * редстоун-управления.
     *
     * <p>Выполняет кодирование и декодирование
     * значения {@link RedstoneControlMode}
     * с использованием кодека перечислений NeoForge.</p>
     */
    public static final StreamCodec<FriendlyByteBuf, SetMachineRedstoneModePayload> STREAM_CODEC =
        StreamCodec.composite(
            NeoForgeStreamCodecs.enumCodec(RedstoneControlMode.class),
            SetMachineRedstoneModePayload::mode,
            SetMachineRedstoneModePayload::new
        );

    /**
     * Возвращает зарегистрированный тип данного
     * сетевого пакета.
     *
     * @return тип сетевого пакета
     */
    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
