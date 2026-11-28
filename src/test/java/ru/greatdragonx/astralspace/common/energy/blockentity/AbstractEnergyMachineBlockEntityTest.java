package ru.greatdragonx.astralspace.common.energy.blockentity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.junit.jupiter.api.Test;
import ru.greatdragonx.astralspace.common.energy.component.EnergyComponent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class AbstractEnergyMachineBlockEntityTest {
    private static final long CAPACITY = 10_000L;
    private static final BlockPos POSITION = new BlockPos(10, 64, -20);
    private static final String ENERGY = "energy";
    private static final String STORED_ENERGY = "stored_energy";
    private static final List<String> CHANGE = List.of("setChanged", "onEnergyChanged");

    private static final class TestMachine extends AbstractEnergyMachineBlockEntity {
        private final List<String> events = new ArrayList<>();
        private final List<Long> observedEnergy = new ArrayList<>();

        private TestMachine(long capacity) {
            super(BlockEntityType.FURNACE, POSITION, Blocks.FURNACE.defaultBlockState(), capacity);
        }

        private EnergyComponent component() {
            return getEnergyComponent();
        }

        @Override
        public void setChanged() {
            events.add("setChanged");
            super.setChanged();
        }

        @Override
        protected void onEnergyChanged() {
            events.add("onEnergyChanged");
            observedEnergy.add(getStoredEnergy());
        }

        private void clearEvents() {
            events.clear();
            observedEnergy.clear();
        }
    }

    private static TestMachine machine() {
        return new TestMachine(CAPACITY);
    }

    private static ValueInput input(CompoundTag tag) {
        return TagValueInput.create(
            ProblemReporter.DISCARDING,
            HolderLookup.Provider.create(Stream.empty()),
            tag
        );
    }

    private static CompoundTag save(TestMachine machine) {
        TagValueOutput output = TagValueOutput.createWithoutContext(ProblemReporter.DISCARDING);
        machine.saveAdditional(output);
        return output.buildResult();
    }

    private static CompoundTag energyTag(long stored) {
        CompoundTag root = new CompoundTag();
        CompoundTag energy = new CompoundTag();
        energy.putLong(STORED_ENERGY, stored);
        root.put(ENERGY, energy);
        return root;
    }

    private static void load(TestMachine machine, CompoundTag tag) {
        machine.loadAdditional(input(tag));
    }

    private static void assertSilent(TestMachine machine) {
        assertAll(
            () -> assertTrue(
                machine.events.isEmpty(),
                "Unexpected change events: " + machine.events
            ),
            () -> assertTrue(machine.observedEnergy.isEmpty())
        );
    }

    private static void assertChange(TestMachine machine, long expectedEnergy) {
        assertAll(
            () -> assertEquals(expectedEnergy, machine.getStoredEnergy()),
            () -> assertEquals(CHANGE, machine.events),
            () -> assertEquals(List.of(expectedEnergy), machine.observedEnergy)
        );
    }

    private static void assertSerializedEnergy(CompoundTag root, long expected) {
        CompoundTag section = assertInstanceOf(CompoundTag.class, root.get(ENERGY));
        LongTag stored = assertInstanceOf(LongTag.class, section.get(STORED_ENERGY));
        assertAll(
            () -> assertEquals(expected, stored.longValue()),
            () -> assertEquals(1, section.size(), "Only stored energy belongs in the FE section"),
            () -> assertFalse(root.contains(STORED_ENERGY), "FE must not be stored at the root")
        );
    }

    private static void assertInvalidRestoration(CompoundTag invalidRoot) {
        TestMachine machine = machine();
        load(machine, energyTag(6_000L));
        assertEquals(6_000L, machine.getStoredEnergy());
        assertSilent(machine);

        load(machine, invalidRoot);
        assertEquals(0L, machine.getStoredEnergy());
        assertEquals(CAPACITY, machine.getEnergyCapacity());
        assertSilent(machine);
    }

    @Test
    void shouldCreateEmptyMachineWithoutWorldOrNotifications() {
        TestMachine machine = machine();
        assertAll(
            () -> assertEquals(0L, machine.getStoredEnergy()),
            () -> assertEquals(CAPACITY, machine.getEnergyCapacity()),
            () -> assertEquals(POSITION, machine.getBlockPos()),
            () -> assertSame(BlockEntityType.FURNACE, machine.getType()),
            () -> assertSame(Blocks.FURNACE.defaultBlockState(), machine.getBlockState()),
            () -> assertNull(machine.getLevel())
        );
        assertSilent(machine);
    }

    @Test
    void shouldRejectNonPositiveCapacity() {
        for (long capacity : new long[]{0L, -1L, Long.MIN_VALUE}) {
            assertThrows(IllegalArgumentException.class, () -> new TestMachine(capacity));
        }
    }

    @Test
    void shouldSupportMaximumLongCapacity() {
        TestMachine machine = new TestMachine(Long.MAX_VALUE);
        assertEquals(Long.MAX_VALUE, machine.getEnergyCapacity());
        assertEquals(0L, machine.getStoredEnergy());
        assertSilent(machine);
    }

    @Test
    void shouldExposeSameComponentAndDelegateStateReads() {
        TestMachine machine = machine();
        EnergyComponent component = machine.component();
        assertSame(component, machine.component());
        assertEquals(CAPACITY, component.getCapacity());
        assertEquals(1_234, component.storeProducedEnergy(1_234));
        assertEquals(component.getStoredEnergy(), machine.getStoredEnergy());
        assertSame(component, machine.component());
        assertChange(machine, 1_234L);
    }

    @Test
    void shouldKeepEnergyOfDifferentMachinesIndependent() {
        TestMachine first = machine();
        TestMachine second = machine();
        assertNotSame(first.component(), second.component());
        first.component().storeProducedEnergy(900);
        assertChange(first, 900L);
        assertEquals(0L, second.getStoredEnergy());
        assertSilent(second);
    }

    @Test
    void shouldNotifyImmediatelyOnProductionInCorrectOrder() {
        TestMachine machine = machine();
        assertEquals(600, machine.component().storeProducedEnergy(600));
        assertChange(machine, 600L);
    }

    @Test
    void shouldNotifyOnlyForAcceptedProduction() {
        TestMachine machine = machine();
        load(machine, energyTag(9_900L));
        assertEquals(100, machine.component().storeProducedEnergy(300));
        assertChange(machine, CAPACITY);
        machine.clearEvents();
        assertEquals(0, machine.component().storeProducedEnergy(300));
        assertEquals(0, machine.component().storeProducedEnergy(0));
        assertSilent(machine);
    }

    @Test
    void shouldNotifyAfterCommittedInsertion() {
        TestMachine machine = machine();
        try (Transaction tx = Transaction.openRoot()) {
            assertEquals(800, machine.component().insertEnergy(800, tx));
            assertEquals(800L, machine.getStoredEnergy());
            assertSilent(machine);
            tx.commit();
        }
        assertChange(machine, 800L);
    }

    @Test
    void shouldNotNotifyOnRolledBackInsertion() {
        TestMachine machine = machine();
        try (Transaction tx = Transaction.openRoot()) {
            assertEquals(800, machine.component().insertEnergy(800, tx));
            assertSilent(machine);
        }
        assertEquals(0L, machine.getStoredEnergy());
        assertSilent(machine);
    }

    @Test
    void shouldNotifyAfterCommittedExtraction() {
        TestMachine machine = machine();
        load(machine, energyTag(6_000L));
        try (Transaction tx = Transaction.openRoot()) {
            assertEquals(1_000, machine.component().extractEnergy(1_000, tx));
            assertSilent(machine);
            tx.commit();
        }
        assertChange(machine, 5_000L);
    }

    @Test
    void shouldNotNotifyOnRolledBackExtraction() {
        TestMachine machine = machine();
        load(machine, energyTag(6_000L));
        try (Transaction tx = Transaction.openRoot()) {
            assertEquals(1_000, machine.component().extractEnergy(1_000, tx));
            assertSilent(machine);
        }
        assertEquals(6_000L, machine.getStoredEnergy());
        assertSilent(machine);
    }

    @Test
    void shouldNotifyAfterCommittedWorkConsumption() {
        TestMachine machine = machine();
        load(machine, energyTag(6_000L));
        try (Transaction tx = Transaction.openRoot()) {
            assertTrue(machine.component().tryConsumeForWork(500, tx));
            assertSilent(machine);
            tx.commit();
        }
        assertChange(machine, 5_500L);
    }

    @Test
    void shouldNotNotifyOnRolledBackWorkConsumption() {
        TestMachine machine = machine();
        load(machine, energyTag(6_000L));
        try (Transaction tx = Transaction.openRoot()) {
            assertTrue(machine.component().tryConsumeForWork(500, tx));
            assertSilent(machine);
        }
        assertEquals(6_000L, machine.getStoredEnergy());
        assertSilent(machine);
    }

    @Test
    void shouldNotifyOnceForSeveralChangesInOneTransaction() {
        TestMachine machine = machine();
        load(machine, energyTag(1_000L));
        try (Transaction tx = Transaction.openRoot()) {
            assertEquals(500, machine.component().insertEnergy(500, tx));
            assertEquals(200, machine.component().extractEnergy(200, tx));
            assertTrue(machine.component().tryConsumeForWork(100, tx));
            assertSilent(machine);
            tx.commit();
        }
        assertChange(machine, 1_200L);
    }

    @Test
    void shouldNotNotifyWhenCommittedNetChangeIsZero() {
        TestMachine machine = machine();
        load(machine, energyTag(1_000L));
        try (Transaction tx = Transaction.openRoot()) {
            assertEquals(500, machine.component().insertEnergy(500, tx));
            assertEquals(500, machine.component().extractEnergy(500, tx));
            tx.commit();
        }
        assertEquals(1_000L, machine.getStoredEnergy());
        assertSilent(machine);
    }

    @Test
    void shouldNotNotifyWhenOperationsCannotChangeEnergy() {
        TestMachine machine = machine();
        try (Transaction tx = Transaction.openRoot()) {
            assertEquals(0, machine.component().insertEnergy(0, tx));
            assertEquals(0, machine.component().extractEnergy(100, tx));
            assertFalse(machine.component().tryConsumeForWork(100, tx));
            assertTrue(machine.component().tryConsumeForWork(0, tx));
            tx.commit();
        }
        assertEquals(0L, machine.getStoredEnergy());
        assertSilent(machine);
    }

    @Test
    void shouldSaveEmptyEnergyAsLongTag() {
        TestMachine machine = machine();
        assertSerializedEnergy(save(machine), 0L);
        assertSilent(machine);
    }

    @Test
    void shouldSaveStoredEnergyInNestedSection() {
        TestMachine machine = machine();
        load(machine, energyTag(6_000L));
        assertSerializedEnergy(save(machine), 6_000L);
        assertSilent(machine);
    }

    @Test
    void shouldSaveEnergyAboveIntegerRangeWithoutTruncation() {
        TestMachine machine = new TestMachine(Long.MAX_VALUE);
        long stored = 4_294_967_297L;
        load(machine, energyTag(stored));
        assertSerializedEnergy(save(machine), stored);
        assertSilent(machine);
    }

    @Test
    void shouldRestoreValidEnergySilently() {
        TestMachine machine = machine();
        load(machine, energyTag(6_000L));
        assertEquals(6_000L, machine.getStoredEnergy());
        assertEquals(CAPACITY, machine.getEnergyCapacity());
        assertSilent(machine);
        load(machine, energyTag(2_000L));
        assertEquals(2_000L, machine.getStoredEnergy());
        assertSilent(machine);
    }

    @Test
    void shouldRestoreLargeLongValuesWithoutIntegerTruncation() {
        TestMachine machine = new TestMachine(Long.MAX_VALUE);
        for (long stored : new long[]{Integer.MAX_VALUE, (long) Integer.MAX_VALUE +
            1L, 3_000_000_000L, 4_294_967_296L, 4_294_967_297L, Long.MAX_VALUE}) {
            load(machine, energyTag(stored));
            assertEquals(stored, machine.getStoredEnergy(), "LongTag value: " + stored);
            assertSilent(machine);
        }
    }

    @Test
    void shouldResetToZeroWhenEnergySectionIsAbsent() {
        assertInvalidRestoration(new CompoundTag());
    }

    @Test
    void shouldResetToZeroWhenStoredEnergyFieldIsAbsent() {
        CompoundTag root = new CompoundTag();
        root.put(ENERGY, new CompoundTag());
        assertInvalidRestoration(root);
    }

    @Test
    void shouldRejectMalformedEnergySections() {
        CompoundTag number = new CompoundTag();
        number.putLong(ENERGY, 6_000L);
        CompoundTag string = new CompoundTag();
        string.putString(ENERGY, "6000");
        CompoundTag list = new CompoundTag();
        list.put(ENERGY, new ListTag());
        assertInvalidRestoration(number);
        assertInvalidRestoration(string);
        assertInvalidRestoration(list);
    }

    @Test
    void shouldRejectAllOtherNumericNbtTypes() {
        List<Consumer<CompoundTag>> writers = List.of(
            tag -> tag.putByte(STORED_ENERGY, (byte) 60),
            tag -> tag.putShort(STORED_ENERGY, (short) 6_000),
            tag -> tag.putInt(STORED_ENERGY, 6_000),
            tag -> tag.putFloat(STORED_ENERGY, 6_000.0F),
            tag -> tag.putDouble(STORED_ENERGY, 6_000.5D)
        );
        for (Consumer<CompoundTag> writer : writers) {
            CompoundTag section = new CompoundTag();
            writer.accept(section);
            CompoundTag root = new CompoundTag();
            root.put(ENERGY, section);
            assertInvalidRestoration(root);
        }
    }

    @Test
    void shouldRejectNonNumericNbtTypes() {
        List<Consumer<CompoundTag>> writers = List.of(
            tag -> tag.putString(STORED_ENERGY, "6000"),
            tag -> tag.put(STORED_ENERGY, new CompoundTag()),
            tag -> tag.put(STORED_ENERGY, new ListTag()),
            tag -> tag.putIntArray(STORED_ENERGY, new int[]{6_000}),
            tag -> tag.putLongArray(STORED_ENERGY, new long[]{6_000L}),
            tag -> tag.putByteArray(STORED_ENERGY, new byte[]{60})
        );
        for (Consumer<CompoundTag> writer : writers) {
            CompoundTag section = new CompoundTag();
            writer.accept(section);
            CompoundTag root = new CompoundTag();
            root.put(ENERGY, section);
            assertInvalidRestoration(root);
        }
    }

    @Test
    void shouldNotReadLegacyOrRootLevelEnergyKeys() {
        CompoundTag root = new CompoundTag();
        root.putLong(STORED_ENERGY, 5_000L);
        root.putLong("energyStored", 6_000L);
        root.putLong("energy_stored", 7_000L);
        assertInvalidRestoration(root);
    }

    @Test
    void shouldClampNegativeEnergyToZeroSilently() {
        TestMachine machine = machine();
        for (long invalid : new long[]{-1L, -500L, Long.MIN_VALUE}) {
            load(machine, energyTag(6_000L));
            load(machine, energyTag(invalid));
            assertEquals(0L, machine.getStoredEnergy());
            assertSilent(machine);
        }
    }

    @Test
    void shouldClampEnergyAboveCapacitySilently() {
        TestMachine machine = machine();
        for (long excess : new long[]{CAPACITY + 1L, 15_000L, Long.MAX_VALUE}) {
            load(machine, energyTag(excess));
            assertEquals(CAPACITY, machine.getStoredEnergy());
            assertSilent(machine);
        }
    }

    @Test
    void shouldUseCurrentCapacityWhenLoadingOldState() {
        TestMachine source = new TestMachine(20_000L);
        load(source, energyTag(15_000L));
        CompoundTag saved = save(source);
        TestMachine smaller = new TestMachine(10_000L);
        TestMachine larger = new TestMachine(30_000L);
        load(smaller, saved);
        load(larger, saved);
        assertAll(
            () -> assertEquals(10_000L, smaller.getEnergyCapacity()),
            () -> assertEquals(10_000L, smaller.getStoredEnergy()),
            () -> assertEquals(30_000L, larger.getEnergyCapacity()),
            () -> assertEquals(15_000L, larger.getStoredEnergy())
        );
        assertSilent(source);
        assertSilent(smaller);
        assertSilent(larger);
    }

    @Test
    void shouldPreserveEnergyAcrossSaveLoadRoundTrip() {
        for (long amount : new long[]{0L, 1L, 6_000L, CAPACITY, 3_000_000_000L, Long.MAX_VALUE}) {
            TestMachine source = new TestMachine(Long.MAX_VALUE);
            load(source, energyTag(amount));
            CompoundTag tag = save(source);
            assertSerializedEnergy(tag, amount);
            TestMachine target = new TestMachine(Long.MAX_VALUE);
            load(target, tag);
            assertEquals(amount, target.getStoredEnergy());
            assertSilent(source);
            assertSilent(target);
        }
    }

    @Test
    void shouldPreserveParentPersistentDataWhenSaving() {
        TestMachine machine = machine();
        load(machine, energyTag(6_000L));
        machine.getPersistentData().putString("test_marker", "parent-state");
        CompoundTag saved = save(machine);
        assertSerializedEnergy(saved, 6_000L);
        CompoundTag parent = assertInstanceOf(CompoundTag.class, saved.get("NeoForgeData"));
        CompoundTag expected = new CompoundTag();
        expected.putString("test_marker", "parent-state");
        assertEquals(expected, parent);
    }

    @Test
    void shouldRestoreParentPersistentDataWhenLoading() {
        TestMachine machine = machine();
        CompoundTag saved = energyTag(6_000L);
        CompoundTag parent = new CompoundTag();
        parent.putString("test_marker", "parent-state");
        saved.put("NeoForgeData", parent);
        load(machine, saved);
        assertEquals(parent, machine.getPersistentData());
        assertEquals(6_000L, machine.getStoredEnergy());
        assertSilent(machine);
    }

    @Test
    void shouldResumeNotificationsAfterSilentRestoration() {
        TestMachine machine = machine();
        EnergyComponent original = machine.component();
        load(machine, energyTag(6_000L));
        assertSame(original, machine.component());
        assertSilent(machine);
        assertEquals(200, machine.component().storeProducedEnergy(200));
        assertChange(machine, 6_200L);
    }
}
