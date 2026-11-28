package ru.greatdragonx.astralspace.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Test-only receiver with observable capability queries and transactional FE.
 */
final class GameTestTransferReceiver implements EnergyHandler {
    private record Key(ServerLevel level, BlockPos pos) {}

    private static final Map<Key, Function<Direction, EnergyHandler>> PROVIDERS = new HashMap<>();
    private final int capacity;
    private int stored;
    int insertCalls;
    int attemptedAccepted;
    private final SnapshotJournal<Integer> journal = new SnapshotJournal<>() {
        @Override
        protected @NonNull Integer createSnapshot() {return stored;}

        @Override
        protected void revertToSnapshot(@NonNull Integer snapshot) {stored = snapshot;}
    };

    GameTestTransferReceiver(int capacity) {this(capacity, 0);}

    GameTestTransferReceiver(int capacity, int stored) {
        if (capacity <= 0 || stored < 0 || stored > capacity) throw new IllegalArgumentException();
        this.capacity = capacity;
        this.stored = stored;
    }

    static void registerCapabilities(RegisterCapabilitiesEvent event) {
        // Separate marker from the GOLD_BLOCK and IRON_BLOCK fixtures of earlier suites.
        event.registerBlock(
            Capabilities.Energy.BLOCK, (level, pos, state, be, side) -> {
                if (!(level instanceof ServerLevel serverLevel)) return null;
                var provider = PROVIDERS.get(new Key(serverLevel, pos));
                return provider == null ? null : provider.apply(side);
            }, Blocks.DIAMOND_BLOCK
        );
    }

    /**
     * Scoped per-level registrations; allows two physical positions to share one storage.
     */
    static final class Scope implements AutoCloseable {
        private final GameTestHelper helper;
        private final List<Key> keys = new ArrayList<>();

        Scope(GameTestHelper helper) {this.helper = helper;}

        void place(BlockPos relative, GameTestTransferReceiver receiver) {
            placeProvider(relative, side -> receiver);
        }

        void placeProvider(BlockPos relative, Function<Direction, EnergyHandler> provider) {
            var key = new Key(helper.getLevel(), helper.absolutePos(relative).immutable());
            helper.setBlock(relative, Blocks.DIAMOND_BLOCK);
            if (PROVIDERS.putIfAbsent(key, provider) != null) {
                throw new IllegalStateException("Duplicate transfer receiver: " + key.pos());
            }
            keys.add(key);
            key.level().invalidateCapabilities(key.pos());
        }

        @Override
        public void close() {
            for (Key key : keys) {
                PROVIDERS.remove(key);
                key.level().invalidateCapabilities(key.pos());
            }
            keys.clear();
        }
    }

    @Override
    public long getAmountAsLong() {return stored;}

    @Override
    public long getCapacityAsLong() {return capacity;}

    @Override
    public int insert(int amount, @NonNull TransactionContext transaction) {
        if (amount < 0) throw new IllegalArgumentException("negative insert");
        insertCalls++;
        int accepted = Math.min(amount, capacity - stored);
        attemptedAccepted += accepted; // Diagnostic counters deliberately do not roll back.
        if (accepted > 0) {
            journal.updateSnapshots(transaction);
            stored += accepted;
        }
        return accepted;
    }

    @Override
    public int extract(int amount, @NonNull TransactionContext transaction) {
        if (amount < 0) throw new IllegalArgumentException("negative extract");
        return 0;
    }
}
