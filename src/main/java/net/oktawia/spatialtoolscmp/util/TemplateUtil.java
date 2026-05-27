package net.oktawia.spatialtoolscmp.util;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.*;
import java.util.function.UnaryOperator;

public final class TemplateUtil {

    public record BlockInfo(BlockPos pos, BlockState state, @Nullable CompoundTag blockEntityTag) {}

    private static final String AE2_CABLE_BUS_ID = "ae2:cable_bus";

    private static final String CB_MULTIPART_BE_ID = "cb_multipart:saved_multipart";
    private static final String CB_MULTIPART_META_KEY = "CBMultipart";
    private static final String CB_MULTIPART_META_PARTS_KEY = "Parts";

    private static final String NBT_CB_PARTS = "parts";
    private static final String NBT_CONN_MAP = "connMap";
    private static final String NBT_SIDE = "side";

    private static final String PROJECTRED_TRANSMISSION_ID_PREFIX = "projectred_transmission:";

    private static final String FASTSTONE_ID_PREFIX = "faststonelogic:";
    private static final String FASTSTONE_META_KEY = "Faststone";
    private static final String FASTSTONE_FULL_BE_TAG_KEY = "FullBlockEntityTag";

    private static final String KEY_NORTH = "north";
    private static final String KEY_SOUTH = "south";
    private static final String KEY_EAST = "east";
    private static final String KEY_WEST = "west";
    private static final String KEY_UP = "up";
    private static final String KEY_DOWN = "down";
    private static final String KEY_CABLE = "cable";
    private static final String KEY_COVER = "cover";

    private static final String KEY_PARTS = "Parts";
    private static final String KEY_REDSTONE_OUTPUTS = "RedstoneOutputs";
    private static final String KEY_PORT_STATES = "PortStates";
    private static final String KEY_PORT_COLORS = "PortColors";
    private static final String KEY_INPUTS = "Inputs";
    private static final String KEY_OUTPUTS = "Outputs";

    private static final String KEY_UPWARDS_FACING = "upwards_facing";

    private static final Set<String> PRESERVE_ON_ROTATE_AND_HORIZONTAL_FLIP_PROPERTIES = Set.of(
            KEY_UPWARDS_FACING
    );

    public static final String TEMPLATE_OFFSET_X_KEY = "crazy_template_offset_x";
    public static final String TEMPLATE_OFFSET_Y_KEY = "crazy_template_offset_y";
    public static final String TEMPLATE_OFFSET_Z_KEY = "crazy_template_offset_z";

    public static final String ENERGY_ORIGIN_KEY = "energyOrigin";

    /*
     * Kopia CodeChickenLib Rotation.sideRotMap / rotSideMap.
     * Dzięki temu TemplateUtil nie musi importować CCL.
     */
    private static final int[] CCL_SIDE_ROT_MAP = {
            3, 4, 2, 5,
            3, 5, 2, 4,
            1, 5, 0, 4,
            1, 4, 0, 5,
            1, 2, 0, 3,
            1, 3, 0, 2
    };

    private static final int[] CCL_ROT_SIDE_MAP = {
            -1, -1,  2,  0,  1,  3,
            -1, -1,  2,  0,  3,  1,
            2,  0, -1, -1,  3,  1,
            2,  0, -1, -1,  1,  3,
            2,  0,  1,  3, -1, -1,
            2,  0,  3,  1, -1, -1
    };

    private TemplateUtil() {
    }

