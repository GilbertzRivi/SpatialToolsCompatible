package net.oktawia.spatialtoolscmp.client.renderer.extensions;

import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.Set;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import org.jetbrains.annotations.Nullable;

import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.data.ModelData;

import appeng.api.implementations.items.IFacadeItem;
import appeng.blockentity.networking.CableBusBlockEntity;
import appeng.client.render.cablebus.CableBusRenderState;
import appeng.client.render.cablebus.FacadeBuilder;
import appeng.client.render.cablebus.FacadeRenderState;
import appeng.thirdparty.fabric.Mesh;

import net.oktawia.spatialtoolscmp.client.renderer.BlockRenderExtension;
import net.oktawia.spatialtoolscmp.client.renderer.PreviewBlock;
import net.oktawia.spatialtoolscmp.client.renderer.PreviewBlockAndTintGetter;
import net.oktawia.spatialtoolscmp.mixin.ae2.CableBusBakedModelAccessor;
import net.oktawia.spatialtoolscmp.util.NbtUtil;
import net.oktawia.spatialtoolscmp.util.StructureToolKeys;

public final class AE2BlockRenderExtension implements BlockRenderExtension {

    private static final String AE2_CABLE_BUS_ID = "ae2:cable_bus";

    @Override
    public boolean canRender(BlockState state, @Nullable CompoundTag rawBeTag) {
        return isAe2CableBusTag(rawBeTag);
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
        ModelData ae2ModelData = getCableBusModelData(
                previewBlock,
                localLevel,
                localPos,
                modelData);

        modelRenderer.tesselateBlock(
                localLevel,
                model,
                state,
                localPos,
                poseStack,
                vertexConsumer,
                false,
                RandomSource.create(seed),
                seed,
                OverlayTexture.NO_OVERLAY,
                ae2ModelData,
                renderType);

        renderFacadeQuads(
                model,
                ae2ModelData.get(CableBusRenderState.PROPERTY),
                localLevel,
                seed,
                renderType,
                poseStack,
                vertexConsumer);

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
        ModelData modelData = getCableBusModelData(
                previewBlock,
                localLevel,
                localPos,
                ModelData.EMPTY);

        for (RenderType renderType : collectCableBusRenderTypes(model, state, modelData, seed)) {
            RenderType guiSafeRenderType = toGuiSafeRenderType(renderType);

            dispatcher.getModelRenderer().tesselateBlock(
                    localLevel,
                    model,
                    state,
                    localPos,
                    poseStack,
                    bufferSource.getBuffer(guiSafeRenderType),
                    false,
                    RandomSource.create(seed),
                    seed,
                    OverlayTexture.NO_OVERLAY,
                    modelData,
                    renderType);

            renderFacadeQuads(
                    model,
                    modelData.get(CableBusRenderState.PROPERTY),
                    localLevel,
                    seed,
                    renderType,
                    poseStack,
                    bufferSource.getBuffer(guiSafeRenderType));
        }

        return true;
    }

    @Override
    public @Nullable Iterable<RenderType> getPreviewRenderTypes(
            PreviewBlock previewBlock,
            int[] sideMap,
            BlockRenderDispatcher dispatcher,
            PreviewBlockAndTintGetter localLevel,
            BakedModel model,
            BlockState state,
            BlockPos localPos,
            long seed,
            ModelData modelData) {
        Set<RenderType> renderTypes = new LinkedHashSet<>();

        for (RenderType renderType : model.getRenderTypes(state, RandomSource.create(seed), modelData)) {
            renderTypes.add(renderType);
        }

        addFacadeRenderTypes(renderTypes, previewBlock);

        return renderTypes;
    }

    private static Set<RenderType> collectCableBusRenderTypes(
            BakedModel model,
            BlockState state,
            ModelData modelData,
            long seed) {
        Set<RenderType> renderTypes = new LinkedHashSet<>();

        for (RenderType renderType : model.getRenderTypes(state, RandomSource.create(seed), modelData)) {
            renderTypes.add(renderType);
        }

        CableBusRenderState renderState = modelData.get(CableBusRenderState.PROPERTY);

        if (renderState != null) {
            for (FacadeRenderState facade : renderState.getFacades().values()) {
                for (RenderType renderType : ItemBlockRenderTypes.getRenderLayers(facade.getSourceBlock())) {
                    renderTypes.add(renderType);
                }
            }
        }

        return renderTypes;
    }

    private static void addFacadeRenderTypes(Set<RenderType> renderTypes, @Nullable PreviewBlock previewBlock) {
        if (previewBlock == null) {
            return;
        }

        CompoundTag rawBeTag = previewBlock.blockEntityTag();

        if (rawBeTag == null) {
            return;
        }

        for (Direction side : Direction.values()) {
            BlockState facadeState = readFacadeBlockState(rawBeTag, side);

            if (facadeState == null) {
                continue;
            }

            for (RenderType renderType : ItemBlockRenderTypes.getRenderLayers(facadeState)) {
                renderTypes.add(renderType);
            }
        }
    }

    private static RenderType toGuiSafeRenderType(RenderType renderType) {
        String name = renderType.toString();

        if (name.contains("translucent")) {
            return RenderType.entityTranslucent(TextureAtlas.LOCATION_BLOCKS);
        }

        return RenderType.entityCutout(TextureAtlas.LOCATION_BLOCKS);
    }

