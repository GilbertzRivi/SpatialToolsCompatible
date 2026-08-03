package net.oktawia.spatialtoolscmp.compat;

import java.util.Map;
import java.util.Optional;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;
import top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler;

public final class CuriosOps {

    private CuriosOps() {
    }

    @FunctionalInterface
    public interface StackSkipper {
        boolean shouldSkip(ItemStack stack);
    }

    @FunctionalInterface
    public interface NestedCounter {
        long count(ItemStack stack);
    }

    @FunctionalInterface
    public interface NestedExtractor {
        long extract(ItemStack stack, long amount, boolean simulate);
    }

    public static long countNested(
            Player player,
            StackSkipper skipper,
            NestedCounter counter) {
        if (player == null || skipper == null || counter == null) {
            return 0L;
        }

        Optional<ICuriosItemHandler> optionalHandler = CuriosApi
                .getCuriosInventory(player)
                .resolve();

        if (optionalHandler.isEmpty()) {
            return 0L;
        }

        long total = 0L;

        for (ICurioStacksHandler stacksHandler : optionalHandler.get().getCurios().values()) {
            IDynamicStackHandler stacks = stacksHandler.getStacks();

            for (int slot = 0; slot < stacks.getSlots(); slot++) {
                ItemStack stack = stacks.getStackInSlot(slot);

                if (stack.isEmpty() || skipper.shouldSkip(stack)) {
                    continue;
                }

                total += counter.count(stack);
            }
        }

        return total;
    }

    public static long extractNested(
            Player player,
            StackSkipper skipper,
            NestedExtractor extractor,
            long amount,
            boolean simulate) {
        if (player == null || skipper == null || extractor == null || amount <= 0) {
            return 0L;
        }

        Optional<ICuriosItemHandler> optionalHandler = CuriosApi
                .getCuriosInventory(player)
                .resolve();

        if (optionalHandler.isEmpty()) {
            return 0L;
        }

        long remaining = amount;
        long extracted = 0L;

        for (Map.Entry<String, ICurioStacksHandler> entry : optionalHandler.get().getCurios().entrySet()) {
            if (remaining <= 0) {
                break;
            }

            IDynamicStackHandler stacks = entry.getValue().getStacks();

            for (int slot = 0; slot < stacks.getSlots(); slot++) {
                if (remaining <= 0) {
                    break;
                }

                ItemStack stack = stacks.getStackInSlot(slot);

                if (stack.isEmpty() || skipper.shouldSkip(stack)) {
                    continue;
                }

                long pulled = extractor.extract(stack, remaining, simulate);

                if (pulled <= 0) {
                    continue;
                }

                extracted += pulled;
                remaining -= pulled;

                if (!simulate) {
                    stacks.setStackInSlot(slot, stack);
                }
            }
        }

        return extracted;
    }
}
