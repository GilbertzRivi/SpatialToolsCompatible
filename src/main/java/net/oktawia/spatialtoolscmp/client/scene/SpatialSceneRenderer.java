package net.oktawia.spatialtoolscmp.client.scene;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexSorting;

import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.client.model.data.ModelDataManager;

import net.oktawia.spatialtoolscmp.client.renderer.PreviewChunkGeometry;

public class SpatialSceneRenderer {

    public interface BatchHook {

        void render(MultiBufferSource bufferSource, float partialTicks);
    }

    public interface GeometryFiller {

        void fill(PreviewChunkGeometry.LayerSink sink, PoseStack poseStack);
    }

    private static final int FULL_BRIGHT = 0xF000F0;
    private static final float RESORT_DISTANCE = 2.0F;

    private final Level level;

    private final Map<BlockPos, ModelData> modelDataCache = new HashMap<>();

    private @Nullable GeometryFiller geometryFiller;
    private @Nullable PreviewChunkGeometry geometry;
    private Vector3f geometrySortOrigin = new Vector3f();

    private int invalidationCounter;
    private int cachedInvalidationCounter = -1;
    private int cachedLevelVersion = -1;

    private Collection<BlockPos> renderedBlocks = List.of();
    private Vector3f eyePos = new Vector3f(0, 0, 10);
    private Vector3f lookAt = new Vector3f(0, 0, 0);
    private Vector3f worldUp = new Vector3f(0, 1, 0);

    private boolean ortho;
    private float orthoX;
    private float orthoY;
    private float orthoZ;
    private float fov = 60;
    private int clearColor;

    private @Nullable BatchHook beforeBatchEnd;

    public SpatialSceneRenderer(Level level) {
        this.level = level;
    }

    public void setRenderedBlocks(Collection<BlockPos> renderedBlocks) {
        this.renderedBlocks = renderedBlocks;
    }

    public void setBeforeBatchEnd(@Nullable BatchHook beforeBatchEnd) {
        this.beforeBatchEnd = beforeBatchEnd;
    }

    public void setGeometryFiller(@Nullable GeometryFiller geometryFiller) {
        this.geometryFiller = geometryFiller;
        invalidate();
    }

    public void invalidate() {
        this.invalidationCounter++;
    }

    public void close() {
        closeGeometry();
        this.modelDataCache.clear();
    }

    public void useOrtho(boolean ortho) {
        this.ortho = ortho;
    }

    public void setCameraOrtho(float x, float y, float z) {
        this.orthoX = x;
        this.orthoY = y;
        this.orthoZ = z;
    }

    public void setClearColor(int clearColor) {
        this.clearColor = clearColor;
    }

    public void setCameraLookAt(Vector3f lookAt, double radius, double horizontalAngle, double verticalAngle) {
        Vector3f horizontal = new Vector3f((float) Math.cos(horizontalAngle), 0, (float) Math.sin(horizontalAngle));
        Vector3f vertical = new Vector3f(0, (float) (Math.tan(verticalAngle) * horizontal.length()), 0);
        Vector3f offset = new Vector3f(horizontal).add(vertical).normalize().mul((float) radius);

        this.lookAt = new Vector3f(lookAt);
        this.eyePos = offset.add(lookAt.x(), lookAt.y(), lookAt.z());
    }

    private record Viewport(int x, int y, int width, int height) {
    }

    public void render(PoseStack poseStack, float x, float y, float width, float height) {
        Matrix4f pose = poseStack.last().pose();
        Vector4f topLeft = pose.transform(new Vector4f(x, y, 0, 1));
        Vector4f bottomRight = pose.transform(new Vector4f(x + width, y + height, 0, 1));

        Viewport viewport = toWindowRect(
                topLeft.x(),
                topLeft.y(),
                bottomRight.x() - topLeft.x(),
                bottomRight.y() - topLeft.y());

        setupCamera(viewport);
        drawWorld();
        resetCamera();
    }

    private static Viewport toWindowRect(float x, float y, float width, float height) {
        var window = Minecraft.getInstance().getWindow();

        double scaleX = window.getWidth() / (window.getGuiScaledWidth() * 1.0);
        double scaleY = window.getHeight() / (window.getGuiScaledHeight() * 1.0);

        int windowWidth = (int) (width * scaleX);
        int windowHeight = (int) (height * scaleY);
        int windowX = (int) (x * scaleX);
        int windowY = window.getHeight() - (int) (y * scaleY) - windowHeight;

        return new Viewport(windowX, windowY, windowWidth, windowHeight);
    }

