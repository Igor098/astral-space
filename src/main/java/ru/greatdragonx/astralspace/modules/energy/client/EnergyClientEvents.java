package ru.greatdragonx.astralspace.modules.energy.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import ru.greatdragonx.astralspace.AstralSpace;
import ru.greatdragonx.astralspace.modules.energy.client.screens.CoalGeneratorScreen;
import ru.greatdragonx.astralspace.modules.energy.menus.EnergyMenus;

@EventBusSubscriber(modid = AstralSpace.MOD_ID, value = Dist.CLIENT)
public final class EnergyClientEvents {

    private EnergyClientEvents() {
    }

    @SubscribeEvent
    public static void registerScreens(
        RegisterMenuScreensEvent event
    ) {
        event.register(
            EnergyMenus.COAL_GENERATOR_MENU.get(),
            CoalGeneratorScreen::new
        );
    }
}
