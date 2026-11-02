package ru.greatdragonx.astralspace.common.energy;

import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandlerUtil;
import net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler;
import net.neoforged.neoforge.transfer.transaction.RootCommitJournal;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.junit.jupiter.api.Test;
import ru.greatdragonx.astralspace.common.energy.port.EnergyPortId;

import java.lang.reflect.Field;
import java.util.*;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Deterministic integration fixture for the supplied energy classes, NOT a Minecraft GameTest.
 * Assumes synchronous calls and independent consumers accepting up to 80 FE per operation.
 * Copies the energy call sequence from CoalGeneratorBlockEntity, not its Minecraft world plumbing.
 * Failing tests describe desired regressions; production sources must not be replaced with this file.
 */
public class EnergyLifecycleRegressionTest {
    static final int FIRST_TICK = 100;
    static final int LAST_TICK = 900;
    static final int WARM_TICK = 300;

    record Tick(
        int tick,
        int start,
        int beforePush,
        int push,
        int afterPush,
        int generation,
        int transferred,
        int end,
        List<Integer> received,
        List<Long> credits
    ) {}

    static final class Rig {
        final EnergyDistributionManager manager = new EnergyDistributionManager(80);
        final EnergyExtractionBudget budget = new EnergyExtractionBudget(80);
        final EnergyTransferTracker tracker = new EnergyTransferTracker();
        final SimpleEnergyHandler buffer = new SimpleEnergyHandler(
            10_000,
            0,
            80
        );
        final List<EnergyPortId> ports;
        final List<SimpleEnergyHandler> sinks = new ArrayList<>();
        final List<EnergyHandler> outputs = new ArrayList<>();
        final int initial;
        List<EnergyPortId> enabled;
        List<Integer> pushOrder, beforeOrder, afterOrder;
        long tick, generatedTotal, receivedTotal;
        int committedThisTick;
        int[] receivedThisTick;

        Rig(
            int n,
            int initial,
            List<Integer> before,
            List<Integer> after
        )
        {
            this.initial = initial;
            buffer.set(initial);
            ports = IntStream.range(
                    0,
                    n
                )
                .mapToObj(i -> new EnergyPortId("test." + (char) ('A' + i)))
                .toList();
            enabled = ports;
            pushOrder = IntStream.range(
                    0,
                    n
                )
                .boxed()
                .toList();
            beforeOrder = before;
            afterOrder = after;
            receivedThisTick = new int[n];
            manager.updateAvailablePorts(enabled);
            for (int i = 0; i < n; i++) {
                sinks.add(new SimpleEnergyHandler(
                    1_000_000,
                    80,
                    80
                ));
                final int index = i;
                outputs.add(new EnergyHandler() {
                    public long getAmountAsLong() {return buffer.getAmountAsLong();}

                    public long getCapacityAsLong() {return buffer.getCapacityAsLong();}

                    public int insert(
                        int amount,
                        TransactionContext tx
                    )
                    {return 0;}

                    public int extract(
                        int amount,
                        TransactionContext tx
                    )
                    {
                        if (amount < 0) throw new IllegalArgumentException();
                        EnergyPortId port = ports.get(index);
                        if (!enabled.contains(port)) return 0;
                        manager.recordRequest(
                            port,
                            amount,
                            tick,
                            tx
                        );
                        int adaptive = manager.getAllowedAdaptiveAmount(
                            port,
                            amount,
                            budget.getRemaining(tick),
                            buffer.getAmountAsInt(),
                            tick,
                            tx
                        );
                        int allowed = budget.getAllowedAmount(
                            adaptive,
                            tick
                        );
                        if (allowed == 0) return 0;
                        int extracted = buffer.extract(
                            allowed,
                            tx
                        );
                        if (extracted > 0) {
                            budget.recordExtraction(
                                extracted,
                                tick,
                                tx
                            );
                            manager.recordExtraction(
                                port,
                                extracted,
                                tick,
                                tx
                            );
                            final long eventTick = tick;
                            new RootCommitJournal(() -> {
                                tracker.recordTransfer(
                                    extracted,
                                    eventTick
                                );
                                committedThisTick += extracted;
                                receivedThisTick[index] += extracted;
                                receivedTotal += extracted;
                            }).updateSnapshots(tx);
                        }
                        return extracted;
                    }
                });
            }
        }

