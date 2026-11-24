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
import ru.greatdragonx.astralspace.common.machine.visual.layout.PortLayout;
import ru.greatdragonx.astralspace.common.machine.visual.layout.PortLayoutPreset;
import ru.greatdragonx.astralspace.common.machine.visual.layout.PortPlacement;
import ru.greatdragonx.astralspace.modules.energy.blocks.state.LavaGeneratorFrontState;

import java.util.*;

public record LavaGeneratorBlockStateModel(
    BlockModelPart body,
    BlockModelPart frontOff,
    BlockModelPart frontRunning,
    BlockModelPart frontLowLava,
    Map<PortModelKey, BlockModelPart> portModels
) implements DynamicBlockStateModel
{
    private static final PortLayout PORT_LAYOUT = new PortLayout();

    private record GeometryKey(
        LavaGeneratorBlockStateModel model,
        BlockState state,
        LavaGeneratorFrontState frontState,
        Set<PortModelKey> activePortModels
    ) {}

    private record PortModelKey(
        VisualPortType type, MachineSide side, float offsetU, float offsetV, float scale
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
        List<PortPlacement> placements = calculatePortPlacements(ports);
        for (PortPlacement placement : placements) {
            PortModelKey key = toPortModelKey(placement);
            BlockModelPart part = portModels.get(key);
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

        Set<PortModelKey> activeKeys = new HashSet<>();

        for (PortPlacement placement : calculatePortPlacements(ports)) {
            PortModelKey key = toPortModelKey(placement);

            if (portModels.containsKey(key)) {
                activeKeys.add(key);
            }
        }

        return new GeometryKey(
            this,
            state,
            frontState,
            Set.copyOf(activeKeys)
        );
    }

    public record Unbaked(
        Identifier body,
        Identifier frontOff,
        Identifier frontRunning,
        Identifier frontLowLava,
        Identifier energyPort,
        Identifier fluidPort,
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

                    Identifier.CODEC.fieldOf("fluid_port")
                        .forGetter(Unbaked::fluidPort),

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
            resolver.markDependency(fluidPort);
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
                this.bakePortModels(baker)
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

        private static @NonNull @Unmodifiable List<PortModelKey> createPortModelKeys(
            VisualPortType type,
            MachineSide side
        )
        {
            Objects.requireNonNull(
                type,
                "type cannot be null"
            );
            Objects.requireNonNull(
                side,
                "side cannot be null"
            );

            List<PortLayoutPreset> presets = PORT_LAYOUT.supportedPresets();
            List<PortModelKey> keys = new ArrayList<>();

            for (PortLayoutPreset preset : presets) {
                keys.add(new PortModelKey(
                    type,
                    side,
                    preset.offsetU(),
                    preset.offsetV(),
                    preset.scale()
                ));
            }

            return List.copyOf(keys);
        }

        private @NonNull @Unmodifiable Map<PortModelKey, BlockModelPart> bakePortModels(
            @NonNull ModelBaker baker
        )
        {
            Map<PortModelKey, BlockModelPart> models = new HashMap<>();

            for (VisualPortType type : List.of(
                VisualPortType.ENERGY,
                VisualPortType.FLUID
            )) {
                for (MachineSide side : MachineSide.values()) {
                    if (side == MachineSide.FRONT) {
                        continue;
                    }

                    List<PortModelKey> keys = createPortModelKeys(
                        type,
                        side
                    );
                    for (PortModelKey key : keys) {
                        models.put(
                            key,
                            bakePortModel(
                                baker,
                                key
                            )
                        );
                    }
                }
            }

            return Map.copyOf(models);
        }

        private @NonNull BlockModelPart bakePortModel(
            @NonNull ModelBaker baker,
            @NonNull PortModelKey key
        )
        {
            Identifier modelId = this.resolvePortModelId(key.type());
            ModelState modelState = this.createPortModelState(key);
            return bakePart(
                baker,
                modelId,
                modelState
            );
        }

        private @NonNull ModelState createModelState() {
            return BlockModelRotation.get(rotationY.rotationY);
        }

        private @NonNull ModelState createPortModelState(
            @NonNull PortModelKey key
        )
        {
            Matrix4f matrix = new Matrix4f();

            switch (key.side()) {
                case BACK -> {}
                case LEFT -> matrix.rotateY((float) Math.toRadians(90));
                case RIGHT -> matrix.rotateY((float) Math.toRadians(-90));
                case TOP -> matrix.rotateX((float) Math.toRadians(-90));
                case BOTTOM -> matrix.rotateX((float) Math.toRadians(90));
                default -> throw new IllegalArgumentException(
                    "Unsupported machine side: " + key.side());
            }

            matrix.translate(
                key.offsetU(),
                key.offsetV(),
                1.0f
            );

            matrix.scale(
                key.scale(),
                key.scale(),
                1.0f
            );

            Transformation transformation = new Transformation(matrix);
            return new ComposedModelState(
                this.createModelState(),
                transformation
            );
        }

        private @NonNull Identifier resolvePortModelId(
            @NonNull VisualPortType type
        )
        {
            return switch (type) {
                case ENERGY -> energyPort;
                case FLUID -> fluidPort;
                default -> throw new IllegalArgumentException(
                    "Unsupported visual port type: " + type);
            };
        }
    }

    private static @NonNull @Unmodifiable Map<MachineSide, List<VisualPort>> groupPortsBySide(
        @NonNull List<VisualPort> ports
    )
    {
        Objects.requireNonNull(
            ports,
            "Visual ports cannot be null"
        );

        Map<MachineSide, List<VisualPort>> portsBySide = new EnumMap<>(MachineSide.class);
        for (VisualPort port : ports) {
            Objects.requireNonNull(
                port,
                "Visual port cannot be null"
            );

            portsBySide.computeIfAbsent(
                    port.side(),
                    s -> new ArrayList<>()
                )
                .add(port);
        }

        portsBySide.replaceAll((side, sidePorts) -> List.copyOf(sidePorts));

        return Map.copyOf(portsBySide);
    }

    private static @NonNull @Unmodifiable List<PortPlacement> calculatePortPlacements(
        @NonNull List<VisualPort> ports
    )
    {
        Map<MachineSide, List<VisualPort>> portsBySide = groupPortsBySide(ports);
        List<PortPlacement> portPlacements = new ArrayList<>();

        for (MachineSide side : MachineSide.values()) {
            List<VisualPort> sidePorts = portsBySide.getOrDefault(
                side,
                Collections.emptyList()
            );

            portPlacements.addAll(PORT_LAYOUT.arrange(sidePorts));
        }

        return List.copyOf(portPlacements);
    }

    private static @NonNull PortModelKey toPortModelKey(
        @NonNull PortPlacement placement
    )
    {
        return new PortModelKey(
            placement.port()
                .type(),
            placement.port()
                .side(),
            placement.offsetU(),
            placement.offsetV(),
            placement.scale()
        );
    }
}

