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
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;

import net.oktawia.spatialtoolscmp.items.AbstractStructureCaptureToolItem;
import net.oktawia.spatialtoolscmp.logic.ClonerPasteContext;
import net.oktawia.spatialtoolscmp.logic.PlacementPlan;
import net.oktawia.spatialtoolscmp.logic.StructureCloneExtension;

public final class ProductiveBeesClonerExtension implements StructureCloneExtension {

    private static final String MOD_ID = "productivebees";

    private static final String NBT_ID = "id";
    private static final String NBT_UPGRADES = "upgrades";
    private static final String NBT_SIZE = "Size";
    private static final String NBT_ITEMS = "Items";

    private static final String CLONE_KEY = "productivebees_upgrades";

    @Override
    public boolean handlesRequirements(BlockState state, @Nullable CompoundTag rawBeTag) {
        return isProductiveBeesBlock(state, rawBeTag);
    }

    @Override
    public boolean collectMetadata(
            ServerLevel level,
            BlockPos pos,
            BlockEntity be,
            @Nullable CompoundTag rawBeTag,
            AbstractStructureCaptureToolItem.RequirementSink requirements,
            CompoundTag blockEntry) {
        if (!isProductiveBeesBlock(level.getBlockState(pos), rawBeTag)) {
            return false;
        }

        addBaseBlockRequirement(level, pos, requirements);

        CompoundTag upgrades = readUpgrades(rawBeTag);

        if (upgrades == null) {
            return false;
        }

        for (ItemStack upgrade : readUpgradeStacks(upgrades)) {
            requirements.add(costStack(upgrade));
        }

        blockEntry.put(CLONE_KEY, upgrades.copy());
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
        if (!isProductiveBeesBlock(state, rawBeTag) || rawBeTag == null) {
            return Optional.empty();
        }

        Map<Item, Integer> reserved = new LinkedHashMap<>();
        List<ItemStack> costs = new ArrayList<>();

        ItemStack baseItem = costStack(ctx.getRequiredBlockItem(state));

        if (baseItem.isEmpty()) {
            if (!player.isCreative()) {
                return Optional.of(PlacementPlan.none());
            }
        } else {
            if (!player.isCreative() && !ctx.canReserveForPaste(reserved, baseItem, baseItem.getCount())) {
                return Optional.of(PlacementPlan.none());
            }

            costs.add(baseItem);
        }

        CompoundTag upgrades = readUpgrades(blockMetadata != null ? getStoredUpgrades(blockMetadata) : null);

        if (upgrades == null) {
            upgrades = readUpgrades(rawBeTag);
        }

        ListTag keptItems = new ListTag();

        if (upgrades != null) {
            for (int i = 0; i < upgrades.getList(NBT_ITEMS, Tag.TAG_COMPOUND).size(); i++) {
                CompoundTag itemTag = upgrades.getList(NBT_ITEMS, Tag.TAG_COMPOUND).getCompound(i);
                ItemStack upgrade = ItemStack.of(itemTag);

                if (upgrade.isEmpty()) {
                    continue;
                }

                ItemStack cost = costStack(upgrade);

                if (!player.isCreative() && !ctx.canReserveForPaste(reserved, cost, cost.getCount())) {
                    continue;
                }

                costs.add(cost);
                keptItems.add(itemTag.copy());
            }
        }

        CompoundTag beTag = new CompoundTag();

        if (rawBeTag.contains(NBT_ID, Tag.TAG_STRING)) {
            beTag.putString(NBT_ID, rawBeTag.getString(NBT_ID));
        }

        if (!keptItems.isEmpty()) {
            CompoundTag upgradesOut = new CompoundTag();
            upgradesOut.putInt(NBT_SIZE, Math.max(upgrades.getInt(NBT_SIZE), keptItems.size()));
            upgradesOut.put(NBT_ITEMS, keptItems);
            beTag.put(NBT_UPGRADES, upgradesOut);
        }

        return Optional.of(new PlacementPlan(true, state, beTag, costs));
    }

