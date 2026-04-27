package net.oktawia.spatialtoolscmp;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.ForgeConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

import java.util.List;

public final class SpatialConfig {

    public static final ForgeConfigSpec COMMON_SPEC;
    public static final Common COMMON;

    static {
        Pair<Common, ForgeConfigSpec> pair = new ForgeConfigSpec.Builder().configure(Common::new);
        COMMON = pair.getLeft();
        COMMON_SPEC = pair.getRight();
    }

    public static final class Common {
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> ENERGY_UPGRADE_ITEMS;

        public final ForgeConfigSpec.IntValue PORTABLE_SPATIAL_STORAGE_COST;
        public final ForgeConfigSpec.DoubleValue PORTABLE_SPATIAL_STORAGE_ENERGY_COST_MULTIPLIER;
        public final ForgeConfigSpec.IntValue PORTABLE_SPATIAL_STORAGE_BASE_INTERNAL_POWER_CAPACITY;
        public final ForgeConfigSpec.IntValue PORTABLE_SPATIAL_STORAGE_MAX_STRUCTURE_SIZE;

        public final ForgeConfigSpec.IntValue PORTABLE_SPATIAL_CLONER_COST;
        public final ForgeConfigSpec.DoubleValue PORTABLE_SPATIAL_CLONER_ENERGY_COST_MULTIPLIER;
        public final ForgeConfigSpec.IntValue PORTABLE_SPATIAL_CLONER_BASE_INTERNAL_POWER_CAPACITY;
        public final ForgeConfigSpec.IntValue PORTABLE_SPATIAL_CLONER_MAX_STRUCTURE_SIZE;

        public Common(ForgeConfigSpec.Builder builder) {
            builder.comment(
                    "Spatial Tools Compatible - Configuration.",
                    "For every config entry that defines a limit, -1 means no limit."
            ).push("features");

            builder.comment(
                    "Energy upgrade items accepted by portable spatial tools.",
                    "Put item registry names here, for example:",
                    "- ae2:energy_card",
                    "- minecraft:stick",
                    "Every valid item in this list can be inserted into energy upgrade slots.",
                    "Crafting upgrade slot is not affected by this list."
            ).push("energyUpgrades");

            ENERGY_UPGRADE_ITEMS = builder
                    .comment("List of item registry names accepted as energy upgrades.")
                    .defineList(
                            "items",
                            List.of("ae2:energy_card"),
                            Common::isValidItemIdString
                    );

            builder.pop();

            builder.comment(
                    "Portable spatial storage feature.",
                    "Portable spatial storage can cut structures from the world,",
                    "rotate or flip them, and then paste them back,",
                    "while preserving block NBT and other metadata."
            ).push("portableSpatialStorage");

            PORTABLE_SPATIAL_STORAGE_COST = nonNegativeInt(builder,
                    "cost", 1,
                    "Base AE cost factor for cutting or pasting one block.",
                    "The final cost is: base cost * block distance * energy cost multiplier."
            );

            PORTABLE_SPATIAL_STORAGE_ENERGY_COST_MULTIPLIER = nonNegativeDouble(builder,
                    "energyCostMultiplier", 1.0D,
                    "Multiplier applied to the distance-based AE cost.",
                    "Final cost is: base cost * block distance * this multiplier.",
                    "Set to 0 to disable energy cost for this gadget."
            );

            PORTABLE_SPATIAL_STORAGE_BASE_INTERNAL_POWER_CAPACITY = nonNegativeInt(builder,
                    "baseInternalPowerCapacity", 200000,
                    "Base internal power capacity for portable spatial storage.",
                    "Each energy upgrade adds this amount once more to the item's",
                    "internal energy storage."
            );

            PORTABLE_SPATIAL_STORAGE_MAX_STRUCTURE_SIZE = unlimitedInt(builder,
                    "maxStructureSize", -1,
                    "Maximum allowed structure size for portable spatial storage.",
                    "-1 means no limit."
            );

            builder.pop();

            builder.comment(
                    "Portable spatial cloner feature.",
                    "Portable spatial cloner can copy structures from the world,",
                    "rotate or flip them, and then paste them back,",
                    "while preserving machine settings. It works with AE2 cables and parts."
            ).push("portableSpatialCloner");

            PORTABLE_SPATIAL_CLONER_COST = nonNegativeInt(builder,
                    "cost", 1,
                    "Base AE cost factor for copying or pasting one block.",
                    "The final cost is: base cost * block distance * energy cost multiplier."
            );

            PORTABLE_SPATIAL_CLONER_ENERGY_COST_MULTIPLIER = nonNegativeDouble(builder,
                    "energyCostMultiplier", 1.0D,
                    "Multiplier applied to the distance-based AE cost.",
                    "Final cost is: base cost * block distance * this multiplier.",
                    "Set to 0 to disable energy cost for this gadget."
            );

            PORTABLE_SPATIAL_CLONER_BASE_INTERNAL_POWER_CAPACITY = nonNegativeInt(builder,
                    "baseInternalPowerCapacity", 200000,
                    "Base internal power capacity for portable spatial cloner.",
                    "Each energy upgrade adds this amount once more to the item's",
                    "internal energy storage."
            );

            PORTABLE_SPATIAL_CLONER_MAX_STRUCTURE_SIZE = unlimitedInt(builder,
                    "maxStructureSize", -1,
                    "Maximum allowed structure size for portable spatial cloner.",
                    "-1 means no limit."
            );

            builder.pop();

            builder.pop();
        }


        private static ForgeConfigSpec.IntValue nonNegativeInt(
                ForgeConfigSpec.Builder builder,
                String key,
                int defaultValue,
                String... comment
        ) {
            return builder.comment(comment).defineInRange(key, defaultValue, 0, Integer.MAX_VALUE);
        }

        private static ForgeConfigSpec.IntValue unlimitedInt(
                ForgeConfigSpec.Builder builder,
                String key,
                int defaultValue,
                String... comment
        ) {
            return builder.comment(comment).defineInRange(key, defaultValue, -1, Integer.MAX_VALUE);
        }

        private static ForgeConfigSpec.DoubleValue nonNegativeDouble(
                ForgeConfigSpec.Builder builder,
                String key,
                double defaultValue,
                String... comment
        ) {
            return builder.comment(comment).defineInRange(key, defaultValue, 0.0D, Double.MAX_VALUE);
        }

        private static boolean isValidItemIdString(Object value) {
            if (!(value instanceof String string)) {
                return false;
            }

            return ResourceLocation.tryParse(string) != null;
        }
    }

    private SpatialConfig() {
    }
}