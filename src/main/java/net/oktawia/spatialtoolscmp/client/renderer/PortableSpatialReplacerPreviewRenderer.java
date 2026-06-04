package net.oktawia.spatialtoolscmp.client.renderer;

import com.gregtechceu.gtceu.api.block.PipeBlock;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;
import net.oktawia.spatialtoolscmp.SpatialConfig;
import net.oktawia.spatialtoolscmp.items.PortableSpatialReplacer;
import net.oktawia.spatialtoolscmp.logic.ClientReplacerExtension;
import net.oktawia.spatialtoolscmp.logic.ClientReplacerExtensions;
import net.oktawia.spatialtoolscmp.logic.ReplacerContext;
import net.oktawia.spatialtoolscmp.logic.ReplacerContext.ConnectivityMode;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

public class PortableSpatialReplacerPreviewRenderer {

    private static final int REFRESH_INTERVAL_TICKS = 8;
    private static final int MAX_PREVIEW_BLOCKS = 512;
    private static final int PREVIEW_BUFFER_SIZE = 262_144;

    private static final float PREVIEW_MODEL_SCALE = 1.004F;
    private static final float PREVIEW_POLYGON_OFFSET_FACTOR = -1.0F;
    private static final float PREVIEW_POLYGON_OFFSET_UNITS = -10.0F;

    private final BufferBuilder previewBufferBuilder = new BufferBuilder(PREVIEW_BUFFER_SIZE);
    private final MultiBufferSource.BufferSource previewBufferSource =
            MultiBufferSource.immediate(this.previewBufferBuilder);

    private Set<BlockPos> cachedPositions = Collections.emptySet();
    private BlockState cachedTargetState = null;
    private BlockPos cachedLookPos = null;

    private long lastSeenGameTime = -1L;
    private int refreshAgeTicks = REFRESH_INTERVAL_TICKS;

    private enum PreviewPass {
        SOLID,
        CUTOUT,
        CUTOUT_MIPPED,
        TRANSLUCENT
    }

    @SubscribeEvent
    public void onRenderLevelStage(RenderLevelStageEvent event) {
        if (!isPreviewRenderStage(event.getStage())) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();

        if (mc.player == null || mc.level == null) {
            return;
        }

        ItemStack stack = mc.player.getMainHandItem();

        if (stack.isEmpty() || !(stack.getItem() instanceof PortableSpatialReplacer)) {
            clearCache();
            return;
        }

        ItemStack targetItem = PortableSpatialReplacer.getTargetBlock(stack);

        if (targetItem.isEmpty()) {
            clearCache();
            return;
        }

        ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(targetItem.getItem());

        if (itemId == null) {
            clearCache();
            return;
        }

        Block targetBlock = ForgeRegistries.BLOCKS.getValue(itemId);

        if (targetBlock == null) {
            clearCache();
            return;
        }

        BlockState targetState = targetBlock.defaultBlockState();

        if (targetState.isAir()) {
            clearCache();
            return;
        }

        ensurePreviewCache(mc, stack, targetState);

        if (this.cachedPositions.isEmpty()) {
            return;
        }

        PoseStack poseStack = event.getPoseStack();
        Vec3 camera = event.getCamera().getPosition();

        poseStack.pushPose();
        poseStack.translate(-camera.x, -camera.y, -camera.z);

        try {
            renderForStage(
                    mc,
                    event,
                    poseStack,
                    targetState,
                    this.cachedPositions
            );
        } finally {
            poseStack.popPose();

            resetPreviewRenderState(
                    mc,
                    isTripwireStage(event.getStage())
            );
        }
    }

    private static boolean isPreviewRenderStage(RenderLevelStageEvent.Stage stage) {
        return isSolidStage(stage)
                || isCutoutMippedStage(stage)
                || isCutoutStage(stage)
                || isTripwireStage(stage);
    }

    private static boolean isSolidStage(RenderLevelStageEvent.Stage stage) {
        return stage == RenderLevelStageEvent.Stage.AFTER_SOLID_BLOCKS;
    }

    private static boolean isCutoutMippedStage(RenderLevelStageEvent.Stage stage) {
        return stage == RenderLevelStageEvent.Stage.AFTER_CUTOUT_MIPPED_BLOCKS_BLOCKS;
    }

    private static boolean isCutoutStage(RenderLevelStageEvent.Stage stage) {
        return stage == RenderLevelStageEvent.Stage.AFTER_CUTOUT_BLOCKS;
    }

    private static boolean isTripwireStage(RenderLevelStageEvent.Stage stage) {
        return stage == RenderLevelStageEvent.Stage.AFTER_TRIPWIRE_BLOCKS;
    }