    private static ModelData getCableBusModelData(
            @Nullable PreviewBlock previewBlock,
            PreviewBlockAndTintGetter localLevel,
            BlockPos localPos,
            ModelData fallback) {
        BlockEntity blockEntity = localLevel.getBlockEntity(localPos);

        if (!(blockEntity instanceof CableBusBlockEntity)) {
            return fallback;
        }

        ModelData modelData;

        try {
            modelData = blockEntity.getModelData();
        } catch (Throwable ignored) {
            return fallback;
        }

        if (modelData == null) {
            return fallback;
        }

        CableBusRenderState renderState = modelData.get(CableBusRenderState.PROPERTY);

        if (renderState == null) {
            return modelData;
        }

        CableBusRenderState transformedState = copyCableBusRenderState(renderState);
        applyFacadesFromRawTag(transformedState, localLevel, localPos, previewBlock);

        return ModelData.builder()
                .with(CableBusRenderState.PROPERTY, transformedState)
                .build();
    }

    private static void applyFacadesFromRawTag(
            CableBusRenderState renderState,
            PreviewBlockAndTintGetter localLevel,
            BlockPos localPos,
            @Nullable PreviewBlock previewBlock) {
        if (previewBlock == null) {
            return;
        }

        CompoundTag rawBeTag = previewBlock.blockEntityTag();

        if (rawBeTag == null) {
            return;
        }

        boolean hasAnyFacade = false;

        for (Direction side : Direction.values()) {
            if (readFacadeBlockState(rawBeTag, side) != null) {
                hasAnyFacade = true;
                break;
            }
        }

        if (!hasAnyFacade) {
            return;
        }

        renderState.getFacades().clear();

        for (Direction side : Direction.values()) {
            BlockState facadeState = readFacadeBlockState(rawBeTag, side);

            if (facadeState == null) {
                continue;
            }

            boolean transparent;

            try {
                transparent = !facadeState.isSolidRender(localLevel, localPos);
            } catch (Throwable ignored) {
                transparent = false;
            }

            renderState.getFacades().put(
                    side,
                    new FacadeRenderState(facadeState, transparent));
        }
    }

    private static void renderFacadeQuads(
            BakedModel model,
            @Nullable CableBusRenderState renderState,
            PreviewBlockAndTintGetter localLevel,
            long seed,
            RenderType renderType,
            PoseStack poseStack,
            VertexConsumer vertexConsumer) {
        if (renderState == null || renderState.getFacades().isEmpty()) {
            return;
        }

        if (!(model instanceof CableBusBakedModelAccessor accessor)) {
            return;
        }

        try {
            FacadeBuilder facadeBuilder = accessor.getFacadeBuilder();

            EnumMap<Direction, ModelData> facadeData = new EnumMap<>(Direction.class);

            for (Direction side : renderState.getFacades().keySet()) {
                facadeData.put(side, ModelData.EMPTY);
            }

            Mesh mesh = facadeBuilder.getFacadeMesh(
                    renderState,
                    () -> RandomSource.create(seed),
                    localLevel,
                    facadeData,
                    renderType);

            for (BakedQuad quad : mesh.toBakedBlockQuads()) {
                vertexConsumer.putBulkData(
                        poseStack.last(),
                        quad,
                        1f, 1f, 1f,
                        0xF000F0,
                        OverlayTexture.NO_OVERLAY);
            }
        } catch (Throwable ignored) {
        }
    }

    @Nullable
    private static BlockState readFacadeBlockState(CompoundTag rawBeTag, Direction side) {
        String key = facadeKey(side);

        if (!rawBeTag.contains(key, Tag.TAG_COMPOUND)) {
            return null;
        }

        ItemStack facadeStack = NbtUtil.tryReadSavedItemStack(rawBeTag.getCompound(key));

        if (facadeStack.isEmpty() || !(facadeStack.getItem() instanceof IFacadeItem facadeItem)) {
            return null;
        }

        return facadeItem.getTextureBlockState(facadeStack);
    }

    private static String facadeKey(Direction direction) {
        return switch (direction) {
            case NORTH -> "facadeNorth";
            case SOUTH -> "facadeSouth";
            case EAST -> "facadeEast";
            case WEST -> "facadeWest";
            case UP -> "facadeUp";
            case DOWN -> "facadeDown";
        };
    }

    private static CableBusRenderState copyCableBusRenderState(CableBusRenderState source) {
        CableBusRenderState copy = new CableBusRenderState();

        copy.setPos(source.getPos());
        copy.setCableType(source.getCableType());
        copy.setCoreType(source.getCoreType());
        copy.setCableColor(source.getCableColor());

        copy.getConnectionTypes().putAll(source.getConnectionTypes());
        copy.getCableBusAdjacent().addAll(source.getCableBusAdjacent());
        copy.getChannelsOnSide().putAll(source.getChannelsOnSide());

        copy.getAttachments().putAll(source.getAttachments());
        copy.getAttachmentConnections().putAll(source.getAttachmentConnections());
        copy.getFacades().putAll(source.getFacades());
        copy.getPartModelData().putAll(source.getPartModelData());
        copy.getBoundingBoxes().addAll(source.getBoundingBoxes());

        return copy;
    }

    private static boolean isAe2CableBusTag(@Nullable CompoundTag rawBeTag) {
        if (rawBeTag == null) {
            return false;
        }

        String id = rawBeTag.getString("id");

        if (AE2_CABLE_BUS_ID.equals(id)) {
            return true;
        }

        if (!id.isBlank()) {
            return false;
        }

        for (String key : StructureToolKeys.AE2_CABLE_BUS_KEYS) {
            if (rawBeTag.contains(key)) {
                return true;
            }
        }

        return false;
    }
}
