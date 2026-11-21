package ru.greatdragonx.astralspace.gametest;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

/** Регистрирует исходные жидкостные тесты и дополнительный набор Lava Generator. */
@Mod(AstralSpaceGameTestMod.MOD_ID)
public final class AstralSpaceGameTestMod {
    public static final String MOD_ID = "astralspace_gametest";

    public AstralSpaceGameTestMod(IEventBus modEventBus) {
        // Четыре существующих теста жидкостной системы — без изменений.
        LavaGeneratorGameTests.TEST_FUNCTIONS.register(modEventBus);
        modEventBus.addListener(LavaGeneratorGameTests::registerGameTests);

        // Новые тесты горения, FE, портов, Push/Pull и сериализации.
        LavaGeneratorMechanicsGameTests.TEST_FUNCTIONS.register(modEventBus);
        modEventBus.addListener(LavaGeneratorMechanicsGameTests::registerGameTests);

        // Тестовый приёмник энергии через capability ванильного GOLD_BLOCK.
        // Регистрация действует только при загрузке gameTest-мода.
        modEventBus.addListener(GameTestEnergySink::registerCapabilities);
    }
}
