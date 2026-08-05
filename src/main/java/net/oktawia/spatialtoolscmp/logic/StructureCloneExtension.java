package net.oktawia.spatialtoolscmp.logic;

import java.util.List;
import java.util.Optional;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import net.oktawia.spatialtoolscmp.items.AbstractStructureCaptureToolItem;
import net.oktawia.spatialtoolscmp.util.TemplateUtil;

public interface StructureCloneExtension {

    boolean collectMetadata(
            ServerLevel level,
            BlockPos pos,
            BlockEntity be,
            @Nullable CompoundTag rawBeTag,
            AbstractStructureCaptureToolItem.RequirementSink requirements,
            CompoundTag blockEntry);

    boolean handlesRequirements(BlockState state, @Nullable CompoundTag rawBeTag);

    default boolean sanitizeCapturedBlockEntityTag(BlockState state, CompoundTag rawBeTag) {
        return false;
    }

    default void onBeforePaste(
            ServerLevel level,
            Player player,
            List<TemplateUtil.BlockInfo> blocks) {
    }

    Optional<PlacementPlan> buildPlacementPlan(
            ServerLevel level,
            Player player,
            BlockState state,
            @Nullable CompoundTag rawBeTag,
            @Nullable CompoundTag blockMetadata,
            ClonerPasteContext ctx);

    void onBlockPlaced(
            ServerLevel level,
            BlockPos pos,
            @Nullable BlockEntity be,
            @Nullable CompoundTag blockMetadata);

    default void onBlockRestored(
            ServerLevel level,
            BlockPos pos,
            @Nullable BlockEntity be,
            @Nullable CompoundTag savedBeTag) {
    }

    default boolean collectUndoRefunds(
            ServerLevel level,
            BlockPos pos,
            BlockState state,
            @Nullable BlockEntity be,
            List<ItemStack> refunds) {
        return false;
    }

    default boolean hasNonEmptyStorage(
            ServerLevel level,
            BlockPos pos,
            BlockState state,
            @Nullable BlockEntity be) {
        if (be instanceof Container container) {
            return !container.isEmpty();
        }
        return false;
    }
}
