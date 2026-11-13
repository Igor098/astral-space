package ru.greatdragonx.astralspace.modules.energy;

import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import org.jspecify.annotations.NonNull;
import ru.greatdragonx.astralspace.modules.energy.blockentities.CoalGeneratorBlockEntity;
import ru.greatdragonx.astralspace.modules.energy.blockentities.LavaGeneratorBlockEntity;

public class EnergyCapabilitiesRegistration {
    public static void register(@NonNull RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
            Capabilities.Energy.BLOCK,
            EnergyBlockEntities.COAL_GENERATOR_BLOCK_ENTITY.get(),
            CoalGeneratorBlockEntity::getEnergyHandlerForSide
        );

        event.registerBlockEntity(
            Capabilities.Fluid.BLOCK,
            EnergyBlockEntities.LAVA_GENERATOR_BLOCK_ENTITY.get(),
            LavaGeneratorBlockEntity::getFluidPortHandlerForWorldSide
        );
    }
}
