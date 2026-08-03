package net.oktawia.spatialtoolscmp.client.renderer;

import com.gregtechceu.gtceu.client.model.GTModelProperties;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.model.data.ModelData;
import net.oktawia.spatialtoolscmp.SpatialConfig;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

public final class PreviewGhostRenderer {

    public enum PreviewPass {
        SOLID,
        CUTOUT,
        CUTOUT_MIPPED,
        TRANSLUCENT
    }

    @FunctionalInterface
    public interface GhostBlockRenderer {
        boolean render(
                BlockPos pos,
                RenderType renderType,
                ModelData modelData,
                PoseStack poseStack,
                MultiBufferSource bufferSource,
                long seed
        );
    }

    private static final int PREVIEW_BUFFER_SIZE = 262_144;

    private static final float PREVIEW_MODEL_SCALE = 1.004F;
    private static final float PREVIEW_POLYGON_OFFSET_FACTOR = -1.0F;
    private static final float PREVIEW_POLYGON_OFFSET_UNITS = -10.0F;

    private final BufferBuilder previewBufferBuilder = new BufferBuilder(PREVIEW_BUFFER_SIZE);
    private final MultiBufferSource.BufferSource previewBufferSource =
            MultiBufferSource.immediate(this.previewBufferBuilder);

    public static boolean isTripwireStage(RenderLevelStageEvent.Stage stage) {
        return stage == RenderLevelStageEvent.Stage.AFTER_TRIPWIRE_BLOCKS;
    }

    public static Set<BlockPos> limitToPreviewCap(Set<BlockPos> positions) {
        int cap = Math.max(0, SpatialConfig.COMMON.PREVIEW_MAX_BLOCKS.get());

        if (positions.size() <= cap) {
            return positions;
        }

        Set<BlockPos> limited = new LinkedHashSet<>(cap);

        for (BlockPos pos : positions) {
            if (limited.size() >= cap) {
                break;
            }

            limited.add(pos);
        }

        return limited;
    }

    public static List<BlockPos> limitToPreviewCap(List<BlockPos> positions) {
        int cap = Math.max(0, SpatialConfig.COMMON.PREVIEW_MAX_BLOCKS.get());

        return positions.size() <= cap ? positions : positions.subList(0, cap);
    }

    @Nullable
    public static PreviewPass passForStage(RenderLevelStageEvent.Stage stage) {
        if (stage == RenderLevelStageEvent.Stage.AFTER_SOLID_BLOCKS) {
            return PreviewPass.SOLID;
        }

        if (stage == RenderLevelStageEvent.Stage.AFTER_CUTOUT_MIPPED_BLOCKS_BLOCKS) {
            return PreviewPass.CUTOUT_MIPPED;
        }

        if (stage == RenderLevelStageEvent.Stage.AFTER_CUTOUT_BLOCKS) {
            return PreviewPass.CUTOUT;
        }

        if (stage == RenderLevelStageEvent.Stage.AFTER_TRIPWIRE_BLOCKS) {
            return PreviewPass.TRANSLUCENT;
        }

        return null;
    }

