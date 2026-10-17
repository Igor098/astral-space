package ru.greatdragonx.astralspace.modules.energy.blockentities;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.FuelValues;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.jspecify.annotations.NonNull;
import ru.greatdragonx.astralspace.modules.energy.EnergyBlockEntities;

public class CoalGeneratorBlockEntity extends BlockEntity {
    private static final int FUEL_SLOT = 0;
    private static final int ENERGY_CAPACITY = 10_000;
    private static final int MAX_EXTRACT = 80;
    private static final int ENERGY_GENERATION_PER_TICK = 40;
    private final SimpleEnergyHandler energyHandler;
    private final ItemStacksResourceHandler fuelInventory;
    private int burnTime = 0;
    private int burnDuration = 0;

    public CoalGeneratorBlockEntity(
        BlockPos pos,
        BlockState blockState
    ) {
        super(
            EnergyBlockEntities.COAL_GENERATOR_BLOCK_ENTITY.get(),
            pos,
            blockState
        );

        this.energyHandler = new SimpleEnergyHandler(
            ENERGY_CAPACITY,
            0,
            MAX_EXTRACT
        ) {
            @Override
            protected void onEnergyChanged(int previousAmount) {
                CoalGeneratorBlockEntity.this.setChanged();
            }
        };

        this.fuelInventory = new ItemStacksResourceHandler(1) {
            @Override
            protected void onContentsChanged(
                int index,
                @NonNull ItemStack previousContents
            ) {
                CoalGeneratorBlockEntity.this.setChanged();
            }

            @Override
            public boolean isValid(
                int index,
                @NonNull ItemResource resource
            ) {
                ItemStack stack = resource.toStack();
                Level level = CoalGeneratorBlockEntity.this.level;

                if (level == null) {
                    return false;
                }

                if (!stack.getCraftingRemainder()
                    .isEmpty()) {
                    return false;
                }

                FuelValues fuelValues = level.fuelValues();
                return stack.getBurnTime(
                    null,
                    fuelValues
                ) > 0;
            }
        };
    }

    public static void serverTick(
        Level level,
        BlockPos pos,
        BlockState state,
        CoalGeneratorBlockEntity blockEntity
    ) {
        if (blockEntity.energyHandler.getAmountAsInt() >= ENERGY_CAPACITY) {
            return;
        }

        if (blockEntity.burnTime <= 0) {
            blockEntity.tryStartBurning();

            if (blockEntity.burnTime <= 0) {
                return;
            }
        }

        int currentEnergy = blockEntity.energyHandler.getAmountAsInt();
        int generatedEnergy = Math.min(
            ENERGY_GENERATION_PER_TICK,
            ENERGY_CAPACITY - currentEnergy
        );

        blockEntity.energyHandler.set(currentEnergy + generatedEnergy);
        blockEntity.burnTime -= 1;
        blockEntity.setChanged();
    }

    @Override
    protected void loadAdditional(@NonNull ValueInput input) {
        super.loadAdditional(input);
        this.energyHandler.set(input.getIntOr(
            "energyStored",
            0
        ));
        this.burnTime = Math.max(
            0,
            input.getIntOr(
                "burnTime",
                0
            )
        );
        this.burnDuration = Math.max(
            0,
            input.getIntOr(
                "burnDuration",
                0
            )
        );
        this.fuelInventory.deserialize(input.childOrEmpty("fuelInventory"));
    }

    @Override
    protected void saveAdditional(@NonNull ValueOutput output) {
        super.saveAdditional(output);
        ValueOutput fuelOutput = output.child("fuelInventory");
        output.putInt(
            "energyStored",
            this.energyHandler.getAmountAsInt()
        );
        output.putInt(
            "burnTime",
            this.burnTime
        );
        output.putInt(
            "burnDuration",
            this.burnDuration
        );
        this.fuelInventory.serialize(fuelOutput);
    }

    private boolean consumeFuel() {
        int amount = fuelInventory.getAmountAsInt(FUEL_SLOT);
        if (amount == 0) {
            return false;
        }

        ItemResource resource = fuelInventory.getResource(FUEL_SLOT);

        try (Transaction tx = Transaction.openRoot()) {
            int extracted = fuelInventory.extract(
                FUEL_SLOT,
                resource,
                1,
                tx
            );
            if (extracted == 1) {
                tx.commit();
                return true;
            }

            return false;
        }
    }

    private boolean tryStartBurning() {
        if (burnTime > 0) {
            return false;
        }

        int amount = energyHandler.getAmountAsInt();
        int capacity = energyHandler.getCapacityAsInt();
        if (amount >= capacity) {
            return false;
        }

        if (this.level == null) {
            return false;
        }
        FuelValues fuelValues = this.level.fuelValues();

        if (fuelInventory.getAmountAsInt(FUEL_SLOT) == 0) {
            return false;
        }
        ItemResource resource = fuelInventory.getResource(FUEL_SLOT);
        ItemStack stack = resource.toStack();

        if (!stack.getCraftingRemainder()
            .isEmpty()) {
            return false;
        }

        int burningTime = stack.getBurnTime(
            null,
            fuelValues
        );

        if (burningTime <= 0) {
            return false;
        }

        boolean fuelConsumed = this.consumeFuel();
        if (fuelConsumed) {
            this.burnDuration = burningTime;
            this.burnTime = burningTime;
            this.setChanged();

            return true;
        }
        return false;
    }
}
