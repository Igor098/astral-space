package ru.greatdragonx.astralspace.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jspecify.annotations.NonNull;

import java.util.HashMap;
import java.util.Map;

/**
 * Test-only scoped receiver. Insert attempts are observable even when FE roll back.
 */
final class GameTestRecordingEnergySink implements EnergyHandler, AutoCloseable {
    private record Key(ServerLevel level, BlockPos pos) {}

    private static final Map<Key, GameTestRecordingEnergySink> HANDLERS = new HashMap<>();
    private final Key key;
    private final int capacity;
    private int stored;
    private int insertCalls;

    private final SnapshotJournal<Integer> journal = new SnapshotJournal<>() {
        @Override
        protected @NonNull Integer createSnapshot() {return stored;}

        @Override
        protected void revertToSnapshot(@NonNull Integer snapshot) {stored = snapshot;}
    };

    private GameTestRecordingEnergySink(Key key, int capacity) {
        if (capacity <= 0) throw new IllegalArgumentException("capacity must be positive");
        this.key = key;
        this.capacity = capacity;
    }

    static void registerCapabilities(RegisterCapabilitiesEvent event) {
        // Unlike the existing GOLD_BLOCK sink, only this fixture uses IRON_BLOCK.
        // The provider exposes a capability only for an explicitly registered position.
        event.registerBlock(
            Capabilities.Energy.BLOCK,
            (level, pos, state, blockEntity, side) -> level instanceof ServerLevel serverLevel ?
                HANDLERS.get(new Key(serverLevel, pos)) : null,
            Blocks.IRON_BLOCK
        );
    }

    static GameTestRecordingEnergySink place(
        GameTestHelper helper,
        BlockPos relativePos,
        int capacity
    )
    {
        Key key = new Key(helper.getLevel(), helper.absolutePos(relativePos).immutable());
        var sink = new GameTestRecordingEnergySink(key, capacity);
        helper.setBlock(relativePos, Blocks.IRON_BLOCK);
        if (HANDLERS.putIfAbsent(key, sink) != null) {
            throw new IllegalStateException("Recording sink already registered at " + key.pos());
        }
        key.level().invalidateCapabilities(key.pos());
        return sink;
    }

    int insertCalls() {return insertCalls;}

    @Override
    public long getAmountAsLong() {return stored;}

    @Override
    public long getCapacityAsLong() {return capacity;}

    @Override
    public int insert(int amount, @NonNull TransactionContext transaction) {
        if (amount < 0) throw new IllegalArgumentException("amount must be nonnegative");
        insertCalls++;
        int accepted = Math.min(amount, capacity - stored);
        if (accepted > 0) {
            journal.updateSnapshots(transaction);
            stored += accepted;
        }
        return accepted;
    }

    @Override
    public int extract(int amount, @NonNull TransactionContext transaction) {
        if (amount < 0) throw new IllegalArgumentException("amount must be nonnegative");
        return 0;
    }

    @Override
    public void close() {
        if (HANDLERS.remove(key, this)) key.level().invalidateCapabilities(key.pos());
    }
}
