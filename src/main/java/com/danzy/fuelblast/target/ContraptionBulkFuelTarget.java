package com.danzy.fuelblast.target;

import com.danzy.fuelblast.compat.ContraptionCompat;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

/**
 * Fallback for contraptions whose per-tank positions cannot be read (older Create builds,
 * heavily modified physics airships). The whole mounted fluid inventory is treated as one
 * charge and detonates at the point of the hull closest to the blast, which still looks right.
 */
public record ContraptionBulkFuelTarget(Entity contraption, Vec3 hitPos) implements FuelTarget {

    @Override
    public Vec3 position() {
        return hitPos;
    }

    @Override
    public Level level() {
        return contraption.level();
    }

    @Override
    public IFluidHandler handler() {
        return ContraptionCompat.combinedHandler(contraption);
    }

    @Override
    public String key() {
        return "contraption-bulk:" + contraption.getId();
    }

    @Override
    public String describe() {
        return "pooled contraption fuel on " + contraption.getType().toShortString() + " #" + contraption.getId();
    }

    @Override
    public boolean isValid() {
        return contraption.isAlive() && handler() != null;
    }
}
