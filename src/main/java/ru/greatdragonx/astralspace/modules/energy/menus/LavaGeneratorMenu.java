package ru.greatdragonx.astralspace.modules.energy.menus;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.NonNull;
import ru.greatdragonx.astralspace.modules.energy.EnergyBlocks;
import ru.greatdragonx.astralspace.modules.energy.blockentities.LavaGeneratorBlockEntity;
import ru.greatdragonx.astralspace.modules.energy.blocks.state.LavaGeneratorFrontState;

public class LavaGeneratorMenu extends AbstractContainerMenu {
    private final ContainerLevelAccess access;
    private final ContainerData data;

    public LavaGeneratorMenu(
        int containerId,
        Inventory playerInventory
    )
    {
        this(
            containerId,
            playerInventory,
            ContainerLevelAccess.NULL,
            new SimpleContainerData(LavaGeneratorBlockEntity.DATA_COUNT)
        );
    }

    public LavaGeneratorMenu(
        int containerId,
        Inventory playerInventory,
        ContainerLevelAccess access,
        ContainerData data
    )
    {
        super(
            EnergyMenus.LAVA_GENERATOR_MENU.get(),
            containerId
        );

        this.access = access;

        this.addInventoryExtendedSlots(
            playerInventory,
            9,
            107
        );

        this.addInventoryHotbarSlots(
            playerInventory,
            9,
            169
        );

        AbstractContainerMenu.checkContainerDataCount(
            data,
            LavaGeneratorBlockEntity.DATA_COUNT
        );

        this.addDataSlots(data);
        this.data = data;
    }

    @Override
    public boolean stillValid(@NonNull Player player) {
        return AbstractContainerMenu.stillValid(
            this.access,
            player,
            EnergyBlocks.LAVA_GENERATOR.get()
        );
    }

    @Override
    public @NonNull ItemStack quickMoveStack(
        @NonNull Player player,
        int index
    )
    {
        return ItemStack.EMPTY;
    }

    public int getLavaStored() {
        return this.data.get(LavaGeneratorBlockEntity.DATA_LAVA_STORED);
    }

    public long getEnergyStored() {
        long low = this.data.get(LavaGeneratorBlockEntity.DATA_ENERGY_LOW) & 0xFFFFL;
        long high = this.data.get(LavaGeneratorBlockEntity.DATA_ENERGY_HIGH) & 0xFFFFL;

        return (high << 16) | low;
    }

    public long getEnergyCapacity() {
        return LavaGeneratorBlockEntity.ENERGY_CAPACITY;
    }

    public int getBurnTime() {
        return this.data.get(LavaGeneratorBlockEntity.DATA_BURN_TIME);
    }

    public int getBurnDuration() {
        return this.data.get(LavaGeneratorBlockEntity.DATA_BURN_DURATION);
    }

    public LavaGeneratorFrontState getFrontState() {
        int value = this.data.get(LavaGeneratorBlockEntity.DATA_FRONT_STATE);

        LavaGeneratorFrontState[] states = LavaGeneratorFrontState.values();

        if (value < 0 || value >= states.length) {
            return LavaGeneratorFrontState.OFF;
        }

        return states[value];
    }
}
