package com.danzy.fuelblast;

import com.danzy.fuelblast.client.ModParticles;
import com.danzy.fuelblast.network.FuelBlastNetwork;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Create: Fuel Blast - addon for Create Aeronautics.
 * Makes fluid tanks and vessels filled with flammable fuel detonate when an
 * explosion goes off next to them. The bigger the fuel charge, the bigger the blast.
 */
@Mod(FuelBlast.ID)
public class FuelBlast {
    public static final String ID = "fuelblast";
    public static final Logger LOGGER = LoggerFactory.getLogger("Create: Fuel Blast");

    public FuelBlast() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();

        ModParticles.PARTICLES.register(modBus);
        modBus.addListener(this::commonSetup);

        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, FuelBlastConfig.SPEC);

        MinecraftForge.EVENT_BUS.register(new ExplosionHandler());
        MinecraftForge.EVENT_BUS.register(BlastScheduler.class);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(FuelBlastNetwork::register);
    }
}
