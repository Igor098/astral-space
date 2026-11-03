package ru.greatdragonx.astralspace.common.energy;

import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.Test;
import ru.greatdragonx.astralspace.common.energy.port.EnergyPortId;

import java.lang.reflect.Field;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class EnergyDistributionManagerTest {
    private static final EnergyPortId PORT_DOWN = new EnergyPortId("test.down");
    private static final EnergyPortId PORT_UP = new EnergyPortId("test.up");
    private static final EnergyPortId PORT_NORTH = new EnergyPortId("test.north");
    private static final EnergyPortId PORT_SOUTH = new EnergyPortId("test.south");
    private static final EnergyPortId PORT_WEST = new EnergyPortId("test.west");
    private static final EnergyPortId PORT_EAST = new EnergyPortId("test.east");

    private static final List<EnergyPortId> LEGACY_PORT_ORDER = List.of(
        PORT_DOWN,
        PORT_UP,
        PORT_NORTH,
        PORT_SOUTH,
        PORT_WEST,
        PORT_EAST
    );

    private static @NonNull EnergyDistributionManager newManager(int maxTransferPerTick) {
        EnergyDistributionManager manager = new EnergyDistributionManager(maxTransferPerTick);
        manager.updateAvailablePorts(LEGACY_PORT_ORDER);
        return manager;
    }

    private static @NonNull EnergyDistributionManager managerWithPorts(EnergyPortId... ports) {
        EnergyDistributionManager manager = new EnergyDistributionManager(80);
        manager.updateAvailablePorts(List.of(ports));
        return manager;
    }

    private static void recordCommittedRequest(
        @NonNull EnergyDistributionManager manager,
        @NonNull EnergyPortId portId,
        int requested,
        long currentTick
    )
    {
        try (Transaction tx = Transaction.openRoot()) {
            manager.recordRequest(
                portId,
                requested,
                currentTick,
                tx
            );
            tx.commit();
        }
    }

    private static int getCommittedAdaptiveAmount(
        @NonNull EnergyDistributionManager manager,
        @NonNull EnergyPortId portId,
        int requested,
        int remainingGlobalBudget,
        int storedEnergy,
        long currentTick
    )
    {
        try (Transaction tx = Transaction.openRoot()) {
            int allowed = manager.getAllowedAdaptiveAmount(
                portId,
                requested,
                remainingGlobalBudget,
                storedEnergy,
                currentTick,
                tx
            );
            tx.commit();
            return allowed;
        }
    }

    private static @NonNull EnergyDistributionManager managerWithThreePortCredits()
        throws ReflectiveOperationException
    {
        EnergyDistributionManager manager = newManager(80);

        for (long tick = 99; tick <= 100; tick++) {
            recordCommittedRequest(
                manager,
                PORT_NORTH,
                80,
                tick
            );
            recordCommittedRequest(
                manager,
                PORT_SOUTH,
                80,
                tick
            );
            recordCommittedRequest(
                manager,
                PORT_EAST,
                80,
                tick
            );
        }

        try (Transaction tx = Transaction.openRoot()) {
            manager.recordExtraction(
                PORT_NORTH,
                30,
                100,
                tx
            );
            manager.recordExtraction(
                PORT_SOUTH,
                10,
                100,
                tx
            );
            tx.commit();
        }

        getCommittedAdaptiveAmount(
            manager,
            PORT_NORTH,
            80,
            80,
            80,
            101
        );

        assertTrue(fairnessCredits(manager).getOrDefault(
            PORT_EAST,
            0L
        ) > 0);

        for (long tick : new long[]{110, 119}) {
            recordCommittedRequest(
                manager,
                PORT_NORTH,
                80,
                tick
            );
            recordCommittedRequest(
                manager,
                PORT_SOUTH,
                80,
                tick
            );
        }

        assertFalse(manager.isActive(
            PORT_EAST,
            120
        ));
        assertEquals(
            2,
            manager.countActivePorts(120)
        );

        return manager;
    }

    /**
     * Получает внутренний список доступных портов.
     * <p>
     * Используется только в тестах, чтобы не добавлять
     * в production API метод для чтения внутреннего состояния.
     */
    @SuppressWarnings("unchecked")
    private static List<EnergyPortId> getAvailablePorts(
        EnergyDistributionManager manager
    ) throws ReflectiveOperationException
    {
        Field field = EnergyDistributionManager.class.getDeclaredField("availablePorts");

        field.setAccessible(true);

        return (List<EnergyPortId>) field.get(manager);
    }

    @Test
    void shouldPreservePortOrder() throws ReflectiveOperationException {
        EnergyDistributionManager manager = new EnergyDistributionManager(80);

        EnergyPortId portA = new EnergyPortId("a");
        EnergyPortId portB = new EnergyPortId("b");
        EnergyPortId portC = new EnergyPortId("c");

        List<EnergyPortId> expected = List.of(
            portB,
            portA,
            portC
        );

        manager.updateAvailablePorts(expected);

        assertEquals(
            expected,
            getAvailablePorts(manager)
        );
    }

    @Test
    void shouldRejectDuplicatePortsWithoutChangingConfiguration()
        throws ReflectiveOperationException
    {

        EnergyDistributionManager manager = new EnergyDistributionManager(80);

        EnergyPortId portA = new EnergyPortId("a");
        EnergyPortId portB = new EnergyPortId("b");

        List<EnergyPortId> initialPorts = List.of(
            portA,
            portB
        );

        manager.updateAvailablePorts(initialPorts);

        assertThrows(
            IllegalArgumentException.class,
            () -> manager.updateAvailablePorts(List.of(
                new EnergyPortId("a"),
                new EnergyPortId("a")
            ))
        );

        assertEquals(
            initialPorts,
            getAvailablePorts(manager)
        );
    }

    @Test
    void shouldNotBeAffectedByOriginalListMutation() throws ReflectiveOperationException {

        EnergyDistributionManager manager = new EnergyDistributionManager(80);

        EnergyPortId portA = new EnergyPortId("a");
        EnergyPortId portB = new EnergyPortId("b");

        List<EnergyPortId> source = new ArrayList<>();
        source.add(portA);
        source.add(portB);

        manager.updateAvailablePorts(source);

        source.clear();

        assertEquals(
            List.of(
                portA,
                portB
            ),
            getAvailablePorts(manager)
        );
    }

    @Test
    void shouldStoreImmutableList() throws ReflectiveOperationException {
        EnergyDistributionManager manager = new EnergyDistributionManager(80);

        manager.updateAvailablePorts(List.of(new EnergyPortId("a")));

        List<EnergyPortId> stored = getAvailablePorts(manager);

        assertThrows(
            UnsupportedOperationException.class,
            () -> stored.add(new EnergyPortId("b"))
        );
    }

    @Test
    void shouldRejectNullArgumentWithoutChangingConfiguration() throws ReflectiveOperationException
    {

        EnergyDistributionManager manager = new EnergyDistributionManager(80);

        List<EnergyPortId> initialPorts = List.of(new EnergyPortId("a"));

        manager.updateAvailablePorts(initialPorts);

        assertThrows(
            NullPointerException.class,
            () -> manager.updateAvailablePorts(null)
        );

        assertEquals(
            initialPorts,
            getAvailablePorts(manager)
        );
    }

    @Test
    void shouldRejectNullElementWithoutChangingConfiguration() throws ReflectiveOperationException {

        EnergyDistributionManager manager = new EnergyDistributionManager(80);

        List<EnergyPortId> initialPorts = List.of(new EnergyPortId("a"));

        manager.updateAvailablePorts(initialPorts);

        List<EnergyPortId> invalidPorts = new ArrayList<>();
        invalidPorts.add(new EnergyPortId("b"));
        invalidPorts.add(null);

        assertThrows(
            NullPointerException.class,
            () -> manager.updateAvailablePorts(invalidPorts)
        );

        assertEquals(
            initialPorts,
            getAvailablePorts(manager)
        );
    }

    @Test
    void shouldAllowEmptyPortList() throws ReflectiveOperationException {
        EnergyDistributionManager manager = new EnergyDistributionManager(80);

        manager.updateAvailablePorts(List.of(new EnergyPortId("a")));

        manager.updateAvailablePorts(List.of());

        assertTrue(getAvailablePorts(manager).isEmpty());
    }

    @Test
    void shouldAllowRepeatedUpdateWithSamePorts() throws ReflectiveOperationException {

        EnergyDistributionManager manager = new EnergyDistributionManager(80);

        List<EnergyPortId> ports = List.of(
            new EnergyPortId("a"),
            new EnergyPortId("b")
        );

        manager.updateAvailablePorts(ports);
        manager.updateAvailablePorts(ports);

        assertEquals(
            ports,
            getAvailablePorts(manager)
        );
    }


    @Test
    void shouldNotReplaceAvailablePortsWhenConfigurationIsUnchanged()
        throws ReflectiveOperationException
    {

        EnergyDistributionManager manager = new EnergyDistributionManager(80);

        List<EnergyPortId> initialPorts = List.of(
            new EnergyPortId("back"),
            new EnergyPortId("left"),
            new EnergyPortId("right")
        );

        manager.updateAvailablePorts(initialPorts);

        List<EnergyPortId> previous = getAvailablePorts(manager);

        manager.updateAvailablePorts(new ArrayList<>(initialPorts));

        assertSame(
            previous,
            getAvailablePorts(manager),
            "При неизменной конфигурации список не должен заменяться"
        );
    }

    @Test
    void shouldResetPortActivityAfterDisableAndReEnable() {
        EnergyDistributionManager manager = new EnergyDistributionManager(80);

        EnergyPortId portA = new EnergyPortId("a");
        EnergyPortId portB = new EnergyPortId("b");

        manager.updateAvailablePorts(List.of(
            portA,
            portB
        ));

        try (Transaction tx = Transaction.openRoot()) {
            manager.recordRequest(
                portA,
                80,
                100L,
                tx
            );
            tx.commit();
        }

        assertTrue(manager.isActive(
            portA,
            100L
        ));

        manager.updateAvailablePorts(List.of(portB));

        assertFalse(manager.isActive(
            portA,
            100L
        ));

        manager.updateAvailablePorts(List.of(
            portA,
            portB
        ));

        assertFalse(
            manager.isActive(
                portA,
                101L
            ),
            "Повторно включённый порт не должен наследовать старую активность"
        );

        try (Transaction tx = Transaction.openRoot()) {
            manager.recordRequest(
                portA,
                40,
                101L,
                tx
            );
            tx.commit();
        }

        assertTrue(manager.isActive(
            portA,
            101L
        ));
    }

    @Test
    void shouldTrackActivePort() {
        EnergyDistributionManager manager = newManager(80);

        recordCommittedRequest(
            manager,
            PORT_NORTH,
            80,
            100
        );

        assertTrue(manager.isActive(
            PORT_NORTH,
            100
        ));
        assertTrue(manager.isActive(
            PORT_NORTH,
            119
        ));
        assertFalse(manager.isActive(
            PORT_NORTH,
            120
        ));

        assertFalse(manager.isActive(
            PORT_SOUTH,
            100
        ));

        recordCommittedRequest(
            manager,
            PORT_EAST,
            0,
            100
        );

        assertFalse(manager.isActive(
            PORT_EAST,
            100
        ));
    }

    @Test
    void shouldCountActivePorts() {
        EnergyDistributionManager manager = newManager(80);
        assertEquals(
            0,
            manager.countActivePorts(100)
        );

        recordCommittedRequest(
            manager,
            PORT_NORTH,
            80,
            100
        );

        assertEquals(
            1,
            manager.countActivePorts(100)
        );

        recordCommittedRequest(
            manager,
            PORT_SOUTH,
            80,
            105
        );

        assertEquals(
            2,
            manager.countActivePorts(105)
        );

        assertTrue(manager.isActive(
            PORT_NORTH,
            119
        ));
        assertTrue(manager.isActive(
            PORT_SOUTH,
            119
        ));
        assertEquals(
            2,
            manager.countActivePorts(119)
        );

        assertFalse(manager.isActive(
            PORT_NORTH,
            120
        ));
        assertEquals(
            1,
            manager.countActivePorts(120)
        );

        assertFalse(manager.isActive(
            PORT_SOUTH,
            125
        ));
        assertEquals(
            0,
            manager.countActivePorts(125)
        );
    }

    @Test
    void shouldCalculateBaseQuota() {
        EnergyDistributionManager manager = newManager(80);

        recordCommittedRequest(
            manager,
            PORT_NORTH,
            80,
            100
        );

        assertEquals(
            80,
            manager.getBaseQuota(
                PORT_NORTH,
                100
            )
        );
        assertEquals(
            0,
            manager.getBaseQuota(
                PORT_SOUTH,
                100
            )
        );

        recordCommittedRequest(
            manager,
            PORT_SOUTH,
            40,
            105
        );
        assertEquals(
            40,
            manager.getBaseQuota(
                PORT_NORTH,
                105
            )
        );
        assertEquals(
            40,
            manager.getBaseQuota(
                PORT_SOUTH,
                105
            )
        );

        recordCommittedRequest(
            manager,
            PORT_EAST,
            20,
            108
        );
        assertEquals(
            26,
            manager.getBaseQuota(
                PORT_NORTH,
                108
            )
        );
        assertEquals(
            26,
            manager.getBaseQuota(
                PORT_SOUTH,
                108
            )
        );
        assertEquals(
            26,
            manager.getBaseQuota(
                PORT_EAST,
                108
            )
        );
    }

    @Test
    void shouldRestorePortUsageAfterRollback() {
        EnergyDistributionManager manager = newManager(80);

        try (Transaction tx = Transaction.openRoot()) {
            manager.recordExtraction(
                PORT_NORTH,
                30,
                100,
                tx
            );
            assertEquals(
                30,
                manager.getExtractedByPort(
                    PORT_NORTH,
                    100
                )
            );

            tx.commit();
        }

        assertEquals(
            30,
            manager.getExtractedByPort(
                PORT_NORTH,
                100
            )
        );

        try (Transaction tx = Transaction.openRoot()) {
            manager.recordExtraction(
                PORT_NORTH,
                20,
                100,
                tx
            );
            assertEquals(
                50,
                manager.getExtractedByPort(
                    PORT_NORTH,
                    100
                )
            );
        }

        assertEquals(
            30,
            manager.getExtractedByPort(
                PORT_NORTH,
                100
            )
        );

        assertEquals(
            0,
            manager.getExtractedByPort(
                PORT_NORTH,
                101
            )
        );
    }

    @Test
    void shouldRestoreMultiplePortsAfterNestedRollback() {
        EnergyDistributionManager manager = newManager(80);

        try (Transaction root = Transaction.openRoot()) {
            manager.recordExtraction(
                PORT_NORTH,
                30,
                100,
                root
            );
            assertEquals(
                30,
                manager.getExtractedByPort(
                    PORT_NORTH,
                    100
                )
            );

            manager.recordExtraction(
                PORT_SOUTH,
                20,
                100,
                root
            );
            assertEquals(
                20,
                manager.getExtractedByPort(
                    PORT_SOUTH,
                    100
                )
            );

            try (Transaction nested = Transaction.open(root)) {
                manager.recordExtraction(
                    PORT_NORTH,
                    10,
                    100,
                    nested
                );
                manager.recordExtraction(
                    PORT_EAST,
                    15,
                    100,
                    nested
                );

                assertEquals(
                    40,
                    manager.getExtractedByPort(
                        PORT_NORTH,
                        100
                    )
                );
                assertEquals(
                    15,
                    manager.getExtractedByPort(
                        PORT_EAST,
                        100
                    )
                );
            }

            assertEquals(
                30,
                manager.getExtractedByPort(
                    PORT_NORTH,
                    100
                )
            );
            assertEquals(
                20,
                manager.getExtractedByPort(
                    PORT_SOUTH,
                    100
                )
            );
            assertEquals(
                0,
                manager.getExtractedByPort(
                    PORT_EAST,
                    100
                )
            );

            root.commit();
        }

        assertEquals(
            30,
            manager.getExtractedByPort(
                PORT_NORTH,
                100
            )
        );
        assertEquals(
            20,
            manager.getExtractedByPort(
                PORT_SOUTH,
                100
            )
        );
        assertEquals(
            0,
            manager.getExtractedByPort(
                PORT_EAST,
                100
            )
        );
    }

    @Test
    void shouldCalculateRemainingBaseQuota() {
        EnergyDistributionManager manager = newManager(80);

        recordCommittedRequest(
            manager,
            PORT_NORTH,
            80,
            100
        );

        recordCommittedRequest(
            manager,
            PORT_SOUTH,
            80,
            100
        );

        assertEquals(
            40,
            manager.getBaseQuota(
                PORT_NORTH,
                100
            )
        );
        assertEquals(
            40,
            manager.getBaseQuota(
                PORT_SOUTH,
                100
            )
        );

        try (Transaction tx = Transaction.openRoot()) {
            manager.recordExtraction(
                PORT_NORTH,
                25,
                100,
                tx
            );
            assertEquals(
                15,
                manager.getRemainingBaseQuota(
                    PORT_NORTH,
                    100
                )
            );
            assertEquals(
                40,
                manager.getRemainingBaseQuota(
                    PORT_SOUTH,
                    100
                )
            );

            manager.recordExtraction(
                PORT_NORTH,
                20,
                100,
                tx
            );
            assertEquals(
                0,
                manager.getRemainingBaseQuota(
                    PORT_NORTH,
                    100
                )
            );
            assertEquals(
                40,
                manager.getRemainingBaseQuota(
                    PORT_SOUTH,
                    100
                )
            );

            tx.commit();
        }

        assertEquals(
            0,
            manager.getRemainingBaseQuota(
                PORT_NORTH,
                100
            )
        );
        assertEquals(
            40,
            manager.getRemainingBaseQuota(
                PORT_SOUTH,
                100
            )
        );

        assertEquals(
            40,
            manager.getRemainingBaseQuota(
                PORT_NORTH,
                101
            )
        );
        assertEquals(
            40,
            manager.getRemainingBaseQuota(
                PORT_SOUTH,
                101
            )
        );
    }

    @Test
    void shouldLimitRequestByRemainingBaseQuota() {
        EnergyDistributionManager manager = newManager(80);

        recordCommittedRequest(
            manager,
            PORT_NORTH,
            80,
            100
        );

        recordCommittedRequest(
            manager,
            PORT_SOUTH,
            80,
            100
        );

        int allowed = manager.getAllowedBaseAmount(
            PORT_NORTH,
            60,
            100
        );
        assertEquals(
            40,
            allowed
        );

        try (Transaction tx = Transaction.openRoot()) {
            manager.recordExtraction(
                PORT_NORTH,
                25,
                100,
                tx
            );

            tx.commit();
        }

        assertEquals(
            15,
            manager.getAllowedBaseAmount(
                PORT_NORTH,
                50,
                100
            )
        );

        assertEquals(
            10,
            manager.getAllowedBaseAmount(
                PORT_NORTH,
                10,
                100
            )
        );

        assertEquals(
            40,
            manager.getAllowedBaseAmount(
                PORT_SOUTH,
                80,
                100
            )
        );

        assertEquals(
            0,
            manager.getAllowedBaseAmount(
                PORT_EAST,
                80,
                100
            )
        );

        assertEquals(
            0,
            manager.getAllowedBaseAmount(
                PORT_NORTH,
                0,
                100
            )
        );
    }

    @Test
    void shouldReadPreviousTickUsage() {
        EnergyDistributionManager manager = newManager(80);

        try (Transaction tx = Transaction.openRoot()) {
            manager.recordExtraction(
                PORT_NORTH,
                30,
                100,
                tx
            );

            tx.commit();
        }

        assertEquals(
            30,
            manager.getPreviousExtractedByPort(
                PORT_NORTH,
                101
            )
        );

        try (Transaction tx = Transaction.openRoot()) {
            manager.recordExtraction(
                PORT_NORTH,
                20,
                101,
                tx
            );

            tx.commit();
        }

        assertEquals(
            30,
            manager.getPreviousExtractedByPort(
                PORT_NORTH,
                101
            )
        );

        assertEquals(
            20,
            manager.getPreviousExtractedByPort(
                PORT_NORTH,
                102
            )
        );

        assertEquals(
            0,
            manager.getPreviousExtractedByPort(
                PORT_NORTH,
                105
            )
        );
    }

    @Test
    void shouldRestoreUsageHistoryAfterRollback() {
        EnergyDistributionManager manager = newManager(80);

        try (Transaction tx = Transaction.openRoot()) {
            manager.recordExtraction(
                PORT_NORTH,
                30,
                100,
                tx
            );
            assertEquals(
                30,
                manager.getPreviousExtractedByPort(
                    PORT_NORTH,
                    101
                )
            );
            tx.commit();
        }
        assertEquals(
            30,
            manager.getPreviousExtractedByPort(
                PORT_NORTH,
                101
            )
        );

        try (Transaction tx = Transaction.openRoot()) {
            manager.recordExtraction(
                PORT_NORTH,
                20,
                101,
                tx
            );
            assertEquals(
                20,
                manager.getExtractedByPort(
                    PORT_NORTH,
                    101
                )
            );
            assertEquals(
                30,
                manager.getPreviousExtractedByPort(
                    PORT_NORTH,
                    101
                )
            );
        }

        assertEquals(
            30,
            manager.getExtractedByPort(
                PORT_NORTH,
                100
            )
        );

        assertEquals(
            0,
            manager.getExtractedByPort(
                PORT_NORTH,
                101
            )
        );

        assertEquals(
            30,
            manager.getPreviousExtractedByPort(
                PORT_NORTH,
                101
            )
        );
    }

    @Test
    void shouldTrackMaximumDemandPerPort() {
        EnergyDistributionManager manager = newManager(80);

        recordCommittedRequest(
            manager,
            PORT_NORTH,
            20,
            100
        );

        recordCommittedRequest(
            manager,
            PORT_NORTH,
            60,
            100
        );

        recordCommittedRequest(
            manager,
            PORT_NORTH,
            30,
            100
        );

        recordCommittedRequest(
            manager,
            PORT_SOUTH,
            40,
            100
        );

        assertEquals(
            60,
            manager.getMaxRequestedByPort(
                PORT_NORTH,
                100
            )
        );
        assertEquals(
            40,
            manager.getMaxRequestedByPort(
                PORT_SOUTH,
                100
            )
        );
        assertEquals(
            0,
            manager.getMaxRequestedByPort(
                PORT_EAST,
                100
            )
        );
        assertEquals(
            0,
            manager.getMaxRequestedByPort(
                PORT_NORTH,
                101
            )
        );

        recordCommittedRequest(
            manager,
            PORT_NORTH,
            10,
            101
        );
        assertEquals(
            10,
            manager.getMaxRequestedByPort(
                PORT_NORTH,
                101
            )
        );
        assertEquals(
            0,
            manager.getMaxRequestedByPort(
                PORT_SOUTH,
                101
            )
        );
    }

    @Test
    void shouldReadPreviousTickDemand() {
        EnergyDistributionManager manager = newManager(80);

        recordCommittedRequest(
            manager,
            PORT_NORTH,
            80,
            100
        );
        assertEquals(
            80,
            manager.getPreviousMaxRequestedByPort(
                PORT_NORTH,
                101
            )
        );

        recordCommittedRequest(
            manager,
            PORT_SOUTH,
            40,
            100
        );
        assertEquals(
            40,
            manager.getPreviousMaxRequestedByPort(
                PORT_SOUTH,
                101
            )
        );

        recordCommittedRequest(
            manager,
            PORT_NORTH,
            30,
            101
        );
        assertEquals(
            80,
            manager.getPreviousMaxRequestedByPort(
                PORT_NORTH,
                101
            )
        );
        assertEquals(
            40,
            manager.getPreviousMaxRequestedByPort(
                PORT_SOUTH,
                101
            )
        );

        assertEquals(
            30,
            manager.getPreviousMaxRequestedByPort(
                PORT_NORTH,
                102
            )
        );
        assertEquals(
            0,
            manager.getPreviousMaxRequestedByPort(
                PORT_SOUTH,
                102
            )
        );
        assertEquals(
            0,
            manager.getPreviousMaxRequestedByPort(
                PORT_NORTH,
                105
            )
        );
    }

    @Test
    void shouldCalculateProtectedRemainingQuota() {
        EnergyDistributionManager manager = newManager(80);

        recordCommittedRequest(
            manager,
            PORT_NORTH,
            10,
            99
        );
        recordCommittedRequest(
            manager,
            PORT_SOUTH,
            80,
            99
        );

        assertEquals(
            10,
            manager.getProtectedRemainingQuota(
                PORT_NORTH,
                80,
                100
            )
        );

        try (Transaction tx = Transaction.openRoot()) {
            manager.recordExtraction(
                PORT_NORTH,
                5,
                100,
                tx
            );
            assertEquals(
                5,
                manager.getProtectedRemainingQuota(
                    PORT_NORTH,
                    80,
                    100
                )
            );

            manager.recordExtraction(
                PORT_NORTH,
                5,
                100,
                tx
            );

            tx.commit();
        }
        assertEquals(
            0,
            manager.getProtectedRemainingQuota(
                PORT_NORTH,
                80,
                100
            )
        );

        recordCommittedRequest(
            manager,
            PORT_NORTH,
            80,
            100
        );
        assertEquals(
            30,
            manager.getProtectedRemainingQuota(
                PORT_NORTH,
                80,
                100
            )
        );
    }

    @Test
    void shouldReduceProtectedQuotaWithoutRecentDemand() {
        EnergyDistributionManager manager = newManager(80);

        recordCommittedRequest(
            manager,
            PORT_NORTH,
            40,
            98
        );
        recordCommittedRequest(
            manager,
            PORT_SOUTH,
            40,
            98
        );

        assertEquals(
            40,
            manager.getProtectedRemainingQuota(
                PORT_NORTH,
                80,
                100
            )
        );

        try (Transaction tx = Transaction.openRoot()) {
            manager.recordExtraction(
                PORT_NORTH,
                15,
                100,
                tx
            );

            tx.commit();
        }

        assertEquals(
            25,
            manager.getProtectedRemainingQuota(
                PORT_NORTH,
                80,
                100
            )
        );
    }

    @Test
    void shouldBorrowUnusedProtectedQuota() {
        EnergyDistributionManager manager = newManager(80);

        recordCommittedRequest(
            manager,
            PORT_NORTH,
            10,
            99
        );
        recordCommittedRequest(
            manager,
            PORT_SOUTH,
            80,
            99
        );

        recordCommittedRequest(
            manager,
            PORT_SOUTH,
            80,
            100
        );

        assertEquals(
            70,
            getCommittedAdaptiveAmount(
                manager,
                PORT_SOUTH,
                80,
                80,
                80,
                100
            )
        );

        try (Transaction tx = Transaction.openRoot()) {
            manager.recordExtraction(
                PORT_SOUTH,
                70,
                100,
                tx
            );

            tx.commit();
        }

        recordCommittedRequest(
            manager,
            PORT_NORTH,
            10,
            100
        );
        assertEquals(
            10,
            getCommittedAdaptiveAmount(
                manager,
                PORT_NORTH,
                10,
                10,
                10,
                100
            )
        );
    }

    @Test
    void shouldDistributeEnergyInReverseRequestOrder() {
        EnergyDistributionManager manager = newManager(80);

        recordCommittedRequest(
            manager,
            PORT_NORTH,
            10,
            99
        );
        recordCommittedRequest(
            manager,
            PORT_SOUTH,
            80,
            99
        );

        recordCommittedRequest(
            manager,
            PORT_NORTH,
            10,
            100
        );

        int allowed = getCommittedAdaptiveAmount(
            manager,
            PORT_NORTH,
            10,
            80,
            80,
            100
        );

        assertEquals(
            10,
            allowed
        );

        try (Transaction tx = Transaction.openRoot()) {
            manager.recordExtraction(
                PORT_NORTH,
                10,
                100,
                tx
            );

            tx.commit();
        }

        recordCommittedRequest(
            manager,
            PORT_SOUTH,
            80,
            100
        );

        int allowedSouth = getCommittedAdaptiveAmount(
            manager,
            PORT_SOUTH,
            80,
            70,
            70,
            100
        );

        assertEquals(
            70,
            allowedSouth
        );
    }

    @Test
    void shouldRecoverAfterSecondPortBecomesActive() {
        EnergyDistributionManager manager = newManager(80);

        recordCommittedRequest(
            manager,
            PORT_NORTH,
            80,
            100
        );

        assertEquals(
            80,
            getCommittedAdaptiveAmount(
                manager,
                PORT_NORTH,
                80,
                80,
                80,
                100
            )
        );

        try (Transaction tx = Transaction.openRoot()) {
            manager.recordExtraction(
                PORT_NORTH,
                80,
                100,
                tx
            );

            tx.commit();
        }

        recordCommittedRequest(
            manager,
            PORT_SOUTH,
            80,
            100
        );
        assertEquals(
            0,
            getCommittedAdaptiveAmount(
                manager,
                PORT_SOUTH,
                80,
                0,
                0,
                100
            )
        );

        recordCommittedRequest(
            manager,
            PORT_NORTH,
            80,
            101
        );

        assertEquals(
            40,
            getCommittedAdaptiveAmount(
                manager,
                PORT_NORTH,
                80,
                80,
                80,
                101
            )
        );

        try (Transaction tx = Transaction.openRoot()) {
            manager.recordExtraction(
                PORT_NORTH,
                40,
                101,
                tx
            );

            tx.commit();
        }

        recordCommittedRequest(
            manager,
            PORT_SOUTH,
            80,
            101
        );
        assertEquals(
            40,
            getCommittedAdaptiveAmount(
                manager,
                PORT_SOUTH,
                80,
                40,
                40,
                101
            )
        );
    }

    @Test
    void shouldDistributeEnergyWhenBufferIsLow() {
        EnergyDistributionManager manager = newManager(80);

        recordCommittedRequest(
            manager,
            PORT_NORTH,
            80,
            99
        );
        recordCommittedRequest(
            manager,
            PORT_SOUTH,
            80,
            99
        );

        recordCommittedRequest(
            manager,
            PORT_NORTH,
            80,
            100
        );
        int allowedNorth = getCommittedAdaptiveAmount(
            manager,
            PORT_NORTH,
            80,
            80,
            40,
            100
        );
        assertEquals(
            20,
            allowedNorth
        );

        try (Transaction tx = Transaction.openRoot()) {
            manager.recordExtraction(
                PORT_NORTH,
                allowedNorth,
                100,
                tx
            );
            tx.commit();
        }

        recordCommittedRequest(
            manager,
            PORT_SOUTH,
            80,
            100
        );
        int allowedSouth = getCommittedAdaptiveAmount(
            manager,
            PORT_SOUTH,
            80,
            60,
            20,
            100
        );
        assertEquals(
            20,
            allowedSouth
        );

        try (Transaction tx = Transaction.openRoot()) {
            manager.recordExtraction(
                PORT_SOUTH,
                allowedSouth,
                100,
                tx
            );
            tx.commit();
        }

        assertEquals(
            20,
            manager.getExtractedByPort(
                PORT_NORTH,
                100
            )
        );
        assertEquals(
            20,
            manager.getExtractedByPort(
                PORT_SOUTH,
                100
            )
        );
    }

    @Test
    void shouldShareEnergyFairlyWhenProductionOccursBetweenPullRequests() {
        EnergyDistributionManager manager = newManager(80);

        recordCommittedRequest(
            manager,
            PORT_NORTH,
            128,
            99
        );
        recordCommittedRequest(
            manager,
            PORT_SOUTH,
            128,
            99
        );

        int storedEnergy = 20;
        int totalNorth = 0;
        int totalSouth = 0;

        for (long tick = 100; tick < 121; tick++) {
            recordCommittedRequest(
                manager,
                PORT_SOUTH,
                128,
                tick
            );
            int allowedSouth = getCommittedAdaptiveAmount(
                manager,
                PORT_SOUTH,
                128,
                80,
                storedEnergy,
                tick
            );
            int extractedSouth = Math.min(
                allowedSouth,
                storedEnergy
            );

            try (Transaction tx = Transaction.openRoot()) {
                manager.recordExtraction(
                    PORT_SOUTH,
                    extractedSouth,
                    tick,
                    tx
                );
                tx.commit();
            }

            storedEnergy -= extractedSouth;

            if (tick > 100) {
                totalSouth += extractedSouth;
            }

            storedEnergy += 40;

            recordCommittedRequest(
                manager,
                PORT_NORTH,
                128,
                tick
            );
            int allowedNorth = getCommittedAdaptiveAmount(
                manager,
                PORT_NORTH,
                128,
                80 - extractedSouth,
                storedEnergy,
                tick
            );
            int extractedNorth = Math.min(
                allowedNorth,
                storedEnergy
            );

            if (tick > 100) {
                assertEquals(
                    20,
                    extractedSouth
                );
                assertEquals(
                    20,
                    extractedNorth
                );
            }

            try (Transaction tx = Transaction.openRoot()) {
                manager.recordExtraction(
                    PORT_NORTH,
                    extractedNorth,
                    tick,
                    tx
                );
                tx.commit();
            }

            storedEnergy -= extractedNorth;

            if (tick > 100) {
                totalNorth += extractedNorth;
            }
        }

        assertEquals(
            800,
            totalNorth + totalSouth
        );
        assertEquals(
            20,
            storedEnergy
        );

        assertEquals(
            400,
            totalSouth,
            "SOUTH не должен голодать из-за порядка Pull-запросов"
        );
        assertEquals(
            400,
            totalNorth,
            "NORTH не должен получать львиную долю энергии"
        );
    }

    @Test
    void shouldAllowFullExtractionAfterOtherPortDisconnects() {
        EnergyDistributionManager manager = newManager(80);

        recordCommittedRequest(
            manager,
            PORT_NORTH,
            128,
            100
        );
        recordCommittedRequest(
            manager,
            PORT_SOUTH,
            128,
            100
        );

        recordCommittedRequest(
            manager,
            PORT_NORTH,
            128,
            101
        );
        recordCommittedRequest(
            manager,
            PORT_SOUTH,
            128,
            101
        );

        try (Transaction tx = Transaction.openRoot()) {
            manager.recordExtraction(
                PORT_NORTH,
                30,
                101,
                tx
            );
            manager.recordExtraction(
                PORT_SOUTH,
                10,
                101,
                tx
            );
            tx.commit();
        }

        recordCommittedRequest(
            manager,
            PORT_NORTH,
            40,
            102
        );

        getCommittedAdaptiveAmount(
            manager,
            PORT_NORTH,
            40,
            80,
            40,
            102
        );

        recordCommittedRequest(
            manager,
            PORT_NORTH,
            40,
            121
        );

        assertFalse(manager.isActive(
            PORT_SOUTH,
            121
        ));
        assertTrue(manager.isActive(
            PORT_NORTH,
            121
        ));
        assertEquals(
            1,
            manager.countActivePorts(121)
        );

        int allowed = getCommittedAdaptiveAmount(
            manager,
            PORT_NORTH,
            40,
            80,
            40,
            121
        );

        assertEquals(
            40,
            allowed,
            "Единственный потребитель не должен ограничиваться старым fairnessCredits"
        );
    }

    @Test
    void shouldRedistributeEnergyAfterPortReconnects() {
        EnergyDistributionManager manager = newManager(80);

        recordCommittedRequest(
            manager,
            PORT_NORTH,
            128,
            99
        );
        recordCommittedRequest(
            manager,
            PORT_SOUTH,
            128,
            99
        );

        assertEquals(
            10,
            pullEnergy(
                manager,
                PORT_SOUTH,
                100,
                20,
                80
            )
        );
        assertEquals(
            30,
            pullEnergy(
                manager,
                PORT_NORTH,
                100,
                50,
                70
            )
        );

        assertEquals(
            20,
            pullEnergy(
                manager,
                PORT_SOUTH,
                101,
                20,
                80
            )
        );
        assertEquals(
            20,
            pullEnergy(
                manager,
                PORT_NORTH,
                101,
                40,
                60
            )
        );

        for (long tick = 102; tick <= 121; tick++) {
            pullEnergy(
                manager,
                PORT_NORTH,
                tick,
                40,
                80
            );
        }

        assertFalse(manager.isActive(
            PORT_SOUTH,
            122
        ));
        assertEquals(
            1,
            manager.countActivePorts(122)
        );

        assertEquals(
            40,
            pullEnergy(
                manager,
                PORT_NORTH,
                122,
                40,
                80
            )
        );

        assertEquals(
            40,
            pullEnergy(
                manager,
                PORT_NORTH,
                123,
                40,
                80
            )
        );
        assertEquals(
            0,
            pullEnergy(
                manager,
                PORT_SOUTH,
                123,
                0,
                40
            )
        );

        assertEquals(
            40,
            pullEnergy(
                manager,
                PORT_NORTH,
                124,
                40,
                80
            ),
            "NORTH: первый тик возвращения SOUTH"
        );

        assertEquals(
            20,
            pullEnergy(
                manager,
                PORT_SOUTH,
                124,
                20,
                40
            ),
            "SOUTH должен успешно зарегистрироваться при возвращении"
        );
        assertTrue(manager.isActive(
            PORT_SOUTH,
            124
        ));
        assertEquals(
            2,
            manager.countActivePorts(124)
        );

        for (long tick = 125; tick <= 128; tick++) {
            int north = pullEnergy(
                manager,
                PORT_NORTH,
                tick,
                40,
                80
            );
            int south = pullEnergy(
                manager,
                PORT_SOUTH,
                tick,
                20,
                60
            );

            assertEquals(
                20,
                north,
                "NORTH: тик " + tick
            );
            assertEquals(
                20,
                south,
                "SOUTH: тик " + tick
            );
        }
    }

    @Test
    void shouldNotWasteEnergyWhenConsumersHaveDifferentDemand() {
        EnergyDistributionManager manager = newManager(80);

        recordCommittedRequest(
            manager,
            PORT_NORTH,
            10,
            99
        );
        recordCommittedRequest(
            manager,
            PORT_SOUTH,
            128,
            99
        );

        assertEquals(
            10,
            pullEnergy(
                manager,
                PORT_NORTH,
                10,
                100,
                40,
                80
            )
        );

        assertEquals(
            30,
            pullEnergy(
                manager,
                PORT_SOUTH,
                128,
                100,
                30,
                70
            )
        );

        assertEquals(
            10,
            pullEnergy(
                manager,
                PORT_NORTH,
                10,
                101,
                40,
                80
            )
        );

        assertEquals(
            30,
            pullEnergy(
                manager,
                PORT_SOUTH,
                128,
                101,
                30,
                70
            )
        );
    }

    @Test
    void shouldRollbackRequestAfterRootTransactionIsAborted() {
        EnergyDistributionManager manager = newManager(80);

        try (Transaction tx = Transaction.openRoot()) {
            manager.recordRequest(
                PORT_NORTH,
                64,
                100,
                tx
            );

            assertTrue(manager.isActive(
                PORT_NORTH,
                100
            ));
            assertEquals(
                1,
                manager.countActivePorts(100)
            );
            assertEquals(
                64,
                manager.getMaxRequestedByPort(
                    PORT_NORTH,
                    100
                )
            );
        }

        assertFalse(manager.isActive(
            PORT_NORTH,
            100
        ));
        assertEquals(
            0,
            manager.countActivePorts(100)
        );
        assertEquals(
            0,
            manager.getMaxRequestedByPort(
                PORT_NORTH,
                100
            )
        );
        assertEquals(
            0,
            manager.getPreviousMaxRequestedByPort(
                PORT_NORTH,
                101
            )
        );
    }

    @Test
    void shouldRollbackSimulatedRequestWithoutUndoingParentRequest() {
        EnergyDistributionManager manager = newManager(80);

        try (Transaction root = Transaction.openRoot()) {
            manager.recordRequest(
                PORT_NORTH,
                30,
                100,
                root
            );

            try (Transaction simulated = Transaction.open(root)) {
                manager.recordRequest(
                    PORT_NORTH,
                    80,
                    100,
                    simulated
                );
                manager.recordRequest(
                    PORT_SOUTH,
                    128,
                    100,
                    simulated
                );

                assertEquals(
                    80,
                    manager.getMaxRequestedByPort(
                        PORT_NORTH,
                        100
                    )
                );
                assertTrue(manager.isActive(
                    PORT_SOUTH,
                    100
                ));
            }

            assertEquals(
                30,
                manager.getMaxRequestedByPort(
                    PORT_NORTH,
                    100
                )
            );
            assertEquals(
                0,
                manager.getMaxRequestedByPort(
                    PORT_SOUTH,
                    100
                )
            );
            assertFalse(manager.isActive(
                PORT_SOUTH,
                100
            ));
            root.commit();
        }

        assertEquals(
            30,
            manager.getMaxRequestedByPort(
                PORT_NORTH,
                100
            )
        );
        assertFalse(manager.isActive(
            PORT_SOUTH,
            100
        ));
    }

    @Test
    void shouldRollbackChildRequestWhenParentTransactionIsAborted() {
        EnergyDistributionManager manager = newManager(80);

        try (Transaction root = Transaction.openRoot()) {
            try (Transaction child = Transaction.open(root)) {
                manager.recordRequest(
                    PORT_EAST,
                    128,
                    100,
                    child
                );
                child.commit();
            }

            assertTrue(manager.isActive(
                PORT_EAST,
                100
            ));
            assertEquals(
                128,
                manager.getMaxRequestedByPort(
                    PORT_EAST,
                    100
                )
            );
        }

        assertFalse(manager.isActive(
            PORT_EAST,
            100
        ));
        assertEquals(
            0,
            manager.getMaxRequestedByPort(
                PORT_EAST,
                100
            )
        );
        assertEquals(
            0,
            manager.getPreviousMaxRequestedByPort(
                PORT_EAST,
                101
            )
        );
    }

    @Test
    void shouldRestorePreviousDemandHistoryAfterRollback() {
        EnergyDistributionManager manager = newManager(80);
        recordCommittedRequest(
            manager,
            PORT_NORTH,
            60,
            100
        );

        try (Transaction tx = Transaction.openRoot()) {
            manager.recordRequest(
                PORT_NORTH,
                10,
                101,
                tx
            );
            manager.recordRequest(
                PORT_SOUTH,
                40,
                101,
                tx
            );

            assertEquals(
                60,
                manager.getPreviousMaxRequestedByPort(
                    PORT_NORTH,
                    101
                )
            );
            assertEquals(
                10,
                manager.getMaxRequestedByPort(
                    PORT_NORTH,
                    101
                )
            );
            assertEquals(
                40,
                manager.getMaxRequestedByPort(
                    PORT_SOUTH,
                    101
                )
            );
        }

        assertEquals(
            60,
            manager.getMaxRequestedByPort(
                PORT_NORTH,
                100
            )
        );
        assertEquals(
            60,
            manager.getPreviousMaxRequestedByPort(
                PORT_NORTH,
                101
            )
        );
        assertEquals(
            0,
            manager.getMaxRequestedByPort(
                PORT_NORTH,
                101
            )
        );
        assertEquals(
            0,
            manager.getPreviousMaxRequestedByPort(
                PORT_SOUTH,
                101
            )
        );
        assertFalse(manager.isActive(
            PORT_SOUTH,
            101
        ));
    }

    private static int pullEnergy(
        EnergyDistributionManager manager,
        EnergyPortId portId,
        long currentTick,
        int storedEnergy,
        int remainingGlobalBudget
    )
    {
        return pullEnergy(
            manager,
            portId,
            128,
            currentTick,
            storedEnergy,
            remainingGlobalBudget
        );
    }

    private static int pullEnergy(
        @NonNull EnergyDistributionManager manager,
        EnergyPortId portId,
        int requested,
        long currentTick,
        int storedEnergy,
        int remainingGlobalBudget
    )
    {
        try (Transaction tx = Transaction.openRoot()) {
            manager.recordRequest(
                portId,
                requested,
                currentTick,
                tx
            );

            int extracted = manager.getAllowedAdaptiveAmount(
                portId,
                requested,
                remainingGlobalBudget,
                storedEnergy,
                currentTick,
                tx
            );

            if (extracted > 0) {
                manager.recordExtraction(
                    portId,
                    extracted,
                    currentTick,
                    tx
                );
                tx.commit();
            }
            return extracted;
        }
    }

    @Test
    void shouldDistributeEnergyFairlyAmongThreeConsumers() {
        EnergyDistributionManager manager = newManager(80);

        recordCommittedRequest(
            manager,
            PORT_NORTH,
            5,
            99
        );
        recordCommittedRequest(
            manager,
            PORT_SOUTH,
            128,
            99
        );
        recordCommittedRequest(
            manager,
            PORT_EAST,
            128,
            99
        );

        for (long tick = 100; tick < 110; tick++) {
            int storedEnergy = 40;
            int budget = 80;

            int north = pullEnergy(
                manager,
                PORT_NORTH,
                5,
                tick,
                storedEnergy,
                budget
            );
            storedEnergy -= north;
            budget -= north;

            int south = pullEnergy(
                manager,
                PORT_SOUTH,
                128,
                tick,
                storedEnergy,
                budget
            );
            storedEnergy -= south;
            budget -= south;

            int east = pullEnergy(
                manager,
                PORT_EAST,
                128,
                tick,
                storedEnergy,
                budget
            );
            storedEnergy -= east;
            budget -= east;

            assertEquals(
                5,
                north,
                "NORTH: тик " + tick
            );

            assertEquals(
                40,
                north + south + east,
                "Тик " + tick
            );

            if (tick > 100) {
                assertTrue(
                    Math.abs(south - east) <= 1,
                    "SOUTH и EAST должны получать почти поровну на тике " + tick
                );
            }
        }
    }

    private static @NonNull EnergyDistributionManager managerWithPendingFairnessCorrection() {
        EnergyDistributionManager manager = newManager(80);

        for (long tick = 99; tick <= 100; tick++) {
            recordCommittedRequest(
                manager,
                PORT_NORTH,
                80,
                tick
            );
            recordCommittedRequest(
                manager,
                PORT_SOUTH,
                80,
                tick
            );
        }

        try (Transaction tx = Transaction.openRoot()) {
            manager.recordExtraction(
                PORT_NORTH,
                30,
                100,
                tx
            );
            manager.recordExtraction(
                PORT_SOUTH,
                10,
                100,
                tx
            );
            tx.commit();
        }
        return manager;
    }

    private static long fairnessUpdateTick(EnergyDistributionManager manager)
        throws ReflectiveOperationException
    {
        Field field = EnergyDistributionManager.class.getDeclaredField("lastFairnessUpdateTick");
        field.setAccessible(true);
        return field.getLong(manager);
    }

    @SuppressWarnings("unchecked")
    private static Map<EnergyPortId, Long> fairnessCredits(EnergyDistributionManager manager)
        throws ReflectiveOperationException
    {
        Field field = EnergyDistributionManager.class.getDeclaredField("fairnessCredits");
        field.setAccessible(true);
        return (Map<EnergyPortId, Long>) field.get(manager);
    }

    @Test
    void shouldRollbackFairnessCreditsAndTickAfterSimulation() throws ReflectiveOperationException {
        EnergyDistributionManager manager = managerWithPendingFairnessCorrection();
        long beforeTick = fairnessUpdateTick(manager);
        Map<EnergyPortId, Long> beforeCredits = Map.copyOf(fairnessCredits(manager));

        try (Transaction simulated = Transaction.openRoot()) {
            assertEquals(
                30,
                manager.getAllowedAdaptiveAmount(
                    PORT_NORTH,
                    80,
                    80,
                    80,
                    101,
                    simulated
                )
            );
            assertEquals(
                101L,
                fairnessUpdateTick(manager)
            );
            assertEquals(
                -10L,
                fairnessCredits(manager).get(PORT_NORTH)
            );
            assertEquals(
                10L,
                fairnessCredits(manager).get(PORT_SOUTH)
            );
        }

        assertEquals(
            beforeTick,
            fairnessUpdateTick(manager)
        );
        assertEquals(
            beforeCredits,
            fairnessCredits(manager)
        );

        try (Transaction real = Transaction.openRoot()) {
            assertEquals(
                30,
                manager.getAllowedAdaptiveAmount(
                    PORT_NORTH,
                    80,
                    80,
                    80,
                    101,
                    real
                )
            );
            real.commit();
        }
        assertEquals(
            101L,
            fairnessUpdateTick(manager)
        );
        assertEquals(
            -10L,
            fairnessCredits(manager).get(PORT_NORTH)
        );
        assertEquals(
            10L,
            fairnessCredits(manager).get(PORT_SOUTH)
        );
    }

    @Test
    void shouldRollbackFairnessFromCommittedChildWhenRootAborts()
        throws ReflectiveOperationException
    {
        EnergyDistributionManager manager = managerWithPendingFairnessCorrection();
        long beforeTick = fairnessUpdateTick(manager);
        Map<EnergyPortId, Long> beforeCredits = Map.copyOf(fairnessCredits(manager));

        try (Transaction root = Transaction.openRoot()) {
            try (Transaction child = Transaction.open(root)) {
                assertEquals(
                    30,
                    manager.getAllowedAdaptiveAmount(
                        PORT_NORTH,
                        80,
                        80,
                        80,
                        101,
                        child
                    )
                );
                child.commit();
            }

            assertEquals(
                101L,
                fairnessUpdateTick(manager)
            );
            assertEquals(
                -10L,
                fairnessCredits(manager).get(PORT_NORTH)
            );
            assertEquals(
                10L,
                fairnessCredits(manager).get(PORT_SOUTH)
            );
        }

        assertEquals(
            beforeTick,
            fairnessUpdateTick(manager)
        );
        assertEquals(
            beforeCredits,
            fairnessCredits(manager)
        );
    }

    @Test
    void shouldRestoreCreditsAfterRollbackOfSinglePortReset() throws ReflectiveOperationException {
        EnergyDistributionManager manager = managerWithPendingFairnessCorrection();
        getCommittedAdaptiveAmount(
            manager,
            PORT_NORTH,
            80,
            80,
            80,
            101
        );

        assertEquals(
            -10L,
            fairnessCredits(manager).get(PORT_NORTH)
        );
        assertEquals(
            10L,
            fairnessCredits(manager).get(PORT_SOUTH)
        );
        assertEquals(
            101L,
            fairnessUpdateTick(manager)
        );

        recordCommittedRequest(
            manager,
            PORT_NORTH,
            80,
            110
        );
        recordCommittedRequest(
            manager,
            PORT_NORTH,
            80,
            121
        );
        assertEquals(
            1,
            manager.countActivePorts(121)
        );

        try (Transaction simulated = Transaction.openRoot()) {
            assertEquals(
                80,
                manager.getAllowedAdaptiveAmount(
                    PORT_NORTH,
                    80,
                    80,
                    80,
                    121,
                    simulated
                )
            );
            assertTrue(fairnessCredits(manager).isEmpty());
            assertEquals(
                121L,
                fairnessUpdateTick(manager)
            );
        }

        assertEquals(
            101L,
            fairnessUpdateTick(manager)
        );
        assertEquals(
            -10L,
            fairnessCredits(manager).get(PORT_NORTH)
        );
        assertEquals(
            10L,
            fairnessCredits(manager).get(PORT_SOUTH)
        );
    }

    @Test
    void shouldNotTreatSimulatedRequestAsActiveAfterRollback() {
        EnergyDistributionManager manager = newManager(80);
        recordCommittedRequest(
            manager,
            PORT_NORTH,
            80,
            120
        );

        try (Transaction root = Transaction.openRoot()) {
            try (Transaction simulated = Transaction.open(root)) {
                manager.recordRequest(
                    PORT_SOUTH,
                    128,
                    121,
                    simulated
                );
                assertEquals(
                    2,
                    manager.countActivePorts(121)
                );
                assertEquals(
                    40,
                    manager.getAllowedAdaptiveAmount(
                        PORT_SOUTH,
                        128,
                        80,
                        80,
                        121,
                        simulated
                    )
                );
            }

            assertFalse(manager.isActive(
                PORT_SOUTH,
                121
            ));
            assertEquals(
                1,
                manager.countActivePorts(121)
            );
            assertEquals(
                80,
                manager.getAllowedAdaptiveAmount(
                    PORT_NORTH,
                    80,
                    80,
                    80,
                    121,
                    root
                )
            );
            root.commit();
        }
    }


    @Test
    void shouldNotPenalizeRepeatedSmallTransfersAsExcessDemand() {
        EnergyDistributionManager manager = newManager(80);

        recordCommittedRequest(
            manager,
            PORT_NORTH,
            10,
            99
        );
        recordCommittedRequest(
            manager,
            PORT_SOUTH,
            10,
            99
        );

        int storedEnergy = 80;
        int remainingBudget = 80;

        for (int packet = 0; packet < 4; packet++) {
            int north = pullEnergy(
                manager,
                PORT_NORTH,
                10,
                100,
                storedEnergy,
                remainingBudget
            );
            assertEquals(
                10,
                north,
                "NORTH packet " + packet
            );
            storedEnergy -= north;
            remainingBudget -= north;

            int south = pullEnergy(
                manager,
                PORT_SOUTH,
                10,
                100,
                storedEnergy,
                remainingBudget
            );
            assertEquals(
                10,
                south,
                "SOUTH packet " + packet
            );
            storedEnergy -= south;
            remainingBudget -= south;
        }

        assertEquals(
            40,
            manager.getExtractedByPort(
                PORT_NORTH,
                100
            )
        );
        assertEquals(
            40,
            manager.getExtractedByPort(
                PORT_SOUTH,
                100
            )
        );
        assertEquals(
            10,
            manager.getMaxRequestedByPort(
                PORT_NORTH,
                100
            )
        );
        assertEquals(
            10,
            manager.getMaxRequestedByPort(
                PORT_SOUTH,
                100
            )
        );

        int north = pullEnergy(
            manager,
            PORT_NORTH,
            10,
            101,
            40,
            80
        );
        assertEquals(
            10,
            north,
            "NORTH must still receive energy on tick 101"
        );

        int south = pullEnergy(
            manager,
            PORT_SOUTH,
            10,
            101,
            40 - north,
            80 - north
        );
        assertEquals(
            10,
            south,
            "SOUTH must still receive energy on tick 101"
        );
    }


    @Test
    void shouldKeepOtherFairnessCreditsWhileThirdPortIsInactive()
        throws ReflectiveOperationException
    {
        EnergyDistributionManager manager = managerWithThreePortCredits();
        Map<EnergyPortId, Long> before = Map.copyOf(fairnessCredits(manager));

        assertTrue(before.getOrDefault(
            PORT_EAST,
            0L
        ) > 0);

        assertTrue(manager.isActive(
            PORT_NORTH,
            120
        ));
        assertTrue(manager.isActive(
            PORT_SOUTH,
            120
        ));
        assertFalse(manager.isActive(
            PORT_EAST,
            120
        ));

        assertEquals(
            before,
            fairnessCredits(manager),
            "Отключение EAST не должно сбрасывать остальные компенсации"
        );
    }

    @Test
    void shouldResetOnlyReconnectedPortFairnessCredit() throws ReflectiveOperationException
    {
        EnergyDistributionManager manager = managerWithThreePortCredits();

        Map<EnergyPortId, Long> before = Map.copyOf(fairnessCredits(manager));
        long lastUpdate = fairnessUpdateTick(manager);

        recordCommittedRequest(
            manager,
            PORT_EAST,
            80,
            120
        );

        assertTrue(manager.isActive(
            PORT_EAST,
            120
        ));
        assertEquals(
            3,
            manager.countActivePorts(120)
        );
        assertEquals(
            80,
            manager.getMaxRequestedByPort(
                PORT_EAST,
                120
            )
        );

        assertFalse(
            fairnessCredits(manager).containsKey(PORT_EAST),
            "EAST не должен сохранять компенсацию после переподключения"
        );

        assertEquals(
            before.get(PORT_NORTH),
            fairnessCredits(manager).get(PORT_NORTH)
        );

        assertEquals(
            before.get(PORT_SOUTH),
            fairnessCredits(manager).get(PORT_SOUTH)
        );

        assertEquals(
            lastUpdate,
            fairnessUpdateTick(manager),
            "Повторное подключение не должно пересчитывать компенсации"
        );
    }

    @Test
    void shouldRollbackReconnectionAndRestoreExpiredPortCredit() throws ReflectiveOperationException
    {
        EnergyDistributionManager manager = managerWithThreePortCredits();

        Map<EnergyPortId, Long> before = Map.copyOf(fairnessCredits(manager));
        long lastUpdate = fairnessUpdateTick(manager);

        try (Transaction root = Transaction.openRoot()) {
            try (Transaction child = Transaction.open(root)) {
                manager.recordRequest(
                    PORT_EAST,
                    80,
                    120,
                    child
                );

                assertTrue(manager.isActive(
                    PORT_EAST,
                    120
                ));
                assertFalse(fairnessCredits(manager).containsKey(PORT_EAST));

                child.commit();
            }

            assertEquals(
                3,
                manager.countActivePorts(120)
            );

        }

        assertFalse(manager.isActive(
            PORT_EAST,
            120
        ));
        assertEquals(
            2,
            manager.countActivePorts(120)
        );
        assertEquals(
            0,
            manager.getMaxRequestedByPort(
                PORT_EAST,
                120
            )
        );

        assertEquals(
            before,
            fairnessCredits(manager),
            "После отката должны восстановиться прежние компенсации"
        );

        assertEquals(
            lastUpdate,
            fairnessUpdateTick(manager)
        );
    }

    @Test
    void abortedRequestIsNotRememberedAsDemand() {
        EnergyDistributionManager manager = newManager(80);

        long tick = 100L;

        try (Transaction tx = Transaction.openRoot()) {
            manager.recordRequest(
                PORT_NORTH,
                40,
                tick,
                tx
            );

            assertTrue(manager.isActive(
                PORT_NORTH,
                tick
            ));
        }

        assertFalse(manager.isActive(
            PORT_NORTH,
            tick
        ));

        try (Transaction tx = Transaction.openRoot()) {
            manager.recordRequest(
                PORT_SOUTH,
                40,
                tick,
                tx
            );

            int allowed = manager.getAllowedAdaptiveAmount(
                PORT_SOUTH,
                40,
                80,
                40,
                tick,
                tx
            );

            assertEquals(
                40,
                allowed
            );
            tx.commit();
        }
    }

    @Test
    void shouldProtectPushConsumerShareWhenConfirmedDemandIsSmall() {
        EnergyDistributionManager manager = newManager(80);

        long previousTick = 99L;
        long currentTick = 100L;

        try (Transaction tx = Transaction.openRoot()) {
            manager.recordRequest(
                PORT_NORTH,
                40,
                previousTick,
                tx
            );
            manager.recordRequest(
                PORT_SOUTH,
                1,
                previousTick,
                tx
            );
            tx.commit();
        }

        manager.updatePushAvailability(
            Map.of(
                PORT_NORTH,
                80,
                PORT_SOUTH,
                80
            ),
            currentTick
        );

        try (Transaction tx = Transaction.openRoot()) {
            manager.recordRequest(
                PORT_NORTH,
                2,
                currentTick,
                tx
            );
            manager.recordExtraction(
                PORT_NORTH,
                2,
                currentTick,
                tx
            );

            manager.recordRequest(
                PORT_SOUTH,
                1,
                currentTick,
                tx
            );
            manager.recordExtraction(
                PORT_SOUTH,
                1,
                currentTick,
                tx
            );

            manager.recordRequest(
                PORT_NORTH,
                128,
                currentTick,
                tx
            );

            int allowed = manager.getAllowedAdaptiveAmount(
                PORT_NORTH,
                128,
                77,
                40,
                currentTick,
                tx
            );

            assertEquals(
                20,
                allowed
            );
        }
    }

    @Test
    void shouldAllowPositiveFairnessCreditToCompensatePreviousDeficit() {
        EnergyDistributionManager manager = newManager(80);

        for (long tick : new long[]{98L, 99L}) {
            try (Transaction tx = Transaction.openRoot()) {
                manager.recordRequest(
                    PORT_NORTH,
                    40,
                    tick,
                    tx
                );
                manager.recordRequest(
                    PORT_SOUTH,
                    40,
                    tick,
                    tx
                );

                if (tick == 99L) {
                    manager.recordExtraction(
                        PORT_SOUTH,
                        40,
                        tick,
                        tx
                    );
                }

                tx.commit();
            }
        }

        long currentTick = 100L;

        manager.updatePushAvailability(
            Map.of(
                PORT_NORTH,
                80,
                PORT_SOUTH,
                80
            ),
            currentTick
        );

        try (Transaction tx = Transaction.openRoot()) {
            manager.recordRequest(
                PORT_NORTH,
                40,
                currentTick,
                tx
            );

            int allowed = manager.getAllowedAdaptiveAmount(
                PORT_NORTH,
                40,
                80,
                40,
                currentTick,
                tx
            );

            assertEquals(
                40,
                allowed
            );
        }
    }

    @Test
    void shouldProtectObservedPushConsumerWithoutCommittedRequest() {
        EnergyDistributionManager manager = newManager(80);

        long currentTick = 100L;

        manager.updatePushAvailability(
            Map.of(
                PORT_NORTH,
                80,
                PORT_SOUTH,
                80
            ),
            currentTick
        );

        int protectedQuota = manager.getProtectedRemainingQuota(
            PORT_SOUTH,
            40,
            currentTick
        );

        assertEquals(
            20,
            protectedQuota
        );
    }

    @Test
    void shouldPreservePreviousPushAvailabilityAcrossTicks() {
        EnergyDistributionManager manager = newManager(80);

        manager.updatePushAvailability(
            Map.of(
                PORT_NORTH,
                80,
                PORT_SOUTH,
                60
            ),
            100L
        );

        assertEquals(
            80,
            manager.getPreviousPushAvailability(
                PORT_NORTH,
                101L
            )
        );
        assertEquals(
            60,
            manager.getPreviousPushAvailability(
                PORT_SOUTH,
                101L
            )
        );

        manager.updatePushAvailability(
            Map.of(
                PORT_NORTH,
                40,
                PORT_SOUTH,
                20
            ),
            101L
        );

        assertEquals(
            40,
            manager.getPushAvailability(
                PORT_NORTH,
                101L
            )
        );
        assertEquals(
            20,
            manager.getPushAvailability(
                PORT_SOUTH,
                101L
            )
        );

        assertEquals(
            80,
            manager.getPreviousPushAvailability(
                PORT_NORTH,
                101L
            )
        );
        assertEquals(
            60,
            manager.getPreviousPushAvailability(
                PORT_SOUTH,
                101L
            )
        );
    }

    @Test
    void shouldKeepPreviousPushAvailabilityOnSameTickUpdate() {
        EnergyDistributionManager manager = newManager(80);

        manager.updatePushAvailability(
            Map.of(
                PORT_NORTH,
                80
            ),
            100L
        );

        manager.updatePushAvailability(
            Map.of(
                PORT_NORTH,
                40
            ),
            101L
        );

        manager.updatePushAvailability(
            Map.of(
                PORT_NORTH,
                20
            ),
            101L
        );

        assertEquals(
            20,
            manager.getPushAvailability(
                PORT_NORTH,
                101L
            )
        );

        assertEquals(
            80,
            manager.getPreviousPushAvailability(
                PORT_NORTH,
                101L
            )
        );
    }

    @Test
    void shouldNotReturnStalePushAvailabilityAfterSkippedTick() {
        EnergyDistributionManager manager = newManager(80);

        manager.updatePushAvailability(
            Map.of(
                PORT_NORTH,
                80
            ),
            100L
        );

        assertEquals(
            0,
            manager.getPreviousPushAvailability(
                PORT_NORTH,
                104L
            )
        );

        manager.updatePushAvailability(
            Map.of(
                PORT_NORTH,
                30
            ),
            104L
        );

        assertEquals(
            0,
            manager.getPreviousPushAvailability(
                PORT_NORTH,
                104L
            )
        );

        assertEquals(
            30,
            manager.getPreviousPushAvailability(
                PORT_NORTH,
                105L
            )
        );
    }

    @Test
    void shouldUsePreviousPushAvailabilityWhenCalculatingFairnessCredits() {
        EnergyDistributionManager manager = newManager(80);

        try (Transaction tx = Transaction.openRoot()) {
            manager.recordRequest(
                PORT_NORTH,
                40,
                98L,
                tx
            );
            manager.recordRequest(
                PORT_SOUTH,
                40,
                98L,
                tx
            );
            tx.commit();
        }

        manager.updatePushAvailability(
            Map.of(
                PORT_NORTH,
                80,
                PORT_SOUTH,
                80
            ),
            99L
        );

        try (Transaction tx = Transaction.openRoot()) {
            manager.recordRequest(
                PORT_NORTH,
                40,
                99L,
                tx
            );
            manager.recordRequest(
                PORT_SOUTH,
                1,
                99L,
                tx
            );

            manager.recordExtraction(
                PORT_NORTH,
                39,
                99L,
                tx
            );
            manager.recordExtraction(
                PORT_SOUTH,
                1,
                99L,
                tx
            );

            tx.commit();
        }

        manager.updatePushAvailability(
            Map.of(
                PORT_NORTH,
                80,
                PORT_SOUTH,
                80
            ),
            100L
        );

        try (Transaction tx = Transaction.openRoot()) {
            manager.recordRequest(
                PORT_SOUTH,
                40,
                100L,
                tx
            );

            int allowed = manager.getAllowedAdaptiveAmount(
                PORT_SOUTH,
                40,
                80,
                40,
                100L,
                tx
            );

            assertEquals(
                39,
                allowed
            );
        }
    }

    @Test
    void shouldCompensatePushConsumerWithoutPreviousCommittedRequest() {
        EnergyDistributionManager manager = newManager(80);

        try (Transaction tx = Transaction.openRoot()) {
            manager.recordRequest(
                PORT_NORTH,
                40,
                98L,
                tx
            );
            tx.commit();
        }

        manager.updatePushAvailability(
            Map.of(
                PORT_NORTH,
                80,
                PORT_SOUTH,
                80
            ),
            99L
        );

        try (Transaction tx = Transaction.openRoot()) {
            manager.recordRequest(
                PORT_NORTH,
                40,
                99L,
                tx
            );

            manager.recordExtraction(
                PORT_NORTH,
                40,
                99L,
                tx
            );

            tx.commit();
        }

        manager.updatePushAvailability(
            Map.of(
                PORT_NORTH,
                80,
                PORT_SOUTH,
                80
            ),
            100L
        );

        try (Transaction tx = Transaction.openRoot()) {
            manager.recordRequest(
                PORT_SOUTH,
                40,
                100L,
                tx
            );

            int allowed = manager.getAllowedAdaptiveAmount(
                PORT_SOUTH,
                40,
                80,
                40,
                100L,
                tx
            );

            assertEquals(
                40,
                allowed
            );
        }
    }

    @Test
    void shouldNotFavorOnePortOverManyTicks() {
        EnergyDistributionManager manager = newManager(80);

        EnergyPortId[] ports = {PORT_NORTH, PORT_SOUTH, PORT_WEST};

        Map<EnergyPortId, Integer> totalExtracted = new LinkedHashMap<>();

        for (EnergyPortId portId : ports) {
            totalExtracted.put(
                portId,
                0
            );
        }

        for (long tick = 100L; tick < 160L; tick++) {
            int storedEnergy = 40;
            int remainingBudget = 80;

            manager.updatePushAvailability(
                Map.of(
                    PORT_NORTH,
                    80,
                    PORT_SOUTH,
                    80,
                    PORT_WEST,
                    80
                ),
                tick
            );

            try (Transaction tx = Transaction.openRoot()) {

                for (EnergyPortId portId : ports) {
                    manager.recordRequest(
                        portId,
                        80,
                        tick,
                        tx
                    );
                }

                for (EnergyPortId portId : ports) {
                    int allowed = manager.getAllowedAdaptiveAmount(
                        portId,
                        80,
                        remainingBudget,
                        storedEnergy,
                        tick,
                        tx
                    );

                    int extracted = Math.min(
                        allowed,
                        storedEnergy
                    );

                    manager.recordExtraction(
                        portId,
                        extracted,
                        tick,
                        tx
                    );

                    storedEnergy -= extracted;
                    remainingBudget -= extracted;

                    totalExtracted.merge(
                        portId,
                        extracted,
                        Integer::sum
                    );
                }

                tx.commit();
            }

            assertEquals(
                0,
                storedEnergy,
                "Не вся энергия распределена в тике " + tick
            );
        }

        int north = totalExtracted.get(PORT_NORTH);
        int south = totalExtracted.get(PORT_SOUTH);
        int west = totalExtracted.get(PORT_WEST);

        int max = Math.max(
            north,
            Math.max(
                south,
                west
            )
        );
        int min = Math.min(
            north,
            Math.min(
                south,
                west
            )
        );

        assertTrue(
            max - min <= 2,
            "Обнаружен долговременный перекос: " + "N=" + north + " S=" + south + " W=" + west
        );
    }

    // -------------------------------------------------------------------------
    // Новые регрессионные сценарии для логических портов и динамического состава.
    // -------------------------------------------------------------------------

    @Test
    void shouldCountDifferentLogicalPortsWithSamePhysicalSidePrefixSeparately() {
        EnergyPortId first = new EnergyPortId("north.output.first");
        EnergyPortId second = new EnergyPortId("north.output.second");
        EnergyDistributionManager manager = managerWithPorts(
            first,
            second
        );

        recordCommittedRequest(
            manager,
            first,
            80,
            100L
        );
        recordCommittedRequest(
            manager,
            second,
            80,
            100L
        );

        assertEquals(
            2,
            manager.countActivePorts(100L)
        );
        assertEquals(
            40,
            manager.getBaseQuota(
                first,
                100L
            )
        );
        assertEquals(
            40,
            manager.getBaseQuota(
                second,
                100L
            )
        );

        try (Transaction tx = Transaction.openRoot()) {
            manager.recordExtraction(
                first,
                15,
                100L,
                tx
            );
            tx.commit();
        }

        assertEquals(
            15,
            manager.getExtractedByPort(
                first,
                100L
            )
        );
        assertEquals(
            0,
            manager.getExtractedByPort(
                second,
                100L
            )
        );
        assertEquals(
            25,
            manager.getRemainingBaseQuota(
                first,
                100L
            )
        );
        assertEquals(
            40,
            manager.getRemainingBaseQuota(
                second,
                100L
            )
        );
    }

    @Test
    void shouldSupportMoreThanSixIndependentPorts() {
        List<EnergyPortId> ports = new ArrayList<>();
        for (int index = 0; index < 8; index++) {
            ports.add(new EnergyPortId("output." + index));
        }

        EnergyDistributionManager manager = new EnergyDistributionManager(80);
        manager.updateAvailablePorts(ports);

        try (Transaction tx = Transaction.openRoot()) {
            for (EnergyPortId portId : ports) {
                manager.recordRequest(
                    portId,
                    80,
                    100L,
                    tx
                );
            }
            tx.commit();
        }

        assertEquals(
            8,
            manager.countActivePorts(100L)
        );
        for (EnergyPortId portId : ports) {
            assertEquals(
                10,
                manager.getBaseQuota(
                    portId,
                    100L
                )
            );
        }
    }

    @Test
    void shouldIgnoreRequestsFromUnavailablePorts() {
        EnergyPortId allowed = new EnergyPortId("allowed");
        EnergyPortId unavailable = new EnergyPortId("unavailable");
        EnergyDistributionManager manager = managerWithPorts(allowed);

        recordCommittedRequest(
            manager,
            unavailable,
            80,
            100L
        );

        assertFalse(manager.isActive(
            unavailable,
            100L
        ));
        assertEquals(
            0,
            manager.countActivePorts(100L)
        );
        assertEquals(
            0,
            manager.getMaxRequestedByPort(
                unavailable,
                100L
            )
        );
    }

    @Test
    void shouldRejectExtractionFromUnavailablePortWithoutModifyingUsage() {
        EnergyPortId allowed = new EnergyPortId("allowed");
        EnergyPortId unavailable = new EnergyPortId("unavailable");
        EnergyDistributionManager manager = managerWithPorts(allowed);

        try (Transaction tx = Transaction.openRoot()) {
            manager.recordExtraction(
                allowed,
                20,
                100L,
                tx
            );
            tx.commit();
        }

        assertThrows(
            IllegalStateException.class,
            () -> {
                try (Transaction tx = Transaction.openRoot()) {
                    manager.recordExtraction(
                        unavailable,
                        15,
                        100L,
                        tx
                    );
                    tx.commit();
                }
            }
        );

        assertEquals(
            20,
            manager.getExtractedByPort(
                allowed,
                100L
            )
        );
        assertEquals(
            0,
            manager.getExtractedByPort(
                unavailable,
                100L
            )
        );
    }

    @Test
    void shouldDenyAdaptiveExtractionFromUnavailablePort() {
        EnergyPortId allowed = new EnergyPortId("allowed");
        EnergyPortId unavailable = new EnergyPortId("unavailable");
        EnergyDistributionManager manager = managerWithPorts(allowed);

        recordCommittedRequest(
            manager,
            allowed,
            80,
            100L
        );
        assertEquals(
            0,
            getCommittedAdaptiveAmount(
                manager,
                unavailable,
                80,
                80,
                80,
                100L
            )
        );
        assertEquals(
            1,
            manager.countActivePorts(100L)
        );
    }

    @Test
    void shouldForgetPortActivityDemandUsageAndPushOnDisableAndReenable() {
        EnergyPortId first = new EnergyPortId("first");
        EnergyPortId second = new EnergyPortId("second");
        EnergyDistributionManager manager = managerWithPorts(
            first,
            second
        );

        recordCommittedRequest(
            manager,
            first,
            80,
            100L
        );
        try (Transaction tx = Transaction.openRoot()) {
            manager.recordExtraction(
                first,
                30,
                100L,
                tx
            );
            tx.commit();
        }
        manager.updatePushAvailability(
            Map.of(
                first,
                40,
                second,
                20
            ),
            100L
        );
        assertTrue(manager.isActive(
            first,
            100L
        ));

        manager.updateAvailablePorts(List.of(second));
        assertFalse(manager.isActive(
            first,
            100L
        ));
        assertEquals(
            0,
            manager.getExtractedByPort(
                first,
                100L
            )
        );
        assertEquals(
            0,
            manager.getPushAvailability(
                first,
                100L
            )
        );
        assertEquals(
            20,
            manager.getPushAvailability(
                second,
                100L
            )
        );

        manager.updateAvailablePorts(List.of(
            first,
            second
        ));
        assertFalse(manager.isActive(
            first,
            101L
        ));
        assertEquals(
            0,
            manager.getMaxRequestedByPort(
                first,
                100L
            )
        );
        assertEquals(
            0,
            manager.getPreviousMaxRequestedByPort(
                first,
                101L
            )
        );
        assertEquals(
            0,
            manager.getPreviousExtractedByPort(
                first,
                101L
            )
        );
        assertEquals(
            0,
            manager.getPreviousPushAvailability(
                first,
                101L
            )
        );

        recordCommittedRequest(
            manager,
            first,
            40,
            101L
        );
        assertTrue(manager.isActive(
            first,
            101L
        ));
        assertEquals(
            40,
            manager.getMaxRequestedByPort(
                first,
                101L
            )
        );
    }

    @Test
    void shouldKeepRemainingPortHistoryAfterAnotherPortIsRemoved() {
        EnergyPortId first = new EnergyPortId("first");
        EnergyPortId second = new EnergyPortId("second");
        EnergyDistributionManager manager = managerWithPorts(
            first,
            second
        );

        recordCommittedRequest(
            manager,
            first,
            80,
            100L
        );
        recordCommittedRequest(
            manager,
            second,
            40,
            100L
        );
        try (Transaction tx = Transaction.openRoot()) {
            manager.recordExtraction(
                first,
                15,
                100L,
                tx
            );
            manager.recordExtraction(
                second,
                25,
                100L,
                tx
            );
            tx.commit();
        }

        manager.updateAvailablePorts(List.of(second));

        assertFalse(manager.isActive(
            first,
            100L
        ));
        assertTrue(manager.isActive(
            second,
            100L
        ));
        assertEquals(
            40,
            manager.getMaxRequestedByPort(
                second,
                100L
            )
        );
        assertEquals(
            25,
            manager.getExtractedByPort(
                second,
                100L
            )
        );
        assertEquals(
            25,
            manager.getPreviousExtractedByPort(
                second,
                101L
            )
        );
    }

    @Test
    void shouldPreserveStatisticsWhenPortsAreReordered() {
        EnergyPortId first = new EnergyPortId("first");
        EnergyPortId second = new EnergyPortId("second");
        EnergyDistributionManager manager = managerWithPorts(
            first,
            second
        );

        recordCommittedRequest(
            manager,
            first,
            60,
            100L
        );
        recordCommittedRequest(
            manager,
            second,
            40,
            100L
        );
        try (Transaction tx = Transaction.openRoot()) {
            manager.recordExtraction(
                first,
                20,
                100L,
                tx
            );
            tx.commit();
        }

        manager.updateAvailablePorts(List.of(
            second,
            first
        ));

        assertEquals(
            2,
            manager.countActivePorts(100L)
        );
        assertEquals(
            60,
            manager.getMaxRequestedByPort(
                first,
                100L
            )
        );
        assertEquals(
            40,
            manager.getMaxRequestedByPort(
                second,
                100L
            )
        );
        assertEquals(
            20,
            manager.getExtractedByPort(
                first,
                100L
            )
        );
    }

    @Test
    void shouldRejectInvalidPushMapBeforeChangingObservedAvailability() {
        EnergyPortId portId = new EnergyPortId("output");
        EnergyDistributionManager manager = managerWithPorts(portId);
        manager.updatePushAvailability(
            Map.of(
                portId,
                80
            ),
            100L
        );

        Map<EnergyPortId, Integer> invalid = new HashMap<>();
        invalid.put(
            portId,
            null
        );

        assertThrows(
            NullPointerException.class,
            () -> manager.updatePushAvailability(
                invalid,
                101L
            )
        );

        assertEquals(
            80,
            manager.getPushAvailability(
                portId,
                100L
            )
        );
        assertEquals(
            80,
            manager.getPreviousPushAvailability(
                portId,
                101L
            )
        );
    }

    @Test
    void shouldIgnorePushAvailabilityForUnknownPorts() {
        EnergyPortId known = new EnergyPortId("known");
        EnergyPortId unknown = new EnergyPortId("unknown");
        EnergyDistributionManager manager = managerWithPorts(known);

        manager.updatePushAvailability(
            Map.of(
                known,
                40,
                unknown,
                80
            ),
            100L
        );

        assertEquals(
            40,
            manager.getPushAvailability(
                known,
                100L
            )
        );
        assertEquals(
            0,
            manager.getPushAvailability(
                unknown,
                100L
            )
        );
        assertEquals(
            40,
            manager.getProtectedRemainingQuota(
                known,
                40,
                100L
            )
        );
    }


    @Test
    void shouldNormalizeRemainingFairnessCreditsWhenPortIsRemoved()
        throws ReflectiveOperationException
    {
        EnergyDistributionManager manager = managerWithThreePortCredits();

        Map<EnergyPortId, Long> before = Map.copyOf(fairnessCredits(manager));

        long previousDifference = before.get(PORT_NORTH) - before.get(PORT_SOUTH);

        manager.updateAvailablePorts(List.of(
            PORT_NORTH,
            PORT_SOUTH
        ));

        Map<EnergyPortId, Long> after = fairnessCredits(manager);

        assertFalse(after.containsKey(PORT_EAST));

        assertTrue(after.containsKey(PORT_NORTH));
        assertTrue(after.containsKey(PORT_SOUTH));

        long sum = after.values()
            .stream()
            .mapToLong(Long::longValue)
            .sum();

        assertEquals(
            0L,
            sum,
            "Сумма кредитов после удаления порта должна быть нулевой"
        );

        long currentDifference = after.get(PORT_NORTH) - after.get(PORT_SOUTH);

        assertTrue(
            Math.abs(currentDifference - previousDifference) <= 1,
            "Нужно сохранить относительный приоритет портов"
        );
    }


    @Test
    void shouldNotLoseBudgetOrFairnessAfterPortReconfiguration() throws ReflectiveOperationException
    {
        EnergyDistributionManager manager = managerWithPorts(
            PORT_NORTH,
            PORT_SOUTH,
            PORT_EAST
        );

        for (long tick = 99; tick <= 101; tick++) {
            recordCommittedRequest(
                manager,
                PORT_NORTH,
                80,
                tick
            );
            recordCommittedRequest(
                manager,
                PORT_SOUTH,
                80,
                tick
            );
            recordCommittedRequest(
                manager,
                PORT_EAST,
                80,
                tick
            );
        }

        try (Transaction tx = Transaction.openRoot()) {
            manager.recordExtraction(
                PORT_NORTH,
                40,
                100,
                tx
            );
            tx.commit();
        }

        getCommittedAdaptiveAmount(
            manager,
            PORT_NORTH,
            80,
            80,
            80,
            101
        );

        assertTrue(
            fairnessCredits(manager).getOrDefault(
                PORT_EAST,
                0L
            ) > 0,
            "У EAST должен быть положительный кредит"
        );

        manager.updateAvailablePorts(List.of(
            PORT_NORTH,
            PORT_SOUTH
        ));

        assertFalse(
            fairnessCredits(manager).containsKey(PORT_EAST),
            "Кредит отключённого EAST должен удалиться"
        );

        long northTwoTotal = 0;
        long southTwoTotal = 0;
        int underusedTwoTicks = 0;

        for (long tick = 102; tick < 302; tick++) {
            recordCommittedRequest(
                manager,
                PORT_NORTH,
                80,
                tick
            );
            recordCommittedRequest(
                manager,
                PORT_SOUTH,
                80,
                tick
            );

            int north = pullEnergy(
                manager,
                PORT_NORTH,
                80,
                tick,
                80,
                80
            );

            int remaining = 80 - north;

            int south = pullEnergy(
                manager,
                PORT_SOUTH,
                80,
                tick,
                remaining,
                remaining
            );

            int total = north + south;

            assertTrue(
                total <= 80,
                "Превышен бюджет: тик " + tick
            );

            if (tick >= 122 && total < 80) {
                underusedTwoTicks++;
            }

            northTwoTotal += north;
            southTwoTotal += south;
        }

        long twoPortDifference = Math.abs(northTwoTotal - southTwoTotal);

        manager.updateAvailablePorts(List.of(
            PORT_NORTH,
            PORT_SOUTH,
            PORT_EAST
        ));

        assertFalse(
            fairnessCredits(manager).containsKey(PORT_EAST),
            "Старый кредит EAST не должен восстанавливаться"
        );

        long northThreeTotal = 0;
        long southThreeTotal = 0;
        long eastThreeTotal = 0;
        int underusedThreeTicks = 0;

        for (long tick = 302; tick < 602; tick++) {
            recordCommittedRequest(
                manager,
                PORT_NORTH,
                80,
                tick
            );
            recordCommittedRequest(
                manager,
                PORT_SOUTH,
                80,
                tick
            );
            recordCommittedRequest(
                manager,
                PORT_EAST,
                80,
                tick
            );

            int remaining = 80;

            int north = pullEnergy(
                manager,
                PORT_NORTH,
                80,
                tick,
                remaining,
                remaining
            );
            remaining -= north;

            int south = pullEnergy(
                manager,
                PORT_SOUTH,
                80,
                tick,
                remaining,
                remaining
            );
            remaining -= south;

            int east = pullEnergy(
                manager,
                PORT_EAST,
                80,
                tick,
                remaining,
                remaining
            );
            remaining -= east;

            int total = north + south + east;

            assertTrue(
                total <= 80,
                "Превышен бюджет: тик " + tick
            );

            if (tick >= 322 && total < 80) {
                underusedThreeTicks++;
            }

            northThreeTotal += north;
            southThreeTotal += south;
            eastThreeTotal += east;
        }

        long maxThree = Math.max(
            northThreeTotal,
            Math.max(
                southThreeTotal,
                eastThreeTotal
            )
        );
        long minThree = Math.min(
            northThreeTotal,
            Math.min(
                southThreeTotal,
                eastThreeTotal
            )
        );

        final int missedTwo = underusedTwoTicks;
        final int missedThree = underusedThreeTicks;
        final long differenceTwo = twoPortDifference;
        final long differenceThree = maxThree - minThree;

        assertAll(
            "Регрессия конфигурации 3 -> 2 -> 3",
            () -> assertEquals(
                0,
                missedTwo,
                "После отключения порта теряется доступный бюджет"
            ),
            () -> assertEquals(
                0,
                missedThree,
                "После возвращения порта теряется доступный бюджет"
            ),
            () -> assertTrue(
                differenceTwo <= 80,
                "Два потребителя получают систематически разные доли"
            ),
            () -> assertTrue(
                differenceThree <= 80,
                "Три потребителя получают систематически разные доли"
            )
        );
    }


    @Test
    void shouldNormalizeFairnessCreditsAtBoundaries() throws ReflectiveOperationException
    {
        long[][] scenarios = {{80, -80, 60, -60, 50, -80, 30}, {-80, 80, -60, 60, -50, 80, -30}};

        for (long[] scenario : scenarios) {
            EnergyPortId removedPort = new EnergyPortId("test.removed");

            EnergyDistributionManager manager = managerWithPorts(
                PORT_NORTH,
                PORT_SOUTH,
                PORT_EAST,
                removedPort
            );

            Field field = EnergyDistributionManager.class.getDeclaredField("fairnessCredits");

            field.setAccessible(true);

            @SuppressWarnings("unchecked") Map<EnergyPortId, Long> credits =
                (Map<EnergyPortId, Long>) field.get(manager);

            credits.put(
                PORT_NORTH,
                scenario[0]
            );
            credits.put(
                PORT_SOUTH,
                scenario[1]
            );
            credits.put(
                PORT_EAST,
                scenario[2]
            );
            credits.put(
                removedPort,
                scenario[3]
            );

            assertEquals(
                0L,
                credits.values()
                    .stream()
                    .mapToLong(Long::longValue)
                    .sum()
            );

            manager.updateAvailablePorts(List.of(
                PORT_NORTH,
                PORT_SOUTH,
                PORT_EAST
            ));

            assertFalse(credits.containsKey(removedPort));
            assertEquals(
                3,
                credits.size()
            );

            assertEquals(
                0L,
                credits.values()
                    .stream()
                    .mapToLong(Long::longValue)
                    .sum()
            );

            for (long credit : credits.values()) {
                assertTrue(
                    credit >= -80 && credit <= 80,
                    "Кредит вышел за пределы ±80: " + credit
                );
            }

            assertEquals(
                Long.valueOf(scenario[4]),
                credits.get(PORT_NORTH)
            );
            assertEquals(
                Long.valueOf(scenario[5]),
                credits.get(PORT_SOUTH)
            );
            assertEquals(
                Long.valueOf(scenario[6]),
                credits.get(PORT_EAST)
            );

            Map<EnergyPortId, Long> snapshot = Map.copyOf(credits);

            manager.updateAvailablePorts(List.of(
                PORT_EAST,
                PORT_SOUTH,
                PORT_NORTH
            ));

            assertEquals(
                snapshot,
                credits
            );
        }
    }


    @Test
    void shouldUseAllAvailableEnergyWithThreeConsumers() {
        EnergyDistributionManager manager = managerWithPorts(
            PORT_NORTH,
            PORT_SOUTH,
            PORT_EAST
        );

        List<EnergyPortId> ports = List.of(
            PORT_NORTH,
            PORT_SOUTH,
            PORT_EAST
        );

        Map<EnergyPortId, Integer> availability = Map.of(
            PORT_NORTH,
            80,
            PORT_SOUTH,
            80,
            PORT_EAST,
            80
        );

        for (long tick = 100; tick < 320; tick++) {
            int storedEnergy = 40;
            int remainingBudget = 80;

            int[] extractedByPort = new int[3];

            manager.updatePushAvailability(
                availability,
                tick
            );

            int shift = Math.floorMod(
                tick,
                ports.size()
            );

            for (int i = 0; i < ports.size(); i++) {
                int index = (i + shift) % ports.size();

                EnergyPortId portId = ports.get(index);

                int extracted = pullEnergy(
                    manager,
                    portId,
                    80,
                    tick,
                    storedEnergy,
                    remainingBudget
                );

                extractedByPort[index] = extracted;

                storedEnergy -= extracted;
                remainingBudget -= extracted;
            }

            int totalExtracted = 40 - storedEnergy;

            assertTrue(
                totalExtracted <= 40,
                "Передано больше доступной энергии"
            );

            assertTrue(
                remainingBudget >= 0,
                "Превышен глобальный бюджет 80 FE/t"
            );

            if (tick >= 120) {
                assertEquals(
                    40,
                    totalExtracted,
                    "Не вся доступная энергия передана!" +
                        " tick=" +
                        tick +
                        " NORTH=" +
                        extractedByPort[0] +
                        " SOUTH=" +
                        extractedByPort[1] +
                        " EAST=" +
                        extractedByPort[2] +
                        " remainingEnergy=" +
                        storedEnergy
                );
            }
        }
    }
}
