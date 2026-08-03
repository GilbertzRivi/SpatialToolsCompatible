package net.oktawia.spatialtoolscmp.logic;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;

public interface PiperExtension {

    enum PathAction {
        BUILD,
        SKIP,
        BLOCKED
    }

    @Nullable
    default PathAction resolvePathAction(
            ServerLevel level,
            BlockPos pos,
            BlockState state,
            ItemStack target
    ) {
        return null;
    }

    default void onBeforeRouteBuilt(ServerLevel level, Set<BlockPos> positions, ItemStack target) {
    }

    default boolean onPathBuilt(
            ServerLevel level,
            List<BlockPos> orderedPath,
            Set<BlockPos> placed,
            ItemStack target,
            ItemStack toolStack
    ) {
        return false;
    }

    default void onRouteBuilt(ServerLevel level, Set<BlockPos> positions, ItemStack target) {
    }
}
