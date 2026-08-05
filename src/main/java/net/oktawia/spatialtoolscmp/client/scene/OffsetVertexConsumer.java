package net.oktawia.spatialtoolscmp.client.scene;

import com.mojang.blaze3d.vertex.VertexConsumer;

public class OffsetVertexConsumer implements VertexConsumer {

    private final VertexConsumer delegate;

    private double offsetX;
    private double offsetY;
    private double offsetZ;

    public OffsetVertexConsumer(VertexConsumer delegate) {
        this.delegate = delegate;
    }

    public void setOffset(double x, double y, double z) {
        this.offsetX = x;
        this.offsetY = y;
        this.offsetZ = z;
    }

    public void clearOffset() {
        setOffset(0, 0, 0);
    }

    @Override
    public VertexConsumer vertex(double x, double y, double z) {
        return this.delegate.vertex(x + this.offsetX, y + this.offsetY, z + this.offsetZ);
    }

    @Override
    public VertexConsumer color(int red, int green, int blue, int alpha) {
        return this.delegate.color(red, green, blue, alpha);
    }

    @Override
    public VertexConsumer uv(float u, float v) {
        return this.delegate.uv(u, v);
    }

    @Override
    public VertexConsumer overlayCoords(int u, int v) {
        return this.delegate.overlayCoords(u, v);
    }

    @Override
    public VertexConsumer uv2(int u, int v) {
        return this.delegate.uv2(u, v);
    }

    @Override
    public VertexConsumer normal(float x, float y, float z) {
        return this.delegate.normal(x, y, z);
    }

    @Override
    public void endVertex() {
        this.delegate.endVertex();
    }

    @Override
    public void defaultColor(int red, int green, int blue, int alpha) {
        this.delegate.defaultColor(red, green, blue, alpha);
    }

    @Override
    public void unsetDefaultColor() {
        this.delegate.unsetDefaultColor();
    }
}
