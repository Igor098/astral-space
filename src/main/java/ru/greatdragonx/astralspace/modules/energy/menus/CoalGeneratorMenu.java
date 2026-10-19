package ru.greatdragonx.astralspace.modules.energy.menus;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;
import net.neoforged.neoforge.transfer.item.ResourceHandlerSlot;
import org.jspecify.annotations.NonNull;
import ru.greatdragonx.astralspace.modules.energy.EnergyBlocks;
import ru.greatdragonx.astralspace.modules.energy.blockentities.CoalGeneratorBlockEntity;
import ru.greatdragonx.astralspace.modules.energy.blocks.state.CoalGeneratorFrontState;

public class CoalGeneratorMenu extends AbstractContainerMenu {
    private final ContainerLevelAccess access;
    private final ContainerData data;

    public CoalGeneratorMenu(
        int containerId,
        Inventory playerInventory
    ) {
        this(
            containerId,
            playerInventory,
            new ItemStacksResourceHandler(1),
            ContainerLevelAccess.NULL,
            new SimpleContainerData(CoalGeneratorBlockEntity.DATA_COUNT)
        );
    }

    public CoalGeneratorMenu(
        int containerId,
        Inventory playerInventory,
        ItemStacksResourceHandler fuelInventory,
        ContainerLevelAccess access,
        ContainerData data
    ) {
        super(
            EnergyMenus.COAL_GENERATOR_MENU.get(),
            containerId
        );

        this.access = access;
        this.addSlot(new ResourceHandlerSlot(
            fuelInventory,
            fuelInventory::set,
            CoalGeneratorBlockEntity.FUEL_SLOT,
            23,
            36
        ));
        this.addStandardInventorySlots(
            playerInventory,
            8,
            84
        );

        AbstractContainerMenu.checkContainerDataCount(
            data,
            CoalGeneratorBlockEntity.DATA_COUNT
        );

        this.addDataSlots(data);
        this.data = data;
    }

    public int getEnergyStored() {
        return this.data.get(CoalGeneratorBlockEntity.DATA_ENERGY);
    }

    public int getBurnTime() {
        return this.data.get(CoalGeneratorBlockEntity.DATA_BURN_TIME);
    }

    public int getBurnDuration() {
        return this.data.get(CoalGeneratorBlockEntity.DATA_BURN_DURATION);
    }

    public CoalGeneratorFrontState getFrontState() {
        int value = this.data.get(CoalGeneratorBlockEntity.DATA_FRONT_STATE);

        return switch (value) {
            case 1 -> CoalGeneratorFrontState.RUNNING_NORMAL;
            case 2 -> CoalGeneratorFrontState.RUNNING_LOW_FUEL;
            default -> CoalGeneratorFrontState.OFF;
        };
    }

    public int getEnergyCapacity() {
        return CoalGeneratorBlockEntity.ENERGY_CAPACITY;
    }

    public int getCurrentProductionRate() {
        return this.data.get(CoalGeneratorBlockEntity.DATA_PRODUCTION);
    }

    public int getCurrentTransferRate() {
        return this.data.get(CoalGeneratorBlockEntity.DATA_TRANSFER);
    }

    @Override
    public boolean stillValid(@NonNull Player player) {
        return AbstractContainerMenu.stillValid(
            this.access,
            player,
            EnergyBlocks.COAL_GENERATOR.get()
        );
    }

    @Override
    public @NonNull ItemStack quickMoveStack(
        @NonNull Player player,
        int index
    ) {
        Slot slot = this.slots.get(index);

        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }

        ItemStack stack = slot.getItem();
        ItemStack originalStack = stack.copy();

        boolean hasMoved;
        if (index == CoalGeneratorBlockEntity.FUEL_SLOT) {
            hasMoved = this.moveItemStackTo(
                stack,
                1,
                37,
                true
            );
        } else {
            hasMoved = this.moveItemStackTo(
                stack,
                0,
                1,
                false
            );
        }
        if (!hasMoved) {
            return ItemStack.EMPTY;
        }

        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }

        slot.onTake(
            player,
            stack
        );

        return originalStack;
    }
}
