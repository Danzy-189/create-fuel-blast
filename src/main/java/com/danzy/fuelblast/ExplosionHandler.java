package com.danzy.fuelblast;

import com.danzy.fuelblast.compat.AeronauticsCompat;
import com.danzy.fuelblast.compat.ContraptionCompat;
import com.danzy.fuelblast.compat.ValkyrienCompat;
import com.danzy.fuelblast.target.BlockFuelTarget;
import com.danzy.fuelblast.target.FuelTarget;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Listens to *any* explosion - TNT, creepers, addon bombs, modded weapons or another
 * fuel blast - and primes every nearby fuel container, wherever it lives:
 *
 *  - blocks in the world (Create fluid tanks, Create: Connected vessels, any modded tank)
 *  - tanks mounted on assembled Create / Create Aeronautics contraptions
 *  - tanks inside an Aeronautics airship interior
 *  - tanks on Valkyrien Skies physics ships
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

        Vec3 center = ExplosionAccess.centerOf(event.getExplosion());
        double power = ExplosionAccess.radiusOf(event.getExplosion());
        double radius = FuelBlastConfig.scanRadius.get() + FuelBlastConfig.radiusPerPower.get() * power;

        Set<String> seenKeys = new HashSet<>();
        Set<IFluidHandler> seenHandlers = new HashSet<>();

        Consumer<FuelTarget> prime = target -> {
            if (!seenKeys.add(target.key())) return;
            if (BlastScheduler.isPrimed(target.key())) return;

            IFluidHandler handler = target.handler();
            if (handler == null) return;
            if (!seenHandlers.add(handler)) return;            // shared multiblock handler
            if (fuelAmount(handler) < FuelBlastConfig.minFuelMb.get()) return;

            BlastScheduler.prime(level, target, depth + 1);
        };

        // 1. plain world blocks, plus the same blast projected into nearby VS shipyards
        for (Vec3 origin : ValkyrienCompat.origins(level, center, radius)) {
            scanBlocks(level, origin, radius, pos -> prime.accept(new BlockFuelTarget(level, pos)));
        }

        // 2. tanks riding assembled contraptions (Create vehicles, Aeronautics aircraft)
        if (FuelBlastConfig.contraptionTanks.get()) {
            ContraptionCompat.collect(level, center, radius, prime);
        }

        // 3. tanks inside Aeronautics airship interiors
        if (FuelBlastConfig.aeronauticsInteriors.get()) {
            AeronauticsCompat.collect(level, center, radius, ExplosionHandler::scanBlocks, prime);
        }
    }

    /** Chunk-based block entity scan: cheap even with a large radius. */
    public static void scanBlocks(ServerLevel level, Vec3 center, double radius, Consumer<BlockPos> out) {
        double radiusSq = radius * radius;
        BlockPos min = BlockPos.containing(center.x - radius, center.y - radius, center.z - radius);
        BlockPos max = BlockPos.containing(center.x + radius, center.y + radius, center.z + radius);

        for (int cx = SectionPos.blockToSectionCoord(min.getX()); cx <= SectionPos.blockToSectionCoord(max.getX()); cx++) {
            for (int cz = SectionPos.blockToSectionCoord(min.getZ()); cz <= SectionPos.blockToSectionCoord(max.getZ()); cz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) continue;

                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    if (be.isRemoved()) continue;
                    BlockPos pos = be.getBlockPos();
                    if (pos.getY() < min.getY() || pos.getY() > max.getY()) continue;
                    if (center.distanceToSqr(Vec3.atCenterOf(pos)) > radiusSq) continue;
                    if (Capabilities.FluidHandler.BLOCK.getCapability(level, pos, null, be, null) != null) out.accept(pos);
                }
            }
        }
    }

    /** Total amount of flammable fluid (mB, weighted by fuel energy) in a handler. */
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
