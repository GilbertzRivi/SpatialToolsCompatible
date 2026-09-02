package net.oktawia.spatialtoolscmp.client.misc.widgets;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import org.jetbrains.annotations.Nullable;

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

import lombok.Setter;

import net.oktawia.spatialtoolscmp.defs.LangDefs;

public class RotationAxisWidget {

    public static final int SIZE = 132;

    private static final float PIXELS_PER_UNIT = 46.0f;

    private static final float CAMERA_PITCH = 24.0f;

    private static final float GUI_DEPTH = 150.0f;

    private static final float HUB_SIZE = 0.5f;

    private static final float[] ARM_DISTANCES = { 0.40f, 0.70f, 1.00f };
    private static final float ARM_SIZE = 0.3f;

    private static final float HOVER_GROW = 1.25f;

    private static final float HIT_RADIUS = 0.25f;

    private static final float HUB_HIT_RADIUS = 0.32f;

    private static final Direction.Axis[] AXES = Direction.Axis.values();

    private static final BlockState HUB_BLOCK = Blocks.WHITE_WOOL.defaultBlockState();
    private static final BlockState DISABLED_BLOCK = Blocks.GRAY_WOOL.defaultBlockState();

    private int x;
    private int y;
    @Setter
    private boolean enabled = true;

    private @Nullable Direction.Axis selectedAxis;

    public void setPosition(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public void setSelectedAxis(@Nullable Direction.Axis axis) {
        this.selectedAxis = axis;
    }

    public @Nullable Direction.Axis getSelectedAxis() {
        return this.selectedAxis;
    }

    public boolean isHoveringHub(double mouseX, double mouseY) {
        if (!this.enabled || this.selectedAxis == null) {
            return false;
        }

        float dx = (float) mouseX - (this.x + SIZE / 2.0f);
        float dy = (float) mouseY - (this.y + SIZE / 2.0f);

        return Mth.sqrt(dx * dx + dy * dy) <= HUB_HIT_RADIUS * PIXELS_PER_UNIT;
    }

    public @Nullable Direction.Axis getHoveredAxis(double mouseX, double mouseY) {
        if (!this.enabled || isHoveringHub(mouseX, mouseY)) {
            return null;
        }

        View view = new View(playerYaw(Minecraft.getInstance().getFrameTime()));

        float centerX = this.x + SIZE / 2.0f;
        float centerY = this.y + SIZE / 2.0f;
        float hitRange = HIT_RADIUS * PIXELS_PER_UNIT;

        Direction.Axis best = null;
        float bestDistance = Float.MAX_VALUE;
        float bestDepth = Float.MAX_VALUE;

        for (Direction.Axis axis : AXES) {
            if (this.selectedAxis != null && axis != this.selectedAxis) {
                continue;
            }

            for (int sign = -1; sign <= 1; sign += 2) {
                for (float armDistance : ARM_DISTANCES) {
                    float[] end = view.project(
                            stepX(axis) * armDistance * sign,
                            stepY(axis) * armDistance * sign,
                            stepZ(axis) * armDistance * sign);

                    float dx = (float) mouseX - (centerX + end[0]);
                    float dy = (float) mouseY - (centerY + end[1]);
                    float distance = Mth.sqrt(dx * dx + dy * dy);

                    if (distance > hitRange) {
                        continue;
                    }

                    boolean closerToCursor = distance < bestDistance - 2.0f;
                    boolean sameSpotButNearerCamera = distance < bestDistance + 2.0f && end[2] < bestDepth;

                    if (closerToCursor || sameSpotButNearerCamera) {
                        bestDistance = distance;
                        bestDepth = end[2];
                        best = axis;
                    }
                }
            }
        }

        return best;
    }

    public LangDefs tooltipFor(Direction.Axis axis) {
        return switch (axis) {
            case X -> LangDefs.ROTATE_AXIS_X_TOOLTIP;
            case Y -> LangDefs.ROTATE_AXIS_Y_TOOLTIP;
            case Z -> LangDefs.ROTATE_AXIS_Z_TOOLTIP;
        };
    }

    public void render(GuiGraphics graphics, double mouseX, double mouseY, float partialTick) {
        Direction.Axis hovered = getHoveredAxis(mouseX, mouseY);

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

        float hubSize = isHoveringHub(mouseX, mouseY) ? HUB_SIZE * HOVER_GROW : HUB_SIZE;

        renderCube(dispatcher, pose, bufferSource, hubBlock(), 0.0f, 0.0f, 0.0f, hubSize);

        for (Direction.Axis axis : AXES) {
            if (this.selectedAxis != null && axis != this.selectedAxis) {
                continue;
            }

            BlockState state = blockFor(axis);
            float size = axis == hovered ? ARM_SIZE * HOVER_GROW : ARM_SIZE;

            for (int sign = -1; sign <= 1; sign += 2) {
                for (float distance : ARM_DISTANCES) {
                    renderCube(
                            dispatcher,
                            pose,
                            bufferSource,
                            state,
                            stepX(axis) * distance * sign,
                            stepY(axis) * distance * sign,
                            stepZ(axis) * distance * sign,
                            size);
                }
            }
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
            float offsetX,
            float offsetY,
            float offsetZ,
            float size) {
        pose.pushPose();

        pose.translate(offsetX, offsetY, offsetZ);

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

    private BlockState blockFor(Direction.Axis axis) {
        if (!this.enabled) {
            return DISABLED_BLOCK;
        }

        return switch (axis) {
            case X -> Blocks.RED_WOOL.defaultBlockState();
            case Y -> Blocks.LIME_WOOL.defaultBlockState();
            case Z -> Blocks.BLUE_WOOL.defaultBlockState();
        };
    }

    private static float stepX(Direction.Axis axis) {
        return axis == Direction.Axis.X ? 1.0f : 0.0f;
    }

    private static float stepY(Direction.Axis axis) {
        return axis == Direction.Axis.Y ? 1.0f : 0.0f;
    }

    private static float stepZ(Direction.Axis axis) {
        return axis == Direction.Axis.Z ? 1.0f : 0.0f;
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
