package com.danzy.fuelblast.compat;

import com.danzy.fuelblast.FuelBlast;
import com.danzy.fuelblast.FuelBlastConfig;
import com.danzy.fuelblast.target.FuelTarget;
import com.danzy.fuelblast.target.SableFuelTarget;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.capabilities.ForgeCapabilities;

import java.util.Map;
import java.util.function.Consumer;

/** Soft integration with Sable, the sub-level physics engine used by Aeronautics/Simulated. */
public final class SableCompat {
    private static final String CONTAINER = "dev.ryanhcode.sable.api.sublevel.SubLevelContainer";

    public static boolean available() {
        return Reflect.present(CONTAINER);
    }

    /**
     * Finds fluid handlers in every loaded physical sub-level whose global bounds touch the
     * explosion. This is deliberately event-driven: no world-wide per-tick scan is needed.
     */
    public static void collect(Level level, Vec3 center, double radius, Consumer<FuelTarget> out) {
        if (!available()) return;

        Class<?> containerClass = Reflect.clazz(CONTAINER);
        Object container = Reflect.invoke(Reflect.method(containerClass, "getContainer", Level.class), null, level);
        if (container == null) return;

        Object all = Reflect.invoke(Reflect.methodByName(container.getClass(), "getAllSubLevels", 0), container);
        if (!(all instanceof Iterable<?> subLevels)) return;

        double radiusSq = radius * radius;
        for (Object subLevel : subLevels) {
            if (subLevel == null || removed(subLevel)) continue;

            AABB globalBounds = boundsOf(subLevel);
            if (globalBounds != null && distanceTo(globalBounds, center) > radius) continue;

            Object plot = Reflect.invoke(Reflect.methodByName(subLevel.getClass(), "getPlot", 0), subLevel);
            Object embedded = plot == null ? null
                    : Reflect.invoke(Reflect.methodByName(plot.getClass(), "getEmbeddedLevelAccessor", 0), plot);
            if (embedded == null) continue;

            Object loaded = Reflect.invoke(Reflect.methodByName(plot.getClass(), "getLoadedChunks", 0), plot);
            if (!(loaded instanceof Iterable<?> chunks)) continue;

            int matched = 0;
            for (Object holder : chunks) {
                Object chunk = Reflect.invoke(Reflect.methodByName(holder.getClass(), "getChunk", 0), holder);
                if (!(chunk instanceof LevelChunk levelChunk)) continue;

                for (BlockEntity blockEntity : levelChunk.getBlockEntities().values()) {
                    if (blockEntity.isRemoved()
                            || !blockEntity.getCapability(ForgeCapabilities.FLUID_HANDLER).isPresent()) continue;

                    BlockPos localPos = blockEntity.getBlockPos();
                    SableFuelTarget target = new SableFuelTarget(subLevel, embedded, level, localPos);
                    Vec3 worldPos = target.position();
                    if (worldPos.distanceToSqr(center) > radiusSq) continue;
                    out.accept(target);
                    matched++;
                }
            }

            if (FuelBlastConfig.debugLogging.get() && matched > 0) {
                FuelBlast.LOGGER.info("[fuelblast] Sable sub-level {} matched {} fluid block(s)",
                        subLevelName(subLevel), matched);
            }
        }
    }

    private static boolean removed(Object subLevel) {
        return Boolean.TRUE.equals(Reflect.invoke(Reflect.methodByName(subLevel.getClass(), "isRemoved", 0), subLevel));
    }

    private static String subLevelName(Object subLevel) {
        Object name = Reflect.invoke(Reflect.methodByName(subLevel.getClass(), "getName", 0), subLevel);
        return name == null ? String.valueOf(subLevel) : String.valueOf(name);
    }

    private static AABB boundsOf(Object subLevel) {
        Object bounds = Reflect.invoke(Reflect.methodByName(subLevel.getClass(), "boundingBox", 0), subLevel);
        if (bounds == null) return null;
        Object minX = Reflect.invoke(Reflect.publicMethodByName(bounds.getClass(), "minX", 0), bounds);
        Object minY = Reflect.invoke(Reflect.publicMethodByName(bounds.getClass(), "minY", 0), bounds);
        Object minZ = Reflect.invoke(Reflect.publicMethodByName(bounds.getClass(), "minZ", 0), bounds);
        Object maxX = Reflect.invoke(Reflect.publicMethodByName(bounds.getClass(), "maxX", 0), bounds);
        Object maxY = Reflect.invoke(Reflect.publicMethodByName(bounds.getClass(), "maxY", 0), bounds);
        Object maxZ = Reflect.invoke(Reflect.publicMethodByName(bounds.getClass(), "maxZ", 0), bounds);
        if (!(minX instanceof Number a) || !(minY instanceof Number b) || !(minZ instanceof Number c)
                || !(maxX instanceof Number d) || !(maxY instanceof Number e) || !(maxZ instanceof Number f)) return null;
        return new AABB(a.doubleValue(), b.doubleValue(), c.doubleValue(),
                d.doubleValue(), e.doubleValue(), f.doubleValue());
    }

    private static double distanceTo(AABB box, Vec3 point) {
        double x = Math.max(box.minX, Math.min(point.x, box.maxX));
        double y = Math.max(box.minY, Math.min(point.y, box.maxY));
        double z = Math.max(box.minZ, Math.min(point.z, box.maxZ));
        return point.distanceTo(new Vec3(x, y, z));
    }

    private SableCompat() {}
}
