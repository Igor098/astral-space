package ru.greatdragonx.astralspace.modules.energy.menus;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import ru.greatdragonx.astralspace.common.energy.port.EnergyPortMode;
import ru.greatdragonx.astralspace.common.fluid.port.FluidPortMode;
import ru.greatdragonx.astralspace.common.machine.MachineSide;
import ru.greatdragonx.astralspace.modules.energy.EnergyBlocks;
import ru.greatdragonx.astralspace.modules.energy.blockentities.LavaGeneratorBlockEntity;
import ru.greatdragonx.astralspace.modules.energy.blocks.state.LavaGeneratorFrontState;

import java.util.Objects;

public class LavaGeneratorMenu extends AbstractContainerMenu {
    private final ContainerLevelAccess access;
    private final ContainerData data;

    public LavaGeneratorMenu(int containerId, Inventory playerInventory)
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
        super(EnergyMenus.LAVA_GENERATOR_MENU.get(), containerId);

        this.access = access;

        this.addInventoryExtendedSlots(playerInventory, 9, 107);

        this.addInventoryHotbarSlots(playerInventory, 9, 169);

        AbstractContainerMenu.checkContainerDataCount(data, LavaGeneratorBlockEntity.DATA_COUNT);

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
    public @NonNull ItemStack quickMoveStack(@NonNull Player player, int index)
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

    public int getCurrentProductionRate() {
        return this.data.get(LavaGeneratorBlockEntity.DATA_PRODUCTION);
    }

    public int getCurrentTransferRate() {
        return this.data.get(LavaGeneratorBlockEntity.DATA_TRANSFER);
    }

    public LavaGeneratorFrontState getFrontState() {
        int value = this.data.get(LavaGeneratorBlockEntity.DATA_FRONT_STATE);

        LavaGeneratorFrontState[] states = LavaGeneratorFrontState.values();

        if (value < 0 || value >= states.length) {
            return LavaGeneratorFrontState.OFF;
        }

        return states[value];
    }

    public @Nullable EnergyPortMode getEnergyPortMode(
        @NonNull MachineSide side
    )
    {
        Objects.requireNonNull(side, "side cannot be null");

        int offset = getSideOffset(side);
        int index = LavaGeneratorBlockEntity.DATA_ENERGY_PORT_START + offset;
        int code = this.data.get(index);

        return decodeEnergyPortMode(code);
    }

    public @Nullable FluidPortMode getFluidPortMode(
        @NonNull MachineSide side
    )
    {
        Objects.requireNonNull(side, "side cannot be null");

        int offset = getSideOffset(side);
        int index = LavaGeneratorBlockEntity.DATA_FLUID_PORT_START + offset;
        int code = this.data.get(index);

        return decodeFluidPortMode(code);
    }

    private static int getSideOffset(@NonNull MachineSide side) {
        return switch (side) {
            case FRONT -> 0;
            case BACK -> 1;
            case LEFT -> 2;
            case RIGHT -> 3;
            case TOP -> 4;
            case BOTTOM -> 5;
        };
    }

    private static @Nullable EnergyPortMode decodeEnergyPortMode(int code) {
        return switch (code) {
            case -1 -> null;
            case 0 -> EnergyPortMode.DISABLED;
            case 1 -> EnergyPortMode.OUTPUT;
            case 2 -> EnergyPortMode.INPUT;
            default -> throw new IllegalArgumentException("Unknown energy port mode code: " + code);
        };
    }

    private static @Nullable FluidPortMode decodeFluidPortMode(int code) {
        return switch (code) {
            case -1 -> null;
            case 0 -> FluidPortMode.DISABLED;
            case 1 -> FluidPortMode.INPUT;
            case 2 -> FluidPortMode.SERVICE;
            case 3 -> FluidPortMode.OUTPUT;
            default -> throw new IllegalArgumentException("Unknown fluid port mode code: " + code);
        };
    }
}
