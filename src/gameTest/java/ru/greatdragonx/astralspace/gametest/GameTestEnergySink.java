package ru.greatdragonx.astralspace.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jspecify.annotations.NonNull;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Тестовый приёмник FE. Существует ТОЛЬКО в sourceSet gameTest.
 *
 * <p>В качестве физического маркера используется обычный золотой блок.
 * Capability для него регистрируется только когда загружен astralspace_gametest.
 * Транзакционное состояние поддерживает откат, поэтому probeEnergyAcceptance
 * не приводит к фактическому зачислению пробной энергии.</p>
 */
final class GameTestEnergySink implements EnergyHandler {
    private static final Map<BlockPos, GameTestEnergySink> HANDLERS = new ConcurrentHashMap<>();

    private final int capacity;
    private int stored;

    private final SnapshotJournal<Integer> journal = new SnapshotJournal<>() {
        @Override
        protected @NonNull Integer createSnapshot() {
            return GameTestEnergySink.this.stored;
        }

        @Override
        protected void revertToSnapshot(@NonNull Integer snapshot) {
            GameTestEnergySink.this.stored = snapshot;
        }
    };

    private GameTestEnergySink(int capacity) {
        if (capacity <= 0) throw new IllegalArgumentException("capacity must be positive");
        this.capacity = capacity;
    }

    static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlock(
            Capabilities.Energy.BLOCK,
            (level, pos, state, blockEntity, side) -> HANDLERS.get(pos),
            Blocks.GOLD_BLOCK
        );
    }

    static GameTestEnergySink place(GameTestHelper helper, BlockPos relativePos, int capacity) {
        BlockPos worldPos = helper.absolutePos(relativePos);
        GameTestEnergySink sink = new GameTestEnergySink(capacity);
        HANDLERS.put(worldPos.immutable(), sink);
        helper.setBlock(relativePos, Blocks.GOLD_BLOCK);
        helper.getLevel().invalidateCapabilities(worldPos);
        return sink;
    }

    @Override
    public long getAmountAsLong() {
        return this.stored;
    }

    @Override
    public long getCapacityAsLong() {
        return this.capacity;
    }

    @Override
    public int insert(int amount, @NonNull TransactionContext transaction) {
        if (amount < 0) throw new IllegalArgumentException("amount must be nonnegative");
        int accepted = Math.min(amount, this.capacity - this.stored);
        if (accepted > 0) {
            this.journal.updateSnapshots(transaction);
            this.stored += accepted;
        }
        return accepted;
    }

    @Override
    public int extract(int amount, @NonNull TransactionContext transaction) {
        if (amount < 0) throw new IllegalArgumentException("amount must be nonnegative");
        return 0; // Только потребитель, не источник FE.
    }
}
