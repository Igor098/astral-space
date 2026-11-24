package ru.greatdragonx.astralspace.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import ru.greatdragonx.astralspace.AstralSpace;
import ru.greatdragonx.astralspace.modules.energy.EnergyBlocks;
import ru.greatdragonx.astralspace.modules.energy.blocks.LavaGeneratorBlock;
import ru.greatdragonx.astralspace.modules.energy.blockentities.LavaGeneratorBlockEntity;
import ru.greatdragonx.astralspace.common.machine.MachineSide;
import ru.greatdragonx.astralspace.common.fluid.port.FluidPortMode;

import java.util.List;
import java.util.function.Consumer;

/**
 * Игровые интеграционные тесты жидкостных capability Lava Generator Mk I.
 *
 * <p>Тест работает с реальным {@link ServerLevel} и запрашивает
 * {@link Capabilities.Fluid#BLOCK} через NeoForge, а не напрямую
 * через внутренние поля или методы хранилища BlockEntity.</p>
 *
 * <p>В Minecraft 1.21.11 тестовая функция регистрируется отдельно
 * от самого {@link FunctionGameTestInstance}. Пустой шаблон находится
 * в data/astralspace/structure/gametest/empty_3x3x3.nbt.</p>
 */
public final class LavaGeneratorGameTests {
    private static final Identifier TEST_ID = Identifier.fromNamespaceAndPath(
        AstralSpace.MOD_ID,
        "lava_generator/input_side"
    );
    private static final Identifier DYNAMIC_MODES_TEST_ID = Identifier.fromNamespaceAndPath(
        AstralSpace.MOD_ID,
        "lava_generator/dynamic_fluid_port_modes"
    );
    private static final Identifier PERSISTENCE_TEST_ID = Identifier.fromNamespaceAndPath(
        AstralSpace.MOD_ID,
        "lava_generator/fluid_persistence"
    );
    private static final Identifier INVALID_PORT_NBT_TEST_ID = Identifier.fromNamespaceAndPath(
        AstralSpace.MOD_ID,
        "lava_generator/invalid_fluid_port_nbt"
    );
    private static final Identifier ENVIRONMENT_ID = Identifier.fromNamespaceAndPath(
        AstralSpace.MOD_ID,
        "lava_generator/default_environment"
    );
    private static final Identifier STRUCTURE_ID = Identifier.fromNamespaceAndPath(
        AstralSpace.MOD_ID,
        "gametest/empty_3x3x3"
    );

    /**
     * Реестр тестовых функций. Регистрируется отдельным тестовым модом
     * AstralSpaceGameTestMod, а не production-классом AstralSpace.
     */
    public static final DeferredRegister<Consumer<GameTestHelper>> TEST_FUNCTIONS =
        DeferredRegister.create(BuiltInRegistries.TEST_FUNCTION, AstralSpace.MOD_ID);

    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>>
        INPUT_SIDE_TEST_FUNCTION = TEST_FUNCTIONS.register(
            "lava_generator/input_side",
            () -> LavaGeneratorGameTests::shouldAcceptLavaOnlyFromConfiguredInputSide
        );

    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>>
        DYNAMIC_MODES_TEST_FUNCTION = TEST_FUNCTIONS.register(
            "lava_generator/dynamic_fluid_port_modes",
            () -> LavaGeneratorGameTests::shouldApplyFluidPortModeChangesWithoutRecreatingHandlers
        );

    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>>
        PERSISTENCE_TEST_FUNCTION = TEST_FUNCTIONS.register(
            "lava_generator/fluid_persistence",
            () -> LavaGeneratorGameTests::shouldRestoreFluidAndPortModesInNewBlockEntity
        );

    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>>
        INVALID_PORT_NBT_TEST_FUNCTION = TEST_FUNCTIONS.register(
            "lava_generator/invalid_fluid_port_nbt",
            () -> LavaGeneratorGameTests::shouldIgnoreInvalidAndUnknownFluidPortModes
        );

    private LavaGeneratorGameTests() {
    }

