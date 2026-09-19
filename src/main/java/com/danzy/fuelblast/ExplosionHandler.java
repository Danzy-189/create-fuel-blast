package com.danzy.fuelblast;

import com.danzy.fuelblast.target.FuelTarget;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraft.world.entity.projectile.Projectile;
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
        handleExplosion(event.getLevel(), event.getExplosion().getPosition(),
                ExplosionAccess.radiusOf(event.getExplosion()));
    }

    /**
     * Some weapon mods expose a projectile impact but create their own blast instead of
     * calling Level.explode. This fallback covers the common rocket/bomb/grenade/shell
     * entities without depending on any weapon mod classes. Mods with a different naming
     * scheme can call FuelBlastApi.reportExplosion directly.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onProjectileImpact(ProjectileImpactEvent event) {
        Projectile projectile = event.getProjectile();
        if (projectile.level().isClientSide() || !looksExplosive(projectile)) return;
        handleExplosion(projectile.level(), projectile.position(), projectilePower(projectile));
    }

    public static void handleExplosion(Level level, Vec3 center, double power) {
        if (level == null || level.isClientSide() || !FuelBlastConfig.enableFuelExplosions.get()) return;

        int depth = FuelExplosion.currentChainDepth();
        if (depth > 0 && !FuelBlastConfig.enableChainReactions.get()) return;
        if (depth > FuelBlastConfig.maxChainDepth.get()) return;

        double safePower = Math.max(0.1D, Math.min(power, 100.0D));
        double radius = FuelBlastConfig.scanRadius.get() + FuelBlastConfig.radiusPerPower.get() * safePower;

        List<FuelTarget> targets = FuelScan.collect(level, center, radius);
        for (FuelTarget target : targets) {
            if (BlastScheduler.isPrimed(target.key())) continue;
            BlastScheduler.prime(level, target, depth + 1);
        }
    }

    private static boolean looksExplosive(Projectile projectile) {
        String name = projectile.getClass().getName().toLowerCase(java.util.Locale.ROOT);
        return name.contains("rocket") || name.contains("missile") || name.contains("bomb")
                || name.contains("grenade") || name.contains("shell") || name.contains("warhead")
                || name.contains("artillery") || name.contains("explosive") || name.contains("dynamite");
    }

    private static double projectilePower(Projectile projectile) {
        for (String name : new String[]{"getExplosionPower", "getExplosionRadius", "getBlastRadius", "getPower"}) {
            java.lang.reflect.Method method = com.danzy.fuelblast.compat.Reflect.publicMethodByName(
                    projectile.getClass(), name, 0);
            Object value = com.danzy.fuelblast.compat.Reflect.invoke(method, projectile);
            if (value instanceof Number n && n.doubleValue() > 0.0D) return n.doubleValue();
        }
        return 4.0D;
    }
}
