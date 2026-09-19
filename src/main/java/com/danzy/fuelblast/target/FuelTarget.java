package com.danzy.fuelblast.target;

import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraft.world.phys.Vec3;

/**
 * Something that stores fuel and can blow up. It may be a block in the world, a tank
 * mounted on a moving Create contraption, or a tank on an Aeronautics airship.
 */
public interface FuelTarget {

    /** Current world position - contraption targets move between priming and detonation. */
    Vec3 position();

    /** Live fluid handler, or null when the target is gone. */
    IFluidHandler handler();

    /** Stable key used to avoid priming the same tank twice. */
    String key();

    boolean isValid();
}
