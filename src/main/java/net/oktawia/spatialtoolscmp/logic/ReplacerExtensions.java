package net.oktawia.spatialtoolscmp.logic;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class ReplacerExtensions {

    private static final CopyOnWriteArrayList<ReplacerExtension> EXTENSIONS = new CopyOnWriteArrayList<>();

    private ReplacerExtensions() {
    }

    public static void register(ReplacerExtension extension) {
        if (!EXTENSIONS.contains(extension)) {
            EXTENSIONS.add(extension);
        }
    }

    public static List<ReplacerExtension> get() {
        return EXTENSIONS;
    }

    public static List<ItemStack> placementCost(ServerLevel level, ItemStack target) {
        for (ReplacerExtension extension : EXTENSIONS) {
            List<ItemStack> cost = extension.getPlacementCost(level, target);

            if (cost != null && !cost.isEmpty()) {
                return mergeCost(cost);
            }
        }

        return List.of(target);
    }

    private static List<ItemStack> mergeCost(List<ItemStack> cost) {
        List<ItemStack> merged = new ArrayList<>();

        for (ItemStack stack : cost) {
            if (stack.isEmpty()) {
                continue;
            }

            ItemStack existing = null;

            for (ItemStack candidate : merged) {
                if (ItemStack.isSameItemSameTags(candidate, stack)) {
                    existing = candidate;
                    break;
                }
            }

            if (existing != null) {
                existing.setCount(existing.getCount() + stack.getCount());
            } else {
                merged.add(stack.copy());
            }
        }

        return merged;
    }
}