    /**
     * Регистрирует среду и игровые тесты через событие NeoForge.
     *
     * @param event событие регистрации GameTests
     */
    public static void registerGameTests(RegisterGameTestsEvent event) {
        Holder<TestEnvironmentDefinition> environment = event.registerEnvironment(
            ENVIRONMENT_ID,
            new TestEnvironmentDefinition.AllOf(List.of())
        );

        event.registerTest(
            TEST_ID,
            new FunctionGameTestInstance(
                INPUT_SIDE_TEST_FUNCTION.getKey(),
                new TestData<>(
                    environment,
                    STRUCTURE_ID,
                    100,           // max_ticks
                    0,             // setup_ticks
                    true,          // required
                    Rotation.NONE,
                    false,         // manual_only: запускать на GameTestServer
                    1,             // max_attempts
                    1,             // required_successes
                    false          // sky_access
                )
            )
        );

        event.registerTest(
            DYNAMIC_MODES_TEST_ID,
            new FunctionGameTestInstance(
                DYNAMIC_MODES_TEST_FUNCTION.getKey(),
                new TestData<>(
                    environment,
                    STRUCTURE_ID,
                    100,           // max_ticks
                    0,             // setup_ticks
                    true,          // required
                    Rotation.NONE,
                    false,         // manual_only
                    1,             // max_attempts
                    1,             // required_successes
                    false          // sky_access
                )
            )
        );
        event.registerTest(
            PERSISTENCE_TEST_ID,
            new FunctionGameTestInstance(
                PERSISTENCE_TEST_FUNCTION.getKey(),
                new TestData<>(
                    environment,
                    STRUCTURE_ID,
                    100,
                    0,
                    true,
                    Rotation.NONE,
                    false,
                    1,
                    1,
                    false
                )
            )
        );

        event.registerTest(
            INVALID_PORT_NBT_TEST_ID,
            new FunctionGameTestInstance(
                INVALID_PORT_NBT_TEST_FUNCTION.getKey(),
                new TestData<>(
                    environment,
                    STRUCTURE_ID,
                    100,
                    0,
                    true,
                    Rotation.NONE,
                    false,
                    1,
                    1,
                    false
                )
            )
        );
    }

    /**
     * Проверяет интеграцию: установка блока -> получение capability
     * по мировому направлению -> транзакционное заполнение лавой.
     *
     * <p>При направлении лицевой грани NORTH локальная сторона RIGHT
     * соответствует WEST, а BACK соответствует SOUTH. По умолчанию
     * ввод лавы разрешён только через RIGHT.</p>
     *
     * @param helper контекст реального игрового GameTest
     */
    private static void shouldAcceptLavaOnlyFromConfiguredInputSide(
        GameTestHelper helper
    ) {
        BlockPos relativePos = new BlockPos(1, 1, 1);
        helper.setBlock(
            relativePos,
            EnergyBlocks.LAVA_GENERATOR.get()
                .defaultBlockState()
                .setValue(LavaGeneratorBlock.FACING, Direction.NORTH)
        );

        ServerLevel level = helper.getLevel();
        BlockPos worldPos = helper.absolutePos(relativePos);

        ResourceHandler<FluidResource> input = level.getCapability(
            Capabilities.Fluid.BLOCK,
            worldPos,
            Direction.WEST
        );
        if (input == null) {
            helper.fail("Отсутствует жидкостная capability на локальной стороне RIGHT");
            return;
        }

        ResourceHandler<FluidResource> disabled = level.getCapability(
            Capabilities.Fluid.BLOCK,
            worldPos,
            Direction.SOUTH
        );
        if (disabled == null) {
            helper.fail("На зарегистрированной стороне BACK должен существовать обработчик");
            return;
        }

        ResourceHandler<FluidResource> front = level.getCapability(
            Capabilities.Fluid.BLOCK,
            worldPos,
            Direction.NORTH
        );
        if (front != null) {
            helper.fail("На стороне FRONT capability должна отсутствовать");
            return;
        }

        FluidResource lava = FluidResource.of(Fluids.LAVA);

        try (Transaction tx = Transaction.openRoot()) {
            int inserted = input.insert(0, lava, 1_000, tx);
            if (inserted != 1_000) {
                helper.fail("Через RIGHT ожидалась вставка 1000 mB лавы, получено: " + inserted);
                return;
            }
            tx.commit();
        }

        long actual = input.getAmountAsLong(0);
        if (actual != 1_000L) {
            helper.fail("После commit в резервуаре должно быть 1000 mB, фактически: " + actual);
            return;
        }

        try (Transaction tx = Transaction.openRoot()) {
            int inserted = disabled.insert(0, lava, 1_000, tx);
            if (inserted != 0) {
                helper.fail("Порт BACK в режиме DISABLED принял лаву: " + inserted);
                return;
            }
            tx.commit();
        }

        if (input.getAmountAsLong(0) != 1_000L) {
            helper.fail("Вставка через DISABLED изменила содержимое резервуара");
            return;
        }

        helper.succeed();
    }

