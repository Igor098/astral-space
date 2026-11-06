package ru.greatdragonx.astralspace.modules.energy.client.model;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.neoforged.neoforge.model.data.ModelData;
import org.jspecify.annotations.NonNull;
import ru.greatdragonx.astralspace.modules.energy.blockentities.LavaGeneratorBlockEntity;
import ru.greatdragonx.astralspace.modules.energy.blocks.state.LavaGeneratorFrontState;

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
}
