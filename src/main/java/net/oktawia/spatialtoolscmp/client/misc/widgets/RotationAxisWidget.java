package net.oktawia.spatialtoolscmp.client.misc.widgets;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3f;
import org.joml.Vector3f;

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

    private static final float PIXELS_PER_UNIT = 42.0f;

    private static final float CAMERA_PITCH = 24.0f;

    private static final float GUI_DEPTH = 150.0f;

    private static final float HUB_SIZE = 0.5f;

    private static final float[] ARM_DISTANCES = { 0.40f, 0.70f, 1.00f };
    private static final float ARM_SIZE = 0.3f;

    private static final float ARC_RADIUS = 0.90f;
    private static final float ARC_START = 25.0f;
    private static final float ARC_SPAN = 110.0f;
    private static final int ARC_SEGMENTS = 12;
    private static final float ARC_SIZE = 0.22f;

    private static final float TIP_SIZE = 0.24f;
    private static final float TIP_FORWARD = 0.10f;

    private static final float[][] BARBS = {
            { 0.22f, 0.10f, 0.10f },
            { 0.21f, 0.21f, 0.21f },
            { 0.19f, 0.32f, 0.32f },
            { 0.17f, 0.43f, 0.43f }
    };

    private static final float HOVER_GROW = 1.25f;

    private static final float HIT_RADIUS = 0.25f;

    private static final float HUB_HIT_RADIUS = 0.32f;

    private static final float HIT_TIE_RANGE = 2.0f;

    private static final Direction.Axis[] AXES = Direction.Axis.values();

    private static final BlockState HUB_BLOCK = Blocks.WHITE_WOOL.defaultBlockState();
    private static final BlockState DISABLED_BLOCK = Blocks.GRAY_WOOL.defaultBlockState();
    private static final BlockState ARROW_BLOCK = Blocks.ANDESITE.defaultBlockState();

    private enum Part {
        HUB,
        AXIS,
        ROTATION
    }

    private enum Role {
        HUB,
        ARM,
        ARC,
        TIP
    }

    private record Target(Part part, @Nullable Direction.Axis axis, boolean clockwise) {
    }

    private record Node(Vector3f position, float size, Role role, Target target) {
    }

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
        Target hovered = hovered(mouseX, mouseY);

        return hovered != null && hovered.part() == Part.HUB && this.selectedAxis != null;
    }

    public @Nullable Direction.Axis getHoveredAxis(double mouseX, double mouseY) {
        Target hovered = hovered(mouseX, mouseY);

        return hovered != null && hovered.part() == Part.AXIS ? hovered.axis() : null;
    }

    public @Nullable Boolean getHoveredRotation(double mouseX, double mouseY) {
        Target hovered = hovered(mouseX, mouseY);

        return hovered != null && hovered.part() == Part.ROTATION ? hovered.clockwise() : null;
    }

    public LangDefs tooltipFor(Direction.Axis axis) {
        return switch (axis) {
            case X -> LangDefs.ROTATE_AXIS_X_TOOLTIP;
            case Y -> LangDefs.ROTATE_AXIS_Y_TOOLTIP;
            case Z -> LangDefs.ROTATE_AXIS_Z_TOOLTIP;
        };
    }

    public void render(GuiGraphics graphics, double mouseX, double mouseY, float partialTick) {
        Target hovered = hovered(mouseX, mouseY);

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

        for (Node node : nodes()) {
            float size = node.target().equals(hovered) ? node.size() * HOVER_GROW : node.size();

            renderCube(dispatcher, pose, bufferSource, blockFor(node), node.position(), size);
        }

        bufferSource.endBatch();

        pose.popPose();

        Lighting.setupFor3DItems();
    }

    private List<Node> nodes() {
        List<Node> nodes = new ArrayList<>();

        nodes.add(new Node(new Vector3f(), HUB_SIZE, Role.HUB, new Target(Part.HUB, this.selectedAxis, false)));

        for (Direction.Axis axis : AXES) {
            if (this.selectedAxis != null && axis != this.selectedAxis) {
                continue;
            }

            Target target = new Target(Part.AXIS, axis, false);
            Vector3f step = axisVector(axis);

            for (int sign = -1; sign <= 1; sign += 2) {
                for (float distance : ARM_DISTANCES) {
                    nodes.add(new Node(new Vector3f(step).mul(distance * sign), ARM_SIZE, Role.ARM, target));
                }
            }
        }

        if (this.selectedAxis != null) {
            addArrow(nodes, this.selectedAxis, false);
            addArrow(nodes, this.selectedAxis, true);
        }

        return nodes;
    }

    private void addArrow(List<Node> nodes, Direction.Axis axis, boolean clockwise) {
        Target target = new Target(Part.ROTATION, axis, clockwise);

        Vector3f u = planeU(axis);
        Vector3f v = planeV(axis);

        float tail = clockwise ? ARC_START + ARC_SPAN + 180.0f : ARC_START;
        float span = clockwise ? -ARC_SPAN : ARC_SPAN;

        for (int i = 0; i < ARC_SEGMENTS - 1; i++) {
            float angle = tail + span * i / (ARC_SEGMENTS - 1);

            nodes.add(new Node(onCircle(u, v, angle, ARC_RADIUS), ARC_SIZE, Role.ARC, target));
        }

        float headAngle = tail + span;

        Vector3f head = onCircle(u, v, headAngle, ARC_RADIUS);
        Vector3f radial = onCircle(u, v, headAngle, 1.0f);
        Vector3f forward = onCircle(u, v, headAngle + 90.0f, clockwise ? -1.0f : 1.0f);

        nodes.add(new Node(
                new Vector3f(head).add(new Vector3f(forward).mul(TIP_FORWARD)),
                TIP_SIZE,
                Role.TIP,
                target));

        for (float[] barb : BARBS) {
            for (int side = -1; side <= 1; side += 2) {
                Vector3f position = new Vector3f(head)
                        .add(new Vector3f(forward).mul(-barb[1]))
                        .add(new Vector3f(radial).mul(barb[2] * side));

                nodes.add(new Node(position, barb[0], Role.TIP, target));
            }
        }
    }

    private @Nullable Target hovered(double mouseX, double mouseY) {
        if (!this.enabled) {
            return null;
        }

        Matrix3f orientation = orientation(Minecraft.getInstance().getFrameTime());

        float centerX = this.x + SIZE / 2.0f;
        float centerY = this.y + SIZE / 2.0f;

        Target best = null;
        float bestDistance = Float.MAX_VALUE;
        float bestNearness = -Float.MAX_VALUE;

        for (Node node : nodes()) {
            Vector3f screen = orientation.transform(new Vector3f(node.position()));

            float dx = (float) mouseX - (centerX + screen.x() * PIXELS_PER_UNIT);
            float dy = (float) mouseY - (centerY - screen.y() * PIXELS_PER_UNIT);
            float distance = Mth.sqrt(dx * dx + dy * dy);

            if (distance > hitRadius(node.role()) * PIXELS_PER_UNIT) {
                continue;
            }

            boolean closerToCursor = distance < bestDistance - HIT_TIE_RANGE;
            boolean sameSpotButNearerCamera = distance < bestDistance + HIT_TIE_RANGE && screen.z() > bestNearness;

            if (closerToCursor || sameSpotButNearerCamera) {
                bestDistance = distance;
                bestNearness = screen.z();
                best = node.target();
            }
        }

        return best;
    }

    private static Matrix3f orientation(float partialTick) {
        return new Matrix3f()
                .rotateX((float) Math.toRadians(CAMERA_PITCH))
                .rotateY((float) Math.toRadians(playerYaw(partialTick) + 180.0f));
    }

    private static float hitRadius(Role role) {
        return role == Role.HUB ? HUB_HIT_RADIUS : HIT_RADIUS;
    }

    private BlockState blockFor(Node node) {
        if (!this.enabled) {
            return DISABLED_BLOCK;
        }

        return switch (node.role()) {
            case HUB -> HUB_BLOCK;
            case ARC -> ARROW_BLOCK;
            case ARM, TIP -> {
                Direction.Axis axis = node.target().axis();

                yield axis == null ? HUB_BLOCK : blockFor(axis);
            }
        };
    }

    private static void renderCube(
            BlockRenderDispatcher dispatcher,
            PoseStack pose,
            MultiBufferSource bufferSource,
            BlockState state,
            Vector3f position,
            float size) {
        pose.pushPose();

        pose.translate(position.x(), position.y(), position.z());

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

    private static BlockState blockFor(Direction.Axis axis) {
        return switch (axis) {
            case X -> Blocks.RED_WOOL.defaultBlockState();
            case Y -> Blocks.LIME_WOOL.defaultBlockState();
            case Z -> Blocks.BLUE_WOOL.defaultBlockState();
        };
    }

    private static Vector3f onCircle(Vector3f u, Vector3f v, float degrees, float radius) {
        float radians = (float) Math.toRadians(degrees);

        return new Vector3f(u).mul(Mth.cos(radians)).add(new Vector3f(v).mul(Mth.sin(radians))).mul(radius);
    }

    private static Vector3f axisVector(Direction.Axis axis) {
        return switch (axis) {
            case X -> new Vector3f(1.0f, 0.0f, 0.0f);
            case Y -> new Vector3f(0.0f, 1.0f, 0.0f);
            case Z -> new Vector3f(0.0f, 0.0f, 1.0f);
        };
    }

    private static Vector3f planeU(Direction.Axis axis) {
        return switch (axis) {
            case X -> new Vector3f(0.0f, 1.0f, 0.0f);
            case Y -> new Vector3f(0.0f, 0.0f, 1.0f);
            case Z -> new Vector3f(1.0f, 0.0f, 0.0f);
        };
    }

    private static Vector3f planeV(Direction.Axis axis) {
        return switch (axis) {
            case X -> new Vector3f(0.0f, 0.0f, 1.0f);
            case Y -> new Vector3f(1.0f, 0.0f, 0.0f);
            case Z -> new Vector3f(0.0f, 1.0f, 0.0f);
        };
    }

    private static float playerYaw(float partialTick) {
        Minecraft mc = Minecraft.getInstance();

        if (mc.player == null) {
            return 0.0f;
        }

        return mc.player.getViewYRot(partialTick);
    }
}
