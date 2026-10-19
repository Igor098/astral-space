package ru.greatdragonx.astralspace.modules.energy.menus;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.registries.DeferredRegister;
import ru.greatdragonx.astralspace.AstralSpace;

import java.util.function.Supplier;

public final class EnergyMenus {
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(
        Registries.MENU,
        AstralSpace.MOD_ID
    );
    public static final Supplier<MenuType<CoalGeneratorMenu>> COAL_GENERATOR_MENU = MENUS.register(
        "coal_generator_menu",
        () -> new MenuType<>(
            CoalGeneratorMenu::new,
            FeatureFlags.DEFAULT_FLAGS
        )
    );

    private EnergyMenus() {
    }
}
