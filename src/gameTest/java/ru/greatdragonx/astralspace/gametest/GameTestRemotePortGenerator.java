package ru.greatdragonx.astralspace.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.jspecify.annotations.NonNull;
import ru.greatdragonx.astralspace.common.energy.blockentity.AbstractGeneratorBlockEntity;
import ru.greatdragonx.astralspace.common.energy.generation.GenerationProcess;
import ru.greatdragonx.astralspace.common.energy.generation.GeneratorConfiguration;
import ru.greatdragonx.astralspace.common.energy.generation.GeneratorEnergySpec;
import ru.greatdragonx.astralspace.common.energy.port.*;

import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Only G-57 uses this fixture: a synthetic available remote port, not a real hatch.
 */
final class GameTestRemotePortGenerator extends AbstractGeneratorBlockEntity {
    static final BlockPos REMOTE_OFFSET = new BlockPos(16_384, 0, 0);
    private static final EnergyPortId REMOTE = new EnergyPortId("remote");
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(
        AstralSpaceGameTestMod.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> TYPES = DeferredRegister.create(
        Registries.BLOCK_ENTITY_TYPE,
        AstralSpaceGameTestMod.MOD_ID
    );
    static final DeferredBlock<RemoteBlock> BLOCK = BLOCKS.registerBlock(
        "remote_port_generator",
        RemoteBlock::new,
        () -> BlockBehaviour.Properties.of().destroyTime(2.0f)
    );
    private static final Supplier<BlockEntityType<GameTestRemotePortGenerator>> TYPE =
        TYPES.register(
            "remote_port_generator_entity",
            () -> new BlockEntityType<>(GameTestRemotePortGenerator::new, false, BLOCK.get())
        );

    static void register(IEventBus bus) {
        BLOCKS.register(bus);
        TYPES.register(bus);
    }

    private GameTestRemotePortGenerator(BlockPos pos, BlockState state) {
        super(
            TYPE.get(), pos, state, new GeneratorConfiguration(
                new GeneratorEnergySpec(10_000, 80, 40),
                new EnergyPortRules(
                    EnergyPortRules.ConfigurationType.FIXED,
                    List.of(new EnergyPortRule(
                        REMOTE,
                        EnergyPortMode.OUTPUT,
                        Set.of(EnergyPortMode.OUTPUT)
                    ))
                ),
                new EnergyPortTopology(List.of(new EnergyPortPoint(
                    REMOTE,
                    REMOTE_OFFSET,
                    Direction.EAST
                )))
            )
        );
        bindGenerationProcess(new GenerationProcess() {
            @Override
            public boolean prepareProductionTick() {return false;}

            @Override
            public void onProductionTickCompleted() {
                throw new AssertionError("Unexpected production");
            }
        });
    }

    @Override
    protected boolean isEnergyPortPhysicallyAvailable(@NonNull EnergyPortPoint point) {
        return REMOTE.equals(point.portId());
    }

    int charge(int amount) {return getEnergyComponent().storeProducedEnergy(amount);}

    void tickOnce() {serverTick();}

    BlockPos remotePoint() {return getBlockPos().offset(REMOTE_OFFSET);}

    static final class RemoteBlock extends Block implements EntityBlock {
        RemoteBlock(BlockBehaviour.Properties properties) {super(properties);}

        @Override
        public @NonNull BlockEntity newBlockEntity(
            @NonNull BlockPos pos,
            @NonNull BlockState state
        )
        {
            return new GameTestRemotePortGenerator(pos, state);
        }
    }
}