    public static byte[] compressNbt(CompoundTag tag) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        NbtIo.writeCompressed(tag, output);
        return output.toByteArray();
    }

    public static CompoundTag decompressNbt(byte[] bytes) throws IOException {
        return NbtIo.readCompressed(new ByteArrayInputStream(bytes));
    }

    public static String toBase64(byte[] bytes) {
        return java.util.Base64.getEncoder().encodeToString(bytes);
    }

    public static byte[] fromBase64(String value) {
        return java.util.Base64.getDecoder().decode(value);
    }

    public static BlockPos getTemplateOffset(@Nullable CompoundTag tag) {
        if (tag == null) {
            return BlockPos.ZERO;
        }

        return new BlockPos(
                clampOffset(tag.getInt(TEMPLATE_OFFSET_X_KEY)),
                clampOffset(tag.getInt(TEMPLATE_OFFSET_Y_KEY)),
                clampOffset(tag.getInt(TEMPLATE_OFFSET_Z_KEY))
        );
    }

    public static void setTemplateOffset(CompoundTag tag, BlockPos offset) {
        if (tag == null) {
            return;
        }

        tag.putInt(TEMPLATE_OFFSET_X_KEY, clampOffset(offset.getX()));
        tag.putInt(TEMPLATE_OFFSET_Y_KEY, clampOffset(offset.getY()));
        tag.putInt(TEMPLATE_OFFSET_Z_KEY, clampOffset(offset.getZ()));
    }

    public static CompoundTag applyOffsetToTag(CompoundTag tag, int dx, int dy, int dz) {
        CompoundTag result = tag.copy();
        BlockPos current = getTemplateOffset(result);

        setTemplateOffset(result, new BlockPos(
                current.getX() + dx,
                current.getY() + dy,
                current.getZ() + dz
        ));

        return result;
    }

    private static int clampOffset(int value) {
        return Mth.clamp(value, -99, 99);
    }

    public static BlockPos getEnergyOrigin(@Nullable CompoundTag tag) {
        if (tag == null || !tag.contains(ENERGY_ORIGIN_KEY, Tag.TAG_COMPOUND)) {
            return BlockPos.ZERO;
        }

        CompoundTag origin = tag.getCompound(ENERGY_ORIGIN_KEY);
        return new BlockPos(origin.getInt("x"), origin.getInt("y"), origin.getInt("z"));
    }

    public static void setEnergyOrigin(CompoundTag tag, BlockPos pos) {
        CompoundTag origin = new CompoundTag();
        origin.putInt("x", pos.getX());
        origin.putInt("y", pos.getY());
        origin.putInt("z", pos.getZ());
        tag.put(ENERGY_ORIGIN_KEY, origin);
    }

    public static void copyPreviewTransformState(CompoundTag source, CompoundTag target) {
        setTemplateOffset(target, getTemplateOffset(source));
        setEnergyOrigin(target, getEnergyOrigin(source));
    }

    public static List<BlockInfo> parseBlocksFromTag(CompoundTag tag) {
        return parseBlocksFromTag(tag, true);
    }

    public static List<BlockInfo> parseRawBlocksFromTag(CompoundTag tag) {
        return parseBlocksFromTag(tag, false);
    }

    private static List<BlockInfo> parseBlocksFromTag(CompoundTag tag, boolean applyTemplateOffset) {
        List<BlockInfo> out = new ArrayList<>();
        if (tag == null) {
            return out;
        }

        BlockPos templateOffset = applyTemplateOffset ? getTemplateOffset(tag) : BlockPos.ZERO;

        ListTag paletteTag = tag.getList("palette", Tag.TAG_COMPOUND);
        List<BlockState> palette = new ArrayList<>(paletteTag.size());
        for (int i = 0; i < paletteTag.size(); i++) {
            palette.add(parseBlockStateFromTag(paletteTag.getCompound(i)));
        }

        ListTag blocksTag = tag.getList("blocks", Tag.TAG_COMPOUND);
        for (int i = 0; i < blocksTag.size(); i++) {
            CompoundTag blockTag = blocksTag.getCompound(i);
            int stateIdx = blockTag.getInt("state");
            if (stateIdx < 0 || stateIdx >= palette.size()) {
                continue;
            }

            BlockState state = palette.get(stateIdx);
            if (state == null) {
                continue;
            }

            ListTag posTag = blockTag.getList("pos", Tag.TAG_INT);
            if (posTag.size() < 3) {
                continue;
            }

            BlockPos pos = new BlockPos(
                    posTag.getInt(0),
                    posTag.getInt(1),
                    posTag.getInt(2)
            ).offset(templateOffset);

            CompoundTag blockEntityTag = blockTag.contains("nbt", Tag.TAG_COMPOUND)
                    ? blockTag.getCompound("nbt").copy()
                    : null;

            out.add(new BlockInfo(pos, state, blockEntityTag));
        }

        return out;
    }

    public static CompoundTag stripAirFromTag(CompoundTag tag) {
        if (tag == null) {
            return new CompoundTag();
        }

        CompoundTag result = tag.copy();

        ListTag paletteTag = result.getList("palette", Tag.TAG_COMPOUND);
        ListTag blocksTag = result.getList("blocks", Tag.TAG_COMPOUND);

        if (paletteTag.isEmpty() || blocksTag.isEmpty()) {
            return result;
        }

        BlockState[] palette = new BlockState[paletteTag.size()];
        for (int i = 0; i < paletteTag.size(); i++) {
            palette[i] = parseBlockStateFromTag(paletteTag.getCompound(i));
        }

        ListTag filteredBlocks = new ListTag();

        for (int i = 0; i < blocksTag.size(); i++) {
            CompoundTag blockTag = blocksTag.getCompound(i);
            int stateIdx = blockTag.getInt("state");

            if (stateIdx < 0 || stateIdx >= palette.length) {
                continue;
            }

            BlockState state = palette[stateIdx];
            if (state == null || state.isAir()) {
                continue;
            }

            filteredBlocks.add(blockTag.copy());
        }

        result.put("blocks", filteredBlocks);
        return result;
    }

    public static CompoundTag applyFlipHToTag(CompoundTag tag, Direction sourceFacing) {
        Direction.Axis mirrorAxis = horizontalMirrorAxisForSourceFacing(sourceFacing);

        CableBusTransform cableBusTransform = sourceFacing.getAxis() == Direction.Axis.Z
                ? CableBusTransform.FLIP_H_AXIS_Z
                : CableBusTransform.FLIP_H_AXIS_X;

        return applyTransform(
                tag,
                horizontalFlipPositionTransform(mirrorAxis),
                state -> flipHorizontalState(state, sourceFacing),
                cableBusTransform
        );
    }

    public static CompoundTag applyFlipEastWestToTag(CompoundTag tag) {
        return applyFlipHToTag(tag, Direction.NORTH);
    }

    public static CompoundTag applyFlipNorthSouthToTag(CompoundTag tag) {
        return applyFlipHToTag(tag, Direction.EAST);
    }

    public static CompoundTag applyFlipEastWestAroundOriginToTag(CompoundTag tag) {
        return applyFlipHAroundOriginToTag(tag, Direction.NORTH);
    }

    public static CompoundTag applyFlipNorthSouthAroundOriginToTag(CompoundTag tag) {
        return applyFlipHAroundOriginToTag(tag, Direction.EAST);
    }

    private static Direction.Axis horizontalMirrorAxisForSourceFacing(Direction sourceFacing) {
        return sourceFacing.getAxis() == Direction.Axis.X
                ? Direction.Axis.Z
                : Direction.Axis.X;
    }

    private static Transform horizontalFlipPositionTransform(Direction.Axis mirrorAxis) {
        if (mirrorAxis == Direction.Axis.Z) {
            return (x, y, z, minX, maxX, minY, maxY, minZ, maxZ) ->
                    new int[]{x, y, minZ + maxZ - z};
        }

        return (x, y, z, minX, maxX, minY, maxY, minZ, maxZ) ->
                new int[]{minX + maxX - x, y, z};
    }

    public static CompoundTag applyFlipVToTag(CompoundTag tag) {
        return applyTransform(
                tag,
                (x, y, z, minX, maxX, minY, maxY, minZ, maxZ) ->
                        new int[]{x, minY + maxY - y, z},
                TemplateUtil::flipVerticalState,
                CableBusTransform.FLIP_V
        );
    }

    public static CompoundTag applyRotateCWToTag(CompoundTag tag, int times) {
        int normalizedTurns = ((times % 4) + 4) % 4;
        if (normalizedTurns == 0) {
            return tag;
        }

        Rotation rotation = switch (normalizedTurns) {
            case 1 -> Rotation.CLOCKWISE_90;
            case 2 -> Rotation.CLOCKWISE_180;
            case 3 -> Rotation.COUNTERCLOCKWISE_90;
            default -> Rotation.NONE;
        };

        CableBusTransform cableBusTransform = switch (normalizedTurns) {
            case 1 -> CableBusTransform.ROTATE_CW;
            case 2 -> CableBusTransform.ROTATE_180;
            case 3 -> CableBusTransform.ROTATE_CCW;
            default -> CableBusTransform.NONE;
        };

        return applyTransform(
                tag,
                (x, y, z, minX, maxX, minY, maxY, minZ, maxZ) -> switch (normalizedTurns) {
                    case 1 -> new int[]{
                            minX + (maxZ - z),
                            y,
                            minZ + (x - minX)
                    };
                    case 2 -> new int[]{
                            minX + (maxX - x),
                            y,
                            minZ + (maxZ - z)
                    };
                    case 3 -> new int[]{
                            minX + (z - minZ),
                            y,
                            minZ + (maxX - x)
                    };
                    default -> new int[]{x, y, z};
                },
                state -> rotateState(state, rotation),
                cableBusTransform
        );
    }

    public static CompoundTag applyFlipHAroundOriginToTag(CompoundTag tag, Direction sourceFacing) {
        CompoundTag transformed = applyFlipHToTag(tag, sourceFacing);
        setTemplateOffset(transformed, flipHorizontalOffset(getTemplateOffset(tag), sourceFacing));
        return transformed;
    }

    public static CompoundTag applyFlipVAroundOriginToTag(CompoundTag tag) {
        CompoundTag transformed = applyFlipVToTag(tag);
        setTemplateOffset(transformed, flipVerticalOffset(getTemplateOffset(tag)));
        return transformed;
    }

    public static CompoundTag applyRotateCWAroundOriginToTag(CompoundTag tag, int times) {
        int normalizedTurns = ((times % 4) + 4) % 4;
        if (normalizedTurns == 0) {
            return tag;
        }

        CompoundTag transformed = applyRotateCWToTag(tag, times);
        setTemplateOffset(transformed, rotateOffsetCW(getTemplateOffset(tag), normalizedTurns));
        return transformed;
    }

    private static BlockPos rotateOffsetCW(BlockPos offset, int normalizedTurns) {
        return switch (normalizedTurns) {
            case 1 -> new BlockPos(
                    clampOffset(-offset.getZ()),
                    clampOffset(offset.getY()),
                    clampOffset(offset.getX())
            );
            case 2 -> new BlockPos(
                    clampOffset(-offset.getX()),
                    clampOffset(offset.getY()),
                    clampOffset(-offset.getZ())
            );
            case 3 -> new BlockPos(
                    clampOffset(offset.getZ()),
                    clampOffset(offset.getY()),
                    clampOffset(-offset.getX())
            );
            default -> offset;
        };
    }

    private static BlockPos flipHorizontalOffset(BlockPos offset, Direction sourceFacing) {
        Direction.Axis mirrorAxis = horizontalMirrorAxisForSourceFacing(sourceFacing);

        if (mirrorAxis == Direction.Axis.X) {
            return new BlockPos(
                    clampOffset(-offset.getX()),
                    clampOffset(offset.getY()),
                    clampOffset(offset.getZ())
            );
        }

        return new BlockPos(
                clampOffset(offset.getX()),
                clampOffset(offset.getY()),
                clampOffset(-offset.getZ())
        );
    }

    private static BlockPos flipVerticalOffset(BlockPos offset) {
        return new BlockPos(
                clampOffset(offset.getX()),
                clampOffset(-offset.getY()),
                clampOffset(offset.getZ())
        );
    }

    @FunctionalInterface
    private interface Transform {
        int[] apply(int x, int y, int z, int minX, int maxX, int minY, int maxY, int minZ, int maxZ);
    }

    private enum CableBusTransform {
        NONE,
        ROTATE_CW,
        ROTATE_180,
        ROTATE_CCW,
        FLIP_H_AXIS_Z,
        FLIP_H_AXIS_X,
        FLIP_V
    }

    private enum FramedPropertyTransform {
        FLIP_V,
        ROTATE_CW,
        ROTATE_180,
        ROTATE_CCW
    }

    private enum FramedTypeTransform {
        FLIP_H,
        FLIP_V
    }

    private static CompoundTag applyTransform(
            CompoundTag tag,
            Transform positionTransform,
            UnaryOperator<BlockState> stateTransform,
            CableBusTransform cableBusTransform
    ) {
        return applyTransformInternal(tag, positionTransform, stateTransform, cableBusTransform);
    }

    private static CompoundTag applyTransformInternal(
            CompoundTag tag,
            Transform positionTransform,
            UnaryOperator<BlockState> stateTransform,
            CableBusTransform cableBusTransform
    ) {
        ListTag blocksTag = tag.getList("blocks", Tag.TAG_COMPOUND);
        ListTag paletteTag = tag.getList("palette", Tag.TAG_COMPOUND);

        if (blocksTag.isEmpty() || paletteTag.isEmpty()) {
            return tag.copy();
        }

        BlockState[] oldPalette = new BlockState[paletteTag.size()];
        for (int i = 0; i < paletteTag.size(); i++) {
            oldPalette[i] = parseBlockStateFromTag(paletteTag.getCompound(i));
        }

        List<BlockState> newPaletteStates = new ArrayList<>();
        List<CompoundTag> newPaletteFallbacks = new ArrayList<>();
        Map<BlockState, Integer> newPaletteIndex = new LinkedHashMap<>();
        int[] oldToNew = new int[oldPalette.length];

        for (int i = 0; i < oldPalette.length; i++) {
            BlockState oldState = oldPalette[i];
            if (oldState == null) {
                oldToNew[i] = newPaletteStates.size();
                newPaletteStates.add(null);
                newPaletteFallbacks.add(paletteTag.getCompound(i).copy());
                continue;
            }

            BlockState transformedState = stateTransform.apply(oldState);
            if (transformedState == null) {
                transformedState = oldState;
            }

            BlockState key = transformedState;
            oldToNew[i] = newPaletteIndex.computeIfAbsent(key, ignored -> {
                int index = newPaletteStates.size();
                newPaletteStates.add(key);
                newPaletteFallbacks.add(null);
                return index;
            });
        }

        int blockCount = blocksTag.size();
        int[] tx = new int[blockCount];
        int[] ty = new int[blockCount];
        int[] tz = new int[blockCount];
        int[] mappedStateIndex = new int[blockCount];
        CompoundTag[] copiedEntries = new CompoundTag[blockCount];

        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;

        for (int i = 0; i < blockCount; i++) {
            CompoundTag blockTag = blocksTag.getCompound(i);
            ListTag posTag = blockTag.getList("pos", Tag.TAG_INT);
            if (posTag.size() < 3) {
                continue;
            }

            int x = posTag.getInt(0);
            int y = posTag.getInt(1);
            int z = posTag.getInt(2);

            minX = Math.min(minX, x);
            minY = Math.min(minY, y);
            minZ = Math.min(minZ, z);
            maxX = Math.max(maxX, x);
            maxY = Math.max(maxY, y);
            maxZ = Math.max(maxZ, z);
        }

        int newMinX = Integer.MAX_VALUE;
        int newMinY = Integer.MAX_VALUE;
        int newMinZ = Integer.MAX_VALUE;

        for (int i = 0; i < blockCount; i++) {
            CompoundTag blockTag = blocksTag.getCompound(i).copy();
            copiedEntries[i] = blockTag;

            ListTag posTag = blockTag.getList("pos", Tag.TAG_INT);
            if (posTag.size() < 3) {
                tx[i] = 0;
                ty[i] = 0;
                tz[i] = 0;
            } else {
                int[] transformed = positionTransform.apply(
                        posTag.getInt(0),
                        posTag.getInt(1),
                        posTag.getInt(2),
                        minX,
                        maxX,
                        minY,
                        maxY,
                        minZ,
                        maxZ
                );

                tx[i] = transformed[0];
                ty[i] = transformed[1];
                tz[i] = transformed[2];

                newMinX = Math.min(newMinX, tx[i]);
                newMinY = Math.min(newMinY, ty[i]);
                newMinZ = Math.min(newMinZ, tz[i]);
            }

            int oldStateIndex = blockTag.getInt("state");
            mappedStateIndex[i] = oldStateIndex >= 0 && oldStateIndex < oldToNew.length
                    ? oldToNew[oldStateIndex]
                    : 0;

            if (blockTag.contains("nbt", Tag.TAG_COMPOUND)) {
                CompoundTag transformedBlockEntityTag = transformBlockEntityTag(
                        blockTag.getCompound("nbt"),
                        cableBusTransform
                );
                blockTag.put("nbt", transformedBlockEntityTag);
            }
        }

        int newMaxX = Integer.MIN_VALUE;
        int newMaxY = Integer.MIN_VALUE;
        int newMaxZ = Integer.MIN_VALUE;

        ListTag newBlocksTag = new ListTag();
        for (int i = 0; i < blockCount; i++) {
            CompoundTag blockTag = copiedEntries[i];

            int normalizedX = tx[i] - newMinX;
            int normalizedY = ty[i] - newMinY;
            int normalizedZ = tz[i] - newMinZ;

            ListTag newPosTag = new ListTag();
            newPosTag.add(IntTag.valueOf(normalizedX));
            newPosTag.add(IntTag.valueOf(normalizedY));
            newPosTag.add(IntTag.valueOf(normalizedZ));

            blockTag.put("pos", newPosTag);
            blockTag.putInt("state", mappedStateIndex[i]);

            newMaxX = Math.max(newMaxX, normalizedX);
            newMaxY = Math.max(newMaxY, normalizedY);
            newMaxZ = Math.max(newMaxZ, normalizedZ);

            newBlocksTag.add(blockTag);
        }

        ListTag newPaletteTag = new ListTag();
        for (int i = 0; i < newPaletteStates.size(); i++) {
            BlockState state = newPaletteStates.get(i);
            CompoundTag fallback = newPaletteFallbacks.get(i);
            newPaletteTag.add(state != null ? blockStateToTag(state) : fallback.copy());
        }

        CompoundTag result = tag.copy();
        result.put("blocks", newBlocksTag);
        result.put("palette", newPaletteTag);

        ListTag sizeTag = new ListTag();
        sizeTag.add(IntTag.valueOf(newMaxX + 1));
        sizeTag.add(IntTag.valueOf(newMaxY + 1));
        sizeTag.add(IntTag.valueOf(newMaxZ + 1));
        result.put("size", sizeTag);

        BlockPos oldOrigin = getEnergyOrigin(tag);
        BlockPos oldOffset = getTemplateOffset(tag);

        int[] transformedOrigin = positionTransform.apply(
                oldOrigin.getX(),
                oldOrigin.getY(),
                oldOrigin.getZ(),
                minX,
                maxX,
                minY,
                maxY,
                minZ,
                maxZ
        );

        BlockPos newOrigin = new BlockPos(
                transformedOrigin[0] - newMinX,
                transformedOrigin[1] - newMinY,
                transformedOrigin[2] - newMinZ
        );

        setEnergyOrigin(result, newOrigin);
        setTemplateOffset(result, new BlockPos(
                clampOffset(oldOffset.getX() + newOrigin.getX() - oldOrigin.getX()),
                clampOffset(oldOffset.getY() + newOrigin.getY() - oldOrigin.getY()),
                clampOffset(oldOffset.getZ() + newOrigin.getZ() - oldOrigin.getZ())
        ));

        transformCloneMetadata(
                result,
                positionTransform,
                minX, maxX,
                minY, maxY,
                minZ, maxZ,
                newMinX, newMinY, newMinZ,
                cableBusTransform
        );

        return result;
    }

    private static void transformCloneMetadata(
            CompoundTag tag,
            Transform positionTransform,
            int minX,
            int maxX,
            int minY,
            int maxY,
            int minZ,
            int maxZ,
            int newMinX,
            int newMinY,
            int newMinZ,
            CableBusTransform cableBusTransform
    ) {
        if (!tag.contains(StructureToolKeys.CLONE_METADATA_KEY, Tag.TAG_COMPOUND)) {
            return;
        }

        CompoundTag cloneMetadata = tag.getCompound(StructureToolKeys.CLONE_METADATA_KEY).copy();
        if (!cloneMetadata.contains(StructureToolKeys.CLONE_METADATA_BLOCKS_KEY, Tag.TAG_LIST)) {
            tag.put(StructureToolKeys.CLONE_METADATA_KEY, cloneMetadata);
            return;
        }

        ListTag oldBlocks = cloneMetadata.getList(StructureToolKeys.CLONE_METADATA_BLOCKS_KEY, Tag.TAG_COMPOUND);
        ListTag newBlocks = new ListTag();

        for (int i = 0; i < oldBlocks.size(); i++) {
            CompoundTag blockEntry = oldBlocks.getCompound(i).copy();

            if (blockEntry.contains(StructureToolKeys.CLONE_KEY_POS, Tag.TAG_COMPOUND)) {
                CompoundTag posTag = blockEntry.getCompound(StructureToolKeys.CLONE_KEY_POS);
                int oldX = posTag.getInt("x");
                int oldY = posTag.getInt("y");
                int oldZ = posTag.getInt("z");

                int[] transformed = positionTransform.apply(
                        oldX,
                        oldY,
                        oldZ,
                        minX,
                        maxX,
                        minY,
                        maxY,
                        minZ,
                        maxZ
                );

                CompoundTag newPos = new CompoundTag();
                newPos.putInt("x", transformed[0] - newMinX);
                newPos.putInt("y", transformed[1] - newMinY);
                newPos.putInt("z", transformed[2] - newMinZ);
                blockEntry.put(StructureToolKeys.CLONE_KEY_POS, newPos);
            }

            if (blockEntry.contains(StructureToolKeys.CLONE_KEY_PARTS, Tag.TAG_COMPOUND)) {
                blockEntry.put(
                        StructureToolKeys.CLONE_KEY_PARTS,
                        transformDirectionalMetadataTag(
                                blockEntry.getCompound(StructureToolKeys.CLONE_KEY_PARTS),
                                cableBusTransform
                        )
                );
            }

            if (blockEntry.contains(CB_MULTIPART_META_KEY, Tag.TAG_COMPOUND)) {
                blockEntry.put(
                        CB_MULTIPART_META_KEY,
                        transformCbMultipartCloneMetadata(
                                blockEntry.getCompound(CB_MULTIPART_META_KEY),
                                cableBusTransform
                        )
                );
            }

            if (blockEntry.contains(FASTSTONE_META_KEY, Tag.TAG_COMPOUND)) {
                blockEntry.put(
                        FASTSTONE_META_KEY,
                        transformFaststoneCloneMetadata(
                                blockEntry.getCompound(FASTSTONE_META_KEY),
                                cableBusTransform
                        )
                );
            }

            if (blockEntry.contains(StructureToolKeys.CLONE_KEY_GREG, Tag.TAG_COMPOUND)) {
                CompoundTag gregTag = blockEntry.getCompound(StructureToolKeys.CLONE_KEY_GREG).copy();

                if (gregTag.contains(KEY_COVER, Tag.TAG_COMPOUND)) {
                    gregTag.put(
                            KEY_COVER,
                            transformGregPipeCoverTag(gregTag.getCompound(KEY_COVER), cableBusTransform)
                    );
                }

                if (gregTag.contains(StructureToolKeys.CLONE_KEY_GREG_PIPE, Tag.TAG_COMPOUND)) {
                    CompoundTag pipeTag = gregTag.getCompound(StructureToolKeys.CLONE_KEY_GREG_PIPE).copy();

                    if (pipeTag.contains("connections", Tag.TAG_INT)) {
                        pipeTag.putInt(
                                "connections",
                                remapGregConnectionMask(pipeTag.getInt("connections"), cableBusTransform)
                        );
                    }

                    if (pipeTag.contains("blockedConnections", Tag.TAG_INT)) {
                        pipeTag.putInt(
                                "blockedConnections",
                                remapGregConnectionMask(pipeTag.getInt("blockedConnections"), cableBusTransform)
                        );
                    }

                    gregTag.put(StructureToolKeys.CLONE_KEY_GREG_PIPE, pipeTag);
                }

                blockEntry.put(StructureToolKeys.CLONE_KEY_GREG, gregTag);
            }

            newBlocks.add(blockEntry);
        }

        cloneMetadata.put(StructureToolKeys.CLONE_METADATA_BLOCKS_KEY, newBlocks);
        tag.put(StructureToolKeys.CLONE_METADATA_KEY, cloneMetadata);
    }

    private static CompoundTag transformDirectionalMetadataTag(CompoundTag tag, CableBusTransform transform) {
        CompoundTag result = new CompoundTag();

        for (String key : tag.getAllKeys()) {
            Tag value = tag.get(key);
            if (value == null) {
                continue;
            }

            Direction side = directionFromKey(key);
            if (side == null) {
                result.put(key, value.copy());
                continue;
            }

            Direction mappedSide = mapCableBusSide(side, transform);
            result.put(directionKey(mappedSide), value.copy());
        }

        return result;
    }

    private static CompoundTag transformBlockEntityTag(CompoundTag tag, CableBusTransform transform) {
        if (transform == CableBusTransform.NONE) {
            return tag.copy();
        }

        String id = tag.getString("id");

        if (AE2_CABLE_BUS_ID.equals(id)) {
            return transformCableBusTag(tag, transform);
        }

        if (isCbMultipartBlockEntityTag(tag)) {
            return transformCbMultipartBlockEntityTag(tag, transform);
        }

        if (isFaststoneBlockEntityTag(tag)) {
            return transformFaststoneBlockEntityTag(tag, transform);
        }

        if (!id.isBlank() && id.startsWith(StructureToolKeys.GTCEU_ID_PREFIX)) {
            return transformGregBlockEntityTag(tag, transform);
        }

        if (isFramedCollapsibleBlockEntityTag(tag)) {
            return transformCollapsibleBlockEntityTag(tag, transform);
        }

        return tag.copy();
    }

    private static boolean isFramedCollapsibleBlockEntityTag(CompoundTag tag) {
        String id = tag.getString("id");
        return id.startsWith("framedblocks:")
                && tag.contains("face")
                && tag.contains("offsets", Tag.TAG_INT);
    }

    private static CompoundTag transformCollapsibleBlockEntityTag(CompoundTag tag, CableBusTransform transform) {
        CompoundTag result = tag.copy();

        Tag faceTag = tag.get("face");
        if (!(faceTag instanceof NumericTag numericFaceTag)) {
            return result;
        }

        int faceOrdinal = numericFaceTag.getAsInt();
        if (faceOrdinal < 0 || faceOrdinal >= Direction.values().length) {
            return result;
        }

        Direction oldFace = Direction.values()[faceOrdinal];
        Direction newFace = mapCableBusSide(oldFace, transform);

        putDirectionOrdinalPreservingType(result, "face", faceTag, newFace.ordinal());

        int[] perm = collapsibleVertexPermutation(oldFace, transform);
        byte[] oldVertexOffsets = unpackCollapsibleOffsets(tag.getInt("offsets"));
        byte[] newVertexOffsets = new byte[4];

        for (int i = 0; i < 4; i++) {
            newVertexOffsets[perm[i]] = oldVertexOffsets[i];
        }

        result.putInt("offsets", packCollapsibleOffsets(newVertexOffsets));
        return result;
    }

    private static int[] collapsibleVertexPermutation(Direction oldFace, CableBusTransform transform) {
        return switch (transform) {
            case FLIP_V -> new int[]{1, 0, 3, 2};
            case FLIP_H_AXIS_Z -> new int[]{3, 2, 1, 0};
            case FLIP_H_AXIS_X -> oldFace.getAxis() == Direction.Axis.Y
                    ? new int[]{1, 0, 3, 2}
                    : new int[]{3, 2, 1, 0};
            default -> new int[]{0, 1, 2, 3};
        };
    }

    private static int packCollapsibleOffsets(byte[] offsets) {
        int result = 0;
        for (int i = 0; i < 4; i++) {
            result |= (offsets[i] & 0x1F) << (i * 5);
        }
        return result;
    }

    private static byte[] unpackCollapsibleOffsets(int packed) {
        byte[] offsets = new byte[4];
        for (int i = 0; i < 4; i++) {
            offsets[i] = (byte) ((packed >> (i * 5)) & 0x1F);
        }
        return offsets;
    }

    private static boolean isCbMultipartBlockEntityTag(CompoundTag tag) {
        String id = tag.getString("id");

        return CB_MULTIPART_BE_ID.equals(id)
                || (id.isBlank() && tag.contains(NBT_CB_PARTS, Tag.TAG_LIST));
    }

    private static CompoundTag transformCbMultipartBlockEntityTag(
            CompoundTag tag,
            CableBusTransform transform
    ) {
        CompoundTag result = tag.copy();

        if (!result.contains(NBT_CB_PARTS, Tag.TAG_LIST)) {
            return result;
        }

        result.put(
                NBT_CB_PARTS,
                transformCbMultipartParts(
                        result.getList(NBT_CB_PARTS, Tag.TAG_COMPOUND),
                        transform
                )
        );

        return result;
    }

    private static CompoundTag transformCbMultipartCloneMetadata(
            CompoundTag metadata,
            CableBusTransform transform
    ) {
        CompoundTag result = metadata.copy();

        if (!result.contains(CB_MULTIPART_META_PARTS_KEY, Tag.TAG_LIST)) {
            return result;
        }

        result.put(
                CB_MULTIPART_META_PARTS_KEY,
                transformCbMultipartParts(
                        result.getList(CB_MULTIPART_META_PARTS_KEY, Tag.TAG_COMPOUND),
                        transform
                )
        );

        return result;
    }

    private static ListTag transformCbMultipartParts(
            ListTag parts,
            CableBusTransform transform
    ) {
        ListTag out = new ListTag();

        for (int i = 0; i < parts.size(); i++) {
            out.add(transformCbMultipartPartTag(parts.getCompound(i), transform));
        }

        return out;
    }

    private static CompoundTag transformCbMultipartPartTag(
            CompoundTag partTag,
            CableBusTransform transform
    ) {
        CompoundTag result = partTag.copy();

        if (transform == CableBusTransform.NONE) {
            return result;
        }

        int oldSideOrdinal = readDirectionOrdinal(partTag.get(NBT_SIDE));

        if (oldSideOrdinal >= 0) {
            Direction oldSide = Direction.values()[oldSideOrdinal];
            Direction newSide = mapCableBusSide(oldSide, transform);

            putDirectionOrdinalPreservingType(
                    result,
                    NBT_SIDE,
                    partTag.get(NBT_SIDE),
                    newSide.ordinal()
            );
        }

        if (isProjectRedTransmissionPart(partTag)
                && partTag.contains(NBT_CONN_MAP, Tag.TAG_ANY_NUMERIC)) {
            int oldConnMap = partTag.getInt(NBT_CONN_MAP);
            int newConnMap;

            if (oldSideOrdinal >= 0) {
                Direction oldSide = Direction.values()[oldSideOrdinal];
                newConnMap = remapProjectRedFaceConnMap(
                        oldConnMap,
                        oldSide,
                        transform
                );
            } else {
                newConnMap = remapProjectRedCenterConnMap(
                        oldConnMap,
                        transform
                );
            }

            result.putInt(NBT_CONN_MAP, newConnMap);
        }

        return result;
    }

    private static boolean isProjectRedTransmissionPart(CompoundTag partTag) {
        if (!partTag.contains("id", Tag.TAG_STRING)) {
            return false;
        }

        return partTag.getString("id").startsWith(PROJECTRED_TRANSMISSION_ID_PREFIX);
    }

    private static int remapProjectRedFaceConnMap(
            int connMap,
            Direction oldSide,
            CableBusTransform transform
    ) {
        Direction newSide = mapCableBusSide(oldSide, transform);

        int result = connMap;

        /*
         * ProjectRed face connMap:
         * bits 0..3      corner
         * bits 4..7      straight
         * bits 8..11     internal
         * bits 12..15    open
         * bit 16         center
         * bits 20..23    render corner
         */
        result &= ~0x0000000F;
        result &= ~0x000000F0;
        result &= ~0x00000F00;
        result &= ~0x0000F000;
        result &= ~0x00F00000;

        int[] laneBases = {
                0x00000001,
                0x00000010,
                0x00000100,
                0x00001000,
                0x00100000
        };

        for (int oldRot = 0; oldRot < 4; oldRot++) {
            int oldAbsSideOrdinal = cclRotateSide(oldSide.ordinal(), oldRot);

            if (oldAbsSideOrdinal < 0 || oldAbsSideOrdinal >= Direction.values().length) {
                continue;
            }

            Direction oldAbsSide = Direction.values()[oldAbsSideOrdinal];
            Direction newAbsSide = mapCableBusSide(oldAbsSide, transform);

            int newRot = cclRotationTo(newSide.ordinal(), newAbsSide.ordinal());

            if (newRot < 0 || newRot >= 4) {
                continue;
            }

            for (int laneBase : laneBases) {
                if ((connMap & (laneBase << oldRot)) != 0) {
                    result |= laneBase << newRot;
                }
            }
        }

        return result;
    }

    private static int remapProjectRedCenterConnMap(
            int connMap,
            CableBusTransform transform
    ) {
        /*
         * ProjectRed center connMap:
         * bits 0..5    external straight
         * bits 6..11   internal
         * bits 12..17  open
         */
        return remapSixDirectionBitGroups(
                connMap,
                transform,
                0,
                6,
                12
        );
    }

    private static int remapSixDirectionBitGroups(
            int value,
            CableBusTransform transform,
            int... groupOffsets
    ) {
        int result = value;
        int clearMask = 0;

        for (int offset : groupOffsets) {
            clearMask |= 0x3F << offset;
        }

        result &= ~clearMask;

        for (int offset : groupOffsets) {
            int group = (value >> offset) & 0x3F;

            for (Direction oldSide : Direction.values()) {
                if ((group & (1 << oldSide.ordinal())) == 0) {
                    continue;
                }

                Direction newSide = mapCableBusSide(oldSide, transform);
                result |= 1 << (offset + newSide.ordinal());
            }
        }

        return result;
    }

    private static int cclRotateSide(int side, int rotation) {
        int index = (side << 2) | rotation;

        if (index < 0 || index >= CCL_SIDE_ROT_MAP.length) {
            return -1;
        }

        return CCL_SIDE_ROT_MAP[index];
    }

    private static int cclRotationTo(int side, int absoluteSide) {
        int index = side * 6 + absoluteSide;

        if (index < 0 || index >= CCL_ROT_SIDE_MAP.length) {
            return -1;
        }

        return CCL_ROT_SIDE_MAP[index];
    }

    private static int readDirectionOrdinal(@Nullable Tag tag) {
        if (!(tag instanceof NumericTag numericTag)) {
            return -1;
        }

        int value = numericTag.getAsInt();

        return value >= 0 && value < Direction.values().length
                ? value
                : -1;
    }

    private static void putDirectionOrdinalPreservingType(
            CompoundTag target,
            String key,
            @Nullable Tag originalTag,
            int value
    ) {
        if (originalTag == null) {
            target.putInt(key, value);
            return;
        }

        switch (originalTag.getId()) {
            case Tag.TAG_BYTE -> target.putByte(key, (byte) value);
            case Tag.TAG_SHORT -> target.putShort(key, (short) value);
            case Tag.TAG_LONG -> target.putLong(key, value);
            default -> target.putInt(key, value);
        }
    }

    private static boolean isFaststoneBlockEntityTag(CompoundTag tag) {
        String id = tag.getString("id");

        if (id.startsWith(FASTSTONE_ID_PREFIX)) {
            return true;
        }

        if (tag.contains(KEY_PORT_STATES, Tag.TAG_COMPOUND)
                && tag.contains(KEY_PORT_COLORS, Tag.TAG_COMPOUND)
                && tag.contains(KEY_INPUTS, Tag.TAG_COMPOUND)
                && tag.contains(KEY_OUTPUTS, Tag.TAG_COMPOUND)) {
            return true;
        }

        return tag.contains(KEY_PARTS, Tag.TAG_COMPOUND)
                && tag.contains(KEY_REDSTONE_OUTPUTS, Tag.TAG_COMPOUND);
    }

    private static CompoundTag transformFaststoneBlockEntityTag(
            CompoundTag tag,
            CableBusTransform transform
    ) {
        CompoundTag result = tag.copy();

        remapFaststoneDirectionalCompound(result, KEY_PARTS, transform);
        remapFaststoneDirectionalCompound(result, KEY_REDSTONE_OUTPUTS, transform);
        remapFaststoneDirectionalCompound(result, KEY_PORT_STATES, transform);
        remapFaststoneDirectionalCompound(result, KEY_PORT_COLORS, transform);
        remapFaststoneDirectionalCompound(result, KEY_INPUTS, transform);
        remapFaststoneDirectionalCompound(result, KEY_OUTPUTS, transform);

        return result;
    }

    private static void remapFaststoneDirectionalCompound(
            CompoundTag tag,
            String key,
            CableBusTransform transform
    ) {
        if (!tag.contains(key, Tag.TAG_COMPOUND)) {
            return;
        }

        tag.put(
                key,
                transformDirectionalMetadataTag(
                        tag.getCompound(key),
                        transform
                )
        );
    }

    private static CompoundTag transformFaststoneCloneMetadata(
            CompoundTag metadata,
            CableBusTransform transform
    ) {
        CompoundTag result = metadata.copy();

        if (result.contains(FASTSTONE_FULL_BE_TAG_KEY, Tag.TAG_COMPOUND)) {
            result.put(
                    FASTSTONE_FULL_BE_TAG_KEY,
                    transformFaststoneBlockEntityTag(
                            result.getCompound(FASTSTONE_FULL_BE_TAG_KEY),
                            transform
                    )
            );
        }

        return result;
    }

    private static CompoundTag transformGregBlockEntityTag(CompoundTag tag, CableBusTransform transform) {
        CompoundTag result = tag.copy();

        if (result.contains("connections", Tag.TAG_INT)) {
            result.putInt("connections", remapGregConnectionMask(result.getInt("connections"), transform));
        }

        if (result.contains("blockedConnections", Tag.TAG_INT)) {
            result.putInt("blockedConnections", remapGregConnectionMask(result.getInt("blockedConnections"), transform));
        }

        if (result.contains(KEY_COVER, Tag.TAG_COMPOUND)) {
            CompoundTag transformedCoverTag = transformGregPipeCoverTag(result.getCompound(KEY_COVER), transform);
            result.put(KEY_COVER, transformedCoverTag);
        }

        remapGregDirectionalFieldsInPlace(result, transform);

        return result;
    }

    private static void remapGregDirectionalFieldsInPlace(CompoundTag tag, CableBusTransform transform) {
        List<String> keys = new ArrayList<>(tag.getAllKeys());

        for (String key : keys) {
            if (KEY_COVER.equals(key)) {
                continue;
            }

            Tag value = tag.get(key);
            if (value == null) {
                continue;
            }

            if (value instanceof CompoundTag childTag) {
                remapGregDirectionalFieldsInPlace(childTag, transform);
                continue;
            }

            if (value instanceof ListTag listTag) {
                remapGregDirectionalListInPlace(listTag, transform);
                continue;
            }

            if (!looksLikeGregDirectionalKey(key)) {
                continue;
            }

            if (value.getId() == Tag.TAG_STRING) {
                Direction direction = directionFromName(tag.getString(key));
                if (direction != null) {
                    tag.putString(key, directionName(mapCableBusSide(direction, transform)));
                }
                continue;
            }

            if (value.getId() == Tag.TAG_INT) {
                int raw = tag.getInt(key);
                if (raw >= 0 && raw < Direction.values().length) {
                    Direction direction = Direction.values()[raw];
                    tag.putInt(key, mapCableBusSide(direction, transform).ordinal());
                }
            }
        }
    }

    private static void remapGregDirectionalListInPlace(ListTag listTag, CableBusTransform transform) {
        for (int i = 0; i < listTag.size(); i++) {
            Tag entry = listTag.get(i);

            if (entry instanceof CompoundTag childTag) {
                remapGregDirectionalFieldsInPlace(childTag, transform);
                continue;
            }

            if (entry instanceof ListTag childList) {
                remapGregDirectionalListInPlace(childList, transform);
            }
        }
    }

    private static boolean looksLikeGregDirectionalKey(String key) {
        String normalized = key.toLowerCase(Locale.ROOT);

        return normalized.contains("facing")
                || normalized.endsWith("side")
                || normalized.contains("outputside")
                || normalized.contains("inputside")
                || normalized.contains("frontside")
                || normalized.contains("backside")
                || normalized.equals("front")
                || normalized.equals("back");
    }

    private static @Nullable Direction directionFromName(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }

        return switch (name.toLowerCase()) {
            case "down" -> Direction.DOWN;
            case "up" -> Direction.UP;
            case "north" -> Direction.NORTH;
            case "south" -> Direction.SOUTH;
            case "west" -> Direction.WEST;
            case "east" -> Direction.EAST;
            default -> null;
        };
    }

    private static String directionName(Direction direction) {
        return switch (direction) {
            case DOWN -> "down";
            case UP -> "up";
            case NORTH -> "north";
            case SOUTH -> "south";
            case WEST -> "west";
            case EAST -> "east";
        };
    }

    private static CompoundTag transformGregPipeCoverTag(CompoundTag coverTag, CableBusTransform transform) {
        CompoundTag result = new CompoundTag();

        for (String key : coverTag.getAllKeys()) {
            Direction side = directionFromKey(key);
            Tag rawValue = coverTag.get(key);

            if (rawValue == null) {
                continue;
            }

            if (side == null) {
                result.put(key, rawValue.copy());
                continue;
            }

            if (!(rawValue instanceof CompoundTag sideCoverTag)) {
                result.put(key, rawValue.copy());
                continue;
            }

            Direction mappedSide = mapCableBusSide(side, transform);
            CompoundTag movedCoverTag = sideCoverTag.copy();

            if (movedCoverTag.contains("uid", Tag.TAG_COMPOUND)) {
                CompoundTag uidTag = movedCoverTag.getCompound("uid").copy();
                uidTag.putInt("side", mappedSide.ordinal());
                movedCoverTag.put("uid", uidTag);
            }

            result.put(directionKey(mappedSide), movedCoverTag);
        }

        return result;
    }

    private static @Nullable Direction directionFromKey(String key) {
        return switch (key) {
            case KEY_NORTH -> Direction.NORTH;
            case KEY_SOUTH -> Direction.SOUTH;
            case KEY_EAST -> Direction.EAST;
            case KEY_WEST -> Direction.WEST;
            case KEY_UP -> Direction.UP;
            case KEY_DOWN -> Direction.DOWN;
            default -> null;
        };
    }

    private static int remapGregConnectionMask(int mask, CableBusTransform transform) {
        int out = 0;

        for (Direction side : Direction.values()) {
            int bit = gregBit(side);
            if ((mask & bit) == 0) {
                continue;
            }

            Direction mapped = mapCableBusSide(side, transform);
            out |= gregBit(mapped);
        }

        return out;
    }

    private static int gregBit(Direction side) {
        return 1 << side.ordinal();
    }

    private static CompoundTag transformCableBusTag(CompoundTag tag, CableBusTransform transform) {
        CompoundTag result = tag.copy();

        Tag north = tag.get(KEY_NORTH);
        Tag south = tag.get(KEY_SOUTH);
        Tag east = tag.get(KEY_EAST);
        Tag west = tag.get(KEY_WEST);
        Tag up = tag.get(KEY_UP);
        Tag down = tag.get(KEY_DOWN);
        Tag cable = tag.get(KEY_CABLE);

        result.remove(KEY_NORTH);
        result.remove(KEY_SOUTH);
        result.remove(KEY_EAST);
        result.remove(KEY_WEST);
        result.remove(KEY_UP);
        result.remove(KEY_DOWN);
        result.remove(KEY_CABLE);

        putMovedSide(result, transform, Direction.NORTH, north);
        putMovedSide(result, transform, Direction.SOUTH, south);
        putMovedSide(result, transform, Direction.EAST, east);
        putMovedSide(result, transform, Direction.WEST, west);
        putMovedSide(result, transform, Direction.UP, up);
        putMovedSide(result, transform, Direction.DOWN, down);

        if (cable != null) {
            result.put(KEY_CABLE, cable.copy());
        }

        return result;
    }

    private static void putMovedSide(
            CompoundTag target,
            CableBusTransform transform,
            Direction fromSide,
            @Nullable Tag sideTag
    ) {
        if (sideTag == null) {
            return;
        }

        Direction toSide = mapCableBusSide(fromSide, transform);
        target.put(directionKey(toSide), sideTag.copy());
    }

    private static Direction mapCableBusSide(Direction side, CableBusTransform transform) {
        return switch (transform) {
            case ROTATE_CW -> switch (side) {
                case NORTH -> Direction.EAST;
                case EAST -> Direction.SOUTH;
                case SOUTH -> Direction.WEST;
                case WEST -> Direction.NORTH;
                case UP -> Direction.UP;
                case DOWN -> Direction.DOWN;
            };
            case ROTATE_180 -> switch (side) {
                case NORTH -> Direction.SOUTH;
                case SOUTH -> Direction.NORTH;
                case EAST -> Direction.WEST;
                case WEST -> Direction.EAST;
                case UP -> Direction.UP;
                case DOWN -> Direction.DOWN;
            };
            case ROTATE_CCW -> switch (side) {
                case NORTH -> Direction.WEST;
                case WEST -> Direction.SOUTH;
                case SOUTH -> Direction.EAST;
                case EAST -> Direction.NORTH;
                case UP -> Direction.UP;
                case DOWN -> Direction.DOWN;
            };
            case FLIP_H_AXIS_Z -> switch (side) {
                case EAST -> Direction.WEST;
                case WEST -> Direction.EAST;
                default -> side;
            };
            case FLIP_H_AXIS_X -> switch (side) {
                case NORTH -> Direction.SOUTH;
                case SOUTH -> Direction.NORTH;
                default -> side;
            };
            case FLIP_V -> switch (side) {
                case UP -> Direction.DOWN;
                case DOWN -> Direction.UP;
                default -> side;
            };
            case NONE -> side;
        };
    }

    public static String directionKey(Direction direction) {
        return switch (direction) {
            case NORTH -> KEY_NORTH;
            case SOUTH -> KEY_SOUTH;
            case EAST -> KEY_EAST;
            case WEST -> KEY_WEST;
            case UP -> KEY_UP;
            case DOWN -> KEY_DOWN;
        };
    }

    private static CompoundTag blockStateToTag(BlockState state) {
        CompoundTag tag = new CompoundTag();

        ResourceLocation key = ForgeRegistries.BLOCKS.getKey(state.getBlock());
        tag.putString("Name", key != null ? key.toString() : "minecraft:air");

        if (!state.getValues().isEmpty()) {
            CompoundTag properties = new CompoundTag();

            for (Map.Entry<Property<?>, Comparable<?>> entry : state.getValues().entrySet()) {
                properties.putString(
                        entry.getKey().getName(),
                        getPropertyValueName(entry.getKey(), entry.getValue())
                );
            }

            tag.put("Properties", properties);
        }

        return tag;
    }

    private static @Nullable BlockState parseBlockStateFromTag(CompoundTag tag) {
        ResourceLocation resourceLocation = ResourceLocation.tryParse(tag.getString("Name"));
        if (resourceLocation == null) {
            return null;
        }

        Block block = ForgeRegistries.BLOCKS.getValue(resourceLocation);
        if (block == null) {
            return null;
        }

        BlockState state = block.defaultBlockState();
        if (!tag.contains("Properties", Tag.TAG_COMPOUND)) {
            return state;
        }

        CompoundTag properties = tag.getCompound("Properties");
        StateDefinition<?, ?> definition = block.getStateDefinition();

        for (String key : properties.getAllKeys()) {
            Property<?> property = definition.getProperty(key);
            if (property == null) {
                continue;
            }

            Optional<?> value = property.getValue(properties.getString(key));
            if (value.isPresent()) {
                state = setUnchecked(state, property, (Comparable<?>) value.get());
            }
        }

        return state;
    }

    private static BlockState flipHorizontalState(BlockState state, Direction sourceFacing) {
        Mirror mirror = sourceFacing.getAxis() == Direction.Axis.Z
                ? Mirror.FRONT_BACK
                : Mirror.LEFT_RIGHT;

        BlockState mirrored = state.mirror(mirror);

        mirrored = preserveNamedProperties(
                state,
                mirrored,
                PRESERVE_ON_ROTATE_AND_HORIZONTAL_FLIP_PROPERTIES
        );

        BlockState result = remapUnchangedHorizontalDirectionalProperties(
                state,
                mirrored,
                sourceFacing.getAxis()
        );

        CableBusTransform transform = sourceFacing.getAxis() == Direction.Axis.Z
                ? CableBusTransform.FLIP_H_AXIS_Z
                : CableBusTransform.FLIP_H_AXIS_X;

        if (!hasDirectionalBooleanPropertyChange(state, result)) {
            result = remapDirectionalBooleanProperties(result, transform);
        }

        return remapFramedTypePropertyIfUnchanged(state, result, FramedTypeTransform.FLIP_H);
    }

    private static BlockState remapUnchangedHorizontalDirectionalProperties(
            BlockState original,
            BlockState transformed,
            Direction.Axis sourceAxis
    ) {
        if (original.getBlock() != transformed.getBlock()) {
            return transformed;
        }

        BlockState result = transformed;
        StateDefinition<?, ?> definition = result.getBlock().getStateDefinition();

        for (Map.Entry<Property<?>, Comparable<?>> entry : original.getValues().entrySet()) {
            Property<?> originalProperty = entry.getKey();

            if (PRESERVE_ON_ROTATE_AND_HORIZONTAL_FLIP_PROPERTIES.contains(originalProperty.getName())) {
                continue;
            }

            Property<?> resultProperty = definition.getProperty(originalProperty.getName());

            if (resultProperty == null) {
                continue;
            }

            Object afterValue = getPropertyValue(result, resultProperty);
            if (afterValue == null) {
                continue;
            }

            String beforeName = getPropertyValueName(originalProperty, entry.getValue());
            String afterName = getPropertyValueName(resultProperty, afterValue);

            if (!beforeName.equals(afterName)) {
                continue;
            }

            Direction beforeDirection = directionFromName(beforeName);
            if (beforeDirection == null || !beforeDirection.getAxis().isHorizontal()) {
                continue;
            }

            Direction mappedDirection = flipHorizontalDirection(beforeDirection, sourceAxis);

            Optional<?> mappedValue = resultProperty.getValue(directionName(mappedDirection));
            if (mappedValue.isEmpty()) {
                continue;
            }

            result = setUnchecked(result, resultProperty, (Comparable<?>) mappedValue.get());
        }

        return result;
    }

    private static Direction flipHorizontalDirection(Direction direction, Direction.Axis sourceAxis) {
        return switch (sourceAxis) {
            case Z -> switch (direction) {
                case EAST -> Direction.WEST;
                case WEST -> Direction.EAST;
                default -> direction;
            };
            case X -> switch (direction) {
                case NORTH -> Direction.SOUTH;
                case SOUTH -> Direction.NORTH;
                default -> direction;
            };
            default -> direction;
        };
    }

    private static BlockState flipVerticalState(BlockState state) {
        BlockState result = state;

        result = remapPropertyValues(result, "half", Map.of(
                "top", "bottom",
                "bottom", "top",
                "upper", "lower",
                "lower", "upper"
        ));

        result = remapPropertyValues(result, "face", Map.of(
                "floor", "ceiling",
                "ceiling", "floor",
                "up", "down",
                "down", "up"
        ));

        result = remapPropertyValues(result, KEY_UPWARDS_FACING, Map.of(
                "north", "south",
                "south", "north",
                "east", "west",
                "west", "east"
        ));

        result = flipVerticalDirectionProperties(result);
        result = remapFramedProperties(result, FramedPropertyTransform.FLIP_V);
        result = remapFramedTypePropertyIfUnchanged(state, result, FramedTypeTransform.FLIP_V);

        if (!hasDirectionalBooleanPropertyChange(state, result)) {
            result = remapDirectionalBooleanProperties(result, CableBusTransform.FLIP_V);
        }

        return result;
    }

    private static BlockState rotateState(BlockState state, Rotation rotation) {
        BlockState rotated = state.rotate(rotation);

        rotated = preserveNamedProperties(
                state,
                rotated,
                PRESERVE_ON_ROTATE_AND_HORIZONTAL_FLIP_PROPERTIES
        );

        if (rotation == Rotation.NONE) {
            return rotated;
        }

        BlockState result = rotateUnchangedDirectionalProperties(state, rotated, rotation);

        FramedPropertyTransform framedTransform = switch (rotation) {
            case CLOCKWISE_90 -> FramedPropertyTransform.ROTATE_CW;
            case CLOCKWISE_180 -> FramedPropertyTransform.ROTATE_180;
            case COUNTERCLOCKWISE_90 -> FramedPropertyTransform.ROTATE_CCW;
            case NONE -> null;
        };

        CableBusTransform booleanTransform = switch (rotation) {
            case CLOCKWISE_90 -> CableBusTransform.ROTATE_CW;
            case CLOCKWISE_180 -> CableBusTransform.ROTATE_180;
            case COUNTERCLOCKWISE_90 -> CableBusTransform.ROTATE_CCW;
            case NONE -> CableBusTransform.NONE;
        };

        if (!hasDirectionalBooleanPropertyChange(state, result)) {
            result = remapDirectionalBooleanProperties(result, booleanTransform);
        }

        return framedTransform == null
                ? result
                : remapFramedProperties(result, framedTransform);
    }

    private static BlockState rotateUnchangedDirectionalProperties(
            BlockState original,
            BlockState transformed,
            Rotation rotation
    ) {
        if (rotation == Rotation.NONE || original.getBlock() != transformed.getBlock()) {
            return transformed;
        }

        BlockState result = transformed;
        StateDefinition<?, ?> definition = result.getBlock().getStateDefinition();

        for (Map.Entry<Property<?>, Comparable<?>> entry : original.getValues().entrySet()) {
            Property<?> originalProperty = entry.getKey();

            if (PRESERVE_ON_ROTATE_AND_HORIZONTAL_FLIP_PROPERTIES.contains(originalProperty.getName())) {
                continue;
            }

            Property<?> resultProperty = definition.getProperty(originalProperty.getName());

            if (resultProperty == null) {
                continue;
            }

            Object afterValue = getPropertyValue(result, resultProperty);
            if (afterValue == null) {
                continue;
            }

            String beforeName = getPropertyValueName(originalProperty, entry.getValue());
            String afterName = getPropertyValueName(resultProperty, afterValue);

            if (!beforeName.equals(afterName)) {
                continue;
            }

            Direction beforeDirection = directionFromName(beforeName);
            if (beforeDirection == null) {
                continue;
            }

            Direction mappedDirection = rotateDirection(beforeDirection, rotation);

            Optional<?> mappedValue = resultProperty.getValue(directionName(mappedDirection));
            if (mappedValue.isEmpty()) {
                continue;
            }

            result = setUnchecked(result, resultProperty, (Comparable<?>) mappedValue.get());
        }

        return result;
    }

    private static BlockState preserveNamedProperties(
            BlockState original,
            BlockState transformed,
            Set<String> propertyNames
    ) {
        if (original.getBlock() != transformed.getBlock()) {
            return transformed;
        }

        BlockState result = transformed;
        StateDefinition<?, ?> originalDefinition = original.getBlock().getStateDefinition();
        StateDefinition<?, ?> resultDefinition = result.getBlock().getStateDefinition();

        for (String propertyName : propertyNames) {
            Property<?> originalProperty = originalDefinition.getProperty(propertyName);
            Property<?> resultProperty = resultDefinition.getProperty(propertyName);

            if (originalProperty == null || resultProperty == null) {
                continue;
            }

            Object originalValue = getPropertyValue(original, originalProperty);
            if (originalValue == null) {
                continue;
            }

            String originalValueName = getPropertyValueName(originalProperty, originalValue);
            Optional<?> parsedValue = resultProperty.getValue(originalValueName);

            if (parsedValue.isPresent()) {
                result = setUnchecked(result, resultProperty, (Comparable<?>) parsedValue.get());
            }
        }

        return result;
    }

    private static Direction rotateDirection(Direction direction, Rotation rotation) {
        return switch (rotation) {
            case CLOCKWISE_90 -> switch (direction) {
                case NORTH -> Direction.EAST;
                case EAST -> Direction.SOUTH;
                case SOUTH -> Direction.WEST;
                case WEST -> Direction.NORTH;
                case UP -> Direction.UP;
                case DOWN -> Direction.DOWN;
            };
            case CLOCKWISE_180 -> switch (direction) {
                case NORTH -> Direction.SOUTH;
                case SOUTH -> Direction.NORTH;
                case EAST -> Direction.WEST;
                case WEST -> Direction.EAST;
                case UP -> Direction.UP;
                case DOWN -> Direction.DOWN;
            };
            case COUNTERCLOCKWISE_90 -> switch (direction) {
                case NORTH -> Direction.WEST;
                case WEST -> Direction.SOUTH;
                case SOUTH -> Direction.EAST;
                case EAST -> Direction.NORTH;
                case UP -> Direction.UP;
                case DOWN -> Direction.DOWN;
            };
            case NONE -> direction;
        };
    }

    private static BlockState remapDirectionalBooleanProperties(
            BlockState state,
            CableBusTransform transform
    ) {
        if (transform == CableBusTransform.NONE) {
            return state;
        }

        Map<Direction, Boolean> values = new EnumMap<>(Direction.class);

        for (Direction direction : Direction.values()) {
            Property<?> property = state.getBlock()
                    .getStateDefinition()
                    .getProperty(directionKey(direction));

            if (property == null) {
                continue;
            }

            Object value = getPropertyValue(state, property);

            if (value instanceof Boolean bool) {
                values.put(direction, bool);
            }
        }

        if (values.isEmpty()) {
            return state;
        }

        BlockState result = state;

        for (Direction direction : values.keySet()) {
            Property<?> property = result.getBlock()
                    .getStateDefinition()
                    .getProperty(directionKey(direction));

            if (property != null) {
                result = setUnchecked(result, property, false);
            }
        }

        for (Map.Entry<Direction, Boolean> entry : values.entrySet()) {
            if (!entry.getValue()) {
                continue;
            }

            Direction mappedDirection = mapCableBusSide(entry.getKey(), transform);
            Property<?> property = result.getBlock()
                    .getStateDefinition()
                    .getProperty(directionKey(mappedDirection));

            if (property != null) {
                result = setUnchecked(result, property, true);
            }
        }

        return result;
    }

    private static boolean hasDirectionalBooleanPropertyChange(
            BlockState before,
            BlockState after
    ) {
        for (Direction direction : Direction.values()) {
            Property<?> property = before.getBlock()
                    .getStateDefinition()
                    .getProperty(directionKey(direction));

            if (property == null) {
                continue;
            }

            Object beforeValue = getPropertyValue(before, property);

            if (!(beforeValue instanceof Boolean)) {
                continue;
            }

            Property<?> afterProperty = after.getBlock()
                    .getStateDefinition()
                    .getProperty(directionKey(direction));

            if (afterProperty == null) {
                continue;
            }

            Object afterValue = getPropertyValue(after, afterProperty);

            if (!Objects.equals(beforeValue, afterValue)) {
                return true;
            }
        }

        return false;
    }

    private static BlockState remapPropertyValues(BlockState state, String propertyName, Map<String, String> mapping) {
        Property<?> property = state.getBlock().getStateDefinition().getProperty(propertyName);
        if (property == null) {
            return state;
        }

        Object currentValue = getPropertyValue(state, property);
        if (currentValue == null) {
            return state;
        }

        String currentValueName = getPropertyValueName(property, currentValue);
        String targetValueName = mapping.get(currentValueName);
        if (targetValueName == null) {
            return state;
        }

        Optional<?> parsed = property.getValue(targetValueName);
        if (parsed.isEmpty()) {
            return state;
        }

        return setUnchecked(state, property, (Comparable<?>) parsed.get());
    }

    private static BlockState flipVerticalDirectionProperties(BlockState state) {
        BlockState result = state;

        for (Map.Entry<Property<?>, Comparable<?>> entry : state.getValues().entrySet()) {
            Property<?> property = entry.getKey();
            Comparable<?> value = entry.getValue();

            if (value instanceof Direction direction && direction.getAxis() == Direction.Axis.Y) {
                Direction flipped = direction == Direction.UP ? Direction.DOWN : Direction.UP;
                Direction allowed = coerceDirectionForProperty(property, flipped);

                if (allowed != null && allowed != direction) {
                    result = setUnchecked(result, property, allowed);
                }
            }
        }

        return result;
    }

    private static BlockState remapFramedProperties(BlockState state, FramedPropertyTransform transform) {
        if (!isFramedBlocksState(state)) {
            return state;
        }

        return switch (transform) {
            case FLIP_V -> toggleBooleanProperty(state, "top");
            case ROTATE_CW, ROTATE_CCW -> swapBooleanProperties(state, "x_axis", "z_axis");
            case ROTATE_180 -> state;
        };
    }

    private static BlockState toggleBooleanProperty(BlockState state, String propertyName) {
        Property<?> property = state.getBlock().getStateDefinition().getProperty(propertyName);
        if (property == null) {
            return state;
        }

        Object value = getPropertyValue(state, property);
        if (!(value instanceof Boolean bool)) {
            return state;
        }

        return setUnchecked(state, property, !bool);
    }

    private static BlockState swapBooleanProperties(BlockState state, String firstName, String secondName) {
        Property<?> first = state.getBlock().getStateDefinition().getProperty(firstName);
        Property<?> second = state.getBlock().getStateDefinition().getProperty(secondName);

        if (first == null || second == null) {
            return state;
        }

        Object firstValue = getPropertyValue(state, first);
        Object secondValue = getPropertyValue(state, second);

        if (!(firstValue instanceof Boolean firstBool) || !(secondValue instanceof Boolean secondBool)) {
            return state;
        }

        if (firstBool == secondBool) {
            return state;
        }

        BlockState result = setUnchecked(state, first, secondBool);
        return setUnchecked(result, second, firstBool);
    }

    private static @Nullable Direction coerceDirectionForProperty(Property<?> property, Direction wanted) {
        if (propertyContainsValue(property, wanted)) {
            return wanted;
        }

        Direction opposite = wanted.getOpposite();
        if (propertyContainsValue(property, opposite)) {
            return opposite;
        }

        return null;
    }

    private static boolean propertyContainsValue(Property<?> property, Object wanted) {
        for (Object possible : property.getPossibleValues()) {
            if (possible == wanted || possible.equals(wanted)) {
                return true;
            }
        }

        return false;
    }

    private static boolean isFramedBlocksState(BlockState state) {
        ResourceLocation key = ForgeRegistries.BLOCKS.getKey(state.getBlock());
        return key != null && "framedblocks".equals(key.getNamespace());
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static BlockState setUnchecked(BlockState state, Property<?> property, Comparable value) {
        return state.setValue((Property) property, value);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Object getPropertyValue(BlockState state, Property<?> property) {
        return state.getValue((Property) property);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static String getPropertyValueName(Property<?> property, Object value) {
        return ((Property) property).getName((Comparable) value);
    }

    private static BlockState remapFramedTypePropertyIfUnchanged(
            BlockState original,
            BlockState transformed,
            FramedTypeTransform transform
    ) {
        if (!isFramedBlocksState(original)) {
            return transformed;
        }

        if (original.getBlock() != transformed.getBlock()) {
            return transformed;
        }

        Property<?> property = original.getBlock().getStateDefinition().getProperty("type");
        if (property == null) {
            return transformed;
        }

        Object beforeValue = getPropertyValue(original, property);
        Object afterValue = getPropertyValue(transformed, property);

        if (beforeValue == null || afterValue == null) {
            return transformed;
        }

        String beforeName = getPropertyValueName(property, beforeValue);
        String afterName = getPropertyValueName(property, afterValue);

        if (!beforeName.equals(afterName)) {
            return transformed;
        }

        Comparable mappedValue = findMappedFramedTypeValue(property, beforeName, transform);
        if (mappedValue == null) {
            return transformed;
        }

        return setUnchecked(transformed, property, mappedValue);
    }

    @SuppressWarnings("rawtypes")
    private static @Nullable Comparable findMappedFramedTypeValue(
            Property<?> property,
            String currentName,
            FramedTypeTransform transform
    ) {
        FramedTypeParts currentParts = parseFramedTypeParts(currentName);
        if (currentParts.directionalTokens().isEmpty()) {
            return null;
        }

        List<String> wantedDirectionalTokens = new ArrayList<>();

        for (String token : currentParts.directionalTokens()) {
            String mapped = transformFramedTypeToken(token, transform);
            if (!wantedDirectionalTokens.contains(mapped)) {
                wantedDirectionalTokens.add(mapped);
            }
        }

        Object foundValue = null;
        int foundCount = 0;

        for (Object candidateValue : property.getPossibleValues()) {
            String candidateName = getPropertyValueName(property, candidateValue);
            FramedTypeParts candidateParts = parseFramedTypeParts(candidateName);

            if (!candidateParts.otherTokens().equals(currentParts.otherTokens())) {
                continue;
            }

            if (!sameStringSet(candidateParts.directionalTokens(), wantedDirectionalTokens)) {
                continue;
            }

            foundValue = candidateValue;
            foundCount++;
        }

        if (foundCount != 1 || !(foundValue instanceof Comparable comparable)) {
            return null;
        }

        return comparable;
    }

    private static FramedTypeParts parseFramedTypeParts(String value) {
        List<String> otherTokens = new ArrayList<>();
        List<String> directionalTokens = new ArrayList<>();

        if (value == null || value.isBlank()) {
            return new FramedTypeParts(otherTokens, directionalTokens);
        }

        for (String token : value.split("_")) {
            if (isFramedTypeDirectionalToken(token)) {
                if (!directionalTokens.contains(token)) {
                    directionalTokens.add(token);
                }
            } else {
                otherTokens.add(token);
            }
        }

        return new FramedTypeParts(otherTokens, directionalTokens);
    }

    private static boolean isFramedTypeDirectionalToken(String token) {
        return "top".equals(token)
                || "bottom".equals(token)
                || "left".equals(token)
                || "right".equals(token);
    }

    private static String transformFramedTypeToken(String token, FramedTypeTransform transform) {
        return switch (transform) {
            case FLIP_H -> switch (token) {
                case "left" -> "right";
                case "right" -> "left";
                default -> token;
            };
            case FLIP_V -> switch (token) {
                case "top" -> "bottom";
                case "bottom" -> "top";
                default -> token;
            };
        };
    }

    private static boolean sameStringSet(List<String> a, List<String> b) {
        if (a.size() != b.size()) {
            return false;
        }

        for (String value : a) {
            if (!b.contains(value)) {
                return false;
            }
        }

        return true;
    }

    private record FramedTypeParts(
            List<String> otherTokens,
            List<String> directionalTokens
    ) {
    }
}
