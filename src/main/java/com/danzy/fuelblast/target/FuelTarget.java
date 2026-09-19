package com.danzy.fuelblast.target;

import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

/**
 * Something that stores fuel and can blow up: a block in the world, a tank mounted on a
 * moving contraption, or the pooled fuel of a whole physics airship.
 */
public interface FuelTarget {

    /** Current world position - contraption targets move between priming and detonation. */
    Vec3 position();

    /** Level the blast should happen in (an airship interior is its own level). */
    Level level();

    /** Live fluid handler, or null when the target is gone. */
    IFluidHandler handler();

    /** Stable key used to avoid priming the same tank twice. */
    String key();

    /** Human readable description, used by the /fuelblast diagnostics command. */
    String describe();

    /** Remove the physical container after its fuel charge has detonated. */
    default void removeAfterDetonation() { }

    boolean isValid();
}
