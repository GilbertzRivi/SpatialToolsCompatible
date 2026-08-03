package net.oktawia.spatialtoolscmp.compat;

import org.jetbrains.annotations.Nullable;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.p3pp3rf1y.sophisticatedcore.api.IStorageWrapper;
import net.p3pp3rf1y.sophisticatedcore.inventory.InventoryHandler;
import net.p3pp3rf1y.sophisticatedstorage.item.CapabilityStorageWrapper;
import net.p3pp3rf1y.sophisticatedstorage.item.StorageBlockItem;

public final class SophisticatedStorageOps {

    private SophisticatedStorageOps() {
    }

    public static boolean isStorageStack(ItemStack stack) {
        return stack != null
                && !stack.isEmpty()
                && stack.getItem() instanceof StorageBlockItem;
    }

    public static long count(
            @Nullable MinecraftServer server,
            ItemStack storageStack,
            ItemStack wanted) {
        if (server == null || !server.isSameThread()) {
            return 0L;
        }

        if (!isUsableStorageStack(storageStack) || wanted == null || wanted.isEmpty()) {
            return 0L;
        }

        InventoryHandler handler = getInventoryHandler(storageStack);

        if (handler == null) {
            return 0L;
        }

        long total = 0L;

        for (int slot = 0; slot < handler.getSlots(); slot++) {
            ItemStack inSlot = handler.getStackInSlot(slot);

            if (!inSlot.isEmpty() && ItemStack.isSameItemSameTags(inSlot, wanted)) {
                total += inSlot.getCount();
            }
        }

        return total;
    }

    public static long extract(
            @Nullable MinecraftServer server,
            ItemStack storageStack,
            ItemStack wanted,
            long amount,
            boolean simulate) {
        if (server == null || !server.isSameThread()) {
            return 0L;
        }

        if (!isUsableStorageStack(storageStack) || wanted == null || wanted.isEmpty() || amount <= 0L) {
            return 0L;
        }

        InventoryHandler handler = getInventoryHandler(storageStack);

        if (handler == null) {
            return 0L;
        }

        long remaining = amount;
        long extracted = 0L;

        for (int slot = 0; slot < handler.getSlots(); slot++) {
            if (remaining <= 0L) {
                break;
            }

            ItemStack inSlot = handler.getStackInSlot(slot);

            if (inSlot.isEmpty()) {
                continue;
            }

            if (!ItemStack.isSameItemSameTags(inSlot, wanted)) {
                continue;
            }

            int request = (int) Math.min(Integer.MAX_VALUE, remaining);
            ItemStack pulled = handler.extractItem(slot, request, simulate);

            if (pulled.isEmpty()) {
                continue;
            }

            extracted += pulled.getCount();
            remaining -= pulled.getCount();
        }

        return extracted;
    }

    private static boolean isUsableStorageStack(ItemStack stack) {
        return isStorageStack(stack) && stack.getCount() == 1;
    }

    @Nullable
    private static InventoryHandler getInventoryHandler(ItemStack storageStack) {
        try {
            IStorageWrapper wrapper = storageStack
                    .getCapability(CapabilityStorageWrapper.getCapabilityInstance())
                    .orElse(null);

            if (wrapper == null) {
                return null;
            }

            return wrapper.getInventoryHandler();
        } catch (Throwable ignored) {
            return null;
        }
    }
}
