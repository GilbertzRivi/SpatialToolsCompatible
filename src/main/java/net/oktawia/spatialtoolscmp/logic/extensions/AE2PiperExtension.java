package net.oktawia.spatialtoolscmp.logic.extensions;

import appeng.api.parts.IPartHost;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.oktawia.spatialtoolscmp.logic.PiperExtension;
import org.jetbrains.annotations.Nullable;

public final class AE2PiperExtension implements PiperExtension {

    @Override
    public @Nullable PathAction resolvePathAction(
            ServerLevel level,
            BlockPos pos,
            BlockState state,
            ItemStack target
    ) {
        return pathAction(level, pos, target);
    }

    @Nullable
    public static PathAction pathAction(BlockGetter level, BlockPos pos, ItemStack target) {
        if (!(level.getBlockEntity(pos) instanceof IPartHost)) {
            return null;
        }

        Item current = AE2ReplacerExtension.centerCableItem(level, pos);

        if (current == null) {
            return PathAction.BLOCKED;
        }

        return current == target.getItem() ? PathAction.SKIP : PathAction.BLOCKED;
    }
}