        void setEnabled(List<EnergyPortId> list) {
            if (Transaction.getLifecycle() != Transaction.Lifecycle.NONE)
                throw new AssertionError("configuration inside transaction");
            enabled = List.copyOf(list);
            manager.updateAvailablePorts(enabled);
        }

        void setPortEnabled(
            int index,
            boolean value
        )
        {
            setEnabled(ports.stream()
                .filter(p -> value || !p.equals(ports.get(index)))
                .toList());
        }

        void move(int index) {
            if (!enabled.contains(ports.get(index))) return;
            try (Transaction tx = Transaction.openRoot()) {
                int transferred = EnergyHandlerUtil.move(
                    outputs.get(index),
                    sinks.get(index),
                    80,
                    tx
                );
                if (transferred > 0) tx.commit();
            }
        }

        Tick step(int t) {
            tick = t;
            committedThisTick = 0;
            receivedThisTick = new int[ports.size()];
            int start = buffer.getAmountAsInt();
            for (int p : beforeOrder) move(p);
            int beforePush = buffer.getAmountAsInt();
            List<Integer> order = new ArrayList<>(pushOrder.stream()
                .filter(p -> enabled.contains(ports.get(p)))
                .toList());
            if (!order.isEmpty()) Collections.rotate(
                order,
                -Math.floorMod(
                    t,
                    order.size()
                )
            );
            Map<EnergyPortId, Integer> availability = new LinkedHashMap<>();
            for (int p : order) {
                try (Transaction probe = Transaction.openRoot()) {
                    int accepted = sinks.get(p)
                        .insert(
                            80,
                            probe
                        );
                    if (accepted > 0) availability.put(
                        ports.get(p),
                        accepted
                    );
                }
            }
            manager.updatePushAvailability(
                availability,
                t
            );
            for (int p : order) move(p);
            int afterPush = buffer.getAmountAsInt();
            int generation = Math.min(
                40,
                10_000 - afterPush
            );
            buffer.set(afterPush + generation);
            generatedTotal += generation;

            if (generation > 0 && budget.getRemaining(t) > 0) {
                Map<EnergyPortId, Integer> postGenerationAvailability = new LinkedHashMap<>();

                for (int p : order) {
                    try (Transaction probe = Transaction.openRoot()) {
                        int accepted = sinks.get(p)
                            .insert(
                                80,
                                probe
                            );

                        if (accepted > 0) {
                            postGenerationAvailability.put(
                                ports.get(p),
                                accepted
                            );
                        }
                    }
                }

                manager.updatePushAvailability(
                    postGenerationAvailability,
                    t
                );

                for (int p : order) {
                    move(p);
                }
            }

            for (int p : afterOrder) move(p);
            int end = buffer.getAmountAsInt();
            assertEquals(
                start + generation - committedThisTick,
                end,
                "per tick conservation: " + t
            );
            assertEquals(
                initial + generatedTotal - receivedTotal,
                end,
                "cumulative conservation: " + t
            );
            assertEquals(
                receivedTotal,
                sinks.stream()
                    .mapToLong(SimpleEnergyHandler::getAmountAsLong)
                    .sum(),
                "receiver balance: " + t
            );
            assertTrue(
                committedThisTick >= 0 && committedThisTick <= 80,
                "global limit: " + t
            );
            assertEquals(
                80 - committedThisTick,
                budget.getRemaining(t),
                "shared budget: " + t
            );
            assertEquals(
                committedThisTick,
                tracker.getCurrentTransferRate(t + 1),
                "committed tracker: " + t
            );
            var credit = manager.getFairnessCreditsSnapshot();
            return new Tick(
                t,
                start,
                beforePush,
                beforePush - afterPush,
                afterPush,
                generation,
                committedThisTick,
                end,
                Arrays.stream(receivedThisTick)
                    .boxed()
                    .toList(),
                ports.stream()
                    .map(p -> credit.getOrDefault(
                        p,
                        0L
                    ))
                    .toList()
            );
        }
    }

