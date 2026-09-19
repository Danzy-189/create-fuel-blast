package com.danzy.fuelblast;

import net.minecraft.world.level.Explosion;

import java.lang.reflect.Field;

/** Small helper to read the radius of an explosion across mappings/mods. */
public final class ExplosionAccess {
    private static Field radiusField;

    public static float radiusOf(Explosion explosion) {
        try {
            if (radiusField == null) {
                for (Field f : Explosion.class.getDeclaredFields()) {
                    if (f.getType() == float.class) {
                        f.setAccessible(true);
                        radiusField = f;
                        break;
                    }
                }
            }
            if (radiusField != null) return radiusField.getFloat(explosion);
        } catch (Exception ignored) {
        }
        return 4.0F;
    }

    private ExplosionAccess() {}
}
