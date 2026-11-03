package ru.greatdragonx.astralspace.common.energy;

import net.neoforged.neoforge.transfer.TransferPreconditions;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandlerUtil;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jspecify.annotations.NonNull;
import ru.greatdragonx.astralspace.common.energy.port.EnergyPortId;
import ru.greatdragonx.astralspace.common.energy.port.EnergyPortPolicy;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public final class EnergyPushTransfer {
    private final EnergyTransferCoordinator coordinator;
    private final EnergyPortPolicy policy;
    private final Map<EnergyPortId, EnergyHandler> sourceHandlers = new HashMap<>();

    public EnergyPushTransfer(
        EnergyTransferCoordinator coordinator,
        EnergyPortPolicy policy
    )
    {
        this.coordinator = Objects.requireNonNull(
            coordinator,
            "coordinator cannot be null"
        );

        this.policy = Objects.requireNonNull(
            policy,
            "policy cannot be null"
        );
    }

    public int push(
        EnergyPortId portId,
        EnergyHandler target,
        int maxAmount,
        TransactionContext transaction
    )
    {
        Objects.requireNonNull(
            portId,
            "portId cannot be null"
        );
        Objects.requireNonNull(
            target,
            "target cannot be null"
        );
        Objects.requireNonNull(
            transaction,
            "transaction cannot be null"
        );
        TransferPreconditions.checkNonNegative(maxAmount);

        if (maxAmount == 0) {
            return 0;
        }

        if (!this.policy.allowsExtract(portId)) {
            return 0;
        }
        EnergyHandler source = this.sourceHandlers.computeIfAbsent(
            portId,
            this::createSourceHandler
        );

        return EnergyHandlerUtil.move(
            source,
            target,
            maxAmount,
            transaction
        );
    }

    private @NonNull EnergyHandler createSourceHandler(EnergyPortId portId) {
        return new EnergyHandler() {
            @Override
            public long getAmountAsLong() {
                return EnergyPushTransfer.this.coordinator.getStoredEnergy();
            }

            @Override
            public long getCapacityAsLong() {
                return EnergyPushTransfer.this.coordinator.getCapacity();
            }

            @Override
            public int insert(
                int amount,
                @NonNull TransactionContext transaction
            )
            {
                return 0;
            }

            @Override
            public int extract(
                int amount,
                @NonNull TransactionContext transaction
            )
            {
                if (!EnergyPushTransfer.this.policy.allowsExtract(portId)) {
                    return 0;
                }

                return EnergyPushTransfer.this.coordinator.extract(
                    portId,
                    amount,
                    transaction,
                    EnergyTransferCoordinator.ExtractionOrigin.PUSH
                );
            }
        };
    }
}
