package com.danzy.fuelblast;

import com.danzy.fuelblast.network.BlastEffectPacket;
import com.danzy.fuelblast.network.FuelBlastNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.registries.ForgeRegistries;

/** Turns the fuel stored in a tank into a properly sized explosion. */
public final class FuelExplosion {

    private static final ThreadLocal<Integer> CHAIN_DEPTH = ThreadLocal.withInitial(() -> 0);

    public static int currentChainDepth() {
        return CHAIN_DEPTH.get();
    }

    public static void detonate(ServerLevel level, BlockPos pos, int depth) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be == null || be.isRemoved()) return;

        IFluidHandler handler = be.getCapability(ForgeCapabilities.FLUID_HANDLER).orElse(null);
        if (handler == null) return;

        int fuel = ExplosionHandler.fuelAmount(handler);
        if (fuel < FuelBlastConfig.minFuelMb.get()) return;

        ResourceLocation fluidId = dominantFluid(handler);

        // The fuel is consumed by the blast.
        drainFuel(handler);

        float power = (float) powerFor(fuel);
        Vec3 center = Vec3.atCenterOf(pos);

        CHAIN_DEPTH.set(depth);
        try {
            level.explode(null,
                    level.damageSources().explosion(null, null),
                    null,
                    center.x, center.y, center.z,
                    power,
                    FuelBlastConfig.causeFire.get(),
                    FuelBlastConfig.breakBlocks.get()
                            ? Level.ExplosionInteraction.TNT
                            : Level.ExplosionInteraction.NONE);
        } finally {
            CHAIN_DEPTH.set(0);
        }

        FuelBlastNetwork.CHANNEL.send(
                PacketDistributor.NEAR.with(() -> new PacketDistributor.TargetPoint(
                        center.x, center.y, center.z, 192.0D, level.dimension())),
                new BlastEffectPacket(center, power, fuel, fluidId));
    }

    /** Sub-linear curve: more fuel always means a bigger boom, but never an infinite one. */
    public static double powerFor(int fuelMb) {
        double buckets = fuelMb / 1000.0D;
        double power = FuelBlastConfig.basePower.get()
                + FuelBlastConfig.powerCoefficient.get() * Math.pow(buckets, FuelBlastConfig.powerExponent.get());
        return Math.min(power, FuelBlastConfig.maxPower.get());
    }

    private static void drainFuel(IFluidHandler handler) {
        for (int i = 0; i < handler.getTanks(); i++) {
            FluidStack stack = handler.getFluidInTank(i);
            if (stack.isEmpty() || !FuelRegistry.isFuel(stack)) continue;
            handler.drain(new FluidStack(stack.getFluid(), stack.getAmount()), IFluidHandler.FluidAction.EXECUTE);
        }
    }

    private static ResourceLocation dominantFluid(IFluidHandler handler) {
        FluidStack best = FluidStack.EMPTY;
        for (int i = 0; i < handler.getTanks(); i++) {
            FluidStack stack = handler.getFluidInTank(i);
            if (!FuelRegistry.isFuel(stack)) continue;
            if (best.isEmpty() || stack.getAmount() > best.getAmount()) best = stack;
        }
        return best.isEmpty() ? new ResourceLocation("minecraft", "empty")
                : ForgeRegistries.FLUIDS.getKey(best.getFluid());
    }

    private FuelExplosion() {}
}
