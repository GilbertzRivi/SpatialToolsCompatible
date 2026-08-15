package net.oktawia.spatialtoolscmp.logic.extensions;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.cyclops.integrateddynamics.api.part.IPartContainer;
import org.cyclops.integrateddynamics.api.part.IPartState;
import org.cyclops.integrateddynamics.api.part.IPartType;
import org.cyclops.integrateddynamics.core.blockentity.BlockEntityMultipartTicking;
import org.cyclops.integrateddynamics.core.helper.CableHelpers;
import org.cyclops.integrateddynamics.core.part.PartTypes;
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
import net.oktawia.spatialtoolscmp.logic.StructurePasteExtension;
import net.oktawia.spatialtoolscmp.util.StructureToolKeys;
import net.oktawia.spatialtoolscmp.util.TemplateUtil;

public final class IntegratedDynamicsClonerExtension implements StructureCloneExtension, StructurePasteExtension {

    private static final String CABLE_ITEM_ID = "integrateddynamics:cable";
    private static final String FACADE_ITEM_ID = "integrateddynamics:facade";

    private final IntegratedDynamicsIdRemapper idRemapper = new IntegratedDynamicsIdRemapper();

    @Override
    public boolean handlesRequirements(BlockState state, @Nullable CompoundTag rawBeTag) {
        return isCable(state, rawBeTag);
    }

    @Override
    public void onBeforePaste(
            ServerLevel level,
            Player player,
            List<TemplateUtil.BlockInfo> blocks) {
        idRemapper.prepare(blocks);
    }

    @Override
    public boolean collectMetadata(
            ServerLevel level,
            BlockPos pos,
            BlockEntity be,
            @Nullable CompoundTag rawBeTag,
            AbstractStructureCaptureToolItem.RequirementSink requirements,
            CompoundTag blockEntry) {
        CompoundTag tag = rawBeTag != null ? rawBeTag : saveCurrentTag(be);

        if (!isCable(level.getBlockState(pos), tag) || tag == null) {
            return false;
        }

        if (isRealCable(tag)) {
            addIfPresent(requirements, itemStackById(CABLE_ITEM_ID));
        }

        for (ItemStack cost : facadeCosts(tag)) {
            requirements.add(cost);
        }

        for (int i = 0; i < partsList(tag).size(); i++) {
            for (ItemStack cost : partCosts(partsList(tag).getCompound(i))) {
                requirements.add(cost);
            }
        }

        return false;
    }

    @Override
    public Optional<PlacementPlan> buildPlacementPlan(
            ServerLevel level,
            Player player,
            BlockState state,
            @Nullable CompoundTag rawBeTag,
            @Nullable CompoundTag blockMetadata,
            ClonerPasteContext ctx) {
        if (rawBeTag == null || !isCable(state, rawBeTag)) {
            return Optional.empty();
        }

        boolean creative = player.isCreative();
        boolean realCable = isRealCable(rawBeTag);

        CompoundTag placementTag = rawBeTag.copy();
        Map<Item, Integer> reserved = new LinkedHashMap<>();
        List<ItemStack> costs = new ArrayList<>();

        if (realCable) {
            ItemStack cable = itemStackById(CABLE_ITEM_ID);

            if (cable.isEmpty()) {
                return Optional.of(PlacementPlan.none());
            }

            if (!creative && !reserveAll(ctx, reserved, List.of(cable))) {
                return Optional.of(PlacementPlan.none());
            }

            costs.add(cable);
        }

        List<ItemStack> facadeCosts = facadeCosts(placementTag);

        if (!facadeCosts.isEmpty()) {
            if (creative || reserveAll(ctx, reserved, facadeCosts)) {
                costs.addAll(facadeCosts);
            } else {
                placementTag.remove(StructureToolKeys.INTDYN_KEY_FACADE);
            }
        }

        ListTag sourceParts = partsList(placementTag);
        ListTag keptParts = new ListTag();

        for (int i = 0; i < sourceParts.size(); i++) {
            CompoundTag partTag = sourceParts.getCompound(i);
            List<ItemStack> partCosts = partCosts(partTag);

            if (partCosts.isEmpty()) {
                continue;
            }

            if (!creative && !reserveAll(ctx, reserved, partCosts)) {
                continue;
            }

            costs.addAll(partCosts);
            keptParts.add(partTag.copy());
        }

        setPartsList(placementTag, keptParts);
        idRemapper.apply(placementTag);

        if (!realCable && keptParts.isEmpty()) {
            return Optional.of(PlacementPlan.none());
        }

        return Optional.of(new PlacementPlan(true, state, placementTag, costs));
    }