    public void renderGhostModels(
            Minecraft mc,
            PoseStack poseStack,
            BlockState targetState,
            Set<BlockPos> positions,
            PreviewPass pass,
            Function<BlockPos, ModelData> modelDataProvider,
            @Nullable GhostBlockRenderer customRenderer,
            @Nullable Iterable<RenderType> renderTypesOverride
    ) {
        if (targetState.isAir() || positions.isEmpty()) {
            return;
        }

        RenderType fallbackRenderType = switch (pass) {
            case SOLID -> RenderType.solid();
            case CUTOUT -> RenderType.cutout();
            case CUTOUT_MIPPED -> RenderType.cutoutMipped();
            case TRANSLUCENT -> RenderType.translucent();
        };

        if (pass == PreviewPass.TRANSLUCENT) {
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.depthMask(false);
            RenderSystem.disableCull();
        } else {
            RenderSystem.disableBlend();
            RenderSystem.depthMask(true);
            RenderSystem.enableCull();
        }

        RenderSystem.enableDepthTest();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.setShaderTexture(0, TextureAtlas.LOCATION_BLOCKS);
        RenderSystem.enablePolygonOffset();
        RenderSystem.polygonOffset(
                PREVIEW_POLYGON_OFFSET_FACTOR,
                PREVIEW_POLYGON_OFFSET_UNITS
        );

        mc.gameRenderer.lightTexture().turnOnLightLayer();

        BlockRenderDispatcher dispatcher = mc.getBlockRenderer();
        ModelBlockRenderer modelRenderer = dispatcher.getModelRenderer();
        BakedModel model = dispatcher.getBlockModel(targetState);

        GhostBlockGetter ghostLevel = new GhostBlockGetter(
                mc.level,
                positions,
                targetState
        );

        Set<RenderType> usedRenderTypes =
                Collections.newSetFromMap(new IdentityHashMap<>());

        try {
            for (BlockPos pos : positions) {
                ModelData modelData = modelDataProvider.apply(pos);

                long seed = targetState.getSeed(pos);

                poseStack.pushPose();
                poseStack.translate(
                        pos.getX() + 0.5D,
                        pos.getY() + 0.5D,
                        pos.getZ() + 0.5D
                );
                poseStack.scale(
                        PREVIEW_MODEL_SCALE,
                        PREVIEW_MODEL_SCALE,
                        PREVIEW_MODEL_SCALE
                );
                poseStack.translate(
                        -0.5D,
                        -0.5D,
                        -0.5D
                );

                try {
                    Iterable<RenderType> renderTypes = renderTypesOverride != null
                            ? renderTypesOverride
                            : model.getRenderTypes(
                                    targetState,
                                    RandomSource.create(seed),
                                    modelData
                            );

                    for (RenderType renderType : renderTypes) {
                        if (renderTypesOverride == null && classifyRenderType(renderType) != pass) {
                            continue;
                        }

                        if (renderTypesOverride != null && pass != PreviewPass.CUTOUT) {
                            continue;
                        }

                        usedRenderTypes.add(renderType);

                        if (customRenderer != null && customRenderer.render(
                                pos,
                                renderType,
                                modelData,
                                poseStack,
                                this.previewBufferSource,
                                seed
                        )) {
                            continue;
                        }

                        modelRenderer.tesselateBlock(
                                ghostLevel,
                                model,
                                targetState,
                                pos,
                                poseStack,
                                this.previewBufferSource.getBuffer(renderType),
                                false,
                                RandomSource.create(seed),
                                seed,
                                OverlayTexture.NO_OVERLAY,
                                modelData,
                                renderType
                        );
                    }
                } catch (Throwable ignored) {
                } finally {
                    poseStack.popPose();
                }
            }
        } finally {
            if (usedRenderTypes.isEmpty()) {
                this.previewBufferSource.endBatch(fallbackRenderType);
            } else {
                for (RenderType renderType : usedRenderTypes) {
                    this.previewBufferSource.endBatch(renderType);
                }
            }

            RenderSystem.polygonOffset(0.0F, 0.0F);
            RenderSystem.disablePolygonOffset();
        }
    }

    public void renderLineBoxes(
            Minecraft mc,
            PoseStack poseStack,
            Collection<BlockPos> positions,
            float red,
            float green,
            float blue,
            float alpha
    ) {
        if (positions.isEmpty()) {
            return;
        }

        RenderType renderType = RenderType.translucent();

        prepareLineState();

        VertexConsumer quads = this.previewBufferSource.getBuffer(renderType);
        Matrix4f matrix = poseStack.last().pose();
        Vec3 camera = mc.gameRenderer.getMainCamera().getPosition();
        TextureAtlasSprite sprite = previewWhiteSprite(mc);

        try {
            for (BlockPos pos : positions) {
                addBillboardLineBox(
                        quads,
                        matrix,
                        camera,
                        sprite,
                        pos.getX(),
                        pos.getY(),
                        pos.getZ(),
                        pos.getX() + 1.0F,
                        pos.getY() + 1.0F,
                        pos.getZ() + 1.0F,
                        0.0085F,
                        red,
                        green,
                        blue,
                        alpha
                );
            }
        } finally {
            this.previewBufferSource.endBatch(renderType);
        }
    }

    public void renderPipeConnectionLines(
            Minecraft mc,
            PoseStack poseStack,
            Set<BlockPos> positions,
            Function<BlockPos, ModelData> modelDataProvider
    ) {
        if (positions.isEmpty()) {
            return;
        }

        RenderType renderType = RenderType.translucent();

        prepareLineState();

        VertexConsumer quads = this.previewBufferSource.getBuffer(renderType);
        Matrix4f matrix = poseStack.last().pose();
        Vec3 camera = mc.gameRenderer.getMainCamera().getPosition();
        TextureAtlasSprite sprite = previewWhiteSprite(mc);

        try {
            for (BlockPos pos : positions) {
                ModelData modelData = modelDataProvider.apply(pos);

                int connections = modelData.has(GTModelProperties.PIPE_CONNECTION_MASK)
                        ? modelData.get(GTModelProperties.PIPE_CONNECTION_MASK)
                        : 0;

                float cx = pos.getX() + 0.5F;
                float cy = pos.getY() + 0.5F;
                float cz = pos.getZ() + 0.5F;

                for (Direction direction : Direction.values()) {
                    if ((connections & (1 << direction.ordinal())) == 0) {
                        continue;
                    }

                    float ex = cx + direction.getStepX() * 0.5F;
                    float ey = cy + direction.getStepY() * 0.5F;
                    float ez = cz + direction.getStepZ() * 0.5F;

                    billboardLine(
                            quads,
                            matrix,
                            camera,
                            sprite,
                            cx,
                            cy,
                            cz,
                            ex,
                            ey,
                            ez,
                            0.0125F,
                            0.20F,
                            0.85F,
                            1.00F,
                            1.00F
                    );
                }
            }
        } finally {
            this.previewBufferSource.endBatch(renderType);
        }
    }

