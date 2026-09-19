package com.danzy.fuelblast.client;

import com.danzy.fuelblast.FuelBlast;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Particle types used by the blast VFX. Safe to load on a dedicated server. */
public final class ModParticles {
    public static final DeferredRegister<ParticleType<?>> PARTICLES =
            DeferredRegister.create(ForgeRegistries.PARTICLE_TYPES, FuelBlast.ID);

    public static final RegistryObject<SimpleParticleType> FIREBALL =
            PARTICLES.register("fireball", () -> new SimpleParticleType(true));
    public static final RegistryObject<SimpleParticleType> EMBER =
            PARTICLES.register("ember", () -> new SimpleParticleType(true));
    public static final RegistryObject<SimpleParticleType> SHOCKWAVE =
            PARTICLES.register("shockwave", () -> new SimpleParticleType(true));

    private ModParticles() {}
}