    @Override
    public void onBlockPlaced(
            ServerLevel level,
            Player player,
            BlockPos pos,
            @Nullable BlockEntity be,
            @Nullable CompoundTag blockMetadata) {
        if (!(be instanceof BlockEntityMultipartTicking cable)) {
            return;
        }

        refreshConnections(level, pos);
        CableHelpers.onCableAdded(level, pos);
        attachParts(level, pos, cable);
    }

    @Override
    public void onTemplatePasted(ServerLevel level, BlockPos placementOrigin, CompoundTag templateTag) {
        List<BlockPos> cables = new ArrayList<>();

        for (TemplateUtil.BlockInfo info : TemplateUtil.parseRawBlocksFromTag(templateTag)) {
            BlockPos worldPos = placementOrigin.offset(info.pos()).immutable();

            if (level.getBlockEntity(worldPos) instanceof BlockEntityMultipartTicking) {
                cables.add(worldPos);
            }
        }

        for (BlockPos pos : cables) {
            refreshConnections(level, pos);
        }

        for (BlockPos pos : cables) {
            CableHelpers.onCableAdded(level, pos);
        }

        for (BlockPos pos : cables) {
            if (level.getBlockEntity(pos) instanceof BlockEntityMultipartTicking cable) {
                attachParts(level, pos, cable);
            }
        }
    }

    private static void refreshConnections(ServerLevel level, BlockPos pos) {
        try {
            CableHelpers.updateConnections(level, pos, null);
        } catch (Throwable ignored) {
        }
    }

    private static void attachParts(ServerLevel level, BlockPos pos, BlockEntityMultipartTicking cable) {
        IPartContainer container = cable.getPartContainer();
        Map<Direction, IPartType<?, ?>> parts = new LinkedHashMap<>(container.getParts());

        for (Map.Entry<Direction, IPartType<?, ?>> entry : parts.entrySet()) {
            try {
                IPartState<?> partState = container.getPartState(entry.getKey());
                attachPart(container, entry.getKey(), entry.getValue(), partState);
            } catch (Throwable ignored) {
            }
        }

        cable.setChanged();

        BlockState state = level.getBlockState(pos);
        level.sendBlockUpdated(pos, state, state, Block.UPDATE_ALL);
    }

    @Override
    public boolean collectUndoRefunds(
            ServerLevel level,
            BlockPos pos,
            BlockState state,
            @Nullable BlockEntity be,
            List<ItemStack> refunds) {
        CompoundTag tag = saveCurrentTag(be);

        if (!isCable(state, tag) || tag == null) {
            return false;
        }

        if (isRealCable(tag)) {
            ItemStack cable = itemStackById(CABLE_ITEM_ID);

            if (!cable.isEmpty()) {
                refunds.add(cable);
            }
        }

        refunds.addAll(facadeCosts(tag));

        ListTag parts = partsList(tag);

        for (int i = 0; i < parts.size(); i++) {
            refunds.addAll(partCosts(parts.getCompound(i)));
        }

        return true;
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static void attachPart(
            IPartContainer container,
            Direction side,
            IPartType partType,
            IPartState partState) {
        container.setPart(side, partType, partState);
    }

    private static boolean isCable(BlockState state, @Nullable CompoundTag rawBeTag) {
        ResourceLocation blockId = ForgeRegistries.BLOCKS.getKey(state.getBlock());

        if (blockId != null && StructureToolKeys.INTDYN_CABLE_BLOCK_ID.equals(blockId.toString())) {
            return true;
        }

        return rawBeTag != null && StructureToolKeys.INTDYN_CABLE_BE_ID.equals(rawBeTag.getString("id"));
    }

    private static boolean isRealCable(CompoundTag tag) {
        return !tag.contains(StructureToolKeys.INTDYN_KEY_REAL_CABLE)
                || tag.getBoolean(StructureToolKeys.INTDYN_KEY_REAL_CABLE);
    }

    private static ListTag partsList(CompoundTag beTag) {
        if (!beTag.contains(StructureToolKeys.INTDYN_KEY_PART_CONTAINER, Tag.TAG_COMPOUND)) {
            return new ListTag();
        }

        return beTag
                .getCompound(StructureToolKeys.INTDYN_KEY_PART_CONTAINER)
                .getList(StructureToolKeys.INTDYN_KEY_PARTS, Tag.TAG_COMPOUND);
    }

    private static void setPartsList(CompoundTag beTag, ListTag parts) {
        CompoundTag container = beTag.contains(StructureToolKeys.INTDYN_KEY_PART_CONTAINER, Tag.TAG_COMPOUND)
                ? beTag.getCompound(StructureToolKeys.INTDYN_KEY_PART_CONTAINER).copy()
                : new CompoundTag();

        container.put(StructureToolKeys.INTDYN_KEY_PARTS, parts);
        beTag.put(StructureToolKeys.INTDYN_KEY_PART_CONTAINER, container);
    }

    private static List<ItemStack> partCosts(CompoundTag partTag) {
        ItemStack partItem = partItemStack(partTag.getString(StructureToolKeys.INTDYN_KEY_PART_TYPE));

        if (partItem.isEmpty()) {
            return List.of();
        }

        List<ItemStack> costs = new ArrayList<>();
        costs.add(partItem);
        collectStoredItemStacks(partTag, costs);

        return costs;
    }

    private static List<ItemStack> facadeCosts(CompoundTag beTag) {
        if (!beTag.contains(StructureToolKeys.INTDYN_KEY_FACADE, Tag.TAG_COMPOUND)) {
            return List.of();
        }

        CompoundTag facadeTag = beTag.getCompound(StructureToolKeys.INTDYN_KEY_FACADE);

        if (facadeTag.isEmpty()) {
            return List.of();
        }

        ItemStack facadeItem = itemStackById(FACADE_ITEM_ID);

        if (facadeItem.isEmpty()) {
            return List.of();
        }

        List<ItemStack> costs = new ArrayList<>();
        costs.add(facadeItem);

        ItemStack facadeBlock = facadeBlockItemStack(facadeTag);

        if (!facadeBlock.isEmpty()) {
            costs.add(facadeBlock);
        }

        return costs;
    }

    private static ItemStack facadeBlockItemStack(CompoundTag facadeTag) {
        ResourceLocation blockId = ResourceLocation.tryParse(facadeTag.getString("Name"));

        if (blockId == null) {
            return ItemStack.EMPTY;
        }

        Block block = ForgeRegistries.BLOCKS.getValue(blockId);

        if (block == null) {
            return ItemStack.EMPTY;
        }

        Item item = block.asItem();

        return item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item);
    }

