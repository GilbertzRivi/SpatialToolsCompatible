package net.oktawia.spatialtoolscmp.items.helpers;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.oktawia.spatialtoolscmp.logic.PlacementPlan;
import org.jetbrains.annotations.Nullable;

public final class ClonerBlockPlacer {

    private ClonerBlockPlacer() {}

    public static boolean placeRegularBlockBestEffort(
            ServerLevel level,
            BlockPos worldPos,
            BlockState stateToPlace,
            Player player,
            ItemStack toolStack
    ) {
        BlockState existing = level.getBlockState(worldPos);

        if (hasCollision(existing, stateToPlace)) {
            return false;
        }

        if (!player.isCreative()) {
            ItemStack required = getRequiredBlockItem(stateToPlace);

            if (required.isEmpty()) {
                return false;
            }

            if (ClonerInventoryAccess.countAvailableForPaste(level, player, toolStack, required) < 1) {
                return false;
            }
        }

        if (!placeBlockAndLoadTag(level, worldPos, stateToPlace, null)) {
            return false;
        }

        if (!player.isCreative()) {
            ItemStack required = getRequiredBlockItem(stateToPlace);
            return ClonerInventoryAccess.consumeForPaste(level, player, toolStack, required, 1);
        }

        return true;
    }

    public static boolean placePlannedBlockBestEffort(
            ServerLevel level,
            BlockPos worldPos,
            PlacementPlan plan,
            Player player,
            ItemStack toolStack
    ) {
        if (!plan.shouldPlace()) {
            return false;
        }

        BlockState stateToPlace = plan.stateToPlace();

        if (stateToPlace == null) {
            return false;
        }

        BlockState existing = level.getBlockState(worldPos);

        if (hasCollision(existing, stateToPlace)) {
            return false;
        }

        if (!placeBlockAndLoadTag(level, worldPos, stateToPlace, plan.blockEntityTag())) {
            return false;
        }

        if (!player.isCreative()) {
            for (ItemStack cost : plan.consumedStacks()) {
                if (!ClonerInventoryAccess.consumeForPaste(level, player, toolStack, cost, cost.getCount())) {
                    return false;
                }
            }
        }

        return true;
    }

    public static boolean placeBlockAndLoadTag(
            ServerLevel level,
            BlockPos worldPos,
            BlockState stateToPlace,
            @Nullable CompoundTag rawBeTag
    ) {
        if (hasCollision(level.getBlockState(worldPos), stateToPlace)) {
            return false;
        }

        return replaceBlockAndLoadTag(level, worldPos, stateToPlace, rawBeTag);
    }

    public static boolean replaceBlockAndLoadTag(
            ServerLevel level,
            BlockPos worldPos,
            BlockState stateToPlace,
            @Nullable CompoundTag rawBeTag
    ) {
        BlockState oldState = level.getBlockState(worldPos);

        if (level.getBlockEntity(worldPos) != null) {
            level.removeBlockEntity(worldPos);
        }

        if (!oldState.isAir()) {
            level.setBlock(worldPos, Blocks.AIR.defaultBlockState(), 3);
        }

        if (!level.setBlock(worldPos, stateToPlace, 3)) {
            return false;
        }

        if (rawBeTag != null) {
            BlockEntity blockEntity = level.getBlockEntity(worldPos);

            if (blockEntity != null) {
                CompoundTag beTag = rawBeTag.copy();

                beTag.putInt("x", worldPos.getX());
                beTag.putInt("y", worldPos.getY());
                beTag.putInt("z", worldPos.getZ());

                try {
                    blockEntity.load(beTag);
                    blockEntity.setChanged();
                } catch (Throwable ignored) {
                }
            }
        }

        level.sendBlockUpdated(worldPos, oldState, stateToPlace, 3);
        return true;
    }

    public static boolean hasCollision(BlockState existing, BlockState target) {
        if (existing.equals(target)) {
            return true;
        }

        return !(existing.isAir() || existing.canBeReplaced());
    }

    public static ItemStack getRequiredBlockItem(BlockState state) {
        Item item = state.getBlock().asItem();

        if (item == ItemStack.EMPTY.getItem() || item == Items.AIR) {
            return ItemStack.EMPTY;
        }

        ItemStack stack = new ItemStack(item);
        stack.setCount(1);
        stack.setTag(null);
        return stack;
    }
}
