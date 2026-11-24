package ru.greatdragonx.astralspace.modules.energy.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.codec.NeoForgeStreamCodecs;
import org.jspecify.annotations.NonNull;
import ru.greatdragonx.astralspace.AstralSpace;
import ru.greatdragonx.astralspace.common.machine.MachineSide;
import ru.greatdragonx.astralspace.common.machine.visual.VisualPortType;

public record SetLavaGeneratorPortModePayload(
    MachineSide side, VisualPortType portType, String modeId
) implements CustomPacketPayload
{

    public static final Type<SetLavaGeneratorPortModePayload> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath(
            AstralSpace.MOD_ID,
            "set_lava_generator_port_mode"
        ));

    public static final StreamCodec<FriendlyByteBuf, SetLavaGeneratorPortModePayload> STREAM_CODEC =
        StreamCodec.composite(
            NeoForgeStreamCodecs.enumCodec(MachineSide.class),
            SetLavaGeneratorPortModePayload::side,

            NeoForgeStreamCodecs.enumCodec(VisualPortType.class),
            SetLavaGeneratorPortModePayload::portType,

            ByteBufCodecs.stringUtf8(32),
            SetLavaGeneratorPortModePayload::modeId,

            SetLavaGeneratorPortModePayload::new
        );

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
