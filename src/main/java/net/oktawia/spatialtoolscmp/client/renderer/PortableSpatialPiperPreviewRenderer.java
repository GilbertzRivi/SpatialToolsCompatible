package net.oktawia.spatialtoolscmp.client.renderer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.mojang.blaze3d.vertex.PoseStack;

import org.jetbrains.annotations.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import net.oktawia.spatialtoolscmp.compat.gtceu.GTCEuClientCompat;
import net.oktawia.spatialtoolscmp.compat.gtceu.GTCEuCompat;
import net.oktawia.spatialtoolscmp.items.PortableSpatialPiper;
import net.oktawia.spatialtoolscmp.logic.ClientPiperExtension;
import net.oktawia.spatialtoolscmp.logic.ClientPiperExtensions;
import net.oktawia.spatialtoolscmp.logic.PiperExtension;
import net.oktawia.spatialtoolscmp.logic.PiperRoute;
import net.oktawia.spatialtoolscmp.logic.SpatialPowerCost;

public class PortableSpatialPiperPreviewRenderer {

    private static final int REFRESH_INTERVAL_TICKS = 4;

    private final PreviewGhostRenderer ghostRenderer = new PreviewGhostRenderer();
    private final PreviewTargetResolver targetResolver = new PreviewTargetResolver();

    private static double previewDistanceSum = 0.0D;
    private static int previewBlocks = 0;

    private Set<BlockPos> cachedBuildPositions = Collections.emptySet();
    private List<BlockPos> cachedRouteBoxes = List.of();
    private List<BlockPos> cachedPendingBoxes = List.of();
    private List<BlockPos> cachedBlockedBoxes = List.of();

    private Map<BlockPos, Integer> cachedPathMasks = Map.of();
    private Map<BlockPos, Integer> cachedPipeBlockedMasks = Map.of();
    private List<BlockPos> cachedRoute = List.of();
    private BlockPos cachedLookPos = null;
    private BlockState cachedTargetState = null;
    private ClientLevel cachedLevel = null;

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

        ItemStack mainHand = mc.player.getMainHandItem();
        boolean heldInMainHand = mainHand.getItem() instanceof PortableSpatialPiper;

        ItemStack stack = heldInMainHand ? mainHand : mc.player.getOffhandItem();

        if (!(stack.getItem() instanceof PortableSpatialPiper)) {
            clearCache();
            return;
        }

        if (!PortableSpatialPiper.isRouteInLevel(stack, mc.level)) {
            clearCache();
            return;
        }

        ItemStack targetItem = PortableSpatialPiper.getTargetBlock(stack);

        if (targetItem.isEmpty()) {
            clearCache();
            return;
        }

        BlockState targetState = this.targetResolver.resolve(targetItem);

        if (targetState == null || targetState.isAir()) {
            clearCache();
            return;
        }

        ensurePreviewCache(mc, stack, targetState, heldInMainHand);

        if (this.cachedBuildPositions.isEmpty()
                && this.cachedBlockedBoxes.isEmpty()) {
            return;
        }

        PoseStack poseStack = event.getPoseStack();
        Vec3 camera = event.getCamera().getPosition();

        poseStack.pushPose();
        poseStack.translate(-camera.x, -camera.y, -camera.z);

        Set<BlockPos> buildPositions = this.cachedBuildPositions;