    private void setupCamera(Viewport viewport) {
        RenderSystem.enableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.viewport(viewport.x(), viewport.y(), viewport.width(), viewport.height());
        RenderSystem.depthMask(true);

        clearView(viewport);

        RenderSystem.backupProjectionMatrix();

        float aspectRatio = viewport.width() / (viewport.height() * 1.0F);
        VertexSorting sorting = VertexSorting.byDistance(this.eyePos);

        if (this.ortho) {
            RenderSystem.setProjectionMatrix(
                    new Matrix4f().setOrtho(
                            -this.orthoX,
                            this.orthoX,
                            -this.orthoY / aspectRatio,
                            this.orthoY / aspectRatio,
                            -this.orthoZ,
                            this.orthoZ),
                    sorting);
        } else {
            RenderSystem.setProjectionMatrix(
                    new Matrix4f().setPerspective(this.fov * 0.01745329238474369F, aspectRatio, 0.1F, 10000.0F),
                    sorting);
        }

        PoseStack modelView = RenderSystem.getModelViewStack();
        modelView.pushPose();
        modelView.setIdentity();
        modelView.mulPoseMatrix(new Matrix4f().lookAt(this.eyePos, this.lookAt, this.worldUp));
        RenderSystem.applyModelViewMatrix();

        RenderSystem.activeTexture(GL13.GL_TEXTURE0);
        RenderSystem.enableCull();
    }

    private void clearView(Viewport viewport) {
        int alpha = (this.clearColor & 0xFF000000) >>> 24;

        if (alpha == 0) {
            RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
            return;
        }

        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(viewport.x(), viewport.y(), viewport.width(), viewport.height());

        RenderSystem.clearColor(
                ((this.clearColor >> 16) & 0xFF) / 255.0F,
                ((this.clearColor >> 8) & 0xFF) / 255.0F,
                (this.clearColor & 0xFF) / 255.0F,
                alpha / 255.0F);
        RenderSystem.clear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);

