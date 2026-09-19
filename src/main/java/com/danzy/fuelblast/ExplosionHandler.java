package com.danzy.fuelblast;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;

import java.util.HashSet;
import java.util.Set;

/**
 * Listens to *any* explosion - TNT, creepers, addon bombs, modded weapons, or another
 * fuel blast - and primes every nearby tank/vessel that stores flammable fluid.
 */
public class ExplosionHandler {

    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        FuelRegistry.clearCache();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onDetonate(ExplosionEvent.Detonate event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;

        int depth = FuelExplosion.currentChainDepth();
        if (depth > FuelBlastConfig.maxChainDepth.get()) return;

        Vec3 center = event.getExplosion().getPosition();
        double power = ExplosionAccess.radiusOf(event.getExplosion());
        double radius = FuelBlastConfig.scanRadius.get() + FuelBlastConfig.radiusPerPower.get() * power;
        double radiusSq = radius * radius;

        Set<IFluidHandler> seen = new HashSet<>();
        BlockPos min = BlockPos.containing(center.x - radius, center.y - radius, center.z - radius);
        BlockPos max = BlockPos.containing(center.x + radius, center.y + radius, center.z + radius);

        for (int cx = SectionPos.blockToSectionCoord(min.getX()); cx <= SectionPos.blockToSectionCoord(max.getX()); cx++) {
            for (int cz = SectionPos.blockToSectionCoord(min.getZ()); cz <= SectionPos.blockToSectionCoord(max.getZ()); cz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) continue;

                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    BlockPos pos = be.getBlockPos();
                    if (pos.getY() < min.getY() || pos.getY() > max.getY()) continue;
                    if (center.distanceToSqr(Vec3.atCenterOf(pos)) > radiusSq) continue;
                    if (be.isRemoved()) continue;

                    be.getCapability(ForgeCapabilities.FLUID_HANDLER).ifPresent(handler -> {
                        if (!seen.add(handler)) return;           // shared multiblock handler
                        int fuel = fuelAmount(handler);
                        if (fuel < FuelBlastConfig.minFuelMb.get()) return;
                        if (BlastScheduler.isPrimed(level, pos)) return;
                        BlastScheduler.prime(level, pos, depth + 1);
                    });
                }
            }
        }
    }

    /** Total amount of flammable fluid (in mB, energy weighted) held by a handler. */
    public static int fuelAmount(IFluidHandler handler) {
        double total = 0.0D;
        for (int i = 0; i < handler.getTanks(); i++) {
            FluidStack stack = handler.getFluidInTank(i);
            double energy = FuelRegistry.energyOf(stack);
            if (energy > 0) total += stack.getAmount() * energy;
        }
        return (int) Math.min(Integer.MAX_VALUE, total);
    }
}
