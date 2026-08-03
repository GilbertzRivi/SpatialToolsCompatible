package net.oktawia.spatialtoolscmp.client.renderer.extensions;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import org.jetbrains.annotations.Nullable;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.data.ModelData;

import net.oktawia.faststone.blocks.LogicCableBlock;
import net.oktawia.spatialtoolscmp.client.renderer.BlockRenderExtension;
import net.oktawia.spatialtoolscmp.client.renderer.PreviewBlock;
import net.oktawia.spatialtoolscmp.client.renderer.PreviewBlockAndTintGetter;

public final class FaststoneBlockRenderExtension implements BlockRenderExtension {

    @Override
    public boolean canRender(BlockState state, @Nullable CompoundTag rawBeTag) {
        return state.getBlock() instanceof LogicCableBlock;
    }

    @Override
    public boolean renderForPreview(
            PreviewBlock previewBlock,
            int[] sideMap,
            BlockRenderDispatcher dispatcher,
            ModelBlockRenderer modelRenderer,
            PreviewBlockAndTintGetter localLevel,
            BakedModel model,
            BlockState state,
            BlockPos localPos,
            PoseStack poseStack,
            VertexConsumer vertexConsumer,
            RenderType renderType,
            long seed,
            ModelData modelData) {
        BlockState transformedState = transformCableState(state, sideMap);
        BakedModel transformedModel = dispatcher.getBlockModel(transformedState);

        modelRenderer.tesselateBlock(
                localLevel,
                transformedModel,
                transformedState,
                localPos,
                poseStack,
                vertexConsumer,
                false,
                RandomSource.create(seed),
                seed,
                OverlayTexture.NO_OVERLAY,
                modelData,
                renderType);

        return true;
    }

    @Override
    public boolean renderForWidget(
            PreviewBlock previewBlock,
            int[] sideMap,
            BlockRenderDispatcher dispatcher,
            PreviewBlockAndTintGetter localLevel,
            BlockState state,
            BakedModel model,
            BlockPos localPos,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            long seed) {
        BlockState transformedState = transformCableState(state, sideMap);
        BakedModel transformedModel = dispatcher.getBlockModel(transformedState);

        for (RenderType renderType : transformedModel.getRenderTypes(
                transformedState,
                RandomSource.create(seed),
                ModelData.EMPTY)) {
            dispatcher.getModelRenderer().tesselateBlock(
                    localLevel,
                    transformedModel,
                    transformedState,
                    localPos,
                    poseStack,
                    bufferSource.getBuffer(toGuiSafeRenderType(renderType)),
                    false,
                    RandomSource.create(seed),
                    seed,
                    OverlayTexture.NO_OVERLAY,
                    ModelData.EMPTY,
                    renderType);
        }

        return true;
    }

    @Nullable
    @Override
    public Iterable<RenderType> getPreviewRenderTypes(
            PreviewBlock previewBlock,
            int[] sideMap,
            BlockRenderDispatcher dispatcher,
            PreviewBlockAndTintGetter localLevel,
            BakedModel model,
            BlockState state,
            BlockPos localPos,
            long seed,
            ModelData modelData) {
        BlockState transformedState = transformCableState(state, sideMap);
        BakedModel transformedModel = dispatcher.getBlockModel(transformedState);

        return transformedModel.getRenderTypes(
                transformedState,
                RandomSource.create(seed),
                modelData);
    }

    private static BlockState transformCableState(BlockState state, int[] sideMap) {
        if (!(state.getBlock() instanceof LogicCableBlock)) {
            return state;
        }

        BlockState transformed = state
                .setValue(LogicCableBlock.NORTH, false)
                .setValue(LogicCableBlock.SOUTH, false)
                .setValue(LogicCableBlock.WEST, false)
                .setValue(LogicCableBlock.EAST, false)
                .setValue(LogicCableBlock.UP, false)
                .setValue(LogicCableBlock.DOWN, false);

        for (Direction originalSide : Direction.values()) {
            if (!state.getValue(LogicCableBlock.prop(originalSide))) {
                continue;
            }

            Direction mappedSide = mapWithSideMap(originalSide, sideMap);

            transformed = transformed.setValue(
                    LogicCableBlock.prop(mappedSide),
                    true);
        }

        return transformed;
    }

    private static Direction mapWithSideMap(Direction side, int[] sideMap) {
        if (sideMap == null || side.ordinal() < 0 || side.ordinal() >= sideMap.length) {
            return side;
        }

        int mappedOrdinal = sideMap[side.ordinal()];
        Direction[] values = Direction.values();

        if (mappedOrdinal < 0 || mappedOrdinal >= values.length) {
            return side;
        }

        return values[mappedOrdinal];
    }

    private static RenderType toGuiSafeRenderType(RenderType renderType) {
        String name = renderType.toString();

        if (name.contains("translucent")) {
            return RenderType.entityTranslucent(TextureAtlas.LOCATION_BLOCKS);
        }

        return RenderType.entityCutout(TextureAtlas.LOCATION_BLOCKS);
    }
}
