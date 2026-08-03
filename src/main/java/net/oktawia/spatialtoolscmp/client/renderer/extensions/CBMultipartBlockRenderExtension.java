package net.oktawia.spatialtoolscmp.client.renderer.extensions;

import java.util.List;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import org.jetbrains.annotations.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.registries.ForgeRegistries;

import codechicken.lib.render.CCRenderState;
import codechicken.lib.render.buffer.TransformingVertexConsumer;
import codechicken.multipart.api.MultipartClientRegistry;
import codechicken.multipart.api.part.MultiPart;
import codechicken.multipart.api.part.render.PartRenderer;
import codechicken.multipart.block.TileMultipart;

import net.oktawia.spatialtoolscmp.client.renderer.BlockRenderExtension;
import net.oktawia.spatialtoolscmp.client.renderer.PreviewBlock;
import net.oktawia.spatialtoolscmp.client.renderer.PreviewBlockAndTintGetter;

public final class CBMultipartBlockRenderExtension implements BlockRenderExtension {

    private static final String CB_MULTIPART_BLOCK_ID = "cb_multipart:multipart";
    private static final String CB_MULTIPART_BE_ID = "cb_multipart:saved_multipart";

    private static final String NBT_ID = "id";
    private static final String NBT_PARTS = "parts";

    private static final List<RenderType> PREVIEW_RENDER_TYPES = List.of(
            RenderType.cutout());

    @Override
    public boolean canRender(BlockState state, @Nullable CompoundTag rawBeTag) {
        return isCbMultipart(state, rawBeTag);
    }

    @Nullable
    @Override
    public Iterable<RenderType> getPreviewRenderTypes(
            PreviewBlock previewBlock,
            int[] sideMap,
            BlockRenderDispatcher dispatcher,
            PreviewBlockAndTintGetter localLevel,
            BakedModel model,
            BlockState state,
            BlockPos localPos,
            long seed,
            ModelData modelData) {
        if (!isCbMultipart(state, previewBlock.blockEntityTag())) {
            return null;
        }

        return PREVIEW_RENDER_TYPES;
    }

    @Override
    public boolean renderForPreview(
            PreviewBlock previewBlock,
            int[] sideMap,
            BlockRenderDispatcher dispatcher,
            ModelBlockRenderer modelRenderer,
            PreviewBlockAndTintGetter localLevel,
            BakedModel model,
            BlockState state,
            BlockPos localPos,
            PoseStack poseStack,
            VertexConsumer vertexConsumer,
            RenderType renderType,
            long seed,
            ModelData modelData) {
        if (!isCbMultipart(state, previewBlock.blockEntityTag())) {
            return false;
        }

        TileMultipart tile = createPreviewTile(
                previewBlock.blockEntityTag(),
                localPos);

        if (tile == null) {
            return true;
        }

        renderMultipartStatic(
                tile,
                localLevel,
                localPos,
                poseStack,
                vertexConsumer,
                LightTexture.FULL_BRIGHT,
                OverlayTexture.NO_OVERLAY);

        return true;
    }

    @Override
    public boolean renderForWidget(
            PreviewBlock previewBlock,
            int[] sideMap,
            BlockRenderDispatcher dispatcher,
            PreviewBlockAndTintGetter localLevel,
            BlockState state,
            BakedModel model,
            BlockPos localPos,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            long seed) {
        if (!isCbMultipart(state, previewBlock.blockEntityTag())) {
            return false;
        }

        TileMultipart tile = createPreviewTile(
                previewBlock.blockEntityTag(),
                localPos);

        if (tile == null) {
            return true;
        }

        VertexConsumer consumer = bufferSource.getBuffer(RenderType.cutout());

        renderMultipartStatic(
                tile,
                localLevel,
                localPos,
                poseStack,
                consumer,
                LightTexture.FULL_BRIGHT,
                OverlayTexture.NO_OVERLAY);

        return true;
    }

    @Nullable
    public static TileMultipart createPreviewTile(
            @Nullable CompoundTag rawBeTag,
            BlockPos localPos) {
        return createPreviewTile(rawBeTag, localPos, Minecraft.getInstance().level);
    }

