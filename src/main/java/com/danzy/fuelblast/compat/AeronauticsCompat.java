package com.danzy.fuelblast.compat;

import com.danzy.fuelblast.FuelBlast;
import com.danzy.fuelblast.target.BlockFuelTarget;
import com.danzy.fuelblast.target.FuelTarget;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;

import java.lang.reflect.Method;
import java.util.List;
import java.util.function.Consumer;

/**
 * Create Aeronautics specific glue.
 *
 * Aeronautics airships keep their interior as real blocks in a separate simulation
 * level and only render them through the airship entity. A blast happening outside the
 * airship therefore has to be projected into that interior before tanks can be found,
 * and a blast inside the interior has to be able to reach tanks in the outside world.
 */
public final class AeronauticsCompat {

    private static final String[] MOD_IDS = {"aeronautics", "create_aeronautics", "createaeronautics"};
    private static final String[] INTERIOR_GETTERS =
            {"getContraptionWorld", "getContraptionLevel", "getSimulationWorld", "getInteriorLevel", "getAirshipWorld"};

    private static Boolean loaded;

    public static boolean loaded() {
        if (loaded == null) {
            boolean found = false;
            for (String id : MOD_IDS) {
                if (ModList.get().isLoaded(id)) {
                    found = true;
                    FuelBlast.LOGGER.info("Create Aeronautics detected ({}), airship tanks are armed", id);
                    break;
                }
            }
            loaded = found;
        }
        return loaded;
    }

    /** Projects the blast into every nearby airship interior and scans it for tanks. */
    public static void collect(ServerLevel level, Vec3 center, double radius,
                               BlockScanner scanner, Consumer<FuelTarget> out) {
        if (!loaded() || !ContraptionCompat.available()) return;

        Class<?> entityClass = Reflect.clazz("com.simibubi.create.content.contraptions.AbstractContraptionEntity");
        if (entityClass == Void.class) return;

        AABB box = new AABB(center, center).inflate(radius + 64.0D);
        List<Entity> ships = level.getEntities((Entity) null, box, e ->
                entityClass.isInstance(e) && isAeronautics(e));

        for (Entity ship : ships) {
            ServerLevel interior = interiorOf(ship);
            if (interior == null) continue;

            Vec3 local = toLocal(ship, center);
            if (local == null) continue;

            scanner.scan(interior, local, radius, pos -> out.accept(new BlockFuelTarget(interior, pos)));
        }
    }

    public static boolean isAeronautics(Entity entity) {
        String name = entity.getClass().getName().toLowerCase();
        return name.contains("aeronautic") || name.contains("airship") || name.contains("physics");
    }

    private static ServerLevel interiorOf(Entity ship) {
        for (String getter : INTERIOR_GETTERS) {
            Method m = Reflect.methodByName(ship.getClass(), getter, 0);
            Object value = Reflect.invoke(m, ship);
            if (value instanceof ServerLevel sl) return sl;
        }
        Object contraption = Reflect.invoke(Reflect.methodByName(ship.getClass(), "getContraption", 0), ship);
        if (contraption != null) {
            for (String getter : INTERIOR_GETTERS) {
                Method m = Reflect.methodByName(contraption.getClass(), getter, 0);
                Object value = Reflect.invoke(m, contraption);
                if (value instanceof ServerLevel sl) return sl;
            }
            Object field = Reflect.field(contraption, "contraptionWorld", "simulationWorld", "interiorLevel");
            if (field instanceof ServerLevel sl) return sl;
        }
        return null;
    }

    private static Vec3 toLocal(Entity ship, Vec3 worldPos) {
        Method m = Reflect.method(ship.getClass(), "toLocalVector", Vec3.class, float.class);
        if (m == null) m = Reflect.methodByName(ship.getClass(), "toLocalVector", 2);
        Object local = Reflect.invoke(m, ship, worldPos, 1.0F);
        return local instanceof Vec3 v ? v : null;
    }

    /** Callback so the scanning logic lives in one place (ExplosionHandler). */
    @FunctionalInterface
    public interface BlockScanner {
        void scan(ServerLevel level, Vec3 center, double radius, Consumer<BlockPos> out);
    }

    private AeronauticsCompat() {}
}
