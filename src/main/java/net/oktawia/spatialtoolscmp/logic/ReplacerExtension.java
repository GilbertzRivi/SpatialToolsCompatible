package net.oktawia.spatialtoolscmp.logic;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

public interface ReplacerExtension {

    boolean canHandleSource(ServerLevel level, BlockPos pos, BlockState state);

    Set<BlockPos> findReplacementTargets(
            ServerLevel level,
            BlockPos startPos,
            BlockState sourceState,
            ReplacerContext ctx
    );

    @Nullable
    default CompoundTag capturePreReplacementState(ServerLevel level, BlockPos pos) {
        return null;
    }

    default void onBlockReplaced(
            ServerLevel level,
            BlockPos pos,
            @Nullable CompoundTag savedState
    ) {
    }

    default boolean canHandleTarget(ServerLevel level, Block targetBlock) {
        return false;
    }

    default void onNewBlocksPlaced(ServerLevel level, Set<BlockPos> positions) {
    }
}
