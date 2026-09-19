package com.danzy.fuelblast;

import com.danzy.fuelblast.compat.AeronauticsCompat;
import com.danzy.fuelblast.compat.ContraptionCompat;
import com.danzy.fuelblast.compat.ValkyrienCompat;
import com.danzy.fuelblast.compat.SableCompat;
import com.danzy.fuelblast.target.FuelTarget;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

import java.util.List;

/**
 * /fuelblast scan [radius]      - list every fuel container the addon can see from here
 * /fuelblast detonate [radius]  - prime them for real
 *
 * Physics airships are the hardest case to get right, so the detection is inspectable
 * in-game instead of being a black box.
 */
public final class FuelBlastCommands {

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("fuelblast")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("scan")
                        .executes(ctx -> run(ctx.getSource(), 12.0D, false))
                        .then(Commands.argument("radius", DoubleArgumentType.doubleArg(1, 128))
                                .executes(ctx -> run(ctx.getSource(),
                                        DoubleArgumentType.getDouble(ctx, "radius"), false))))
                .then(Commands.literal("detonate")
                        .executes(ctx -> run(ctx.getSource(), 12.0D, true))
                        .then(Commands.argument("radius", DoubleArgumentType.doubleArg(1, 128))
                                .executes(ctx -> run(ctx.getSource(),
                                        DoubleArgumentType.getDouble(ctx, "radius"), true)))));
    }

    private static int run(CommandSourceStack source, double radius, boolean detonate) {
        Level level = source.getLevel();
        Vec3 center = source.getPosition();

        List<Entity> contraptions = ContraptionCompat.available()
                ? ContraptionCompat.nearbyContraptions(level, center, radius) : List.of();
        List<FuelTarget> targets = FuelScan.collect(level, center, radius);

        source.sendSuccess(() -> Component.literal(
                "Fuel Blast: " + targets.size() + " fuelled container(s) within " + (int) radius + " blocks"
                        + " | contraptions nearby: " + contraptions.size()
                        + " | Create: " + (ContraptionCompat.available() ? "yes" : "no")
                        + " | Valkyrien Skies: " + (ValkyrienCompat.available() ? "yes" : "no")
                        + " | Sable: " + (SableCompat.available() ? "yes" : "no")), false);

        for (Entity entity : contraptions) {
            Level interior = AeronauticsCompat.interiorOf(entity);
            source.sendSuccess(() -> Component.literal("  contraption " + entity.getClass().getSimpleName()
                    + " #" + entity.getId()
                    + " | mounted fluid storages: " + sizeOf(entity)
                    + " | interior level: " + (interior == null ? "none" : interior.dimension().location())), false);
        }

        for (FuelTarget target : targets) {
            IFluidHandler handler = target.handler();
            int fuel = handler == null ? 0 : FuelScan.fuelAmount(handler);
            source.sendSuccess(() -> Component.literal("  " + target.describe()
                    + " | fuel " + fuel + " mB"
                    + " | blast power " + String.format("%.1f", FuelExplosion.powerFor(fuel))), false);
        }

        if (detonate) {
            for (FuelTarget target : targets) {
                if (!BlastScheduler.isPrimed(target.key())) BlastScheduler.prime(level, target, 1);
            }
            source.sendSuccess(() -> Component.literal("Primed " + targets.size() + " container(s)."), true);
        }
        return targets.size();
    }

    private static String sizeOf(Entity entity) {
        var map = ContraptionCompat.fluidStorages(entity);
        return map == null ? "unreadable" : String.valueOf(map.size());
    }

    private FuelBlastCommands() {}
}
