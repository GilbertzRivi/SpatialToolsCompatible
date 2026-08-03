package net.oktawia.spatialtoolscmp.client.misc.widgets;

import java.util.EnumMap;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import net.oktawia.spatialtoolscmp.defs.LangDefs;

public class DirectionStarWidget {

    public static final int SIZE = 132;

    private static final float PIXELS_PER_UNIT = 46.0f;

    private static final float CAMERA_PITCH = 24.0f;

    private static final float GUI_DEPTH = 150.0f;

    private static final float HUB_SIZE = 0.5f;

    private static final float[] SHAFT_DISTANCES = { 0.40f, 0.70f };
    private static final float SHAFT_SIZE = 0.3f;

    private static final float HEAD_DISTANCE = 1.10f;
    private static final float HEAD_SIZE = 0.5f;

    private static final float HOVER_GROW = 1.3f;

    private static final float HIT_RADIUS = 0.25f;

    private static final BlockState HUB_BLOCK = Blocks.WHITE_WOOL.defaultBlockState();
    private static final BlockState DISABLED_BLOCK = Blocks.GRAY_WOOL.defaultBlockState();

    private final EnumMap<Direction, Integer> actions = new EnumMap<>(Direction.class);

    private int x;
    private int y;
    private boolean enabled = true;

