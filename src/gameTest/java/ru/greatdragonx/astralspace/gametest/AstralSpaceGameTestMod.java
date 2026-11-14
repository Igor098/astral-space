package ru.greatdragonx.astralspace.gametest;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

@Mod(AstralSpaceGameTestMod.MOD_ID)
public final class AstralSpaceGameTestMod {

    public static final String MOD_ID = "astralspace_gametest";

    public AstralSpaceGameTestMod(IEventBus modEventBus) {
        LavaGeneratorGameTests.TEST_FUNCTIONS.register(modEventBus);

        modEventBus.addListener(LavaGeneratorGameTests::registerGameTests);
    }
}
