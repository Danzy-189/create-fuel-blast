package com.danzy.fuelblast.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.*;
import net.minecraft.core.particles.SimpleParticleType;

/**
 * The body of the fireball: a puff that expands fast, glows white-hot, cools down
 * through orange and deep red, then turns into greasy black smoke before dying.
 */
public class FireballParticle extends TextureSheetParticle {

    private final SpriteSet sprites;
    private final float maxSize;

    protected FireballParticle(ClientLevel level, double x, double y, double z,
                               double vx, double vy, double vz, SpriteSet sprites) {
        super(level, x, y, z, 0, 0, 0);
        this.sprites = sprites;

        this.xd = vx;
        this.yd = vy;
        this.zd = vz;

        double speed = Math.sqrt(vx * vx + vy * vy + vz * vz);
        this.maxSize = (float) (1.6D + speed * 7.0D + this.random.nextFloat() * 1.4D);
        this.quadSize = this.maxSize * 0.25F;

        this.lifetime = 22 + this.random.nextInt(18) + (int) (speed * 22.0D);
        this.friction = 0.88F;
        this.gravity = -0.012F;              // hot gas rises
        this.hasPhysics = true;
        this.roll = this.random.nextFloat() * (float) Math.PI * 2.0F;
        this.oRoll = this.roll;

        this.setSpriteFromAge(sprites);
        this.setColor(1.0F, 0.95F, 0.7F);
        this.alpha = 0.95F;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.removed) return;

        this.setSpriteFromAge(this.sprites);

        float t = (float) this.age / (float) this.lifetime;

        // Growth: violent at first, then it just drifts and bloats slowly.
        float growth = t < 0.25F ? t / 0.25F : 1.0F;
        this.quadSize = this.maxSize * (0.25F + 0.75F * growth) * (1.0F + t * 0.45F);

        // Cooling curve: white -> yellow -> orange -> red -> smoke.
        if (t < 0.15F) {
            this.setColor(1.0F, 0.97F, 0.80F);
        } else if (t < 0.35F) {
            float k = (t - 0.15F) / 0.20F;
            this.setColor(1.0F, lerp(0.97F, 0.62F, k), lerp(0.80F, 0.16F, k));
        } else if (t < 0.65F) {
            float k = (t - 0.35F) / 0.30F;
            this.setColor(lerp(1.0F, 0.55F, k), lerp(0.62F, 0.15F, k), lerp(0.16F, 0.05F, k));
        } else {
            float k = (t - 0.65F) / 0.35F;
            this.setColor(lerp(0.55F, 0.16F, k), lerp(0.15F, 0.15F, k), lerp(0.05F, 0.15F, k));
        }

        this.alpha = t < 0.7F ? 0.95F : 0.95F * (1.0F - (t - 0.7F) / 0.3F);

        this.oRoll = this.roll;
        this.roll += 0.02F;
        this.yd += 0.0016D;                  // buoyancy of the rising column
    }

    private static float lerp(float a, float b, float k) {
        return a + (b - a) * Math.min(1.0F, Math.max(0.0F, k));
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    @Override
    public int getLightColor(float partialTick) {
        float t = (float) this.age / (float) this.lifetime;
        int packed = super.getLightColor(partialTick);
        int block = Math.round(Math.max(0, 15 - t * 18));
        int sky = (packed >> 16) & 0xFFFF;
        return (sky << 16) | (Math.max((packed & 0xFF) / 16, block) * 16);
    }

    public record Provider(SpriteSet sprites) implements ParticleProvider<SimpleParticleType> {
        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level,
                                       double x, double y, double z, double vx, double vy, double vz) {
            return new FireballParticle(level, x, y, z, vx, vy, vz, sprites);
        }
    }
}