        try {
            this.ghostRenderer.renderGhostModels(
                    mc,
                    poseStack,
                    targetState,
                    buildPositions,
                    pass,
                    pos -> buildModelData(targetState, pos, buildPositions),
                    (pos, renderType, modelData, stackPose, bufferSource, seed) -> renderWithExtension(
                            targetState,
                            targetItem,
                            pos,
                            buildPositions,
                            stackPose,
                            bufferSource,
                            renderType,
                            modelData,
                            seed),
                    this.targetResolver.previewRenderTypes(targetState));

            if (PreviewGhostRenderer.isTripwireStage(event.getStage())) {
                this.ghostRenderer.renderLineBoxes(
                        mc,
                        poseStack,
                        this.cachedRouteBoxes,
                        0.20F,
                        1.00F,
                        0.35F,
                        1.00F);

                this.ghostRenderer.renderLineBoxes(
                        mc,
                        poseStack,
                        this.cachedPendingBoxes,
                        1.00F,
                        0.65F,
                        0.10F,
                        1.00F);

                this.ghostRenderer.renderLineBoxes(
                        mc,
                        poseStack,
                        this.cachedBlockedBoxes,
                        1.00F,
                        0.15F,
                        0.15F,
                        1.00F);

                if (GTCEuClientCompat.isPipe(targetState)) {
                    this.ghostRenderer.renderPipeConnectionLines(
                            mc,
                            poseStack,
                            buildPositions,
                            pos -> buildModelData(targetState, pos, buildPositions));
                }
            }
        } finally {
            poseStack.popPose();

            PreviewGhostRenderer.resetPreviewRenderState(
                    mc,
                    PreviewGhostRenderer.isTripwireStage(event.getStage()));
        }
    }

    private ModelData buildModelData(
            BlockState targetState,
            BlockPos pos,
            Set<BlockPos> allPositions) {
        Integer pathMask = this.cachedPathMasks.get(pos);

        if (pathMask != null && GTCEuClientCompat.isPipe(targetState)) {
            return GTCEuClientCompat.pipeModelData(
                    pathMask,
                    this.cachedPipeBlockedMasks.getOrDefault(pos, 0));
        }

        return this.targetResolver.buildModelData(targetState, pos, allPositions);
    }

    private boolean renderWithExtension(
            BlockState targetState,
            ItemStack target,
            BlockPos pos,
            Set<BlockPos> allPositions,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            RenderType renderType,
            ModelData modelData,
            long seed) {
        for (ClientPiperExtension ext : ClientPiperExtensions.get()) {
            if (ext.renderRouteBlock(
                    targetState,
                    target,
                    pos,
                    allPositions,
                    poseStack,
                    bufferSource,
                    renderType,
                    modelData,
                    seed)) {
                return true;
            }
        }

        return false;
    }

    private void ensurePreviewCache(
            Minecraft mc,
            ItemStack stack,
            BlockState targetState,
            boolean heldInMainHand) {
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

        List<BlockPos> route = PortableSpatialPiper.getRoute(stack);
        BlockPos lookPos = heldInMainHand ? getRouteLookPos(mc, stack) : null;

        boolean needsRefresh = this.refreshAgeTicks >= REFRESH_INTERVAL_TICKS
                || this.cachedLevel != level
                || !route.equals(this.cachedRoute)
                || !Objects.equals(lookPos, this.cachedLookPos)
                || this.cachedTargetState == null
                || !this.cachedTargetState.equals(targetState);

        if (!needsRefresh) {
            return;
        }

        this.refreshAgeTicks = 0;
        this.cachedLevel = level;
        this.cachedRoute = route;
        this.cachedLookPos = lookPos;
        this.cachedTargetState = targetState;

        recomputePositions(level, stack, route, lookPos, targetState);
    }

    private void recomputePositions(
            ClientLevel level,
            ItemStack stack,
            List<BlockPos> route,
            @Nullable BlockPos lookPos,
            BlockState targetState) {
        this.cachedBuildPositions = Collections.emptySet();
        this.cachedRouteBoxes = List.of();
        this.cachedPathMasks = Map.of();
        this.cachedPipeBlockedMasks = Map.of();
        previewDistanceSum = 0.0D;
        previewBlocks = 0;
        this.cachedPendingBoxes = List.of();
        this.cachedBlockedBoxes = List.of();

        if (route.isEmpty() && lookPos == null) {
            return;
        }

        int cap = PortableSpatialPiper.getMaxRouteBlocks();

        Set<BlockPos> routePositions = PortableSpatialPiper.expandRoute(stack, route, cap);
        Set<BlockPos> pendingPositions = Collections.emptySet();

        if (lookPos != null
                && routePositions.size() < cap
                && PortableSpatialPiper.acceptsMorePoints(stack, route)) {
            if (route.isEmpty()) {
                pendingPositions = Set.of(lookPos);
            } else {
                List<BlockPos> candidate = new ArrayList<>(route);
                candidate.add(PortableSpatialPiper.nextRoutePoint(stack, route, lookPos));

                pendingPositions = PortableSpatialPiper.expandRoute(stack, candidate, cap);
            }
        }

        ItemStack target = PortableSpatialPiper.getTargetBlock(stack);

        if (PortableSpatialPiper.getFillMode(stack) == PortableSpatialPiper.FillMode.PATH) {
            Set<BlockPos> orderedPath = pendingPositions.isEmpty() ? routePositions : pendingPositions;

            this.cachedPathMasks = PiperRoute.pathConnectionMasks(orderedPath);
            this.cachedPipeBlockedMasks = pipeBlockedMasks(stack, target, orderedPath);
        }

        Set<BlockPos> buildPositions = new LinkedHashSet<>();
        List<BlockPos> routeBoxes = new ArrayList<>();
        List<BlockPos> pendingBoxes = new ArrayList<>();
        List<BlockPos> blockedBoxes = new ArrayList<>();

        for (BlockPos pos : routePositions) {
            PiperExtension.PathAction action = pathAction(level, pos, target, targetState);

            if (action == PiperExtension.PathAction.BUILD) {
                buildPositions.add(pos);
                routeBoxes.add(pos);
            } else if (action == PiperExtension.PathAction.BLOCKED) {
                blockedBoxes.add(pos);
            }
        }

        for (BlockPos pos : pendingPositions) {
            if (routePositions.contains(pos)) {
                continue;
            }

            PiperExtension.PathAction action = pathAction(level, pos, target, targetState);

            if (action == PiperExtension.PathAction.BUILD) {
                buildPositions.add(pos);
                pendingBoxes.add(pos);
            } else if (action == PiperExtension.PathAction.BLOCKED) {
                blockedBoxes.add(pos);
            }
        }

        previewBlocks = route.isEmpty() ? 0 : routeBoxes.size();
        previewDistanceSum = route.isEmpty()
                ? 0.0D
                : SpatialPowerCost.distanceSum(routeBoxes, route.get(0));

        this.cachedBuildPositions = buildPositions;
        this.cachedRouteBoxes = routeBoxes;
        this.cachedPendingBoxes = pendingBoxes;
        this.cachedBlockedBoxes = blockedBoxes;
    }

    private static Map<BlockPos, Integer> pipeBlockedMasks(
            ItemStack stack,
            ItemStack target,
            Set<BlockPos> orderedPath) {
        PortableSpatialPiper.PipeDirectionMode mode = PortableSpatialPiper.getPipeDirectionMode(stack);

        if (mode == PortableSpatialPiper.PipeDirectionMode.OFF
                || !GTCEuCompat.supportsPipeDirection(target)) {
            return Map.of();
        }

        Map<BlockPos, Integer> masks = new HashMap<>();

        PiperRoute.pathStepDirections(orderedPath).forEach(
                (pos, step) -> masks.put(pos, GTCEuCompat.blockedMask(step, mode)));

        return masks;
    }

    private static PiperExtension.PathAction pathAction(
            ClientLevel level,
            BlockPos pos,
            ItemStack target,
            BlockState targetState) {
        BlockState state = level.getBlockState(pos);

        for (ClientPiperExtension ext : ClientPiperExtensions.get()) {
            PiperExtension.PathAction action = ext.resolvePathAction(level, pos, state, target);

            if (action != null) {
                return action;
            }
        }

        return PortableSpatialPiper.defaultPathAction(state, targetState.getBlock());
    }

    @Nullable
    private BlockPos getRouteLookPos(Minecraft mc, ItemStack stack) {
        if (mc.player == null) {
            return null;
        }

        BlockHitResult hit = mc.hitResult != null && mc.hitResult.getType() == HitResult.Type.BLOCK
                ? (BlockHitResult) mc.hitResult
                : null;

        return PortableSpatialPiper.resolveRoutePos(
                mc.level,
                stack,
                mc.player,
                hit == null ? null : hit.getBlockPos(),
                hit == null ? null : hit.getDirection());
    }

    private void clearCache() {
        this.cachedBuildPositions = Collections.emptySet();
        this.cachedRouteBoxes = List.of();
        previewDistanceSum = 0.0D;
        previewBlocks = 0;
        this.cachedPendingBoxes = List.of();
        this.cachedBlockedBoxes = List.of();
        this.cachedPathMasks = Map.of();
        this.cachedPipeBlockedMasks = Map.of();
        this.cachedRoute = List.of();
        this.cachedLookPos = null;
        this.cachedTargetState = null;
        this.cachedLevel = null;
        this.refreshAgeTicks = REFRESH_INTERVAL_TICKS;
    }
}
