package com.danzy.fuelblast.target;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

/** A tank, vessel or fluid container standing in the world (or in an airship interior). */
public record BlockFuelTarget(ServerLevel level, BlockPos pos) implements FuelTarget {

    @Override
    public Vec3 position() {
        return Vec3.atCenterOf(pos);
    }

    @Override
    public IFluidHandler handler() {
        return Capabilities.FluidHandler.BLOCK.getCapability(level, pos, null, null, null);
    }

    @Override
    public String key() {
        return level.dimension().location() + "@" + pos.asLong();
    }

    @Override
    public boolean isValid() {
        return handler() != null;
    }
}