    static List<Tick> run(
        Rig r,
        int offTick,
        int duration
    )
    {
        List<Tick> rows = new ArrayList<>();
        for (int t = FIRST_TICK; t < LAST_TICK; t++) {
            if (t == offTick) r.setPortEnabled(
                0,
                false
            );
            if (t == offTick + duration) r.setPortEnabled(
                0,
                true
            );
            rows.add(r.step(t));
        }
        return rows;
    }

    static void assertStable(List<Tick> rows) {
        for (Tick row : rows)
            if (row.tick() >= WARM_TICK) assertEquals(
                40,
                row.transferred(),
                row.toString()
            );
    }

    @Test
    void creditMustNotBeGrantedAgainAfterThePortConsumedItsAdjustedShare() {
        EnergyDistributionManager m = new EnergyDistributionManager(80);
        EnergyPortId a = new EnergyPortId("A"), b = new EnergyPortId("B");
        m.updateAvailablePorts(List.of(
            a,
            b
        ));
        for (int t = 98; t <= 99; t++)
            try (Transaction tx = Transaction.openRoot()) {
                m.recordRequest(
                    a,
                    80,
                    t,
                    tx
                );
                m.recordRequest(
                    b,
                    80,
                    t,
                    tx
                );
                if (t == 99) {
                    m.recordExtraction(
                        a,
                        10,
                        t,
                        tx
                    );
                    m.recordExtraction(
                        b,
                        30,
                        t,
                        tx
                    );
                }
                tx.commit();
            }
        try (Transaction tx = Transaction.openRoot()) {
            m.recordRequest(
                a,
                80,
                100,
                tx
            );
            assertEquals(
                30,
                m.getAllowedAdaptiveAmount(
                    a,
                    80,
                    80,
                    40,
                    100,
                    tx
                )
            );
            m.recordExtraction(
                a,
                30,
                100,
                tx
            );
            assertEquals(
                10L,
                m.getFairnessCreditsSnapshot()
                    .get(a)
            );
            assertEquals(
                0,
                m.getAllowedAdaptiveAmount(
                    a,
                    80,
                    50,
                    10,
                    100,
                    tx
                ),
                "A already received base 20 + credit 10; remaining 10 belongs to B"
            );
        }
    }

    @Test
    void firstCommittedRequestMustNotEraseCreditJustEarnedFromObservedPushDemand() {
        EnergyDistributionManager m = new EnergyDistributionManager(80);
        EnergyPortId a = new EnergyPortId("A"), b = new EnergyPortId("B"), c =
            new EnergyPortId("C");
        m.updateAvailablePorts(List.of(
            a,
            b,
            c
        ));
        m.updatePushAvailability(
            Map.of(
                a,
                80,
                b,
                80,
                c,
                80
            ),
            99
        );
        for (int t = 98; t <= 99; t++)
            try (Transaction tx = Transaction.openRoot()) {
                m.recordRequest(
                    a,
                    80,
                    t,
                    tx
                );
                m.recordRequest(
                    c,
                    80,
                    t,
                    tx
                );
                if (t == 99) m.recordExtraction(
                    c,
                    14,
                    t,
                    tx
                );
                tx.commit();
            }
        try (Transaction tx = Transaction.openRoot()) {
            m.recordRequest(
                a,
                80,
                100,
                tx
            );
            m.getAllowedAdaptiveAmount(
                a,
                80,
                80,
                26,
                100,
                tx
            );
            assertEquals(
                5L,
                m.getFairnessCreditsSnapshot()
                    .get(b)
            );
            m.recordRequest(
                b,
                80,
                100,
                tx
            );
            assertEquals(
                5L,
                m.getFairnessCreditsSnapshot()
                    .getOrDefault(
                        b,
                        0L
                    ),
                "B was observed accepting Push in the previous tick; this is not an old inactive-session credit"
            );
        }
    }

