package net.oktawia.spatialtoolscmp.logic.extensions;

import com.gregtechceu.gtceu.api.blockentity.PipeBlockEntity;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.oktawia.spatialtoolscmp.logic.ClientReplacerExtension;
import net.oktawia.spatialtoolscmp.logic.ReplacerContext;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.LinkedHashSet;
import java.util.Queue;
import java.util.Set;

public final class GTCEuClientReplacerExtension implements ClientReplacerExtension {

    @Override
    public boolean canHandleSource(ClientLevel level, BlockPos pos, BlockState state) {
        return level.getBlockEntity(pos) instanceof PipeBlockEntity<?, ?>;
    }

    @Override
    @Nullable
    public Set<BlockPos> computePreviewPositions(ClientLevel level, BlockPos pos, BlockState state, ReplacerContext ctx) {
        try {
            int hardCap = ctx.hardCapMax();
            Set<BlockPos> visited = new LinkedHashSet<>();
            Queue<BlockPos> queue = new ArrayDeque<>();
            visited.add(pos.immutable());
            queue.add(pos.immutable());

            while (!queue.isEmpty() && visited.size() < hardCap) {
                BlockPos current = queue.poll();
                BlockEntity be = level.getBlockEntity(current);
                if (!(be instanceof PipeBlockEntity<?, ?> pipe)) continue;

                int connections = pipe.getConnections();
                for (Direction dir : Direction.values()) {
                    if (!PipeBlockEntity.isConnected(connections, dir)) continue;
                    BlockPos neighbor = current.relative(dir).immutable();
                    if (visited.contains(neighbor)) continue;
                    if (level.getBlockState(neighbor).getBlock() != state.getBlock()) continue;
                    visited.add(neighbor);
                    queue.add(neighbor);
                }
            }
            return visited;
        } catch (Throwable ignored) {
            return null;
        }
    }
}
