package com.danzy.fuelblast;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * Tanks do not pop instantly: they hiss for a couple of ticks, then go off.
 * That fuse is what makes chain reactions look like a cascade instead of one frame of fire.
 */
public final class BlastScheduler {

    private record Primed(ServerLevel level, BlockPos pos, int depth, int[] fuse) {}

    private static final List<Primed> PENDING = new ArrayList<>();
    private static final Set<String> PRIMED_KEYS = new HashSet<>();

    public static boolean isPrimed(ServerLevel level, BlockPos pos) {
        return PRIMED_KEYS.contains(key(level, pos));
    }

    public static void prime(ServerLevel level, BlockPos pos, int depth) {
        int min = FuelBlastConfig.minFuseTicks.get();
        int max = Math.max(min, FuelBlastConfig.maxFuseTicks.get());
        int fuse = min + level.random.nextInt(max - min + 1);

        PENDING.add(new Primed(level, pos.immutable(), depth, new int[]{fuse}));
        PRIMED_KEYS.add(key(level, pos));

        level.playSound(null, pos, SoundEvents.LAVA_EXTINGUISH, SoundSource.BLOCKS,
                1.6F, 0.6F + level.random.nextFloat() * 0.2F);
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || PENDING.isEmpty()) return;

        Iterator<Primed> it = PENDING.iterator();
        List<Primed> ready = new ArrayList<>();
        while (it.hasNext()) {
            Primed p = it.next();
            if (--p.fuse()[0] <= 0) {
                ready.add(p);
                it.remove();
            }
        }
        for (Primed p : ready) {
            PRIMED_KEYS.remove(key(p.level(), p.pos()));
            FuelExplosion.detonate(p.level(), p.pos(), p.depth());
        }
    }

    @SubscribeEvent
    public static void onServerStopping(net.minecraftforge.event.server.ServerStoppingEvent event) {
        PENDING.clear();
        PRIMED_KEYS.clear();
    }

    private static String key(ServerLevel level, BlockPos pos) {
        return level.dimension().location() + "@" + pos.asLong();
    }

    private BlastScheduler() {}
}
