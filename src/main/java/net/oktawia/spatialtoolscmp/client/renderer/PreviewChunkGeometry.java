package net.oktawia.spatialtoolscmp.client.renderer;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import net.minecraft.client.renderer.ChunkBufferBuilderPack;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

public final class PreviewChunkGeometry implements AutoCloseable {

    public interface LayerSink {

        VertexConsumer get(RenderType renderType);
    }

    private final Map<RenderType, VertexBuffer> layers;

    private PreviewChunkGeometry(Map<RenderType, VertexBuffer> layers) {
        this.layers = layers;
    }

    public static RenderType canonicalLayer(RenderType renderType) {
        if (renderType == RenderType.solid()
                || renderType == RenderType.cutout()
                || renderType == RenderType.cutoutMipped()
                || renderType == RenderType.translucent()) {
            return renderType;
        }

        String name = renderType.toString().toLowerCase();

        if (name.contains("translucent") || name.contains("tripwire")) {
            return RenderType.translucent();
        }

        if (name.contains("cutout_mipped") || name.contains("cutoutmipped")) {
            return RenderType.cutoutMipped();
        }

        if (name.contains("cutout")) {
            return RenderType.cutout();
        }

        return RenderType.solid();
    }

    public static PreviewChunkGeometry compile(Consumer<LayerSink> filler, Vec3 sortOrigin) {
        ChunkBufferBuilderPack builderPack = new ChunkBufferBuilderPack();
        Set<RenderType> started = new LinkedHashSet<>();

        ModelBlockRenderer.enableCaching();

        try {
            filler.accept(renderType -> {
                RenderType layer = canonicalLayer(renderType);
                BufferBuilder builder = builderPack.builder(layer);

                if (started.add(layer)) {
                    builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.BLOCK);
                }

                return builder;
            });
        } finally {
            ModelBlockRenderer.clearCache();
        }

        if (started.contains(RenderType.translucent())) {
            BufferBuilder builder = builderPack.builder(RenderType.translucent());

            if (!builder.isCurrentBatchEmpty()) {
                builder.setQuadSorting(VertexSorting.byDistance(
                        (float) sortOrigin.x,
                        (float) sortOrigin.y,
                        (float) sortOrigin.z));
            }
        }

        Map<RenderType, VertexBuffer> layers = new LinkedHashMap<>();

        for (RenderType layer : started) {
            BufferBuilder.RenderedBuffer rendered = builderPack.builder(layer).endOrDiscardIfEmpty();

            if (rendered == null) {
                continue;
            }

            VertexBuffer buffer = new VertexBuffer(VertexBuffer.Usage.STATIC);

            buffer.bind();
            buffer.upload(rendered);
            VertexBuffer.unbind();

            layers.put(layer, buffer);
        }

        return new PreviewChunkGeometry(layers);
    }

    public boolean isEmpty(RenderType layer) {
        VertexBuffer buffer = this.layers.get(layer);

        return buffer == null || buffer.isInvalid();
    }

    public void draw(
            RenderType layer,
            Matrix4f modelViewMatrix,
            Matrix4f projectionMatrix,
            Vec3 cameraPos,
            BlockPos origin) {
        VertexBuffer buffer = this.layers.get(layer);

        if (buffer == null || buffer.isInvalid()) {
            return;
        }

        layer.setupRenderState();

        ShaderInstance shader = RenderSystem.getShader();

        if (shader == null) {
            layer.clearRenderState();
            return;
        }

        for (int i = 0; i < 12; i++) {
            shader.setSampler("Sampler" + i, RenderSystem.getShaderTexture(i));
        }

        if (shader.MODEL_VIEW_MATRIX != null) {
            shader.MODEL_VIEW_MATRIX.set(modelViewMatrix);
        }

        if (shader.PROJECTION_MATRIX != null) {
            shader.PROJECTION_MATRIX.set(projectionMatrix);
        }

        if (shader.COLOR_MODULATOR != null) {
            shader.COLOR_MODULATOR.set(RenderSystem.getShaderColor());
        }

        if (shader.GLINT_ALPHA != null) {
            shader.GLINT_ALPHA.set(RenderSystem.getShaderGlintAlpha());
        }

        if (shader.FOG_START != null) {
            shader.FOG_START.set(RenderSystem.getShaderFogStart());
        }

        if (shader.FOG_END != null) {
            shader.FOG_END.set(RenderSystem.getShaderFogEnd());
        }

        if (shader.FOG_COLOR != null) {
            shader.FOG_COLOR.set(RenderSystem.getShaderFogColor());
        }

        if (shader.FOG_SHAPE != null) {
            shader.FOG_SHAPE.set(RenderSystem.getShaderFogShape().getIndex());
        }

        if (shader.TEXTURE_MATRIX != null) {
            shader.TEXTURE_MATRIX.set(RenderSystem.getTextureMatrix());
        }

        if (shader.GAME_TIME != null) {
            shader.GAME_TIME.set(RenderSystem.getShaderGameTime());
        }

        RenderSystem.setupShaderLights(shader);
        shader.apply();

        Uniform chunkOffset = shader.CHUNK_OFFSET;

        if (chunkOffset != null) {
            chunkOffset.set(
                    (float) (origin.getX() - cameraPos.x),
                    (float) (origin.getY() - cameraPos.y),
                    (float) (origin.getZ() - cameraPos.z));
            chunkOffset.upload();
        }

        buffer.bind();
        buffer.draw();

        if (chunkOffset != null) {
            chunkOffset.set(new Vector3f());
        }

        shader.clear();
        VertexBuffer.unbind();
        layer.clearRenderState();
    }

    @Override
    public void close() {
        if (this.layers.isEmpty()) {
            return;
        }

        if (!RenderSystem.isOnRenderThread()) {
            RenderSystem.recordRenderCall(this::closeBuffers);
            return;
        }

        closeBuffers();
    }

    private void closeBuffers() {
        for (VertexBuffer buffer : this.layers.values()) {
            if (!buffer.isInvalid()) {
                buffer.close();
            }
        }

        this.layers.clear();
    }
}
