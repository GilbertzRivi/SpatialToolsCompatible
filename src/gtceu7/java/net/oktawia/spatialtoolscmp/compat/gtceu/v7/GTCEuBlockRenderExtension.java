package net.oktawia.spatialtoolscmp.compat.gtceu.v7;

import java.util.LinkedHashSet;
import java.util.Set;

import com.gregtechceu.gtceu.api.block.PipeBlock;
import com.gregtechceu.gtceu.api.pipenet.IPipeNode;
import com.gregtechceu.gtceu.client.model.GTModelProperties;
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
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.registries.ForgeRegistries;

import net.oktawia.spatialtoolscmp.client.renderer.BlockRenderExtension;
import net.oktawia.spatialtoolscmp.client.renderer.PreviewBlock;
import net.oktawia.spatialtoolscmp.client.renderer.PreviewBlockAndTintGetter;

public final class GTCEuBlockRenderExtension implements BlockRenderExtension {

    private static final RenderType PIPE_RENDER_TYPE = RenderType.cutoutMipped();

    @Override
    public boolean canRender(BlockState state, @Nullable CompoundTag rawBeTag) {
        return state.getBlock() instanceof PipeBlock<?, ?, ?>;
    }

    @Override
    public @Nullable Iterable<RenderType> getPreviewRenderTypes(
            PreviewBlock previewBlock,
            int[] sideMap,
            BlockRenderDispatcher dispatcher,
            PreviewBlockAndTintGetter localLevel,
            BakedModel model,
            BlockState state,
            BlockPos localPos,
            long seed,
            ModelData modelData) {
        if (!(state.getBlock() instanceof PipeBlock<?, ?, ?>)) {
            return null;
        }

        CompoundTag tag = previewBlock.blockEntityTag();

        if (tag == null) {
            return null;
        }

        Set<RenderType> types = new LinkedHashSet<>();
        types.add(PIPE_RENDER_TYPE);

        ModelData gregModelData = createGregPipeModelData(
                localLevel,
                model,
                state,
                localPos,
                tag,
                modelData);

        try {
            for (RenderType type : model.getRenderTypes(
                    state,
                    RandomSource.create(seed),
                    gregModelData)) {
                types.add(type);
            }
        } catch (Throwable ignored) {
        }

        if (getPipeNode(localLevel, localPos) == null) {
            BlockState frameState = getGregFrameState(tag);

            if (frameState != null) {
                BakedModel frameModel = dispatcher.getBlockModel(frameState);

                for (RenderType frameType : frameModel.getRenderTypes(
                        frameState,
                        RandomSource.create(seed),
                        ModelData.EMPTY)) {
                    types.add(frameType);
                }
            }
        }

        return types;
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
        if (!(state.getBlock() instanceof PipeBlock<?, ?, ?>)) {
            return false;
        }

        CompoundTag tag = previewBlock.blockEntityTag();

        if (tag == null) {
            return false;
        }

        boolean renderedAnything = renderRealGregPipeModel(
                modelRenderer,
                localLevel,
                model,
                state,
                localPos,
                tag,
                poseStack,
                vertexConsumer,
                renderType,
                seed,
                modelData);

        if (getPipeNode(localLevel, localPos) == null) {
            renderedAnything |= renderFallbackFrame(
                    dispatcher,
                    modelRenderer,
                    localLevel,
                    tag,
                    localPos,
                    poseStack,
                    vertexConsumer,
                    renderType,
                    seed);
        }

        return renderedAnything;
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
        if (!(state.getBlock() instanceof PipeBlock<?, ?, ?>)) {
            return false;
        }

        CompoundTag tag = previewBlock.blockEntityTag();

        if (tag == null) {
            return false;
        }

        Set<RenderType> renderTypes = collectWidgetRenderTypes(
                dispatcher,
                localLevel,
                model,
                state,
                localPos,
                tag,
                seed);

        boolean renderedAnything = false;

        for (RenderType renderType : renderTypes) {
            VertexConsumer consumer = bufferSource.getBuffer(toGuiSafeRenderType(renderType));

            renderedAnything |= renderRealGregPipeModel(
                    dispatcher.getModelRenderer(),
                    localLevel,
                    model,
                    state,
                    localPos,
                    tag,
                    poseStack,
                    consumer,
                    renderType,
                    seed,
                    ModelData.EMPTY);

            if (getPipeNode(localLevel, localPos) == null) {
                renderedAnything |= renderFallbackFrame(
                        dispatcher,
                        dispatcher.getModelRenderer(),
                        localLevel,
                        tag,
                        localPos,
                        poseStack,
                        consumer,
                        renderType,
                        seed);
            }
        }

        return renderedAnything || !renderTypes.isEmpty();
    }

