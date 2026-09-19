package com.danzy.fuelblast;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Burning fuel does not simply vanish with the shockwave: it sprays around and keeps
 * burning where it lands. After the blast, fire is scattered over the surroundings with a
 * density that falls off with distance, plus a few denser pools near the centre, and
 * anything caught in the radius is set alight.
 */
public final class FireScatter {

    public static void scatter(Level level, Vec3 center, float power, int fuelMb) {
        if (!FuelBlastConfig.leaveFire.get()) return;

        RandomSource rng = level.getRandom();
        double radius = Math.max(2.0D, power * FuelBlastConfig.fireRadiusFactor.get());
        int budget = FuelBlastConfig.maxFireBlocks.get();
        int attempts = (int) Math.min(budget * 4L, Math.round(radius * radius * 2.5D));
        double chance = FuelBlastConfig.fireChance.get();
        int placed = 0;

        for (int i = 0; i < attempts && placed < budget; i++) {
            double angle = rng.nextDouble() * Math.PI * 2;
            double dist = radius * Math.sqrt(rng.nextDouble());            // uniform over the disc
            double x = center.x + Math.cos(angle) * dist;
            double z = center.z + Math.sin(angle) * dist;
            double y = center.y + (rng.nextDouble() - 0.4D) * radius * 0.7D;

            BlockPos pos = BlockPos.containing(x, y, z);
            pos = dropToSurface(level, pos, 8);
            if (pos == null) continue;

            // Denser near the centre, sparse at the edge - looks like splashed fuel, not a grid.
            double falloff = 1.0D - (dist / (radius + 1.0D));
            if (rng.nextDouble() > chance * (0.35D + 0.65D * falloff)) continue;

            if (place(level, pos)) {
                placed++;
                // Occasionally a pool: a couple of extra tiles clustered around the same spot.
                if (rng.nextFloat() < 0.35F) {
                    for (int j = 0; j < 2 && placed < budget; j++) {
                        BlockPos side = pos.offset(rng.nextInt(3) - 1, 0, rng.nextInt(3) - 1);
                        BlockPos grounded = dropToSurface(level, side, 3);
                        if (grounded != null && place(level, grounded)) placed++;
                    }
                }
            }
        }

        if (FuelBlastConfig.igniteEntities.get()) {
            int seconds = (int) Math.min(20, 3 + power * 0.8F);
            for (Entity entity : level.getEntities((Entity) null,
                    new AABB(center, center).inflate(radius), e -> !e.fireImmune())) {
                if (entity.position().distanceTo(center) > radius) continue;
                entity.igniteForSeconds(seconds);
            }
        }

        if (FuelBlastConfig.debugLogging.get()) {
            FuelBlast.LOGGER.info("[fuelblast] scattered {} fire block(s) over r={}",
                    placed, String.format("%.1f", radius));
        }
    }

    /** Lets fuel "fall" a few blocks so fire ends up on surfaces instead of hanging in the air. */
    private static BlockPos dropToSurface(Level level, BlockPos pos, int maxDrop) {
        BlockPos current = pos;
        for (int i = 0; i < maxDrop; i++) {
            if (!level.getBlockState(current).isAir()) {
                // started inside a block: try the space just above it
                if (i == 0) {
                    BlockPos above = current.above();
                    return level.getBlockState(above).isAir() ? above : null;
                }
                return current.above();
            }
            current = current.below();
        }
        return null;
    }

    private static boolean place(Level level, BlockPos pos) {
        if (!level.isLoaded(pos)) return false;
        if (!level.getBlockState(pos).isAir()) return false;

        BlockState fire = BaseFireBlock.getState(level, pos);
        if (fire.isAir() || fire.is(Blocks.AIR)) return false;
        if (!fire.canSurvive(level, pos)) return false;

        level.setBlock(pos, fire, Block.UPDATE_ALL);
        return true;
    }

    private FireScatter() {}
}
