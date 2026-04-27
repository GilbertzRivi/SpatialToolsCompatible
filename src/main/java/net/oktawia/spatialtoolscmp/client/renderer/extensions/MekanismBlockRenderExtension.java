package net.oktawia.spatialtoolscmp.client.renderer.extensions;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import mekanism.client.model.data.TransmitterModelData;
import mekanism.common.lib.transmitter.ConnectionType;
import mekanism.common.tile.transmitter.TileEntityTransmitter;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.registries.ForgeRegistries;
import net.oktawia.spatialtoolscmp.client.renderer.BlockRenderExtension;
import net.oktawia.spatialtoolscmp.client.renderer.PreviewBlock;
import net.oktawia.spatialtoolscmp.client.renderer.PreviewBlockAndTintGetter;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;

public final class MekanismBlockRenderExtension implements BlockRenderExtension {

    private static final String MOD_ID = "mekanism";
    private static final String NBT_ID = "id";
    private static final String NBT_CONNECTION_PREFIX = "connection";

    @Override
    public boolean canRender(BlockState state, @Nullable CompoundTag rawBeTag) {
        return isMekanismTransmitter(state, rawBeTag);
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
            ModelData modelData
    ) {
        ModelData mekanismData = buildTransmitterModelData(
                previewBlock,
                localLevel,
                localPos,
                sideMap,
                modelData
        );

        return model.getRenderTypes(
                state,
                RandomSource.create(seed),
                mekanismData
        );
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
            ModelData modelData
    ) {
        ModelData mekanismData = buildTransmitterModelData(
                previewBlock,
                localLevel,
                localPos,
                sideMap,
                modelData
        );

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
                mekanismData,
                renderType
        );

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
            long seed
    ) {
        ModelData mekanismData = buildTransmitterModelData(
                previewBlock,
                localLevel,
                localPos,
                sideMap,
                ModelData.EMPTY
        );

        for (RenderType renderType : model.getRenderTypes(
                state,
                RandomSource.create(seed),
                mekanismData
        )) {
            dispatcher.getModelRenderer().tesselateBlock(
                    localLevel,
                    model,
                    state,
                    localPos,
                    poseStack,
                    bufferSource.getBuffer(toGuiSafeRenderType(renderType)),
                    false,
                    RandomSource.create(seed),
                    seed,
                    OverlayTexture.NO_OVERLAY,
                    mekanismData,
                    renderType
            );
        }

        return true;
    }

    private static ModelData buildTransmitterModelData(
            PreviewBlock previewBlock,
            PreviewBlockAndTintGetter localLevel,
            BlockPos localPos,
            int[] sideMap,
            ModelData fallback
    ) {
        CompoundTag rawTag = getRawBlockEntityTag(previewBlock, localLevel, localPos);

        TransmitterModelData transmitterData = new TransmitterModelData();

        for (Direction originalSide : Direction.values()) {
            Direction renderedSide = mapWithSideMap(originalSide, sideMap);

            ConnectionType configuredType = readConnectionType(rawTag, originalSide);

            if (configuredType == ConnectionType.NONE) {
                transmitterData.setConnectionData(renderedSide, ConnectionType.NONE);
                continue;
            }

            if (hasConnectableNeighbor(localLevel, localPos, renderedSide)) {
                transmitterData.setConnectionData(renderedSide, configuredType);
            } else {
                transmitterData.setConnectionData(renderedSide, ConnectionType.NONE);
            }
        }

        transmitterData.setHasColor(hasColor(rawTag));

        return ModelData.builder()
                .with(TileEntityTransmitter.TRANSMITTER_PROPERTY, transmitterData)
                .build();
    }

    private static ConnectionType readConnectionType(
            @Nullable CompoundTag rawTag,
            Direction side
    ) {
        if (rawTag == null) {
            return ConnectionType.NORMAL;
        }

        String key = NBT_CONNECTION_PREFIX + side.ordinal();

        if (!rawTag.contains(key, Tag.TAG_ANY_NUMERIC)) {
            return ConnectionType.NORMAL;
        }

        try {
            return ConnectionType.byIndexStatic(rawTag.getInt(key));
        } catch (Throwable ignored) {
            return ConnectionType.NORMAL;
        }
    }

    private static boolean hasConnectableNeighbor(
            PreviewBlockAndTintGetter localLevel,
            BlockPos localPos,
            Direction side
    ) {
        BlockPos neighborPos = localPos.relative(side);
        BlockState neighborState = localLevel.getBlockState(neighborPos);

        if (neighborState == null || neighborState.isAir()) {
            return false;
        }

        CompoundTag neighborTag = getRawBlockEntityTag(null, localLevel, neighborPos);

        if (isMekanismTransmitter(neighborState, neighborTag)) {
            return true;
        }

        return isMekanismBlock(neighborState, neighborTag);
    }

    private static boolean hasColor(@Nullable CompoundTag rawTag) {
        if (rawTag == null) {
            return false;
        }

        return rawTag.contains("color", Tag.TAG_ANY_NUMERIC)
                || rawTag.contains("color0", Tag.TAG_ANY_NUMERIC)
                || rawTag.contains("color1", Tag.TAG_ANY_NUMERIC)
                || rawTag.contains("color2", Tag.TAG_ANY_NUMERIC)
                || rawTag.contains("color3", Tag.TAG_ANY_NUMERIC)
                || rawTag.contains("color4", Tag.TAG_ANY_NUMERIC)
                || rawTag.contains("color5", Tag.TAG_ANY_NUMERIC);
    }

    private static @Nullable CompoundTag getRawBlockEntityTag(
            @Nullable PreviewBlock previewBlock,
            PreviewBlockAndTintGetter localLevel,
            BlockPos localPos
    ) {
        CompoundTag fromPreview = getRawTagFromPreviewBlock(previewBlock);

        if (fromPreview != null) {
            return fromPreview;
        }

        BlockEntity be = localLevel.getBlockEntity(localPos);

        if (be == null) {
            return null;
        }

        try {
            return be.saveWithFullMetadata();
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static @Nullable CompoundTag getRawTagFromPreviewBlock(@Nullable PreviewBlock previewBlock) {
        if (previewBlock == null) {
            return null;
        }

        String[] possibleAccessors = {
                "blockEntityTag",
                "rawBeTag",
                "rawBlockEntityTag",
                "beTag",
                "nbt"
        };

        for (String accessor : possibleAccessors) {
            try {
                Method method = previewBlock.getClass().getMethod(accessor);
                Object value = method.invoke(previewBlock);

                if (value instanceof CompoundTag tag) {
                    return tag;
                }
            } catch (Throwable ignored) {
            }
        }

        return null;
    }

    private static Direction mapWithSideMap(Direction side, int[] sideMap) {
        if (sideMap == null || side.ordinal() < 0 || side.ordinal() >= sideMap.length) {
            return side;
        }

        int mappedOrdinal = sideMap[side.ordinal()];
        Direction[] values = Direction.values();

        if (mappedOrdinal < 0 || mappedOrdinal >= values.length) {
            return side;
        }

        return values[mappedOrdinal];
    }

    private static RenderType toGuiSafeRenderType(RenderType renderType) {
        String name = renderType.toString();

        if (name.contains("translucent")) {
            return RenderType.entityTranslucent(TextureAtlas.LOCATION_BLOCKS);
        }

        return RenderType.entityCutout(TextureAtlas.LOCATION_BLOCKS);
    }

    private static boolean isMekanismTransmitter(BlockState state, @Nullable CompoundTag rawBeTag) {
        ResourceLocation blockId = ForgeRegistries.BLOCKS.getKey(state.getBlock());

        if (blockId != null
                && MOD_ID.equals(blockId.getNamespace())
                && looksLikeTransmitterPath(blockId.getPath())) {
            return true;
        }

        if (rawBeTag == null) {
            return false;
        }

        String id = rawBeTag.getString(NBT_ID);

        if (id.isBlank()) {
            return false;
        }

        ResourceLocation beId = ResourceLocation.tryParse(id);

        return beId != null
                && MOD_ID.equals(beId.getNamespace())
                && looksLikeTransmitterPath(beId.getPath());
    }

    private static boolean isMekanismBlock(BlockState state, @Nullable CompoundTag rawBeTag) {
        ResourceLocation blockId = ForgeRegistries.BLOCKS.getKey(state.getBlock());

        if (blockId != null && MOD_ID.equals(blockId.getNamespace())) {
            return true;
        }

        if (rawBeTag == null) {
            return false;
        }

        String id = rawBeTag.getString(NBT_ID);

        if (id.isBlank()) {
            return false;
        }

        ResourceLocation beId = ResourceLocation.tryParse(id);

        return beId != null && MOD_ID.equals(beId.getNamespace());
    }

    private static boolean looksLikeTransmitterPath(String path) {
        return path.contains("cable")
                || path.contains("pipe")
                || path.contains("tube")
                || path.contains("transporter")
                || path.contains("conductor");
    }
}