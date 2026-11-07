package ru.greatdragonx.astralspace.modules.energy.client.model;

import com.mojang.math.Quadrant;
import com.mojang.math.Transformation;
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
import net.neoforged.neoforge.client.model.ComposedModelState;
import net.neoforged.neoforge.client.model.DynamicBlockStateModel;
import net.neoforged.neoforge.client.model.block.CustomUnbakedBlockStateModel;
import org.jetbrains.annotations.Unmodifiable;
import org.joml.Matrix4f;
import org.jspecify.annotations.NonNull;
import ru.greatdragonx.astralspace.common.machine.MachineSide;
import ru.greatdragonx.astralspace.common.machine.visual.VisualPort;
import ru.greatdragonx.astralspace.common.machine.visual.VisualPortType;
import ru.greatdragonx.astralspace.modules.energy.blocks.state.LavaGeneratorFrontState;

import java.util.*;

public record LavaGeneratorBlockStateModel(
    BlockModelPart body,
    BlockModelPart frontOff,
    BlockModelPart frontRunning,
    BlockModelPart frontLowLava,
    Map<MachineSide, BlockModelPart> energyPorts
) implements DynamicBlockStateModel
{
    private record GeometryKey(
        LavaGeneratorBlockStateModel model,
        BlockState state,
        LavaGeneratorFrontState frontState,
        Set<MachineSide> activeEnergySides
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

        List<VisualPort> ports = LavaGeneratorModelDataReader.readVisualPorts(
            level,
            pos
        );
        for (VisualPort port : ports) {
            if (port.type() != VisualPortType.ENERGY) {
                continue;
            }
            BlockModelPart part = energyPorts.get(port.side());
            if (part != null) {
                parts.add(part);
            }
        }
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
        List<VisualPort> ports = LavaGeneratorModelDataReader.readVisualPorts(
            level,
            pos
        );

        Set<MachineSide> activeSides = new HashSet<>();

        for (VisualPort port : ports) {
            if (port.type() == VisualPortType.ENERGY && energyPorts.containsKey(port.side())) {
                activeSides.add(port.side());
            }
        }

        return new GeometryKey(
            this,
            state,
            frontState,
            Set.copyOf(activeSides)
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
                this.bakeEnergyPorts(baker)
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

        private @NonNull ModelState createPortModelState(@NonNull MachineSide side) {
            Matrix4f matrix = new Matrix4f();

            switch (side) {
                case BACK -> {}
                case LEFT -> matrix.rotateY((float) Math.toRadians(90));
                case RIGHT -> matrix.rotateY((float) Math.toRadians(-90));
                case TOP -> matrix.rotateX((float) Math.toRadians(-90));
                case BOTTOM -> matrix.rotateX((float) Math.toRadians(90));
                default -> throw new IllegalArgumentException("Unsupported machine side: " + side);
            }

            matrix.translate(
                0.0f,
                0.0f,
                1.0f
            );

            Transformation transformation = new Transformation(matrix);
            return new ComposedModelState(
                this.createModelState(),
                transformation
            );
        }

        private @NonNull @Unmodifiable Map<MachineSide, BlockModelPart> bakeEnergyPorts(
            @NonNull ModelBaker baker
        )
        {
            Map<MachineSide, BlockModelPart> ports = new EnumMap<>(MachineSide.class);

            for (MachineSide side : MachineSide.values()) {
                if (side == MachineSide.FRONT) {
                    continue;
                }

                ports.put(
                    side,
                    bakePart(
                        baker,
                        energyPort,
                        createPortModelState(side)
                    )
                );
            }

            return Map.copyOf(ports);
        }
    }
}

