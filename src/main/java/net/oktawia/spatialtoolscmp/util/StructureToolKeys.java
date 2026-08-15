package net.oktawia.spatialtoolscmp.util;

import java.util.List;

public final class StructureToolKeys {
    private StructureToolKeys() {
    }

    public static final List<String> AE2_CABLE_BUS_KEYS = List.of(
            "cable", "north", "south", "east", "west", "up", "down");

    public static final List<String> AE2_FACADE_KEYS = List.of(
            "facadeNorth", "facadeSouth", "facadeEast", "facadeWest", "facadeUp", "facadeDown");

    public static final String AE2_FACADE_ITEM_ID = "ae2:facade";

    public static final String CLONE_METADATA_KEY = "clone_metadata";
    public static final String CLONE_METADATA_BLOCKS_KEY = "blocks";
    public static final String CLONE_REQUIREMENTS_KEY = "requirements";
    public static final String CLONE_KEY_POS = "pos";
    public static final String CLONE_KEY_SETTINGS = "settings";
    public static final String CLONE_KEY_UPGRADES = "upgrades";
    public static final String CLONE_KEY_PARTS = "parts";
    public static final String CLONE_KEY_AE2_CABLE_VISUAL = "ae2CableVisual";
    public static final String AE2_CABLE_VISUAL_CONNECTIONS = "connections";
    public static final String AE2_CABLE_VISUAL_CHANNELS_PREFIX = "channels";
    public static final String CLONE_KEY_STACK = "stack";
    public static final String CLONE_KEY_COUNT = "count";

    public static final String CRAZYAE2_MOD_ID = "crazyae2addons";
    public static final String CRAZYAE2_PROVIDER_BE_ID = "crazyae2addons:crazy_pattern_provider_be";
    public static final String CRAZYAE2_PROVIDER_PART_ID = "crazyae2addons:crazy_pattern_provider_part";
    public static final String CRAZYAE2_UPGRADE_ITEM_ID = "crazy_upgrade";
    public static final String CRAZYAE2_NBT_PROVIDER = "crazy_provider";
    public static final String CRAZYAE2_NBT_STATE = "state";
    public static final String CRAZYAE2_NBT_LEGACY_STATE = "crazy_state";
    public static final String CRAZYAE2_NBT_ADDED = "added";
    public static final String CLONE_KEY_CRAZY_UPGRADES = "crazyUpgrades";

    public static final String GTCEU_ID_PREFIX = "gtceu:";
    public static final String GT_CABLE_ID = "gtceu:cable";
    public static final String GT_ITEM_PIPE_ID = "gtceu:item_pipe";
    public static final String GT_FLUID_PIPE_ID = "gtceu:fluid_pipe";

    public static final String CLONE_KEY_GREG = "greg";
    public static final String CLONE_KEY_GREG_COVER = "cover";
    public static final String CLONE_KEY_GREG_PIPE = "pipe";
    public static final String CLONE_KEY_GREG_MACHINE = "machine";

    public static final String LASERIO_ID_PREFIX = "laserio:";
    public static final String CLONE_KEY_LASERIO = "laserio";

    public static final String MEKANISM_ID_PREFIX = "mekanism:";
    public static final String CLONE_KEY_MEKANISM = "mekanism";

    public static final List<String> MEKANISM_SIDE_KEY_PREFIXES = List.of(
            "connection", "mode");

    public static final String INTDYN_CABLE_BLOCK_ID = "integrateddynamics:cable";
    public static final String INTDYN_CABLE_BE_ID = "integrateddynamics:multipart_ticking";

    public static final String INTDYN_KEY_PART_CONTAINER = "partContainer";
    public static final String INTDYN_KEY_PARTS = "parts";
    public static final String INTDYN_KEY_PART_TYPE = "__partType";
    public static final String INTDYN_KEY_PART_SIDE = "__side";
    public static final String INTDYN_KEY_REAL_CABLE = "realCable";
    public static final String INTDYN_KEY_FACADE = "facadeBlockTag";
    public static final String INTDYN_KEY_TARGET_SIDE = "targetSide";
    public static final String INTDYN_KEY_OFFSET_X = "offsetX";
    public static final String INTDYN_KEY_OFFSET_Y = "offsetY";
    public static final String INTDYN_KEY_OFFSET_Z = "offsetZ";

    public static final List<String> INTDYN_SIDE_MAP_KEYS = List.of(
            "connected",
            "forceDisconnected",
            "redstoneLevels",
            "redstoneInputs",
            "redstoneStrong",
            "lastRedstonePulses",
            "lightLevels");

    public static final String CHISELED_BE_ID = "chiselsandbits:chiseled_block";
    public static final String CLONE_KEY_CHISELED = "chiseled";
    public static final String CHISELED_KEY_OPS = "ops";

    public static final int CHISELED_OP_ROTATE_CW = 1;
    public static final int CHISELED_OP_ROTATE_180 = 2;
    public static final int CHISELED_OP_ROTATE_CCW = 3;
    public static final int CHISELED_OP_MIRROR_Z = 4;
    public static final int CHISELED_OP_MIRROR_X = 5;
    public static final int CHISELED_OP_MIRROR_Y = 6;
}
