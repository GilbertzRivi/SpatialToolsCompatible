package net.oktawia.spatialtoolscmp.logic;

import java.util.List;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public interface ReplacerExtension {

    boolean canHandleSource(ServerLevel level, BlockPos pos, BlockState state);

    Set<BlockPos> findReplacementTargets(
            ServerLevel level,
            BlockPos startPos,
            BlockState sourceState,
            ReplacerContext ctx);

    @Nullable
    default CompoundTag capturePreReplacementState(ServerLevel level, BlockPos pos) {
        return null;
    }

    @Nullable
    default List<ItemStack> collectPreservedSourceRefunds(
            ServerLevel level,
            BlockPos pos,
            BlockState state,
            @Nullable BlockEntity be,
            Block targetBlock) {
        return null;
    }

    default void onBlockReplaced(
            ServerLevel level,
            BlockPos pos,
            @Nullable CompoundTag savedState) {
    }

    default boolean canHandleTarget(ServerLevel level, Block targetBlock) {
        return false;
    }

    @Nullable
    default Block resolveTargetBlock(ServerLevel level, ItemStack target) {
        return null;
    }

    @Nullable
    default ItemStack pickTargetItem(ServerLevel level, BlockPos pos, BlockState state) {
        return null;
    }

    default boolean isUnplaceableTarget(ServerLevel level, Block targetBlock, ItemStack target) {
        return false;
    }

    default boolean needsReplacement(ServerLevel level, BlockPos pos, ItemStack target) {
        return false;
    }

    @Nullable
    default ItemStack getInPlaceSwapItem(ServerLevel level, BlockPos pos, ItemStack target) {
        return null;
    }

    @Nullable
    default List<ItemStack> getPlacementCost(ServerLevel level, ItemStack target) {
        return null;
    }

    default boolean placeTarget(
            ServerLevel level,
            BlockPos pos,
            ItemStack target,
            @Nullable ServerPlayer player) {
        return false;
    }

    default void onNewBlocksPlaced(ServerLevel level, Set<BlockPos> positions) {
    }

    default void onBeforeReplacement(ServerLevel level, Set<BlockPos> positions) {
    }

    default void onReplacementDone(ServerLevel level, Set<BlockPos> positions) {
    }
}
