package com.danzy.fuelblast.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.*;
import net.minecraft.core.particles.SimpleParticleType;

/** Burning debris thrown out of the blast: bright, arcing, flickering sparks. */
public class EmberParticle extends TextureSheetParticle {

    private final SpriteSet sprites;
    private float flicker;

    protected EmberParticle(ClientLevel level, double x, double y, double z,
                            double vx, double vy, double vz, SpriteSet sprites) {
        super(level, x, y, z, 0, 0, 0);
        this.sprites = sprites;
        this.xd = vx;
        this.yd = vy;
        this.zd = vz;
        this.quadSize = 0.06F + this.random.nextFloat() * 0.09F;
        this.lifetime = 30 + this.random.nextInt(50);
        this.gravity = 0.32F;
        this.friction = 0.96F;
        this.hasPhysics = true;
        this.setSpriteFromAge(sprites);
        this.setColor(1.0F, 0.72F, 0.25F);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.removed) return;
        this.setSpriteFromAge(this.sprites);

        float t = (float) this.age / (float) this.lifetime;
        this.flicker = 0.75F + this.random.nextFloat() * 0.25F;
        this.setColor(1.0F * this.flicker, (0.72F - 0.5F * t) * this.flicker, (0.25F - 0.22F * t) * this.flicker);
        this.alpha = 1.0F - t * t;

        if (this.onGround) {
            this.xd *= 0.5D;
            this.zd *= 0.5D;
        }
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    @Override
    public int getLightColor(float partialTick) {
        return 240 | (super.getLightColor(partialTick) & 0xFFFF0000);
    }

    public record Provider(SpriteSet sprites) implements ParticleProvider<SimpleParticleType> {
        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level,
                                       double x, double y, double z, double vx, double vy, double vz) {
            return new EmberParticle(level, x, y, z, vx, vy, vz, sprites);
        }
    }
}
