package ru.greatdragonx.astralspace.modules.energy.blockentities;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.FuelValues;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;
import net.neoforged.neoforge.transfer.transaction.RootCommitJournal;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import ru.greatdragonx.astralspace.common.machine.MachineSide;
import ru.greatdragonx.astralspace.common.machine.MachineSideDirections;
import ru.greatdragonx.astralspace.common.machine.RedstoneControlMode;
import ru.greatdragonx.astralspace.modules.energy.EnergyBlockEntities;
import ru.greatdragonx.astralspace.modules.energy.blocks.CoalGeneratorBlock;
import ru.greatdragonx.astralspace.modules.energy.blocks.state.CoalGeneratorFrontState;
import ru.greatdragonx.astralspace.modules.energy.config.CoalGeneratorEnergyOutputMapper;
import ru.greatdragonx.astralspace.modules.energy.menus.CoalGeneratorMenu;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

public class CoalGeneratorBlockEntity extends BlockEntity implements MenuProvider {
    private static final long LOW_FUEL_THRESHOLD = 8_000L;
    public static final int FUEL_SLOT = 0;
    public static final int ENERGY_CAPACITY = 10_000;
    public static final int MAX_EXTRACT = 80;
    public static final int ENERGY_GENERATION_PER_TICK = 40;
    public static final int DATA_ENERGY = 0;
    public static final int DATA_BURN_TIME = 1;
    public static final int DATA_BURN_DURATION = 2;
    public static final int DATA_FRONT_STATE = 3;
    public static final int DATA_PRODUCTION = 4;
    public static final int DATA_TRANSFER = 5;
    public static final int DATA_OUTPUT_MASK = 6;
    public static final int DATA_REDSTONE_MODE = 7;
    public static final int DATA_REDSTONE_BLOCKED = 8;
    public static final int DATA_COUNT = 9;
    private final SimpleEnergyHandler energyHandler;
    private final ItemStacksResourceHandler fuelInventory;
    private final Map<Direction, OutputEnergyHandler> outputHandlers =
        new EnumMap<>(Direction.class);
    private int burnTime = 0;
    private int burnDuration = 0;
    private long transferTick = -1L;
    private int transferredThisTick = 0;
    private int transferredPreviousTick = 0;
    private int currentProductionRate = 0;
    private RedstoneControlMode redstoneControlMode = RedstoneControlMode.IGNORE;

    private final ContainerData data = new ContainerData() {

        @Override
        public int get(int index) {
            return switch (index) {
                case DATA_ENERGY -> CoalGeneratorBlockEntity.this.energyHandler.getAmountAsInt();
                case DATA_BURN_TIME -> CoalGeneratorBlockEntity.this.burnTime;
                case DATA_BURN_DURATION -> CoalGeneratorBlockEntity.this.burnDuration;
                case DATA_FRONT_STATE -> {
                    CoalGeneratorFrontState frontState =
                        CoalGeneratorBlockEntity.this.getBlockState()
                            .getValue(CoalGeneratorBlock.FRONT_STATE);
                    yield switch (frontState) {
                        case OFF -> 0;
                        case RUNNING_NORMAL -> 1;
                        case RUNNING_LOW_FUEL -> 2;
                    };
                }
                case DATA_PRODUCTION -> CoalGeneratorBlockEntity.this.getCurrentProductionRate();
                case DATA_TRANSFER -> CoalGeneratorBlockEntity.this.getCurrentTransferRate();
                case DATA_OUTPUT_MASK -> CoalGeneratorBlockEntity.this.getEnergyOutputMask();
                case DATA_REDSTONE_MODE ->
                    CoalGeneratorBlockEntity.this.redstoneControlMode.ordinal();
                case DATA_REDSTONE_BLOCKED -> {
                    Level level = CoalGeneratorBlockEntity.this.getLevel();
                    if (level == null) {
                        yield 0;
                    }

                    boolean hasSignal = level.hasNeighborSignal(worldPosition);
                    boolean canOperate = redstoneControlMode.allowsOperation(hasSignal);

                    yield canOperate ? 0 : 1;
                }
                default -> 0;
            };
        }

        @Override
        public void set(
            int index,
            int value
        ) {
        }

        @Override
        public int getCount() {
            return DATA_COUNT;
        }
    };

