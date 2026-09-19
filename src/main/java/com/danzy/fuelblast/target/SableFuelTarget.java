package com.danzy.fuelblast.target;

import com.danzy.fuelblast.compat.Reflect;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

/** A tank stored in a Sable physical sub-level used by Aeronautics/Simulated. */
public final class SableFuelTarget implements FuelTarget {
    private final Object subLevel;
    private final Object embeddedLevel;
    private final Level parentLevel;
    private final BlockPos localPos;
    private final BlockEntity discoveredBlockEntity;

    public SableFuelTarget(Object subLevel, Object embeddedLevel, Level parentLevel, BlockPos localPos, BlockEntity discoveredBlockEntity) {
        this.subLevel = subLevel;
        this.embeddedLevel = embeddedLevel;
        this.parentLevel = parentLevel;
        this.localPos = localPos.immutable();
        this.discoveredBlockEntity = discoveredBlockEntity;
    }

    @Override
    public Vec3 position() {
        Object pose = Reflect.invoke(Reflect.methodByName(subLevel.getClass(), "logicalPose", 0), subLevel);
        if (pose != null) {
            // Pose3dc has several transformPosition overloads. Select the Vec3 overload
            // explicitly; choosing a Vector3d overload by arity silently returns null.
            Object transformed = Reflect.invoke(Reflect.publicMethod(pose.getClass(), "transformPosition", Vec3.class),
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
        BlockEntity blockEntity = be instanceof BlockEntity found ? found : discoveredBlockEntity;
        if (blockEntity == null || blockEntity.isRemoved()) return null;
        if (blockEntity instanceof IFluidHandler direct) return direct;
        IFluidHandler capability = Capabilities.FluidHandler.BLOCK.getCapability(parentLevel, localPos,
                blockEntity.getBlockState(), blockEntity, null);
        if (capability != null) return capability;
        for (String methodName : new String[]{"getFluidHandler", "getFluidTank", "getTank"}) {
            Object value = Reflect.invoke(Reflect.publicMethodByName(blockEntity.getClass(), methodName, 0), blockEntity);
            if (value instanceof IFluidHandler handler) return handler;
        }
        Object field = Reflect.fieldOfType(blockEntity, IFluidHandler.class);
        return field instanceof IFluidHandler handler ? handler : null;
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

    public void destroyBlock() {
        java.lang.reflect.Method method = Reflect.method(embeddedLevel.getClass(), "destroyBlock",
                BlockPos.class, boolean.class, Entity.class, int.class);
        Reflect.invoke(method, embeddedLevel, localPos, true, null, 0);
    }
}
