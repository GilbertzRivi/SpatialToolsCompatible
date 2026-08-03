package net.oktawia.spatialtoolscmp.logic.extensions;

import codechicken.multipart.api.ItemMultipart;
import codechicken.multipart.api.part.MultiPart;
import codechicken.multipart.block.TileMultipart;
import com.lowdragmc.lowdraglib.utils.TrackedDummyWorld;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.data.ModelData;
import net.oktawia.spatialtoolscmp.client.renderer.extensions.CBMultipartBlockRenderExtension;
import net.oktawia.spatialtoolscmp.items.PortableSpatialReplacer;
import net.oktawia.spatialtoolscmp.mixin.LevelClientSideAccessor;
import net.oktawia.spatialtoolscmp.logic.ClientPiperExtension;
import net.oktawia.spatialtoolscmp.logic.ClientReplacerExtension;
import net.oktawia.spatialtoolscmp.logic.PiperExtension;
import net.oktawia.spatialtoolscmp.logic.ReplacerContext;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class CBMultipartClientExtension
        implements ClientReplacerExtension, ClientPiperExtension {

    @Override
    public boolean canHandleSource(ClientLevel level, BlockPos pos, BlockState state) {
        return CBMultipartReplacerExtension.partItem(level, pos) != null;
    }

    @Nullable
    @Override
    public Set<BlockPos> computePreviewPositions(
            ClientLevel level,
            BlockPos pos,
            BlockState state,
            ReplacerContext ctx
    ) {
        Item sourceItem = CBMultipartReplacerExtension.partItem(level, pos);

        if (sourceItem == null) {
            return null;
        }

        return PortableSpatialReplacer.floodFill(
                level,
                pos,
                ctx,
                (checkedLevel, checkedPos) ->
                        CBMultipartReplacerExtension.partItem(checkedLevel, checkedPos) == sourceItem
        );
    }

    @Override
    public boolean needsReplacement(ClientLevel level, BlockPos pos, ItemStack target) {
        Item current = CBMultipartReplacerExtension.partItem(level, pos);

        return current != null
                && target.getItem() instanceof ItemMultipart
                && current != target.getItem();
    }

    @Nullable
    @Override
    public PiperExtension.PathAction resolvePathAction(
            ClientLevel level,
            BlockPos pos,
            BlockState state,
            ItemStack target
    ) {
        return CBMultipartPiperExtension.pathAction(level, pos, target);
    }

    @Nullable
    @Override
    public ModelData buildTargetModelData(
            BlockState targetState,
            ItemStack target,
            BlockPos pos,
            Set<BlockPos> allPositions
    ) {
        return null;
    }

    @Nullable
    @Override
    public Iterable<RenderType> getPreviewRenderTypes(BlockState targetState, ItemStack target) {
        return CBMultipartReplacerExtension.hasStashedParts(target)
                ? PREVIEW_RENDER_TYPES
                : null;
    }

    @Override
    public boolean renderRouteBlock(
            BlockState targetState,
            ItemStack target,
            BlockPos pos,
            Set<BlockPos> allPositions,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            RenderType renderType,
            ModelData modelData,
            long seed
    ) {
        CompoundTag parts = CBMultipartReplacerExtension.stashedParts(target);

        if (parts == null) {
            return false;
        }

        TileMultipart tile = previewTile(parts, pos, allPositions);

        if (tile == null) {
            return true;
        }

        CBMultipartBlockRenderExtension.renderMultipartStatic(
                tile,
                null,
                pos,
                poseStack,
                bufferSource.getBuffer(RenderType.cutout()),
                LightTexture.FULL_BRIGHT,
                OverlayTexture.NO_OVERLAY
        );

        return true;
    }

    private static final List<RenderType> PREVIEW_RENDER_TYPES = List.of(RenderType.cutout());

    private static final int MAX_CONNECTION_PASSES = 8;

    @Nullable
    private CompoundTag cachedParts = null;
    private Set<BlockPos> cachedPositions = Set.of();
    @Nullable
    private Set<BlockPos> cachedRequestedPositions = null;
    private Map<BlockPos, TileMultipart> cachedTiles = Map.of();

    @Nullable
    private TileMultipart previewTile(
            CompoundTag parts,
            BlockPos pos,
            Set<BlockPos> allPositions
    ) {
        boolean samePositions = allPositions == this.cachedRequestedPositions
                || allPositions.equals(this.cachedPositions);

        if (!parts.equals(this.cachedParts) || !samePositions) {
            this.cachedParts = parts.copy();
            this.cachedPositions = Set.copyOf(allPositions);
            this.cachedRequestedPositions = allPositions;
            this.cachedTiles = buildConnectedTiles(parts, this.cachedPositions);
        }

        TileMultipart connected = this.cachedTiles.get(pos);

        return connected != null
                ? connected
                : CBMultipartBlockRenderExtension.createPreviewTile(parts, pos);
    }

    private static Map<BlockPos, TileMultipart> buildConnectedTiles(
            CompoundTag parts,
            Set<BlockPos> positions
    ) {
        Block block = CBMultipartReplacerExtension.multipartBlock();

        if (block == null || Minecraft.getInstance().level == null) {
            return Map.of();
        }

        try {
            PreviewMultipartLevel previewLevel =
                    new PreviewMultipartLevel(block.defaultBlockState());

            Map<BlockPos, TileMultipart> tiles = new LinkedHashMap<>();

            for (BlockPos pos : positions) {
                TileMultipart tile = CBMultipartBlockRenderExtension.createPreviewTile(
                        parts,
                        pos,
                        previewLevel
                );

                if (tile != null) {
                    tiles.put(pos.immutable(), tile);
                    previewLevel.addTile(pos, tile);
                }
            }

            connectTiles(tiles.values());

            return tiles;
        } catch (Throwable ignored) {
            return Map.of();
        }
    }

    private static void connectTiles(Collection<TileMultipart> tiles) {
        for (int pass = 0; pass < MAX_CONNECTION_PASSES; pass++) {
            boolean incomplete = false;

            for (TileMultipart tile : tiles) {
                for (Object object : tile.getPartList()) {
                    if (object instanceof MultiPart part && !updateConnections(part)) {
                        incomplete = true;
                    }
                }
            }

            if (!incomplete) {
                return;
            }
        }
    }

    private static boolean updateConnections(MultiPart part) {
        try {
            part.onAdded();
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static final class PreviewMultipartLevel extends TrackedDummyWorld {

        private final Map<BlockPos, BlockEntity> tiles = new HashMap<>();
        private final BlockState multipartState;

        private PreviewMultipartLevel(BlockState multipartState) {
            this.multipartState = multipartState;

            ((LevelClientSideAccessor) (Object) this).setClientSide(false);
        }

        private void addTile(BlockPos pos, BlockEntity tile) {
            this.tiles.put(pos.immutable(), tile);
        }

        @Nullable
        @Override
        public BlockEntity getBlockEntity(BlockPos pos) {
            return this.tiles.get(pos);
        }

        @Override
        public BlockState getBlockState(BlockPos pos) {
            BlockEntity tile = this.tiles.get(pos);

            return tile != null ? this.multipartState : Blocks.AIR.defaultBlockState();
        }
    }

    @Nullable
    @Override
    public BlockState resolveTargetState(ItemStack target) {
        if (!(target.getItem() instanceof ItemMultipart)) {
            return null;
        }

        Block block = CBMultipartReplacerExtension.multipartBlock();

        return block != null ? block.defaultBlockState() : null;
    }
}