    @Override
    public void onBlockPlaced(
            ServerLevel level,
            Player player,
            BlockPos pos,
            @Nullable BlockEntity be,
            @Nullable CompoundTag blockMetadata) {
        if (be == null || blockMetadata == null || !blockMetadata.contains(CLONE_KEY, Tag.TAG_COMPOUND)) {
            return;
        }

        be.setChanged();

        BlockState state = level.getBlockState(pos);
        level.sendBlockUpdated(pos, state, state, 3);
    }

    @Override
    public boolean collectUndoRefunds(
            ServerLevel level,
            BlockPos pos,
            BlockState state,
            @Nullable BlockEntity be,
            List<ItemStack> refunds) {
        CompoundTag currentTag = saveCurrentTag(be);

        if (!isProductiveBeesBlock(state, currentTag)) {
            return false;
        }

        addBaseBlockRefund(level, pos, refunds);

        CompoundTag upgrades = readUpgrades(currentTag);

        if (upgrades != null) {
            for (ItemStack upgrade : readUpgradeStacks(upgrades)) {
                refunds.add(costStack(upgrade));
            }
        }

        return true;
    }

    @Nullable
    private static CompoundTag getStoredUpgrades(CompoundTag blockMetadata) {
        if (!blockMetadata.contains(CLONE_KEY, Tag.TAG_COMPOUND)) {
            return null;
        }

        return blockMetadata.getCompound(CLONE_KEY);
    }

    @Nullable
    private static CompoundTag readUpgrades(@Nullable CompoundTag source) {
        if (source == null) {
            return null;
        }

        if (source.contains(NBT_ITEMS, Tag.TAG_LIST) && source.contains(NBT_SIZE)) {
            return source.getList(NBT_ITEMS, Tag.TAG_COMPOUND).isEmpty() ? null : source;
        }

        if (!source.contains(NBT_UPGRADES, Tag.TAG_COMPOUND)) {
            return null;
        }

        CompoundTag upgrades = source.getCompound(NBT_UPGRADES);

        return upgrades.getList(NBT_ITEMS, Tag.TAG_COMPOUND).isEmpty() ? null : upgrades;
    }

    private static List<ItemStack> readUpgradeStacks(CompoundTag upgrades) {
        List<ItemStack> result = new ArrayList<>();
        ListTag items = upgrades.getList(NBT_ITEMS, Tag.TAG_COMPOUND);

        for (int i = 0; i < items.size(); i++) {
            ItemStack stack = ItemStack.of(items.getCompound(i));

            if (!stack.isEmpty()) {
                result.add(stack);
            }
        }

        return result;
    }

    private static ItemStack costStack(ItemStack stack) {
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }

        ItemStack copy = stack.copy();
        copy.setTag(null);

        return copy;
    }

    private static boolean isProductiveBeesBlock(BlockState state, @Nullable CompoundTag rawBeTag) {
        ResourceLocation blockId = ForgeRegistries.BLOCKS.getKey(state.getBlock());

        if (blockId != null && MOD_ID.equals(blockId.getNamespace())) {
            return true;
        }

        if (rawBeTag == null) {
            return false;
        }

        String id = rawBeTag.getString(NBT_ID);

        if (id.isBlank()) {
            return false;
        }

        ResourceLocation beId = ResourceLocation.tryParse(id);

        return beId != null && MOD_ID.equals(beId.getNamespace());
    }

    private static void addBaseBlockRequirement(
            ServerLevel level,
            BlockPos pos,
            AbstractStructureCaptureToolItem.RequirementSink requirements) {
        ItemStack picked = pickBlock(level, pos);

        if (!picked.isEmpty()) {
            requirements.add(picked);
        }
    }

    private static void addBaseBlockRefund(
            ServerLevel level,
            BlockPos pos,
            List<ItemStack> refunds) {
        ItemStack picked = pickBlock(level, pos);

        if (!picked.isEmpty()) {
            refunds.add(picked);
        }
    }

    private static ItemStack pickBlock(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
        ItemStack picked = state.getCloneItemStack(hit, level, pos, null);

        if (!picked.isEmpty()) {
            return picked;
        }

        Item item = state.getBlock().asItem();

        return item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item);
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
