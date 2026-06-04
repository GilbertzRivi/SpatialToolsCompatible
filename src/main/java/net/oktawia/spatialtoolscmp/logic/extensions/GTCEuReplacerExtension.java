package net.oktawia.spatialtoolscmp.logic.extensions;

import com.gregtechceu.gtceu.api.block.PipeBlock;
import com.gregtechceu.gtceu.api.blockentity.PipeBlockEntity;
import com.gregtechceu.gtceu.api.pipenet.LevelPipeNet;
import com.gregtechceu.gtceu.api.pipenet.PipeNet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.oktawia.spatialtoolscmp.logic.ReplacerContext;
import net.oktawia.spatialtoolscmp.logic.ReplacerExtension;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashSet;
import java.util.Set;

public final class GTCEuReplacerExtension implements ReplacerExtension {

    @Override
    public boolean canHandleSource(ServerLevel level, BlockPos pos, BlockState state) {
        BlockEntity be = level.getBlockEntity(pos);
        return be instanceof PipeBlockEntity<?, ?>;
    }

    @Override
    public Set<BlockPos> findReplacementTargets(
            ServerLevel level,
            BlockPos startPos,
            BlockState sourceState,
            ReplacerContext ctx
    ) {
        BlockEntity be = level.getBlockEntity(startPos);

        if (!(be instanceof PipeBlockEntity<?, ?> pipe)) {
            return Set.of();
        }

        try {
            PipeBlock<?, ?, ?> pipeBlock = (PipeBlock<?, ?, ?>) pipe.getPipeBlock();
            LevelPipeNet<?, ?> worldPipeNet = pipeBlock.getWorldPipeNet(level);
            PipeNet<?> net = worldPipeNet.getNetFromPos(startPos);

            if (net == null) {
                return Set.of(startPos.immutable());
            }

            int hardCap = ctx.hardCapMax();
            Set<BlockPos> positions = new LinkedHashSet<>();

            for (BlockPos nodePos : net.getAllNodes().keySet()) {
                BlockState nodeState = level.getBlockState(nodePos);
                if (nodeState.getBlock() == sourceState.getBlock()) {
                    positions.add(nodePos.immutable());
                }

                if (positions.size() >= hardCap) {
                    break;
                }
            }

            return positions;
        } catch (Throwable ignored) {
            return Set.of(startPos.immutable());
        }
    }

    @Override
    @Nullable
    public CompoundTag capturePreReplacementState(ServerLevel level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);

        if (!(be instanceof PipeBlockEntity<?, ?> pipe)) {
            return null;
        }

        CompoundTag saved = new CompoundTag();
        saved.putInt("connections", pipe.getConnections());
        saved.putInt("blockedConnections", pipe.getBlockedConnections());
        return saved;
    }

    @Override
    public void onBlockReplaced(ServerLevel level, BlockPos pos, @Nullable CompoundTag savedState) {
        GTCEuStructureExtension.scheduleReplacedPipeInit(level, pos, savedState);
    }

    @Override
    public boolean canHandleTarget(ServerLevel level, Block targetBlock) {
        return targetBlock instanceof PipeBlock<?, ?, ?>;
    }

    @Override
    public void onNewBlocksPlaced(ServerLevel level, Set<BlockPos> positions) {
        for (BlockPos pos : positions) {
            GTCEuStructureExtension.scheduleReplacedPipeInit(level, pos, buildConnectionHint(level, pos, positions));
        }
    }

    private static CompoundTag buildConnectionHint(ServerLevel level, BlockPos pos, Set<BlockPos> allPositions) {
        int connections = 0;
        for (Direction dir : Direction.values()) {
            BlockPos neighbor = pos.relative(dir);
            if (allPositions.contains(neighbor)) {
                connections |= 1 << dir.ordinal();
            }
        }
        CompoundTag tag = new CompoundTag();
        tag.putInt("connections", connections);
        tag.putInt("blockedConnections", 0);
        return tag;
    }
}
