package ru.greatdragonx.astralspace.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import ru.greatdragonx.astralspace.AstralSpace;
import ru.greatdragonx.astralspace.common.energy.port.EnergyPortMode;
import ru.greatdragonx.astralspace.common.fluid.port.FluidPortMode;
import ru.greatdragonx.astralspace.common.machine.MachineSide;
import ru.greatdragonx.astralspace.modules.energy.EnergyBlocks;
import ru.greatdragonx.astralspace.modules.energy.blockentities.LavaGeneratorBlockEntity;
import ru.greatdragonx.astralspace.modules.energy.blocks.LavaGeneratorBlock;
import ru.greatdragonx.astralspace.modules.energy.blocks.state.LavaGeneratorFrontState;
import ru.greatdragonx.astralspace.modules.energy.menus.LavaGeneratorMenu;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Дополнительные GameTests для Lava Generator Mk I.
 *
 * <p>Существующие четыре жидкостных теста в LavaGeneratorGameTests НЕ заменяет.
 * Тесты используют реальную серверную BE, реальную capability или экземпляр BE,
 * восстановленный через BlockEntity.loadStatic. Для точной проверки длительности
 * горения serverTick вызывается вручную без ожидания 500 игровых тиков.
 * Тестовые приёмники FE регистрируются ТОЛЬКО в gameTest-моде.</p>
 */
public final class LavaGeneratorMechanicsGameTests {
    private static final BlockPos GENERATOR_POS = new BlockPos(1, 1, 1);
    private static final BlockPos BACK_SINK_POS = new BlockPos(1, 1, 2); // NORTH -> BACK = SOUTH
    private static final BlockPos LEFT_SINK_POS = new BlockPos(2, 1, 1); // NORTH -> LEFT = EAST
    private static final Identifier ENVIRONMENT_ID = Identifier.fromNamespaceAndPath(
        AstralSpace.MOD_ID, "lava_generator/mechanics_environment"
    );
    private static final Identifier STRUCTURE_ID = Identifier.fromNamespaceAndPath(
        AstralSpace.MOD_ID, "gametest/empty_3x3x3"
    );

    public static final DeferredRegister<Consumer<GameTestHelper>> TEST_FUNCTIONS =
        DeferredRegister.create(BuiltInRegistries.TEST_FUNCTION, AstralSpace.MOD_ID);

    private record Entry(
        Identifier id,
        DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> function,
        int timeout
    ) {}
    private static final List<Entry> TESTS = new ArrayList<>();

    static {
        add("no_lava_no_generation", LavaGeneratorMechanicsGameTests::noLavaNoGeneration);
        add("insufficient_lava_99", LavaGeneratorMechanicsGameTests::insufficientLava99);
        add("exact_lava_100", LavaGeneratorMechanicsGameTests::exactLava100);
        add("no_water_in_lava_tank", LavaGeneratorMechanicsGameTests::noWaterInLavaTank);
        add("lava_tank_capacity_8000", LavaGeneratorMechanicsGameTests::lavaTankCapacity);
        add("front_running_normal", LavaGeneratorMechanicsGameTests::frontRunningNormal);
        add("front_running_low_at_threshold", LavaGeneratorMechanicsGameTests::frontRunningLowAtThreshold);
        add("fuel_spent_only_on_cycle_start", LavaGeneratorMechanicsGameTests::fuelSpentOnlyOnCycleStart);
        add("cycle_exact_500_ticks", LavaGeneratorMechanicsGameTests::cycleExact500Ticks);
        add("cycle_restarts_after_500", LavaGeneratorMechanicsGameTests::cycleRestartsAfter500);
        add("energy_capacity_exact_2000_ticks", LavaGeneratorMechanicsGameTests::energyCapacityExact2000Ticks);
        add("buffer_full_no_new_fuel", LavaGeneratorMechanicsGameTests::bufferFullNoNewFuel);
        add("buffer_full_pauses_active_burn", LavaGeneratorMechanicsGameTests::bufferFullPausesActiveBurn);
        add("free_space_59_pauses", LavaGeneratorMechanicsGameTests::freeSpace59Pauses);
        add("free_space_60_generates", LavaGeneratorMechanicsGameTests::freeSpace60Generates);
        add("energy_default_capability", LavaGeneratorMechanicsGameTests::defaultEnergyCapability);
        add("energy_rotates_with_facing", LavaGeneratorMechanicsGameTests::energyRotatesWithFacing);
        add("energy_mode_changes_and_cached_handler", LavaGeneratorMechanicsGameTests::energyModeChangesAndCachedHandler);
        add("energy_output_refuses_insert", LavaGeneratorMechanicsGameTests::energyOutputRefusesInsert);
        add("pull_rollback_restores_energy_and_budget", LavaGeneratorMechanicsGameTests::pullRollbackRestoresBudget);
        add("pull_max_120_per_tick", LavaGeneratorMechanicsGameTests::pullMax120PerTick);
        add("pull_budget_resets_next_tick", LavaGeneratorMechanicsGameTests::pullBudgetResetsNextTick);
        add("push_one_sink", LavaGeneratorMechanicsGameTests::pushOneSink);
        add("push_disabled_side", LavaGeneratorMechanicsGameTests::pushDisabledSide);
        add("push_two_sinks_shared_limit", LavaGeneratorMechanicsGameTests::pushTwoSinksSharedLimit);
        add("push_two_sinks_fair_over_time", LavaGeneratorMechanicsGameTests::pushTwoSinksFairOverTime);
        add("push_pull_shared_limit", LavaGeneratorMechanicsGameTests::pushPullSharedLimit);
        add("full_buffer_resumes_after_push", LavaGeneratorMechanicsGameTests::fullBufferResumesAfterPush);
        add("persist_energy_burn_and_ports", LavaGeneratorMechanicsGameTests::persistEnergyBurnAndPorts);
        add("nbt_clamps_invalid_values", LavaGeneratorMechanicsGameTests::nbtClampsInvalidValues);
        add("invalid_energy_port_nbt", LavaGeneratorMechanicsGameTests::invalidEnergyPortNbt);
        add("menu_energy_16_bit_reassembly", LavaGeneratorMechanicsGameTests::menuEnergy16BitReassembly);
        add("model_data_reflects_front_state", LavaGeneratorMechanicsGameTests::modelDataReflectsFrontState);
    }

