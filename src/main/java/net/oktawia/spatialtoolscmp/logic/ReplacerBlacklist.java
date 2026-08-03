package net.oktawia.spatialtoolscmp.logic;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;

import net.oktawia.spatialtoolscmp.SpatialConfig;
import net.oktawia.spatialtoolscmp.SpatialToolsCMP;
import net.oktawia.spatialtoolscmp.util.BlockMatcher;

public final class ReplacerBlacklist {

    private static volatile Set<Block> blockedBlocks = Set.of();

    private ReplacerBlacklist() {
    }

    public static boolean isProtected(BlockGetter level, BlockPos pos, BlockState state) {
        if (state == null) {
            return false;
        }

        boolean indestructible = !state.isAir() && state.getDestroySpeed(level, pos) < 0.0F;

        return indestructible || blockedBlocks.contains(state.getBlock());
    }

    public static void rebuild() {
        List<BlockMatcher.Compiled> expressions = new ArrayList<>();
        int invalid = 0;

        for (String entry : SpatialConfig.COMMON.PORTABLE_SPATIAL_REPLACER_BLACKLIST.get()) {
            BlockMatcher.Compiled compiled = BlockMatcher.compile(entry);

            if (!compiled.isValid()) {
                invalid++;
                SpatialToolsCMP.getLOGGER().warn("Invalid replacer blacklist expression: {}", entry);
                continue;
            }

            if (!compiled.isEmpty()) {
                expressions.add(compiled);
            }
        }

        if (expressions.isEmpty()) {
            blockedBlocks = Set.of();
            BlockMatcher.clearThreadLocalCache();
            return;
        }

        Set<Block> blocked = new HashSet<>();

        for (Block block : ForgeRegistries.BLOCKS) {
            BlockState state = block.defaultBlockState();

            for (BlockMatcher.Compiled compiled : expressions) {
                if (BlockMatcher.matches(state, compiled)) {
                    blocked.add(block);
                    break;
                }
            }
        }

        blockedBlocks = blocked;
        BlockMatcher.clearThreadLocalCache();

        SpatialToolsCMP.getLOGGER().info(
                "Replacer blacklist: {} expressions, {} invalid, {} blocked blocks",
                expressions.size(),
                invalid,
                blocked.size());
    }
}
