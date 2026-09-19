package com.danzy.fuelblast;

import com.danzy.fuelblast.client.ModParticles;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Create: Fuel Blast - addon for Create Aeronautics (Minecraft 1.21.1 / NeoForge).
 * Fuel tanks and vessels cook off when an explosion goes off next to them.
 */
@Mod(FuelBlast.ID)
public class FuelBlast {
    public static final String ID = "fuelblast";
    public static final Logger LOGGER = LoggerFactory.getLogger("Create: Fuel Blast");

    public FuelBlast(IEventBus modBus, ModContainer container) {
        ModParticles.PARTICLES.register(modBus);
        container.registerConfig(ModConfig.Type.COMMON, FuelBlastConfig.SPEC);

        NeoForge.EVENT_BUS.register(new ExplosionHandler());
        NeoForge.EVENT_BUS.register(BlastScheduler.class);
        NeoForge.EVENT_BUS.register(FuelBlastCommands.class);
    }
}
