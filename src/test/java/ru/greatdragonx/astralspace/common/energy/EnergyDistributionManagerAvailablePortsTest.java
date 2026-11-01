package ru.greatdragonx.astralspace.common.energy;

import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.junit.jupiter.api.Test;
import ru.greatdragonx.astralspace.common.energy.port.EnergyPortId;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EnergyDistributionManagerAvailablePortsTest {

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
}

