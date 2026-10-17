package ru.greatdragonx.astralspace.modules.metalworking;

import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import ru.greatdragonx.astralspace.AstralSpace;
import ru.greatdragonx.astralspace.modules.metalworking.items.MetalworkingHammerItem;

public final class MetalworkingItems {
    public static final DeferredRegister.Items ITEMS =
        DeferredRegister.createItems(AstralSpace.MOD_ID);
    public static final DeferredItem<MetalworkingHammerItem> METALWORKING_HAMMER =
        ITEMS.registerItem(
            "metalworking_hammer",
            MetalworkingHammerItem::new,
            properties -> properties.durability(128)
        );

    private MetalworkingItems() {
    }
}