    private LavaGeneratorMechanicsGameTests() {}

    private static void add(String name, Consumer<GameTestHelper> method) {
        String path = "lava_generator/" + name;
        TESTS.add(new Entry(
            Identifier.fromNamespaceAndPath(AstralSpace.MOD_ID, path),
            TEST_FUNCTIONS.register(path, () -> method),
            100
        ));
    }

    public static void registerGameTests(RegisterGameTestsEvent event) {
        Holder<TestEnvironmentDefinition> environment = event.registerEnvironment(
            ENVIRONMENT_ID,
            new TestEnvironmentDefinition.AllOf(List.of())
        );
        for (Entry test : TESTS) {
            event.registerTest(test.id(), new FunctionGameTestInstance(
                test.function().getKey(),
                new TestData<>(
                    environment,
                    STRUCTURE_ID,
                    test.timeout(),
                    0,
                    true,
                    Rotation.NONE,
                    false,
                    1,
                    1,
                    false
                )
            ));
        }
    }

    // ---------- Инфраструктура сценариев ----------

    private static LavaGeneratorBlockEntity placeGenerator(GameTestHelper helper) {
        helper.setBlock(
            GENERATOR_POS,
            EnergyBlocks.LAVA_GENERATOR.get()
                .defaultBlockState()
                .setValue(LavaGeneratorBlock.FACING, Direction.NORTH)
        );
        return helper.getBlockEntity(GENERATOR_POS, LavaGeneratorBlockEntity.class);
    }

    private static BlockPos absolutePos(GameTestHelper helper) {
        return helper.absolutePos(GENERATOR_POS);
    }

    private static ResourceHandler<FluidResource> fluidInput(GameTestHelper helper) {
        ResourceHandler<FluidResource> input = helper.getLevel().getCapability(
            Capabilities.Fluid.BLOCK,
            absolutePos(helper),
            Direction.WEST // FACING=NORTH: local RIGHT
        );
        check(helper, input != null, "Нет жидкостной capability RIGHT");
        return input;
    }

    private static int insertLava(GameTestHelper helper, int requested) {
        ResourceHandler<FluidResource> input = fluidInput(helper);
        int inserted;
        try (Transaction tx = Transaction.openRoot()) {
            inserted = input.insert(0, FluidResource.of(Fluids.LAVA), requested, tx);
            if (inserted > 0) tx.commit();
        }
        return inserted;
    }

    private static EnergyHandler energy(GameTestHelper helper, Direction worldSide) {
        EnergyHandler handler = helper.getLevel().getCapability(
            Capabilities.Energy.BLOCK,
            absolutePos(helper),
            worldSide
        );
        check(helper, handler != null, "Энергетическая capability отсутствует на " + worldSide);
        return handler;
    }

    private static CompoundTag snapshot(GameTestHelper helper, LavaGeneratorBlockEntity generator) {
        return generator.saveWithFullMetadata(helper.getLevel().registryAccess());
    }

    private static int burnLeft(GameTestHelper helper, LavaGeneratorBlockEntity generator) {
        return snapshot(helper, generator).getIntOr("burn_time", 0);
    }

    private static int burnDuration(GameTestHelper helper, LavaGeneratorBlockEntity generator) {
        return snapshot(helper, generator).getIntOr("burn_duration", 0);
    }

    private static void ticks(GameTestHelper helper, LavaGeneratorBlockEntity generator, int count) {
        ServerLevel level = helper.getLevel();
        for (int i = 0; i < count; i++) {
            LavaGeneratorBlockEntity.serverTick(
                level,
                generator.getBlockPos(),
                generator.getBlockState(),
                generator
            );
        }
    }

    /** Полный цикл без соседей оставляет ровно 30000 FE. */
    private static LavaGeneratorBlockEntity chargedOneCycle(GameTestHelper helper) {
        LavaGeneratorBlockEntity generator = placeGenerator(helper);
        equal(helper, 100L, insertLava(helper, 100), "Вставка 100 mB для зарядки");
        ticks(helper, generator, 500);
        equal(helper, 30_000L, generator.getEnergyStored(), "Энергия полного цикла");
        equal(helper, 0L, generator.getLavaStored(), "Остаток лавы после цикла");
        return generator;
    }

