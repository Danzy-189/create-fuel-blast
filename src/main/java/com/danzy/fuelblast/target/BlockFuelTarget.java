package com.danzy.fuelblast.target;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.capability.IFluidHandler;

/** A tank, vessel or fluid container standing in the world (or in a shipyard world). */
public record BlockFuelTarget(ServerLevel level, BlockPos pos) implements FuelTarget {

    @Override
    public Vec3 position() {
        return Vec3.atCenterOf(pos);
    }

    @Override
    public IFluidHandler handler() {
        BlockEntity be = level.getBlockEntity(pos);
        if (be == null || be.isRemoved()) return null;
        return be.getCapability(ForgeCapabilities.FLUID_HANDLER).orElse(null);
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
