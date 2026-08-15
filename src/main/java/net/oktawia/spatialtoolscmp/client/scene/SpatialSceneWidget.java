package net.oktawia.spatialtoolscmp.client.scene;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

import com.mojang.blaze3d.vertex.PoseStack;

import org.joml.Vector3f;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import net.oktawia.spatialtoolscmp.client.renderer.PreviewChunkGeometry;

public class SpatialSceneWidget {

    private final SpatialSceneRenderer renderer;
    private final Set<BlockPos> core = new LinkedHashSet<>();

    private int positionX;
    private int positionY;
    private int sizeWidth;
    private int sizeHeight;

    private Vector3f center = new Vector3f();
    private float rotationYaw = 25;
    private float rotationPitch = -135;
    private float zoom = 5;
    private float orthoRange = 1;
    private boolean ortho;

    public SpatialSceneWidget(int x, int y, int width, int height, Level level) {
        this.positionX = x;
        this.positionY = y;
        this.sizeWidth = width;
        this.sizeHeight = height;

        this.renderer = new SpatialSceneRenderer(level);
        this.renderer.setRenderedBlocks(this.core);
        this.renderer.setBeforeBatchEnd(this::renderBeforeBatchEnd);
        this.renderer.setGeometryFiller(this::fillStaticGeometry);
    }

    public void close() {
        this.renderer.close();
    }

    public int getPositionX() {
        return this.positionX;
    }

    public int getPositionY() {
        return this.positionY;
    }

    public int getSizeWidth() {
        return this.sizeWidth;
    }

    public int getSizeHeight() {
        return this.sizeHeight;
    }

    public void setSelfPosition(int x, int y) {
        this.positionX = x;
        this.positionY = y;
    }

    public void setSize(int width, int height) {
        this.sizeWidth = width;
        this.sizeHeight = height;
    }

    public SpatialSceneWidget useOrtho(boolean ortho) {
        this.ortho = ortho;
        this.renderer.useOrtho(ortho);
        return this;
    }

    public SpatialSceneWidget setOrthoRange(float orthoRange) {
        this.orthoRange = orthoRange;
        return this;
    }

    public SpatialSceneWidget setClearColor(int clearColor) {
        this.renderer.setClearColor(clearColor);
        return this;
    }

    public SpatialSceneWidget setRenderedCore(Collection<BlockPos> blocks) {
        this.core.clear();
        this.core.addAll(blocks);
        this.renderer.invalidate();
        return this;
    }

    public SpatialSceneWidget setCenter(Vector3f center) {
        this.center = new Vector3f(center);
        return this;
    }

    public SpatialSceneWidget setCameraYawAndPitch(float rotationYaw, float rotationPitch) {
        this.rotationYaw = rotationYaw;
        this.rotationPitch = rotationPitch;
        return this;
    }

    public SpatialSceneWidget setZoom(float zoom) {
        this.zoom = zoom;
        return this;
    }

    public void draw(GuiGraphics graphics, float partialTicks) {
        this.renderer.setCameraOrtho(
                this.orthoRange * this.zoom,
                this.orthoRange * this.zoom,
                this.orthoRange * this.zoom);
        this.renderer.setCameraLookAt(
                this.center,
                this.ortho ? 0.1F : this.zoom,
                Math.toRadians(this.rotationPitch),
                Math.toRadians(this.rotationYaw));

        this.renderer.render(
                graphics.pose(),
                this.positionX,
                this.positionY,
                this.sizeWidth,
                this.sizeHeight);
    }

    protected void renderBeforeBatchEnd(MultiBufferSource bufferSource, float partialTicks) {
    }

    protected void fillStaticGeometry(PreviewChunkGeometry.LayerSink sink, PoseStack poseStack) {
    }
}
