package ru.greatdragonx.astralspace.common.energy.port;

import net.neoforged.neoforge.transfer.TransferPreconditions;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jspecify.annotations.NonNull;

import java.util.Objects;

public final class EnergyPortHandler implements EnergyHandler {
    private final EnergyPortId portId;
    private final EnergyPortPolicy policy;
    private final EnergyPortOperations operations;

    public EnergyPortHandler(
        EnergyPortId portId,
        EnergyPortPolicy policy,
        EnergyPortOperations operations
    )
    {
        this.portId = Objects.requireNonNull(
            portId,
            "portId cannot be null"
        );
        this.policy = Objects.requireNonNull(
            policy,
            "policy cannot be null"
        );
        this.operations = Objects.requireNonNull(
            operations,
            "operations cannot be null"
        );
    }

    @Override
    public long getAmountAsLong() {
        return this.operations.getStoredEnergy();
    }

    @Override
    public long getCapacityAsLong() {
        return this.operations.getCapacity();
    }

    @Override
    public int insert(
        int amount,
        @NonNull TransactionContext transaction
    )
    {
        TransferPreconditions.checkNonNegative(amount);
        if (amount == 0) {
            return 0;
        }

        if (!this.policy.allowsInsert(this.portId)) {
            return 0;
        }

        return this.operations.insert(
            this.portId,
            amount,
            transaction
        );
    }

    @Override
    public int extract(
        int amount,
        @NonNull TransactionContext transaction
    )
    {
        TransferPreconditions.checkNonNegative(amount);
        if (amount == 0) {
            return 0;
        }

        if (!this.policy.allowsExtract(this.portId)) {
            return 0;
        }

        return this.operations.extract(
            this.portId,
            amount,
            transaction
        );
    }
}
