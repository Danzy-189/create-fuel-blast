package com.danzy.fuelblast;

import com.danzy.fuelblast.compat.AeronauticsCompat;
import com.danzy.fuelblast.compat.ContraptionCompat;
import com.danzy.fuelblast.compat.ValkyrienCompat;
import com.danzy.fuelblast.compat.SableCompat;
import com.danzy.fuelblast.target.BlockFuelTarget;
import com.danzy.fuelblast.target.FuelTarget;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * One place that answers "which fuel containers are within X blocks of this point",
 * across every kind of structure the addon supports. Used by the explosion handler and by
 * the /fuelblast diagnostics command, so what you can debug is exactly what detonates.
 */
public final class FuelScan {

    public static List<FuelTarget> collect(Level level, Vec3 center, double radius) {
        List<FuelTarget> found = new ArrayList<>();
        Set<String> seenKeys = new HashSet<>();
        Set<IFluidHandler> seenHandlers = new HashSet<>();

        Consumer<FuelTarget> add = target -> {
            if (!seenKeys.add(target.key())) return;
            IFluidHandler handler = target.handler();
            if (handler == null) return;
            if (!seenHandlers.add(handler)) return;                 // shared multiblock handler
            if (fuelAmount(handler) < FuelBlastConfig.minFuelMb.get()) return;
            found.add(target);
        };

        // 1. blocks in this level, plus the blast projected into nearby Valkyrien Skies ships
        for (Vec3 origin : ValkyrienCompat.origins(level, center, radius)) {
            scanBlocks(level, origin, radius, pos -> add.accept(new BlockFuelTarget(level, pos)));
        }

        // 2. tanks mounted on assembled contraptions (Create vehicles, Aeronautics aircraft)
        if (FuelBlastConfig.contraptionTanks.get() && ContraptionCompat.available()) {
            ContraptionCompat.collect(level, center, radius, add);
        }

        // 3. Sable physical sub-levels used by Aeronautics/Simulated.
        if (FuelBlastConfig.aeronauticsInteriors.get()) {
            SableCompat.collect(level, center, radius, add);
        }

        // 4. tanks living inside an airship interior level
        if (FuelBlastConfig.aeronauticsInteriors.get()) {
            AeronauticsCompat.collect(level, center, radius, add);
        }

        if (FuelBlastConfig.debugLogging.get()) {
            FuelBlast.LOGGER.info("[fuelblast] scan r={} at {} in {} -> {} fuelled target(s)",
                    String.format("%.1f", radius), blockPosOf(center), level.dimension().location(), found.size());
            for (FuelTarget t : found) FuelBlast.LOGGER.info("[fuelblast]   - {}", t.describe());
        }
        return found;
    }

    /** Chunk-based block entity scan, with a brute-force fallback for wrapped/fake levels. */
    public static void scanBlocks(Level level, Vec3 center, double radius, Consumer<BlockPos> out) {
        double radiusSq = radius * radius;
        BlockPos min = BlockPos.containing(center.x - radius, center.y - radius, center.z - radius);
        BlockPos max = BlockPos.containing(center.x + radius, center.y + radius, center.z + radius);

        boolean sawChunk = false;
        for (int cx = SectionPos.blockToSectionCoord(min.getX()); cx <= SectionPos.blockToSectionCoord(max.getX()); cx++) {
            for (int cz = SectionPos.blockToSectionCoord(min.getZ()); cz <= SectionPos.blockToSectionCoord(max.getZ()); cz++) {
                ChunkAccess chunk;
                try {
                    chunk = level.getChunk(cx, cz, ChunkStatus.FULL, false);
                } catch (Throwable t) {
                    chunk = null;
                }
                if (!(chunk instanceof LevelChunk levelChunk)) continue;
                sawChunk = true;

                for (BlockEntity be : levelChunk.getBlockEntities().values()) {
                    if (be.isRemoved()) continue;
                    BlockPos pos = be.getBlockPos();
                    if (pos.getY() < min.getY() || pos.getY() > max.getY()) continue;
                    if (center.distanceToSqr(Vec3.atCenterOf(pos)) > radiusSq) continue;
                    if (Capabilities.FluidHandler.BLOCK.getCapability(level, pos, null, be, null) != null) out.accept(pos);
                }
            }
        }

        // Airship interiors are sometimes wrapped levels that do not expose chunks the usual
        // way. Then every position in range is probed directly - bounded, and only as a fallback.
        if (!sawChunk && FuelBlastConfig.deepScanFallback.get() && radius <= 20.0D) {
            for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
                if (center.distanceToSqr(Vec3.atCenterOf(pos)) > radiusSq) continue;
                BlockEntity be = level.getBlockEntity(pos);
                if (be == null || be.isRemoved()) continue;
                if (Capabilities.FluidHandler.BLOCK.getCapability(level, pos, null, be, null) != null) {
                    out.accept(pos.immutable());
                }
            }
        }
    }

    /** Total flammable fluid (mB, weighted by fuel energy) inside a handler. */
    public static int fuelAmount(IFluidHandler handler) {
        double total = 0.0D;
        for (int i = 0; i < handler.getTanks(); i++) {
            FluidStack stack = handler.getFluidInTank(i);
            double energy = FuelRegistry.energyOf(stack);
            if (energy > 0) total += stack.getAmount() * energy;
        }
        return (int) Math.min(Integer.MAX_VALUE, total);
    }

    private static String blockPosOf(Vec3 v) {
        return BlockPos.containing(v).toShortString();
    }

    private FuelScan() {}
}
