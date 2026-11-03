package ru.greatdragonx.astralspace.common.energy.port;

import net.neoforged.neoforge.transfer.transaction.TransactionContext;

public interface EnergyPortOperations {

    long getStoredEnergy();

    long getCapacity();

    int insert(
        EnergyPortId portId,
        int amount,
        TransactionContext transaction
    );

    int extract(
        EnergyPortId portId,
        int amount,
        TransactionContext transaction
    );
}