    private void renderForStage(
            Minecraft mc,
            RenderLevelStageEvent event,
            PoseStack poseStack,
            BlockState targetState,
            Set<BlockPos> positions
    ) {
        RenderLevelStageEvent.Stage stage = event.getStage();

        if (isSolidStage(stage)) {
            renderGhostModelsPass(
                    mc,
                    poseStack,
                    targetState,
                    positions,
                    PreviewPass.SOLID
            );
            return;
        }

        if (isCutoutMippedStage(stage)) {
            renderGhostModelsPass(
                    mc,
                    poseStack,
                    targetState,
                    positions,
                    PreviewPass.CUTOUT_MIPPED
            );
            return;
        }

        if (isCutoutStage(stage)) {
            renderGhostModelsPass(
                    mc,
                    poseStack,
                    targetState,
                    positions,
                    PreviewPass.CUTOUT
            );
            return;
        }

        if (isTripwireStage(stage)) {
            renderGhostModelsPass(
                    mc,
                    poseStack,
                    targetState,
                    positions,
                    PreviewPass.TRANSLUCENT
            );

            renderLineBoxes(
                    mc,
                    poseStack,
                    positions
            );

            if (targetState.getBlock() instanceof PipeBlock<?, ?, ?>) {
                renderPipeConnectionLines(
                        mc,
                        poseStack,
                        targetState,
                        positions
                );
            }
        }
    }

    private void ensurePreviewCache(
            Minecraft mc,
            ItemStack stack,
            BlockState targetState
    ) {
        ClientLevel level = mc.level;

        if (level == null) {
            clearCache();
            return;
        }

        long gameTime = level.getGameTime();

        if (gameTime != this.lastSeenGameTime) {
            this.lastSeenGameTime = gameTime;
            this.refreshAgeTicks++;
        }

        BlockPos lookPos = getLookPos(mc);

        boolean needsRefresh = this.refreshAgeTicks >= REFRESH_INTERVAL_TICKS
                || !posEquals(lookPos, this.cachedLookPos)
                || this.cachedTargetState == null
                || !this.cachedTargetState.equals(targetState);

        if (!needsRefresh) {
            return;
        }

        this.refreshAgeTicks = 0;
        this.cachedLookPos = lookPos;
        this.cachedTargetState = targetState;
        this.cachedPositions = computePositions(mc, stack, lookPos, targetState);
    }

    private void clearCache() {
        this.cachedPositions = Collections.emptySet();
        this.cachedTargetState = null;
        this.cachedLookPos = null;
        this.refreshAgeTicks = REFRESH_INTERVAL_TICKS;
    }

    private BlockPos getLookPos(Minecraft mc) {
        if (mc.hitResult == null || mc.hitResult.getType() != HitResult.Type.BLOCK) {
            return null;
        }

        return ((BlockHitResult) mc.hitResult).getBlockPos();
    }

    private boolean posEquals(BlockPos a, BlockPos b) {
        if (a == b) {
            return true;
        }

        if (a == null || b == null) {
            return false;
        }

        return a.equals(b);
    }

    private Set<BlockPos> computePositions(
            Minecraft mc,
            ItemStack stack,
            BlockPos startPos,
            BlockState targetState
    ) {
        if (startPos == null) {
            return Collections.emptySet();
        }

        ClientLevel level = mc.level;

        if (level == null) {
            return Collections.emptySet();
        }

        BlockState sourceState = level.getBlockState(startPos);

        if (sourceState.isAir() || sourceState.getBlock() == targetState.getBlock()) {
            return Collections.emptySet();
        }

        int radius = PortableSpatialReplacer.getRadius(stack);
        int hardCap = Math.min(
                SpatialConfig.COMMON.PORTABLE_SPATIAL_REPLACER_MAX_BLOCKS.get(),
                MAX_PREVIEW_BLOCKS
        );

        ConnectivityMode mode = PortableSpatialReplacer.getConnectivityMode(stack);
        boolean strict = PortableSpatialReplacer.isSameBlockstate(stack);

        ReplacerContext ctx = new ReplacerContext(
                radius,
                hardCap,
                mode,
                strict
        );

        for (ClientReplacerExtension ext : ClientReplacerExtensions.get()) {
            if (ext.canHandleSource(level, startPos, sourceState)) {
                Set<BlockPos> result = ext.computePreviewPositions(
                        level,
                        startPos,
                        sourceState,
                        ctx
                );

                if (result != null) {
                    return result;
                }
            }
        }

        return PortableSpatialReplacer.floodFill(
                level,
                startPos,
                sourceState,
                ctx
        );
    }

