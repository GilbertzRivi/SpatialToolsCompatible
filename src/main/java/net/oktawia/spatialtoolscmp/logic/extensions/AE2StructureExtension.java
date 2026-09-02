package net.oktawia.spatialtoolscmp.logic.extensions;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.inventories.InternalInventory;
import appeng.api.parts.IPart;
import appeng.api.parts.IPartItem;
import appeng.api.upgrades.IUpgradeInventory;
import appeng.api.upgrades.IUpgradeableObject;
import appeng.blockentity.AEBaseBlockEntity;
import appeng.blockentity.networking.CableBusBlockEntity;
import appeng.blockentity.qnb.QuantumBridgeBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import appeng.util.SettingsFrom;

import net.oktawia.spatialtoolscmp.items.AbstractStructureCaptureToolItem;
import net.oktawia.spatialtoolscmp.items.helpers.ClonerBlockPlacer;
import net.oktawia.spatialtoolscmp.logic.ClonerPasteContext;
import net.oktawia.spatialtoolscmp.logic.PlacementPlan;
import net.oktawia.spatialtoolscmp.logic.StructureCloneExtension;
import net.oktawia.spatialtoolscmp.logic.StructurePasteExtension;
import net.oktawia.spatialtoolscmp.util.NbtUtil;
import net.oktawia.spatialtoolscmp.util.StructureToolKeys;
import net.oktawia.spatialtoolscmp.util.TemplateUtil;

public final class AE2StructureExtension implements StructureCloneExtension, StructurePasteExtension {

    private static final String AE2_CABLE_BUS_ID = "ae2:cable_bus";

    private static final String AE2_SETTINGS_UPGRADES_KEY = "upgrades";

    private static final int CRAZY_PROVIDER_BASE_SLOTS = 8 * 9;

    private static final int CRAZY_PROVIDER_SLOTS_PER_UPGRADE = 9;

    private static final String CRAZY_UPGRADES_BLOCK_KEY = "";

    private static final String AE2_QNB_INV_SLOT_PREFIX = "item";

    private static final int QNB_MAX_WAIT_TICKS = 40;

    private static final List<PendingQuantumBridgeInventory> PENDING_QNB = new CopyOnWriteArrayList<>();

    private static volatile boolean qnbTickRegistered = false;

    private final Map<String, Integer> plannedCrazyUpgrades = new HashMap<>();

