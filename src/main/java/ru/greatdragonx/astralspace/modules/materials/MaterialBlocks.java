package ru.greatdragonx.astralspace.modules.materials;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

import static ru.greatdragonx.astralspace.AstralSpace.MOD_ID;

public final class MaterialBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MOD_ID);
    public static final DeferredBlock<Block> ALUMINUM_ORE = BLOCKS.registerSimpleBlock(
        "aluminum_ore",
        properties -> properties.destroyTime(3.0f)
            .explosionResistance(3.0f)
            .sound(SoundType.STONE)
            .requiresCorrectToolForDrops()
    );
    public static final DeferredBlock<Block> TIN_ORE = BLOCKS.registerSimpleBlock(
        "tin_ore",
        properties -> properties.destroyTime(3.0f)
            .explosionResistance(3.0f)
            .sound(SoundType.STONE)
            .requiresCorrectToolForDrops()
    );
    public static final DeferredBlock<Block> DEEPSLATE_ALUMINUM_ORE = BLOCKS.registerSimpleBlock(
        "deepslate_aluminum_ore",
        properties -> properties.destroyTime(4.5f)
            .explosionResistance(3.0f)
            .sound(SoundType.DEEPSLATE)
            .requiresCorrectToolForDrops()
    );
    public static final DeferredBlock<Block> DEEPSLATE_TIN_ORE = BLOCKS.registerSimpleBlock(
        "deepslate_tin_ore",
        properties -> properties.destroyTime(4.5f)
            .explosionResistance(3.0f)
            .sound(SoundType.DEEPSLATE)
            .requiresCorrectToolForDrops()
    );
    public static final DeferredBlock<Block> ALUMINUM_BLOCK = BLOCKS.registerSimpleBlock(
        "aluminum_block",
        properties -> properties.destroyTime(5.0f)
            .explosionResistance(6.0f)
            .sound(SoundType.METAL)
            .requiresCorrectToolForDrops()
    );
    public static final DeferredBlock<Block> TIN_BLOCK = BLOCKS.registerSimpleBlock(
        "tin_block",
        properties -> properties.destroyTime(5.0f)
            .explosionResistance(6.0f)
            .sound(SoundType.METAL)
            .requiresCorrectToolForDrops()
    );

    private MaterialBlocks() {
    }
}
