package com.danzy.fuelblast;

import com.danzy.fuelblast.network.BlastEffectPacket;
import com.danzy.fuelblast.target.FuelTarget;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Turns the fuel stored in a target into a properly sized explosion. */
public final class FuelExplosion {

    private static final ThreadLocal<Integer> CHAIN_DEPTH = ThreadLocal.withInitial(() -> 0);
    /** Keys already consumed by a detonation; prevents the synchronous explosion event
     * from scheduling the same multiblock a second time before its blocks are removed. */
    private static final Set<String> CONSUMED = ConcurrentHashMap.newKeySet();

    public static int currentChainDepth() {
        return CHAIN_DEPTH.get();
    }

    public static boolean isConsumed(String key) {
        return CONSUMED.contains(key);
    }

    public static void clearConsumed() {
        CONSUMED.clear();
    }

    public static void detonate(Level originLevel, FuelTarget target, int depth) {
        IFluidHandler handler = target.handler();
        if (handler == null) return;

        int fuel = FuelScan.fuelAmount(handler);
        if (fuel < FuelBlastConfig.minFuelMb.get()) return;
        if (!CONSUMED.add(target.key())) return;

        ResourceLocation fluidId = dominantFluid(handler);
        drainFuel(handler);

        float power = (float) powerFor(fuel);
        Vec3 center = target.position();
        Level blastLevel = target.level() != null ? target.level() : originLevel;

        // Remove the complete physical tank group before creating the nested explosion.
        // ExplosionEvent.Detonate is synchronous; delaying removal until after explode()
        // lets the event rediscover every remaining segment and queue duplicate blasts.
        if (FuelBlastConfig.breakBlocks.get()) {
            target.removeAfterDetonation();
        }

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

        FireScatter.scatter(blastLevel, center, power, fuel);

        BlastEffectPacket packet = new BlastEffectPacket(center, power, fuel, fluidId);
        sendEffect(blastLevel, center, packet);
        // Tanks inside an airship interior are watched from the outside level too.
        if (blastLevel != originLevel) sendEffect(originLevel, center, packet);

        if (FuelBlastConfig.debugLogging.get()) {
            FuelBlast.LOGGER.info("[fuelblast] detonated {} with {} mB -> power {}",
                    target.describe(), fuel, String.format("%.2f", power));
        }
    }

    private static void sendEffect(Level level, Vec3 center, BlastEffectPacket packet) {
        if (level instanceof ServerLevel serverLevel) {
            PacketDistributor.sendToPlayersNear(serverLevel, null, center.x, center.y, center.z, 192.0D, packet);
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
