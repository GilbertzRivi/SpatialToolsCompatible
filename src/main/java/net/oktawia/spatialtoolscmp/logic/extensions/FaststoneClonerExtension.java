package net.oktawia.spatialtoolscmp.logic.extensions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.RegistryObject;

import net.oktawia.faststone.blocks.LogicCableBlock;
import net.oktawia.faststone.defs.regs.BlockRegistrar;
import net.oktawia.faststone.defs.regs.ItemRegistrar;
import net.oktawia.faststone.entities.LogicCableBlockEntity;
import net.oktawia.faststone.logic.LogicCableColor;
import net.oktawia.faststone.logic.network.LogicNetworkGraph;
import net.oktawia.faststone.logic.parts.LogicCablePartType;
import net.oktawia.spatialtoolscmp.items.AbstractStructureCaptureToolItem;
import net.oktawia.spatialtoolscmp.logic.ClonerPasteContext;
import net.oktawia.spatialtoolscmp.logic.PlacementPlan;
import net.oktawia.spatialtoolscmp.logic.StructureCloneExtension;

public final class FaststoneClonerExtension implements StructureCloneExtension {

    private static final String MOD_ID = "faststonelogic";

    private static final String META_KEY = "Faststone";
    private static final String KEY_FULL_BE_TAG = "FullBlockEntityTag";

    private static final String BE_ID_CABLE = MOD_ID + ":logic_cable";
    private static final String OLD_BE_ID_CABLE = MOD_ID + ":cable";

    private static final String KEY_PARTS = "Parts";
    private static final String KEY_REDSTONE_OUTPUTS = "RedstoneOutputs";

    @Override
    public boolean collectMetadata(
            ServerLevel level,
            BlockPos pos,
            BlockEntity be,
            @Nullable CompoundTag rawBeTag,
            AbstractStructureCaptureToolItem.RequirementSink requirements,
            CompoundTag blockEntry) {
        if (!isFaststoneTag(rawBeTag)) {
            return false;
        }

        BlockState state = be.getBlockState();

        if (isFaststoneCableState(state) || isFaststoneCableTag(rawBeTag)) {
            ItemStack cableStack = getFaststoneCableItemStack(state);

            if (!cableStack.isEmpty()) {
                requirements.add(cableStack);
            }

            collectCablePartRequirements(rawBeTag, requirements);
        }

        CompoundTag metadata = new CompoundTag();
        metadata.put(KEY_FULL_BE_TAG, rawBeTag.copy());

        blockEntry.put(META_KEY, metadata);
        return true;
    }

    @Override
    public boolean handlesRequirements(BlockState state, @Nullable CompoundTag rawBeTag) {
        return isFaststoneTag(rawBeTag);
    }

    @Override
    public Optional<PlacementPlan> buildPlacementPlan(
            ServerLevel level,
            Player player,
            BlockState state,
            @Nullable CompoundTag rawBeTag,
            @Nullable CompoundTag blockMetadata,
            ClonerPasteContext ctx) {
        CompoundTag fullBeTag = getFullBeTag(rawBeTag, blockMetadata);

        if (!isFaststoneTag(rawBeTag) && !hasFaststoneMetadata(blockMetadata)) {
            return Optional.empty();
        }

        List<ItemStack> costs = new ArrayList<>();
        Map<Item, Integer> reserved = new HashMap<>();

        ItemStack baseItem = getRequiredBaseItem(state, ctx);

        if (!baseItem.isEmpty()) {
            if (!tryReserveAndAddCost(player, ctx, reserved, costs, baseItem)) {
                return Optional.of(PlacementPlan.none());
            }
        } else if (!player.isCreative()) {
            return Optional.of(PlacementPlan.none());
        }

        if (isFaststoneCableState(state) || isFaststoneCableTag(fullBeTag) || isFaststoneCableTag(rawBeTag)) {
            if (!addCablePartCosts(fullBeTag, player, ctx, reserved, costs)) {
                return Optional.of(PlacementPlan.none());
            }
        }

        CompoundTag placementTag = buildPlacementBeTag(rawBeTag, blockMetadata);

        return Optional.of(new PlacementPlan(true, state, placementTag, costs));
    }