    private final class OutputEnergyHandler implements EnergyHandler {
        private final Direction side;

        private OutputEnergyHandler(Direction side) {
            this.side = side;
        }

        @Override
        public long getAmountAsLong() {
            return CoalGeneratorBlockEntity.this.energyHandler.getAmountAsLong();
        }

        @Override
        public long getCapacityAsLong() {
            return CoalGeneratorBlockEntity.this.energyHandler.getCapacityAsLong();
        }

        @Override
        public int insert(
            int amount,
            @NonNull TransactionContext transaction
        ) {
            return 0;
        }

        @Override
        public int extract(
            int amount,
            @NonNull TransactionContext transaction
        ) {
            boolean isOutputEnabled = CoalGeneratorBlockEntity.this.isEnergyOutputEnabled(side);
            if (!isOutputEnabled) {
                return 0;
            }

            int extracted = CoalGeneratorBlockEntity.this.energyHandler.extract(
                amount,
                transaction
            );

            if (extracted > 0) {
                new RootCommitJournal(() -> CoalGeneratorBlockEntity.this.recordEnergyTransfer(extracted)).updateSnapshots(transaction);
            }

            return extracted;
        }
    }

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

    public @Nullable EnergyHandler getEnergyHandlerForSide(
        @Nullable Direction side
    ) {
        if (side == null) {
            return null;
        }

        boolean isOutputEnabled = this.isEnergyOutputEnabled(side);
        if (!isOutputEnabled) {
            return null;
        }

        return outputHandlers.computeIfAbsent(
            side,
            OutputEnergyHandler::new
        );
    }

    public static void serverTick(
        Level level,
        BlockPos pos,
        BlockState state,
        @NonNull CoalGeneratorBlockEntity blockEntity
    ) {
        blockEntity.currentProductionRate = 0;

        if (blockEntity.energyHandler.getAmountAsInt() >= ENERGY_CAPACITY) {
            blockEntity.updateFrontState(
                state,
                false
            );
            return;
        }

        boolean hasSignal = level.hasNeighborSignal(pos);
        boolean canOperate = blockEntity.redstoneControlMode.allowsOperation(hasSignal);

        if (!canOperate) {
            blockEntity.updateFrontState(
                state,
                false
            );
            return;
        }

        if (blockEntity.burnTime <= 0) {
            boolean isBurningStarted = blockEntity.tryStartBurning();

            if (!isBurningStarted || blockEntity.burnTime <= 0) {
                blockEntity.updateFrontState(
                    state,
                    false
                );
                return;
            }
        }

        int currentEnergy = blockEntity.energyHandler.getAmountAsInt();
        int generatedEnergy = Math.min(
            ENERGY_GENERATION_PER_TICK,
            ENERGY_CAPACITY - currentEnergy
        );

        blockEntity.energyHandler.set(currentEnergy + generatedEnergy);
        blockEntity.currentProductionRate = generatedEnergy;
        blockEntity.burnTime -= 1;
        blockEntity.setChanged();
        blockEntity.updateFrontState(
            state,
            true
        );
    }

    public int getCurrentTransferRate() {
        if (this.level == null) {
            return 0;
        }

        long currentTick = this.level.getGameTime();

        if (this.transferTick == currentTick) {
            return this.transferredPreviousTick;
        }

        if (this.transferTick == currentTick - 1) {
            return this.transferredThisTick;
        }

        return 0;
    }

    public int getCurrentProductionRate() {
        return this.currentProductionRate;
    }

