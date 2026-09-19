package com.danzy.fuelblast;

import net.minecraft.world.level.Explosion;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Reads the centre and radius of an explosion. Both moved around between 1.20 and 1.21,
 * so everything is probed once and cached instead of being hard-coded.
 */
public final class ExplosionAccess {
    private static Method centerMethod;
    private static Field radiusField;
    private static boolean centerProbed;

    public static Vec3 centerOf(Explosion explosion) {
        if (!centerProbed) {
            centerProbed = true;
            for (String name : new String[]{"center", "getPosition"}) {
                try {
                    Method m = Explosion.class.getMethod(name);
                    if (Vec3.class.isAssignableFrom(m.getReturnType())) {
                        m.setAccessible(true);
                        centerMethod = m;
                        break;
                    }
                } catch (Throwable ignored) {
                }
            }
        }
        if (centerMethod != null) {
            try {
                Object v = centerMethod.invoke(explosion);
                if (v instanceof Vec3 vec) return vec;
            } catch (Throwable ignored) {
            }
        }
        // NeoForge runs on official mappings, so the raw fields are readable as a fallback.
        try {
            double x = readDouble(explosion, "x");
            double y = readDouble(explosion, "y");
            double z = readDouble(explosion, "z");
            return new Vec3(x, y, z);
        } catch (Throwable t) {
            return Vec3.ZERO;
        }
    }

    public static float radiusOf(Explosion explosion) {
        try {
            if (radiusField == null) {
                try {
                    radiusField = Explosion.class.getDeclaredField("radius");
                } catch (NoSuchFieldException e) {
                    for (Field f : Explosion.class.getDeclaredFields()) {
                        if (f.getType() == float.class) {
                            radiusField = f;
                            break;
                        }
                    }
                }
                if (radiusField != null) radiusField.setAccessible(true);
            }
            if (radiusField != null) return radiusField.getFloat(explosion);
        } catch (Throwable ignored) {
        }
        return 4.0F;
    }

    private static double readDouble(Explosion explosion, String name) throws Exception {
        Field f = Explosion.class.getDeclaredField(name);
        f.setAccessible(true);
        return f.getDouble(explosion);
    }

    private ExplosionAccess() {}
}