    /** Сериализует BlockEntity, меняет NBT и создаёт НОВЫЙ экземпляр. */
    private static LavaGeneratorBlockEntity detachedFromNbt(
        GameTestHelper helper,
        LavaGeneratorBlockEntity source,
        Consumer<CompoundTag> edit
    ) {
        CompoundTag tag = snapshot(helper, source);
        edit.accept(tag);
        BlockEntity loaded = BlockEntity.loadStatic(
            source.getBlockPos(),
            source.getBlockState(),
            tag,
            helper.getLevel().registryAccess()
        );
        check(helper, loaded instanceof LavaGeneratorBlockEntity, "BlockEntity.loadStatic не восстановил Lava Generator");
        LavaGeneratorBlockEntity clone = (LavaGeneratorBlockEntity) loaded;
        clone.setLevel(helper.getLevel());
        return clone;
    }

    private static void check(GameTestHelper helper, boolean valid, String message) {
        if (!valid) helper.fail(message);
    }

    private static void equal(GameTestHelper helper, long expected, long actual, String what) {
        if (expected != actual) helper.fail(what + ": ожидалось " + expected + ", получено " + actual);
    }

    private static void state(GameTestHelper helper, LavaGeneratorFrontState expected, LavaGeneratorBlockEntity entity) {
        if (entity.getFrontState() != expected) {
            helper.fail("frontState: ожидалось " + expected + ", получено " + entity.getFrontState());
        }
    }

    private static void success(GameTestHelper helper) { helper.succeed(); }

    // ---------- Горение и жидкость ----------

    private static void noLavaNoGeneration(GameTestHelper helper) {
        LavaGeneratorBlockEntity generator = placeGenerator(helper);
        ticks(helper, generator, 1);
        equal(helper, 0, generator.getEnergyStored(), "Генерация без топлива");
        equal(helper, 0, burnLeft(helper, generator), "Таймер без топлива");
        state(helper, LavaGeneratorFrontState.OFF, generator);
        success(helper);
    }

    private static void insufficientLava99(GameTestHelper helper) {
        LavaGeneratorBlockEntity generator = placeGenerator(helper);
        equal(helper, 99, insertLava(helper, 99), "Вставка 99 mB");
        ticks(helper, generator, 1);
        equal(helper, 99, generator.getLavaStored(), "99 mB не должны тратиться");
        equal(helper, 0, generator.getEnergyStored(), "99 mB недостаточно для генерации");
        equal(helper, 0, burnLeft(helper, generator), "Таймер без полной порции");
        success(helper);
    }

    private static void exactLava100(GameTestHelper helper) {
        LavaGeneratorBlockEntity generator = placeGenerator(helper);
        equal(helper, 100, insertLava(helper, 100), "Вставка ровно 100 mB");
        ticks(helper, generator, 1);
        equal(helper, 0, generator.getLavaStored(), "100 mB списаны при старте");
        equal(helper, 60, generator.getEnergyStored(), "Первый активный тик");
        equal(helper, 499, burnLeft(helper, generator), "Оставшиеся тики");
        equal(helper, 500, burnDuration(helper, generator), "Длительность цикла");
        state(helper, LavaGeneratorFrontState.LOW_LAVA, generator);
        success(helper);
    }

    private static void noWaterInLavaTank(GameTestHelper helper) {
        LavaGeneratorBlockEntity generator = placeGenerator(helper);
        int inserted;
        try (Transaction tx = Transaction.openRoot()) {
            inserted = fluidInput(helper).insert(0, FluidResource.of(Fluids.WATER), 1000, tx);
            if (inserted > 0) tx.commit();
        }
        equal(helper, 0, inserted, "Вода не должна приниматься");
        equal(helper, 0, generator.getLavaStored(), "Бак после попытки залить воду");
        success(helper);
    }

    private static void lavaTankCapacity(GameTestHelper helper) {
        LavaGeneratorBlockEntity generator = placeGenerator(helper);
        equal(helper, 8000, insertLava(helper, 9000), "Ограничение бака 8000 mB");
        equal(helper, 0, insertLava(helper, 1000), "Полный бак не принимает лаву");
        equal(helper, 8000, generator.getLavaStored(), "Количество в полном баке");
        success(helper);
    }

    private static void frontRunningNormal(GameTestHelper helper) {
        LavaGeneratorBlockEntity generator = placeGenerator(helper);
        equal(helper, 1200, insertLava(helper, 1200), "Загрузка 1200 mB");
        ticks(helper, generator, 1);
        equal(helper, 1100, generator.getLavaStored(), "Остаток после старта");
        state(helper, LavaGeneratorFrontState.RUNNING, generator);
        success(helper);
    }

    private static void frontRunningLowAtThreshold(GameTestHelper helper) {
        LavaGeneratorBlockEntity generator = placeGenerator(helper);
        equal(helper, 1100, insertLava(helper, 1100), "Загрузка 1100 mB");
        ticks(helper, generator, 1);
        equal(helper, 1000, generator.getLavaStored(), "Ровно граница low lava");
        state(helper, LavaGeneratorFrontState.LOW_LAVA, generator);
        success(helper);
    }

