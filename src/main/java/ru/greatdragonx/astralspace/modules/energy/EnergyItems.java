package ru.greatdragonx.astralspace.modules.energy;

import net.minecraft.world.item.BlockItem;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import ru.greatdragonx.astralspace.AstralSpace;

public class EnergyItems {
    public static final DeferredRegister.Items ITEMS =
        DeferredRegister.createItems(AstralSpace.MOD_ID);
    public static final DeferredItem<BlockItem> COAL_GENERATOR_ITEM =
        ITEMS.registerSimpleBlockItem(EnergyBlocks.COAL_GENERATOR);

    private EnergyItems() {
    }
}
