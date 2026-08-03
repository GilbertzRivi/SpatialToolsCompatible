package net.oktawia.spatialtoolscmp.client.renderer;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.client.model.data.ModelData;
import net.oktawia.spatialtoolscmp.SpatialToolsCMP;

public final class PreviewRenderModelDataHelper {

    private PreviewRenderModelDataHelper() {
    }

    public static ModelData getPreviewModelData(
            PreviewStructure structure,
            PreviewBlock previewBlock,
            int[] sideMap,
            String sideMapKey,
            ClientLevel level,
            BakedModel model,
            PreviewBlockAndTintGetter localLevel
    ) {
        return structure.getOrComputeModelData(sideMapKey, previewBlock.pos(), () -> {
            ModelData baseData;
            try {
                baseData = model.getModelData(
                        localLevel,
                        previewBlock.pos(),
                        previewBlock.state(),
                        ModelData.EMPTY
                );
            } catch (Throwable t) {
                SpatialToolsCMP.getLOGGER().debug(t.getLocalizedMessage());
                baseData = ModelData.EMPTY;
            }

            BlockEntity blockEntity = structure.blockEntities(level).get(previewBlock.pos());

            for (BlockRenderExtension extension : BlockRenderExtensions.all()) {
                if (!extension.canRender(previewBlock.state(), previewBlock.blockEntityTag())) {
                    continue;
                }

                ModelData extensionData;
                try {
                    extensionData = extension.getPreviewModelData(
                            previewBlock,
                            localLevel,
                            model,
                            previewBlock.state(),
                            blockEntity
                    );
                } catch (Throwable t) {
                    SpatialToolsCMP.getLOGGER().debug(t.getLocalizedMessage());
                    continue;
                }

                if (extensionData != null) {
                    return extensionData;
                }
            }

            if (blockEntity == null) {
                return baseData;
            }

            try {
                ModelData modelData = blockEntity.getModelData();
                return modelData != null ? modelData : baseData;
            } catch (Throwable t) {
                SpatialToolsCMP.getLOGGER().debug(t.getLocalizedMessage());
                return baseData;
            }
        });
    }
}