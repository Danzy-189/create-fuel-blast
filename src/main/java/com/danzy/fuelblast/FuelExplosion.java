package com.danzy.fuelblast;

import com.danzy.fuelblast.network.BlastEffectPacket;
import com.danzy.fuelblast.target.FuelTarget;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.neoforge.network.PacketDistributor;

/** Turns the fuel stored in a target into a properly sized explosion. */
public final class FuelExplosion {

    private static final ThreadLocal<Integer> CHAIN_DEPTH = ThreadLocal.withInitial(() -> 0);

    public static int currentChainDepth() {
        return CHAIN_DEPTH.get();
    }

    public static void detonate(ServerLevel level, FuelTarget target, int depth) {
        IFluidHandler handler = target.handler();
        if (handler == null) return;

        int fuel = ExplosionHandler.fuelAmount(handler);
        if (fuel < FuelBlastConfig.minFuelMb.get()) return;

        ResourceLocation fluidId = dominantFluid(handler);
        drainFuel(handler);

        float power = (float) powerFor(fuel);
        Vec3 center = target.position();

        // A blast inside an airship interior must be felt in that level, not the outside one.
        ServerLevel blastLevel = target instanceof com.danzy.fuelblast.target.BlockFuelTarget b ? b.level() : level;

        CHAIN_DEPTH.set(depth);
        try {
            blastLevel.explode(null,
                    blastLevel.damageSources().explosion(null, null),
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

        BlastEffectPacket packet = new BlastEffectPacket(center, power, fuel, fluidId);
        PacketDistributor.sendToPlayersNear(blastLevel, null, center.x, center.y, center.z, 192.0D, packet);

        // Contraption tanks live in structure space; players see the airship, not the interior,
        // so the visuals are also sent to everyone watching the parent level.
        if (blastLevel != level) {
            PacketDistributor.sendToPlayersNear(level, null, center.x, center.y, center.z, 192.0D, packet);
        }
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
        return best.isEmpty() ? ResourceLocation.withDefaultNamespace("empty")
                : BuiltInRegistries.FLUID.getKey(best.getFluid());
    }

    private FuelExplosion() {}
}
