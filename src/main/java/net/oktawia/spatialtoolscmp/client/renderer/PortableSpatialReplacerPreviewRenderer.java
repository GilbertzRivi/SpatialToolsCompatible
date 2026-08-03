package net.oktawia.spatialtoolscmp.client.renderer;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;

import com.gregtechceu.gtceu.api.block.PipeBlock;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import net.oktawia.spatialtoolscmp.SpatialConfig;
import net.oktawia.spatialtoolscmp.items.PortableSpatialReplacer;
import net.oktawia.spatialtoolscmp.logic.ClientReplacerExtension;
import net.oktawia.spatialtoolscmp.logic.ClientReplacerExtensions;
import net.oktawia.spatialtoolscmp.logic.ReplacerBlacklist;
import net.oktawia.spatialtoolscmp.logic.ReplacerContext;
import net.oktawia.spatialtoolscmp.logic.ReplacerContext.ConnectivityMode;
import net.oktawia.spatialtoolscmp.logic.SpatialPowerCost;

public class PortableSpatialReplacerPreviewRenderer {

    private static final int REFRESH_INTERVAL_TICKS = 8;

    private final PreviewGhostRenderer ghostRenderer = new PreviewGhostRenderer();

    private static double previewDistanceSum = 0.0D;
    private static int previewBlocks = 0;

    private Set<BlockPos> cachedPositions = Collections.emptySet();
    private Set<BlockPos> cachedRenderPositions = Collections.emptySet();
    private BlockState cachedTargetState = null;
    private BlockPos cachedLookPos = null;
    private BlockState cachedSourceState = null;
    private ClientLevel cachedLevel = null;

    private final PreviewTargetResolver targetResolver = new PreviewTargetResolver();

    private long lastSeenGameTime = -1L;
    private int refreshAgeTicks = REFRESH_INTERVAL_TICKS;

    public static double getPreviewDistanceSum() {
        return previewDistanceSum;
    }

    public static int getPreviewBlocks() {
        return previewBlocks;
    }

    @SubscribeEvent
    public void onRenderLevelStage(RenderLevelStageEvent event) {
        PreviewGhostRenderer.PreviewPass pass = PreviewGhostRenderer.passForStage(event.getStage());

        if (pass == null) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();

        if (mc.player == null || mc.level == null) {
            clearCache();
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

        BlockState targetState = this.targetResolver.resolve(targetItem);

        if (targetState == null || targetState.isAir()) {
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

        Set<BlockPos> positions = this.cachedRenderPositions;

        try {
            this.ghostRenderer.renderGhostModels(
                    mc,
                    poseStack,
                    targetState,
                    positions,
                    pass,
                    pos -> this.targetResolver.buildModelData(targetState, pos, positions),
                    null,
                    this.targetResolver.previewRenderTypes(targetState));

            if (PreviewGhostRenderer.isTripwireStage(event.getStage())) {
                this.ghostRenderer.renderLineBoxes(
                        mc,
                        poseStack,
                        positions,
                        1.00F,
                        0.30F,
                        0.00F,
                        1.00F);

                if (targetState.getBlock() instanceof PipeBlock<?, ?, ?>) {
                    this.ghostRenderer.renderPipeConnectionLines(
                            mc,
                            poseStack,
                            positions,
                            pos -> this.targetResolver.buildModelData(targetState, pos, positions));
                }
            }
        } finally {
            poseStack.popPose();

            PreviewGhostRenderer.resetPreviewRenderState(
                    mc,
                    PreviewGhostRenderer.isTripwireStage(event.getStage()));
        }
    }

    private void ensurePreviewCache(
            Minecraft mc,
            ItemStack stack,
            BlockState targetState) {
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
        BlockState sourceState = lookPos != null ? level.getBlockState(lookPos) : null;

        boolean needsRefresh = this.refreshAgeTicks >= REFRESH_INTERVAL_TICKS
                || this.cachedLevel != level
                || !Objects.equals(lookPos, this.cachedLookPos)
                || !Objects.equals(this.cachedSourceState, sourceState)
                || this.cachedTargetState == null
                || !this.cachedTargetState.equals(targetState);

        if (!needsRefresh) {
            return;
        }

        this.refreshAgeTicks = 0;
        this.cachedLevel = level;
        this.cachedSourceState = sourceState;
        this.cachedLookPos = lookPos;
        this.cachedTargetState = targetState;
        this.cachedPositions = computePositions(mc, stack, lookPos, targetState);
        this.cachedRenderPositions = PreviewGhostRenderer.limitToPreviewCap(this.cachedPositions);

        previewBlocks = lookPos == null ? 0 : this.cachedPositions.size();
        previewDistanceSum = lookPos == null
                ? 0.0D
                : SpatialPowerCost.distanceSum(this.cachedPositions, lookPos);
    }

    private void clearCache() {
        this.cachedPositions = Collections.emptySet();
        this.cachedRenderPositions = Collections.emptySet();
        previewDistanceSum = 0.0D;
        previewBlocks = 0;
        this.cachedTargetState = null;
        this.cachedLookPos = null;
        this.cachedSourceState = null;
        this.cachedLevel = null;
        this.refreshAgeTicks = REFRESH_INTERVAL_TICKS;
    }

    private BlockPos getLookPos(Minecraft mc) {
        if (mc.hitResult == null || mc.hitResult.getType() != HitResult.Type.BLOCK) {
            return null;
        }

        return ((BlockHitResult) mc.hitResult).getBlockPos();
    }

    private Set<BlockPos> computePositions(
            Minecraft mc,
            ItemStack stack,
            BlockPos startPos,
            BlockState targetState) {
        if (startPos == null) {
            return Collections.emptySet();
        }

        ClientLevel level = mc.level;

        if (level == null) {
            return Collections.emptySet();
        }

        BlockState sourceState = level.getBlockState(startPos);

        if (sourceState.isAir()) {
            return Collections.emptySet();
        }

        if (ReplacerBlacklist.isProtected(level, startPos, sourceState)) {
            return Collections.emptySet();
        }

        if (sourceState.getBlock() == targetState.getBlock()
                && !this.targetResolver.needsReplacement(level, startPos)) {
            return Collections.emptySet();
        }

        int radius = PortableSpatialReplacer.getRadius(stack);
        int hardCap = SpatialConfig.COMMON.PORTABLE_SPATIAL_REPLACER_MAX_BLOCKS.get();

        ConnectivityMode mode = PortableSpatialReplacer.getConnectivityMode(stack);
        boolean strict = PortableSpatialReplacer.isSameBlockstate(stack);

        ReplacerContext ctx = new ReplacerContext(
                radius,
                hardCap,
                mode,
                strict);

        for (ClientReplacerExtension ext : ClientReplacerExtensions.get()) {
            if (ext.canHandleSource(level, startPos, sourceState)) {
                Set<BlockPos> result = ext.computePreviewPositions(
                        level,
                        startPos,
                        sourceState,
                        ctx);

                if (result != null) {
                    return PortableSpatialReplacer.filterProtected(level, result);
                }
            }
        }

        return PortableSpatialReplacer.filterProtected(
                level,
                PortableSpatialReplacer.floodFill(
                        level,
                        startPos,
                        sourceState,
                        ctx));
    }
}
