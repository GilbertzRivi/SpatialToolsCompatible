package net.oktawia.spatialtoolscmp.logic.extensions;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;

import net.oktawia.spatialtoolscmp.items.AbstractStructureCaptureToolItem;
import net.oktawia.spatialtoolscmp.logic.ClonerPasteContext;
import net.oktawia.spatialtoolscmp.logic.PlacementPlan;
import net.oktawia.spatialtoolscmp.logic.StructureCloneExtension;
import net.oktawia.spatialtoolscmp.util.NbtUtil;

public final class CBMultipartStructureExtension implements StructureCloneExtension {

    private static final String CB_MULTIPART_BLOCK_ID = "cb_multipart:multipart";
    private static final String CB_MULTIPART_BE_ID = "cb_multipart:saved_multipart";

    private static final String NBT_ID = "id";
    private static final String NBT_PARTS = "parts";

    private static final String META_KEY = "CBMultipart";
    private static final String META_PARTS = "Parts";

    private static final String[] COMMON_ITEM_KEYS = {
            "item",
            "Item",
            "stack",
            "Stack",
            "itemStack",
            "ItemStack",
            "drop",
            "Drop"
    };

    @Override
    public boolean handlesRequirements(BlockState state, @Nullable CompoundTag rawBeTag) {
        return isCbMultipart(state, rawBeTag);
    }

    @Override
    public boolean collectMetadata(
            ServerLevel level,
            BlockPos pos,
            BlockEntity be,
            @Nullable CompoundTag rawBeTag,
            AbstractStructureCaptureToolItem.RequirementSink requirements,
            CompoundTag blockEntry) {
        if (!isCbMultipart(level.getBlockState(pos), rawBeTag)) {
            return false;
        }

        CompoundTag metadata = new CompoundTag();
        ListTag keptParts = new ListTag();

        if (rawBeTag != null && rawBeTag.contains(NBT_PARTS, Tag.TAG_LIST)) {
            ListTag parts = rawBeTag.getList(NBT_PARTS, Tag.TAG_COMPOUND);

            for (int i = 0; i < parts.size(); i++) {
                CompoundTag partTag = parts.getCompound(i);

                keptParts.add(partTag.copy());

                ItemStack cost = normalizeCostStack(getPartCostStack(partTag));

                if (!cost.isEmpty()) {
                    requirements.add(cost);
                }
            }
        }

        metadata.put(META_PARTS, keptParts);
        blockEntry.put(META_KEY, metadata);

        return true;
    }

    @Override
    public Optional<PlacementPlan> buildPlacementPlan(
            ServerLevel level,
            Player player,
            BlockState state,
            @Nullable CompoundTag rawBeTag,
            @Nullable CompoundTag blockMetadata,
            ClonerPasteContext ctx) {
        if (!isCbMultipart(state, rawBeTag) && !hasMetadata(blockMetadata)) {
            return Optional.empty();
        }

        ListTag sourceParts = getStoredParts(rawBeTag, blockMetadata);

        if (sourceParts.isEmpty()) {
            return Optional.of(PlacementPlan.none());
        }

        ListTag filteredParts = new ListTag();
        List<ItemStack> costs = new ArrayList<>();
        Map<Item, Integer> reserved = new LinkedHashMap<>();

        for (int i = 0; i < sourceParts.size(); i++) {
            CompoundTag partTag = sourceParts.getCompound(i);
            ItemStack cost = normalizeCostStack(getPartCostStack(partTag));

            if (player.isCreative()) {
                filteredParts.add(partTag.copy());

                if (!cost.isEmpty()) {
                    costs.add(cost);
                }

                continue;
            }

            if (cost.isEmpty()) {
                continue;
            }

            ItemStack reserveKey = normalizeSingle(cost);
            int amount = Math.max(1, cost.getCount());

            Map<Item, Integer> trialReserved = new LinkedHashMap<>(reserved);

            if (!ctx.canReserveForPaste(trialReserved, reserveKey, amount)) {
                continue;
            }

            reserved.clear();
            reserved.putAll(trialReserved);

            filteredParts.add(partTag.copy());
            costs.add(cost);
        }

        if (filteredParts.isEmpty()) {
            return Optional.of(PlacementPlan.none());
        }

        CompoundTag placementTag = createMultipartPlacementTag(filteredParts);

        return Optional.of(new PlacementPlan(
                true,
                state,
                placementTag,
                costs));
    }

    @Override
    public void onBlockPlaced(
            ServerLevel level,
            BlockPos pos,
            @Nullable BlockEntity be,
            @Nullable CompoundTag blockMetadata) {
        if (be == null || !hasMetadata(blockMetadata)) {
            return;
        }

        ListTag parts = getStoredParts(null, blockMetadata);

        if (parts.isEmpty()) {
            return;
        }

        CompoundTag tag = createMultipartPlacementTag(parts);

        tag.putInt("x", pos.getX());
        tag.putInt("y", pos.getY());
        tag.putInt("z", pos.getZ());

        try {
            be.load(tag);
        } catch (Throwable ignored) {
            return;
        }

        be.setChanged();

        BlockState state = level.getBlockState(pos);
        level.sendBlockUpdated(pos, state, state, Block.UPDATE_ALL);

        refreshMultipartConnections(level, pos, state);
    }

