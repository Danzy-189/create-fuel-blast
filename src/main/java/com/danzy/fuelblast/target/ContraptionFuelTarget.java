package com.danzy.fuelblast.target;

import com.danzy.fuelblast.compat.ContraptionCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fluids.capability.IFluidHandler;

/**
 * One mounted tank welded onto an assembled contraption - a Create vehicle or a
 * Create Aeronautics aircraft. The tank has no block entity, so its contents come from the
 * contraption's MountedStorageManager, and its position follows the moving structure.
 */
public record ContraptionFuelTarget(Entity contraption, BlockPos localPos) implements FuelTarget {

    @Override
    public Vec3 position() {
        return ContraptionCompat.toWorld(contraption, localPos);
    }

    @Override
    public Level level() {
        return contraption.level();
    }

    @Override
    public IFluidHandler handler() {
        return ContraptionCompat.storageAt(contraption, localPos);
    }

    @Override
    public String key() {
        return "contraption:" + contraption.getId() + "@" + localPos.asLong();
    }

    @Override
    public String describe() {
        return "mounted tank " + localPos.toShortString() + " on " + contraption.getType().toShortString()
                + " #" + contraption.getId();
    }

    @Override
    public boolean isValid() {
        return contraption.isAlive() && handler() != null;
    }
}
