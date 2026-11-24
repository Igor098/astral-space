package ru.greatdragonx.astralspace.modules.energy;

import net.minecraft.world.item.BlockItem;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import ru.greatdragonx.astralspace.AstralSpace;

public final class EnergyItems {
    public static final DeferredRegister.Items ITEMS =
        DeferredRegister.createItems(AstralSpace.MOD_ID);
    public static final DeferredItem<BlockItem> COAL_GENERATOR_ITEM =
        ITEMS.registerSimpleBlockItem(EnergyBlocks.COAL_GENERATOR);
    public static final DeferredItem<BlockItem> LAVA_GENERATOR_ITEM =
        ITEMS.registerSimpleBlockItem(EnergyBlocks.LAVA_GENERATOR);

    private EnergyItems() {
    }
}
