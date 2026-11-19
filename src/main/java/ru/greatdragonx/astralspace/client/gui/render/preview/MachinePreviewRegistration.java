package ru.greatdragonx.astralspace.client.gui.render.preview;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterPictureInPictureRenderersEvent;
import org.jspecify.annotations.NonNull;
import ru.greatdragonx.astralspace.AstralSpace;

@EventBusSubscriber(modid = AstralSpace.MOD_ID, value = Dist.CLIENT)
public class MachinePreviewRegistration {
    private MachinePreviewRegistration() {}

    @SubscribeEvent
    public static void register(
        @NonNull RegisterPictureInPictureRenderersEvent event
    )
    {
        event.register(
            MachinePreviewRenderState.class,
            MachinePreviewRenderer::new
        );
    }
}
