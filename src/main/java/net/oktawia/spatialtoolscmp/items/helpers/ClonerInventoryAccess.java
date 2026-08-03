package net.oktawia.spatialtoolscmp.items.helpers;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;

import net.oktawia.spatialtoolscmp.IsModLoaded;
import net.oktawia.spatialtoolscmp.compat.CuriosOps;
import net.oktawia.spatialtoolscmp.compat.SophisticatedStorageOps;
import net.oktawia.spatialtoolscmp.compat.ae2.AE2MEOps;
import net.oktawia.spatialtoolscmp.items.PortableSpatialCloner;

public final class ClonerInventoryAccess {

    private ClonerInventoryAccess() {
    }

    public record ClonerRefundResult(boolean success, boolean insertedIntoMe) {
        public static ClonerRefundResult success(boolean insertedIntoMe) {
            return new ClonerRefundResult(true, insertedIntoMe);
        }

        public static ClonerRefundResult failure(boolean insertedIntoMe) {
            return new ClonerRefundResult(false, insertedIntoMe);
        }
    }

    public static long countAvailableForPaste(
            ServerLevel level,
            Player player,
            ItemStack toolStack,
            ItemStack wanted) {
        if (wanted.isEmpty()) {
            return 0;
        }

        long total = countInPlayerInventory(player, wanted);

        total += extractFromMe(level, player, toolStack, wanted, Integer.MAX_VALUE, true);

        PortableSpatialCloner.NestedInventoryResourceMode nestedMode = PortableSpatialCloner
                .getNestedInventoryResourceMode(toolStack);

        if (nestedMode.usePlayerNested()) {
            total += countNestedInventoryInPlayerInventory(level, player, toolStack, wanted);
        }

        if (nestedMode.useConnectedNested() && ClonerItemHandlerLink.hasItemHandlerLink(toolStack)) {
            total += countNestedInventoryInLinkedItemHandlerStorage(level, toolStack, wanted);
        }

        return total;
    }

    public static boolean canReserveForPaste(
            ServerLevel level,
            Player player,
            ItemStack toolStack,
            Map<Item, Integer> reserved,
            ItemStack wanted,
            int amount) {
        if (wanted.isEmpty() || amount <= 0) {
            return false;
        }

        long available = countAvailableForPaste(level, player, toolStack, wanted);
        int alreadyReserved = reserved.getOrDefault(wanted.getItem(), 0);

        if (available < alreadyReserved + amount) {
            return false;
        }

        reserved.put(wanted.getItem(), alreadyReserved + amount);
        return true;
    }

    public static boolean consumeForPaste(
            ServerLevel level,
            Player player,
            ItemStack toolStack,
            ItemStack wanted,
            int amount) {
        if (wanted.isEmpty() || amount <= 0) {
            return true;
        }

        PortableSpatialCloner.NestedInventoryResourceMode nestedMode = PortableSpatialCloner
                .getNestedInventoryResourceMode(toolStack);

        int remaining = amount;

        int inInv = countInPlayerInventory(player, wanted);
        int fromInv = Math.min(inInv, remaining);
        remaining -= fromInv;

        long fromConnected = 0L;

        if (remaining > 0) {
            fromConnected = extractFromMe(level, player, toolStack, wanted, remaining, true);
            remaining -= (int) Math.min(remaining, fromConnected);
        }

        long fromNestedPlayer = 0L;

        if (remaining > 0 && nestedMode.usePlayerNested()) {
            fromNestedPlayer = extractNestedFromPlayerInventory(level, player, toolStack, wanted, remaining, true);
            remaining -= (int) Math.min(remaining, fromNestedPlayer);
        }

        long fromNestedConnected = 0L;

        if (remaining > 0 && nestedMode.useConnectedNested() && ClonerItemHandlerLink.hasItemHandlerLink(toolStack)) {
            fromNestedConnected = extractNestedFromConnectedStorage(level, toolStack, wanted, remaining, true);
            remaining -= (int) Math.min(remaining, fromNestedConnected);
        }

        if (remaining > 0) {
            return false;
        }

        int leftToTake = amount;

        if (fromInv > 0) {
            int removed = consumeFromPlayerInventoryPartial(player, wanted, fromInv);

            if (removed < fromInv) {
                return false;
            }

            leftToTake -= removed;
        }

        if (leftToTake > 0 && fromConnected > 0) {
            long toExtract = Math.min(leftToTake, fromConnected);
            long extracted = extractFromMe(level, player, toolStack, wanted, toExtract, false);

            if (extracted < toExtract) {
                return false;
            }

            leftToTake -= (int) extracted;
        }

        if (leftToTake > 0 && fromNestedPlayer > 0 && nestedMode.usePlayerNested()) {
            long toExtract = Math.min(leftToTake, fromNestedPlayer);
            long extracted = extractNestedFromPlayerInventory(level, player, toolStack, wanted, toExtract, false);

            if (extracted < toExtract) {
                return false;
            }

            leftToTake -= (int) extracted;
        }

        if (leftToTake > 0 && fromNestedConnected > 0 && nestedMode.useConnectedNested()
                && ClonerItemHandlerLink.hasItemHandlerLink(toolStack)) {
            long toExtract = Math.min(leftToTake, fromNestedConnected);
            long extracted = extractNestedFromConnectedStorage(level, toolStack, wanted, toExtract, false);

            if (extracted < toExtract) {
                return false;
            }

            leftToTake -= (int) extracted;
        }

        return leftToTake <= 0;
    }

