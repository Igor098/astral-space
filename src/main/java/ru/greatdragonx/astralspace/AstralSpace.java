package ru.greatdragonx.astralspace;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;
import ru.greatdragonx.astralspace.modules.materials.MaterialBlocks;
import ru.greatdragonx.astralspace.modules.materials.MaterialItems;

@Mod(AstralSpace.MODID)
public class AstralSpace {
    public static final String MODID = "astralspace";
    public static final Logger LOGGER = LogUtils.getLogger();

    public AstralSpace(IEventBus modEventBus, ModContainer modContainer) {
        MaterialBlocks.BLOCKS.register(modEventBus);
        MaterialItems.ITEMS.register(modEventBus);
    }
}
