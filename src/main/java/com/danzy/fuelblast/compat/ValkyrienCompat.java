package com.danzy.fuelblast.compat;

import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Valkyrien Skies support (used by Create Aeronautics for physics-driven airships).
 *
 * A blast that happens in world space must also be tested inside the shipyard space of
 * every ship near it, otherwise tanks bolted to a flying ship are never found.
 * VS ships keep real blocks and block entities in the shipyard, so once the position is
 * transformed the normal block scan just works.
 */
public final class ValkyrienCompat {

    private static final String UTILS = "org.valkyrienskies.mod.common.VSGameUtilsKt";

    public static boolean available() {
        return Reflect.present(UTILS);
    }

    /**
     * @return the original blast position plus its equivalent inside every nearby ship.
     */
    public static List<Vec3> origins(Level level, Vec3 center, double radius) {
        List<Vec3> result = new ArrayList<>(2);
        result.add(center);
        if (!available()) return result;

        Class<?> utils = Reflect.clazz(UTILS);
        Method m = Reflect.methodByName(utils, "transformToNearbyShipsAndWorld", 5);
        Object transformed = Reflect.invoke(m, null, level, center.x, center.y, center.z, radius);
        if (!(transformed instanceof Iterable<?> iterable)) return result;

        for (Object o : iterable) {
            Vec3 v = toVec3(o);
            if (v != null && v.distanceToSqr(center) > 1.0E-6D) result.add(v);
        }
        return result;
    }

    private static Vec3 toVec3(Object o) {
        if (o instanceof Vec3 v) return v;
        if (o == null) return null;
        try {
            Object x = Reflect.field(o, "x");
            Object y = Reflect.field(o, "y");
            Object z = Reflect.field(o, "z");
            if (x instanceof Number nx && y instanceof Number ny && z instanceof Number nz) {
                return new Vec3(nx.doubleValue(), ny.doubleValue(), nz.doubleValue());
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private ValkyrienCompat() {}
}