    public static boolean canStoreRefundStacks(
            ServerLevel level,
            Player player,
            ItemStack toolStack,
            List<ItemStack> refundStacks) {
        List<ItemStack> inventoryRemainders = new ArrayList<>();

        for (ItemStack refundStack : ClonerUndoHandler.aggregateRefundStacks(refundStacks)) {
            int count = refundStack.getCount();
            long inserted = insertIntoMe(level, player, toolStack, refundStack, count, true);

            int remaining = count - (int) Math.min(count, inserted);

            if (remaining > 0) {
                ItemStack remainder = refundStack.copy();
                remainder.setCount(remaining);
                inventoryRemainders.add(remainder);
            }
        }

        return canFitInInventory(player, inventoryRemainders);
    }

    public static ClonerRefundResult refundStacksToAeThenInventory(
            ServerLevel level,
            Player player,
            ItemStack toolStack,
            List<ItemStack> refundStacks) {
        boolean insertedIntoMe = false;

        for (ItemStack refundStack : ClonerUndoHandler.aggregateRefundStacks(refundStacks)) {
            int count = refundStack.getCount();
            long inserted = insertIntoMe(level, player, toolStack, refundStack, count, false);

            if (inserted > 0) {
                insertedIntoMe = true;
            }

            int remaining = count - (int) Math.min(count, inserted);

            while (remaining > 0) {
                ItemStack part = refundStack.copy();
                int partCount = Math.min(remaining, part.getMaxStackSize());

                part.setCount(partCount);

                boolean added = player.getInventory().add(part);

                if (!added || !part.isEmpty()) {
                    player.getInventory().setChanged();
                    return ClonerRefundResult.failure(insertedIntoMe);
                }

                remaining -= partCount;
            }
        }

        player.getInventory().setChanged();
        return ClonerRefundResult.success(insertedIntoMe);
    }

    public static boolean canFitInInventory(Player player, List<ItemStack> stacks) {
        if (stacks.isEmpty()) {
            return true;
        }

        List<ItemStack> simulated = new ArrayList<>();

        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            simulated.add(player.getInventory().getItem(i).copy());
        }

