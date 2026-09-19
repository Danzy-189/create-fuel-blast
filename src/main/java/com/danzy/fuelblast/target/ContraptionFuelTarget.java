package com.danzy.fuelblast.target;

import com.danzy.fuelblast.compat.ContraptionCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fluids.capability.IFluidHandler;

/**
 * A tank welded onto an assembled Create / Create Aeronautics contraption.
 * The tank has no block entity in the world, so everything goes through the
 * contraption's mounted storage, and the position follows the moving structure.
 */
public record ContraptionFuelTarget(Entity contraption, BlockPos localPos) implements FuelTarget {

    @Override
    public Vec3 position() {
        return ContraptionCompat.toWorld(contraption, localPos);
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
    public boolean isValid() {
        return contraption.isAlive() && handler() != null;
    }
}
