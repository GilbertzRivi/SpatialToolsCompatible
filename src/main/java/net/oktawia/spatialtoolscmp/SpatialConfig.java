package net.oktawia.spatialtoolscmp;

import java.util.List;

import org.apache.commons.lang3.tuple.Pair;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.ForgeConfigSpec;

public final class SpatialConfig {

    public static final ForgeConfigSpec COMMON_SPEC;
    public static final Common COMMON;

    static {
        Pair<Common, ForgeConfigSpec> pair = new ForgeConfigSpec.Builder().configure(Common::new);
        COMMON = pair.getLeft();
        COMMON_SPEC = pair.getRight();
    }

    public static boolean usePower() {
        return COMMON.USE_POWER.get();
    }

    public static double energyCostMultiplier(ForgeConfigSpec.DoubleValue configured) {
        return usePower() ? configured.get() : 0.0D;
    }

    public static final class Common {
        public final ForgeConfigSpec.BooleanValue USE_POWER;
        public final ForgeConfigSpec.IntValue PREVIEW_MAX_BLOCKS;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> ENERGY_UPGRADE_ITEMS;

        public final ForgeConfigSpec.IntValue PORTABLE_SPATIAL_STORAGE_COST;
        public final ForgeConfigSpec.DoubleValue PORTABLE_SPATIAL_STORAGE_ENERGY_COST_MULTIPLIER;
        public final ForgeConfigSpec.IntValue PORTABLE_SPATIAL_STORAGE_BASE_INTERNAL_POWER_CAPACITY;
        public final ForgeConfigSpec.IntValue PORTABLE_SPATIAL_STORAGE_MAX_STRUCTURE_SIZE;

        public final ForgeConfigSpec.IntValue PORTABLE_SPATIAL_CLONER_COST;
        public final ForgeConfigSpec.DoubleValue PORTABLE_SPATIAL_CLONER_ENERGY_COST_MULTIPLIER;
        public final ForgeConfigSpec.IntValue PORTABLE_SPATIAL_CLONER_BASE_INTERNAL_POWER_CAPACITY;
        public final ForgeConfigSpec.IntValue PORTABLE_SPATIAL_CLONER_MAX_STRUCTURE_SIZE;

        public final ForgeConfigSpec.IntValue PORTABLE_SPATIAL_REPLACER_COST;
        public final ForgeConfigSpec.DoubleValue PORTABLE_SPATIAL_REPLACER_ENERGY_COST_MULTIPLIER;
        public final ForgeConfigSpec.IntValue PORTABLE_SPATIAL_REPLACER_BASE_INTERNAL_POWER_CAPACITY;
        public final ForgeConfigSpec.IntValue PORTABLE_SPATIAL_REPLACER_MAX_BLOCKS;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> PORTABLE_SPATIAL_REPLACER_BLACKLIST;

        public final ForgeConfigSpec.IntValue PORTABLE_SPATIAL_PIPER_COST;
        public final ForgeConfigSpec.DoubleValue PORTABLE_SPATIAL_PIPER_ENERGY_COST_MULTIPLIER;
        public final ForgeConfigSpec.IntValue PORTABLE_SPATIAL_PIPER_BASE_INTERNAL_POWER_CAPACITY;
        public final ForgeConfigSpec.IntValue PORTABLE_SPATIAL_PIPER_MAX_BLOCKS;
        public final ForgeConfigSpec.IntValue PORTABLE_SPATIAL_PIPER_SELECTION_RANGE;

        public final ForgeConfigSpec.IntValue PORTABLE_SPATIAL_TOOL_BASE_INTERNAL_POWER_CAPACITY;

