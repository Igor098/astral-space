package ru.greatdragonx.astralspace.modules.energy.client.model;

import com.mojang.math.Quadrant;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.client.renderer.block.model.BlockModelPart;
import net.minecraft.client.renderer.block.model.SimpleModelWrapper;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BlockModelRotation;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.client.resources.model.ResolvableModel;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.DynamicBlockStateModel;
import net.neoforged.neoforge.client.model.block.CustomUnbakedBlockStateModel;
import org.jspecify.annotations.NonNull;
import ru.greatdragonx.astralspace.modules.energy.blocks.state.LavaGeneratorFrontState;

import java.util.List;

public record LavaGeneratorBlockStateModel(
    BlockModelPart body,
    BlockModelPart frontOff,
    BlockModelPart frontRunning,
    BlockModelPart frontLowLava,
    BlockModelPart energyPort
) implements DynamicBlockStateModel
{
    private record GeometryKey(
        LavaGeneratorBlockStateModel model, BlockState state, LavaGeneratorFrontState frontState
    ) {}

    public BlockModelPart selectFrontPart(
        @NonNull LavaGeneratorFrontState state
    )
    {
        return switch (state) {
            case OFF -> frontOff;
            case RUNNING -> frontRunning;
            case LOW_LAVA -> frontLowLava;
        };
    }

    @Override
    public void collectParts(
        @NonNull BlockAndTintGetter level,
        @NonNull BlockPos pos,
        @NonNull BlockState state,
        @NonNull RandomSource random,
        @NonNull List<BlockModelPart> parts
    )
    {
        parts.add(body);

        LavaGeneratorFrontState frontState = LavaGeneratorModelDataReader.readFrontState(
            level,
            pos
        );
        BlockModelPart frontPart = this.selectFrontPart(frontState);
        parts.add(frontPart);

        parts.add(energyPort);
    }

    @Override
    public @NonNull TextureAtlasSprite particleIcon() {
        return body.particleIcon();
    }

    @Override
    public @NonNull Object createGeometryKey(
        @NonNull BlockAndTintGetter level,
        @NonNull BlockPos pos,
        @NonNull BlockState state,
        @NonNull RandomSource random
    )
    {
        LavaGeneratorFrontState frontState = LavaGeneratorModelDataReader.readFrontState(
            level,
            pos
        );

        return new GeometryKey(
            this,
            state,
            frontState
        );
    }

    public record Unbaked(
        Identifier body,
        Identifier frontOff,
        Identifier frontRunning,
        Identifier frontLowLava,
        Identifier energyPort,
        Quadrant rotationY
    ) implements CustomUnbakedBlockStateModel
    {
        public static final Identifier ID = Identifier.fromNamespaceAndPath(
            "astralspace",
            "lava_generator_dynamic"
        );

        public static final MapCodec<Unbaked> CODEC =
            RecordCodecBuilder.mapCodec(instance -> instance.group(
                    Identifier.CODEC.fieldOf("body")
                        .forGetter(Unbaked::body),

                    Identifier.CODEC.fieldOf("front_off")
                        .forGetter(Unbaked::frontOff),

                    Identifier.CODEC.fieldOf("front_running")
                        .forGetter(Unbaked::frontRunning),

                    Identifier.CODEC.fieldOf("front_low_lava")
                        .forGetter(Unbaked::frontLowLava),

                    Identifier.CODEC.fieldOf("energy_port")
                        .forGetter(Unbaked::energyPort),

                    Quadrant.CODEC.fieldOf("rotation_y")
                        .forGetter(Unbaked::rotationY)
                )
                .apply(
                    instance,
                    Unbaked::new
                ));

        @Override
        public @NonNull MapCodec<? extends CustomUnbakedBlockStateModel> codec() {
            return CODEC;
        }

        @Override
        public void resolveDependencies(
            ResolvableModel.@NonNull Resolver resolver
        )
        {
            resolver.markDependency(body);
            resolver.markDependency(frontOff);
            resolver.markDependency(frontRunning);
            resolver.markDependency(frontLowLava);
            resolver.markDependency(energyPort);
        }

        @Override
        public @NonNull LavaGeneratorBlockStateModel bake(
            @NonNull ModelBaker baker
        )
        {
            ModelState modelState = this.createModelState();

            return new LavaGeneratorBlockStateModel(
                bakePart(
                    baker,
                    body,
                    modelState
                ),
                bakePart(
                    baker,
                    frontOff,
                    modelState
                ),
                bakePart(
                    baker,
                    frontRunning,
                    modelState
                ),
                bakePart(
                    baker,
                    frontLowLava,
                    modelState
                ),
                bakePart(
                    baker,
                    energyPort,
                    modelState
                )
            );
        }

        private static @NonNull BlockModelPart bakePart(
            @NonNull ModelBaker baker,
            @NonNull Identifier modelId,
            @NonNull ModelState modelState
        )
        {
            return SimpleModelWrapper.bake(
                baker,
                modelId,
                modelState
            );
        }

        private @NonNull ModelState createModelState() {
            return BlockModelRotation.get(rotationY.rotationY);
        }
    }
}

