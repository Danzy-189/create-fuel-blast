package com.danzy.fuelblast.compat;

import com.danzy.fuelblast.FuelBlast;
import com.danzy.fuelblast.FuelBlastConfig;
import com.danzy.fuelblast.target.ContraptionBulkFuelTarget;
import com.danzy.fuelblast.target.ContraptionFuelTarget;
import com.danzy.fuelblast.target.FuelTarget;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fluids.capability.IFluidHandler;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Tanks mounted on assembled contraptions: Create vehicles, trains, and the physics
 * airships and aircraft of Create Aeronautics.
 *
 * When a structure is assembled, Create removes the tank block entities and moves their
 * contents into the contraption's {@code MountedStorageManager}. The layout differs between
 * Create versions:
 *
 *   Create 6 (1.21.1): manager.getFluids().storages  -> Map&lt;BlockPos, MountedFluidStorage&gt;
 *                      and MountedFluidStorage itself implements IFluidHandler.
 *   Create 0.5.x:      manager.fluidStorage          -> Map&lt;BlockPos, MountedFluidStorage&gt;
 *                      with a getFluidHandler() accessor.
 *
 * Both are handled, plus a pooled fallback when neither layout can be read.
 */
public final class ContraptionCompat {

    private static final String[] ENTITY_CLASSES = {
            "com.simibubi.create.content.contraptions.AbstractContraptionEntity",
            "com.simibubi.create.content.contraptions.components.structureMovement.AbstractContraptionEntity"
    };

    public static Class<?> entityClass() {
        for (String name : ENTITY_CLASSES) {
            Class<?> c = Reflect.clazz(name);
            if (c != Void.class) return c;
        }
        return Void.class;
    }

    public static boolean available() {
        return entityClass() != Void.class;
    }

    /** Every contraption entity that could be touched by a blast of this size. */
    public static List<Entity> nearbyContraptions(Level level, Vec3 center, double radius) {
        Class<?> type = entityClass();
        if (type == Void.class) return List.of();

        // Contraptions can be huge and their entity position is the anchor, not the hull,
        // so the query box is generous and the real test is done against the bounding box.
        AABB query = new AABB(center, center).inflate(radius + 128.0D);
        return level.getEntities((Entity) null, query, e ->
                type.isInstance(e) && distanceTo(e.getBoundingBox(), center) <= radius);
    }

    public static void collect(Level level, Vec3 center, double radius, Consumer<FuelTarget> out) {
        double radiusSq = radius * radius;

        for (Entity entity : nearbyContraptions(level, center, radius)) {
            boolean found = false;

            Map<BlockPos, ?> storages = fluidStorages(entity);
            if (storages != null) {
                for (Map.Entry<BlockPos, ?> entry : storages.entrySet()) {
                    if (handlerOf(entry.getValue()) == null) continue;
                    Vec3 world = toWorld(entity, entry.getKey());
                    if (world.distanceToSqr(center) > radiusSq) continue;
                    out.accept(new ContraptionFuelTarget(entity, entry.getKey()));
                    found = true;
                }
            }

            // Fallback: the contraption has fuel but the per-tank layout is unreadable.
            if (!found && combinedHandler(entity) != null) {
                Vec3 hit = closestPoint(entity.getBoundingBox(), center);
                out.accept(new ContraptionBulkFuelTarget(entity, hit));
            }

            if (FuelBlastConfig.debugLogging.get()) {
                FuelBlast.LOGGER.info("[fuelblast] contraption {} #{}: {} mounted fluid storages, matched={}",
                        entity.getClass().getSimpleName(), entity.getId(),
                        storages == null ? -1 : storages.size(), found);
            }
        }
    }