        public Common(ForgeConfigSpec.Builder builder) {
            builder.comment(
                    "Spatial Tools Compatible - Configuration.",
                    "For every config entry that defines a limit, -1 means no limit.").push("features");

            USE_POWER = builder
                    .comment(
                            "Whether portable spatial tools consume energy at all.",
                            "Set to false and every tool works for free, ignoring its own cost settings.")
                    .define("usePower", true);

            PREVIEW_MAX_BLOCKS = nonNegativeInt(builder,
                    "previewMaxBlocks", 512,
                    "How many ghost blocks the replacer and piper previews may draw at once.",
                    "Only limits rendering, the tools still act on every block within their own cap.");

            builder.comment(
                    "Energy upgrade items accepted by portable spatial tools.",
                    "Put item registry names here, for example:",
                    "- ae2:energy_card",
                    "- minecraft:stick",
                    "Every valid item in this list can be inserted into energy upgrade slots.",
                    "Crafting upgrade slot is not affected by this list.").push("energyUpgrades");

            ENERGY_UPGRADE_ITEMS = builder
                    .comment("List of item registry names accepted as energy upgrades.")
                    .defineList(
                            "items",
                            List.of("ae2:energy_card"),
                            Common::isValidItemIdString);

            builder.pop();

            builder.comment(
                    "Portable spatial storage feature.",
                    "Portable spatial storage can cut structures from the world,",
                    "rotate or flip them, and then paste them back,",
                    "while preserving block NBT and other metadata.").push("portableSpatialStorage");

            PORTABLE_SPATIAL_STORAGE_COST = nonNegativeInt(builder,
                    "cost", 1,
                    "Base AE cost factor for cutting or pasting one block.",
                    "The final cost is: base cost * block distance * energy cost multiplier.");

            PORTABLE_SPATIAL_STORAGE_ENERGY_COST_MULTIPLIER = nonNegativeDouble(builder,
                    "energyCostMultiplier", 1.0D,
                    "Multiplier applied to the distance-based AE cost.",
                    "Final cost is: base cost * block distance * this multiplier.",
                    "Set to 0 to disable energy cost for this gadget.");

            PORTABLE_SPATIAL_STORAGE_BASE_INTERNAL_POWER_CAPACITY = nonNegativeInt(builder,
                    "baseInternalPowerCapacity", 200000,
                    "Base internal power capacity for portable spatial storage.",
                    "Each energy upgrade adds this amount once more to the item's",
                    "internal energy storage.");

            PORTABLE_SPATIAL_STORAGE_MAX_STRUCTURE_SIZE = unlimitedInt(builder,
                    "maxStructureSize", -1,
                    "Maximum allowed structure size for portable spatial storage.",
                    "-1 means no limit.");

            builder.pop();

            builder.comment(
                    "Portable spatial cloner feature.",
                    "Portable spatial cloner can copy structures from the world,",
                    "rotate or flip them, and then paste them back,",
                    "while preserving machine settings. It works with AE2 cables and parts.")
                    .push("portableSpatialCloner");

            PORTABLE_SPATIAL_CLONER_COST = nonNegativeInt(builder,
                    "cost", 1,
                    "Base AE cost factor for copying or pasting one block.",
                    "The final cost is: base cost * block distance * energy cost multiplier.");

            PORTABLE_SPATIAL_CLONER_ENERGY_COST_MULTIPLIER = nonNegativeDouble(builder,
                    "energyCostMultiplier", 1.0D,
                    "Multiplier applied to the distance-based AE cost.",
                    "Final cost is: base cost * block distance * this multiplier.",
                    "Set to 0 to disable energy cost for this gadget.");

            PORTABLE_SPATIAL_CLONER_BASE_INTERNAL_POWER_CAPACITY = nonNegativeInt(builder,
                    "baseInternalPowerCapacity", 200000,
                    "Base internal power capacity for portable spatial cloner.",
                    "Each energy upgrade adds this amount once more to the item's",
                    "internal energy storage.");

            PORTABLE_SPATIAL_CLONER_MAX_STRUCTURE_SIZE = unlimitedInt(builder,
                    "maxStructureSize", -1,
                    "Maximum allowed structure size for portable spatial cloner.",
                    "-1 means no limit.");

            builder.pop();

            builder.comment(
                    "Portable spatial replacer feature.",
                    "Replaces all connected same-type blocks within radius with the chosen target block.")
                    .push("portableSpatialReplacer");

            PORTABLE_SPATIAL_REPLACER_COST = nonNegativeInt(builder,
                    "cost", 1,
                    "Base AE cost factor for replacing one block.",
                    "The final cost is: base cost * replaced blocks * energy cost multiplier.");

            PORTABLE_SPATIAL_REPLACER_ENERGY_COST_MULTIPLIER = nonNegativeDouble(builder,
                    "energyCostMultiplier", 1.0D,
                    "Multiplier applied to the block-count based AE cost.",
                    "Set to 0 to disable energy cost for this gadget.");

            PORTABLE_SPATIAL_REPLACER_BASE_INTERNAL_POWER_CAPACITY = nonNegativeInt(builder,
                    "baseInternalPowerCapacity", 200000,
                    "Base internal power capacity for portable spatial replacer.",
                    "Each energy upgrade adds this amount once more to the item's",
                    "internal energy storage.");

            PORTABLE_SPATIAL_REPLACER_MAX_BLOCKS = nonNegativeInt(builder,
                    "maxBlocks", 1024,
                    "Hard cap on how many blocks the replacer can replace in one operation.",
                    "Prevents accidental mass-replacement on large servers.");

            PORTABLE_SPATIAL_REPLACER_BLACKLIST = builder
                    .comment(
                            "Blocks the replacer refuses to replace.",
                            "Every entry is a boolean expression over block ids and block tags:",
                            "- plain id: minecraft:bedrock",
                            "- tag: #forge:ores",
                            "- glob: gtceu:*_casing",
                            "- operators: ! (not), & (and), | (or), ^ (xor), parentheses",
                            "Example: \"#forge:ores/* & !minecraft:coal_ore\"",
                            "A block is blacklisted when any entry matches it.")
                    .defineList(
                            "blacklist",
                            List.of("#forge:ores", "#forge:raw_materials"),
                            entry -> entry instanceof String);

            builder.pop();

            builder.comment(
                    "Portable spatial piper feature.",
                    "Builds the chosen block along a route made of axis aligned segments.")
                    .push("portableSpatialPiper");

            PORTABLE_SPATIAL_PIPER_COST = nonNegativeInt(builder,
                    "cost", 1,
                    "Base AE cost factor for building one block.",
                    "The final cost is: base cost * built blocks * energy cost multiplier.");

            PORTABLE_SPATIAL_PIPER_ENERGY_COST_MULTIPLIER = nonNegativeDouble(builder,
                    "energyCostMultiplier", 1.0D,
                    "Multiplier applied to the block-count based AE cost.",
                    "Set to 0 to disable energy cost for this gadget.");

            PORTABLE_SPATIAL_PIPER_BASE_INTERNAL_POWER_CAPACITY = nonNegativeInt(builder,
                    "baseInternalPowerCapacity", 200000,
                    "Base internal power capacity for portable spatial piper.",
                    "Each energy upgrade adds this amount once more to the item's",
                    "internal energy storage.");

            PORTABLE_SPATIAL_PIPER_MAX_BLOCKS = nonNegativeInt(builder,
                    "maxBlocks", 1024,
                    "Hard cap on how many blocks a single piper route can contain.",
                    "Route points that would exceed this cap are rejected.");

            PORTABLE_SPATIAL_PIPER_SELECTION_RANGE = nonNegativeInt(builder,
                    "selectionRange", 32,
                    "How far the piper looks for a block when picking a route point.",
                    "Used when the vanilla reach is not enough to hit the block you aim at.");

            builder.pop();

            builder.comment(
                    "Portable spatial tool feature.",
                    "Portable spatial tool is all four gadgets in one item.",
                    "Its mode is picked in the context menu, and energy and",
                    "energy upgrades are shared between all modes.").push("portableSpatialTool");

            PORTABLE_SPATIAL_TOOL_BASE_INTERNAL_POWER_CAPACITY = nonNegativeInt(builder,
                    "baseInternalPowerCapacity", 200000,
                    "Base internal power capacity for portable spatial tool.",
                    "Each energy upgrade adds this amount once more to the item's",
                    "internal energy storage.",
                    "Capped at the largest baseInternalPowerCapacity of the four gadgets,",
                    "so the capacity never depends on the selected mode.");

            builder.pop();

            builder.pop();
        }

        private static ForgeConfigSpec.IntValue nonNegativeInt(
                ForgeConfigSpec.Builder builder,
                String key,
                int defaultValue,
                String... comment) {
            return builder.comment(comment).defineInRange(key, defaultValue, 0, Integer.MAX_VALUE);
        }

        private static ForgeConfigSpec.IntValue unlimitedInt(
                ForgeConfigSpec.Builder builder,
                String key,
                int defaultValue,
                String... comment) {
            return builder.comment(comment).defineInRange(key, defaultValue, -1, Integer.MAX_VALUE);
        }

        private static ForgeConfigSpec.DoubleValue nonNegativeDouble(
                ForgeConfigSpec.Builder builder,
                String key,
                double defaultValue,
                String... comment) {
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
