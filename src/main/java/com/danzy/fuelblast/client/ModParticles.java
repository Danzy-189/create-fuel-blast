package com.danzy.fuelblast.client;

import com.danzy.fuelblast.FuelBlast;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Particle types used by the blast VFX. Safe to load on a dedicated server.
 * The anonymous subclasses only exist so the protected vanilla constructor is reachable
 * without relying on a loader patch.
 */
public final class ModParticles {
    public static final DeferredRegister<ParticleType<?>> PARTICLES =
            DeferredRegister.create(BuiltInRegistries.PARTICLE_TYPE, FuelBlast.ID);

    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> FIREBALL =
            PARTICLES.register("fireball", () -> new SimpleParticleType(true) {});
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> EMBER =
            PARTICLES.register("ember", () -> new SimpleParticleType(true) {});
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> SHOCKWAVE =
            PARTICLES.register("shockwave", () -> new SimpleParticleType(true) {});

    private ModParticles() {}
}
