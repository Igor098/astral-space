package ru.greatdragonx.astralspace.modules.metalworking.items;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.NonNull;

public class MetalworkingHammerItem extends Item {
    public MetalworkingHammerItem(final Properties properties) {
        super(properties);
    }

    @Override
    public @NonNull ItemStack getCraftingRemainder(@NonNull ItemStack itemStack) {
        ItemStack stack = itemStack.copy();

        int damage = stack.getDamageValue();
        int nextDamage = damage + 1;

        if (nextDamage >= stack.getMaxDamage()) {
            return ItemStack.EMPTY;
        }

        stack.setDamageValue(nextDamage);
        return stack;
    }
}
