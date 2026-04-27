package net.oktawia.spatialtoolscmp.client.renderer.extensions;

import com.gregtechceu.gtceu.api.block.PipeBlock;
import com.gregtechceu.gtceu.api.pipenet.IPipeNode;
import com.gregtechceu.gtceu.client.model.PipeModel;
import com.gregtechceu.gtceu.client.renderer.block.PipeBlockRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.ChunkRenderTypeSet;
import net.minecraftforge.client.model.BakedModelWrapper;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.registries.ForgeRegistries;
import net.oktawia.spatialtoolscmp.client.renderer.BlockRenderExtension;
import net.oktawia.spatialtoolscmp.client.renderer.PreviewBlock;
import net.oktawia.spatialtoolscmp.client.renderer.PreviewBlockAndTintGetter;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static com.lowdragmc.lowdraglib.client.model.forge.LDLRendererModel.RendererBakedModel.CURRENT_MODEL_DATA;
import static com.lowdragmc.lowdraglib.client.model.forge.LDLRendererModel.RendererBakedModel.CURRENT_RENDER_TYPE;
import static com.lowdragmc.lowdraglib.client.model.forge.LDLRendererModel.RendererBakedModel.MODEL_DATA;

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
            ModelData modelData
    ) {
        if (!(state.getBlock() instanceof PipeBlock<?, ?, ?>)) {
            return null;
        }

        CompoundTag tag = previewBlock.blockEntityTag();

        if (tag == null) {
            return null;
        }

        Set<RenderType> types = new LinkedHashSet<>();

        types.add(RenderType.cutout());
        types.add(PIPE_RENDER_TYPE);
        types.add(RenderType.translucent());

        BlockState frameState = getGregFrameState(tag);

        if (frameState != null) {
            BakedModel frameModel = dispatcher.getBlockModel(frameState);

            for (RenderType frameType : frameModel.getRenderTypes(
                    frameState,
                    RandomSource.create(seed),
                    ModelData.EMPTY
            )) {
                types.add(frameType);
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
            ModelData modelData
    ) {
        if (!(state.getBlock() instanceof PipeBlock<?, ?, ?> pipeBlock)) {
            return false;
        }

        CompoundTag tag = previewBlock.blockEntityTag();

        if (tag == null) {
            return false;
        }

        int connections = tag.getInt("connections");
        int blockedConnections = tag.getInt("blockedConnections");

        boolean hasPipeNode = getPipeNode(localLevel, localPos) != null;
        boolean renderedAnything = false;

        if (isPipeOrCoverRenderType(renderType)) {
            PipeBlockRenderer pipeRenderer = pipeBlock.getRenderer(state);

            if (hasPipeNode && pipeRenderer != null) {
                renderedAnything = renderDirectPipeRendererModel(
                        pipeRenderer,
                        modelRenderer,
                        localLevel,
                        model,
                        state,
                        localPos,
                        poseStack,
                        vertexConsumer,
                        renderType,
                        seed,
                        modelData
                );
            }

            if (!renderedAnything && renderType == PIPE_RENDER_TYPE) {
                renderedAnything = renderFallbackPipeModel(
                        pipeBlock,
                        modelRenderer,
                        localLevel,
                        model,
                        state,
                        localPos,
                        poseStack,
                        vertexConsumer,
                        seed,
                        connections,
                        blockedConnections
                );
            }
        }

        if (!hasPipeNode && renderFallbackFrame(
                dispatcher,
                modelRenderer,
                localLevel,
                tag,
                localPos,
                poseStack,
                vertexConsumer,
                renderType,
                seed
        )) {
            return true;
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
            long seed
    ) {
        if (!(state.getBlock() instanceof PipeBlock<?, ?, ?> pipeBlock)) {
            return false;
        }

        CompoundTag tag = previewBlock.blockEntityTag();

        if (tag == null) {
            return false;
        }

        int connections = tag.getInt("connections");
        int blockedConnections = tag.getInt("blockedConnections");

        boolean hasPipeNode = getPipeNode(localLevel, localPos) != null;

        Set<RenderType> renderTypes = collectWidgetRenderTypes(
                dispatcher,
                tag,
                seed
        );

        boolean renderedAnything = false;

        for (RenderType renderType : renderTypes) {
            VertexConsumer consumer = bufferSource.getBuffer(toGuiSafeRenderType(renderType));

            if (isPipeOrCoverRenderType(renderType)) {
                PipeBlockRenderer pipeRenderer = pipeBlock.getRenderer(state);
                boolean renderedPipePass = false;

                if (hasPipeNode && pipeRenderer != null) {
                    renderedPipePass = renderDirectPipeRendererModel(
                            pipeRenderer,
                            dispatcher.getModelRenderer(),
                            localLevel,
                            model,
                            state,
                            localPos,
                            poseStack,
                            consumer,
                            renderType,
                            seed,
                            ModelData.EMPTY
                    );
                }

                if (!renderedPipePass && renderType == PIPE_RENDER_TYPE) {
                    renderedPipePass = renderFallbackPipeModel(
                            pipeBlock,
                            dispatcher.getModelRenderer(),
                            localLevel,
                            model,
                            state,
                            localPos,
                            poseStack,
                            consumer,
                            seed,
                            connections,
                            blockedConnections
                    );
                }

                if (renderedPipePass) {
                    renderedAnything = true;
                }
            }

            if (renderFallbackFrame(
                    dispatcher,
                    dispatcher.getModelRenderer(),
                    localLevel,
                    tag,
                    localPos,
                    poseStack,
                    consumer,
                    renderType,
                    seed
            )) {
                renderedAnything = true;
            }
        }

        return renderedAnything;
    }

    private static @Nullable IPipeNode<?, ?> getPipeNode(
            PreviewBlockAndTintGetter localLevel,
            BlockPos localPos
    ) {
        if (localLevel.getBlockEntity(localPos) instanceof IPipeNode<?, ?> pipeNode) {
            return pipeNode;
        }

        return null;
    }

    private static boolean renderDirectPipeRendererModel(
            PipeBlockRenderer pipeRenderer,
            ModelBlockRenderer modelRenderer,
            PreviewBlockAndTintGetter localLevel,
            BakedModel baseModel,
            BlockState state,
            BlockPos localPos,
            PoseStack poseStack,
            VertexConsumer vertexConsumer,
            RenderType renderType,
            long seed,
            ModelData sourceModelData
    ) {
        BakedModel pipeRendererModel = createDirectPipeRendererModel(
                baseModel,
                pipeRenderer,
                localLevel,
                localPos,
                renderType,
                sourceModelData
        );

        CountingVertexConsumer countingConsumer = new CountingVertexConsumer(vertexConsumer);

        try {
            modelRenderer.tesselateBlock(
                    localLevel,
                    pipeRendererModel,
                    state,
                    localPos,
                    poseStack,
                    countingConsumer,
                    false,
                    RandomSource.create(seed),
                    seed,
                    OverlayTexture.NO_OVERLAY,
                    createGregPipeModelData(sourceModelData),
                    renderType
            );
        } catch (Throwable ignored) {
            return false;
        }

        return countingConsumer.vertexCount() > 0;
    }

    private static BakedModel createDirectPipeRendererModel(
            BakedModel baseModel,
            PipeBlockRenderer pipeRenderer,
            PreviewBlockAndTintGetter localLevel,
            BlockPos localPos,
            RenderType renderType,
            ModelData sourceModelData
    ) {
        return new BakedModelWrapper<>(baseModel) {

            @Override
            public List<BakedQuad> getQuads(
                    @Nullable BlockState state,
                    @Nullable Direction side,
                    RandomSource random
            ) {
                if (state == null) {
                    return List.of();
                }

                return renderPipeQuads(
                        pipeRenderer,
                        localLevel,
                        localPos,
                        state,
                        side,
                        random,
                        renderType,
                        sourceModelData
                );
            }

            @Override
            public List<BakedQuad> getQuads(
                    @Nullable BlockState state,
                    @Nullable Direction side,
                    RandomSource random,
                    ModelData data,
                    @Nullable RenderType requestedRenderType
            ) {
                if (state == null) {
                    return List.of();
                }

                RenderType actualRenderType = requestedRenderType == null ? renderType : requestedRenderType;

                return renderPipeQuads(
                        pipeRenderer,
                        localLevel,
                        localPos,
                        state,
                        side,
                        random,
                        actualRenderType,
                        sourceModelData
                );
            }

            @Override
            public ChunkRenderTypeSet getRenderTypes(
                    BlockState state,
                    RandomSource random,
                    ModelData data
            ) {
                return ChunkRenderTypeSet.of(renderType);
            }
        };
    }

    private static List<BakedQuad> renderPipeQuads(
            PipeBlockRenderer pipeRenderer,
            PreviewBlockAndTintGetter localLevel,
            BlockPos localPos,
            BlockState state,
            @Nullable Direction side,
            RandomSource random,
            RenderType renderType,
            ModelData sourceModelData
    ) {
        ModelData gregModelData = createGregPipeModelData(sourceModelData);

        RenderType previousRenderType = CURRENT_RENDER_TYPE.get();
        ModelData previousModelData = CURRENT_MODEL_DATA.get();

        CURRENT_RENDER_TYPE.set(renderType);
        CURRENT_MODEL_DATA.set(gregModelData);

        try {
            return pipeRenderer.renderModel(
                    localLevel,
                    localPos,
                    state,
                    side,
                    random
            );
        } catch (Throwable ignored) {
            return List.of();
        } finally {
            CURRENT_RENDER_TYPE.set(previousRenderType);
            CURRENT_MODEL_DATA.set(previousModelData);
        }
    }

    private static boolean renderFallbackPipeModel(
            PipeBlock<?, ?, ?> pipeBlock,
            ModelBlockRenderer modelRenderer,
            PreviewBlockAndTintGetter localLevel,
            BakedModel model,
            BlockState state,
            BlockPos localPos,
            PoseStack poseStack,
            VertexConsumer vertexConsumer,
            long seed,
            int connections,
            int blockedConnections
    ) {
        PipeBlockRenderer pipeRenderer = pipeBlock.getRenderer(state);

        if (pipeRenderer == null) {
            return false;
        }

        PipeModel pipeModel = pipeRenderer.getPipeModel();

        if (pipeModel == null) {
            return false;
        }

        BakedModel pipePreviewModel = createFallbackPipeModel(
                model,
                pipeModel,
                connections,
                blockedConnections
        );

        modelRenderer.tesselateBlock(
                localLevel,
                pipePreviewModel,
                state,
                localPos,
                poseStack,
                vertexConsumer,
                false,
                RandomSource.create(seed),
                seed,
                OverlayTexture.NO_OVERLAY,
                ModelData.EMPTY,
                PIPE_RENDER_TYPE
        );

        return true;
    }

    private static ModelData createGregPipeModelData(ModelData sourceModelData) {
        ModelData innerData = sourceModelData == null ? ModelData.EMPTY : sourceModelData;

        return ModelData.builder()
                .with(MODEL_DATA, innerData)
                .build();
    }

    private static boolean isPipeOrCoverRenderType(RenderType renderType) {
        return renderType == RenderType.solid()
                || renderType == RenderType.cutout()
                || renderType == PIPE_RENDER_TYPE
                || renderType == RenderType.translucent()
                || renderType == RenderType.translucentMovingBlock()
                || renderType == RenderType.tripwire()
                || containsRenderTypeName(renderType, "solid")
                || containsRenderTypeName(renderType, "cutout")
                || containsRenderTypeName(renderType, "translucent")
                || containsRenderTypeName(renderType, "tripwire");
    }

    private static boolean containsRenderTypeName(RenderType renderType, String needle) {
        return renderType.toString().toLowerCase().contains(needle);
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
            long seed
    ) {
        BlockState frameState = getGregFrameState(tag);

        if (frameState == null) {
            return false;
        }

        BakedModel frameModel = dispatcher.getBlockModel(frameState);

        if (!shouldRenderFrameInPass(frameModel, frameState, renderType, seed)) {
            return false;
        }

        modelRenderer.tesselateBlock(
                localLevel,
                frameModel,
                frameState,
                localPos,
                poseStack,
                vertexConsumer,
                false,
                RandomSource.create(seed),
                seed,
                OverlayTexture.NO_OVERLAY,
                ModelData.EMPTY,
                renderType
        );

        return true;
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

    private static Set<RenderType> collectWidgetRenderTypes(
            BlockRenderDispatcher dispatcher,
            CompoundTag tag,
            long seed
    ) {
        Set<RenderType> renderTypes = new LinkedHashSet<>();

        renderTypes.add(RenderType.cutout());
        renderTypes.add(PIPE_RENDER_TYPE);
        renderTypes.add(RenderType.translucent());

        BlockState frameState = getGregFrameState(tag);

        if (frameState == null) {
            return renderTypes;
        }

        BakedModel frameModel = dispatcher.getBlockModel(frameState);

        for (RenderType frameType : frameModel.getRenderTypes(
                frameState,
                RandomSource.create(seed),
                ModelData.EMPTY
        )) {
            renderTypes.add(frameType);
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
            long seed
    ) {
        for (RenderType frameType : frameModel.getRenderTypes(
                frameState,
                RandomSource.create(seed),
                ModelData.EMPTY
        )) {
            if (frameType == renderType) {
                return true;
            }
        }

        return false;
    }

    private static BakedModel createFallbackPipeModel(
            BakedModel baseModel,
            PipeModel pipeModel,
            int connections,
            int blockedConnections
    ) {
        return new BakedModelWrapper<>(baseModel) {

            @Override
            public List<BakedQuad> getQuads(
                    @Nullable BlockState state,
                    @Nullable Direction side,
                    RandomSource random
            ) {
                return pipeModel.bakeQuads(
                        side,
                        connections,
                        blockedConnections
                );
            }

            @Override
            public List<BakedQuad> getQuads(
                    @Nullable BlockState state,
                    @Nullable Direction side,
                    RandomSource random,
                    ModelData data,
                    @Nullable RenderType requestedRenderType
            ) {
                if (requestedRenderType == null || requestedRenderType == PIPE_RENDER_TYPE) {
                    return pipeModel.bakeQuads(
                            side,
                            connections,
                            blockedConnections
                    );
                }

                return List.of();
            }

            @Override
            public ChunkRenderTypeSet getRenderTypes(
                    BlockState state,
                    RandomSource random,
                    ModelData data
            ) {
                return ChunkRenderTypeSet.of(PIPE_RENDER_TYPE);
            }
        };
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
                materialPath + "_frame"
        );

        Block frameBlock = ForgeRegistries.BLOCKS.getValue(frameId);

        if (frameBlock == null || frameBlock == Blocks.AIR) {
            return null;
        }

        return frameBlock.defaultBlockState();
    }
}