    /**
     * Проверяет динамическое изменение режимов жидкостных портов
     * без пересоздания обработчиков и без потери общей жидкости.
     *
     * <p>Для генератора, направленного на NORTH, локальная RIGHT —
     * это мировая WEST, а локальная LEFT — мировая EAST.</p>
     *
     * <p>Сценарий: RIGHT INPUT +1000 → DISABLED запрещает вставку →
     * SERVICE извлекает 400 → LEFT INPUT добавляет 500.
     * Итог: 1100 mB лавы в едином резервуаре.</p>
     *
     * @param helper контекст игрового теста
     */
    private static void shouldApplyFluidPortModeChangesWithoutRecreatingHandlers(
        GameTestHelper helper
    )
    {
        BlockPos relativePos = new BlockPos(1, 1, 1);
        helper.setBlock(
            relativePos,
            EnergyBlocks.LAVA_GENERATOR.get()
                .defaultBlockState()
                .setValue(LavaGeneratorBlock.FACING, Direction.NORTH)
        );

        ServerLevel level = helper.getLevel();
        BlockPos worldPos = helper.absolutePos(relativePos);
        if (!(level.getBlockEntity(worldPos) instanceof LavaGeneratorBlockEntity generator)) {
            helper.fail("Отсутствует LavaGeneratorBlockEntity");
            return;
        }

        // Сохраняем ссылку: режимы должны менять поведение, а не объект handler.
        ResourceHandler<FluidResource> rightHandler = level.getCapability(
            Capabilities.Fluid.BLOCK,
            worldPos,
            Direction.WEST
        );
        if (rightHandler == null) {
            helper.fail("Не найдена жидкостная capability локальной стороны RIGHT");
            return;
        }

        FluidResource lava = FluidResource.of(Fluids.LAVA);
        try (Transaction tx = Transaction.openRoot()) {
            int inserted = rightHandler.insert(0, lava, 1_000, tx);
            if (inserted != 1_000) {
                helper.fail("В режиме RIGHT INPUT ожидалась вставка 1000 mB, получено: " + inserted);
                return;
            }
            tx.commit();
        }
        if (rightHandler.getAmountAsLong(0) != 1_000L) {
            helper.fail("После первой транзакции резервуар должен содержать 1000 mB");
            return;
        }

        if (!generator.changeFluidPortMode(MachineSide.RIGHT, FluidPortMode.DISABLED)) {
            helper.fail("Не удалось переключить RIGHT из INPUT в DISABLED");
            return;
        }

        ResourceHandler<FluidResource> afterDisable = level.getCapability(
            Capabilities.Fluid.BLOCK,
            worldPos,
            Direction.WEST
        );
        if (afterDisable != rightHandler) {
            helper.fail("После смены режима RIGHT обработчик capability был заменён");
            return;
        }

        try (Transaction tx = Transaction.openRoot()) {
            int inserted = rightHandler.insert(0, lava, 500, tx);
            if (inserted != 0) {
                helper.fail("RIGHT DISABLED принял жидкость: " + inserted);
                return;
            }
            tx.commit();
        }
        if (rightHandler.getAmountAsLong(0) != 1_000L) {
            helper.fail("RIGHT DISABLED изменил количество лавы");
            return;
        }

        if (!generator.changeFluidPortMode(MachineSide.RIGHT, FluidPortMode.SERVICE)) {
            helper.fail("Не удалось переключить RIGHT из DISABLED в SERVICE");
            return;
        }

        try (Transaction tx = Transaction.openRoot()) {
            int extracted = rightHandler.extract(0, lava, 400, tx);
            if (extracted != 400) {
                helper.fail("Через RIGHT SERVICE ожидалось извлечение 400 mB, получено: " + extracted);
                return;
            }
            tx.commit();
        }
        if (rightHandler.getAmountAsLong(0) != 600L) {
            helper.fail("После сервисного извлечения должно остаться 600 mB");
            return;
        }

        if (!generator.changeFluidPortMode(MachineSide.LEFT, FluidPortMode.INPUT)) {
            helper.fail("Не удалось переключить LEFT из DISABLED в INPUT");
            return;
        }

        ResourceHandler<FluidResource> leftHandler = level.getCapability(
            Capabilities.Fluid.BLOCK,
            worldPos,
            Direction.EAST
        );
        if (leftHandler == null) {
            helper.fail("Не найдена capability локальной стороны LEFT");
            return;
        }

        try (Transaction tx = Transaction.openRoot()) {
            int inserted = leftHandler.insert(0, lava, 500, tx);
            if (inserted != 500) {
                helper.fail("Через LEFT INPUT ожидалась вставка 500 mB, получено: " + inserted);
                return;
            }
            tx.commit();
        }

        long actual = rightHandler.getAmountAsLong(0);
        if (actual != 1_100L || leftHandler.getAmountAsLong(0) != 1_100L) {
            helper.fail("Общий резервуар должен содержать 1100 mB, получено: " + actual);
            return;
        }

        helper.succeed();
    }

