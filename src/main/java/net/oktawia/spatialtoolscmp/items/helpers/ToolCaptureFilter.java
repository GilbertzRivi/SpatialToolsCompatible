package net.oktawia.spatialtoolscmp.items.helpers;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

import net.oktawia.spatialtoolscmp.util.StructureToolKeys;
import net.oktawia.spatialtoolscmp.util.TemplateUtil;

public final class ToolCaptureFilter {

    private ToolCaptureFilter() {
    }

    public static boolean shouldSkipStructureToolBlock(Level level, BlockPos pos, BlockState state) {
        if (state.isAir()) {
            return false;
        }

        if (state.is(Blocks.BEDROCK)
                || state.is(Blocks.NETHER_PORTAL)
                || state.is(Blocks.END_PORTAL)
                || state.is(Blocks.END_GATEWAY)
                || state.is(Blocks.BARRIER)
                || state.is(Blocks.COMMAND_BLOCK)
                || state.is(Blocks.CHAIN_COMMAND_BLOCK)
                || state.is(Blocks.REPEATING_COMMAND_BLOCK)
                || state.is(Blocks.STRUCTURE_BLOCK)
                || state.is(Blocks.STRUCTURE_VOID)
                || state.is(Blocks.JIGSAW)) {
            return true;
        }

        try {
            return state.getDestroySpeed(level, pos) < 0.0F;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static CompoundTag filterUncapturableBlocksFromTemplate(
            Level level,
            BlockPos worldOrigin,
            CompoundTag templateTag) {
        CompoundTag filtered = templateTag.copy();
        List<TemplateUtil.BlockInfo> parsedBlocks = TemplateUtil.parseRawBlocksFromTag(filtered);

        if (parsedBlocks.isEmpty()) {
            return filtered;
        }

        Set<BlockPos> skippedLocalPositions = new HashSet<>();

        for (TemplateUtil.BlockInfo info : parsedBlocks) {
            BlockPos localPos = info.pos();
            BlockPos worldPos = worldOrigin.offset(localPos);

            if (shouldSkipStructureToolBlock(level, worldPos, info.state())) {
                skippedLocalPositions.add(localPos);
            }
        }

        if (skippedLocalPositions.isEmpty()) {
            return filtered;
        }

        removeTemplateBlockEntriesAt(filtered, skippedLocalPositions);
        removeCloneMetadataEntriesAt(filtered, skippedLocalPositions);

        return filtered;
    }

    public static CompoundTag filterIncompleteMultiBlocksFromTemplate(CompoundTag templateTag) {
        CompoundTag filtered = templateTag.copy();
        List<TemplateUtil.BlockInfo> parsedBlocks = TemplateUtil.parseRawBlocksFromTag(filtered);

        if (parsedBlocks.isEmpty()) {
            return filtered;
        }

        Map<BlockPos, BlockState> statesByPos = new HashMap<>();

        for (TemplateUtil.BlockInfo info : parsedBlocks) {
            statesByPos.put(info.pos(), info.state());
        }

        Set<BlockPos> incompleteLocalPositions = new HashSet<>();

        for (TemplateUtil.BlockInfo info : parsedBlocks) {
            BlockPos partnerPos = multiBlockPartnerPos(info.state(), info.pos());

            if (partnerPos == null) {
                continue;
            }

            BlockState partnerState = statesByPos.get(partnerPos);

            if (partnerState == null || !partnerState.is(info.state().getBlock())) {
                incompleteLocalPositions.add(info.pos());
            }
        }

        if (incompleteLocalPositions.isEmpty()) {
            return filtered;
        }

        removeTemplateBlockEntriesAt(filtered, incompleteLocalPositions);
        removeCloneMetadataEntriesAt(filtered, incompleteLocalPositions);

        return filtered;
    }

    private static @Nullable BlockPos multiBlockPartnerPos(BlockState state, BlockPos pos) {
        if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)) {
            return state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.LOWER
                    ? pos.above()
                    : pos.below();
        }

        if (state.hasProperty(BlockStateProperties.BED_PART)
                && state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            Direction facing = state.getValue(BlockStateProperties.HORIZONTAL_FACING);

            return state.getValue(BlockStateProperties.BED_PART) == BedPart.FOOT
                    ? pos.relative(facing)
                    : pos.relative(facing.getOpposite());
        }

        return null;
    }

    private static void removeTemplateBlockEntriesAt(
            CompoundTag templateTag,
            Set<BlockPos> skippedLocalPositions) {
        if (!templateTag.contains("blocks", Tag.TAG_LIST)) {
            return;
        }

        ListTag oldBlocks = templateTag.getList("blocks", Tag.TAG_COMPOUND);
        ListTag newBlocks = new ListTag();

        for (int i = 0; i < oldBlocks.size(); i++) {
            CompoundTag blockEntry = oldBlocks.getCompound(i);
            BlockPos localPos = readTemplateBlockPos(blockEntry);

            if (localPos == null || !skippedLocalPositions.contains(localPos)) {
                newBlocks.add(blockEntry.copy());
            }
        }

        templateTag.put("blocks", newBlocks);
    }

    private static void removeCloneMetadataEntriesAt(
            CompoundTag templateTag,
            Set<BlockPos> skippedLocalPositions) {
        if (!templateTag.contains(StructureToolKeys.CLONE_METADATA_KEY, Tag.TAG_COMPOUND)) {
            return;
        }

        CompoundTag cloneMetadata = templateTag.getCompound(StructureToolKeys.CLONE_METADATA_KEY).copy();

        if (!cloneMetadata.contains(StructureToolKeys.CLONE_METADATA_BLOCKS_KEY, Tag.TAG_LIST)) {
            templateTag.put(StructureToolKeys.CLONE_METADATA_KEY, cloneMetadata);
            return;
        }

        ListTag oldBlocks = cloneMetadata.getList(StructureToolKeys.CLONE_METADATA_BLOCKS_KEY, Tag.TAG_COMPOUND);
        ListTag newBlocks = new ListTag();

        for (int i = 0; i < oldBlocks.size(); i++) {
            CompoundTag blockEntry = oldBlocks.getCompound(i);
            BlockPos localPos = readCloneMetadataBlockPos(blockEntry);

            if (localPos == null || !skippedLocalPositions.contains(localPos)) {
                newBlocks.add(blockEntry.copy());
            }
        }

        cloneMetadata.put(StructureToolKeys.CLONE_METADATA_BLOCKS_KEY, newBlocks);
        templateTag.put(StructureToolKeys.CLONE_METADATA_KEY, cloneMetadata);
    }

    private static @Nullable BlockPos readTemplateBlockPos(CompoundTag blockEntry) {
        if (!blockEntry.contains("pos", Tag.TAG_LIST)) {
            return null;
        }

        ListTag posTag = blockEntry.getList("pos", Tag.TAG_INT);

        if (posTag.size() < 3) {
            return null;
        }

        return new BlockPos(posTag.getInt(0), posTag.getInt(1), posTag.getInt(2));
    }

    private static @Nullable BlockPos readCloneMetadataBlockPos(CompoundTag blockEntry) {
        if (!blockEntry.contains(StructureToolKeys.CLONE_KEY_POS, Tag.TAG_COMPOUND)) {
            return null;
        }

        CompoundTag posTag = blockEntry.getCompound(StructureToolKeys.CLONE_KEY_POS);

        return new BlockPos(posTag.getInt("x"), posTag.getInt("y"), posTag.getInt("z"));
    }
}
