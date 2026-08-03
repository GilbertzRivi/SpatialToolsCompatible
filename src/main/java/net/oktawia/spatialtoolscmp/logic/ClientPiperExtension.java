package net.oktawia.spatialtoolscmp.logic;

import java.util.Set;

import com.mojang.blaze3d.vertex.PoseStack;

import org.jetbrains.annotations.Nullable;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.data.ModelData;

public interface ClientPiperExtension {

    @Nullable
    default PiperExtension.PathAction resolvePathAction(
            ClientLevel level,
            BlockPos pos,
            BlockState state,
            ItemStack target) {
        return null;
    }

    @Nullable
    default BlockState resolveTargetState(ItemStack target) {
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

    @Nullable
    default Iterable<RenderType> getPreviewRenderTypes(BlockState targetState, ItemStack target) {
        return null;
    }

    default boolean renderRouteBlock(
            BlockState targetState,
            ItemStack target,
            BlockPos pos,
            Set<BlockPos> allPositions,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            RenderType renderType,
            ModelData modelData,
            long seed) {
        return false;
    }
}
