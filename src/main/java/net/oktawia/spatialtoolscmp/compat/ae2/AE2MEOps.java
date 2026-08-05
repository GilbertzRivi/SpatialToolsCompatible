package net.oktawia.spatialtoolscmp.compat.ae2;

import static appeng.api.storage.StorageHelper.poweredExtraction;
import static appeng.api.storage.StorageHelper.poweredInsert;

import org.jetbrains.annotations.Nullable;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import appeng.api.networking.IGrid;
import appeng.api.networking.energy.IEnergySource;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import appeng.me.helpers.BaseActionSource;
import appeng.me.helpers.PlayerSource;

public final class AE2MEOps {

    private AE2MEOps() {
    }

    @Nullable
    private static IGrid getGrid(ItemStack linkedItem, ServerLevel level) {
        return AE2GridLinkableHandler.getLinkedGrid(level, linkedItem);
    }

    @Nullable
    private static MEStorage getStorage(IGrid grid) {
        return grid.getStorageService().getInventory();
    }

    private static IEnergySource getEnergy(IGrid grid) {
        return grid.getEnergyService();
    }

    private static IActionSource source(@Nullable Player player) {
        return player != null
                ? new PlayerSource(player)
                : new BaseActionSource();
    }

    private static AEKey toKey(ItemStack filter) {
        return AEItemKey.of(filter);
    }

    public static long getAmount(ItemStack filter, ItemStack linkedItem, ServerLevel level) {
        IGrid grid = getGrid(linkedItem, level);
        if (grid == null || filter.isEmpty())
            return 0;

        MEStorage storage = getStorage(grid);
        if (storage == null)
            return 0;

        KeyCounter counter = new KeyCounter();
        storage.getAvailableStacks(counter);
        return counter.get(toKey(filter));
    }

    public static long extract(
            ItemStack filter,
            ItemStack linkedItem,
            ServerLevel level,
            long amount,
            boolean simulate,
            @Nullable Player player) {
        if (filter.isEmpty() || amount <= 0)
            return 0;

        IGrid grid = getGrid(linkedItem, level);
        if (grid == null)
            return 0;

        MEStorage storage = getStorage(grid);
        if (storage == null)
            return 0;

        if (simulate) {
            return storage.extract(toKey(filter), amount, appeng.api.config.Actionable.SIMULATE, source(player));
        }

        return poweredExtraction(getEnergy(grid), storage, toKey(filter), amount, source(player));
    }

    public static long insert(
            ItemStack toInsert,
            ItemStack linkedItem,
            ServerLevel level,
            boolean simulate,
            @Nullable Player player) {
        if (toInsert.isEmpty())
            return 0;

        IGrid grid = getGrid(linkedItem, level);
        if (grid == null)
            return toInsert.getCount();

        MEStorage storage = getStorage(grid);
        if (storage == null)
            return toInsert.getCount();

        long amount = toInsert.getCount();

        long inserted;
        if (simulate) {
            inserted = storage.insert(toKey(toInsert), amount, appeng.api.config.Actionable.SIMULATE, source(player));
        } else {
            inserted = poweredInsert(getEnergy(grid), storage, toKey(toInsert), amount, source(player));
        }

        return amount - inserted;
    }

    public static boolean isCraftable(ItemStack filter, ItemStack linkedItem, ServerLevel level) {
        if (filter.isEmpty())
            return false;
        IGrid grid = getGrid(linkedItem, level);
        if (grid == null)
            return false;
        return grid.getCraftingService().isCraftable(AEItemKey.of(filter));
    }

    public static void openCraftingMenu(ItemStack filter, long amount, ServerPlayer player) {
        if (filter.isEmpty()) {
            return;
        }

        int slot = findInventorySlot(player, findActiveClonerStack(player));

        if (slot < 0) {
            return;
        }

        AE2CL.open(
                player,
                new AE2ClonerCraftingLocator(slot),
                AEItemKey.of(filter),
                amount);
    }

    private static ItemStack findActiveClonerStack(ServerPlayer player) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);

            if (stack.getItem() instanceof net.oktawia.spatialtoolscmp.items.PortableSpatialCloner) {
                return stack;
            }
        }

        return ItemStack.EMPTY;
    }

    private static int findInventorySlot(ServerPlayer player, ItemStack wanted) {
        if (wanted.isEmpty()) {
            return -1;
        }

        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (player.getInventory().getItem(i) == wanted) {
                return i;
            }
        }

        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (ItemStack.matches(player.getInventory().getItem(i), wanted)) {
                return i;
            }
        }

        return -1;
    }
}
