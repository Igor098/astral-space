package ru.greatdragonx.astralspace.common.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.codec.NeoForgeStreamCodecs;
import org.jspecify.annotations.NonNull;
import ru.greatdragonx.astralspace.AstralSpace;
import ru.greatdragonx.astralspace.common.machine.RedstoneControlMode;

public record SetMachineRedstoneModePayload(RedstoneControlMode mode) implements
    CustomPacketPayload
{

    public static final Type<SetMachineRedstoneModePayload> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath(
            AstralSpace.MOD_ID,
            "set_machine_redstone_mode"
        ));

    public static final StreamCodec<FriendlyByteBuf, SetMachineRedstoneModePayload> STREAM_CODEC =
        StreamCodec.composite(
            NeoForgeStreamCodecs.enumCodec(RedstoneControlMode.class),
            SetMachineRedstoneModePayload::mode,
            SetMachineRedstoneModePayload::new
        );

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
