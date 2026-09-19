package com.danzy.fuelblast.client;

import com.danzy.fuelblast.FuelBlast;
import com.danzy.fuelblast.client.particle.EmberParticle;
import com.danzy.fuelblast.client.particle.FireballParticle;
import com.danzy.fuelblast.client.particle.ShockwaveParticle;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = FuelBlast.ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ClientSetup {

    @SubscribeEvent
    public static void registerParticles(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(ModParticles.FIREBALL.get(), FireballParticle.Provider::new);
        event.registerSpriteSet(ModParticles.EMBER.get(), EmberParticle.Provider::new);
        event.registerSpriteSet(ModParticles.SHOCKWAVE.get(), ShockwaveParticle.Provider::new);
    }

    private ClientSetup() {}
}
