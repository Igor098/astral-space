package ru.greatdragonx.astralspace.common.energy.port;

import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jspecify.annotations.NonNull;

public interface EnergyPortOperations {

    long getStoredEnergy();

    long getCapacity();

    int insert(@NonNull EnergyPortId portId, int amount, @NonNull TransactionContext transaction);

    int extract(@NonNull EnergyPortId portId, int amount, @NonNull TransactionContext transaction);
}
