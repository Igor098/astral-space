package ru.greatdragonx.astralspace.modules.energy;

import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import ru.greatdragonx.astralspace.modules.energy.blockentities.CoalGeneratorBlockEntity;

public class EnergyCapabilitiesRegistration {
    public static void register(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
            Capabilities.Energy.BLOCK,
            EnergyBlockEntities.COAL_GENERATOR_BLOCK_ENTITY.get(),
            CoalGeneratorBlockEntity::getEnergyHandlerForSide
        );
    }
}
