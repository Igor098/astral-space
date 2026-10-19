package ru.greatdragonx.astralspace;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;
import ru.greatdragonx.astralspace.modules.energy.*;
import ru.greatdragonx.astralspace.modules.energy.menus.EnergyMenus;
import ru.greatdragonx.astralspace.modules.materials.MaterialBlocks;
import ru.greatdragonx.astralspace.modules.materials.MaterialItems;
import ru.greatdragonx.astralspace.modules.metalworking.MetalworkingItems;

@Mod(AstralSpace.MOD_ID)
public class AstralSpace {
    public static final String MOD_ID = "astralspace";
    public static final Logger LOGGER = LogUtils.getLogger();

    public AstralSpace(
        IEventBus modEventBus,
        ModContainer modContainer
    ) {
        EnergyBlockTypes.BLOCK_TYPES.register(modEventBus);
        EnergyBlockEntities.BLOCK_ENTITY_TYPES.register(modEventBus);
        EnergyBlocks.BLOCKS.register(modEventBus);
        MaterialBlocks.BLOCKS.register(modEventBus);
        MaterialItems.ITEMS.register(modEventBus);
        MetalworkingItems.ITEMS.register(modEventBus);
        EnergyItems.ITEMS.register(modEventBus);
        EnergyMenus.MENUS.register(modEventBus);

        modEventBus.addListener(EnergyCapabilitiesRegistration::register);
    }
}
