package net.oktawia.spatialtoolscmp.client.renderer;

import java.util.*;
import java.util.function.Supplier;

import org.jetbrains.annotations.Nullable;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.data.ModelData;

import net.oktawia.spatialtoolscmp.SpatialToolsCMP;
import net.oktawia.spatialtoolscmp.util.StructureToolKeys;
import net.oktawia.spatialtoolscmp.util.TemplateUtil;

public final class PreviewStructure {

    private final BlockPos size;
    private final List<PreviewBlock> blocks;
    private final List<PreviewBlock> surfaceBlocks;

    private @Nullable Map<BlockPos, BlockEntity> cachedBlockEntities;

    private final Map<String, Map<BlockPos, ModelData>> modelDataCache = new HashMap<>();

    private @Nullable String previewGeometryKey;
    private @Nullable PreviewChunkGeometry previewGeometry;

    private int refCount;

    private PreviewStructure(
            BlockPos size,
            List<PreviewBlock> blocks,
            List<PreviewBlock> surfaceBlocks) {
        this.size = size;
        this.blocks = blocks;
        this.surfaceBlocks = surfaceBlocks;
    }

    public BlockPos size() {
        return size;
    }

    public List<PreviewBlock> blocks() {
        return blocks;
    }

    public List<PreviewBlock> surfaceBlocks() {
        return surfaceBlocks;
    }

    public Map<BlockPos, BlockEntity> blockEntities(ClientLevel level) {
        if (cachedBlockEntities == null) {
            cachedBlockEntities = buildBlockEntities(level);
        }

        return cachedBlockEntities;
    }

    public ModelData getOrComputeModelData(
            String sideMapKey,
            BlockPos localPos,
            Supplier<ModelData> compute) {
        return modelDataCache
                .computeIfAbsent(sideMapKey, k -> new HashMap<>())
                .computeIfAbsent(localPos, k -> compute.get());
    }

    public @Nullable PreviewChunkGeometry getPreviewGeometry(String sideMapKey) {
        return sideMapKey.equals(previewGeometryKey) ? previewGeometry : null;
    }

    public void storePreviewGeometry(String sideMapKey, PreviewChunkGeometry geometry) {
        if (refCount == 0) {
            geometry.close();
            return;
        }

        if (previewGeometry != null && previewGeometry != geometry) {
            previewGeometry.close();
        }

        previewGeometryKey = sideMapKey;
        previewGeometry = geometry;
    }

    public PreviewStructure acquire() {
        refCount++;
        return this;
    }

    public void release() {
        if (refCount > 0) {
            refCount--;
        }

        if (refCount == 0) {
            dispose();
        }
    }

    private void dispose() {
        if (previewGeometry != null) {
            previewGeometry.close();
        }

        previewGeometryKey = null;
        previewGeometry = null;

        modelDataCache.clear();
        disposeBlockEntities();
    }

    private void disposeBlockEntities() {
        if (cachedBlockEntities == null) {
            return;
        }

        for (BlockEntity be : cachedBlockEntities.values()) {
            try {
                be.setRemoved();
            } catch (Throwable t) {
                SpatialToolsCMP.getLOGGER().debug(t.getLocalizedMessage());
            }
        }

        cachedBlockEntities = null;
    }

    private Map<BlockPos, BlockEntity> buildBlockEntities(ClientLevel level) {
        Map<BlockPos, BlockEntity> result = new HashMap<>();

        for (PreviewBlock block : blocks) {
            if (block.blockEntityTag() == null) {
                continue;
            }

            BlockEntity be = createBlockEntity(
                    level,
                    block.pos(),
                    block.state(),
                    block.blockEntityTag());

            if (be != null) {
                for (BlockRenderExtension extension : BlockRenderExtensions.all()) {
                    if (!extension.canRender(block.state(), block.blockEntityTag())) {
                        continue;
                    }

                    try {
                        extension.onPreviewBlockEntityCreated(block, be);
                    } catch (Throwable t) {
                        SpatialToolsCMP.getLOGGER().debug(t.getLocalizedMessage());
                    }
                }

                result.put(block.pos(), be);
            }
        }

        return result;
    }