    private static boolean renderRealGregPipeModel(
            ModelBlockRenderer modelRenderer,
            PreviewBlockAndTintGetter localLevel,
            BakedModel model,
            BlockState state,
            BlockPos localPos,
            CompoundTag tag,
            PoseStack poseStack,
            VertexConsumer vertexConsumer,
            RenderType renderType,
            long seed,
            ModelData sourceModelData) {
        ModelData gregModelData = createGregPipeModelData(
                localLevel,
                model,
                state,
                localPos,
                tag,
                sourceModelData);

        CountingVertexConsumer countingConsumer = new CountingVertexConsumer(vertexConsumer);

        try {
            modelRenderer.tesselateBlock(
                    localLevel,
                    model,
                    state,
                    localPos,
                    poseStack,
                    countingConsumer,
                    false,
                    RandomSource.create(seed),
                    seed,
                    OverlayTexture.NO_OVERLAY,
                    gregModelData,
                    renderType);
        } catch (Throwable ignored) {
            return false;
        }

        return countingConsumer.vertexCount() > 0;
    }

    private static ModelData createGregPipeModelData(
            PreviewBlockAndTintGetter localLevel,
            BakedModel model,
            BlockState state,
            BlockPos localPos,
            CompoundTag tag,
            ModelData sourceModelData) {
        ModelData inputData = sourceModelData == null ? ModelData.EMPTY : sourceModelData;

        IPipeNode<?, ?> pipeNode = getPipeNode(localLevel, localPos);

        int connections = tag.getInt("connections");
        int blockedConnections = tag.getInt("blockedConnections");

        if (pipeNode != null) {
            try {
                connections = pipeNode.getVisualConnections();
                blockedConnections = pipeNode.getBlockedConnections();
            } catch (Throwable ignored) {
            }
        }

        ModelData baseData = ModelData.builder()
                .with(GTModelProperties.LEVEL, localLevel)
                .with(GTModelProperties.POS, localPos)
                .with(GTModelProperties.PIPE_CONNECTION_MASK, connections)
                .with(GTModelProperties.PIPE_BLOCKED_MASK, blockedConnections)
                .build();

        try {
            ModelData discoveredData = model.getModelData(
                    localLevel,
                    localPos,
                    state,
                    baseData);

            if (discoveredData != null) {
                return discoveredData;
            }
        } catch (Throwable ignored) {
        }

        try {
            ModelData discoveredData = model.getModelData(
                    localLevel,
                    localPos,
                    state,
                    inputData);

            if (discoveredData != null) {
                return discoveredData;
            }
        } catch (Throwable ignored) {
        }

        return baseData;
    }

    private static boolean renderFallbackFrame(
            BlockRenderDispatcher dispatcher,
            ModelBlockRenderer modelRenderer,
            PreviewBlockAndTintGetter localLevel,
            CompoundTag tag,
            BlockPos localPos,
            PoseStack poseStack,
            VertexConsumer vertexConsumer,
            RenderType renderType,
            long seed) {
        BlockState frameState = getGregFrameState(tag);

        if (frameState == null) {
            return false;
        }

        BakedModel frameModel = dispatcher.getBlockModel(frameState);

        if (!shouldRenderFrameInPass(frameModel, frameState, renderType, seed)) {
            return false;
        }

        CountingVertexConsumer countingConsumer = new CountingVertexConsumer(vertexConsumer);

        try {
            modelRenderer.tesselateBlock(
                    localLevel,
                    frameModel,
                    frameState,
                    localPos,
                    poseStack,
                    countingConsumer,
                    false,
                    RandomSource.create(seed),
                    seed,
                    OverlayTexture.NO_OVERLAY,
                    ModelData.EMPTY,
                    renderType);
        } catch (Throwable ignored) {
            return false;
        }

        return countingConsumer.vertexCount() > 0;
    }

    private static @Nullable IPipeNode<?, ?> getPipeNode(
            PreviewBlockAndTintGetter localLevel,
            BlockPos localPos) {
        if (localLevel.getBlockEntity(localPos) instanceof IPipeNode<?, ?> pipeNode) {
            return pipeNode;
        }

        return null;
    }

