package com.danzy.fuelblast;

import com.danzy.fuelblast.target.FuelTarget;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.bus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * Tanks do not pop instantly: they hiss for a couple of ticks, then go off.
 * That fuse is what turns a row of tanks into a cascade instead of one flat frame of fire.
 */
public final class BlastScheduler {

    private record Primed(Level level, FuelTarget target, int depth, int[] fuse) {}

    private static final List<Primed> PENDING = new ArrayList<>();
    private static final Set<String> PRIMED_KEYS = new HashSet<>();

    public static boolean isPrimed(String key) {
        return PRIMED_KEYS.contains(key);
    }

    public static void prime(Level level, FuelTarget target, int depth) {
        int min = FuelBlastConfig.minFuseTicks.get();
        int max = Math.max(min, FuelBlastConfig.maxFuseTicks.get());
        int fuse = min + level.getRandom().nextInt(max - min + 1);

        PENDING.add(new Primed(level, target, depth, new int[]{fuse}));
        PRIMED_KEYS.add(target.key());

        Vec3 p = target.position();
        Level soundLevel = target.level() != null ? target.level() : level;
        soundLevel.playSound(null, BlockPos.containing(p), SoundEvents.LAVA_EXTINGUISH, SoundSource.BLOCKS,
                1.6F, 0.6F + level.getRandom().nextFloat() * 0.2F);
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (PENDING.isEmpty()) return;

        List<Primed> ready = new ArrayList<>();
        Iterator<Primed> it = PENDING.iterator();
        while (it.hasNext()) {
            Primed p = it.next();
            if (!p.target().isValid()) {
                PRIMED_KEYS.remove(p.target().key());
                it.remove();
                continue;
            }
            if (--p.fuse()[0] <= 0) {
                ready.add(p);
                it.remove();
            }
        }
        for (Primed p : ready) {
            PRIMED_KEYS.remove(p.target().key());
            FuelExplosion.detonate(p.level(), p.target(), p.depth());
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        PENDING.clear();
        PRIMED_KEYS.clear();
    }

    private BlastScheduler() {}
}