    @Override
    public @NonNull Component getDisplayName() {
        return Component.translatable("block.astralspace.coal_generator");
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(
        int containerId,
        @NonNull Inventory playerInventory,
        @NonNull Player player
    ) {
        if (this.level == null) {
            return null;
        }

        return new CoalGeneratorMenu(
            containerId,
            playerInventory,
            this.fuelInventory,
            ContainerLevelAccess.create(
                this.level,
                this.worldPosition
            ),
            this.data
        );
    }

    public void toggleEnergyOutput(MachineSide side) {
        if (this.level == null) {
            return;
        }

        if (this.level.isClientSide()) {
            return;
        }

        BlockState currentState = this.getBlockState();
        BlockState newState = CoalGeneratorEnergyOutputMapper.toggleOutput(
            currentState,
            side
        );

        if (currentState == newState) {
            return;
        }

        boolean isSuccess = this.level.setBlock(
            this.getBlockPos(),
            newState,
            Block.UPDATE_ALL
        );

        if (isSuccess) {
            this.level.invalidateCapabilities(this.worldPosition);
        }
    }

    public void setRedstoneControlMode(RedstoneControlMode mode) {
        if (this.level == null) {
            return;
        }

        if (this.level.isClientSide()) {
            return;
        }

        Objects.requireNonNull(
            mode,
            "Redstone control mode cannot be null"
        );

        if (this.redstoneControlMode == mode) {
            return;
        }

        this.redstoneControlMode = mode;
        this.setChanged();
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
        String savedMode = input.getStringOr(
            "redstoneControlMode",
            RedstoneControlMode.IGNORE.name()
        );
        try {
            this.redstoneControlMode = RedstoneControlMode.valueOf(savedMode);
        } catch (IllegalArgumentException e) {
            this.redstoneControlMode = RedstoneControlMode.IGNORE;
        }
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
        output.putString(
            "redstoneControlMode",
            this.redstoneControlMode.name()
        );
        this.fuelInventory.serialize(fuelOutput);
    }

    private void recordEnergyTransfer(int amount) {
        if (this.level == null || amount <= 0) {
            return;
        }

        long currentTick = this.level.getGameTime();

        if (this.transferTick != currentTick) {
            this.transferredPreviousTick =
                this.transferTick == currentTick - 1 ? this.transferredThisTick : 0;

            this.transferTick = currentTick;
            this.transferredThisTick = 0;
        }

        this.transferredThisTick += amount;
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

    private boolean isEnergyOutputEnabled(Direction side) {
        if (side == null) {
            return false;
        }

        BlockState state = this.getBlockState();

        Direction facing = state.getValue(CoalGeneratorBlock.FACING);

        MachineSide machineSide = MachineSideDirections.fromWorld(
            side,
            facing
        );

        return CoalGeneratorEnergyOutputMapper.isOutputEnabled(
            state,
            machineSide
        );
    }

    private long calculateTotalBurnTime() {
        long totalBurnTime = Math.max(
            0,
            this.burnTime
        );
        if (fuelInventory.getAmountAsInt(FUEL_SLOT) == 0) {
            return totalBurnTime;
        }

        if (this.level == null) {
            return totalBurnTime;
        }
        FuelValues fuelValues = this.level.fuelValues();

        ItemResource resource = fuelInventory.getResource(FUEL_SLOT);
        ItemStack stack = resource.toStack();
        int burningTime = stack.getBurnTime(
            null,
            fuelValues
        );

        long fuelCount = fuelInventory.getAmountAsLong(FUEL_SLOT);

        return totalBurnTime + fuelCount * burningTime;
    }

    private int getEnergyOutputMask() {
        BlockState state = this.getBlockState();

        return CoalGeneratorEnergyOutputMapper.toMask(state);
    }

    private CoalGeneratorFrontState determineFrontState(boolean isRunning) {
        if (!isRunning) {
            return CoalGeneratorFrontState.OFF;
        }

        long totalBurnTime = calculateTotalBurnTime();
        if (totalBurnTime >= LOW_FUEL_THRESHOLD) {
            return CoalGeneratorFrontState.RUNNING_NORMAL;
        }

        return CoalGeneratorFrontState.RUNNING_LOW_FUEL;
    }

    private void updateFrontState(
        BlockState state,
        boolean isRunning
    ) {
        if (level == null) {
            return;
        }
        CoalGeneratorFrontState desiredState = determineFrontState(isRunning);
        CoalGeneratorFrontState currentState = state.getValue(CoalGeneratorBlock.FRONT_STATE);

        if (currentState == desiredState) {
            return;
        }

        level.setBlock(
            worldPosition,
            state.setValue(
                CoalGeneratorBlock.FRONT_STATE,
                desiredState
            ),
            Block.UPDATE_CLIENTS
        );
    }
}