    /**
     * Проверяет сохранение резервуара и конфигурации портов в NBT,
     * затем восстановление в НОВОМ экземпляре BlockEntity.
     *
     * <p>Путь данных соответствует сохранению BlockEntity на диск:
     * saveWithFullMetadata -> BlockEntity.loadStatic. Сам GameTest
     * не перезагружает чанк или весь мир с физического диска.</p>
     *
     * <p>После восстановления TOP должен принимать лаву, RIGHT —
     * оставаться отключённым, а количество в общем резервуаре —
     * равняться 3500 mB.</p>
     *
     * @param helper контекст серверного игрового теста
     */
    private static void shouldRestoreFluidAndPortModesInNewBlockEntity(
        GameTestHelper helper
    )
    {
        BlockPos relativePos = new BlockPos(1, 1, 1);
        helper.setBlock(
            relativePos,
            EnergyBlocks.LAVA_GENERATOR.get()
                .defaultBlockState()
                .setValue(LavaGeneratorBlock.FACING, Direction.NORTH)
        );

        ServerLevel level = helper.getLevel();
        BlockPos worldPos = helper.absolutePos(relativePos);
        if (!(level.getBlockEntity(worldPos) instanceof LavaGeneratorBlockEntity original)) {
            helper.fail("Не найдена исходная LavaGeneratorBlockEntity");
            return;
        }

        // Сначала используем реально зарегистрированную capability NeoForge.
        ResourceHandler<FluidResource> originalRight = level.getCapability(
            Capabilities.Fluid.BLOCK,
            worldPos,
            Direction.WEST // FACING=NORTH: локальная RIGHT
        );
        if (originalRight == null) {
            helper.fail("Не найдена capability исходного порта RIGHT");
            return;
        }

        FluidResource lava = FluidResource.of(Fluids.LAVA);
        try (Transaction tx = Transaction.openRoot()) {
            int inserted = originalRight.insert(0, lava, 3_500, tx);
            if (inserted != 3_500) {
                helper.fail("До сохранения ожидалась вставка 3500 mB, получено: " + inserted);
                return;
            }
            tx.commit();
        }

        if (originalRight.getAmountAsLong(0) != 3_500L) {
            helper.fail("До сохранения резервуар должен содержать 3500 mB");
            return;
        }

        if (!original.changeFluidPortMode(MachineSide.RIGHT, FluidPortMode.DISABLED)) {
            helper.fail("Не удалось отключить RIGHT до сохранения");
            return;
        }
        if (!original.changeFluidPortMode(MachineSide.TOP, FluidPortMode.INPUT)) {
            helper.fail("Не удалось включить TOP до сохранения");
            return;
        }

        // Полные данные BlockEntity, включая ID типа, как при сохранении чанка.
        CompoundTag saved = original.saveWithFullMetadata(level.registryAccess());
        BlockEntity loaded = BlockEntity.loadStatic(
            worldPos,
            original.getBlockState(),
            saved,
            level.registryAccess()
        );
        if (!(loaded instanceof LavaGeneratorBlockEntity restored)) {
            helper.fail("NBT не восстановил LavaGeneratorBlockEntity нового экземпляра");
            return;
        }
        if (restored == original) {
            helper.fail("Восстановлен тот же экземпляр BlockEntity, а нужен новый");
            return;
        }

        // Новый объект не помещён в чанк: задаём ему мир для проверки
        // серверных операций с портами после десериализации.
        restored.setLevel(level);

        if (restored.getFluidPortMode(MachineSide.RIGHT) != FluidPortMode.DISABLED) {
            helper.fail("После загрузки RIGHT должен остаться DISABLED");
            return;
        }
        if (restored.getFluidPortMode(MachineSide.TOP) != FluidPortMode.INPUT) {
            helper.fail("После загрузки TOP должен остаться INPUT");
            return;
        }

        // Новый объект ещё не установлен в чанк, поэтому его handler
        // получаем через метод провайдера, а не level.getCapability.
        ResourceHandler<FluidResource> restoredTop =
            restored.getFluidPortHandlerForWorldSide(Direction.UP);
        ResourceHandler<FluidResource> restoredRight =
            restored.getFluidPortHandlerForWorldSide(Direction.WEST);
        if (restoredTop == null || restoredRight == null) {
            helper.fail("В восстановленной BlockEntity отсутствуют обработчики TOP/RIGHT");
            return;
        }

        if (restoredTop.getAmountAsLong(0) != 3_500L
            || !lava.equals(restoredTop.getResource(0)))
        {
            helper.fail("После загрузки ожидалось 3500 mB именно лавы");
            return;
        }

        // Сохранённый режим RIGHT=DISABLED должен продолжать действовать.
        try (Transaction tx = Transaction.openRoot()) {
            int inserted = restoredRight.insert(0, lava, 200, tx);
            if (inserted != 0) {
                helper.fail("Восстановленный RIGHT=DISABLED принял лаву: " + inserted);
                return;
            }
            tx.commit();
        }
        if (restoredTop.getAmountAsLong(0) != 3_500L) {
            helper.fail("Отключённый RIGHT изменил количество жидкости после загрузки");
            return;
        }

        // Изменённый режим TOP=INPUT должен работать и после загрузки.
        try (Transaction tx = Transaction.openRoot()) {
            int inserted = restoredTop.insert(0, lava, 500, tx);
            if (inserted != 500) {
                helper.fail("Через восстановленный TOP ожидалось 500 mB, получено: " + inserted);
                return;
            }
            tx.commit();
        }
        if (restoredTop.getAmountAsLong(0) != 4_000L
            || restoredRight.getAmountAsLong(0) != 4_000L)
        {
            helper.fail("После вставки через TOP резервуар должен содержать 4000 mB");
            return;
        }

        helper.succeed();
    }

