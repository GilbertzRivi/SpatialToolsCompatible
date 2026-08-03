package net.oktawia.spatialtoolscmp.logic;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

public record PlacementPlan(
        boolean shouldPlace,
        @Nullable BlockState stateToPlace,
        @Nullable CompoundTag blockEntityTag,
        List<ItemStack> consumedStacks) {
    public static PlacementPlan none() {
        return new PlacementPlan(false, null, null, List.of());
    }
}