    private static @Nullable BlockEntity createBlockEntity(
            ClientLevel level,
            BlockPos pos,
            BlockState state,
            CompoundTag tag) {
        CompoundTag copy = tag.copy();

        copy.putInt("x", pos.getX());
        copy.putInt("y", pos.getY());
        copy.putInt("z", pos.getZ());

        try {
            BlockEntity be = BlockEntity.loadStatic(pos, state, copy);

            if (be == null) {
                return null;
            }

            be.setLevel(level);

            try {
                be.clearRemoved();
            } catch (Throwable t) {
                SpatialToolsCMP.getLOGGER().debug(t.getLocalizedMessage());
            }

            try {
                be.onLoad();
            } catch (Throwable t) {
                SpatialToolsCMP.getLOGGER().debug(t.getLocalizedMessage());
            }

            return be;
        } catch (Throwable t) {
            SpatialToolsCMP.getLOGGER().debug(t.getLocalizedMessage());
            return null;
        }
    }

    public static PreviewStructure fromTemplateTag(CompoundTag tag) {
        List<TemplateUtil.BlockInfo> parsed = TemplateUtil.parseBlocksFromTag(tag);
        Map<BlockPos, CompoundTag> cloneMetadata = readCloneMetadata(tag);
        List<PreviewBlock> blocks = new ArrayList<>(parsed.size());

        for (TemplateUtil.BlockInfo info : parsed) {
            blocks.add(new PreviewBlock(
                    info.pos(),
                    info.state(),
                    info.blockEntityTag(),
                    cloneMetadata.get(info.pos())));
        }

        BlockPos size = readSize(tag, blocks);
        List<PreviewBlock> surface = computeVisibleBlocks(blocks);

        return new PreviewStructure(
                size,
                List.copyOf(blocks),
                List.copyOf(surface));
    }

    private static Map<BlockPos, CompoundTag> readCloneMetadata(CompoundTag tag) {
        if (tag == null || !tag.contains(StructureToolKeys.CLONE_METADATA_KEY, Tag.TAG_COMPOUND)) {
            return Map.of();
        }

        BlockPos templateOffset = TemplateUtil.getTemplateOffset(tag);
        ListTag entries = tag.getCompound(StructureToolKeys.CLONE_METADATA_KEY)
                .getList(StructureToolKeys.CLONE_METADATA_BLOCKS_KEY, Tag.TAG_COMPOUND);

        Map<BlockPos, CompoundTag> result = new HashMap<>();

        for (int i = 0; i < entries.size(); i++) {
            CompoundTag entry = entries.getCompound(i);
            CompoundTag posTag = entry.getCompound(StructureToolKeys.CLONE_KEY_POS);

            BlockPos pos = new BlockPos(
                    posTag.getInt("x"),
                    posTag.getInt("y"),
                    posTag.getInt("z")).offset(templateOffset);

            result.put(pos, entry);
        }

        return result;
    }

    private static BlockPos readSize(CompoundTag tag, List<PreviewBlock> blocks) {
        if (tag != null && tag.contains("size", Tag.TAG_LIST)) {
            var sizeTag = tag.getList("size", Tag.TAG_INT);

            if (sizeTag.size() >= 3) {
                return new BlockPos(
                        sizeTag.getInt(0),
                        sizeTag.getInt(1),
                        sizeTag.getInt(2));
            }
        }

        if (blocks.isEmpty()) {
            return BlockPos.ZERO;
        }

        int maxX = 0;
        int maxY = 0;
        int maxZ = 0;

        for (PreviewBlock block : blocks) {
            maxX = Math.max(maxX, block.pos().getX());
            maxY = Math.max(maxY, block.pos().getY());
            maxZ = Math.max(maxZ, block.pos().getZ());
        }

        return new BlockPos(
                maxX + 1,
                maxY + 1,
                maxZ + 1);
    }

