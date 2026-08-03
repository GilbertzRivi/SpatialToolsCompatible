package net.oktawia.spatialtoolscmp.client.renderer.extensions;

import org.cyclops.cyclopscore.datastructure.EnumFacingMap;
import org.cyclops.integrateddynamics.core.blockentity.BlockEntityMultipartTicking;
import org.jetbrains.annotations.Nullable;

import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.registries.ForgeRegistries;

import net.oktawia.spatialtoolscmp.client.renderer.BlockRenderExtension;
import net.oktawia.spatialtoolscmp.client.renderer.PreviewBlock;
import net.oktawia.spatialtoolscmp.client.renderer.PreviewBlockAndTintGetter;
import net.oktawia.spatialtoolscmp.util.StructureToolKeys;

public final class IntegratedDynamicsBlockRenderExtension implements BlockRenderExtension {

    @Override
    public boolean canRender(BlockState state, @Nullable CompoundTag rawBeTag) {
        return isCable(state, rawBeTag);
    }

    @Nullable
    @Override
    public ModelData getPreviewModelData(
            PreviewBlock previewBlock,
            PreviewBlockAndTintGetter localLevel,
            BakedModel model,
            BlockState state,
            @Nullable BlockEntity blockEntity) {
        if (!(blockEntity instanceof BlockEntityMultipartTicking cable)) {
            return null;
        }

        EnumFacingMap<Boolean> connected = cable.getConnected();

        if (connected.isEmpty()) {
            for (Direction side : Direction.values()) {
                connected.put(side, false);
            }
        }

        try {
            return cable.getConnectionState();
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static boolean isCable(BlockState state, @Nullable CompoundTag rawBeTag) {
        ResourceLocation blockId = ForgeRegistries.BLOCKS.getKey(state.getBlock());

        if (blockId != null && StructureToolKeys.INTDYN_CABLE_BLOCK_ID.equals(blockId.toString())) {
            return true;
        }

        return rawBeTag != null && StructureToolKeys.INTDYN_CABLE_BE_ID.equals(rawBeTag.getString("id"));
    }
}
