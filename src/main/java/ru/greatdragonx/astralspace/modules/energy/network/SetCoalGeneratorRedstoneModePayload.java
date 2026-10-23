package ru.greatdragonx.astralspace.modules.energy.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.codec.NeoForgeStreamCodecs;
import org.jspecify.annotations.NonNull;
import ru.greatdragonx.astralspace.AstralSpace;
import ru.greatdragonx.astralspace.common.machine.RedstoneControlMode;

public record SetCoalGeneratorRedstoneModePayload(RedstoneControlMode mode) implements
    CustomPacketPayload {

    public static final Type<SetCoalGeneratorRedstoneModePayload> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath(
            AstralSpace.MOD_ID,
            "set_coal_generator_redstone_mode"
        ));

    public static final StreamCodec<FriendlyByteBuf, SetCoalGeneratorRedstoneModePayload>
        STREAM_CODEC = StreamCodec.composite(
        NeoForgeStreamCodecs.enumCodec(RedstoneControlMode.class),
        SetCoalGeneratorRedstoneModePayload::mode,
        SetCoalGeneratorRedstoneModePayload::new
    );

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
