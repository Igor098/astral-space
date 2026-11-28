package ru.greatdragonx.astralspace.gametest;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

/**
 * Test-only registrations; never included in the production mod.
 */
final class GameTestGeneratorRegistry {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(
        AstralSpaceGameTestMod.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
        DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, AstralSpaceGameTestMod.MOD_ID);

    static final DeferredBlock<GameTestGeneratorBlock> CONFIGURABLE_GENERATOR =
        BLOCKS.registerBlock(
            "configurable_generator",
            GameTestGeneratorBlock::new,
            () -> BlockBehaviour.Properties.of().destroyTime(2.0f)
        );

    static final DeferredBlock<GameTestGeneratorBlock> FIXED_GENERATOR = BLOCKS.registerBlock(
        "fixed_generator",
        GameTestGeneratorBlock::new,
        () -> BlockBehaviour.Properties.of().destroyTime(2.0f)
    );

    static final Supplier<BlockEntityType<GameTestGeneratorBlockEntity>> GENERATOR_ENTITY =
        BLOCK_ENTITY_TYPES.register(
            "generator_entity", () -> new BlockEntityType<>(
                GameTestGeneratorBlockEntity::new,
                false,
                CONFIGURABLE_GENERATOR.get(),
                FIXED_GENERATOR.get()
            )
        );

    private GameTestGeneratorRegistry() {}

    static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        BLOCK_ENTITY_TYPES.register(modEventBus);
        modEventBus.addListener(GameTestGeneratorRegistry::registerCapabilities);
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
            Capabilities.Energy.BLOCK,
            GENERATOR_ENTITY.get(),
            GameTestGeneratorBlockEntity::getEnergyHandlerForSide
        );
    }
}