    @Override
    public boolean collectMetadata(
            ServerLevel level,
            BlockPos pos,
            BlockEntity be,
            @Nullable CompoundTag rawBeTag,
            AbstractStructureCaptureToolItem.RequirementSink requirements,
            CompoundTag blockEntry) {
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

            settings.remove(AE2_SETTINGS_UPGRADES_KEY);
            dropSettingsCarriedByBlockState(be.getBlockState(), settings);
            addBlankPatternRequirements(be, requirements);

            if (!settings.isEmpty()) {
                blockEntry.put(StructureToolKeys.CLONE_KEY_SETTINGS, settings);
                hasAnyData = true;
            }

            CompoundTag crazySource = rawBeTag != null ? rawBeTag : saveCurrentTag(be);

            if (isCrazyProviderTag(crazySource, StructureToolKeys.CRAZYAE2_PROVIDER_BE_ID)
                    && storeCrazyUpgrades(crazySource, blockEntry, requirements)) {
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
            CompoundTag cableVisual = new CompoundTag();
            var centerPart = cableBus.getPart((Direction) null);

            if (centerPart != null) {
                try {
                    centerPart.writeVisualStateToNBT(cableVisual);
                } catch (Throwable ignored) {
                }
            }

            if (!cableVisual.isEmpty()) {
                blockEntry.put(StructureToolKeys.CLONE_KEY_AE2_CABLE_VISUAL, cableVisual);
                hasAnyData = true;
            }

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

                partSettings.remove(AE2_SETTINGS_UPGRADES_KEY);

                if (!partSettings.isEmpty()) {
                    partEntry.put(StructureToolKeys.CLONE_KEY_SETTINGS, partSettings);
                    hasPartData = true;
                }

                addBlankPatternRequirements(part, requirements);

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

                CompoundTag rawPartTag = rawPartSection(rawBeTag, dir);

                if (isCrazyProviderTag(rawPartTag, StructureToolKeys.CRAZYAE2_PROVIDER_PART_ID)
                        && storeCrazyUpgrades(rawPartTag, partEntry, requirements)) {
                    hasPartData = true;
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
            ClonerPasteContext ctx) {
        plannedCrazyUpgrades.clear();

        if (isAe2CableBusTag(rawBeTag)) {
            if (rawBeTag == null) {
                return Optional.of(PlacementPlan.none());
            }

            return Optional.of(buildCableBusPlacementPlan(
                    level,
                    player,
                    state,
                    rawBeTag,
                    blockMetadata,
                    ctx));
        }

        if (blockMetadata == null) {
            return Optional.empty();
        }

        if (!blockMetadata.contains(StructureToolKeys.CLONE_KEY_SETTINGS, Tag.TAG_COMPOUND)
                && !blockMetadata.contains(StructureToolKeys.CLONE_KEY_UPGRADES)
                && !blockMetadata.contains(StructureToolKeys.CLONE_KEY_CRAZY_UPGRADES)
                && !blockMetadata.contains(StructureToolKeys.CLONE_KEY_PARTS, Tag.TAG_COMPOUND)) {
            return Optional.empty();
        }

        return Optional.of(buildAe2BlockPlacementPlan(
                level,
                player,
                state,
                blockMetadata,
                ctx));
    }

    @Override
    public boolean applyToExistingBlock(
            ServerLevel level,
            Player player,
            BlockPos pos,
            BlockState stateToPlace,
            @Nullable CompoundTag rawBeTag,
            @Nullable CompoundTag blockMetadata,
            ClonerPasteContext ctx,
            List<ItemStack> consumedStacks) {
        plannedCrazyUpgrades.clear();

        if (rawBeTag == null || !isAe2CableBusTag(rawBeTag)) {
            return false;
        }

        if (!(level.getBlockEntity(pos) instanceof CableBusBlockEntity cableBus)) {
            return false;
        }

        Map<Item, Integer> reserved = new HashMap<>();
        boolean added = false;

        for (String key : StructureToolKeys.AE2_CABLE_BUS_KEYS) {
            if (!rawBeTag.contains(key, Tag.TAG_COMPOUND)) {
                continue;
            }

            Direction side = TemplateUtil.directionFromKey(key);

            if (cableBus.getPart(side) != null) {
                continue;
            }

            ItemStack partStack = normalizeSingle(NbtUtil.tryReadSavedItemStack(rawBeTag.getCompound(key)));

            if (partStack.isEmpty() || !(partStack.getItem() instanceof IPartItem<?> partItem)) {
                continue;
            }

            if (!cableBus.canAddPart(partStack, side)) {
                continue;
            }

            if (!player.isCreative() && !ctx.canReserveForPaste(reserved, partStack, 1)) {
                continue;
            }

            List<ItemStack> partCosts = new ArrayList<>();
            CompoundTag partEntry = getPartEntry(blockMetadata, key);
            int crazyUpgrades = planCrazyUpgrades(partEntry, player, ctx, reserved, partCosts);

            if (!addPatternCosts(
                    level,
                    partEntry,
                    player,
                    ctx,
                    reserved,
                    partCosts,
                    crazyUpgrades)) {
                continue;
            }

            if (cableBus.addPart(partItem, side, player) == null) {
                continue;
            }

            consumedStacks.add(partStack);
            consumedStacks.addAll(partCosts);
            added = true;

            plannedCrazyUpgrades.put(key, crazyUpgrades);
            applyExistingPartMetadata(level, player, cableBus, side, blockMetadata);
        }

        if (!added) {
            return false;
        }

        cableBus.setChanged();

        BlockState state = level.getBlockState(pos);
        level.sendBlockUpdated(pos, state, state, 3);

        return true;
    }

    private void applyExistingPartMetadata(
            Level level,
            Player player,
            CableBusBlockEntity cableBus,
            @Nullable Direction side,
            @Nullable CompoundTag blockMetadata) {
        if (side == null) {
            return;
        }

        CompoundTag partEntry = getPartEntry(blockMetadata, TemplateUtil.directionKey(side));
        var part = cableBus.getPart(side);

        if (part == null || partEntry == null) {
            return;
        }

        applyCrazyUpgrades(part, player, TemplateUtil.directionKey(side));

        if (!partEntry.contains(StructureToolKeys.CLONE_KEY_SETTINGS, Tag.TAG_COMPOUND)) {
            return;
        }

        CompoundTag partSettings = partEntry.getCompound(StructureToolKeys.CLONE_KEY_SETTINGS);

        try {
            part.importSettings(SettingsFrom.MEMORY_CARD, withoutPatterns(partSettings), player);
        } catch (Throwable ignored) {
        }

        restorePatterns(level, part, partEntry);
    }

    @Override
    public void onBlockPlaced(
            ServerLevel level,
            Player player,
            BlockPos pos,
            @Nullable BlockEntity be,
            @Nullable CompoundTag blockMetadata) {
        if (blockMetadata == null || be == null) {
            return;
        }

        if (be instanceof CableBusBlockEntity cableBus) {
            applyAe2CableBusMetadataAfterPlacement(level, player, pos, cableBus, blockMetadata);
            return;
        }

        applyAe2BlockMetadataAfterPlacement(level, player, pos, be, blockMetadata);
    }

    private PlacementPlan buildCableBusPlacementPlan(
            Level level,
            Player player,
            BlockState stateToPlace,
            CompoundTag rawBeTag,
            @Nullable CompoundTag blockMetadata,
            ClonerPasteContext ctx) {
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
                    sectionCosts)) {
                continue;
            }

            CompoundTag partEntry = getPartEntry(blockMetadata, key);
            int crazyUpgrades = planCrazyUpgrades(partEntry, player, ctx, trialReserved, sectionCosts);

            if (!addPatternCosts(
                    level,
                    partEntry,
                    player,
                    ctx,
                    trialReserved,
                    sectionCosts,
                    crazyUpgrades)) {
                continue;
            }

            reserved.clear();
            reserved.putAll(trialReserved);

            plannedCrazyUpgrades.put(key, crazyUpgrades);
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

    private PlacementPlan buildAe2BlockPlacementPlan(
            Level level,
            Player player,
            BlockState stateToPlace,
            CompoundTag blockMetadata,
            ClonerPasteContext ctx) {
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
                    costs)) {
                return PlacementPlan.none();
            }
        }

        int crazyUpgrades = planCrazyUpgrades(blockMetadata, player, ctx, reserved, costs);

        if (!addPatternCosts(level, blockMetadata, player, ctx, reserved, costs, crazyUpgrades)) {
            return PlacementPlan.none();
        }

        plannedCrazyUpgrades.put(CRAZY_UPGRADES_BLOCK_KEY, crazyUpgrades);

        return new PlacementPlan(true, stateToPlace, null, costs);
    }

    private static boolean addAe2PartUpgradeCosts(
            @Nullable CompoundTag blockMetadata,
            String sideKey,
            Player player,
            ClonerPasteContext ctx,
            Map<Item, Integer> reserved,
            List<ItemStack> costs) {
        CompoundTag partEntry = getPartEntry(blockMetadata, sideKey);

        if (partEntry == null) {
            return true;
        }

        if (!partEntry.contains(StructureToolKeys.CLONE_KEY_UPGRADES)) {
            return true;
        }

        return addNestedSavedStackCosts(
                partEntry.get(StructureToolKeys.CLONE_KEY_UPGRADES),
                player,
                ctx,
                reserved,
                costs);
    }

    private static boolean addNestedSavedStackCosts(
            @Nullable Tag tag,
            Player player,
            ClonerPasteContext ctx,
            Map<Item, Integer> reserved,
            List<ItemStack> costs) {
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

    private void applyAe2BlockMetadataAfterPlacement(
            ServerLevel level,
            Player player,
            BlockPos worldPos,
            BlockEntity be,
            CompoundTag blockMetadata) {
        boolean changed = false;

        if (be instanceof AEBaseBlockEntity abbe) {
            changed |= applyCrazyUpgrades(abbe, player, CRAZY_UPGRADES_BLOCK_KEY);

            if (blockMetadata.contains(StructureToolKeys.CLONE_KEY_SETTINGS, Tag.TAG_COMPOUND)) {
                CompoundTag settings = blockMetadata.getCompound(StructureToolKeys.CLONE_KEY_SETTINGS);

                try {
                    abbe.importSettings(SettingsFrom.MEMORY_CARD, withoutPatterns(settings), player);
                    changed = true;
                } catch (Throwable ignored) {
                }

                changed |= restorePatterns(level, be, blockMetadata);
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

    private void applyAe2CableBusMetadataAfterPlacement(
            ServerLevel level,
            Player player,
            BlockPos worldPos,
            CableBusBlockEntity cableBus,
            CompoundTag blockMetadata) {
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

            applyCrazyUpgrades(part, player, key);

            if (partEntry.contains(StructureToolKeys.CLONE_KEY_SETTINGS, Tag.TAG_COMPOUND)) {
                CompoundTag partSettings = partEntry.getCompound(StructureToolKeys.CLONE_KEY_SETTINGS);

                try {
                    part.importSettings(SettingsFrom.MEMORY_CARD, withoutPatterns(partSettings), player);
                } catch (Throwable ignored) {
                }

                restorePatterns(level, part, partEntry);
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
            AbstractStructureCaptureToolItem.RequirementSink requirements) {
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
            AbstractStructureCaptureToolItem.RequirementSink requirements) {
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

    private static void addBlankPatternRequirements(
            Object host,
            AbstractStructureCaptureToolItem.RequirementSink requirements) {
        int encoded = countEncodedPatterns(host);

        if (encoded > 0) {
            requirements.add(AEItems.BLANK_PATTERN.stack(encoded));
        }
    }

    private static int countEncodedPatterns(@Nullable Object host) {
        if (!(host instanceof PatternProviderLogicHost providerHost)) {
            return 0;
        }

        InternalInventory patterns = providerHost.getLogic().getPatternInv();
        int encoded = 0;

        for (int slot = 0; slot < patterns.size(); slot++) {
            if (!patterns.getStackInSlot(slot).isEmpty()) {
                encoded++;
            }
        }

        return encoded;
    }

    private record RestorablePattern(int slot, ItemStack stack) {
    }

    private static @Nullable CompoundTag rawPartSection(@Nullable CompoundTag rawBeTag, Direction side) {
        if (rawBeTag == null) {
            return null;
        }

        String key = TemplateUtil.directionKey(side);

        return rawBeTag.contains(key, Tag.TAG_COMPOUND) ? rawBeTag.getCompound(key) : null;
    }

    private static boolean isCrazyProviderTag(@Nullable CompoundTag tag, String expectedId) {
        return tag != null && expectedId.equals(tag.getString("id"));
    }

    private static int readCrazyAdded(@Nullable CompoundTag tag) {
        if (tag == null) {
            return 0;
        }

        if (tag.contains(StructureToolKeys.CRAZYAE2_NBT_PROVIDER, Tag.TAG_COMPOUND)) {
            CompoundTag providerTag = tag.getCompound(StructureToolKeys.CRAZYAE2_NBT_PROVIDER);

            if (providerTag.contains(StructureToolKeys.CRAZYAE2_NBT_STATE, Tag.TAG_COMPOUND)) {
                return providerTag.getCompound(StructureToolKeys.CRAZYAE2_NBT_STATE)
                        .getInt(StructureToolKeys.CRAZYAE2_NBT_ADDED);
            }
        }

        if (tag.contains(StructureToolKeys.CRAZYAE2_NBT_LEGACY_STATE, Tag.TAG_COMPOUND)) {
            return tag.getCompound(StructureToolKeys.CRAZYAE2_NBT_LEGACY_STATE)
                    .getInt(StructureToolKeys.CRAZYAE2_NBT_ADDED);
        }

        return tag.getInt(StructureToolKeys.CRAZYAE2_NBT_ADDED);
    }

    private static ItemStack crazyUpgradeStack(int count) {
        if (count <= 0) {
            return ItemStack.EMPTY;
        }

        Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(
                StructureToolKeys.CRAZYAE2_MOD_ID,
                StructureToolKeys.CRAZYAE2_UPGRADE_ITEM_ID));

        return item == null ? ItemStack.EMPTY : new ItemStack(item, count);
    }

    private static boolean storeCrazyUpgrades(
            @Nullable CompoundTag sourceTag,
            CompoundTag entry,
            AbstractStructureCaptureToolItem.RequirementSink requirements) {
        int added = readCrazyAdded(sourceTag);
        ItemStack upgrades = crazyUpgradeStack(added);

        if (upgrades.isEmpty()) {
            return false;
        }

        requirements.add(upgrades);
        entry.putInt(StructureToolKeys.CLONE_KEY_CRAZY_UPGRADES, added);

        return true;
    }

    private static int readCrazyUpgradeCount(@Nullable CompoundTag entry) {
        return entry == null ? 0 : entry.getInt(StructureToolKeys.CLONE_KEY_CRAZY_UPGRADES);
    }

    private static int patternCapacity(int added) {
        if (added <= 0) {
            return Integer.MAX_VALUE;
        }

        return CRAZY_PROVIDER_BASE_SLOTS + CRAZY_PROVIDER_SLOTS_PER_UPGRADE * added;
    }

    private static int planCrazyUpgrades(
            @Nullable CompoundTag entry,
            Player player,
            ClonerPasteContext ctx,
            Map<Item, Integer> reserved,
            List<ItemStack> costs) {
        int added = readCrazyUpgradeCount(entry);
        ItemStack upgrades = crazyUpgradeStack(added);

        if (upgrades.isEmpty()) {
            return 0;
        }

        int affordable = added;

        if (!player.isCreative()) {
            long available = ctx.countAvailableForPaste(upgrades)
                    - reserved.getOrDefault(upgrades.getItem(), 0);

            affordable = (int) Math.max(0L, Math.min(added, available));
        }

        if (affordable <= 0) {
            return 0;
        }

        ItemStack cost = crazyUpgradeStack(affordable);

        if (!player.isCreative() && !ctx.canReserveForPaste(reserved, cost, affordable)) {
            return 0;
        }

        costs.add(cost);

        return affordable;
    }

    private boolean applyCrazyUpgrades(@Nullable Object host, Player player, String entryKey) {
        Integer planned = plannedCrazyUpgrades.remove(entryKey);
        int added = planned == null ? 0 : planned;

        if (added <= 0) {
            return false;
        }

        CompoundTag stateTag = new CompoundTag();
        stateTag.putInt(StructureToolKeys.CRAZYAE2_NBT_ADDED, added);

        CompoundTag providerTag = new CompoundTag();
        providerTag.put(StructureToolKeys.CRAZYAE2_NBT_STATE, stateTag);

        CompoundTag input = new CompoundTag();
        input.put(StructureToolKeys.CRAZYAE2_NBT_PROVIDER, providerTag);

        try {
            if (host instanceof AEBaseBlockEntity be) {
                be.importSettings(SettingsFrom.DISMANTLE_ITEM, input, player);
                return true;
            }

            if (host instanceof IPart part) {
                part.importSettings(SettingsFrom.DISMANTLE_ITEM, input, player);
                return true;
            }
        } catch (Throwable ignored) {
        }

        return false;
    }

    private static @Nullable CompoundTag getSettings(@Nullable CompoundTag metadata) {
        if (metadata == null || !metadata.contains(StructureToolKeys.CLONE_KEY_SETTINGS, Tag.TAG_COMPOUND)) {
            return null;
        }

        return metadata.getCompound(StructureToolKeys.CLONE_KEY_SETTINGS);
    }

    private static @Nullable CompoundTag getPartEntry(@Nullable CompoundTag blockMetadata, String sideKey) {
        if (blockMetadata == null || !blockMetadata.contains(StructureToolKeys.CLONE_KEY_PARTS, Tag.TAG_COMPOUND)) {
            return null;
        }

        CompoundTag partsTag = blockMetadata.getCompound(StructureToolKeys.CLONE_KEY_PARTS);

        if (!partsTag.contains(sideKey, Tag.TAG_COMPOUND)) {
            return null;
        }

        return partsTag.getCompound(sideKey);
    }

    private static CompoundTag withoutPatterns(CompoundTag settings) {
        CompoundTag copy = settings.copy();

        copy.remove(PatternProviderLogic.NBT_MEMORY_CARD_PATTERNS);

        return copy;
    }

    private static List<RestorablePattern> readEncodedPatterns(Level level, @Nullable CompoundTag settings) {
        if (settings == null || !settings.contains(PatternProviderLogic.NBT_MEMORY_CARD_PATTERNS, Tag.TAG_LIST)) {
            return List.of();
        }

        ListTag entries = settings.getList(PatternProviderLogic.NBT_MEMORY_CARD_PATTERNS, Tag.TAG_COMPOUND);
        List<RestorablePattern> patterns = new ArrayList<>(entries.size());

        for (int i = 0; i < entries.size(); i++) {
            CompoundTag entry = entries.getCompound(i);
            ItemStack stack = ItemStack.of(entry);

            if (stack.isEmpty()) {
                continue;
            }

            try {
                var details = PatternDetailsHelper.decodePattern(stack, level, true);

                if (details == null) {
                    continue;
                }

                patterns.add(new RestorablePattern(entry.getInt("Slot"), details.getDefinition().toStack()));
            } catch (Throwable ignored) {
            }
        }

        return patterns;
    }

    private static boolean addPatternCosts(
            Level level,
            @Nullable CompoundTag entry,
            Player player,
            ClonerPasteContext ctx,
            Map<Item, Integer> reserved,
            List<ItemStack> costs,
            int crazyUpgrades) {
        int count = Math.min(
                readEncodedPatterns(level, getSettings(entry)).size(),
                patternCapacity(crazyUpgrades));

        if (count <= 0) {
            return true;
        }

        ItemStack blankPatterns = AEItems.BLANK_PATTERN.stack(count);

        if (!player.isCreative() && !ctx.canReserveForPaste(reserved, blankPatterns, count)) {
            return false;
        }

        costs.add(blankPatterns);

        return true;
    }

    private static boolean restorePatterns(Level level, @Nullable Object host, @Nullable CompoundTag entry) {
        if (!(host instanceof PatternProviderLogicHost providerHost)) {
            return false;
        }

        List<RestorablePattern> patterns = readEncodedPatterns(level, getSettings(entry));

        if (patterns.isEmpty()) {
            return false;
        }

        InternalInventory patternInv = providerHost.getLogic().getPatternInv();
        int limit = patternInv.size();
        boolean restored = false;
        int placed = 0;

        for (RestorablePattern pattern : patterns) {
            if (placed >= limit) {
                break;
            }

            int slot = pattern.slot();

            if (slot >= 0 && slot < limit && patternInv.getStackInSlot(slot).isEmpty()) {
                patternInv.setItemDirect(slot, pattern.stack());
                restored = true;
                placed++;
                continue;
            }

            if (patternInv.addItems(pattern.stack()).isEmpty()) {
                restored = true;
                placed++;
            }
        }

        return restored;
    }

    private static void dropSettingsCarriedByBlockState(BlockState state, CompoundTag settings) {
        for (Property<?> property : state.getProperties()) {
            settings.remove(property.getName());
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
            List<ItemStack> refunds) {
        CompoundTag currentTag = saveCurrentTag(be);

        if (be instanceof CableBusBlockEntity cableBus) {
            collectCableBusUndoRefunds(cableBus, currentTag, refunds);
            return true;
        }

        if (isAe2CableBusTag(currentTag)) {
            collectCableBusUndoRefunds(null, currentTag, refunds);
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

        int encodedPatterns = countEncodedPatterns(be);

        if (encodedPatterns > 0) {
            refunds.add(AEItems.BLANK_PATTERN.stack(encodedPatterns));
        }

        if (isCrazyProviderTag(currentTag, StructureToolKeys.CRAZYAE2_PROVIDER_BE_ID)) {
            ItemStack crazyUpgrades = crazyUpgradeStack(readCrazyAdded(currentTag));

            if (!crazyUpgrades.isEmpty()) {
                refunds.add(crazyUpgrades);
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
            @Nullable CableBusBlockEntity cableBus,
            @Nullable CompoundTag rawBeTag,
            List<ItemStack> refunds) {
        if (rawBeTag == null) {
            return;
        }

        for (String key : StructureToolKeys.AE2_CABLE_BUS_KEYS) {
            if (!rawBeTag.contains(key, Tag.TAG_COMPOUND)) {
                continue;
            }

            CompoundTag section = rawBeTag.getCompound(key);

            collectCableBusPartUndoRefunds(cableBus, key, section, refunds);

            if (isCrazyProviderTag(section, StructureToolKeys.CRAZYAE2_PROVIDER_PART_ID)) {
                ItemStack crazyUpgrades = crazyUpgradeStack(readCrazyAdded(section));

                if (!crazyUpgrades.isEmpty()) {
                    refunds.add(crazyUpgrades);
                }
            }
        }

        for (String key : StructureToolKeys.AE2_FACADE_KEYS) {
            if (!rawBeTag.contains(key)) {
                continue;
            }

            collectNestedSavedItemStacks(rawBeTag.get(key), refunds);
        }
    }

    private static void collectCableBusPartUndoRefunds(
            @Nullable CableBusBlockEntity cableBus,
            String sideKey,
            CompoundTag section,
            List<ItemStack> refunds) {
        ItemStack partItem = normalizeSingle(NbtUtil.tryReadSavedItemStack(section));

        if (!partItem.isEmpty()) {
            refunds.add(partItem);
        }

        IPart part = cableBus != null ? cableBus.getPart(TemplateUtil.directionFromKey(sideKey)) : null;

        if (part instanceof IUpgradeableObject upgradable) {
            for (ItemStack upgrade : upgradable.getUpgrades()) {
                if (!upgrade.isEmpty()) {
                    refunds.add(upgrade.copy());
                }
            }
        } else {
            collectNestedSavedItemStacks(section.get(AE2_SETTINGS_UPGRADES_KEY), refunds);
        }

        int encodedPatterns = countEncodedPatterns(part);

        if (encodedPatterns > 0) {
            refunds.add(AEItems.BLANK_PATTERN.stack(encodedPatterns));
        }
    }

    private static void collectNestedSavedItemStacks(
            @Nullable Tag tag,
            List<ItemStack> refunds) {
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

    @Override
    public boolean sanitizeCapturedBlockEntityTag(BlockState state, CompoundTag rawBeTag) {
        if (!isQuantumBridge(state)) {
            return false;
        }

        if (!rawBeTag.contains(StructureToolKeys.AE2_QNB_INV_KEY, Tag.TAG_COMPOUND)) {
            return false;
        }

        CompoundTag invTag = rawBeTag.getCompound(StructureToolKeys.AE2_QNB_INV_KEY);

        if (!hasAnyStoredItem(invTag)) {
            return false;
        }

        rawBeTag.put(StructureToolKeys.AE2_QNB_DEFERRED_INV_KEY, invTag.copy());
        rawBeTag.remove(StructureToolKeys.AE2_QNB_INV_KEY);

        return true;
    }

    @Override
    public void onTemplatePasted(ServerLevel level, BlockPos placementOrigin, CompoundTag templateTag) {
        for (TemplateUtil.BlockInfo info : TemplateUtil.parseRawBlocksFromTag(templateTag)) {
            CompoundTag beTag = info.blockEntityTag();

            if (beTag == null || !beTag.contains(StructureToolKeys.AE2_QNB_DEFERRED_INV_KEY, Tag.TAG_COMPOUND)) {
                continue;
            }

            queueQuantumBridgeInventory(
                    level,
                    placementOrigin.offset(info.pos()).immutable(),
                    beTag.getCompound(StructureToolKeys.AE2_QNB_DEFERRED_INV_KEY).copy());
        }
    }

    @Override
    public boolean hasPendingWork(ServerLevel level) {
        for (PendingQuantumBridgeInventory pending : PENDING_QNB) {
            if (pending.level == level) {
                return true;
            }
        }

        return false;
    }

    private static boolean isQuantumBridge(BlockState state) {
        return state.is(AEBlocks.QUANTUM_LINK.block()) || state.is(AEBlocks.QUANTUM_RING.block());
    }

    private static boolean hasAnyStoredItem(CompoundTag invTag) {
        for (String key : invTag.getAllKeys()) {
            if (!key.startsWith(AE2_QNB_INV_SLOT_PREFIX)) {
                continue;
            }

            if (!ItemStack.of(invTag.getCompound(key)).isEmpty()) {
                return true;
            }
        }

        return false;
    }

    private static void queueQuantumBridgeInventory(ServerLevel level, BlockPos pos, CompoundTag invTag) {
        ensureQnbTickRegistered();
        PENDING_QNB.add(new PendingQuantumBridgeInventory(level, pos, invTag, level.getGameTime()));
    }

    private static void ensureQnbTickRegistered() {
        if (!qnbTickRegistered) {
            synchronized (AE2StructureExtension.class) {
                if (!qnbTickRegistered) {
                    MinecraftForge.EVENT_BUS.register(AE2StructureExtension.class);
                    qnbTickRegistered = true;
                }
            }
        }
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            PENDING_QNB.removeIf(pending -> pending.level == level);
        }
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level)) {
            return;
        }

        if (PENDING_QNB.isEmpty()) {
            return;
        }

        long now = level.getGameTime();
        List<PendingQuantumBridgeInventory> ready = new ArrayList<>();

        PENDING_QNB.removeIf(pending -> {
            if (pending.level != level) {
                return false;
            }

            boolean expired = now - pending.pastedGameTime >= QNB_MAX_WAIT_TICKS;

            if (!(level.getBlockEntity(pending.pos) instanceof QuantumBridgeBlockEntity bridge)) {
                return expired;
            }

            if (expired || bridge.getCluster() != null && bridge.getGridNode() != null) {
                ready.add(pending);
                return true;
            }

            return false;
        });

        for (PendingQuantumBridgeInventory pending : ready) {
            restoreQuantumBridgeInventory(level, pending.pos, pending.invTag);
        }
    }

    private static void restoreQuantumBridgeInventory(ServerLevel level, BlockPos pos, CompoundTag invTag) {
        if (!(level.getBlockEntity(pos) instanceof QuantumBridgeBlockEntity bridge)) {
            return;
        }

        InternalInventory inventory = bridge.getInternalInventory();

        for (int slot = 0; slot < inventory.size(); slot++) {
            ItemStack stack = ItemStack.of(invTag.getCompound(AE2_QNB_INV_SLOT_PREFIX + slot));

            if (stack.isEmpty() || !inventory.getStackInSlot(slot).isEmpty()) {
                continue;
            }

            inventory.setItemDirect(slot, stack);
        }
    }

    private record PendingQuantumBridgeInventory(
            ServerLevel level,
            BlockPos pos,
            CompoundTag invTag,
            long pastedGameTime) {
    }
}
