package com.danzy.fuelblast;

import com.danzy.fuelblast.target.FuelTarget;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.event.server.ServerStartedEvent;

import java.util.List;

/**
 * Listens to *any* explosion - TNT, creepers, addon bombs, modded weapons or another fuel
 * blast - and primes every fuel container near it, in the world, on a contraption, or
 * inside an airship.
 */
public class ExplosionHandler {

    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        FuelRegistry.clearCache();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onDetonate(ExplosionEvent.Detonate event) {
        Level level = event.getLevel();
        if (level == null || level.isClientSide() || !FuelBlastConfig.enableFuelExplosions.get()) return;

        int depth = FuelExplosion.currentChainDepth();
        if (depth > 0 && !FuelBlastConfig.enableChainReactions.get()) return;
        if (depth > FuelBlastConfig.maxChainDepth.get()) return;

        Vec3 center = event.getExplosion().getPosition();
        double power = ExplosionAccess.radiusOf(event.getExplosion());
        double radius = FuelBlastConfig.scanRadius.get() + FuelBlastConfig.radiusPerPower.get() * power;

        List<FuelTarget> targets = FuelScan.collect(level, center, radius);
        for (FuelTarget target : targets) {
            if (BlastScheduler.isPrimed(target.key())) continue;
            BlastScheduler.prime(level, target, depth + 1);
        }
    }
}