        GL11.glDisable(GL11.GL_SCISSOR_TEST);
    }

    private void resetCamera() {
        var window = Minecraft.getInstance().getWindow();

        RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
        RenderSystem.viewport(0, 0, window.getWidth(), window.getHeight());
        RenderSystem.restoreProjectionMatrix();

        PoseStack modelView = RenderSystem.getModelViewStack();
        modelView.popPose();
        RenderSystem.applyModelViewMatrix();

        RenderSystem.depthMask(false);
        RenderSystem.disableDepthTest();
        RenderSystem.enableBlend();
    }

    private void drawWorld() {
        Minecraft minecraft = Minecraft.getInstance();
        float partialTicks = minecraft.getFrameTime();

        refreshCaches();

        MultiBufferSource.BufferSource bufferSource = minecraft.renderBuffers().bufferSource();

        if (PreviewChunkGeometry.exceedsCacheLimit(this.renderedBlocks.size())) {
            drawWorldImmediate(bufferSource, partialTicks);
            return;
        }

        PreviewChunkGeometry sceneGeometry = geometryForCamera();

        Matrix4f modelViewMatrix = new Matrix4f(RenderSystem.getModelViewMatrix());
        Matrix4f projectionMatrix = new Matrix4f(RenderSystem.getProjectionMatrix());

        for (RenderType layer : RenderType.chunkBufferLayers()) {
            if (layer == RenderType.translucent()) {
                layer.setupRenderState();

                RenderSystem.setShaderColor(1, 1, 1, 1);
                RenderSystem.enableDepthTest();
                RenderSystem.disableBlend();
                RenderSystem.depthMask(true);

                renderBlockEntities(new PoseStack(), bufferSource, partialTicks);
                bufferSource.endBatch();
                layer.clearRenderState();
            }

            sceneGeometry.draw(layer, modelViewMatrix, projectionMatrix, Vec3.ZERO, BlockPos.ZERO);
        }

        if (this.beforeBatchEnd != null) {
            this.beforeBatchEnd.render(bufferSource, partialTicks);
        }

        bufferSource.endBatch();
    }

    private void drawWorldImmediate(MultiBufferSource.BufferSource bufferSource, float partialTicks) {
        closeGeometry();

        BlockRenderDispatcher dispatcher = Minecraft.getInstance().getBlockRenderer();
        RandomSource random = RandomSource.createNewThreadLocalInstance();
        PoseStack poseStack = new PoseStack();

        RenderSystem.setShaderColor(1, 1, 1, 1);
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
        RenderSystem.depthMask(true);

        renderBlockEntities(poseStack, bufferSource, partialTicks);

        for (RenderType layer : RenderType.chunkBufferLayers()) {
            renderBlocks(poseStack, dispatcher, layer, bufferSource.getBuffer(layer), random);
        }

        if (this.geometryFiller != null) {
            this.geometryFiller.fill(bufferSource::getBuffer, poseStack);
        }

        if (this.beforeBatchEnd != null) {
            this.beforeBatchEnd.render(bufferSource, partialTicks);
        }

        bufferSource.endBatch();
    }

    private void refreshCaches() {
        int levelVersion = this.level instanceof PreviewLevel preview ? preview.contentVersion() : 0;

        if (levelVersion == this.cachedLevelVersion
                && this.invalidationCounter == this.cachedInvalidationCounter) {
            return;
        }

        this.cachedLevelVersion = levelVersion;
        this.cachedInvalidationCounter = this.invalidationCounter;
        this.modelDataCache.clear();
        closeGeometry();
    }

    private PreviewChunkGeometry geometryForCamera() {
        if (this.geometry != null) {
            float threshold = Math.max(RESORT_DISTANCE, this.eyePos.distance(this.lookAt) * 0.15F);

            if (this.geometry.isEmpty(RenderType.translucent())
                    || this.geometrySortOrigin.distance(this.eyePos) < threshold) {
                return this.geometry;
            }

            closeGeometry();
        }

        BlockRenderDispatcher dispatcher = Minecraft.getInstance().getBlockRenderer();
        RandomSource random = RandomSource.createNewThreadLocalInstance();
        Vec3 sortOrigin = new Vec3(this.eyePos.x(), this.eyePos.y(), this.eyePos.z());

        this.geometry = PreviewChunkGeometry.compile(sink -> {
            PoseStack poseStack = new PoseStack();

            for (RenderType layer : RenderType.chunkBufferLayers()) {
                renderBlocks(poseStack, dispatcher, layer, sink.get(layer), random);
            }

            if (this.geometryFiller != null) {
                this.geometryFiller.fill(sink, poseStack);
            }
        }, sortOrigin);

        this.geometrySortOrigin = new Vector3f(this.eyePos);
        return this.geometry;
    }

    private void closeGeometry() {
        if (this.geometry == null) {
            return;
        }

        this.geometry.close();
        this.geometry = null;
    }

    private void renderBlocks(
            PoseStack poseStack,
            BlockRenderDispatcher dispatcher,
            RenderType layer,
            VertexConsumer buffer,
            RandomSource random) {
        OffsetVertexConsumer consumer = new OffsetVertexConsumer(buffer);

        for (BlockPos pos : this.renderedBlocks) {
            BlockState state = this.level.getBlockState(pos);

            if (state.isAir()) {
                continue;
            }

            if (state.getRenderShape() == RenderShape.MODEL
                    && canRenderInLayer(dispatcher, state, pos, layer, random)) {
                poseStack.pushPose();
                poseStack.translate(pos.getX(), pos.getY(), pos.getZ());

                BakedModel model = dispatcher.getBlockModel(state);

                dispatcher.getModelRenderer().tesselateBlock(
                        this.level,
                        model,
                        state,
                        pos,
                        poseStack,
                        consumer,
                        true,
                        random,
                        state.getSeed(pos),
                        OverlayTexture.NO_OVERLAY,
                        getModelData(model, state, pos),
                        layer);

                poseStack.popPose();
            }

            FluidState fluidState = state.getFluidState();

            if (!fluidState.isEmpty() && ItemBlockRenderTypes.getRenderLayer(fluidState) == layer) {
                consumer.setOffset(
                        pos.getX() - (pos.getX() & 15),
                        pos.getY() - (pos.getY() & 15),
                        pos.getZ() - (pos.getZ() & 15));
                dispatcher.renderLiquid(pos, this.level, consumer, state, fluidState);
                consumer.clearOffset();
            }
        }
    }

    private void renderBlockEntities(PoseStack poseStack, MultiBufferSource bufferSource, float partialTicks) {
        var dispatcher = Minecraft.getInstance().getBlockEntityRenderDispatcher();

        for (BlockPos pos : this.renderedBlocks) {
            BlockEntity blockEntity = this.level.getBlockEntity(pos);

            if (blockEntity == null || !blockEntity.hasLevel()
                    || !blockEntity.getType().isValid(blockEntity.getBlockState())) {
                continue;
            }

            BlockEntityRenderer<BlockEntity> renderer = dispatcher.getRenderer(blockEntity);

            if (renderer == null) {
                continue;
            }

            poseStack.pushPose();
            poseStack.translate(pos.getX(), pos.getY(), pos.getZ());

            try {
                renderer.render(blockEntity, partialTicks, poseStack, bufferSource, FULL_BRIGHT,
                        OverlayTexture.NO_OVERLAY);
            } catch (Throwable ignored) {
            }

            poseStack.popPose();
        }
    }

    private boolean canRenderInLayer(
            BlockRenderDispatcher dispatcher,
            BlockState state,
            BlockPos pos,
            RenderType layer,
            RandomSource random) {
        BakedModel model = dispatcher.getBlockModel(state);

        return model.getRenderTypes(state, random, getModelData(model, state, pos)).contains(layer);
    }

    private ModelData getModelData(BakedModel model, BlockState state, BlockPos pos) {
        ModelData cached = this.modelDataCache.get(pos);

        if (cached != null) {
            return cached;
        }

        ModelData computed = computeModelData(model, state, pos);
        this.modelDataCache.put(pos.immutable(), computed);
        return computed;
    }

    private ModelData computeModelData(BakedModel model, BlockState state, BlockPos pos) {
        ModelData modelData = null;
        ModelDataManager manager = this.level.getModelDataManager();

        if (manager != null) {
            modelData = manager.getAt(pos);
        }

        if (modelData == null) {
            BlockEntity blockEntity = this.level.getExistingBlockEntity(pos);

            if (blockEntity != null) {
                modelData = blockEntity.getModelData();
            }
        }

        return model.getModelData(this.level, pos, state, modelData == null ? ModelData.EMPTY : modelData);
    }
}