    private static void fuelSpentOnlyOnCycleStart(GameTestHelper helper) {
        LavaGeneratorBlockEntity generator = placeGenerator(helper);
        equal(helper, 300, insertLava(helper, 300), "Вставка 300 mB");
        ticks(helper, generator, 20);
        equal(helper, 200, generator.getLavaStored(), "Одна порция на 20 активных тиков");
        equal(helper, 1200, generator.getEnergyStored(), "20 тиков по 60 FE");
        equal(helper, 480, burnLeft(helper, generator), "Таймер после 20 тиков");
        success(helper);
    }

    private static void cycleExact500Ticks(GameTestHelper helper) {
        LavaGeneratorBlockEntity generator = placeGenerator(helper);
        equal(helper, 100, insertLava(helper, 100), "Вставка одной порции");
        ticks(helper, generator, 500);
        equal(helper, 0, generator.getLavaStored(), "Бак после цикла");
        equal(helper, 30_000, generator.getEnergyStored(), "500 × 60 FE");
        equal(helper, 0, burnLeft(helper, generator), "Таймер после 500 тиков");
        equal(helper, 500, burnDuration(helper, generator), "Длительность сохраняется");
        success(helper);
    }

    private static void cycleRestartsAfter500(GameTestHelper helper) {
        LavaGeneratorBlockEntity generator = placeGenerator(helper);
        equal(helper, 200, insertLava(helper, 200), "Две порции");
        ticks(helper, generator, 500);
        equal(helper, 100, generator.getLavaStored(), "Вторая порция ещё в баке");
        equal(helper, 0, burnLeft(helper, generator), "Первый цикл закончился");
        ticks(helper, generator, 1);
        equal(helper, 0, generator.getLavaStored(), "Вторые 100 mB списаны");
        equal(helper, 499, burnLeft(helper, generator), "Новый цикл начался");
        equal(helper, 30_060, generator.getEnergyStored(), "Первая генерация следующего цикла");
        success(helper);
    }

    private static void energyCapacityExact2000Ticks(GameTestHelper helper) {
        LavaGeneratorBlockEntity generator = placeGenerator(helper);
        equal(helper, 1000, insertLava(helper, 1000), "Одно ведро лавы");
        ticks(helper, generator, 2000);
        equal(helper, 120_000, generator.getEnergyStored(), "Ёмкость после 2000 тиков");
        equal(helper, 600, generator.getLavaStored(), "Четыре порции по 100 mB");
        equal(helper, 0, burnLeft(helper, generator), "Четвёртый цикл завершён");
        ticks(helper, generator, 1);
        equal(helper, 120_000, generator.getEnergyStored(), "Нельзя переполнить буфер");
        equal(helper, 600, generator.getLavaStored(), "Пятый цикл не должен начаться");
        state(helper, LavaGeneratorFrontState.OFF, generator);
        success(helper);
    }

    // ---------- Пауза и границы буфера ----------

    private static void bufferFullNoNewFuel(GameTestHelper helper) {
        LavaGeneratorBlockEntity live = placeGenerator(helper);
        equal(helper, 100, insertLava(helper, 100), "Вставка 100 mB");
        LavaGeneratorBlockEntity full = detachedFromNbt(helper, live, nbt -> {
            nbt.putLong("energy_stored", 120_000L);
            nbt.putInt("burn_time", 0);
            nbt.putInt("burn_duration", 0);
        });
        ticks(helper, full, 1);
        equal(helper, 120_000, full.getEnergyStored(), "Полный буфер");
        equal(helper, 100, full.getLavaStored(), "100 mB нельзя потратить впустую");
        equal(helper, 0, burnLeft(helper, full), "Новый цикл не запускается");
        state(helper, LavaGeneratorFrontState.OFF, full);
        success(helper);
    }

    private static void bufferFullPausesActiveBurn(GameTestHelper helper) {
        LavaGeneratorBlockEntity live = placeGenerator(helper);
        equal(helper, 300, insertLava(helper, 300), "Загрузка лавы");
        LavaGeneratorBlockEntity full = detachedFromNbt(helper, live, nbt -> {
            nbt.putLong("energy_stored", 120_000L);
            nbt.putInt("burn_time", 250);
            nbt.putInt("burn_duration", 500);
            nbt.putString("front_state", "RUNNING");
        });
        ticks(helper, full, 3);
        equal(helper, 120_000, full.getEnergyStored(), "Полный буфер при паузе");
        equal(helper, 300, full.getLavaStored(), "Лава сохраняется");
        equal(helper, 250, burnLeft(helper, full), "Таймер заморожен");
        state(helper, LavaGeneratorFrontState.OFF, full);
        success(helper);
    }

    private static void freeSpace59Pauses(GameTestHelper helper) {
        LavaGeneratorBlockEntity live = placeGenerator(helper);
        LavaGeneratorBlockEntity nearFull = detachedFromNbt(helper, live, nbt -> {
            nbt.putLong("energy_stored", 119_941L);
            nbt.putInt("burn_time", 250);
            nbt.putInt("burn_duration", 500);
        });
        ticks(helper, nearFull, 1);
        equal(helper, 119_941, nearFull.getEnergyStored(), "59 свободных FE недостаточно");
        equal(helper, 250, burnLeft(helper, nearFull), "Таймер при нехватке места");
        state(helper, LavaGeneratorFrontState.OFF, nearFull);
        success(helper);
    }

