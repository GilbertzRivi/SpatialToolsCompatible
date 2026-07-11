package net.oktawia.spatialtoolscmp.logic.extensions;

import appeng.api.upgrades.IUpgradeInventory;
import appeng.api.upgrades.IUpgradeableObject;
import appeng.blockentity.AEBaseBlockEntity;
import appeng.blockentity.networking.CableBusBlockEntity;
import appeng.util.SettingsFrom;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.oktawia.spatialtoolscmp.items.AbstractStructureCaptureToolItem;
import net.oktawia.spatialtoolscmp.items.helpers.ClonerBlockPlacer;
import net.oktawia.spatialtoolscmp.logic.ClonerPasteContext;
import net.oktawia.spatialtoolscmp.logic.PlacementPlan;
import net.oktawia.spatialtoolscmp.logic.StructureCloneExtension;
import net.oktawia.spatialtoolscmp.util.NbtUtil;
import net.oktawia.spatialtoolscmp.util.StructureToolKeys;
import net.oktawia.spatialtoolscmp.util.TemplateUtil;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public final class AE2ClonerExtension implements StructureCloneExtension {

    private static final String AE2_CABLE_BUS_ID = "ae2:cable_bus";

    @Override
    public boolean collectMetadata(
            ServerLevel level,
            BlockPos pos,
            BlockEntity be,
            @Nullable CompoundTag rawBeTag,
            AbstractStructureCaptureToolItem.RequirementSink requirements,
            CompoundTag blockEntry
    ) {
        boolean hasAnyData = false;

        if (isAe2CableBusTag(rawBeTag)) {
            collectCableBusRequirements(rawBeTag, requirements);
        }

        if (be instanceof AEBaseBlockEntity abbe) {
            CompoundTag settings = new CompoundTag();

            try {
                abbe.exportSettings(SettingsFrom.MEMORY_CARD, settings, null);
            } catch (Throwable ignored) {
            }

            if (!settings.isEmpty()) {
                blockEntry.put(StructureToolKeys.CLONE_KEY_SETTINGS, settings);
                hasAnyData = true;
            }
        }

        if (be instanceof IUpgradeableObject upgradable) {
            IUpgradeInventory upgrades = upgradable.getUpgrades();

            if (!upgrades.isEmpty()) {
                for (ItemStack upgrade : upgrades) {
                    if (!upgrade.isEmpty()) {
                        requirements.add(upgrade);
                    }
                }

                upgrades.writeToNBT(blockEntry, StructureToolKeys.CLONE_KEY_UPGRADES);
                hasAnyData = true;
            }
        }

        if (be instanceof CableBusBlockEntity cableBus) {
            CompoundTag partsTag = new CompoundTag();

            for (Direction dir : Direction.values()) {
                var part = cableBus.getPart(dir);

                if (part == null) {
                    continue;
                }

                CompoundTag partEntry = new CompoundTag();
                boolean hasPartData = false;

                CompoundTag partSettings = new CompoundTag();

                try {
                    part.exportSettings(SettingsFrom.MEMORY_CARD, partSettings);
                } catch (Throwable ignored) {
                }

                if (!partSettings.isEmpty()) {
                    partEntry.put(StructureToolKeys.CLONE_KEY_SETTINGS, partSettings);
                    hasPartData = true;
                }

                if (part instanceof IUpgradeableObject partUpgradable) {
                    IUpgradeInventory upgrades = partUpgradable.getUpgrades();

                    if (!upgrades.isEmpty()) {
                        for (ItemStack upgrade : upgrades) {
                            if (!upgrade.isEmpty()) {
                                requirements.add(upgrade);
                            }
                        }

                        upgrades.writeToNBT(partEntry, StructureToolKeys.CLONE_KEY_UPGRADES);
                        hasPartData = true;
                    }
                }

                if (hasPartData) {
                    partsTag.put(TemplateUtil.directionKey(dir), partEntry);
                }
            }

            if (!partsTag.isEmpty()) {
                blockEntry.put(StructureToolKeys.CLONE_KEY_PARTS, partsTag);
                hasAnyData = true;
            }
        }

        return hasAnyData;
    }

    @Override
    public boolean handlesRequirements(BlockState state, @Nullable CompoundTag rawBeTag) {
        return isAe2CableBusTag(rawBeTag);
    }

    @Override
    public Optional<PlacementPlan> buildPlacementPlan(
            ServerLevel level,
            Player player,
            BlockState state,
            @Nullable CompoundTag rawBeTag,
            @Nullable CompoundTag blockMetadata,
            ClonerPasteContext ctx
    ) {
        if (isAe2CableBusTag(rawBeTag)) {
            if (rawBeTag == null) {
                return Optional.of(PlacementPlan.none());
            }

            return Optional.of(buildCableBusPlacementPlan(
                    player,
                    state,
                    rawBeTag,
                    blockMetadata,
                    ctx
            ));
        }

        if (blockMetadata == null) {
            return Optional.empty();
        }

        if (!blockMetadata.contains(StructureToolKeys.CLONE_KEY_SETTINGS, Tag.TAG_COMPOUND)
                && !blockMetadata.contains(StructureToolKeys.CLONE_KEY_UPGRADES)
                && !blockMetadata.contains(StructureToolKeys.CLONE_KEY_PARTS, Tag.TAG_COMPOUND)) {
            return Optional.empty();
        }

        return Optional.of(buildAe2BlockPlacementPlan(
                player,
                state,
                blockMetadata,
                ctx
        ));
    }

    @Override
    public void onBlockPlaced(
            ServerLevel level,
            BlockPos pos,
            @Nullable BlockEntity be,
            @Nullable CompoundTag blockMetadata
    ) {
        if (blockMetadata == null || be == null) {
            return;
        }

        if (be instanceof CableBusBlockEntity cableBus) {
            applyAe2CableBusMetadataAfterPlacement(level, pos, cableBus, blockMetadata);
            return;
        }

        applyAe2BlockMetadataAfterPlacement(level, pos, be, blockMetadata);
    }

    private static PlacementPlan buildCableBusPlacementPlan(
            Player player,
            BlockState stateToPlace,
            CompoundTag rawBeTag,
            @Nullable CompoundTag blockMetadata,
            ClonerPasteContext ctx
    ) {
        CompoundTag filtered = createMinimalAeCableBusBaseTag(rawBeTag);
        List<ItemStack> costs = new ArrayList<>();
        Map<Item, Integer> reserved = new HashMap<>();
        boolean keptAnything = false;

        for (String key : StructureToolKeys.AE2_CABLE_BUS_KEYS) {
            if (!rawBeTag.contains(key, Tag.TAG_COMPOUND)) {
                continue;
            }

            CompoundTag rawSection = rawBeTag.getCompound(key);
            CompoundTag minimalSection = createMinimalAePartTag(rawSection);

            if (minimalSection.isEmpty()) {
                continue;
            }

            ItemStack representative = NbtUtil.tryReadSavedItemStack(rawSection);

            if (representative.isEmpty()) {
                representative = NbtUtil.tryReadSavedItemStack(minimalSection);
            }

            Map<Item, Integer> trialReserved = new HashMap<>(reserved);
            List<ItemStack> sectionCosts = new ArrayList<>();

            if (!representative.isEmpty()) {
                ItemStack representativeCost = normalizeSingle(representative);

                if (!player.isCreative()
                        && !ctx.canReserveForPaste(trialReserved, representativeCost, 1)) {
                    continue;
                }

                sectionCosts.add(representativeCost);
            } else if (!player.isCreative()) {
                continue;
            }

            if (!addAe2PartUpgradeCosts(
                    blockMetadata,
                    key,
                    player,
                    ctx,
                    trialReserved,
                    sectionCosts
            )) {
                continue;
            }

            reserved.clear();
            reserved.putAll(trialReserved);

            filtered.put(key, minimalSection);
            costs.addAll(sectionCosts);
            keptAnything = true;
        }

        for (String key : StructureToolKeys.AE2_FACADE_KEYS) {
            if (!rawBeTag.contains(key, Tag.TAG_COMPOUND)) {
                continue;
            }

            CompoundTag facadeSection = rawBeTag.getCompound(key);
            ItemStack facade = NbtUtil.tryReadSavedItemStack(facadeSection);

            if (facade.isEmpty()) {
                continue;
            }

            ItemStack facadeCost = normalizeSinglePreservingTag(facade);

            Map<Item, Integer> trialReserved = new HashMap<>(reserved);

            if (!player.isCreative() && !ctx.canReserveForPaste(trialReserved, facadeCost, 1)) {
                continue;
            }

            reserved.clear();
            reserved.putAll(trialReserved);

            filtered.put(key, facadeSection.copy());
            costs.add(facadeCost);
            keptAnything = true;
        }

        return keptAnything
                ? new PlacementPlan(true, stateToPlace, filtered, costs)
                : PlacementPlan.none();
    }

    private static PlacementPlan buildAe2BlockPlacementPlan(
            Player player,
            BlockState stateToPlace,
            CompoundTag blockMetadata,
            ClonerPasteContext ctx
    ) {
        List<ItemStack> costs = new ArrayList<>();
        Map<Item, Integer> reserved = new HashMap<>();

        ItemStack baseItem = normalizeSingle(ctx.getRequiredBlockItem(stateToPlace));

        if (!baseItem.isEmpty()) {
            if (!player.isCreative() && !ctx.canReserveForPaste(reserved, baseItem, 1)) {
                return PlacementPlan.none();
            }

            costs.add(baseItem);
        } else if (!player.isCreative()) {
            return PlacementPlan.none();
        }

        if (blockMetadata.contains(StructureToolKeys.CLONE_KEY_UPGRADES)) {
            if (!addNestedSavedStackCosts(
                    blockMetadata.get(StructureToolKeys.CLONE_KEY_UPGRADES),
                    player,
                    ctx,
                    reserved,
                    costs
            )) {
                return PlacementPlan.none();
            }
        }

        return new PlacementPlan(true, stateToPlace, null, costs);
    }

    private static boolean addAe2PartUpgradeCosts(
            @Nullable CompoundTag blockMetadata,
            String sideKey,
            Player player,
            ClonerPasteContext ctx,
            Map<Item, Integer> reserved,
            List<ItemStack> costs
    ) {
        if (blockMetadata == null) {
            return true;
        }

        if (!blockMetadata.contains(StructureToolKeys.CLONE_KEY_PARTS, Tag.TAG_COMPOUND)) {
            return true;
        }

        CompoundTag partsTag = blockMetadata.getCompound(StructureToolKeys.CLONE_KEY_PARTS);

        if (!partsTag.contains(sideKey, Tag.TAG_COMPOUND)) {
            return true;
        }

        CompoundTag partEntry = partsTag.getCompound(sideKey);

        if (!partEntry.contains(StructureToolKeys.CLONE_KEY_UPGRADES)) {
            return true;
        }

        return addNestedSavedStackCosts(
                partEntry.get(StructureToolKeys.CLONE_KEY_UPGRADES),
                player,
                ctx,
                reserved,
                costs
        );
    }

    private static boolean addNestedSavedStackCosts(
            @Nullable Tag tag,
            Player player,
            ClonerPasteContext ctx,
            Map<Item, Integer> reserved,
            List<ItemStack> costs
    ) {
        if (tag == null) {
            return true;
        }

        List<ItemStack> found = new ArrayList<>();
        collectNestedSavedItemStacks(tag, found::add);

        for (ItemStack stack : found) {
            ItemStack normalized = normalizeCountPreserving(stack);

            if (normalized.isEmpty()) {
                continue;
            }

            int amount = Math.max(1, normalized.getCount());

            if (!player.isCreative()
                    && !ctx.canReserveForPaste(reserved, normalized, amount)) {
                return false;
            }

            costs.add(normalized);
        }

        return true;
    }

    private static void applyAe2BlockMetadataAfterPlacement(
            ServerLevel level,
            BlockPos worldPos,
            BlockEntity be,
            CompoundTag blockMetadata
    ) {
        boolean changed = false;

        if (be instanceof AEBaseBlockEntity abbe
                && blockMetadata.contains(StructureToolKeys.CLONE_KEY_SETTINGS, Tag.TAG_COMPOUND)) {
            try {
                abbe.importSettings(
                        SettingsFrom.MEMORY_CARD,
                        blockMetadata.getCompound(StructureToolKeys.CLONE_KEY_SETTINGS),
                        null
                );
                changed = true;
            } catch (Throwable ignored) {
            }
        }

        if (be instanceof IUpgradeableObject upgradable
                && blockMetadata.contains(StructureToolKeys.CLONE_KEY_UPGRADES)) {
            try {
                upgradable.getUpgrades().readFromNBT(blockMetadata, StructureToolKeys.CLONE_KEY_UPGRADES);
                changed = true;
            } catch (Throwable ignored) {
            }
        }

        if (!changed) {
            return;
        }

        be.setChanged();

        BlockState state = level.getBlockState(worldPos);
        level.sendBlockUpdated(worldPos, state, state, 3);
    }

    private static void applyAe2CableBusMetadataAfterPlacement(
            ServerLevel level,
            BlockPos worldPos,
            CableBusBlockEntity cableBus,
            CompoundTag blockMetadata
    ) {
        if (!blockMetadata.contains(StructureToolKeys.CLONE_KEY_PARTS, Tag.TAG_COMPOUND)) {
            cableBus.setChanged();

            BlockState state = level.getBlockState(worldPos);
            level.sendBlockUpdated(worldPos, state, state, 3);
            return;
        }

        CompoundTag partsTag = blockMetadata.getCompound(StructureToolKeys.CLONE_KEY_PARTS);

        for (Direction dir : Direction.values()) {
            String key = TemplateUtil.directionKey(dir);

            if (!partsTag.contains(key, Tag.TAG_COMPOUND)) {
                continue;
            }

            CompoundTag partEntry = partsTag.getCompound(key);
            var part = cableBus.getPart(dir);

            if (part == null) {
                continue;
            }

            if (partEntry.contains(StructureToolKeys.CLONE_KEY_SETTINGS, Tag.TAG_COMPOUND)) {
                try {
                    part.importSettings(
                            SettingsFrom.MEMORY_CARD,
                            partEntry.getCompound(StructureToolKeys.CLONE_KEY_SETTINGS),
                            null
                    );
                } catch (Throwable ignored) {
                }
            }

            if (part instanceof IUpgradeableObject partUpgradable
                    && partEntry.contains(StructureToolKeys.CLONE_KEY_UPGRADES)) {
                try {
                    partUpgradable.getUpgrades().readFromNBT(partEntry, StructureToolKeys.CLONE_KEY_UPGRADES);
                } catch (Throwable ignored) {
                }
            }
        }

        cableBus.setChanged();

        BlockState state = level.getBlockState(worldPos);
        level.sendBlockUpdated(worldPos, state, state, 3);
    }

    private static void collectCableBusRequirements(
            @Nullable CompoundTag rawBeTag,
            AbstractStructureCaptureToolItem.RequirementSink requirements
    ) {
        if (rawBeTag == null) {
            return;
        }

        for (String key : StructureToolKeys.AE2_CABLE_BUS_KEYS) {
            if (!rawBeTag.contains(key)) {
                continue;
            }

            collectNestedSavedItemStacks(rawBeTag.get(key), requirements);
        }

        for (String key : StructureToolKeys.AE2_FACADE_KEYS) {
            if (!rawBeTag.contains(key)) {
                continue;
            }

            collectNestedSavedItemStacks(rawBeTag.get(key), requirements);
        }
    }

    private static void collectNestedSavedItemStacks(
            @Nullable Tag tag,
            AbstractStructureCaptureToolItem.RequirementSink requirements
    ) {
        if (tag == null) {
            return;
        }

        if (tag instanceof CompoundTag compoundTag) {
            ItemStack stack = NbtUtil.tryReadSavedItemStack(compoundTag);

            if (!stack.isEmpty()) {
                requirements.add(stack);
                return;
            }

            for (String key : compoundTag.getAllKeys()) {
                collectNestedSavedItemStacks(compoundTag.get(key), requirements);
            }

            return;
        }

        if (tag instanceof ListTag listTag) {
            for (int i = 0; i < listTag.size(); i++) {
                collectNestedSavedItemStacks(listTag.get(i), requirements);
            }
        }
    }

    private static boolean isAe2CableBusTag(@Nullable CompoundTag rawBeTag) {
        if (rawBeTag == null) {
            return false;
        }

        String id = rawBeTag.getString("id");

        if (AE2_CABLE_BUS_ID.equals(id)) {
            return true;
        }

        if (!id.isBlank()) {
            return false;
        }

        for (String key : StructureToolKeys.AE2_CABLE_BUS_KEYS) {
            if (rawBeTag.contains(key)) {
                return true;
            }
        }

        return false;
    }

    private static CompoundTag createMinimalAeCableBusBaseTag(@Nullable CompoundTag rawBeTag) {
        if (rawBeTag == null) {
            return new CompoundTag();
        }

        CompoundTag out = new CompoundTag();
        NbtUtil.copyStringIfPresent(rawBeTag, out, "id");

        return out;
    }

    private static CompoundTag createMinimalAePartTag(@Nullable CompoundTag rawPartTag) {
        if (rawPartTag == null) {
            return new CompoundTag();
        }

        CompoundTag out = new CompoundTag();

        NbtUtil.copyStringIfPresent(rawPartTag, out, "id");
        NbtUtil.copyByteIfPresent(rawPartTag, out, "output");

        return out;
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

    private static ItemStack normalizeSinglePreservingTag(ItemStack stack) {
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }

        ItemStack copy = stack.copy();

        copy.setCount(1);

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

    @Override
    public boolean collectUndoRefunds(
            ServerLevel level,
            BlockPos pos,
            BlockState state,
            @Nullable BlockEntity be,
            List<ItemStack> refunds
    ) {
        CompoundTag currentTag = saveCurrentTag(be);

        if (be instanceof CableBusBlockEntity || isAe2CableBusTag(currentTag)) {
            collectCableBusUndoRefunds(currentTag, refunds);
            return true;
        }

        if (!(be instanceof AEBaseBlockEntity) && !(be instanceof IUpgradeableObject)) {
            return false;
        }

        ItemStack baseItem = normalizeSingle(ClonerBlockPlacer.getRequiredBlockItem(state));

        if (!baseItem.isEmpty()) {
            refunds.add(baseItem);
        }

        if (be instanceof IUpgradeableObject upgradable) {
            IUpgradeInventory upgrades = upgradable.getUpgrades();

            if (!upgrades.isEmpty()) {
                for (ItemStack upgrade : upgrades) {
                    if (!upgrade.isEmpty()) {
                        refunds.add(upgrade.copy());
                    }
                }
            }
        }

        return true;
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

    private static void collectCableBusUndoRefunds(
            @Nullable CompoundTag rawBeTag,
            List<ItemStack> refunds
    ) {
        if (rawBeTag == null) {
            return;
        }

        for (String key : StructureToolKeys.AE2_CABLE_BUS_KEYS) {
            if (!rawBeTag.contains(key)) {
                continue;
            }

            collectNestedSavedItemStacks(rawBeTag.get(key), refunds);
        }

        for (String key : StructureToolKeys.AE2_FACADE_KEYS) {
            if (!rawBeTag.contains(key)) {
                continue;
            }

            collectNestedSavedItemStacks(rawBeTag.get(key), refunds);
        }
    }

    private static void collectNestedSavedItemStacks(
            @Nullable Tag tag,
            List<ItemStack> refunds
    ) {
        if (tag == null) {
            return;
        }

        if (tag instanceof CompoundTag compoundTag) {
            ItemStack stack = NbtUtil.tryReadSavedItemStack(compoundTag);

            if (!stack.isEmpty()) {
                refunds.add(stack);
                return;
            }

            for (String key : compoundTag.getAllKeys()) {
                collectNestedSavedItemStacks(compoundTag.get(key), refunds);
            }

            return;
        }

        if (tag instanceof ListTag listTag) {
            for (int i = 0; i < listTag.size(); i++) {
                collectNestedSavedItemStacks(listTag.get(i), refunds);
            }
        }
    }
}