    private static void prepareLineState() {
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.setShaderTexture(0, TextureAtlas.LOCATION_BLOCKS);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    public static PreviewPass classifyRenderType(RenderType renderType) {
        if (renderType == RenderType.translucent()
                || renderType == RenderType.translucentMovingBlock()
                || renderType == RenderType.tripwire()) {
            return PreviewPass.TRANSLUCENT;
        }

        if (renderType == RenderType.cutoutMipped()) {
            return PreviewPass.CUTOUT_MIPPED;
        }

        if (renderType == RenderType.cutout()) {
            return PreviewPass.CUTOUT;
        }

        if (renderType == RenderType.solid()) {
            return PreviewPass.SOLID;
        }

        String name = renderType.toString().toLowerCase();

        if (name.contains("translucent") || name.contains("tripwire")) {
            return PreviewPass.TRANSLUCENT;
        }

        if (name.contains("cutout_mipped") || name.contains("cutoutmipped")) {
            return PreviewPass.CUTOUT_MIPPED;
        }

        if (name.contains("cutout")) {
            return PreviewPass.CUTOUT;
        }

        return PreviewPass.SOLID;
    }

    public static void resetPreviewRenderState(
            Minecraft mc,
            boolean turnOffLightLayer
    ) {
        RenderSystem.polygonOffset(0.0F, 0.0F);
        RenderSystem.disablePolygonOffset();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.setShaderTexture(0, TextureAtlas.LOCATION_BLOCKS);

        if (turnOffLightLayer) {
            try {
                mc.gameRenderer.lightTexture().turnOffLightLayer();
            } catch (Throwable ignored) {
            }
        }
    }

    private static TextureAtlasSprite previewWhiteSprite(Minecraft mc) {
        return mc
                .getTextureAtlas(TextureAtlas.LOCATION_BLOCKS)
                .apply(ResourceLocation.fromNamespaceAndPath(
                        "minecraft",
                        "block/white_concrete"
                ));
    }

    private static void addBillboardLineBox(
            VertexConsumer consumer,
            Matrix4f matrix,
            Vec3 camera,
            TextureAtlasSprite sprite,
            float x1,
            float y1,
            float z1,
            float x2,
            float y2,
            float z2,
            float halfWidth,
            float red,
            float green,
            float blue,
            float alpha
    ) {
        billboardLine(consumer, matrix, camera, sprite, x1, y1, z1, x2, y1, z1, halfWidth, red, green, blue, alpha);
        billboardLine(consumer, matrix, camera, sprite, x2, y1, z1, x2, y1, z2, halfWidth, red, green, blue, alpha);
        billboardLine(consumer, matrix, camera, sprite, x2, y1, z2, x1, y1, z2, halfWidth, red, green, blue, alpha);
        billboardLine(consumer, matrix, camera, sprite, x1, y1, z2, x1, y1, z1, halfWidth, red, green, blue, alpha);

        billboardLine(consumer, matrix, camera, sprite, x1, y2, z1, x2, y2, z1, halfWidth, red, green, blue, alpha);
        billboardLine(consumer, matrix, camera, sprite, x2, y2, z1, x2, y2, z2, halfWidth, red, green, blue, alpha);
        billboardLine(consumer, matrix, camera, sprite, x2, y2, z2, x1, y2, z2, halfWidth, red, green, blue, alpha);
        billboardLine(consumer, matrix, camera, sprite, x1, y2, z2, x1, y2, z1, halfWidth, red, green, blue, alpha);

        billboardLine(consumer, matrix, camera, sprite, x1, y1, z1, x1, y2, z1, halfWidth, red, green, blue, alpha);
        billboardLine(consumer, matrix, camera, sprite, x2, y1, z1, x2, y2, z1, halfWidth, red, green, blue, alpha);
        billboardLine(consumer, matrix, camera, sprite, x2, y1, z2, x2, y2, z2, halfWidth, red, green, blue, alpha);
        billboardLine(consumer, matrix, camera, sprite, x1, y1, z2, x1, y2, z2, halfWidth, red, green, blue, alpha);
    }

    private static void billboardLine(
            VertexConsumer consumer,
            Matrix4f matrix,
            Vec3 camera,
            TextureAtlasSprite sprite,
            float x1,
            float y1,
            float z1,
            float x2,
            float y2,
            float z2,
            float halfWidth,
            float red,
            float green,
            float blue,
            float alpha
    ) {
        Vector3f direction = new Vector3f(
                x2 - x1,
                y2 - y1,
                z2 - z1
        );

        if (direction.lengthSquared() < 1.0e-6F) {
            return;
        }

        Vector3f middle = new Vector3f(
                (x1 + x2) * 0.5F,
                (y1 + y2) * 0.5F,
                (z1 + z2) * 0.5F
        );

        Vector3f toCamera = new Vector3f(
                (float) camera.x - middle.x,
                (float) camera.y - middle.y,
                (float) camera.z - middle.z
        );

        if (toCamera.lengthSquared() < 1.0e-6F) {
            toCamera.set(0.0F, 1.0F, 0.0F);
        }

        Vector3f side = direction.cross(toCamera, new Vector3f());

        if (side.lengthSquared() < 1.0e-6F) {
            side = direction.cross(
                    new Vector3f(0.0F, 1.0F, 0.0F),
                    new Vector3f()
            );

            if (side.lengthSquared() < 1.0e-6F) {
                side = direction.cross(
                        new Vector3f(1.0F, 0.0F, 0.0F),
                        new Vector3f()
                );
            }
        }

        if (side.lengthSquared() < 1.0e-6F) {
            return;
        }

        side.normalize(halfWidth);

        float u0 = sprite.getU0();
        float v0 = sprite.getV0();
        float u1 = sprite.getU1();
        float v1 = sprite.getV1();

        vertex(consumer, matrix, x1 + side.x, y1 + side.y, z1 + side.z, red, green, blue, alpha, u0, v0);
        vertex(consumer, matrix, x1 - side.x, y1 - side.y, z1 - side.z, red, green, blue, alpha, u0, v1);
        vertex(consumer, matrix, x2 - side.x, y2 - side.y, z2 - side.z, red, green, blue, alpha, u1, v1);
        vertex(consumer, matrix, x2 + side.x, y2 + side.y, z2 + side.z, red, green, blue, alpha, u1, v0);
    }

    private static void vertex(
            VertexConsumer consumer,
            Matrix4f matrix,
            float x,
            float y,
            float z,
            float red,
            float green,
            float blue,
            float alpha,
            float u,
            float v
    ) {
        consumer.vertex(matrix, x, y, z)
                .color(red, green, blue, alpha)
                .uv(u, v)
                .uv2(240, 240)
                .normal(0.0F, 1.0F, 0.0F)
                .endVertex();
    }

    private record GhostBlockGetter(
            ClientLevel wrapped,
            Set<BlockPos> ghostPositions,
            BlockState ghostState
    ) implements BlockAndTintGetter {

        @Override
        public BlockState getBlockState(BlockPos pos) {
            return this.ghostPositions.contains(pos)
                    ? this.ghostState
                    : this.wrapped.getBlockState(pos);
        }

        @Override
        public @Nullable BlockEntity getBlockEntity(BlockPos pos) {
            return this.ghostPositions.contains(pos)
                    ? null
                    : this.wrapped.getBlockEntity(pos);
        }

        @Override
        public FluidState getFluidState(BlockPos pos) {
            return this.ghostPositions.contains(pos)
                    ? Fluids.EMPTY.defaultFluidState()
                    : this.wrapped.getFluidState(pos);
        }

        @Override
        public int getBrightness(LightLayer layer, BlockPos pos) {
            if (this.ghostPositions.contains(pos)) {
                return 15;
            }

            return this.wrapped.getBrightness(layer, pos);
        }

        @Override
        public int getRawBrightness(BlockPos pos, int ambientDarkening) {
            if (this.ghostPositions.contains(pos)) {
                return Math.max(0, 15 - ambientDarkening);
            }

            return this.wrapped.getRawBrightness(pos, ambientDarkening);
        }

        @Override
        public LevelLightEngine getLightEngine() {
            return this.wrapped.getLightEngine();
        }

        @Override
        public float getShade(Direction direction, boolean shade) {
            return this.wrapped.getShade(direction, shade);
        }

        @Override
        public int getBlockTint(BlockPos pos, ColorResolver colorResolver) {
            return this.wrapped.getBlockTint(pos, colorResolver);
        }

        @Override
        public int getHeight() {
            return this.wrapped.getHeight();
        }

        @Override
        public int getMinBuildHeight() {
            return this.wrapped.getMinBuildHeight();
        }
    }
}
