package net.oktawia.spatialtoolscmp.items.helpers;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;
import net.oktawia.spatialtoolscmp.IsModLoaded;
import net.oktawia.spatialtoolscmp.SpatialConfig;
import net.oktawia.spatialtoolscmp.compat.ae2.AE2Compat;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.function.IntSupplier;

public final class ToolPowerManager {

    public static final String CURRENT_POWER_NBT_KEY = "internalCurrentPower";
    public static final String POWER_UPGRADES_NBT_KEY = "internalPowerUpgradeInventory";

    private final IntSupplier basePowerCapacitySupplier;
    private final int powerUpgradeSlots;
    private final int maxPowerUpgrades;

    public ToolPowerManager(IntSupplier basePowerCapacitySupplier, int powerUpgradeSlots, int maxPowerUpgrades) {
        this.basePowerCapacitySupplier = basePowerCapacitySupplier != null ? basePowerCapacitySupplier : () -> 0;
        this.powerUpgradeSlots = Math.max(0, powerUpgradeSlots);
        this.maxPowerUpgrades = Mth.clamp(maxPowerUpgrades, 0, this.powerUpgradeSlots);
    }

    public boolean tryUse(Player player, ItemStack stack, double amount) {
        if (player.isCreative()) {
            return true;
        }

        if (amount <= 0.0D) {
            return true;
        }

        int required = (int) Math.ceil(amount);

        if (getStored(stack) < required) {
            return false;
        }

        int extracted = extract(stack, required, false);
        return extracted >= required;
    }

    public int getCapacity(ItemStack stack) {
        int base = Math.max(0, this.basePowerCapacitySupplier.getAsInt());
        int upgrades = getInstalledUpgrades(stack);

        long capacity = base + (long) base * upgrades;
        return (int) Math.min(Integer.MAX_VALUE, capacity);
    }

    public int getStored(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return 0;
        }

        CompoundTag tag = stack.getTag();

        if (tag == null || !tag.contains(CURRENT_POWER_NBT_KEY, Tag.TAG_ANY_NUMERIC)) {
            return 0;
        }

