package net.oktawia.spatialtoolscmp.items.helpers;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import org.jetbrains.annotations.Nullable;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import net.oktawia.spatialtoolscmp.SpatialConfig;
import net.oktawia.spatialtoolscmp.defs.SpatialItemRegistrar;
import net.oktawia.spatialtoolscmp.items.AbstractStructureCaptureToolItem;
import net.oktawia.spatialtoolscmp.items.PortableSpatialCloner;
import net.oktawia.spatialtoolscmp.items.PortableSpatialPiper;
import net.oktawia.spatialtoolscmp.items.PortableSpatialReplacer;
import net.oktawia.spatialtoolscmp.items.PortableSpatialStorage;
import net.oktawia.spatialtoolscmp.items.PortableSpatialTool;

public final class SpatialMultiTool {

    public static final String MULTI_TOOL_NBT_KEY = "spatialMultiTool";
    public static final String MODE_STATES_NBT_KEY = "spatialMultiToolModes";
    public static final String CRAFTING_UPGRADE_NBT_KEY = "spatialMultiToolCraftingUpgrade";

    private static final String AE2_GRID_LINK_NBT_KEY = "ae2GridLink";

    private static final Set<String> SHARED_NBT_KEYS = Set.of(
            ToolPowerManager.CURRENT_POWER_NBT_KEY,
            ToolPowerManager.POWER_UPGRADES_NBT_KEY,
            AE2_GRID_LINK_NBT_KEY,
            CRAFTING_UPGRADE_NBT_KEY,
            MULTI_TOOL_NBT_KEY,
            MODE_STATES_NBT_KEY);

    public enum Mode {
        STORAGE(SpatialItemRegistrar.PORTABLE_SPATIAL_STORAGE::get),
        CLONER(SpatialItemRegistrar.PORTABLE_SPATIAL_CLONER::get),
        REPLACER(SpatialItemRegistrar.PORTABLE_SPATIAL_REPLACER::get),
        PIPER(SpatialItemRegistrar.PORTABLE_SPATIAL_PIPER::get);

        private final Supplier<? extends AbstractStructureCaptureToolItem> item;

        Mode(Supplier<? extends AbstractStructureCaptureToolItem> item) {
            this.item = item;
        }

        public AbstractStructureCaptureToolItem item() {
            return this.item.get();
        }
    }

    public static final List<Mode> MODES = List.of(Mode.values());

    private SpatialMultiTool() {
    }

    public static int getBasePowerCapacity() {
        int largestToolCapacity = Math.max(
                Math.max(
                        SpatialConfig.COMMON.PORTABLE_SPATIAL_STORAGE_BASE_INTERNAL_POWER_CAPACITY.get(),
                        SpatialConfig.COMMON.PORTABLE_SPATIAL_CLONER_BASE_INTERNAL_POWER_CAPACITY.get()),
                Math.max(
                        SpatialConfig.COMMON.PORTABLE_SPATIAL_REPLACER_BASE_INTERNAL_POWER_CAPACITY.get(),
                        SpatialConfig.COMMON.PORTABLE_SPATIAL_PIPER_BASE_INTERNAL_POWER_CAPACITY.get()));

        int configured = SpatialConfig.COMMON.PORTABLE_SPATIAL_TOOL_BASE_INTERNAL_POWER_CAPACITY.get();

        return Math.max(0, Math.min(configured, largestToolCapacity));
    }

    public static boolean isMultiTool(@Nullable ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }

        if (stack.getItem() instanceof PortableSpatialTool) {
            return true;
        }

