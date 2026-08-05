package net.oktawia.spatialtoolscmp.client.renderer;

import java.util.Set;

import org.jetbrains.annotations.Nullable;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.registries.ForgeRegistries;

import net.oktawia.spatialtoolscmp.compat.gtceu.GTCEuClientCompat;
import net.oktawia.spatialtoolscmp.logic.ClientPiperExtension;
import net.oktawia.spatialtoolscmp.logic.ClientPiperExtensions;
import net.oktawia.spatialtoolscmp.logic.ClientReplacerExtension;
import net.oktawia.spatialtoolscmp.logic.ClientReplacerExtensions;

public final class PreviewTargetResolver {

    private ItemStack targetItem = ItemStack.EMPTY;

    @Nullable
    public BlockState resolve(ItemStack targetItem) {
        this.targetItem = targetItem;

        for (ClientReplacerExtension ext : ClientReplacerExtensions.get()) {
            BlockState resolved = ext.resolveTargetState(targetItem);

            if (resolved != null && !resolved.isAir()) {
                return resolved;
            }
        }

        for (ClientPiperExtension ext : ClientPiperExtensions.get()) {
            BlockState resolved = ext.resolveTargetState(targetItem);

            if (resolved != null && !resolved.isAir()) {
                return resolved;
            }
        }

        ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(targetItem.getItem());
        Block targetBlock = itemId != null ? ForgeRegistries.BLOCKS.getValue(itemId) : null;

        if (targetBlock != null && !targetBlock.defaultBlockState().isAir()) {
            return targetBlock.defaultBlockState();
        }

        return null;
    }

    @Nullable
    public Iterable<RenderType> previewRenderTypes(BlockState targetState) {
        for (ClientReplacerExtension ext : ClientReplacerExtensions.get()) {
            Iterable<RenderType> types = ext.getPreviewRenderTypes(targetState, this.targetItem);

            if (types != null) {
                return types;
            }
        }

        for (ClientPiperExtension ext : ClientPiperExtensions.get()) {
            Iterable<RenderType> types = ext.getPreviewRenderTypes(targetState, this.targetItem);

            if (types != null) {
                return types;
            }
        }

        return null;
    }

    public boolean needsReplacement(ClientLevel level, BlockPos pos) {
        for (ClientReplacerExtension ext : ClientReplacerExtensions.get()) {
            if (ext.needsReplacement(level, pos, this.targetItem)) {
                return true;
            }
        }

        return false;
    }

    public ModelData buildModelData(
            BlockState targetState,
            BlockPos pos,
            Set<BlockPos> allPositions) {
        for (ClientReplacerExtension ext : ClientReplacerExtensions.get()) {
            ModelData extensionData = ext.buildTargetModelData(
                    targetState,
                    this.targetItem,
                    pos,
                    allPositions);

            if (extensionData != null) {
                return extensionData;
            }
        }

        for (ClientPiperExtension ext : ClientPiperExtensions.get()) {
            ModelData extensionData = ext.buildTargetModelData(
                    targetState,
                    this.targetItem,
                    pos,
                    allPositions);

            if (extensionData != null) {
                return extensionData;
            }
        }

        if (!GTCEuClientCompat.isPipe(targetState)) {
            return ModelData.EMPTY;
        }

        int connections = 0;

        for (Direction direction : Direction.values()) {
            if (allPositions.contains(pos.relative(direction))) {
                connections |= 1 << direction.ordinal();
            }
        }

        return GTCEuClientCompat.pipeModelData(connections, 0);
    }
}