    private static void freeSpace60Generates(GameTestHelper helper) {
        LavaGeneratorBlockEntity live = placeGenerator(helper);
        LavaGeneratorBlockEntity nearFull = detachedFromNbt(helper, live, nbt -> {
            nbt.putLong("energy_stored", 119_940L);
            nbt.putInt("burn_time", 250);
            nbt.putInt("burn_duration", 500);
        });
        ticks(helper, nearFull, 1);
        equal(helper, 120_000, nearFull.getEnergyStored(), "Ровно 60 FE свободного места");
        equal(helper, 249, burnLeft(helper, nearFull), "Таймер уменьшился на один тик");
        state(helper, LavaGeneratorFrontState.LOW_LAVA, nearFull);
        success(helper);
    }

    // ---------- Energy capability, Pull ----------

    private static void defaultEnergyCapability(GameTestHelper helper) {
        LavaGeneratorBlockEntity generator = placeGenerator(helper);
        check(helper, generator.getEnergyPortMode(MachineSide.BACK) == EnergyPortMode.OUTPUT,
            "BACK должен быть OUTPUT по умолчанию");
        check(helper, generator.getEnergyPortMode(MachineSide.LEFT) == EnergyPortMode.DISABLED,
            "LEFT должен быть DISABLED по умолчанию");
        check(helper, generator.getEnergyPortMode(MachineSide.FRONT) == EnergyPortMode.DISABLED,
            "FRONT не имеет энергетического порта");
        EnergyHandler back = energy(helper, Direction.SOUTH);
        equal(helper, 120_000, back.getCapacityAsLong(), "Вместимость энергетической capability");
        equal(helper, 0, back.getAmountAsLong(), "Пустой буфер");
        check(helper, helper.getLevel().getCapability(Capabilities.Energy.BLOCK,
            absolutePos(helper), Direction.NORTH) == null, "FRONT должен возвращать null");
        check(helper, helper.getLevel().getCapability(Capabilities.Energy.BLOCK,
            absolutePos(helper), Direction.EAST) == null, "LEFT DISABLED должен возвращать null");
        success(helper);
    }

    private static void energyRotatesWithFacing(GameTestHelper helper) {
        for (Direction facing : new Direction[]{Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST}) {
            helper.setBlock(GENERATOR_POS, EnergyBlocks.LAVA_GENERATOR.get()
                .defaultBlockState().setValue(LavaGeneratorBlock.FACING, facing));
            Direction back = facing.getOpposite();
            EnergyHandler handler = helper.getLevel().getCapability(
                Capabilities.Energy.BLOCK, absolutePos(helper), back
            );
            check(helper, handler != null, "Порт BACK отсутствует при FACING=" + facing);
            EnergyHandler front = helper.getLevel().getCapability(
                Capabilities.Energy.BLOCK, absolutePos(helper), facing
            );
            check(helper, front == null, "FRONT не должен выдавать энергию при FACING=" + facing);
        }
        success(helper);
    }

    private static void energyModeChangesAndCachedHandler(GameTestHelper helper) {
        LavaGeneratorBlockEntity generator = chargedOneCycle(helper);
        EnergyHandler cachedBack = energy(helper, Direction.SOUTH);
        check(helper, generator.changeEnergyPortMode(MachineSide.BACK, EnergyPortMode.DISABLED),
            "BACK -> DISABLED не сработал");
        check(helper, helper.getLevel().getCapability(Capabilities.Energy.BLOCK,
            absolutePos(helper), Direction.SOUTH) == null, "Отключённый BACK ещё выдаёт capability");
        try (Transaction tx = Transaction.openRoot()) {
            equal(helper, 0, cachedBack.extract(10, tx), "Старая ссылка на BACK должна быть заблокирована");
        }
        check(helper, generator.changeEnergyPortMode(MachineSide.BACK, EnergyPortMode.OUTPUT),
            "BACK -> OUTPUT не сработал");
        EnergyHandler back = energy(helper, Direction.SOUTH);
        try (Transaction tx = Transaction.openRoot()) {
            int pulled = back.extract(40, tx);
            equal(helper, 40, pulled, "После включения OUTPUT извлечение должно заработать");
            tx.commit();
        }
        success(helper);
    }

    private static void energyOutputRefusesInsert(GameTestHelper helper) {
        LavaGeneratorBlockEntity generator = chargedOneCycle(helper);
        EnergyHandler handler = energy(helper, Direction.SOUTH);
        try (Transaction tx = Transaction.openRoot()) {
            equal(helper, 0, handler.insert(120, tx), "Выходной порт не принимает энергию");
        }
        equal(helper, 30_000, generator.getEnergyStored(), "Вставка не изменила буфер");
        success(helper);
    }