        return Mth.clamp(tag.getInt(CURRENT_POWER_NBT_KEY), 0, getCapacity(stack));
    }

    public int extract(ItemStack stack, double amount, boolean simulate) {
        if (stack == null || stack.isEmpty() || amount <= 0.0D) {
            return 0;
        }

        int requested = (int) Math.ceil(amount);
        int stored = getStored(stack);
        int extracted = Math.min(stored, requested);

        if (!simulate && extracted > 0) {
            stack.getOrCreateTag().putInt(CURRENT_POWER_NBT_KEY, stored - extracted);
        }

        return extracted;
    }

    public int receive(ItemStack stack, double amount, boolean simulate) {
        if (stack == null || stack.isEmpty() || amount <= 0.0D) {
            return 0;
        }

        int requested = (int) Math.ceil(amount);
        int capacity = getCapacity(stack);
        int stored = getStored(stack);
        int received = Math.min(capacity - stored, requested);

        if (!simulate && received > 0) {
            stack.getOrCreateTag().putInt(CURRENT_POWER_NBT_KEY, stored + received);
        }

        return received;
    }

    public void clamp(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return;
        }

        stack.getOrCreateTag().putInt(CURRENT_POWER_NBT_KEY, getStored(stack));
    }

    public int getInstalledUpgrades(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return 0;
        }

        CompoundTag tag = stack.getTag();

        if (tag == null || !tag.contains(POWER_UPGRADES_NBT_KEY, Tag.TAG_COMPOUND)) {
            return 0;
        }

        CompoundTag inventoryTag = tag.getCompound(POWER_UPGRADES_NBT_KEY);

        if (!inventoryTag.contains("Items", Tag.TAG_LIST)) {
            return 0;
        }

        ListTag items = inventoryTag.getList("Items", Tag.TAG_COMPOUND);
        int upgrades = 0;

        for (int i = 0; i < items.size(); i++) {
            CompoundTag row = items.getCompound(i);
            int slot = row.getInt("Slot");

            if (slot < 0 || slot >= this.maxPowerUpgrades) {
                continue;
            }

            if (!row.contains("Stack", Tag.TAG_COMPOUND)) {
                continue;
            }

            ItemStack stored = ItemStack.of(row.getCompound("Stack"));

            if (isValidPowerUpgradeItem(stored)) {
                upgrades++;
            }
        }

        return Mth.clamp(upgrades, 0, this.maxPowerUpgrades);
    }

    public int getCraftingUpgradeSlotIndex() {
        return this.powerUpgradeSlots > this.maxPowerUpgrades ? this.maxPowerUpgrades : -1;
    }

    public boolean hasCraftingUpgradeSlot() {
        return getCraftingUpgradeSlotIndex() >= 0;
    }

    public boolean isCraftingUpgradeSlot(int slot) {
        return slot == getCraftingUpgradeSlotIndex();
    }

    public boolean hasInstalledCraftingUpgrade(ItemStack stack) {
        int craftingSlot = getCraftingUpgradeSlotIndex();

        if (craftingSlot < 0 || stack == null || stack.isEmpty()) {
            return false;
        }

        CompoundTag tag = stack.getTag();

        if (tag == null || !tag.contains(POWER_UPGRADES_NBT_KEY, Tag.TAG_COMPOUND)) {
            return false;
        }

        CompoundTag inventoryTag = tag.getCompound(POWER_UPGRADES_NBT_KEY);

        if (!inventoryTag.contains("Items", Tag.TAG_LIST)) {
            return false;
        }

        ListTag items = inventoryTag.getList("Items", Tag.TAG_COMPOUND);

        for (int i = 0; i < items.size(); i++) {
            CompoundTag row = items.getCompound(i);

            if (row.getInt("Slot") != craftingSlot) {
                continue;
            }

            if (!row.contains("Stack", Tag.TAG_COMPOUND)) {
                continue;
            }

            return isValidCraftingUpgradeItem(ItemStack.of(row.getCompound("Stack")));
        }

        return false;
    }

    public int getPowerUpgradeSlots() {
        return this.powerUpgradeSlots;
    }

    public int getMaxPowerUpgrades() {
        return this.maxPowerUpgrades;
    }

    public static boolean isValidPowerUpgradeItem(@Nullable ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }

        ResourceLocation stackId = ForgeRegistries.ITEMS.getKey(stack.getItem());

        if (stackId == null) {
            return false;
        }

        for (ResourceLocation configuredId : getConfiguredPowerUpgradeItemIds()) {
            if (stackId.equals(configuredId)) {
                return true;
            }
        }

        return false;
    }

    public static List<ResourceLocation> getConfiguredPowerUpgradeItemIds() {
        LinkedHashSet<ResourceLocation> ids = new LinkedHashSet<>();

        for (String rawId : SpatialConfig.COMMON.ENERGY_UPGRADE_ITEMS.get()) {
            ResourceLocation id = ResourceLocation.tryParse(rawId);

            if (id != null) {
                ids.add(id);
            }
        }

        return List.copyOf(ids);
    }

    public static List<ItemStack> getConfiguredPowerUpgradeItemStacks() {
        ArrayList<ItemStack> stacks = new ArrayList<>();

        for (ResourceLocation id : getConfiguredPowerUpgradeItemIds()) {
            Item item = ForgeRegistries.ITEMS.getValue(id);

            if (item == null || item == Items.AIR) {
                continue;
            }

            stacks.add(new ItemStack(item));
        }

        return List.copyOf(stacks);
    }

    public static boolean isValidCraftingUpgradeItem(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }

        if (IsModLoaded.AE2) {
            return AE2Compat.isCraftingUpgradeItem(stack);
        }

        return false;
    }
}
