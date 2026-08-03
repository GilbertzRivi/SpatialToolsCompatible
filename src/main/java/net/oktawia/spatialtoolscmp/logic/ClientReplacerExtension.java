package net.oktawia.spatialtoolscmp.logic;

import java.util.Set;

import org.jetbrains.annotations.Nullable;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.data.ModelData;

public interface ClientReplacerExtension {

    boolean canHandleSource(ClientLevel level, BlockPos pos, BlockState state);

    @Nullable
    Set<BlockPos> computePreviewPositions(ClientLevel level, BlockPos pos, BlockState state, ReplacerContext ctx);

    default boolean needsReplacement(ClientLevel level, BlockPos pos, ItemStack target) {
        return false;
    }

    @Nullable
    default BlockState resolveTargetState(ItemStack target) {
        return null;
    }

    @Nullable
    default Iterable<RenderType> getPreviewRenderTypes(BlockState targetState, ItemStack target) {
        return null;
    }

    @Nullable
    default ModelData buildTargetModelData(
            BlockState targetState,
            ItemStack target,
            BlockPos pos,
            Set<BlockPos> allPositions) {
        return null;
    }
}
