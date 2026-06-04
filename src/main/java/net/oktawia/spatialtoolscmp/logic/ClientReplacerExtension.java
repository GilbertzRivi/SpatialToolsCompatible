package net.oktawia.spatialtoolscmp.logic;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

public interface ClientReplacerExtension {

    boolean canHandleSource(ClientLevel level, BlockPos pos, BlockState state);

    @Nullable
    Set<BlockPos> computePreviewPositions(ClientLevel level, BlockPos pos, BlockState state, ReplacerContext ctx);
}
