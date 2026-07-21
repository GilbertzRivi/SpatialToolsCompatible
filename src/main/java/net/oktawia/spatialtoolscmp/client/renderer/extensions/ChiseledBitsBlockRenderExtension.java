package net.oktawia.spatialtoolscmp.client.renderer.extensions;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import mod.chiselsandbits.api.multistate.snapshot.IMultiStateSnapshot;
import mod.chiselsandbits.block.entities.storage.SimpleStateEntryStorage;
import mod.chiselsandbits.client.model.baked.chiseled.ChiselRenderType;
import mod.chiselsandbits.client.model.baked.chiseled.ChiseledBlockBakedModelManager;
import mod.chiselsandbits.utils.LZ4DataCompressionUtils;
import mod.chiselsandbits.utils.MultiStateSnapshotUtils;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.data.ModelData;
import net.oktawia.spatialtoolscmp.SpatialToolsCMP;
import net.oktawia.spatialtoolscmp.client.renderer.BlockRenderExtension;
import net.oktawia.spatialtoolscmp.client.renderer.PreviewBlock;
import net.oktawia.spatialtoolscmp.client.renderer.PreviewBlockAndTintGetter;
import net.oktawia.spatialtoolscmp.util.StructureToolKeys;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public final class ChiseledBitsBlockRenderExtension implements BlockRenderExtension {

    private static final String NBT_ID = "id";
    private static final String NBT_DATA = "data";
    private static final String NBT_CHISELED_DATA = "chiseledData";

    private @Nullable CompoundTag cachedTag;
    private @Nullable IMultiStateSnapshot cachedSnapshot;

    @Override
    public boolean canRender(BlockState state, @Nullable CompoundTag rawBeTag) {
        return rawBeTag != null && StructureToolKeys.CHISELED_BE_ID.equals(rawBeTag.getString(NBT_ID));
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
        IMultiStateSnapshot snapshot = getSnapshot(previewBlock);

        if (snapshot == null) {
            return null;
        }

        List<RenderType> renderTypes = new ArrayList<>();

        for (ChiselRenderType chiselRenderType : ChiselRenderType.values()) {
            if (chiselRenderType.isRequiredForRendering(snapshot) && !renderTypes.contains(chiselRenderType.layer)) {
                renderTypes.add(chiselRenderType.layer);
            }
        }

        return renderTypes;
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
        IMultiStateSnapshot snapshot = getSnapshot(previewBlock);

        if (snapshot == null) {
            return false;
        }

        for (BakedModel chiseledModel : buildModels(snapshot, renderType)) {
            modelRenderer.tesselateBlock(
                    localLevel,
                    chiseledModel,
                    state,
                    localPos,
                    poseStack,
                    vertexConsumer,
                    false,
                    RandomSource.create(seed),
                    seed,
                    OverlayTexture.NO_OVERLAY,
                    ModelData.EMPTY,
                    renderType
            );
        }

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
        IMultiStateSnapshot snapshot = getSnapshot(previewBlock);

        if (snapshot == null) {
            return false;
        }

        for (ChiselRenderType chiselRenderType : ChiselRenderType.values()) {
            if (!chiselRenderType.isRequiredForRendering(snapshot)) {
                continue;
            }

            RenderType renderType = chiselRenderType.layer;

            dispatcher.getModelRenderer().tesselateBlock(
                    localLevel,
                    ChiseledBlockBakedModelManager.getInstance().get(
                            snapshot,
                            snapshot.getStatics().getPrimaryState(),
                            chiselRenderType,
                            null,
                            null,
                            localPos,
                            renderType
                    ),
                    state,
                    localPos,
                    poseStack,
                    bufferSource.getBuffer(renderType),
                    false,
                    RandomSource.create(seed),
                    seed,
                    OverlayTexture.NO_OVERLAY,
                    ModelData.EMPTY,
                    renderType
            );
        }

        return true;
    }

    private static List<BakedModel> buildModels(IMultiStateSnapshot snapshot, RenderType renderType) {
        List<BakedModel> models = new ArrayList<>();

        for (ChiselRenderType chiselRenderType : ChiselRenderType.values()) {
            if (chiselRenderType.layer != renderType || !chiselRenderType.isRequiredForRendering(snapshot)) {
                continue;
            }

            models.add(ChiseledBlockBakedModelManager.getInstance().get(
                    snapshot,
                    snapshot.getStatics().getPrimaryState(),
                    chiselRenderType,
                    null,
                    null,
                    BlockPos.ZERO,
                    renderType
            ));
        }

        return models;
    }

    private @Nullable IMultiStateSnapshot getSnapshot(PreviewBlock previewBlock) {
        CompoundTag rawBeTag = previewBlock.blockEntityTag();

        if (rawBeTag == null) {
            return null;
        }

        if (rawBeTag == cachedTag) {
            return cachedSnapshot;
        }

        cachedTag = rawBeTag;
        cachedSnapshot = buildSnapshot(rawBeTag, readOps(previewBlock.cloneMetadata()));

        return cachedSnapshot;
    }

    private static @Nullable IMultiStateSnapshot buildSnapshot(CompoundTag rawBeTag, int[] ops) {
        if (!rawBeTag.contains(NBT_DATA, Tag.TAG_COMPOUND)) {
            return null;
        }

        try {
            SimpleStateEntryStorage storage = new SimpleStateEntryStorage();

            Consumer<CompoundTag> reader = payload -> storage.deserializeNBT(payload.getCompound(NBT_CHISELED_DATA));
            LZ4DataCompressionUtils.decompress(rawBeTag.getCompound(NBT_DATA), reader);

            IMultiStateSnapshot snapshot = MultiStateSnapshotUtils.createFromStorage(storage);
            applyOps(snapshot, ops);

            return snapshot;
        } catch (Throwable t) {
            SpatialToolsCMP.getLOGGER().debug(t.getLocalizedMessage());
            return null;
        }
    }

    private static void applyOps(IMultiStateSnapshot snapshot, int[] ops) {
        for (int op : ops) {
            switch (op) {
                case StructureToolKeys.CHISELED_OP_ROTATE_CW -> snapshot.rotate(Direction.Axis.Y, 1);
                case StructureToolKeys.CHISELED_OP_ROTATE_180 -> snapshot.rotate(Direction.Axis.Y, 2);
                case StructureToolKeys.CHISELED_OP_ROTATE_CCW -> snapshot.rotate(Direction.Axis.Y, 3);
                case StructureToolKeys.CHISELED_OP_MIRROR_Z -> snapshot.mirror(Direction.Axis.Z);
                case StructureToolKeys.CHISELED_OP_MIRROR_X -> snapshot.mirror(Direction.Axis.X);
                case StructureToolKeys.CHISELED_OP_MIRROR_Y -> snapshot.mirror(Direction.Axis.Y);
                default -> {
                }
            }
        }
    }

    private static int[] readOps(@Nullable CompoundTag cloneMetadata) {
        if (cloneMetadata == null || !cloneMetadata.contains(StructureToolKeys.CLONE_KEY_CHISELED, Tag.TAG_COMPOUND)) {
            return new int[0];
        }

        return cloneMetadata.getCompound(StructureToolKeys.CLONE_KEY_CHISELED)
                .getIntArray(StructureToolKeys.CHISELED_KEY_OPS);
    }
}
