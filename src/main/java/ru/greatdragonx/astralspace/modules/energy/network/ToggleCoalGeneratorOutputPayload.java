package ru.greatdragonx.astralspace.modules.energy.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.codec.NeoForgeStreamCodecs;
import org.jspecify.annotations.NonNull;
import ru.greatdragonx.astralspace.AstralSpace;
import ru.greatdragonx.astralspace.common.machine.MachineSide;

public record ToggleCoalGeneratorOutputPayload(MachineSide side) implements CustomPacketPayload {
    public static final Type<ToggleCoalGeneratorOutputPayload> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath(
            AstralSpace.MOD_ID,
            "toggle_coal_generator_output"
        ));

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static final StreamCodec<FriendlyByteBuf, ToggleCoalGeneratorOutputPayload>
        STREAM_CODEC = StreamCodec.composite(
        NeoForgeStreamCodecs.enumCodec(MachineSide.class),
        ToggleCoalGeneratorOutputPayload::side,
        ToggleCoalGeneratorOutputPayload::new
    );
}