    /** Map of local position -> mounted fluid storage, or null when unreadable. */
    @SuppressWarnings("unchecked")
    public static Map<BlockPos, ?> fluidStorages(Entity contraptionEntity) {
        Object manager = storageManager(contraptionEntity);
        if (manager == null) return null;

        // Create 6: getFluids() returns MountedFluidStorageWrapper with a public 'storages' map.
        Object wrapper = Reflect.invoke(Reflect.methodByName(manager.getClass(), "getFluids", 0), manager);
        if (wrapper != null) {
            Object map = Reflect.field(wrapper, "storages");
            if (map instanceof Map<?, ?> m && keysAreBlockPos(m)) return (Map<BlockPos, ?>) m;
        }

        // Create 0.5.x: the manager holds the map directly.
        Object legacy = Reflect.field(manager, "fluidStorage", "fluidStorages", "mountedFluidStorage");
        if (legacy instanceof Map<?, ?> m && keysAreBlockPos(m)) return (Map<BlockPos, ?>) m;

        // Last resort: any BlockPos-keyed map whose values expose a fluid handler.
        Object guess = Reflect.mapFieldMatching(manager, value -> handlerOf(value) != null);
        if (guess instanceof Map<?, ?> m && keysAreBlockPos(m)) return (Map<BlockPos, ?>) m;

        return null;
    }

    /** Live handler of one mounted tank, or null. */
    public static IFluidHandler storageAt(Entity contraptionEntity, BlockPos local) {
        Map<BlockPos, ?> storages = fluidStorages(contraptionEntity);
        if (storages == null) return null;
        return handlerOf(storages.get(local));
    }

    /** One handler covering every mounted tank of the contraption. */
    public static IFluidHandler combinedHandler(Entity contraptionEntity) {
        Object manager = storageManager(contraptionEntity);
        if (manager == null) return null;
        Object wrapper = Reflect.invoke(Reflect.methodByName(manager.getClass(), "getFluids", 0), manager);
        return handlerOf(wrapper);
    }

    /** Structure-space position -> world position, following the hull's rotation and motion. */
    public static Vec3 toWorld(Entity contraptionEntity, BlockPos local) {
        Vec3 localCenter = Vec3.atCenterOf(local);
        Method m = Reflect.method(contraptionEntity.getClass(), "toGlobalVector", Vec3.class, float.class);
        if (m == null) m = Reflect.methodByName(contraptionEntity.getClass(), "toGlobalVector", 2);
        Object global = Reflect.invoke(m, contraptionEntity, localCenter, 1.0F);
        if (global instanceof Vec3 v) return v;
        return contraptionEntity.position().add(localCenter);
    }

    public static Object storageManager(Entity contraptionEntity) {
        Object contraption = Reflect.invoke(
                Reflect.methodByName(contraptionEntity.getClass(), "getContraption", 0), contraptionEntity);
        if (contraption == null) return null;

        Object manager = Reflect.invoke(Reflect.methodByName(contraption.getClass(), "getStorage", 0), contraption);
        if (manager != null) return manager;
        return Reflect.field(contraption, "storage", "storageProxy");
    }

    private static IFluidHandler handlerOf(Object mounted) {
        if (mounted == null) return null;
        if (mounted instanceof IFluidHandler h) return h;            // Create 6
        for (String name : new String[]{"getFluidHandler", "getTank", "getFluidTank", "getFluids"}) {
            Object handler = Reflect.invoke(Reflect.methodByName(mounted.getClass(), name, 0), mounted);
            if (handler instanceof IFluidHandler h) return h;        // Create 0.5.x
        }
        Object field = Reflect.fieldOfType(mounted, IFluidHandler.class);
        return field instanceof IFluidHandler h ? h : null;
    }

    private static boolean keysAreBlockPos(Map<?, ?> map) {
        if (map.isEmpty()) return false;
        return map.keySet().iterator().next() instanceof BlockPos;
    }

    public static double distanceTo(AABB box, Vec3 point) {
        return closestPoint(box, point).distanceTo(point);
    }

    public static Vec3 closestPoint(AABB box, Vec3 point) {
        return new Vec3(
                Math.max(box.minX, Math.min(point.x, box.maxX)),
                Math.max(box.minY, Math.min(point.y, box.maxY)),
                Math.max(box.minZ, Math.min(point.z, box.maxZ)));
    }

    private ContraptionCompat() {}
}