    public void setPosition(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void setActions(int west, int east, int north, int south, int up, int down) {
        this.actions.put(Direction.WEST, west);
        this.actions.put(Direction.EAST, east);
        this.actions.put(Direction.NORTH, north);
        this.actions.put(Direction.SOUTH, south);
        this.actions.put(Direction.UP, up);
        this.actions.put(Direction.DOWN, down);
    }

    public Integer getActionAt(double mouseX, double mouseY) {
        Direction hovered = getHoveredDirection(mouseX, mouseY);

        return hovered == null ? null : this.actions.get(hovered);
    }

    public Direction getHoveredDirection(double mouseX, double mouseY) {
        if (!this.enabled || this.actions.isEmpty()) {
            return null;
        }

        View view = new View(playerYaw(Minecraft.getInstance().getFrameTime()));

        float centerX = this.x + SIZE / 2.0f;
        float centerY = this.y + SIZE / 2.0f;
        float hitRange = HIT_RADIUS * PIXELS_PER_UNIT;

        Direction best = null;
        float bestDistance = Float.MAX_VALUE;
        float bestDepth = Float.MAX_VALUE;

        for (Direction dir : this.actions.keySet()) {
            float[] head = view.project(
                    dir.getStepX() * HEAD_DISTANCE,
                    dir.getStepY() * HEAD_DISTANCE,
                    dir.getStepZ() * HEAD_DISTANCE);

            float dx = (float) mouseX - (centerX + head[0]);
            float dy = (float) mouseY - (centerY + head[1]);
            float distance = Mth.sqrt(dx * dx + dy * dy);

            if (distance > hitRange) {
                continue;
            }

            boolean closerToCursor = distance < bestDistance - 2.0f;
            boolean sameSpotButNearerCamera = distance < bestDistance + 2.0f && head[2] < bestDepth;

            if (closerToCursor || sameSpotButNearerCamera) {
                bestDistance = distance;
                bestDepth = head[2];
                best = dir;
            }
        }

        return best;
    }

    public LangDefs tooltipFor(Direction dir) {
        return switch (dir) {
            case NORTH -> LangDefs.OFFSET_NORTH_TOOLTIP;
            case SOUTH -> LangDefs.OFFSET_SOUTH_TOOLTIP;
            case WEST -> LangDefs.OFFSET_WEST_TOOLTIP;
            case EAST -> LangDefs.OFFSET_EAST_TOOLTIP;
            case UP -> LangDefs.OFFSET_UP_TOOLTIP;
            case DOWN -> LangDefs.OFFSET_DOWN_TOOLTIP;
        };
    }

    public void render(GuiGraphics graphics, double mouseX, double mouseY, float partialTick) {
        if (this.actions.isEmpty()) {
            return;
        }

        Direction hovered = getHoveredDirection(mouseX, mouseY);

        graphics.flush();

        Minecraft minecraft = Minecraft.getInstance();
        BlockRenderDispatcher dispatcher = minecraft.getBlockRenderer();
        MultiBufferSource.BufferSource bufferSource = minecraft.renderBuffers().bufferSource();

        PoseStack pose = graphics.pose();

        pose.pushPose();
        pose.translate(this.x + SIZE / 2.0f, this.y + SIZE / 2.0f, GUI_DEPTH);
        pose.scale(PIXELS_PER_UNIT, -PIXELS_PER_UNIT, PIXELS_PER_UNIT);
        pose.mulPose(Axis.XP.rotationDegrees(CAMERA_PITCH));
        pose.mulPose(Axis.YP.rotationDegrees(playerYaw(partialTick) + 180.0f));

        RenderSystem.enableDepthTest();
        Lighting.setupLevel(pose.last().pose());

        renderCube(dispatcher, pose, bufferSource, hubBlock(), Direction.UP, 0.0f, HUB_SIZE);

        for (Direction dir : this.actions.keySet()) {
            BlockState state = blockFor(dir);

            for (float shaftDistance : SHAFT_DISTANCES) {
                renderCube(dispatcher, pose, bufferSource, state, dir, shaftDistance, SHAFT_SIZE);
            }

            renderCube(
                    dispatcher,
                    pose,
                    bufferSource,
                    state,
                    dir,
                    HEAD_DISTANCE,
                    dir == hovered ? HEAD_SIZE * HOVER_GROW : HEAD_SIZE);
        }

        bufferSource.endBatch();

        pose.popPose();

        Lighting.setupFor3DItems();
    }

    private static void renderCube(
            BlockRenderDispatcher dispatcher,
            PoseStack pose,
            MultiBufferSource bufferSource,
            BlockState state,
            Direction dir,
            float distance,
            float size) {
        pose.pushPose();

        pose.translate(
                dir.getStepX() * distance,
                dir.getStepY() * distance,
                dir.getStepZ() * distance);

        pose.scale(size, size, size);
        pose.translate(-0.5f, -0.5f, -0.5f);

        dispatcher.renderSingleBlock(
                state,
                pose,
                bufferSource,
                LightTexture.FULL_BRIGHT,
                OverlayTexture.NO_OVERLAY);

        pose.popPose();
    }

    private BlockState hubBlock() {
        return this.enabled ? HUB_BLOCK : DISABLED_BLOCK;
    }

    private BlockState blockFor(Direction dir) {
        if (!this.enabled) {
            return DISABLED_BLOCK;
        }

        return switch (dir) {
            case EAST -> Blocks.RED_WOOL.defaultBlockState();
            case WEST -> Blocks.PINK_WOOL.defaultBlockState();
            case UP -> Blocks.LIME_WOOL.defaultBlockState();
            case DOWN -> Blocks.GREEN_WOOL.defaultBlockState();
            case SOUTH -> Blocks.LIGHT_BLUE_WOOL.defaultBlockState();
            case NORTH -> Blocks.CYAN_WOOL.defaultBlockState();
        };
    }

    private static float playerYaw(float partialTick) {
        Minecraft mc = Minecraft.getInstance();

        if (mc.player == null) {
            return 0.0f;
        }

        return mc.player.getViewYRot(partialTick);
    }

    private static final class View {

        private final float rightX;
        private final float rightZ;

        private final float upX;
        private final float upY;
        private final float upZ;

        private final float depthX;
        private final float depthY;
        private final float depthZ;

        private View(float yawDegrees) {
            float yaw = (float) Math.toRadians(yawDegrees);

            float sinYaw = Mth.sin(yaw);
            float cosYaw = Mth.cos(yaw);

            float sinPitch = Mth.sin((float) Math.toRadians(CAMERA_PITCH));
            float cosPitch = Mth.cos((float) Math.toRadians(CAMERA_PITCH));

            float forwardX = -sinYaw;
            float forwardZ = cosYaw;

            this.rightX = -cosYaw;
            this.rightZ = -sinYaw;

            this.upX = forwardX * sinPitch;
            this.upY = cosPitch;
            this.upZ = forwardZ * sinPitch;

            this.depthX = forwardX * cosPitch;
            this.depthY = -sinPitch;
            this.depthZ = forwardZ * cosPitch;
        }

        private float[] project(float x, float y, float z) {
            return new float[] {
                    (x * this.rightX + z * this.rightZ) * PIXELS_PER_UNIT,
                    -(x * this.upX + y * this.upY + z * this.upZ) * PIXELS_PER_UNIT,
                    (x * this.depthX + y * this.depthY + z * this.depthZ) * PIXELS_PER_UNIT
            };
        }
    }
}