    private static ItemStack partItemStack(String partTypeName) {
        ResourceLocation partTypeId = ResourceLocation.tryParse(partTypeName);

        if (partTypeId == null) {
            return ItemStack.EMPTY;
        }

        IPartType<?, ?> partType;

        try {
            partType = PartTypes.REGISTRY.getPartType(partTypeId);
        } catch (Throwable ignored) {
            return ItemStack.EMPTY;
        }

        if (partType == null) {
            return ItemStack.EMPTY;
        }

        Item item = partType.getItem();

        return item == null || item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item);
    }

    private static void collectStoredItemStacks(@Nullable Tag tag, List<ItemStack> out) {
        if (tag instanceof CompoundTag compound) {
            ItemStack stack = readStoredItemStack(compound);

            if (!stack.isEmpty()) {
                out.add(stack);
                return;
            }

            for (String key : compound.getAllKeys()) {
                collectStoredItemStacks(compound.get(key), out);
            }

            return;
        }

        if (tag instanceof ListTag list) {
            for (Tag child : list) {
                collectStoredItemStacks(child, out);
            }
        }
    }

    private static ItemStack readStoredItemStack(CompoundTag tag) {
        if (!tag.contains("id", Tag.TAG_STRING) || !tag.contains("Count", Tag.TAG_ANY_NUMERIC)) {
            return ItemStack.EMPTY;
        }

        ResourceLocation itemId = ResourceLocation.tryParse(tag.getString("id"));

        if (itemId == null || !ForgeRegistries.ITEMS.containsKey(itemId)) {
            return ItemStack.EMPTY;
        }

        ItemStack stack = ItemStack.of(tag);

        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }

        ItemStack cost = stack.copy();
        cost.setTag(null);

        return cost;
    }

    private static ItemStack itemStackById(String id) {
        ResourceLocation itemId = ResourceLocation.tryParse(id);

        if (itemId == null) {
            return ItemStack.EMPTY;
        }

        Item item = ForgeRegistries.ITEMS.getValue(itemId);

        return item == null || item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item);
    }

    private static boolean reserveAll(
            ClonerPasteContext ctx,
            Map<Item, Integer> reserved,
            List<ItemStack> wanted) {
        Map<Item, Integer> trial = new LinkedHashMap<>(reserved);

        for (ItemStack stack : wanted) {
            if (stack.isEmpty()) {
                continue;
            }

            if (!ctx.canReserveForPaste(trial, stack, Math.max(1, stack.getCount()))) {
                return false;
            }
        }

        reserved.clear();
        reserved.putAll(trial);

        return true;
    }

    private static void addIfPresent(AbstractStructureCaptureToolItem.RequirementSink requirements, ItemStack stack) {
        if (!stack.isEmpty()) {
            requirements.add(stack);
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
