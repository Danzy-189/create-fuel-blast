package com.danzy.fuelblast;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Decides whether a fluid burns and how energetic it is.
 * Energy 1.0 == diesel; it multiplies the effective fuel volume.
 */
public final class FuelRegistry {
    private static final Map<Fluid, Double> CACHE = new ConcurrentHashMap<>();

    private static final String[] FUEL_HINTS = {
            "diesel", "gasoline", "petrol", "benzin", "kerosene", "naphtha",
            "jet_fuel", "fuel", "ethanol", "methanol", "propane", "butane",
            "crude_oil", "oil", "lpg", "hydrogen", "syngas", "biodiesel"
    };
    private static final String[] NOT_FUEL_HINTS = {
            "water", "milk", "honey", "chocolate", "tea", "juice", "sewage",
            "coolant", "slime", "potion", "blood", "acid"
    };

    public static void clearCache() {
        CACHE.clear();
    }

    /** @return energy multiplier, or 0 when the fluid is inert. */
    public static double energyOf(FluidStack stack) {
        if (stack == null || stack.isEmpty()) return 0.0D;
        return energyOf(stack.getFluid());
    }

    public static double energyOf(Fluid fluid) {
        return CACHE.computeIfAbsent(fluid, FuelRegistry::compute);
    }

    public static boolean isFuel(FluidStack stack) {
        return energyOf(stack) > 0.0D;
    }

    private static double compute(Fluid fluid) {
        ResourceLocation id = ForgeRegistries.FLUIDS.getKey(fluid);
        if (id == null) return 0.0D;
        String key = id.toString();

        for (String entry : FuelBlastConfig.blacklist.get()) {
            if (entry.equalsIgnoreCase(key)) return 0.0D;
        }

        Map<String, Double> explicit = parse(FuelBlastConfig.fuelValues.get());
        Double direct = explicit.get(key);
        if (direct != null) return direct;

        for (Map.Entry<String, Double> e : parse(FuelBlastConfig.fuelTags.get()).entrySet()) {
            ResourceLocation tagId = ResourceLocation.tryParse(e.getKey());
            if (tagId == null) continue;
            TagKey<Fluid> tag = TagKey.create(Registries.FLUID, tagId);
            if (fluid.defaultFluidState().is(tag)) return e.getValue();
        }

        if (FuelBlastConfig.heuristicDetection.get()) {
            String path = id.getPath();
            for (String bad : NOT_FUEL_HINTS) {
                if (path.contains(bad)) return 0.0D;
            }
            for (String hint : FUEL_HINTS) {
                if (path.contains(hint)) return hint.equals("oil") ? 0.6D : 1.0D;
            }
        }
        return 0.0D;
    }

    private static Map<String, Double> parse(java.util.List<? extends String> list) {
        Map<String, Double> out = new HashMap<>();
        for (String raw : list) {
            String[] parts = raw.split("=");
            if (parts.length != 2) continue;
            try {
                out.put(parts[0].trim(), Double.parseDouble(parts[1].trim()));
            } catch (NumberFormatException ignored) {
                FuelBlast.LOGGER.warn("Bad fuel entry in config: {}", raw);
            }
        }
        return out;
    }

    private FuelRegistry() {}
}