        for (ItemStack wanted : stacks) {
            if (wanted.isEmpty()) {
                continue;
            }

            int remaining = wanted.getCount();

            for (ItemStack slot : simulated) {
                if (remaining <= 0) {
                    break;
                }

                if (slot.isEmpty()) {
                    continue;
                }

                if (!ItemStack.isSameItemSameTags(slot, wanted)) {
                    continue;
                }

                int max = Math.min(slot.getMaxStackSize(), wanted.getMaxStackSize());
                int space = max - slot.getCount();

                if (space <= 0) {
                    continue;
                }

                int inserted = Math.min(space, remaining);
                slot.grow(inserted);
                remaining -= inserted;
            }

            for (int i = 0; i < simulated.size(); i++) {
                if (remaining <= 0) {
                    break;
                }

                ItemStack slot = simulated.get(i);

                if (!slot.isEmpty()) {
                    continue;
                }

                int inserted = Math.min(wanted.getMaxStackSize(), remaining);
                ItemStack copy = wanted.copy();

                copy.setCount(inserted);
                simulated.set(i, copy);
                remaining -= inserted;
            }

            if (remaining > 0) {
                return false;
            }
        }

        return true;
    }

    public static long insertIntoMe(
            ServerLevel level,
            Player player,
            ItemStack toolStack,
            ItemStack wanted,
            long amount,
            boolean simulate) {
        if (wanted.isEmpty() || amount <= 0) {
            return 0;
        }

        if (ClonerItemHandlerLink.hasItemHandlerLink(toolStack)) {
            IItemHandler handler = ClonerItemHandlerLink.getLinkedItemHandler(level, toolStack);

            if (handler == null) {
                return 0;
            }

            int requested = (int) Math.min(Integer.MAX_VALUE, amount);
            ItemStack remainder = wanted.copy();
            remainder.setCount(requested);

            for (int slot = 0; slot < handler.getSlots(); slot++) {
                if (remainder.isEmpty()) {
                    break;
                }

                remainder = handler.insertItem(slot, remainder, simulate);
            }

            return requested - remainder.getCount();
        }

        if (IsModLoaded.AE2) {
            try {
                int requested = (int) Math.min(Integer.MAX_VALUE, amount);

                ItemStack toInsert = wanted.copy();
                toInsert.setCount(requested);

                long overflow = AE2MEOps.insert(toInsert, toolStack, level, simulate, player);

                return requested - Math.min(requested, overflow);
            } catch (Throwable ignored) {
                return 0;
            }
        }

        return 0;
    }

    public static long extractFromMe(
            ServerLevel level,
            Player player,
            ItemStack toolStack,
            ItemStack wanted,
            long amount,
            boolean simulate) {
        if (wanted.isEmpty() || amount <= 0) {
            return 0;
        }

        if (ClonerItemHandlerLink.hasItemHandlerLink(toolStack)) {
            IItemHandler handler = ClonerItemHandlerLink.getLinkedItemHandler(level, toolStack);

            if (handler == null) {
                return 0;
            }

            long remaining = amount;
            long extracted = 0;

            for (int slot = 0; slot < handler.getSlots(); slot++) {
                if (remaining <= 0) {
                    break;
                }

                ItemStack inSlot = handler.getStackInSlot(slot);

                if (inSlot.isEmpty() || !ItemStack.isSameItemSameTags(inSlot, wanted)) {
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

        if (IsModLoaded.AE2) {
            try {
                return AE2MEOps.extract(wanted, toolStack, level, amount, simulate, player);
            } catch (Throwable ignored) {
                return 0;
            }
        }

        return 0;
    }

    public static int countInPlayerInventory(Player player, ItemStack wanted) {
        if (wanted.isEmpty()) {
            return 0;
        }

        int found = 0;

        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);

            if (!stack.isEmpty() && stack.getItem() == wanted.getItem()) {
                found += stack.getCount();
            }
        }

        return found;
    }

    public static int consumeFromPlayerInventoryPartial(Player player, ItemStack wanted, int amount) {
        if (wanted.isEmpty() || amount <= 0) {
            return 0;
        }

        int remaining = amount;
        int removed = 0;

        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);

            if (stack.isEmpty() || stack.getItem() != wanted.getItem()) {
                continue;
            }

            int taken = Math.min(remaining, stack.getCount());
            stack.shrink(taken);
            removed += taken;
            remaining -= taken;

            if (remaining <= 0) {
                player.getInventory().setChanged();
                return removed;
            }
        }

        player.getInventory().setChanged();
        return removed;
    }

    public static long countNestedInventoryInPlayerInventory(
            ServerLevel level,
            Player player,
            ItemStack toolStack,
            ItemStack wanted) {
        if (level == null || player == null || wanted.isEmpty()) {
            return 0L;
        }

        long total = 0L;

        total += countNestedInventoryInStacks(
                level,
                player.getInventory().items,
                toolStack,
                wanted);

        total += countNestedInventoryInStacks(
                level,
                player.getInventory().armor,
                toolStack,
                wanted);

        total += countNestedInventoryInStacks(
                level,
                player.getInventory().offhand,
                toolStack,
                wanted);

        if (IsModLoaded.CURIOS) {
            total += CuriosOps.countNested(
                    player,
                    stack -> shouldSkipNestedContainerStack(stack, toolStack),
                    stack -> countNestedInventoryInStack(level, stack, wanted));
        }

        return total;
    }

    public static long countNestedInventoryInLinkedItemHandlerStorage(
            ServerLevel level,
            ItemStack toolStack,
            ItemStack wanted) {
        if (level == null || toolStack.isEmpty() || wanted.isEmpty()) {
            return 0L;
        }

        IItemHandler linked = ClonerItemHandlerLink.getLinkedItemHandler(level, toolStack);

        if (linked == null) {
            return 0L;
        }

        long total = 0L;

        for (int slot = 0; slot < linked.getSlots(); slot++) {
            ItemStack containerStack = linked.getStackInSlot(slot);

            if (shouldSkipNestedContainerStack(containerStack, toolStack)) {
                continue;
            }

            total += countNestedInventoryInStack(level, containerStack, wanted);
        }

        return total;
    }

    private static boolean shouldSkipNestedContainerStack(ItemStack containerStack, ItemStack toolStack) {
        if (containerStack.isEmpty()) {
            return true;
        }

        if (ItemStack.isSameItemSameTags(containerStack, toolStack)) {
            return true;
        }

        return containerStack.getItem() instanceof PortableSpatialCloner;
    }

    private static long countNestedInventoryInStack(
            ServerLevel level,
            ItemStack containerStack,
            ItemStack wanted) {
        if (level == null || containerStack.isEmpty() || wanted.isEmpty()) {
            return 0L;
        }

        if (IsModLoaded.SOPH_STORAGE) {
            try {
                if (SophisticatedStorageOps.isStorageStack(containerStack)) {
                    return SophisticatedStorageOps.count(level.getServer(), containerStack, wanted);
                }
            } catch (Throwable ignored) {
            }
        }

        IItemHandler handler = containerStack
                .getCapability(ForgeCapabilities.ITEM_HANDLER)
                .orElse(null);

        if (handler != null) {
            long total = 0L;

            for (int slot = 0; slot < handler.getSlots(); slot++) {
                ItemStack inSlot = handler.getStackInSlot(slot);

                if (!inSlot.isEmpty() && ItemStack.isSameItemSameTags(inSlot, wanted)) {
                    total += inSlot.getCount();
                }
            }

            return total;
        }

        return countVanillaContainerTagContents(containerStack, wanted);
    }

    private static long countVanillaContainerTagContents(ItemStack containerStack, ItemStack wanted) {
        CompoundTag tag = containerStack.getTag();

        if (tag == null || !tag.contains("BlockEntityTag", Tag.TAG_COMPOUND)) {
            return 0L;
        }

        CompoundTag blockEntityTag = tag.getCompound("BlockEntityTag");

        if (!blockEntityTag.contains("Items", Tag.TAG_LIST)) {
            return 0L;
        }

        ListTag items = blockEntityTag.getList("Items", Tag.TAG_COMPOUND);
        long total = 0L;

        for (int i = 0; i < items.size(); i++) {
            CompoundTag row = items.getCompound(i);
            ItemStack inSlot = ItemStack.of(row);

            if (!inSlot.isEmpty() && ItemStack.isSameItemSameTags(inSlot, wanted)) {
                total += inSlot.getCount();
            }
        }

        return total;
    }

    public static long extractNestedFromPlayerInventory(
            ServerLevel level,
            Player player,
            ItemStack toolStack,
            ItemStack wanted,
            long amount,
            boolean simulate) {
        if (level == null || player == null || wanted.isEmpty() || amount <= 0) {
            return 0L;
        }

        long remaining = amount;
        long extracted = 0L;

        long pulledFromMain = extractNestedFromStacks(
                level,
                player.getInventory().items,
                toolStack,
                wanted,
                remaining,
                simulate);

        extracted += pulledFromMain;
        remaining -= pulledFromMain;

        if (remaining > 0) {
            long pulledFromArmor = extractNestedFromStacks(
                    level,
                    player.getInventory().armor,
                    toolStack,
                    wanted,
                    remaining,
                    simulate);

            extracted += pulledFromArmor;
            remaining -= pulledFromArmor;
        }

        if (remaining > 0) {
            long pulledFromOffhand = extractNestedFromStacks(
                    level,
                    player.getInventory().offhand,
                    toolStack,
                    wanted,
                    remaining,
                    simulate);

            extracted += pulledFromOffhand;
            remaining -= pulledFromOffhand;
        }

        if (remaining > 0 && IsModLoaded.CURIOS) {
            long pulledFromCurios = CuriosOps.extractNested(
                    player,
                    stack -> shouldSkipNestedContainerStack(stack, toolStack),
                    (stack, requested, sim) -> extractNestedFromStack(
                            level,
                            stack,
                            wanted,
                            requested,
                            sim),
                    remaining,
                    simulate);

            extracted += pulledFromCurios;
            remaining -= pulledFromCurios;
        }

        if (!simulate && extracted > 0L) {
            player.getInventory().setChanged();
        }

        return extracted;
    }

    private static long countNestedInventoryInStacks(
            ServerLevel level,
            Iterable<ItemStack> stacks,
            ItemStack toolStack,
            ItemStack wanted) {
        long total = 0L;

        for (ItemStack containerStack : stacks) {
            if (shouldSkipNestedContainerStack(containerStack, toolStack)) {
                continue;
            }

            total += countNestedInventoryInStack(level, containerStack, wanted);
        }

        return total;
    }

    private static long extractNestedFromStacks(
            ServerLevel level,
            Iterable<ItemStack> stacks,
            ItemStack toolStack,
            ItemStack wanted,
            long amount,
            boolean simulate) {
        long remaining = amount;
        long extracted = 0L;

        for (ItemStack containerStack : stacks) {
            if (remaining <= 0) {
                break;
            }

            if (shouldSkipNestedContainerStack(containerStack, toolStack)) {
                continue;
            }

            long pulled = extractNestedFromStack(
                    level,
                    containerStack,
                    wanted,
                    remaining,
                    simulate);

            if (pulled <= 0L) {
                continue;
            }

            extracted += pulled;
            remaining -= pulled;
        }

        return extracted;
    }

    private static long extractNestedFromConnectedStorage(
            ServerLevel level,
            ItemStack toolStack,
            ItemStack wanted,
            long amount,
            boolean simulate) {
        if (!ClonerItemHandlerLink.hasItemHandlerLink(toolStack)) {
            return 0L;
        }

        IItemHandler linked = ClonerItemHandlerLink.getLinkedItemHandler(level, toolStack);

        if (linked == null || wanted.isEmpty() || amount <= 0) {
            return 0L;
        }

        long remaining = amount;
        long extracted = 0L;

        for (int slot = 0; slot < linked.getSlots(); slot++) {
            if (remaining <= 0) {
                break;
            }

            ItemStack containerStack = linked.getStackInSlot(slot);

            if (shouldSkipNestedContainerStack(containerStack, toolStack)) {
                continue;
            }

            ItemStack simulatedContainer = containerStack.copy();
            simulatedContainer.setCount(1);

            long possible = extractNestedFromStack(level, simulatedContainer, wanted, remaining, true);

            if (possible <= 0L) {
                continue;
            }

            long toPull = Math.min(remaining, possible);

            if (simulate) {
                extracted += toPull;
                remaining -= toPull;
                continue;
            }

            ItemStack extractedContainer = linked.extractItem(slot, 1, false);

            if (extractedContainer.isEmpty()) {
                continue;
            }

            ItemStack originalContainer = extractedContainer.copy();
            extractedContainer.setCount(1);

            long pulled = extractNestedFromStack(level, extractedContainer, wanted, toPull, false);

            if (pulled <= 0L) {
                insertIntoItemHandler(linked, originalContainer, false);
                continue;
            }

            ItemStack remainder = insertIntoItemHandler(linked, extractedContainer, false);

            if (!remainder.isEmpty()) {
                insertIntoItemHandler(linked, originalContainer, false);
                continue;
            }

            extracted += pulled;
            remaining -= pulled;
        }

        return extracted;
    }

    private static ItemStack insertIntoItemHandler(IItemHandler handler, ItemStack stack, boolean simulate) {
        ItemStack remainder = stack.copy();

        for (int slot = 0; slot < handler.getSlots(); slot++) {
            if (remainder.isEmpty()) {
                break;
            }

            remainder = handler.insertItem(slot, remainder, simulate);
        }

        return remainder;
    }

    private static long extractNestedFromStack(
            ServerLevel level,
            ItemStack containerStack,
            ItemStack wanted,
            long amount,
            boolean simulate) {
        if (level == null || containerStack.isEmpty() || wanted.isEmpty() || amount <= 0) {
            return 0L;
        }

        if (IsModLoaded.SOPH_STORAGE) {
            try {
                if (SophisticatedStorageOps.isStorageStack(containerStack)) {
                    return SophisticatedStorageOps.extract(
                            level.getServer(),
                            containerStack,
                            wanted,
                            amount,
                            simulate);
                }
            } catch (Throwable ignored) {
            }
        }

        IItemHandler handler = containerStack
                .getCapability(ForgeCapabilities.ITEM_HANDLER)
                .orElse(null);

        if (handler != null) {
            long remaining = amount;
            long extracted = 0L;

            for (int slot = 0; slot < handler.getSlots(); slot++) {
                if (remaining <= 0) {
                    break;
                }

                ItemStack inSlot = handler.getStackInSlot(slot);

                if (inSlot.isEmpty() || !ItemStack.isSameItemSameTags(inSlot, wanted)) {
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

        return extractNestedFromVanillaContainerTag(containerStack, wanted, amount, simulate);
    }

    private static long extractNestedFromVanillaContainerTag(
            ItemStack containerStack,
            ItemStack wanted,
            long amount,
            boolean simulate) {
        CompoundTag tag = containerStack.getTag();

        if (tag == null || !tag.contains("BlockEntityTag", Tag.TAG_COMPOUND)) {
            return 0L;
        }

        CompoundTag blockEntityTag = tag.getCompound("BlockEntityTag");

        if (!blockEntityTag.contains("Items", Tag.TAG_LIST)) {
            return 0L;
        }

        ListTag oldItems = blockEntityTag.getList("Items", Tag.TAG_COMPOUND);
        ListTag newItems = new ListTag();

        long remaining = amount;
        long extracted = 0L;

        for (int i = 0; i < oldItems.size(); i++) {
            CompoundTag oldRow = oldItems.getCompound(i);
            ItemStack inSlot = ItemStack.of(oldRow);

            if (inSlot.isEmpty() || remaining <= 0 || !ItemStack.isSameItemSameTags(inSlot, wanted)) {
                newItems.add(oldRow.copy());
                continue;
            }

            int taken = (int) Math.min(remaining, inSlot.getCount());

            extracted += taken;
            remaining -= taken;

            ItemStack rest = inSlot.copy();
            rest.shrink(taken);

            if (!rest.isEmpty()) {
                CompoundTag newRow = rest.save(new CompoundTag());

                if (oldRow.contains("Slot", Tag.TAG_BYTE)) {
                    newRow.putByte("Slot", oldRow.getByte("Slot"));
                }

                newItems.add(newRow);
            }
        }

        if (!simulate && extracted > 0L) {
            blockEntityTag.put("Items", newItems);
            tag.put("BlockEntityTag", blockEntityTag);
            containerStack.setTag(tag);
        }

        return extracted;
    }
}
