package com.danzy.fuelblast.client;

import com.danzy.fuelblast.network.BlastEffectPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraftforge.registries.ForgeRegistries;
import org.joml.Vector3f;

/**
 * Layered fuel-air explosion VFX:
 *   1. white flash + pressure disc
 *   2. low fireball ring hugging the ground
 *   3. rising, cooling mushroom column
 *   4. burning debris and a fine mist tinted with the fuel's own colour
 *   5. long-lived smoke that lingers after the fire dies
 */
public final class BlastEffects {

    public static void play(BlastEffectPacket packet) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return;

        Vec3 c = packet.pos();
        float power = packet.power();
        RandomSource rng = level.random;
        double distance = mc.player == null ? 0 : mc.player.position().distanceTo(c);
        if (distance > 192) return;

        float scale = Mth.clamp(power / 4.0F, 0.5F, 4.0F);
        int fluidColor = tintOf(packet.fluid());

        // --- 1. flash and pressure front -------------------------------------------------
        level.addParticle(ParticleTypes.FLASH, c.x, c.y, c.z, 0, 0, 0);
        level.addParticle(ModParticles.SHOCKWAVE.get(), c.x, c.y + 0.3D, c.z, 0, scale * 0.1D, 0);

        int ringCount = (int) (28 + power * 9);
        for (int i = 0; i < ringCount; i++) {
            double a = (Math.PI * 2 * i) / ringCount + rng.nextDouble() * 0.05D;
            double speed = (0.35D + rng.nextDouble() * 0.25D) * scale;
            double dx = Math.cos(a) * speed;
            double dz = Math.sin(a) * speed;
            level.addParticle(ModParticles.FIREBALL.get(),
                    c.x + Math.cos(a) * 0.6D, c.y + 0.1D + rng.nextDouble() * 0.4D, c.z + Math.sin(a) * 0.6D,
                    dx, 0.02D + rng.nextDouble() * 0.05D, dz);
            level.addParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE,
                    c.x, c.y + 0.1D, c.z, dx * 1.4D, 0.02D, dz * 1.4D);
        }

        // --- 2. core fireball --------------------------------------------------------------
        int coreCount = (int) (18 + power * 7);
        for (int i = 0; i < coreCount; i++) {
            Vec3 dir = randomUnit(rng).scale((0.12D + rng.nextDouble() * 0.35D) * scale);
            level.addParticle(ModParticles.FIREBALL.get(),
                    c.x + dir.x, c.y + dir.y + 0.3D, c.z + dir.z,
                    dir.x, dir.y * 0.6D + 0.05D, dir.z);
        }

        // --- 3. mushroom column ------------------------------------------------------------
        int columnCount = (int) (10 + power * 4);
        for (int i = 0; i < columnCount; i++) {
            double h = rng.nextDouble() * scale * 2.2D;
            double spread = 0.25D + h * 0.22D;
            level.addParticle(ModParticles.FIREBALL.get(),
                    c.x + (rng.nextDouble() - 0.5D) * spread,
                    c.y + 0.5D + h,
                    c.z + (rng.nextDouble() - 0.5D) * spread,
                    (rng.nextDouble() - 0.5D) * 0.05D,
                    0.12D + rng.nextDouble() * 0.12D * scale,
                    (rng.nextDouble() - 0.5D) * 0.05D);
        }

        // --- 4. debris, sparks and fuel mist -------------------------------------------------
        int emberCount = (int) (30 + power * 16);
        for (int i = 0; i < emberCount; i++) {
            Vec3 dir = randomUnit(rng).scale((0.35D + rng.nextDouble() * 0.9D) * scale * 0.6D);
            level.addParticle(ModParticles.EMBER.get(),
                    c.x, c.y + 0.4D, c.z, dir.x, Math.abs(dir.y) * 1.4D, dir.z);
        }
        for (int i = 0; i < power * 3; i++) {
            Vec3 dir = randomUnit(rng).scale(0.4D + rng.nextDouble() * 0.5D);
            level.addParticle(ParticleTypes.LAVA,
                    c.x, c.y + 0.4D, c.z, dir.x, Math.abs(dir.y), dir.z);
        }

        DustParticleOptions mist = new DustParticleOptions(
                new Vector3f(((fluidColor >> 16) & 0xFF) / 255.0F,
                        ((fluidColor >> 8) & 0xFF) / 255.0F,
                        (fluidColor & 0xFF) / 255.0F), 1.6F);
        for (int i = 0; i < 25 + power * 6; i++) {
            Vec3 dir = randomUnit(rng).scale(0.5D + rng.nextDouble() * 1.2D * scale);
            level.addParticle(mist, c.x + dir.x, c.y + 0.5D + dir.y, c.z + dir.z,
                    dir.x * 0.08D, 0.03D, dir.z * 0.08D);
        }

        // --- 5. lingering smoke -----------------------------------------------------------
        for (int i = 0; i < 18 + power * 5; i++) {
            double spread = scale * 1.6D;
            level.addParticle(ParticleTypes.LARGE_SMOKE,
                    c.x + (rng.nextDouble() - 0.5D) * spread,
                    c.y + 0.6D + rng.nextDouble() * scale,
                    c.z + (rng.nextDouble() - 0.5D) * spread,
                    (rng.nextDouble() - 0.5D) * 0.04D,
                    0.05D + rng.nextDouble() * 0.09D,
                    (rng.nextDouble() - 0.5D) * 0.04D);
        }

        // --- sound ---------------------------------------------------------------------------
        float volume = Mth.clamp(2.0F + power * 0.9F, 1.0F, 18.0F);
        float pitch = Mth.clamp(1.25F - power * 0.06F, 0.45F, 1.2F);
        level.playLocalSound(c.x, c.y, c.z, SoundEvents.GENERIC_EXPLODE, SoundSource.BLOCKS, volume, pitch, false);
        level.playLocalSound(c.x, c.y, c.z, SoundEvents.FIRECHARGE_USE, SoundSource.BLOCKS, volume * 0.7F, 0.5F, false);
        if (power > 6.0F) {
            level.playLocalSound(c.x, c.y, c.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.BLOCKS,
                    volume * 0.35F, 0.6F, false);
        }
    }

    private static Vec3 randomUnit(RandomSource rng) {
        double theta = rng.nextDouble() * Math.PI * 2;
        double y = rng.nextDouble() * 2 - 1;
        double r = Math.sqrt(1 - y * y);
        return new Vec3(Math.cos(theta) * r, y, Math.sin(theta) * r);
    }

    private static int tintOf(net.minecraft.resources.ResourceLocation fluidId) {
        Fluid fluid = ForgeRegistries.FLUIDS.getValue(fluidId);
        if (fluid == null) return 0xC8823C;
        try {
            int tint = IClientFluidTypeExtensions.of(fluid).getTintColor();
            return tint == -1 ? 0xC8823C : (tint & 0xFFFFFF);
        } catch (Exception e) {
            return 0xC8823C;
        }
    }

    private BlastEffects() {}
}