    @Test
    void fourConsumersShouldNotLockInto34And46() {
        Rig r = new Rig(
            4,
            16,
            List.of(
                0,
                2
            ),
            List.of(
                1,
                3
            )
        );
        assertStable(run(
            r,
            -100,
            2
        ));
    }

    @Test
    void sameTwoTickSideToggleShouldNotChooseDifferentPersistentModes() {
        for (int off : List.of(
            130,
            131
        )) {
            Rig r = new Rig(
                4,
                0,
                List.of(0),
                List.of(
                    1,
                    2,
                    3
                )
            );
            assertStable(run(
                r,
                off,
                2
            ));
        }
    }

    @Test
    void threeMixedConsumersShouldNotLockInto39Then38Then43() {
        Rig r = new Rig(
            3,
            0,
            List.of(0),
            List.of(2)
        ); // B accepts Push only.
        r.pushOrder = List.of(
            2,
            0,
            1
        );
        assertStable(run(
            r,
            -100,
            2
        ));
    }

    @Test
    void steady40MustNotHideStarvationOfThreeSaturatedConsumers() {
        Rig r = new Rig(
            4,
            60,
            List.of(0),
            List.of(
                1,
                2,
                3
            )
        );
        List<Tick> rows = run(
            r,
            -100,
            2
        );
//        assertStable(rows);
        long[] totals = new long[4];
        for (Tick row : rows)
            if (row.tick() >= WARM_TICK) for (int p = 0; p < 4; p++)
                totals[p] += row.received()
                    .get(p);
        assertTrue(
            Arrays.stream(totals)
                .max()
                .orElseThrow() -
                Arrays.stream(totals)
                    .min()
                    .orElseThrow() <= 80,
            "steady 40 does not imply fairness: " + Arrays.toString(totals)
        );
    }

    @Test
    void preserveEnergyBudgetAndCommittedTrackerAcrossOneToFivePortsAndReconfiguration() {
        for (int n = 1; n <= 5; n++)
            for (int initial : List.of(
                0,
                60,
                120
            ))
                for (boolean mixed : List.of(
                    false,
                    true
                )) {
                    List<Integer> pre = IntStream.range(
                            0,
                            n
                        )
                        .filter(p -> mixed && p % 2 == 0)
                        .boxed()
                        .toList();
                    List<Integer> post = IntStream.range(
                            0,
                            n
                        )
                        .filter(p -> !mixed || p % 2 != 0)
                        .boxed()
                        .toList();
                    Rig r = new Rig(
                        n,
                        initial,
                        pre,
                        post
                    );
                    for (int t = 100; t < 220; t++) {
                        if (t == 130) r.setPortEnabled(
                            0,
                            false
                        );
                        if (t == 133) r.setPortEnabled(
                            0,
                            true
                        );
                        if (t == 150) r.setEnabled(List.of());
                        if (t == 153) r.setEnabled(r.ports);
                        r.step(t);
                    }
                }
    }