        CompoundTag tag = stack.getTag();
        return tag != null && tag.getBoolean(MULTI_TOOL_NBT_KEY);
    }

    public static @Nullable Mode getMode(@Nullable ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }

        Item item = stack.getItem();

        if (item instanceof PortableSpatialStorage) {
            return Mode.STORAGE;
        }

        if (item instanceof PortableSpatialCloner) {
            return Mode.CLONER;
        }

        if (item instanceof PortableSpatialReplacer) {
            return Mode.REPLACER;
        }

        if (item instanceof PortableSpatialPiper) {
            return Mode.PIPER;
        }

        return null;
    }

    public record MergeResult(ItemStack tool, List<ItemStack> leftovers) {
    }

    public static MergeResult merge(List<ItemStack> tools) {
        CompoundTag modeStates = new CompoundTag();
        List<ItemStack> energyUpgrades = new ArrayList<>();
        ItemStack craftingUpgrade = ItemStack.EMPTY;
        CompoundTag gridLink = null;
        long storedPower = 0L;

        for (ItemStack tool : tools) {
            Mode mode = getMode(tool);

            if (mode == null || !tool.hasTag()) {
                continue;
            }

            CompoundTag tag = tool.getOrCreateTag().copy();

            modeStates.put(mode.name(), extractModeState(tag));
            storedPower += tag.getInt(ToolPowerManager.CURRENT_POWER_NBT_KEY);

            if (gridLink == null && tag.contains(AE2_GRID_LINK_NBT_KEY, Tag.TAG_COMPOUND)) {
                gridLink = tag.getCompound(AE2_GRID_LINK_NBT_KEY).copy();
            }

            if (craftingUpgrade.isEmpty() && tag.contains(CRAFTING_UPGRADE_NBT_KEY, Tag.TAG_COMPOUND)) {
                craftingUpgrade = ItemStack.of(tag.getCompound(CRAFTING_UPGRADE_NBT_KEY));
            }

            ListTag items = tag.getCompound(ToolPowerManager.POWER_UPGRADES_NBT_KEY)
                    .getList("Items", Tag.TAG_COMPOUND);

            for (int i = 0; i < items.size(); i++) {
                CompoundTag row = items.getCompound(i);

                if (!row.contains("Stack", Tag.TAG_COMPOUND)) {
                    continue;
                }

                ItemStack upgrade = ItemStack.of(row.getCompound("Stack"));

                if (upgrade.isEmpty()) {
                    continue;
                }

                if (row.getInt("Slot") >= mode.item().getMaxPowerUpgrades()) {
                    if (craftingUpgrade.isEmpty()) {
                        craftingUpgrade = upgrade;
                    } else {
                        energyUpgrades.add(upgrade);
                    }
                } else {
                    energyUpgrades.add(upgrade);
                }
            }
        }

        int keptUpgrades = Math.min(energyUpgrades.size(), sharedMaxPowerUpgrades());

        ListTag inventoryItems = new ListTag();

        for (int slot = 0; slot < keptUpgrades; slot++) {
            CompoundTag row = new CompoundTag();
            row.putInt("Slot", slot);
            row.put("Stack", energyUpgrades.get(slot).save(new CompoundTag()));
            inventoryItems.add(row);
        }

        CompoundTag inventory = new CompoundTag();
        inventory.put("Items", inventoryItems);

        CompoundTag tag = new CompoundTag();
        tag.putBoolean(MULTI_TOOL_NBT_KEY, true);
        tag.put(MODE_STATES_NBT_KEY, modeStates);
        tag.put(ToolPowerManager.POWER_UPGRADES_NBT_KEY, inventory);

        long capacity = (long) getBasePowerCapacity() * (1L + keptUpgrades);
        tag.putInt(ToolPowerManager.CURRENT_POWER_NBT_KEY, (int) Math.min(storedPower, capacity));

        if (gridLink != null) {
            tag.put(AE2_GRID_LINK_NBT_KEY, gridLink);
        }

        if (!craftingUpgrade.isEmpty()) {
            tag.put(CRAFTING_UPGRADE_NBT_KEY, craftingUpgrade.save(new CompoundTag()));
        }

        ItemStack result = new ItemStack(SpatialItemRegistrar.PORTABLE_SPATIAL_TOOL.get(), 1);
        result.setTag(tag);

        return new MergeResult(result, stackLeftovers(energyUpgrades.subList(keptUpgrades, energyUpgrades.size())));
    }

    private static List<ItemStack> stackLeftovers(List<ItemStack> upgrades) {
        List<ItemStack> merged = new ArrayList<>();

        for (ItemStack upgrade : upgrades) {
            ItemStack target = null;

            for (ItemStack candidate : merged) {
                if (ItemStack.isSameItemSameTags(candidate, upgrade)
                        && candidate.getCount() < candidate.getMaxStackSize()) {
                    target = candidate;
                    break;
                }
            }

            if (target == null) {
                merged.add(upgrade.copy());
            } else {
                target.grow(upgrade.getCount());
            }
        }

        return merged;
    }

    private static int sharedMaxPowerUpgrades() {
        int shared = Integer.MAX_VALUE;

        for (Mode mode : MODES) {
            shared = Math.min(shared, mode.item().getMaxPowerUpgrades());
        }

        return shared;
    }

    public static ItemStack swap(ItemStack current, Mode target) {
        CompoundTag tag = current.hasTag() ? current.getOrCreateTag().copy() : new CompoundTag();

        CompoundTag modeStates = tag.getCompound(MODE_STATES_NBT_KEY).copy();
        Mode currentMode = getMode(current);

        if (currentMode != null) {
            modeStates.put(currentMode.name(), extractModeState(tag));
        }

        CompoundTag swapped = new CompoundTag();

        for (String key : SHARED_NBT_KEYS) {
            Tag value = tag.get(key);

            if (value != null) {
                swapped.put(key, value.copy());
            }
        }

        CompoundTag restored = modeStates.getCompound(target.name());

        for (String key : restored.getAllKeys()) {
            Tag value = restored.get(key);

            if (value != null) {
                swapped.put(key, value.copy());
            }
        }

        modeStates.remove(target.name());

        swapped.put(MODE_STATES_NBT_KEY, modeStates);
        swapped.putBoolean(MULTI_TOOL_NBT_KEY, true);

        moveCraftingUpgrade(swapped, target.item());

        ItemStack result = new ItemStack(target.item(), 1);
        result.setTag(swapped);

        return result;
    }

    private static CompoundTag extractModeState(CompoundTag tag) {
        CompoundTag state = new CompoundTag();

        for (String key : tag.getAllKeys()) {
            if (SHARED_NBT_KEYS.contains(key)) {
                continue;
            }

            Tag value = tag.get(key);

            if (value != null) {
                state.put(key, value.copy());
            }
        }

        return state;
    }

    private static void moveCraftingUpgrade(CompoundTag tag, AbstractStructureCaptureToolItem target) {
        int targetSlot = target.getCraftingUpgradeSlotIndex();

        ListTag items = tag.getCompound(ToolPowerManager.POWER_UPGRADES_NBT_KEY)
                .getList("Items", Tag.TAG_COMPOUND);

        ListTag kept = new ListTag();
        CompoundTag storedCard = null;

        for (int i = 0; i < items.size(); i++) {
            CompoundTag row = items.getCompound(i);

            if (row.getInt("Slot") >= target.getMaxPowerUpgrades()) {
                storedCard = row.getCompound("Stack").copy();
                continue;
            }

            kept.add(row.copy());
        }

        if (targetSlot >= 0 && storedCard == null && tag.contains(CRAFTING_UPGRADE_NBT_KEY, Tag.TAG_COMPOUND)) {
            storedCard = tag.getCompound(CRAFTING_UPGRADE_NBT_KEY).copy();
        }

        if (targetSlot >= 0 && storedCard != null) {
            CompoundTag row = new CompoundTag();
            row.putInt("Slot", targetSlot);
            row.put("Stack", storedCard);
            kept.add(row);

            tag.remove(CRAFTING_UPGRADE_NBT_KEY);
        } else if (storedCard != null) {
            tag.put(CRAFTING_UPGRADE_NBT_KEY, storedCard);
        }

        CompoundTag inventory = new CompoundTag();
        inventory.put("Items", kept);
        tag.put(ToolPowerManager.POWER_UPGRADES_NBT_KEY, inventory);
    }
}
