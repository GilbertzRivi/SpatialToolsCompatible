package net.oktawia.spatialtoolscmp.logic;

import java.util.Map;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

public interface ClonerPasteContext {

    long countAvailableForPaste(ItemStack wanted);

    boolean canReserveForPaste(
            Map<Item, Integer> reserved,
            ItemStack wanted,
            int amount);

    boolean consumeForPaste(ItemStack wanted, int amount);

    boolean placeBlockAndLoadTag(
            BlockPos pos,
            BlockState state,
            @Nullable CompoundTag rawBeTag);

    boolean hasCollision(BlockState existing, BlockState target);

    ItemStack getRequiredBlockItem(BlockState state);
}
