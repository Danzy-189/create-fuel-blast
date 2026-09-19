package com.danzy.fuelblast.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.*;
import net.minecraft.core.particles.SimpleParticleType;

/** The pressure front: a bright disc that snaps outwards and vanishes in a few ticks. */
public class ShockwaveParticle extends TextureSheetParticle {

    private final float targetSize;

    protected ShockwaveParticle(ClientLevel level, double x, double y, double z,
                                double vx, double vy, double vz, SpriteSet sprites) {
        super(level, x, y, z, 0, 0, 0);
        this.setSprite(sprites.get(this.random));
        this.targetSize = (float) (4.0D + vy * 26.0D);   // vy is used as intensity
        this.quadSize = 0.4F;
        this.lifetime = 7 + this.random.nextInt(4);
        this.gravity = 0.0F;
        this.friction = 1.0F;
        this.hasPhysics = false;
        this.setColor(1.0F, 0.88F, 0.62F);
        this.alpha = 0.7F;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.removed) return;
        float t = (float) this.age / (float) this.lifetime;
        float eased = 1.0F - (1.0F - t) * (1.0F - t);     // ease-out
        this.quadSize = 0.4F + this.targetSize * eased;
        this.alpha = 0.7F * (1.0F - t);
        this.setColor(1.0F, 0.88F - 0.35F * t, 0.62F - 0.5F * t);
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    @Override
    public int getLightColor(float partialTick) {
        return 240;
    }

    public record Provider(SpriteSet sprites) implements ParticleProvider<SimpleParticleType> {
        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level,
                                       double x, double y, double z, double vx, double vy, double vz) {
            return new ShockwaveParticle(level, x, y, z, vx, vy, vz, sprites);
        }
    }
}