    @Override
    public void onBlockPlaced(
            ServerLevel level,
            Player player,
            BlockPos pos,
            @Nullable BlockEntity be,
            @Nullable CompoundTag blockMetadata) {
        if (be == null || !hasFaststoneMetadata(blockMetadata)) {
            return;
        }

        CompoundTag metadata = blockMetadata.getCompound(META_KEY);

        if (!metadata.contains(KEY_FULL_BE_TAG, Tag.TAG_COMPOUND)) {
            return;
        }

        CompoundTag clonedTag = metadata.getCompound(KEY_FULL_BE_TAG).copy();

        clonedTag.putInt("x", pos.getX());
        clonedTag.putInt("y", pos.getY());
        clonedTag.putInt("z", pos.getZ());

        CompoundTag currentTag = be.saveWithFullMetadata();

        if (currentTag.contains("id", Tag.TAG_STRING)) {
            clonedTag.putString("id", currentTag.getString("id"));
        }

        try {
            be.load(clonedTag);
        } catch (Throwable ignored) {
            return;
        }

        be.setChanged();

        refreshFaststoneConnectionsAfterPaste(level, pos);
    }

    @Override
    public boolean collectUndoRefunds(
            ServerLevel level,
            BlockPos pos,
            BlockState state,
            @Nullable BlockEntity be,
            List<ItemStack> refunds) {
        if (!isFaststoneCableState(state)) {
            return false;
        }

        ItemStack cableStack = getFaststoneCableItemStack(state);

        if (!cableStack.isEmpty()) {
            refunds.add(cableStack);
        }

        if (be instanceof LogicCableBlockEntity cable) {
            for (Direction side : Direction.values()) {
                ItemStack partStack = getPartItemStack(cable.getPart(side));

                if (!partStack.isEmpty()) {
                    refunds.add(partStack);
                }
            }

            return true;
        }

        if (be != null) {
            try {
                collectCablePartRefunds(be.saveWithFullMetadata(), refunds);
            } catch (Throwable ignored) {
            }
        }

        return true;
    }

    private static void refreshFaststoneConnectionsAfterPaste(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);

        if (state.isAir()) {
            return;
        }

        if (state.getBlock() instanceof LogicCableBlock) {
            BlockState newState = state;

            for (Direction dir : Direction.values()) {
                BlockPos neighborPos = pos.relative(dir);
                BlockState neighborState = level.getBlockState(neighborPos);

                newState = newState.updateShape(
                        dir,
                        neighborState,
                        level,
                        pos,
                        neighborPos);
            }

            if (!newState.equals(state)) {
                level.setBlock(pos, newState, Block.UPDATE_ALL);
                state = level.getBlockState(pos);
            } else {
                level.sendBlockUpdated(pos, state, state, Block.UPDATE_ALL);
            }
        }

        Block block = state.getBlock();

        for (Direction dir : Direction.values()) {
            BlockPos neighborPos = pos.relative(dir);
            BlockState neighborState = level.getBlockState(neighborPos);

            if (neighborState.getBlock() instanceof LogicCableBlock) {
                BlockState updatedNeighborState = neighborState.updateShape(
                        dir.getOpposite(),
                        state,
                        level,
                        neighborPos,
                        pos);

                if (!updatedNeighborState.equals(neighborState)) {
                    level.setBlock(neighborPos, updatedNeighborState, Block.UPDATE_ALL);
                } else {
                    level.sendBlockUpdated(
                            neighborPos,
                            neighborState,
                            neighborState,
                            Block.UPDATE_ALL);

                    neighborState.neighborChanged(
                            level,
                            neighborPos,
                            block,
                            pos,
                            false);
                }

                LogicNetworkGraph.scheduleRebuildAround(level, neighborPos);
            }

            level.updateNeighborsAt(neighborPos, block);
        }

