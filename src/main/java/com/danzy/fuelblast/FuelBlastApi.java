package com.danzy.fuelblast;

import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Small public bridge for weapon mods that use a private explosion implementation. */
public final class FuelBlastApi {
    private FuelBlastApi() {}

    /** Report a custom explosion after it has been created by another mod. */
    public static void reportExplosion(Level level, Vec3 position, double power) {
        ExplosionHandler.handleExplosion(level, position, power);
    }
}