    @Override
    public boolean collectUndoRefunds(
            ServerLevel level,
            BlockPos pos,
            BlockState state,
            @Nullable BlockEntity be,
            List<ItemStack> refunds) {
        CompoundTag currentTag = saveCurrentTag(be);

        if (!isCbMultipart(state, currentTag)) {
            return false;
        }

        if (currentTag == null || !currentTag.contains(NBT_PARTS, Tag.TAG_LIST)) {
            return true;
        }

        ListTag parts = currentTag.getList(NBT_PARTS, Tag.TAG_COMPOUND);

        for (int i = 0; i < parts.size(); i++) {
            CompoundTag partTag = parts.getCompound(i);
            ItemStack cost = normalizeCostStack(getPartCostStack(partTag));

            if (!cost.isEmpty()) {
                refunds.add(cost);
            }
        }

        return true;
    }

    private static boolean isCbMultipart(BlockState state, @Nullable CompoundTag rawBeTag) {
        ResourceLocation blockId = ForgeRegistries.BLOCKS.getKey(state.getBlock());

        if (blockId != null && CB_MULTIPART_BLOCK_ID.equals(blockId.toString())) {
            return true;
        }

        if (rawBeTag == null) {
            return false;
        }

        String beId = rawBeTag.getString(NBT_ID);

        return CB_MULTIPART_BE_ID.equals(beId)
                || (beId.isBlank() && rawBeTag.contains(NBT_PARTS, Tag.TAG_LIST));
    }

    private static boolean hasMetadata(@Nullable CompoundTag blockMetadata) {
        return blockMetadata != null && blockMetadata.contains(META_KEY, Tag.TAG_COMPOUND);
    }

    private static ListTag getStoredParts(
            @Nullable CompoundTag rawBeTag,
            @Nullable CompoundTag blockMetadata) {
        if (hasMetadata(blockMetadata)) {
            CompoundTag metadata = blockMetadata.getCompound(META_KEY);

            if (metadata.contains(META_PARTS, Tag.TAG_LIST)) {
                return copyList(metadata.getList(META_PARTS, Tag.TAG_COMPOUND));
            }
        }

        if (rawBeTag == null || !rawBeTag.contains(NBT_PARTS, Tag.TAG_LIST)) {
            return new ListTag();
        }

        return copyList(rawBeTag.getList(NBT_PARTS, Tag.TAG_COMPOUND));
    }

    private static CompoundTag createMultipartPlacementTag(ListTag parts) {
        CompoundTag tag = new CompoundTag();

        tag.putString(NBT_ID, CB_MULTIPART_BE_ID);
        tag.put(NBT_PARTS, copyList(parts));

        return tag;
    }

    private static ItemStack getPartCostStack(CompoundTag partTag) {
        ItemStack directStack = NbtUtil.tryReadSavedItemStack(partTag);

        if (!directStack.isEmpty()) {
            return directStack;
        }

        for (String key : COMMON_ITEM_KEYS) {
            if (!partTag.contains(key)) {
                continue;
            }

            Tag tag = partTag.get(key);

            if (tag instanceof CompoundTag compound) {
                ItemStack nestedStack = NbtUtil.tryReadSavedItemStack(compound);

                if (!nestedStack.isEmpty()) {
                    return nestedStack;
                }

                ItemStack byNestedId = getItemStackFromIdTag(compound);

                if (!byNestedId.isEmpty()) {
                    return byNestedId;
                }
            }

            if (tag != null && tag.getId() == Tag.TAG_STRING) {
                ItemStack byString = getItemStackFromId(partTag.getString(key));

                if (!byString.isEmpty()) {
                    return byString;
                }
            }
        }

        return getItemStackFromIdTag(partTag);
    }

    private static ItemStack getItemStackFromIdTag(CompoundTag tag) {
        if (!tag.contains(NBT_ID, Tag.TAG_STRING)) {
            return ItemStack.EMPTY;
        }

        return getItemStackFromId(tag.getString(NBT_ID));
    }

    private static ItemStack getItemStackFromId(String rawId) {
        if (rawId == null || rawId.isBlank()) {
            return ItemStack.EMPTY;
        }

        ResourceLocation itemId = ResourceLocation.tryParse(rawId);

        if (itemId == null) {
            return ItemStack.EMPTY;
        }

        Item item = ForgeRegistries.ITEMS.getValue(itemId);

        if (item == null || item == Items.AIR) {
            return ItemStack.EMPTY;
        }

        return new ItemStack(item);
    }

    private static ItemStack normalizeSingle(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return ItemStack.EMPTY;
        }

        ItemStack copy = stack.copy();

        copy.setCount(1);
        copy.setTag(null);

        return copy;
    }

    private static ItemStack normalizeCostStack(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return ItemStack.EMPTY;
        }

        ItemStack copy = stack.copy();

        copy.setCount(Math.max(1, copy.getCount()));
        copy.setTag(null);

        return copy;
    }

    private static ListTag copyList(ListTag input) {
        ListTag out = new ListTag();

        for (Tag tag : input) {
            out.add(tag.copy());
        }

        return out;
    }

    private static void refreshMultipartConnections(
            ServerLevel level,
            BlockPos pos,
            BlockState state) {
        Block block = state.getBlock();

        level.updateNeighborsAt(pos, block);
        level.updateNeighbourForOutputSignal(pos, block);
        level.blockUpdated(pos, block);

        for (Direction dir : Direction.values()) {
            BlockPos neighborPos = pos.relative(dir);
            BlockState neighborState = level.getBlockState(neighborPos);

            level.updateNeighborsAt(neighborPos, block);

            neighborState.neighborChanged(
                    level,
                    neighborPos,
                    block,
                    pos,
                    false);
        }
    }

    @Nullable
    private static CompoundTag saveCurrentTag(@Nullable BlockEntity be) {
        if (be == null) {
            return null;
        }

        try {
            return be.saveWithFullMetadata();
        } catch (Throwable ignored) {
            return null;
        }
    }
}