    private static void pullRollbackRestoresBudget(GameTestHelper helper) {
        LavaGeneratorBlockEntity generator = chargedOneCycle(helper);
        EnergyHandler handler = energy(helper, Direction.SOUTH);
        try (Transaction tx = Transaction.openRoot()) {
            equal(helper, 120, handler.extract(120, tx), "Пробное извлечение 120 FE");
            // Без commit — откат энергии, бюджета и статистики распределения.
        }
        equal(helper, 30_000, generator.getEnergyStored(), "Откат должен вернуть 120 FE");
        try (Transaction tx = Transaction.openRoot()) {
            equal(helper, 120, handler.extract(120, tx), "Бюджет тоже должен откатиться");
            tx.commit();
        }
        equal(helper, 29_880, generator.getEnergyStored(), "Энергия после commit");
        success(helper);
    }

    private static void pullMax120PerTick(GameTestHelper helper) {
        LavaGeneratorBlockEntity generator = chargedOneCycle(helper);
        EnergyHandler handler = energy(helper, Direction.SOUTH);
        try (Transaction tx = Transaction.openRoot()) {
            equal(helper, 80, handler.extract(80, tx), "Первая Pull-передача");
            tx.commit();
        }
        try (Transaction tx = Transaction.openRoot()) {
            equal(helper, 40, handler.extract(80, tx), "Вторая Pull-передача ограничена остатком");
            tx.commit();
        }
        try (Transaction tx = Transaction.openRoot()) {
            equal(helper, 0, handler.extract(1, tx), "Лимит 120 FE/t исчерпан");
        }
        equal(helper, 29_880, generator.getEnergyStored(), "Общее извлечение 120 FE");
        success(helper);
    }

    private static void pullBudgetResetsNextTick(GameTestHelper helper) {
        chargedOneCycle(helper);
        EnergyHandler handler = energy(helper, Direction.SOUTH);
        try (Transaction tx = Transaction.openRoot()) {
            equal(helper, 120, handler.extract(120, tx), "Лимит текущего тика");
            tx.commit();
        }
        helper.runAfterDelay(2, () -> {
            try (Transaction tx = Transaction.openRoot()) {
                equal(helper, 120, handler.extract(120, tx), "Бюджет нового игрового тика");
                tx.commit();
            }
            helper.succeed();
        });
    }

    // ---------- Push, взаимодействие с соседями ----------

    private static void pushOneSink(GameTestHelper helper) {
        LavaGeneratorBlockEntity generator = chargedOneCycle(helper);
        GameTestEnergySink sink = GameTestEnergySink.place(helper, BACK_SINK_POS, 100_000);
        ticks(helper, generator, 1);
        equal(helper, 120, sink.getAmountAsLong(), "Push к одному соседу");
        equal(helper, 29_880, generator.getEnergyStored(), "Извлечение через Push");
        success(helper);
    }

    private static void pushDisabledSide(GameTestHelper helper) {
        LavaGeneratorBlockEntity generator = chargedOneCycle(helper);
        check(helper, generator.changeEnergyPortMode(MachineSide.BACK, EnergyPortMode.DISABLED),
            "Не удалось выключить BACK");
        GameTestEnergySink sink = GameTestEnergySink.place(helper, BACK_SINK_POS, 100_000);
        ticks(helper, generator, 1);
        equal(helper, 0, sink.getAmountAsLong(), "Push через DISABLED недопустим");
        equal(helper, 30_000, generator.getEnergyStored(), "Буфер после отключения выхода");
        success(helper);
    }

    private static void pushTwoSinksSharedLimit(GameTestHelper helper) {
        LavaGeneratorBlockEntity generator = chargedOneCycle(helper);
        check(helper, generator.changeEnergyPortMode(MachineSide.LEFT, EnergyPortMode.OUTPUT),
            "Не включился LEFT OUTPUT");
        GameTestEnergySink a = GameTestEnergySink.place(helper, BACK_SINK_POS, 100_000);
        GameTestEnergySink b = GameTestEnergySink.place(helper, LEFT_SINK_POS, 100_000);
        ticks(helper, generator, 1);
        long total = a.getAmountAsLong() + b.getAmountAsLong();
        check(helper, total > 0 && total <= 120,
            "Сумма Push в одном тике должна быть в пределах 1..120 FE, получено " + total);
        equal(helper, 30_000 - total, generator.getEnergyStored(), "Сохранение энергии между двумя выходами");
        success(helper);
    }

    private static void pushTwoSinksFairOverTime(GameTestHelper helper) {
        chargedOneCycle(helper);
        LavaGeneratorBlockEntity generator = helper.getBlockEntity(GENERATOR_POS, LavaGeneratorBlockEntity.class);
        check(helper, generator.changeEnergyPortMode(MachineSide.LEFT, EnergyPortMode.OUTPUT),
            "Не включился LEFT OUTPUT");
        GameTestEnergySink a = GameTestEnergySink.place(helper, BACK_SINK_POS, 100_000);
        GameTestEnergySink b = GameTestEnergySink.place(helper, LEFT_SINK_POS, 100_000);
        // Дальше генератор работает обычным тикером мира; GameTest ждёт 12 тиков.
        helper.runAfterDelay(12, () -> {
            long fromBack = a.getAmountAsLong();
            long fromLeft = b.getAmountAsLong();
            check(helper, fromBack > 0, "BACK не получил энергии за 12 тиков");
            check(helper, fromLeft > 0, "LEFT голодает: нет передачи за 12 тиков");
            check(helper, fromBack + fromLeft <= 120L * 14,
                "Нарушен общий FE/t за время наблюдения");
            helper.succeed();
        });
    }

