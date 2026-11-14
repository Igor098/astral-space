package ru.greatdragonx.astralspace.modules.energy.client.model;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.neoforged.neoforge.model.data.ModelData;
import org.jspecify.annotations.NonNull;
import ru.greatdragonx.astralspace.common.machine.visual.VisualPort;
import ru.greatdragonx.astralspace.modules.energy.blockentities.LavaGeneratorBlockEntity;
import ru.greatdragonx.astralspace.modules.energy.blocks.state.LavaGeneratorFrontState;

import java.util.Collections;
import java.util.List;

public final class LavaGeneratorModelDataReader {
    public static LavaGeneratorFrontState readFrontState(
        @NonNull BlockAndTintGetter level,
        @NonNull BlockPos pos
    )
    {
        ModelData data = level.getModelData(pos);
        LavaGeneratorFrontState frontState = data.get(LavaGeneratorBlockEntity.FRONT_STATE);
        if (frontState == null) {
            return LavaGeneratorFrontState.OFF;
        }

        return frontState;
    }

    public static @NonNull List<VisualPort> readVisualPorts(
        @NonNull BlockAndTintGetter level,
        @NonNull BlockPos pos
    )
    {
        ModelData data = level.getModelData(pos);
        List<VisualPort> visualPorts = data.get(LavaGeneratorBlockEntity.VISUAL_PORTS);
        if (visualPorts == null) {
            return Collections.emptyList();
        }

        return visualPorts;
    }
}