    static Map<String, Object> distributionState(EnergyDistributionManager manager) throws Exception
    {
        Map<String, Object> state = new TreeMap<>();
        for (Field f : EnergyDistributionManager.class.getDeclaredFields()) {
            if (java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
            f.setAccessible(true);
            Object value = f.get(manager);
            if (value instanceof Map<?, ?> map) state.put(
                f.getName(),
                new LinkedHashMap<>(map)
            );
            else if (value instanceof List<?> list) state.put(
                f.getName(),
                List.copyOf(list)
            );
            else if (value instanceof Number) state.put(
                f.getName(),
                value
            );
        }
        return state;
    }

    @Test
    void rootAbortMustUndoCommittedChildMovesIncludingDemandUsageCreditsAndCallbacks()
        throws Exception
    {
        Rig r = new Rig(
            3,
            120,
            List.of(),
            List.of(
                0,
                1,
                2
            )
        );
        r.tick = 100;
        r.manager.updatePushAvailability(
            Map.of(
                r.ports.get(0),
                80,
                r.ports.get(1),
                80,
                r.ports.get(2),
                80
            ),
            100
        );
        var before = distributionState(r.manager);
        try (Transaction root = Transaction.openRoot()) {
            assertTrue(EnergyHandlerUtil.move(
                r.outputs.get(0),
                r.sinks.get(0),
                80,
                root
            ) > 0);
            assertTrue(EnergyHandlerUtil.move(
                r.outputs.get(1),
                r.sinks.get(1),
                80,
                root
            ) > 0);
            assertEquals(
                0,
                r.receivedTotal,
                "callbacks must wait for root commit"
            );
        }
        assertEquals(
            before,
            distributionState(r.manager)
        );
        assertEquals(
            120,
            r.buffer.getAmountAsInt()
        );
        assertEquals(
            80,
            r.budget.getRemaining(100)
        );
        assertEquals(
            0,
            r.sinks.stream()
                .mapToInt(SimpleEnergyHandler::getAmountAsInt)
                .sum()
        );
        assertEquals(
            0,
            r.tracker.getCurrentTransferRate(101)
        );
        assertEquals(
            0,
            r.receivedTotal
        );
        try (Transaction tx = Transaction.openRoot()) {
            assertTrue(EnergyHandlerUtil.move(
                r.outputs.get(0),
                r.sinks.get(0),
                80,
                tx
            ) > 0);
            tx.commit();
        }
        assertEquals(
            r.receivedTotal,
            120 - r.buffer.getAmountAsInt()
        );
        assertEquals(
            r.receivedTotal,
            r.tracker.getCurrentTransferRate(101)
        );
    }

    /**
     * Small trace entry point for the analysis; tests themselves require JUnit 5.
     */
    public static void main(String[] args) {
        Map<String, Rig> cases = new LinkedHashMap<>();
        cases.put(
            "four_cycle_34_46",
            new Rig(
                4,
                16,
                List.of(
                    0,
                    2
                ),
                List.of(
                    1,
                    3
                )
            )
        );
        cases.put(
            "four_same_connections_40",
            new Rig(
                4,
                20,
                List.of(
                    0,
                    2
                ),
                List.of(
                    1,
                    3
                )
            )
        );
        cases.put(
            "stable_but_starved",
            new Rig(
                4,
                60,
                List.of(0),
                List.of(
                    1,
                    2,
                    3
                )
            )
        );
        Rig three = new Rig(
            3,
            0,
            List.of(0),
            List.of(2)
        );
        three.pushOrder = List.of(
            2,
            0,
            1
        );
        cases.put(
            "three_cycle_39_38_43",
            three
        );
        cases.put(
            "toggle_130",
            new Rig(
                4,
                0,
                List.of(0),
                List.of(
                    1,
                    2,
                    3
                )
            )
        );
        cases.put(
            "toggle_131",
            new Rig(
                4,
                0,
                List.of(0),
                List.of(
                    1,
                    2,
                    3
                )
            )
        );
        cases.forEach((name, r) -> {
            List<Tick> rows = run(
                r,
                name.equals("toggle_130") ? 130 : name.equals("toggle_131") ? 131 : -100,
                2
            );
            System.out.println(name);
            rows.stream()
                .filter(row -> row.tick() >= 888)
                .forEach(System.out::println);
            long[] totals = new long[r.ports.size()];
            for (Tick row : rows)
                if (row.tick() >= 300) for (int p = 0; p < totals.length; p++)
                    totals[p] += row.received()
                        .get(p);
            System.out.println("last600ByPort=" + Arrays.toString(totals));
        });
    }
}