        level.updateNeighborsAt(pos, block);
        level.updateNeighbourForOutputSignal(pos, block);
        level.blockUpdated(pos, block);

        LogicNetworkGraph.scheduleRebuildAround(level, pos);
    }

    @Nullable
    private static CompoundTag buildPlacementBeTag(
            @Nullable CompoundTag rawBeTag,
            @Nullable CompoundTag blockMetadata) {
        CompoundTag tag = getFullBeTag(rawBeTag, blockMetadata);

        if (tag == null) {
            return null;
        }

        tag = tag.copy();

        tag.remove("x");
        tag.remove("y");
        tag.remove("z");

        return tag;
    }

    @Nullable
    private static CompoundTag getFullBeTag(
            @Nullable CompoundTag rawBeTag,
            @Nullable CompoundTag blockMetadata) {
        if (blockMetadata != null && blockMetadata.contains(META_KEY, Tag.TAG_COMPOUND)) {
            CompoundTag metadata = blockMetadata.getCompound(META_KEY);

            if (metadata.contains(KEY_FULL_BE_TAG, Tag.TAG_COMPOUND)) {
                return metadata.getCompound(KEY_FULL_BE_TAG).copy();
            }
        }

        if (rawBeTag != null) {
            return rawBeTag.copy();
        }

        return null;
    }

    private static void collectCablePartRequirements(
            @Nullable CompoundTag beTag,
            AbstractStructureCaptureToolItem.RequirementSink requirements) {
        if (beTag == null || !beTag.contains(KEY_PARTS, Tag.TAG_COMPOUND)) {
            return;
        }

        CompoundTag partsTag = beTag.getCompound(KEY_PARTS);

        for (Direction side : Direction.values()) {
            LogicCablePartType partType = readPartType(partsTag, side);
            ItemStack partStack = getPartItemStack(partType);

            if (!partStack.isEmpty()) {
                requirements.add(partStack);
            }
        }
    }

    private static boolean addCablePartCosts(
            @Nullable CompoundTag beTag,
            Player player,
            ClonerPasteContext ctx,
            Map<Item, Integer> reserved,
            List<ItemStack> costs) {
        if (beTag == null || !beTag.contains(KEY_PARTS, Tag.TAG_COMPOUND)) {
            return true;
        }

        CompoundTag partsTag = beTag.getCompound(KEY_PARTS);

        for (Direction side : Direction.values()) {
            LogicCablePartType partType = readPartType(partsTag, side);
            ItemStack partStack = getPartItemStack(partType);

            if (partStack.isEmpty()) {
                continue;
            }

            if (!tryReserveAndAddCost(player, ctx, reserved, costs, partStack)) {
                return false;
            }
        }

        return true;
    }

    private static void collectCablePartRefunds(
            @Nullable CompoundTag beTag,
            List<ItemStack> refunds) {
        if (beTag == null || !beTag.contains(KEY_PARTS, Tag.TAG_COMPOUND)) {
            return;
        }

        CompoundTag partsTag = beTag.getCompound(KEY_PARTS);

        for (Direction side : Direction.values()) {
            LogicCablePartType partType = readPartType(partsTag, side);
            ItemStack partStack = getPartItemStack(partType);

            if (!partStack.isEmpty()) {
                refunds.add(partStack);
            }
        }
    }

    private static LogicCablePartType readPartType(CompoundTag partsTag, Direction side) {
        String partName = partsTag.getString(side.getName());

        if (partName == null || partName.isBlank()) {
            return LogicCablePartType.NONE;
        }

        try {
            return LogicCablePartType.byName(partName);
        } catch (Throwable ignored) {
            return LogicCablePartType.NONE;
        }
    }

    private static ItemStack getPartItemStack(LogicCablePartType type) {
        return switch (type) {
            case INPUT -> new ItemStack(ItemRegistrar.LOGIC_INPUT_PART.get());
            case OUTPUT -> new ItemStack(ItemRegistrar.LOGIC_OUTPUT_PART.get());
            case DISPLAY -> new ItemStack(ItemRegistrar.LOGIC_DISPLAY_PART.get());
            case NONE -> ItemStack.EMPTY;
        };
    }

    private static ItemStack getRequiredBaseItem(BlockState state, ClonerPasteContext ctx) {
        if (isFaststoneCableState(state)) {
            ItemStack cableStack = getFaststoneCableItemStack(state);

            if (!cableStack.isEmpty()) {
                return cableStack;
            }
        }

        return normalizeSingle(ctx.getRequiredBlockItem(state));
    }

    private static ItemStack getFaststoneCableItemStack(BlockState state) {
        if (!isFaststoneCableState(state)) {
            return ItemStack.EMPTY;
        }

        LogicCableColor color = state.hasProperty(LogicCableBlock.COLOR)
                ? state.getValue(LogicCableBlock.COLOR)
                : LogicCableColor.RED;

        RegistryObject<Item> item = BlockRegistrar.getLogicCableItem(color);

        if (item == null) {
            return ItemStack.EMPTY;
        }

        return new ItemStack(item.get());
    }

    private static boolean tryReserveAndAddCost(
            Player player,
            ClonerPasteContext ctx,
            Map<Item, Integer> reserved,
            List<ItemStack> costs,
            ItemStack stack) {
        ItemStack normalized = normalizeCountPreserving(stack);

        if (normalized.isEmpty()) {
            return true;
        }

        int amount = Math.max(1, normalized.getCount());

        if (!player.isCreative() && !ctx.canReserveForPaste(reserved, normalized, amount)) {
            return false;
        }

        costs.add(normalized);
        return true;
    }

    private static boolean isFaststoneCableState(BlockState state) {
        return state.getBlock() instanceof LogicCableBlock;
    }

    private static boolean isFaststoneCableTag(@Nullable CompoundTag rawBeTag) {
        if (rawBeTag == null) {
            return false;
        }

        String id = rawBeTag.getString("id");

        if (BE_ID_CABLE.equals(id) || OLD_BE_ID_CABLE.equals(id)) {
            return true;
        }

        return rawBeTag.contains(KEY_PARTS, Tag.TAG_COMPOUND)
                && rawBeTag.contains(KEY_REDSTONE_OUTPUTS, Tag.TAG_COMPOUND);
    }

    private static boolean isFaststoneTag(@Nullable CompoundTag rawBeTag) {
        if (rawBeTag == null) {
            return false;
        }

        String id = rawBeTag.getString("id");

        if (id.startsWith(MOD_ID + ":")) {
            return true;
        }

        if (rawBeTag.contains("PortStates", Tag.TAG_COMPOUND)
                && rawBeTag.contains("PortColors", Tag.TAG_COMPOUND)
                && rawBeTag.contains("Inputs", Tag.TAG_COMPOUND)
                && rawBeTag.contains("Outputs", Tag.TAG_COMPOUND)) {
            return true;
        }

        return isFaststoneCableTag(rawBeTag);
    }

    private static boolean hasFaststoneMetadata(@Nullable CompoundTag blockMetadata) {
        return blockMetadata != null
                && blockMetadata.contains(META_KEY, Tag.TAG_COMPOUND);
    }

    private static ItemStack normalizeSingle(ItemStack stack) {
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }

        ItemStack copy = stack.copy();

        copy.setCount(1);
        copy.setTag(null);

        return copy;
    }

    private static ItemStack normalizeCountPreserving(ItemStack stack) {
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }

        ItemStack copy = stack.copy();

        copy.setTag(null);
        copy.setCount(Math.max(1, stack.getCount()));

        return copy;
    }
}
