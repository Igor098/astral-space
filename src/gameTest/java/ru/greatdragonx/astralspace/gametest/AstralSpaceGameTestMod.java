package ru.greatdragonx.astralspace.gametest;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

/**
 * Registers GameTest-only content and test functions.
 */
@Mod(AstralSpaceGameTestMod.MOD_ID)
public final class AstralSpaceGameTestMod {
    public static final String MOD_ID = "astralspace_gametest";

    public AstralSpaceGameTestMod(IEventBus modEventBus) {
        // Existing Lava tests: preserved unchanged.
        LavaGeneratorGameTests.TEST_FUNCTIONS.register(modEventBus);
        modEventBus.addListener(LavaGeneratorGameTests::registerGameTests);
        LavaGeneratorMechanicsGameTests.TEST_FUNCTIONS.register(modEventBus);
        modEventBus.addListener(LavaGeneratorMechanicsGameTests::registerGameTests);
        modEventBus.addListener(GameTestEnergySink::registerCapabilities);

        // Test generator and first lifecycle suite.
        GameTestGeneratorRegistry.register(modEventBus);
        AbstractGeneratorLifecycleGameTests.TEST_FUNCTIONS.register(modEventBus);
        modEventBus.addListener(AbstractGeneratorLifecycleGameTests::registerGameTests);

        // Energy ports and real BlockCapabilityCache integration.
        AbstractGeneratorPortsGameTests.TEST_FUNCTIONS.register(modEventBus);
        modEventBus.addListener(AbstractGeneratorPortsGameTests::registerGameTests);

        // Production order and observable transactional acceptance probes.
        modEventBus.addListener(GameTestRecordingEnergySink::registerCapabilities);
        AbstractGeneratorProductionGameTests.TEST_FUNCTIONS.register(modEventBus);
        modEventBus.addListener(AbstractGeneratorProductionGameTests::registerGameTests);

        // Transfer integration, scoped receivers and unloaded-neighbor fixture.
        GameTestRemotePortGenerator.register(modEventBus);
        modEventBus.addListener(GameTestTransferReceiver::registerCapabilities);
        AbstractGeneratorTransferGameTests.TEST_FUNCTIONS.register(modEventBus);
        modEventBus.addListener(AbstractGeneratorTransferGameTests::registerGameTests);

        // Redstone gate, real signal and persistence.
        AbstractGeneratorRedstoneGameTests.TEST_FUNCTIONS.register(modEventBus);
        modEventBus.addListener(AbstractGeneratorRedstoneGameTests::registerGameTests);

        // Real rotations and selective endpoint revocation.
        AbstractGeneratorOrientationGameTests.TEST_FUNCTIONS.register(modEventBus);
        modEventBus.addListener(AbstractGeneratorOrientationGameTests::registerGameTests);

        // Structure readiness and physical port availability.
        AbstractGeneratorStructureGameTests.TEST_FUNCTIONS.register(modEventBus);
        modEventBus.addListener(AbstractGeneratorStructureGameTests::registerGameTests);
    }
}
