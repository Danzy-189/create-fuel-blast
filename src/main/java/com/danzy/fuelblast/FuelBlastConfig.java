package com.danzy.fuelblast;

import net.minecraftforge.common.ForgeConfigSpec;

import java.util.List;

/** All tunables of the addon. */
public final class FuelBlastConfig {
    public static final ForgeConfigSpec SPEC;

    /** How far from an explosion centre tanks are scanned, in blocks. */
    public static final ForgeConfigSpec.DoubleValue scanRadius;
    /** Extra reach added per point of triggering explosion power. */
    public static final ForgeConfigSpec.DoubleValue radiusPerPower;
    /** Tanks holding less fuel than this (mB) are ignored. */
    public static final ForgeConfigSpec.IntValue minFuelMb;
    /** Blast power for the first bucket of fuel. */
    public static final ForgeConfigSpec.DoubleValue basePower;
    /** Scaling coefficient: power = base + coefficient * (buckets * energy) ^ exponent. */
    public static final ForgeConfigSpec.DoubleValue powerCoefficient;
    public static final ForgeConfigSpec.DoubleValue powerExponent;
    /** Hard cap so a 100k mB industrial tank does not erase the server. */
    public static final ForgeConfigSpec.DoubleValue maxPower;
    /** Fuse in ticks before a primed tank goes off (randomised between min and max). */
    public static final ForgeConfigSpec.IntValue minFuseTicks;
    public static final ForgeConfigSpec.IntValue maxFuseTicks;
    public static final ForgeConfigSpec.BooleanValue breakBlocks;
    public static final ForgeConfigSpec.BooleanValue causeFire;
    /** Number of chained detonations allowed from one original explosion. */
    public static final ForgeConfigSpec.IntValue maxChainDepth;
    /** Guess flammability from the fluid name when it is not tagged/listed. */
    public static final ForgeConfigSpec.BooleanValue heuristicDetection;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> fuelValues;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> fuelTags;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> blacklist;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();

        b.push("detection");
        scanRadius = b.comment("Base radius (blocks) scanned for fuel tanks around an explosion")
                .defineInRange("scanRadius", 6.0D, 1.0D, 48.0D);
        radiusPerPower = b.comment("Additional scan radius per point of explosion power")
                .defineInRange("radiusPerPower", 0.75D, 0.0D, 8.0D);
        minFuelMb = b.comment("Minimum amount of fuel (mB) in a tank required to detonate")
                .defineInRange("minFuelMb", 250, 1, 1_000_000);
        heuristicDetection = b.comment("Treat unknown fluids whose id looks like fuel (diesel, gasoline, kerosene...) as flammable")
                .define("heuristicDetection", true);
        b.pop();

        b.push("blast");
        basePower = b.defineInRange("basePower", 1.5D, 0.0D, 50.0D);
        powerCoefficient = b.defineInRange("powerCoefficient", 1.35D, 0.0D, 50.0D);
        powerExponent = b.comment("Sub-linear growth keeps huge tanks fun instead of world-ending")
                .defineInRange("powerExponent", 0.62D, 0.1D, 2.0D);
        maxPower = b.defineInRange("maxPower", 14.0D, 1.0D, 100.0D);
        minFuseTicks = b.defineInRange("minFuseTicks", 2, 0, 200);
        maxFuseTicks = b.defineInRange("maxFuseTicks", 9, 0, 400);
        breakBlocks = b.define("breakBlocks", true);
        causeFire = b.define("causeFire", true);
        maxChainDepth = b.comment("How many times a fuel blast may set off further tanks")
                .defineInRange("maxChainDepth", 6, 0, 64);
        b.pop();

        b.push("fuels");
        fuelValues = b.comment(
                        "Explicit fuel list: 'namespace:fluid=energy'. Energy 1.0 = diesel.",
                        "Unknown entries are ignored, so ids from mods you do not have are harmless.")
                .defineList("fuelValues", List.of(
                        // Create: Diesel Generators
                        "createdieselgenerators:diesel=1.0",
                        "createdieselgenerators:biodiesel=0.95",
                        "createdieselgenerators:plant_oil=0.55",
                        "createdieselgenerators:ethanol=1.15",
                        "createdieselgenerators:gasoline=1.35",
                        "createdieselgenerators:crude_oil=0.7",
                        // Create: The Factory Must Grow
                        "createindustry:diesel=1.0",
                        "createindustry:gasoline=1.35",
                        "createindustry:naphtha=1.25",
                        "createindustry:kerosene=1.1",
                        "createindustry:heavy_fuel=0.85",
                        "createindustry:crude_oil=0.7",
                        "createindustry:lubricant=0.35",
                        "createindustry:sulfuric_naphtha=1.2",
                        // Create Aeronautics / misc
                        "aeronautics:jet_fuel=1.4",
                        "create_aeronautics:jet_fuel=1.4",
                        "minecraft:lava=0.4"
                ), o -> o instanceof String);
        fuelTags = b.comment("Fluid tags treated as fuel: 'namespace:path=energy'")
                .defineList("fuelTags", List.of(
                        "forge:diesel=1.0",
                        "forge:biodiesel=0.95",
                        "forge:gasoline=1.35",
                        "forge:kerosene=1.1",
                        "forge:naphtha=1.25",
                        "forge:ethanol=1.15",
                        "forge:plant_oil=0.55",
                        "forge:crude_oil=0.7",
                        "forge:fuel=1.0",
                        "forge:jet_fuel=1.4"
                ), o -> o instanceof String);
        blacklist = b.comment("Fluids that must never explode, even if tagged")
                .defineList("blacklist", List.of("minecraft:water", "create:honey", "create:chocolate"),
                        o -> o instanceof String);
        b.pop();

        SPEC = b.build();
    }

    private FuelBlastConfig() {}
}
