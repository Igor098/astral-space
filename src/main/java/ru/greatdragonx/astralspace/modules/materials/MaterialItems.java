package ru.greatdragonx.astralspace.modules.materials;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import static ru.greatdragonx.astralspace.AstralSpace.MOD_ID;

public final class MaterialItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MOD_ID);
    public static final DeferredItem<BlockItem> ALUMINUM_ORE_ITEM =
        ITEMS.registerSimpleBlockItem(MaterialBlocks.ALUMINUM_ORE);
    public static final DeferredItem<BlockItem> TIN_ORE_ITEM =
        ITEMS.registerSimpleBlockItem(MaterialBlocks.TIN_ORE);
    public static final DeferredItem<BlockItem> DEEPSLATE_ALUMINUM_ORE_ITEM =
        ITEMS.registerSimpleBlockItem(MaterialBlocks.DEEPSLATE_ALUMINUM_ORE);
    public static final DeferredItem<BlockItem> DEEPSLATE_TIN_ORE_ITEM =
        ITEMS.registerSimpleBlockItem(MaterialBlocks.DEEPSLATE_TIN_ORE);
    public static final DeferredItem<BlockItem> ALUMINUM_BLOCK_ITEM =
        ITEMS.registerSimpleBlockItem(MaterialBlocks.ALUMINUM_BLOCK);
    public static final DeferredItem<BlockItem> TIN_BLOCK_ITEM =
        ITEMS.registerSimpleBlockItem(MaterialBlocks.TIN_BLOCK);
    public static final DeferredItem<Item> ALUMINUM_INGOT =
        ITEMS.registerSimpleItem("aluminum_ingot");
    public static final DeferredItem<Item> TIN_INGOT = ITEMS.registerSimpleItem("tin_ingot");
    public static final DeferredItem<Item> RAW_ALUMINUM = ITEMS.registerSimpleItem("raw_aluminum");
    public static final DeferredItem<Item> RAW_TIN = ITEMS.registerSimpleItem("raw_tin");
    public static final DeferredItem<Item> ALUMINUM_PLATE =
        ITEMS.registerSimpleItem("aluminum_plate");
    public static final DeferredItem<Item> TIN_PLATE = ITEMS.registerSimpleItem("tin_plate");

    private MaterialItems() {
    }
}
