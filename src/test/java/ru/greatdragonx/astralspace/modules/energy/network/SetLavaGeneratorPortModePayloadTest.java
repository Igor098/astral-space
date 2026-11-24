package ru.greatdragonx.astralspace.modules.energy.network;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;
import ru.greatdragonx.astralspace.common.machine.MachineSide;
import ru.greatdragonx.astralspace.common.machine.visual.VisualPortType;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SetLavaGeneratorPortModePayloadTest {
    @Test
    void shouldPreservePayloadAfterEncodingAndDecoding() {
        List<SetLavaGeneratorPortModePayload> payloads = List.of(
            new SetLavaGeneratorPortModePayload(MachineSide.RIGHT, VisualPortType.ENERGY, "output"),
            new SetLavaGeneratorPortModePayload(MachineSide.BACK, VisualPortType.FLUID, "service"),
            new SetLavaGeneratorPortModePayload(MachineSide.TOP, VisualPortType.FLUID, "input"),
            new SetLavaGeneratorPortModePayload(
                MachineSide.BOTTOM,
                VisualPortType.ENERGY,
                "disabled"
            )
        );

        for (SetLavaGeneratorPortModePayload original : payloads) {
            FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());

            try {
                SetLavaGeneratorPortModePayload.STREAM_CODEC.encode(buffer, original);

                SetLavaGeneratorPortModePayload decoded =
                    SetLavaGeneratorPortModePayload.STREAM_CODEC.decode(buffer);

                assertEquals(original, decoded);
            } finally {
                buffer.release();
            }
        }
    }

    @Test
    void shouldSupportAllMachineSides() {
        for (MachineSide side : MachineSide.values()) {
            SetLavaGeneratorPortModePayload original = new SetLavaGeneratorPortModePayload(
                side,
                VisualPortType.ENERGY,
                "output"
            );

            FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());

            try {
                SetLavaGeneratorPortModePayload.STREAM_CODEC.encode(buffer, original);

                SetLavaGeneratorPortModePayload decoded =
                    SetLavaGeneratorPortModePayload.STREAM_CODEC.decode(buffer);

                assertEquals(side, decoded.side());
            } finally {
                buffer.release();
            }
        }
    }

    @Test
    void shouldReturnCorrectPayloadType() {
        SetLavaGeneratorPortModePayload payload =
            new SetLavaGeneratorPortModePayload(MachineSide.RIGHT, VisualPortType.FLUID, "input");

        assertEquals(SetLavaGeneratorPortModePayload.TYPE, payload.type());
    }
}