    private static void pushPullSharedLimit(GameTestHelper helper) {
        LavaGeneratorBlockEntity generator = chargedOneCycle(helper);
        GameTestEnergySink sink = GameTestEnergySink.place(helper, BACK_SINK_POS, 100_000);
        check(helper, generator.changeEnergyPortMode(MachineSide.LEFT, EnergyPortMode.OUTPUT),
            "LEFT OUTPUT не включился");
        EnergyHandler pullPort = energy(helper, Direction.EAST); // LEFT
        int pulled;
        try (Transaction tx = Transaction.openRoot()) {
            pulled = pullPort.extract(60, tx);
            equal(helper, 60, pulled, "Pull должен извлечь 60 FE");
            tx.commit();
        }
        ticks(helper, generator, 1); // Push оставшегося бюджета
        long pushed = sink.getAmountAsLong();
        check(helper, pushed > 0, "Push не должен голодать после Pull=60 FE");
        check(helper, pushed + pulled <= 120,
            "Push + Pull превысили общие 120 FE/t: " + (pushed + pulled));
        equal(helper, 30_000 - pushed - pulled, generator.getEnergyStored(), "Сохранение энергии Push/Pull");
        success(helper);
    }

    private static void fullBufferResumesAfterPush(GameTestHelper helper) {
        LavaGeneratorBlockEntity live = placeGenerator(helper);
        equal(helper, 100, insertLava(helper, 100), "Топливо для возобновления");
        LavaGeneratorBlockEntity full = detachedFromNbt(helper, live,
            nbt -> nbt.putLong("energy_stored", 120_000L));
        GameTestEnergySink sink = GameTestEnergySink.place(helper, BACK_SINK_POS, 100_000);
        ticks(helper, full, 1);
        long pushed = sink.getAmountAsLong();
        check(helper, pushed >= 60 && pushed <= 120,
            "Из полного буфера должно уйти достаточно энергии для генерации");
        equal(helper, 120_000 - pushed + 60, full.getEnergyStored(), "После Push + генерации");
        equal(helper, 0, full.getLavaStored(), "100 mB списаны после освобождения места");
        equal(helper, 499, burnLeft(helper, full), "Горение возобновилось");
        state(helper, LavaGeneratorFrontState.LOW_LAVA, full);
        success(helper);
    }

    // ---------- NBT, синхронизация интерфейса ----------

    private static void persistEnergyBurnAndPorts(GameTestHelper helper) {
        LavaGeneratorBlockEntity original = placeGenerator(helper);
        equal(helper, 1200, insertLava(helper, 1200), "Топливо перед сохранением");
        ticks(helper, original, 10);
        check(helper, original.changeEnergyPortMode(MachineSide.LEFT, EnergyPortMode.OUTPUT),
            "Не удалось включить LEFT");
        check(helper, original.changeEnergyPortMode(MachineSide.BACK, EnergyPortMode.DISABLED),
            "Не удалось отключить BACK");
        check(helper, original.changeFluidPortMode(MachineSide.TOP, FluidPortMode.INPUT),
            "Не удалось включить TOP INPUT");

        LavaGeneratorBlockEntity restored = detachedFromNbt(helper, original, nbt -> {});
        equal(helper, 600, restored.getEnergyStored(), "Сохранение энергии");
        equal(helper, 1100, restored.getLavaStored(), "Сохранение лавы");
        equal(helper, 490, burnLeft(helper, restored), "Сохранение burnTime");
        equal(helper, 500, burnDuration(helper, restored), "Сохранение burnDuration");
        state(helper, LavaGeneratorFrontState.RUNNING, restored);
        check(helper, restored.getEnergyPortMode(MachineSide.LEFT) == EnergyPortMode.OUTPUT,
            "LEFT OUTPUT не восстановился");
        check(helper, restored.getEnergyPortMode(MachineSide.BACK) == EnergyPortMode.DISABLED,
            "BACK DISABLED не восстановился");
        check(helper, restored.getFluidPortMode(MachineSide.TOP) == FluidPortMode.INPUT,
            "TOP INPUT не восстановился");
        // Проверяем работу восстановленного координатора/доступности порта.
        EnergyHandler left = restored.getEnergyHandlerForWorldSide(Direction.EAST);
        check(helper, left != null, "После загрузки не появился LEFT handler");
        try (Transaction tx = Transaction.openRoot()) {
            int pulled = left.extract(60, tx);
            equal(helper, 60, pulled, "Pull после восстановления NBT");
            tx.commit();
        }
        success(helper);
    }

    private static void nbtClampsInvalidValues(GameTestHelper helper) {
        LavaGeneratorBlockEntity source = placeGenerator(helper);
        LavaGeneratorBlockEntity negative = detachedFromNbt(helper, source, nbt -> {
            nbt.putLong("energy_stored", -100L);
            nbt.putInt("burn_time", -10);
            nbt.putInt("burn_duration", 500);
            nbt.putString("front_state", "GARBAGE");
        });
        equal(helper, 0, negative.getEnergyStored(), "Отрицательная энергия должна стать 0");
        equal(helper, 0, burnLeft(helper, negative), "Отрицательный burnTime");
        equal(helper, 500, burnDuration(helper, negative), "Валидная длительность 500");
        state(helper, LavaGeneratorFrontState.OFF, negative);

        LavaGeneratorBlockEntity over = detachedFromNbt(helper, source, nbt -> {
            nbt.putLong("energy_stored", 999_999L);
            nbt.putInt("burn_time", 700);
            nbt.putInt("burn_duration", 200);
        });
        equal(helper, 120_000, over.getEnergyStored(), "Энергия ограничена буфером");
        equal(helper, 200, burnLeft(helper, over), "burnTime ограничен burnDuration");
        equal(helper, 200, burnDuration(helper, over), "Восстановленная длительность");
        success(helper);
    }

