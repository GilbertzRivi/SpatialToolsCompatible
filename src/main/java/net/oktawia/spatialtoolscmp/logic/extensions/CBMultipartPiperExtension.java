package net.oktawia.spatialtoolscmp.logic.extensions;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;

import net.oktawia.spatialtoolscmp.logic.PiperExtension;

public final class CBMultipartPiperExtension implements PiperExtension {

    @Nullable
    @Override
    public PathAction resolvePathAction(
            ServerLevel level,
            BlockPos pos,
            BlockState state,
            ItemStack target) {
        return pathAction(level, pos, target);
    }

    @Nullable
    public static PathAction pathAction(BlockGetter level, BlockPos pos, ItemStack target) {
        Item current = CBMultipartReplacerExtension.partItem(level, pos);

        if (current == null) {
            return null;
        }

        return current == target.getItem() ? PathAction.SKIP : PathAction.BLOCKED;
    }
}
