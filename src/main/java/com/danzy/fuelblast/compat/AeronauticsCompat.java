package com.danzy.fuelblast.compat;

import com.danzy.fuelblast.FuelBlast;
import com.danzy.fuelblast.FuelBlastConfig;
import com.danzy.fuelblast.FuelScan;
import com.danzy.fuelblast.target.BlockFuelTarget;
import com.danzy.fuelblast.target.FuelTarget;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.function.Consumer;

/**
 * Create Aeronautics keeps the inside of a physics airship as real blocks in a separate
 * level (its own dimension or a wrapped world) and only renders them through the airship
 * entity. A blast outside the hull therefore has to be projected into that level before any
 * tank can be found; a blast inside it is handled by the normal scan, because the event
 * fires in that level like in any other.
 *
 * Projection: entity.toLocalVector() gives structure space (Create's own API, implemented by
 * every contraption entity), and the interior anchor - if the airship exposes one - shifts it
 * into interior coordinates.
 */
public final class AeronauticsCompat {

    private static final String[] INTERIOR_GETTERS = {
            "getContraptionWorld", "getContraptionLevel", "getSimulationWorld", "getSimulationLevel",
            "getInteriorLevel", "getInteriorWorld", "getAirshipWorld", "getAirshipLevel", "getPlotWorld"
    };
    private static final String[] ANCHOR_FIELDS = {
            "plotPos", "plotPosition", "airshipPos", "interiorAnchor", "anchor", "origin", "offset"
    };

    /** Projects the blast into every nearby airship interior and scans it for tanks. */
    public static void collect(Level level, Vec3 center, double radius, Consumer<FuelTarget> out) {
        if (!ContraptionCompat.available()) return;

        for (Entity ship : ContraptionCompat.nearbyContraptions(level, center, radius)) {
            Level interior = interiorOf(ship);
            if (interior == null || interior == level) continue;

            Vec3 local = toLocal(ship, center);
            if (local == null) continue;

            Vec3i anchor = interiorAnchor(ship);
            Vec3 interiorPos = local.add(anchor.getX(), anchor.getY(), anchor.getZ());

            if (FuelBlastConfig.debugLogging.get()) {
                FuelBlast.LOGGER.info("[fuelblast] airship #{} interior {} -> scanning around {}",
                        ship.getId(), interior.dimension().location(), BlockPos.containing(interiorPos).toShortString());
            }

            FuelScan.scanBlocks(interior, interiorPos, radius, pos -> out.accept(new BlockFuelTarget(interior, pos)));
        }
    }

    /** The level that holds the airship's interior blocks, if the mod exposes one. */
    public static Level interiorOf(Entity ship) {
        for (String getter : INTERIOR_GETTERS) {
            Object value = Reflect.invoke(Reflect.methodByName(ship.getClass(), getter, 0), ship);
            if (value instanceof Level l) return l;
        }
        Object contraption = Reflect.invoke(Reflect.methodByName(ship.getClass(), "getContraption", 0), ship);
        if (contraption != null) {
            for (String getter : INTERIOR_GETTERS) {
                Object value = Reflect.invoke(Reflect.methodByName(contraption.getClass(), getter, 0), contraption);
                if (value instanceof Level l) return l;
            }
            Object field = Reflect.fieldOfType(contraption, Level.class);
            if (field instanceof Level l && l != ship.level()) return l;
        }
        Object entityField = Reflect.fieldOfType(ship, Level.class);
        return entityField instanceof Level l && l != ship.level() ? l : null;
    }

    /** Where the airship's structure sits inside its interior level, if it is offset at all. */
    private static Vec3i interiorAnchor(Entity ship) {
        Object contraption = Reflect.invoke(Reflect.methodByName(ship.getClass(), "getContraption", 0), ship);
        for (Object holder : new Object[]{ship, contraption}) {
            if (holder == null) continue;
            Object value = Reflect.field(holder, ANCHOR_FIELDS);
            if (value instanceof Vec3i pos) return pos;
            for (String getter : new String[]{"getPlotPos", "getAnchor", "getInteriorAnchor"}) {
                Object got = Reflect.invoke(Reflect.methodByName(holder.getClass(), getter, 0), holder);
                if (got instanceof Vec3i pos) return pos;
            }
        }
        return Vec3i.ZERO;
    }

    private static Vec3 toLocal(Entity ship, Vec3 worldPos) {
        Method m = Reflect.method(ship.getClass(), "toLocalVector", Vec3.class, float.class);
        if (m == null) m = Reflect.methodByName(ship.getClass(), "toLocalVector", 2);
        Object local = Reflect.invoke(m, ship, worldPos, 1.0F);
        if (local instanceof Vec3 v) return v;

        // No transform available: fall back to a plain offset from the entity.
        return worldPos.subtract(ship.position());
    }

    /** Unused fields guard so the reflective field list stays honest in reviews. */
    static Field[] declaredFields(Object o) {
        return o == null ? new Field[0] : o.getClass().getDeclaredFields();
    }

    private AeronauticsCompat() {}
}