    private static List<PreviewBlock> computeVisibleBlocks(List<PreviewBlock> blocks) {
        if (blocks.isEmpty()) {
            return List.of();
        }

        Map<BlockPos, PreviewBlock> byPos = new HashMap<>();

        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;

        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;

        for (PreviewBlock block : blocks) {
            BlockPos pos = block.pos();

            byPos.put(pos, block);

            minX = Math.min(minX, pos.getX());
            minY = Math.min(minY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());

            maxX = Math.max(maxX, pos.getX());
            maxY = Math.max(maxY, pos.getY());
            maxZ = Math.max(maxZ, pos.getZ());
        }

        Bounds bounds = new Bounds(
                minX - 1,
                minY - 1,
                minZ - 1,
                maxX + 1,
                maxY + 1,
                maxZ + 1);

        Set<BlockPos> visited = new HashSet<>();
        Queue<BlockPos> queue = new ArrayDeque<>();
        Set<BlockPos> renderPositions = new HashSet<>();

        enqueueBoundary(bounds, visited, queue);

        while (!queue.isEmpty()) {
            BlockPos pos = queue.poll();

            for (Direction direction : Direction.values()) {
                BlockPos next = pos.relative(direction);

                if (!bounds.contains(next) || !visited.add(next)) {
                    continue;
                }

                PreviewBlock nextBlock = byPos.get(next);

                if (nextBlock == null) {
                    queue.add(next);
                    continue;
                }

                renderPositions.add(next);

                if (!hidesBlocksBehind(nextBlock.state())) {
                    queue.add(next);
                }
            }
        }

        List<PreviewBlock> result = new ArrayList<>(renderPositions.size());

        for (PreviewBlock block : blocks) {
            if (renderPositions.contains(block.pos())) {
                result.add(block);
            }
        }

        return result;
    }

    private static boolean hidesBlocksBehind(BlockState state) {
        return state.canOcclude()
                && Block.isShapeFullBlock(state.getOcclusionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO));
    }

    private static void enqueueBoundary(
            Bounds bounds,
            Set<BlockPos> visited,
            Queue<BlockPos> queue) {
        for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
            for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
                enqueueBoundaryPos(new BlockPos(x, y, bounds.minZ()), visited, queue);
                enqueueBoundaryPos(new BlockPos(x, y, bounds.maxZ()), visited, queue);
            }
        }

        for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
            for (int z = bounds.minZ(); z <= bounds.maxZ(); z++) {
                enqueueBoundaryPos(new BlockPos(x, bounds.minY(), z), visited, queue);
                enqueueBoundaryPos(new BlockPos(x, bounds.maxY(), z), visited, queue);
            }
        }

        for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
            for (int z = bounds.minZ(); z <= bounds.maxZ(); z++) {
                enqueueBoundaryPos(new BlockPos(bounds.minX(), y, z), visited, queue);
                enqueueBoundaryPos(new BlockPos(bounds.maxX(), y, z), visited, queue);
            }
        }
    }

    private static void enqueueBoundaryPos(
            BlockPos pos,
            Set<BlockPos> visited,
            Queue<BlockPos> queue) {
        if (visited.add(pos)) {
            queue.add(pos);
        }
    }

    private record Bounds(
            int minX,
            int minY,
            int minZ,
            int maxX,
            int maxY,
            int maxZ) {
        private boolean contains(BlockPos pos) {
            return pos.getX() >= minX
                    && pos.getX() <= maxX
                    && pos.getY() >= minY
                    && pos.getY() <= maxY
                    && pos.getZ() >= minZ
                    && pos.getZ() <= maxZ;
        }
    }
}