    /**
     * Проверяет устойчивость загрузки к повреждённым и устаревшим
     * данным конфигурации жидкостных портов.
     *
     * <p>В сохранённом NBT намеренно создаётся раздел fluid_ports с:
     * неизвестным строковым режимом, известным, но запрещённым OUTPUT,
     * отсутствующим ключом TOP, неизвестным идентификатором порта,
     * неверным NBT-типом режима BACK и допустимым режимом BOTTOM=SERVICE.</p>
     *
     * <p>Проверяется именно загрузка в новый экземпляр BlockEntity.
     * Реальные данные резервуара должны сохраниться, корректная настройка
     * должна примениться, а повреждённые — не менять значения по умолчанию.</p>
     *
     * @param helper контекст игрового теста
     */
    private static void shouldIgnoreInvalidAndUnknownFluidPortModes(
        GameTestHelper helper
    )
    {
        BlockPos relativePos = new BlockPos(1, 1, 1);
        helper.setBlock(
            relativePos,
            EnergyBlocks.LAVA_GENERATOR.get()
                .defaultBlockState()
                .setValue(LavaGeneratorBlock.FACING, Direction.NORTH)
        );

        ServerLevel level = helper.getLevel();
        BlockPos worldPos = helper.absolutePos(relativePos);
        if (!(level.getBlockEntity(worldPos) instanceof LavaGeneratorBlockEntity original)) {
            helper.fail("Не найдена исходная LavaGeneratorBlockEntity");
            return;
        }

        ResourceHandler<FluidResource> originalRight = level.getCapability(
            Capabilities.Fluid.BLOCK,
            worldPos,
            Direction.WEST // FACING=NORTH: локальная RIGHT
        );
        if (originalRight == null) {
            helper.fail("Не найдена исходная capability RIGHT");
            return;
        }

        FluidResource lava = FluidResource.of(Fluids.LAVA);
        try (Transaction tx = Transaction.openRoot()) {
            int inserted = originalRight.insert(0, lava, 750, tx);
            if (inserted != 750) {
                helper.fail("Не удалось заполнить резервуар на 750 mB: " + inserted);
                return;
            }
            tx.commit();
        }

        // В оригинале режимы отличаются от значений по умолчанию.
        // Если значение в NBT невалидно или отсутствует, новая
        // BlockEntity должна сохранить СВОЙ режим по умолчанию,
        // а не режим оригинала.
        if (!original.changeFluidPortMode(MachineSide.RIGHT, FluidPortMode.DISABLED)
            || !original.changeFluidPortMode(MachineSide.LEFT, FluidPortMode.INPUT)
            || !original.changeFluidPortMode(MachineSide.TOP, FluidPortMode.INPUT))
        {
            helper.fail("Не удалось подготовить исходные режимы портов");
            return;
        }

        CompoundTag saved = original.saveWithFullMetadata(level.registryAccess());
        CompoundTag alteredFluidPorts = new CompoundTag();

        // Несуществующее имя режима: игнорируется через valueOf + catch.
        alteredFluidPorts.putString("lava_generator.right", "INVALID_MODE");
        // Существующий enum, но запрещённый политикой INPUT-танка.
        alteredFluidPorts.putString("lava_generator.left", "OUTPUT");
        // Отсутствующий ключ TOP: остаётся DISABLED по умолчанию.
        // Неизвестный ID не должен попасть в конфигурацию.
        alteredFluidPorts.putString("lava_generator.unknown", "INPUT");
        // Неверный тип NBT: getString() должен вернуть Optional.empty().
        alteredFluidPorts.putInt("lava_generator.back", 123);
        // Положительный контроль: допустимые значения читаются.
        alteredFluidPorts.putString("lava_generator.bottom", "SERVICE");

        // Заменяем только дочерний раздел настроек, не затрагивая
        // fluid_storage и служебные данные BlockEntity.
        saved.put("fluid_ports", alteredFluidPorts);

        BlockEntity loaded = BlockEntity.loadStatic(
            worldPos,
            original.getBlockState(),
            saved,
            level.registryAccess()
        );
        if (!(loaded instanceof LavaGeneratorBlockEntity restored)) {
            helper.fail("Повреждённый NBT не восстановил LavaGeneratorBlockEntity");
            return;
        }
        if (restored == original) {
            helper.fail("Тест должен проверять новый экземпляр BlockEntity");
            return;
        }
        restored.setLevel(level);

        if (restored.getFluidPortMode(MachineSide.RIGHT) != FluidPortMode.INPUT) {
            helper.fail("Неизвестный режим RIGHT должен оставить INPUT по умолчанию");
            return;
        }
        if (restored.getFluidPortMode(MachineSide.LEFT) != FluidPortMode.DISABLED) {
            helper.fail("Запрещённый OUTPUT для LEFT должен быть проигнорирован");
            return;
        }
        if (restored.getFluidPortMode(MachineSide.TOP) != FluidPortMode.DISABLED) {
            helper.fail("Отсутствующая настройка TOP должна оставить DISABLED по умолчанию");
            return;
        }
        if (restored.getFluidPortMode(MachineSide.BACK) != FluidPortMode.DISABLED) {
            helper.fail("Неверный NBT-тип BACK не должен менять DISABLED");
            return;
        }
        if (restored.getFluidPortMode(MachineSide.BOTTOM) != FluidPortMode.SERVICE) {
            helper.fail("Допустимая настройка BOTTOM=SERVICE не восстановилась");
            return;
        }

        // Повторная сериализация проверяет, что неизвестный ID не был
        // добавлен в конфигурацию, а повреждённые значения не сохранились.
        CompoundTag savedAgain = restored.saveWithFullMetadata(level.registryAccess());
        CompoundTag cleanedPorts = savedAgain.getCompoundOrEmpty("fluid_ports");
        if (cleanedPorts.contains("lava_generator.unknown")) {
            helper.fail("Неизвестный идентификатор порта сохранился после загрузки");
            return;
        }
        if (!"INPUT".equals(cleanedPorts.getStringOr("lava_generator.right", ""))
            || !"DISABLED".equals(cleanedPorts.getStringOr("lava_generator.left", ""))
            || !"DISABLED".equals(cleanedPorts.getStringOr("lava_generator.top", ""))
            || !"SERVICE".equals(cleanedPorts.getStringOr("lava_generator.bottom", "")))
        {
            helper.fail("После загрузки и повторного сохранения режимы повреждены");
            return;
        }

        // Остальные данные NBT не должны повреждаться настройками портов.
        ResourceHandler<FluidResource> bottomHandler =
            restored.getFluidPortHandlerForWorldSide(Direction.DOWN);
        ResourceHandler<FluidResource> rightHandler =
            restored.getFluidPortHandlerForWorldSide(Direction.WEST);
        if (bottomHandler == null || rightHandler == null) {
            helper.fail("После загрузки отсутствует обработчик RIGHT или BOTTOM");
            return;
        }
        if (bottomHandler.getAmountAsLong(0) != 750L
            || !lava.equals(bottomHandler.getResource(0)))
        {
            helper.fail("Повреждённые настройки не должны уничтожать 750 mB лавы");
            return;
        }

        // Восстановленный SERVICE работает на общем резервуаре.
        try (Transaction tx = Transaction.openRoot()) {
            int extracted = bottomHandler.extract(0, lava, 200, tx);
            if (extracted != 200) {
                helper.fail("BOTTOM=SERVICE не смог извлечь 200 mB: " + extracted);
                return;
            }
            tx.commit();
        }
        // RIGHT=INPUT восстановлен значением по умолчанию и снова принимает лаву.
        try (Transaction tx = Transaction.openRoot()) {
            int inserted = rightHandler.insert(0, lava, 250, tx);
            if (inserted != 250) {
                helper.fail("RIGHT=INPUT не смог принять 250 mB: " + inserted);
                return;
            }
            tx.commit();
        }
        if (bottomHandler.getAmountAsLong(0) != 800L) {
            helper.fail("После извлечения 200 и вставки 250 ожидалось 800 mB");
            return;
        }

        helper.succeed();
    }
}
