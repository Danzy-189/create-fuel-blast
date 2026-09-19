package com.danzy.fuelblast.client;

import com.danzy.fuelblast.FuelBlast;
import com.danzy.fuelblast.client.particle.EmberParticle;
import com.danzy.fuelblast.client.particle.FireballParticle;
import com.danzy.fuelblast.client.particle.ShockwaveParticle;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;

@EventBusSubscriber(modid = FuelBlast.ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class ClientSetup {

    @SubscribeEvent
    public static void registerParticles(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(ModParticles.FIREBALL.get(), FireballParticle.Provider::new);
        event.registerSpriteSet(ModParticles.EMBER.get(), EmberParticle.Provider::new);
        event.registerSpriteSet(ModParticles.SHOCKWAVE.get(), ShockwaveParticle.Provider::new);
    }

    private ClientSetup() {}
}