    @Nullable
    public static TileMultipart createPreviewTile(
            @Nullable CompoundTag rawBeTag,
            BlockPos localPos,
            @Nullable Level previewLevel) {
        if (rawBeTag == null || !rawBeTag.contains(NBT_PARTS, Tag.TAG_LIST)) {
            return null;
        }

        CompoundTag tag = rawBeTag.copy();

        if (!tag.contains(NBT_ID, Tag.TAG_STRING) || tag.getString(NBT_ID).isBlank()) {
            tag.putString(NBT_ID, CB_MULTIPART_BE_ID);
        }

        TileMultipart tile;

        try {
            tile = TileMultipart.fromNBT(tag, localPos);
        } catch (Throwable ignored) {
            return null;
        }

        if (tile == null) {
            return null;
        }

        if (previewLevel != null) {
            try {
                tile.setLevel(previewLevel);
            } catch (Throwable ignored) {
            }
        }

        try {
            tile.clearRemoved();
        } catch (Throwable ignored) {
        }

        try {
            tile.onLoad();
        } catch (Throwable ignored) {
        }

        return tile;
    }

    public static void renderMultipartStatic(
            TileMultipart tile,
            PreviewBlockAndTintGetter localLevel,
            BlockPos localPos,
            PoseStack poseStack,
            VertexConsumer vertexConsumer,
            int packedLight,
            int packedOverlay) {
        for (Object object : tile.getPartList()) {
            if (!(object instanceof MultiPart part)) {
                continue;
            }

            CCRenderState ccrs = CCRenderState.instance();

            setupRenderState(
                    ccrs,
                    localLevel,
                    localPos,
                    poseStack,
                    vertexConsumer,
                    packedLight,
                    packedOverlay);

            renderPartStatic(part, ccrs);
        }
    }

    private static void setupRenderState(
            CCRenderState ccrs,
            PreviewBlockAndTintGetter localLevel,
            BlockPos localPos,
            PoseStack poseStack,
            VertexConsumer vertexConsumer,
            int packedLight,
            int packedOverlay) {
        ccrs.reset();
        ccrs.brightness = packedLight;
        ccrs.overlay = packedOverlay;

        ccrs.bind(
                new TransformingVertexConsumer(
                        new ForcedLightVertexConsumer(vertexConsumer, packedLight),
                        poseStack),
                DefaultVertexFormat.BLOCK);
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static void renderPartStatic(
            MultiPart part,
            CCRenderState ccrs) {
        PartRenderer renderer;

        try {
            renderer = MultipartClientRegistry.getRenderer(part.getType());
        } catch (Throwable ignored) {
            return;
        }

        if (renderer == null) {
            return;
        }

        try {
            renderer.renderStatic(part, null, ccrs);
        } catch (Throwable ignored) {
        }
    }

    private static boolean isCbMultipart(BlockState state, @Nullable CompoundTag rawBeTag) {
        ResourceLocation blockId = ForgeRegistries.BLOCKS.getKey(state.getBlock());

        if (blockId != null && CB_MULTIPART_BLOCK_ID.equals(blockId.toString())) {
            return true;
        }

        if (rawBeTag == null) {
            return false;
        }

        String beId = rawBeTag.getString(NBT_ID);

        return CB_MULTIPART_BE_ID.equals(beId)
                || (beId.isBlank() && rawBeTag.contains(NBT_PARTS, Tag.TAG_LIST));
    }

    private static final class ForcedLightVertexConsumer implements VertexConsumer {

        private final VertexConsumer delegate;
        private final int packedLight;

        private ForcedLightVertexConsumer(VertexConsumer delegate, int packedLight) {
            this.delegate = delegate;
            this.packedLight = packedLight;
        }

        @Override
        public VertexConsumer vertex(double x, double y, double z) {
            delegate.vertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer color(int red, int green, int blue, int alpha) {
            delegate.color(red, green, blue, alpha);
            return this;
        }

        @Override
        public VertexConsumer uv(float u, float v) {
            delegate.uv(u, v);
            return this;
        }

        @Override
        public VertexConsumer overlayCoords(int u, int v) {
            delegate.overlayCoords(u, v);
            return this;
        }

        @Override
        public VertexConsumer uv2(int u, int v) {
            delegate.uv2(packedLight);
            return this;
        }

        @Override
        public VertexConsumer uv2(int packedLight) {
            delegate.uv2(this.packedLight);
            return this;
        }

        @Override
        public VertexConsumer normal(float x, float y, float z) {
            delegate.normal(x, y, z);
            return this;
        }

        @Override
        public void endVertex() {
            delegate.endVertex();
        }

        @Override
        public void defaultColor(int red, int green, int blue, int alpha) {
            delegate.defaultColor(red, green, blue, alpha);
        }

        @Override
        public void unsetDefaultColor() {
            delegate.unsetDefaultColor();
        }
    }
}
