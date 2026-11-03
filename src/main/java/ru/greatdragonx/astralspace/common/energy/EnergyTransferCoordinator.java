package ru.greatdragonx.astralspace.common.energy;

import net.neoforged.neoforge.transfer.TransferPreconditions;
import net.neoforged.neoforge.transfer.transaction.RootCommitJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import ru.greatdragonx.astralspace.common.energy.component.EnergyComponent;
import ru.greatdragonx.astralspace.common.energy.port.EnergyPortId;
import ru.greatdragonx.astralspace.common.energy.port.EnergyPortOperations;

import java.util.Objects;
import java.util.function.LongSupplier;

public final class EnergyTransferCoordinator implements EnergyPortOperations {
    private final EnergyComponent energy;
    private final EnergyDistributionManager distribution;
    private final EnergyTransferBudget insertionBudget;
    private final EnergyTransferBudget extractionBudget;
    private final EnergyTransferTracker transferTracker;
    private final LongSupplier currentTickSupplier;

    public enum ExtractionOrigin {
        PULL,
        PUSH
    }

    public EnergyTransferCoordinator(
        EnergyComponent energy,
        EnergyDistributionManager distribution,
        EnergyTransferBudget insertionBudget,
        EnergyTransferBudget extractionBudget,
        EnergyTransferTracker transferTracker,
        LongSupplier currentTickSupplier
    )
    {
        Objects.requireNonNull(
            insertionBudget,
            "insertionBudget cannot be null"
        );
        Objects.requireNonNull(
            extractionBudget,
            "extractionBudget cannot be null"
        );
        Objects.requireNonNull(
            energy,
            "energy cannot be null"
        );
        Objects.requireNonNull(
            distribution,
            "distribution cannot be null"
        );
        Objects.requireNonNull(
            transferTracker,
            "transferTracker cannot be null"
        );
        Objects.requireNonNull(
            currentTickSupplier,
            "currentTickSupplier cannot be null"
        );

        if (insertionBudget == extractionBudget) {
            throw new IllegalArgumentException("Insertion and extraction budgets must be distinct instances");
        }

        this.insertionBudget = insertionBudget;
        this.extractionBudget = extractionBudget;
        this.energy = energy;
        this.distribution = distribution;
        this.transferTracker = transferTracker;
        this.currentTickSupplier = currentTickSupplier;
    }

    @Override
    public long getStoredEnergy() {
        return this.energy.getStoredEnergy();
    }

    @Override
    public long getCapacity() {
        return this.energy.getCapacity();
    }

    @Override
    public int insert(
        EnergyPortId portId,
        int amount,
        TransactionContext transaction
    )
    {
        Objects.requireNonNull(
            portId,
            "portId cannot be null"
        );
        Objects.requireNonNull(
            transaction,
            "transaction cannot be null"
        );
        TransferPreconditions.checkNonNegative(amount);

        if (amount == 0) {
            return 0;
        }

        long currentTick = this.currentTickSupplier.getAsLong();

        int allowed = this.insertionBudget.getAllowedAmount(
            amount,
            currentTick
        );

        if (allowed == 0) {
            return 0;
        }

        int inserted = this.energy.insertEnergy(
            allowed,
            transaction
        );

        if (inserted > 0) {
            this.insertionBudget.recordTransfer(
                inserted,
                currentTick,
                transaction
            );
        }

        return inserted;
    }

    @Override
    public int extract(
        EnergyPortId portId,
        int amount,
        TransactionContext transaction
    )
    {
        return this.extract(
            portId,
            amount,
            transaction,
            ExtractionOrigin.PULL
        );
    }

    public int extract(
        EnergyPortId portId,
        int amount,
        TransactionContext transaction,
        ExtractionOrigin origin
    )
    {
        Objects.requireNonNull(
            origin,
            "origin cannot be null"
        );
        Objects.requireNonNull(
            portId,
            "portId cannot be null"
        );
        Objects.requireNonNull(
            transaction,
            "transaction cannot be null"
        );
        TransferPreconditions.checkNonNegative(amount);

        if (amount == 0) {
            return 0;
        }

        long currentTick = this.currentTickSupplier.getAsLong();

        if (origin == ExtractionOrigin.PULL) {
            this.distribution.recordRequest(
                portId,
                amount,
                currentTick,
                transaction
            );
        }

        int availableEnergy = (int) Math.min(
            this.energy.getStoredEnergy(),
            Integer.MAX_VALUE
        );
        int remainingBudget = this.extractionBudget.getRemaining(currentTick);
        int adaptiveLimit = this.distribution.getAllowedAdaptiveAmount(
            portId,
            amount,
            remainingBudget,
            availableEnergy,
            currentTick,
            transaction
        );

        int allowed = this.extractionBudget.getAllowedAmount(
            adaptiveLimit,
            currentTick
        );

        if (allowed == 0) {
            return 0;
        }

        int extracted = this.energy.extractEnergy(
            allowed,
            transaction
        );
        if (extracted > 0) {
            this.extractionBudget.recordTransfer(
                extracted,
                currentTick,
                transaction
            );
            this.distribution.recordExtraction(
                portId,
                extracted,
                currentTick,
                transaction
            );
            new RootCommitJournal(() -> {
                this.transferTracker.recordTransfer(
                    extracted,
                    currentTick
                );
            }).updateSnapshots(transaction);
        }

        return extracted;
    }
}