    private static Set<RenderType> collectWidgetRenderTypes(
            BlockRenderDispatcher dispatcher,
            PreviewBlockAndTintGetter localLevel,
            BakedModel model,
            BlockState state,
            BlockPos localPos,
            CompoundTag tag,
            long seed) {
        Set<RenderType> renderTypes = new LinkedHashSet<>();
        renderTypes.add(PIPE_RENDER_TYPE);

        ModelData gregModelData = createGregPipeModelData(
                localLevel,
                model,
                state,
                localPos,
                tag,
                ModelData.EMPTY);

        try {
            for (RenderType type : model.getRenderTypes(
                    state,
                    RandomSource.create(seed),
                    gregModelData)) {
                renderTypes.add(type);
            }
        } catch (Throwable ignored) {
        }

        if (getPipeNode(localLevel, localPos) == null) {
            BlockState frameState = getGregFrameState(tag);

            if (frameState != null) {
                BakedModel frameModel = dispatcher.getBlockModel(frameState);

                for (RenderType frameType : frameModel.getRenderTypes(
                        frameState,
                        RandomSource.create(seed),
                        ModelData.EMPTY)) {
                    renderTypes.add(frameType);
                }
            }
        }

        return renderTypes;
    }

    private static RenderType toGuiSafeRenderType(RenderType renderType) {
        String name = renderType.toString().toLowerCase();

        if (renderType == RenderType.translucent()
                || renderType == RenderType.translucentMovingBlock()
                || renderType == RenderType.tripwire()
                || name.contains("translucent")
                || name.contains("tripwire")) {
            return RenderType.entityTranslucent(TextureAtlas.LOCATION_BLOCKS);
        }

        return RenderType.entityCutout(TextureAtlas.LOCATION_BLOCKS);
    }

    private static boolean shouldRenderFrameInPass(
            BakedModel frameModel,
            BlockState frameState,
            RenderType renderType,
            long seed) {
        for (RenderType frameType : frameModel.getRenderTypes(
                frameState,
                RandomSource.create(seed),
                ModelData.EMPTY)) {
            if (frameType == renderType) {
                return true;
            }
        }

        return false;
    }

    private static final class CountingVertexConsumer implements VertexConsumer {

        private final VertexConsumer delegate;
        private int vertexCount;

        private CountingVertexConsumer(VertexConsumer delegate) {
            this.delegate = delegate;
        }

        private int vertexCount() {
            return vertexCount;
        }

        @Override
        public VertexConsumer vertex(double x, double y, double z) {
            delegate.vertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer color(int red, int green, int blue, int alpha) {
            delegate.color(red, green, blue, alpha);
            return this;
        }

        @Override
        public VertexConsumer uv(float u, float v) {
            delegate.uv(u, v);
            return this;
        }

        @Override
        public VertexConsumer overlayCoords(int u, int v) {
            delegate.overlayCoords(u, v);
            return this;
        }

        @Override
        public VertexConsumer uv2(int u, int v) {
            delegate.uv2(u, v);
            return this;
        }

        @Override
        public VertexConsumer normal(float x, float y, float z) {
            delegate.normal(x, y, z);
            return this;
        }

        @Override
        public void endVertex() {
            vertexCount++;
            delegate.endVertex();
        }

        @Override
        public void defaultColor(int red, int green, int blue, int alpha) {
            delegate.defaultColor(red, green, blue, alpha);
        }

        @Override
        public void unsetDefaultColor() {
            delegate.unsetDefaultColor();
        }
    }

    @Nullable
    private static BlockState getGregFrameState(CompoundTag tag) {
        if (!tag.contains("frameMaterial")) {
            return null;
        }

        return getGregFrameState(tag.getString("frameMaterial"));
    }

    @Nullable
    private static BlockState getGregFrameState(String frameMaterial) {
        if (frameMaterial == null || frameMaterial.isBlank()) {
            return null;
        }

        String materialPath = frameMaterial;
        int separator = materialPath.indexOf(':');

        if (separator >= 0 && separator + 1 < materialPath.length()) {
            materialPath = materialPath.substring(separator + 1);
        }

        ResourceLocation frameId = ResourceLocation.fromNamespaceAndPath(
                "gtceu",
                materialPath + "_frame");

        Block frameBlock = ForgeRegistries.BLOCKS.getValue(frameId);

        if (frameBlock == null || frameBlock == Blocks.AIR) {
            return null;
        }

        return frameBlock.defaultBlockState();
    }
}