    private void renderGhostModelsPass(
            Minecraft mc,
            PoseStack poseStack,
            BlockState targetState,
            Set<BlockPos> positions,
            PreviewPass pass
    ) {
        if (targetState.isAir() || positions.isEmpty()) {
            return;
        }

        RenderType fallbackRenderType;

        switch (pass) {
            case SOLID -> {
                fallbackRenderType = RenderType.solid();

                RenderSystem.disableBlend();
                RenderSystem.depthMask(true);
                RenderSystem.enableCull();
            }
            case CUTOUT -> {
                fallbackRenderType = RenderType.cutout();

                RenderSystem.disableBlend();
                RenderSystem.depthMask(true);
                RenderSystem.enableCull();
            }
            case CUTOUT_MIPPED -> {
                fallbackRenderType = RenderType.cutoutMipped();

                RenderSystem.disableBlend();
                RenderSystem.depthMask(true);
                RenderSystem.enableCull();
            }
            case TRANSLUCENT -> {
                fallbackRenderType = RenderType.translucent();

                RenderSystem.enableBlend();
                RenderSystem.defaultBlendFunc();
                RenderSystem.depthMask(false);
                RenderSystem.disableCull();
            }
            default -> {
                return;
            }
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

        MultiBufferSource.BufferSource bufferSource = previewBufferSource();

        Set<RenderType> usedRenderTypes =
                Collections.newSetFromMap(new IdentityHashMap<>());

        try {
            for (BlockPos pos : positions) {
                ModelData modelData = buildModelData(
                        targetState,
                        pos,
                        positions
                );

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
                    Iterable<RenderType> renderTypes = model.getRenderTypes(
                            targetState,
                            RandomSource.create(seed),
                            modelData
                    );

                    for (RenderType renderType : renderTypes) {
                        PreviewPass renderTypePass = classifyRenderType(renderType);

                        if (renderTypePass != pass) {
                            continue;
                        }

                        usedRenderTypes.add(renderType);

                        modelRenderer.tesselateBlock(
                                ghostLevel,
                                model,
                                targetState,
                                pos,
                                poseStack,
                                bufferSource.getBuffer(renderType),
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
                bufferSource.endBatch(fallbackRenderType);
            } else {
                for (RenderType renderType : usedRenderTypes) {
                    bufferSource.endBatch(renderType);
                }
            }

            RenderSystem.polygonOffset(0.0F, 0.0F);
            RenderSystem.disablePolygonOffset();
        }
    }

    private static PreviewPass classifyRenderType(RenderType renderType) {
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

    private void renderLineBoxes(
            Minecraft mc,
            PoseStack poseStack,
            Set<BlockPos> positions
    ) {
        if (positions.isEmpty()) {
            return;
        }

        MultiBufferSource.BufferSource bufferSource = previewBufferSource();
        RenderType renderType = RenderType.translucent();

        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.setShaderTexture(0, TextureAtlas.LOCATION_BLOCKS);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

        VertexConsumer quads = bufferSource.getBuffer(renderType);
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
                        1.00F,
                        0.30F,
                        0.00F,
                        1.00F
                );
            }
        } finally {
            bufferSource.endBatch(renderType);
        }
    }

    private void renderPipeConnectionLines(
            Minecraft mc,
            PoseStack poseStack,
            BlockState targetState,
            Set<BlockPos> positions
    ) {
        if (positions.isEmpty()) {
            return;
        }

        MultiBufferSource.BufferSource bufferSource = previewBufferSource();
        RenderType renderType = RenderType.translucent();

        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.setShaderTexture(0, TextureAtlas.LOCATION_BLOCKS);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

        VertexConsumer quads = bufferSource.getBuffer(renderType);
        Matrix4f matrix = poseStack.last().pose();
        Vec3 camera = mc.gameRenderer.getMainCamera().getPosition();
        TextureAtlasSprite sprite = previewWhiteSprite(mc);

        try {
            for (BlockPos pos : positions) {
                ModelData modelData = buildModelData(
                        targetState,
                        pos,
                        positions
                );

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
            bufferSource.endBatch(renderType);
        }
    }

    private ModelData buildModelData(
            BlockState targetState,
            BlockPos pos,
            Set<BlockPos> allPositions
    ) {
        if (!(targetState.getBlock() instanceof PipeBlock<?, ?, ?>)) {
            return ModelData.EMPTY;
        }

        int connections = 0;

        for (Direction direction : Direction.values()) {
            if (allPositions.contains(pos.relative(direction))) {
                connections |= 1 << direction.ordinal();
            }
        }

        return ModelData.builder()
                .with(GTModelProperties.PIPE_CONNECTION_MASK, connections)
                .with(GTModelProperties.PIPE_BLOCKED_MASK, 0)
                .build();
    }

    private MultiBufferSource.BufferSource previewBufferSource() {
        return this.previewBufferSource;
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

        vertex(
                consumer,
                matrix,
                x1 + side.x,
                y1 + side.y,
                z1 + side.z,
                red,
                green,
                blue,
                alpha,
                u0,
                v0
        );

        vertex(
                consumer,
                matrix,
                x1 - side.x,
                y1 - side.y,
                z1 - side.z,
                red,
                green,
                blue,
                alpha,
                u0,
                v1
        );

        vertex(
                consumer,
                matrix,
                x2 - side.x,
                y2 - side.y,
                z2 - side.z,
                red,
                green,
                blue,
                alpha,
                u1,
                v1
        );

        vertex(
                consumer,
                matrix,
                x2 + side.x,
                y2 + side.y,
                z2 + side.z,
                red,
                green,
                blue,
                alpha,
                u1,
                v0
        );
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

    private static void resetPreviewRenderState(
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

    private record GhostBlockGetter(ClientLevel wrapped, Set<BlockPos> ghostPositions,
                                    BlockState ghostState) implements BlockAndTintGetter {

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