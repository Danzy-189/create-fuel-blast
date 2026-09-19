package com.danzy.fuelblast.target;

import com.danzy.fuelblast.compat.Reflect;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.capability.IFluidHandler;

/**
 * A tank stored in a Sable physical sub-level. Sable keeps the block entity in an
 * embedded plot while its pose moves in the parent world, so neither a normal
 * LevelChunk scan nor Create's MountedStorageManager can see it.
 */
public final class SableFuelTarget implements FuelTarget {
    private final Object subLevel;
    private final Object embeddedLevel;
    private final Level parentLevel;
    private final BlockPos localPos;

    public SableFuelTarget(Object subLevel, Object embeddedLevel, Level parentLevel, BlockPos localPos) {
        this.subLevel = subLevel;
        this.embeddedLevel = embeddedLevel;
        this.parentLevel = parentLevel;
        this.localPos = localPos.immutable();
    }

    @Override
    public Vec3 position() {
        Object pose = Reflect.invoke(Reflect.methodByName(subLevel.getClass(), "logicalPose", 0), subLevel);
        if (pose != null) {
            Object transformed = Reflect.invoke(Reflect.publicMethodByName(pose.getClass(), "transformPosition", 1),
                    pose, Vec3.atCenterOf(localPos));
            if (transformed instanceof Vec3 v) return v;
        }
        return Vec3.atCenterOf(localPos);
    }

    @Override
    public Level level() {
        return parentLevel;
    }

    @Override
    public IFluidHandler handler() {
        Object be = Reflect.invoke(Reflect.methodByName(embeddedLevel.getClass(), "getBlockEntity", 1),
                embeddedLevel, localPos);
        if (!(be instanceof BlockEntity blockEntity) || blockEntity.isRemoved()) return null;
        return blockEntity.getCapability(ForgeCapabilities.FLUID_HANDLER).orElse(null);
    }

    @Override
    public String key() {
        Object id = Reflect.invoke(Reflect.methodByName(subLevel.getClass(), "getUniqueId", 0), subLevel);
        return "sable:" + (id == null ? System.identityHashCode(subLevel) : id) + "@" + localPos.asLong();
    }

    @Override
    public String describe() {
        return "Sable physical tank " + localPos.toShortString();
    }

    @Override
    public boolean isValid() {
        Object removed = Reflect.invoke(Reflect.methodByName(subLevel.getClass(), "isRemoved", 0), subLevel);
        return !Boolean.TRUE.equals(removed) && handler() != null;
    }

    /** Remove the tank block from the embedded plot after the parent-world blast. */
    public void destroyBlock() {
        Object method = Reflect.method(embeddedLevel.getClass(), "destroyBlock", BlockPos.class, boolean.class,
                Entity.class, int.class);
        Reflect.invoke((java.lang.reflect.Method) method, embeddedLevel, localPos, true, null, 0);
    }
}