    private static void invalidEnergyPortNbt(GameTestHelper helper) {
        LavaGeneratorBlockEntity source = placeGenerator(helper);
        LavaGeneratorBlockEntity restored = detachedFromNbt(helper, source, nbt -> {
            CompoundTag ports = new CompoundTag();
            ports.putString("lava_generator.back", "UNKNOWN"); // игнорировать, default BACK OUTPUT
            ports.putString("lava_generator.left", "INPUT");   // недопустимый режим
            ports.putString("lava_generator.top", "OUTPUT");  // разрешённый режим
            ports.putString("lava_generator.unknown", "OUTPUT");
            ports.putInt("lava_generator.right", 123);        // некорректный NBT-тип
            nbt.put("energy_ports", ports);
        });
        check(helper, restored.getEnergyPortMode(MachineSide.BACK) == EnergyPortMode.OUTPUT,
            "Некорректный BACK должен сохранить OUTPUT по умолчанию");
        check(helper, restored.getEnergyPortMode(MachineSide.LEFT) == EnergyPortMode.DISABLED,
            "Недопустимый INPUT для LEFT нужно игнорировать");
        check(helper, restored.getEnergyPortMode(MachineSide.TOP) == EnergyPortMode.OUTPUT,
            "Валидный TOP OUTPUT должен восстановиться");
        check(helper, restored.getEnergyPortMode(MachineSide.RIGHT) == EnergyPortMode.DISABLED,
            "Числовой режим RIGHT игнорируется");
        check(helper, restored.getEnergyHandlerForWorldSide(Direction.UP) != null,
            "После NBT загрузки отсутствует TOP OUTPUT handler");
        CompoundTag savedAgain = snapshot(helper, restored).getCompoundOrEmpty("energy_ports");
        check(helper, !savedAgain.contains("lava_generator.unknown"),
            "Неизвестный ID порта не должен сохраниться");
        success(helper);
    }

    private static void menuEnergy16BitReassembly(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        SimpleContainerData data = new SimpleContainerData(LavaGeneratorBlockEntity.DATA_COUNT);
        data.set(LavaGeneratorBlockEntity.DATA_LAVA_STORED, 8000);
        // Имитируем 16-битную передачу контейнерных данных со знаком.
        long expected = 120_000L;
        data.set(LavaGeneratorBlockEntity.DATA_ENERGY_LOW, (short) (expected & 0xFFFF));
        data.set(LavaGeneratorBlockEntity.DATA_ENERGY_HIGH, (short) (expected >>> 16));
        data.set(LavaGeneratorBlockEntity.DATA_BURN_TIME, 250);
        data.set(LavaGeneratorBlockEntity.DATA_BURN_DURATION, 500);
        data.set(LavaGeneratorBlockEntity.DATA_FRONT_STATE, LavaGeneratorFrontState.LOW_LAVA.ordinal());
        LavaGeneratorMenu menu = new LavaGeneratorMenu(
            0, player.getInventory(), ContainerLevelAccess.NULL, data
        );
        equal(helper, 120_000, menu.getEnergyStored(), "Сборка двух 16-битных значений");
        equal(helper, 120_000, menu.getEnergyCapacity(), "Ёмкость в GUI");
        equal(helper, 8000, menu.getLavaStored(), "Лава в GUI");
        equal(helper, 250, menu.getBurnTime(), "burnTime в GUI");
        equal(helper, 500, menu.getBurnDuration(), "burnDuration в GUI");
        check(helper, menu.getFrontState() == LavaGeneratorFrontState.LOW_LAVA,
            "Enum frontState должен восстановиться из ContainerData");
        data.set(LavaGeneratorBlockEntity.DATA_FRONT_STATE, 999);
        check(helper, menu.getFrontState() == LavaGeneratorFrontState.OFF,
            "Неизвестный ordinal frontState должен давать OFF");
        menu.removed(player);
        success(helper);
    }

    private static void modelDataReflectsFrontState(GameTestHelper helper) {
        LavaGeneratorBlockEntity generator = placeGenerator(helper);
        check(helper, generator.getModelData().get(LavaGeneratorBlockEntity.FRONT_STATE)
                == LavaGeneratorFrontState.OFF,
            "ModelData должен содержать OFF при создании");
        equal(helper, 1200, insertLava(helper, 1200), "Топливо");
        ticks(helper, generator, 1);
        check(helper, generator.getModelData().get(LavaGeneratorBlockEntity.FRONT_STATE)
                == LavaGeneratorFrontState.RUNNING,
            "ModelData должен отражать RUNNING после генерации");
        success(helper);
    }
}
