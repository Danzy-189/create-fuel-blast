package com.danzy.fuelblast.compat;

import com.danzy.fuelblast.FuelBlast;
import com.danzy.fuelblast.target.ContraptionFuelTarget;
import com.danzy.fuelblast.target.FuelTarget;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Support for tanks mounted on assembled contraptions - Create trains, gantries,
 * and, most importantly, Create Aeronautics airships and aircraft.
 *
 * Mounted tanks are not block entities: Create moves their contents into the
 * contraption's MountedStorageManager. This class reaches into that storage through
 * reflection so no compile-time dependency on internal Create APIs is needed.
 */
public final class ContraptionCompat {

    private static final String ENTITY_CLASS = "com.simibubi.create.content.contraptions.AbstractContraptionEntity";

    public static boolean available() {
        return Reflect.present(ENTITY_CLASS);
    }

    /** Finds every fuel tank riding a contraption within {@code radius} of the blast. */
    public static void collect(ServerLevel level, Vec3 center, double radius, Consumer<FuelTarget> out) {
        if (!available()) return;
        Class<?> entityClass = Reflect.clazz(ENTITY_CLASS);

        AABB box = new AABB(center, center).inflate(radius + 32.0D);   // contraptions are big
        List<Entity> contraptions = level.getEntities((Entity) null, box, entityClass::isInstance);
        if (contraptions.isEmpty()) return;

        double radiusSq = radius * radius;
        for (Entity entity : contraptions) {
            for (BlockPos local : fluidStoragePositions(entity)) {
                Vec3 world = toWorld(entity, local);
                if (world.distanceToSqr(center) > radiusSq) continue;
                out.accept(new ContraptionFuelTarget(entity, local));
            }
        }
    }

    /** Local (structure space) positions of every mounted fluid storage. */
    @SuppressWarnings("unchecked")
    public static List<BlockPos> fluidStoragePositions(Entity contraptionEntity) {
        List<BlockPos> result = new ArrayList<>();
        Object storage = storageManager(contraptionEntity);
        if (storage == null) return result;

        Map<BlockPos, ?> map = fluidStorageMap(storage);
        if (map == null) return result;

        result.addAll(map.keySet());
        return result;
    }

    /** Live handler of one mounted tank, or null. */
    public static IFluidHandler storageAt(Entity contraptionEntity, BlockPos local) {
        Object storage = storageManager(contraptionEntity);
        if (storage == null) return null;

        Map<BlockPos, ?> map = fluidStorageMap(storage);
        if (map == null) return combinedHandler(storage);

        Object mounted = map.get(local);
        if (mounted == null) return null;

        for (String name : new String[]{"getFluidHandler", "getFluids", "getTank", "getFluidTank"}) {
            Method m = Reflect.methodByName(mounted.getClass(), name, 0);
            Object handler = Reflect.invoke(m, mounted);
            if (handler instanceof IFluidHandler h) return h;
        }
        Object field = Reflect.fieldOfType(mounted, IFluidHandler.class);
        return field instanceof IFluidHandler h ? h : null;
    }

    /** Fallback: one handler covering the whole contraption. */
    public static IFluidHandler combinedHandler(Object storageManager) {
        Method m = Reflect.methodByName(storageManager.getClass(), "getFluids", 0);
        Object handler = Reflect.invoke(m, storageManager);
        return handler instanceof IFluidHandler h ? h : null;
    }

    /** Structure-space position -> world position, following rotation and movement. */
    public static Vec3 toWorld(Entity contraptionEntity, BlockPos local) {
        Vec3 localCenter = Vec3.atCenterOf(local);
        Method m = Reflect.method(contraptionEntity.getClass(), "toGlobalVector", Vec3.class, float.class);
        if (m == null) m = Reflect.methodByName(contraptionEntity.getClass(), "toGlobalVector", 2);
        Object global = Reflect.invoke(m, contraptionEntity, localCenter, 1.0F);
        if (global instanceof Vec3 v) return v;
        return contraptionEntity.position().add(localCenter);
    }

    private static Object storageManager(Entity contraptionEntity) {
        Method getContraption = Reflect.methodByName(contraptionEntity.getClass(), "getContraption", 0);
        Object contraption = Reflect.invoke(getContraption, contraptionEntity);
        if (contraption == null) return null;

        for (String name : new String[]{"getStorage", "getSharedInventory", "getStorageManager"}) {
            Method m = Reflect.methodByName(contraption.getClass(), name, 0);
            Object storage = Reflect.invoke(m, contraption);
            if (storage != null && storage.getClass().getName().contains("Storage")) return storage;
        }
        Object field = Reflect.field(contraption, "storage");
        if (field != null) return field;

        FuelBlast.LOGGER.debug("Could not read mounted storage of {}", contraption.getClass());
        return null;
    }

    /** The Map&lt;BlockPos, MountedFluidStorage&gt; inside a MountedStorageManager. */
    @SuppressWarnings("unchecked")
    private static Map<BlockPos, ?> fluidStorageMap(Object storageManager) {
        Object direct = Reflect.field(storageManager, "fluidStorage", "mountedFluidStorage", "fluids");
        if (direct instanceof Map<?, ?> map && looksLikeFluidMap(map)) return (Map<BlockPos, ?>) map;

        Class<?> c = storageManager.getClass();
        while (c != null && c != Object.class) {
            for (java.lang.reflect.Field f : c.getDeclaredFields()) {
                try {
                    f.setAccessible(true);
                    Object value = f.get(storageManager);
                    if (value instanceof Map<?, ?> map && looksLikeFluidMap(map)) return (Map<BlockPos, ?>) map;
                } catch (Throwable ignored) {
                }
            }
            c = c.getSuperclass();
        }
        return null;
    }

    private static boolean looksLikeFluidMap(Map<?, ?> map) {
        if (map.isEmpty()) return false;
        Map.Entry<?, ?> first = map.entrySet().iterator().next();
        return first.getKey() instanceof BlockPos
                && first.getValue() != null
                && first.getValue().getClass().getSimpleName().toLowerCase().contains("fluid");
    }

    private ContraptionCompat() {}
}
