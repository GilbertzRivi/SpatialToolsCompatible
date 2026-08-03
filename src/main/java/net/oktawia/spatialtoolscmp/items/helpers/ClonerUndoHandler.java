package net.oktawia.spatialtoolscmp.items.helpers;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;
import net.oktawia.spatialtoolscmp.logic.StructureCloneExtension;
import net.oktawia.spatialtoolscmp.logic.StructureToolExtensions;
import net.oktawia.spatialtoolscmp.logic.StructureToolStructureStore;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ClonerUndoHandler {

    private static final String UNDO_DIMENSION_KEY = "clonerUndoDimension";
    private static final String UNDO_BLOCKS_KEY = "clonerUndoBlocks";
    public static final String UNDO_ID_KEY = "clonerUndoId";

    private static final String ORIGINAL_STATE_KEY = "originalState";
    private static final String ORIGINAL_BE_TAG_KEY = "originalBeTag";

    private static final String PER_POS_STATE_KEY = "perPosOriginalState";
    private static final String PER_POS_STATE_ENTRY_KEY = "state";

    private static final String PER_POS_BE_KEY = "perPosOriginalBe";
    private static final String PER_POS_BE_ENTRY_KEY = "be";

    private static final String POS_X = "x";
    private static final String POS_Y = "y";
    private static final String POS_Z = "z";

    private static final String STATE_KEY = "state";
    private static final String REFUNDS_KEY = "refunds";
    private static final String UNDO_REFUNDS_KEY = "undoRefunds";
    private static final String REFUND_STACK_KEY = "stack";
    private static final String REFUND_COUNT_KEY = "count";

    private static final int UNDO_CLEAR_FLAGS =
            Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS;

    private ClonerUndoHandler() {
    }

    public record ClonerUndoPlacedBlock(
            BlockPos pos,
            String stateSignature,
            List<ItemStack> refundStacks,
            List<ItemStack> undoRefundStacks
    ) {

        public ClonerUndoPlacedBlock(
                BlockPos pos,
                String stateSignature,
                List<ItemStack> refundStacks
        ) {
            this(pos, stateSignature, refundStacks, List.of());
        }
    }

    public static void store(
            ItemStack toolStack,
            ServerLevel level,
            List<ClonerUndoPlacedBlock> placedBlocks
    ) {
        clear(level, toolStack);

        CompoundTag undoTag = new CompoundTag();
        undoTag.putString(UNDO_DIMENSION_KEY, level.dimension().location().toString());

        ListTag blocksTag = new ListTag();

        for (ClonerUndoPlacedBlock placedBlock : placedBlocks) {
            CompoundTag blockTag = new CompoundTag();

            BlockState currentState = level.getBlockState(placedBlock.pos());

            blockTag.putInt(POS_X, placedBlock.pos().getX());
            blockTag.putInt(POS_Y, placedBlock.pos().getY());
            blockTag.putInt(POS_Z, placedBlock.pos().getZ());
            blockTag.putString(STATE_KEY, getBlockId(currentState));

            ListTag refundList = new ListTag();

            for (ItemStack refundStack : placedBlock.refundStacks()) {
                if (refundStack.isEmpty()) {
                    continue;
                }

                CompoundTag refundEntry = new CompoundTag();
                ItemStack single = refundStack.copy();
                int count = Math.max(1, refundStack.getCount());
                single.setCount(1);

                refundEntry.put(REFUND_STACK_KEY, single.save(new CompoundTag()));
                refundEntry.putInt(REFUND_COUNT_KEY, count);
                refundList.add(refundEntry);
            }

            blockTag.put(REFUNDS_KEY, refundList);
            blocksTag.add(blockTag);
        }

        undoTag.put(UNDO_BLOCKS_KEY, blocksTag);

        String undoId = UUID.randomUUID().toString();

        try {
            StructureToolStructureStore.save(level.getServer(), undoId, undoTag);
            toolStack.getOrCreateTag().putString(UNDO_ID_KEY, undoId);
        } catch (IOException ignored) {
        }
    }

    public static void storeForReplacer(
            ItemStack toolStack,
            ServerLevel level,
            List<ClonerUndoPlacedBlock> placedBlocks,
            CompoundTag originalStateNbt,
            Map<BlockPos, CompoundTag> perPosBeTags
    ) {
        storeForReplacer(
                toolStack,
                level,
                placedBlocks,
                originalStateNbt,
                Map.of(),
                perPosBeTags
        );
    }

    public static void storeForReplacer(
            ItemStack toolStack,
            ServerLevel level,
            List<ClonerUndoPlacedBlock> placedBlocks,
            CompoundTag originalStateNbt,
            Map<BlockPos, CompoundTag> perPosStateTags,
            Map<BlockPos, CompoundTag> perPosBeTags
    ) {
        clear(level, toolStack);

        CompoundTag undoTag = new CompoundTag();
        undoTag.putString(UNDO_DIMENSION_KEY, level.dimension().location().toString());

        ListTag blocksTag = new ListTag();

        for (ClonerUndoPlacedBlock placedBlock : placedBlocks) {
            CompoundTag blockTag = new CompoundTag();

            BlockState currentState = level.getBlockState(placedBlock.pos());

            blockTag.putInt(POS_X, placedBlock.pos().getX());
            blockTag.putInt(POS_Y, placedBlock.pos().getY());
            blockTag.putInt(POS_Z, placedBlock.pos().getZ());
            blockTag.putString(STATE_KEY, getBlockId(currentState));

            ListTag refundList = new ListTag();

            for (ItemStack refundStack : placedBlock.refundStacks()) {
                if (refundStack.isEmpty()) {
                    continue;
                }

                CompoundTag refundEntry = new CompoundTag();
                ItemStack single = refundStack.copy();
                int count = Math.max(1, refundStack.getCount());
                single.setCount(1);

                refundEntry.put(REFUND_STACK_KEY, single.save(new CompoundTag()));
                refundEntry.putInt(REFUND_COUNT_KEY, count);
                refundList.add(refundEntry);
            }

            blockTag.put(REFUNDS_KEY, refundList);
            blockTag.put(UNDO_REFUNDS_KEY, writeRefundList(placedBlock.undoRefundStacks()));
            blocksTag.add(blockTag);
        }

        undoTag.put(UNDO_BLOCKS_KEY, blocksTag);
        undoTag.put(ORIGINAL_STATE_KEY, originalStateNbt);

        if (!perPosStateTags.isEmpty()) {
            ListTag perPosStateListTag = new ListTag();

            for (Map.Entry<BlockPos, CompoundTag> entry : perPosStateTags.entrySet()) {
                CompoundTag entryTag = new CompoundTag();

                entryTag.putInt(POS_X, entry.getKey().getX());
                entryTag.putInt(POS_Y, entry.getKey().getY());
                entryTag.putInt(POS_Z, entry.getKey().getZ());
                entryTag.put(PER_POS_STATE_ENTRY_KEY, entry.getValue().copy());

                perPosStateListTag.add(entryTag);
            }

            undoTag.put(PER_POS_STATE_KEY, perPosStateListTag);
        }

        if (!perPosBeTags.isEmpty()) {
            ListTag perPosBeListTag = new ListTag();

            for (Map.Entry<BlockPos, CompoundTag> entry : perPosBeTags.entrySet()) {
                CompoundTag entryTag = new CompoundTag();

                entryTag.putInt(POS_X, entry.getKey().getX());
                entryTag.putInt(POS_Y, entry.getKey().getY());
                entryTag.putInt(POS_Z, entry.getKey().getZ());
                entryTag.put(PER_POS_BE_ENTRY_KEY, entry.getValue().copy());

                perPosBeListTag.add(entryTag);
            }

            undoTag.put(PER_POS_BE_KEY, perPosBeListTag);
        }

        String undoId = UUID.randomUUID().toString();

        try {
            StructureToolStructureStore.save(level.getServer(), undoId, undoTag);
            toolStack.getOrCreateTag().putString(UNDO_ID_KEY, undoId);
        } catch (IOException ignored) {
        }
    }

    public static @Nullable CompoundTag getOriginalStateNbt(CompoundTag undoTag) {
        if (undoTag == null || !undoTag.contains(ORIGINAL_STATE_KEY, Tag.TAG_COMPOUND)) {
            return null;
        }

        return undoTag.getCompound(ORIGINAL_STATE_KEY);
    }

    public static @Nullable CompoundTag getOriginalBeTag(CompoundTag undoTag) {
        if (undoTag == null || !undoTag.contains(ORIGINAL_BE_TAG_KEY, Tag.TAG_COMPOUND)) {
            return null;
        }

        return undoTag.getCompound(ORIGINAL_BE_TAG_KEY);
    }

    public static Map<BlockPos, CompoundTag> getPerPosStateTags(CompoundTag undoTag) {
        Map<BlockPos, CompoundTag> result = new LinkedHashMap<>();

        if (undoTag == null || !undoTag.contains(PER_POS_STATE_KEY, Tag.TAG_LIST)) {
            return result;
        }

        ListTag list = undoTag.getList(PER_POS_STATE_KEY, Tag.TAG_COMPOUND);

        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);

            BlockPos pos = new BlockPos(
                    entry.getInt(POS_X),
                    entry.getInt(POS_Y),
                    entry.getInt(POS_Z)
            );

            if (entry.contains(PER_POS_STATE_ENTRY_KEY, Tag.TAG_COMPOUND)) {
                result.put(pos, entry.getCompound(PER_POS_STATE_ENTRY_KEY).copy());
            }
        }

        return result;
    }

    public static Map<BlockPos, CompoundTag> getPerPosBeTags(CompoundTag undoTag) {
        Map<BlockPos, CompoundTag> result = new LinkedHashMap<>();

        if (undoTag == null || !undoTag.contains(PER_POS_BE_KEY, Tag.TAG_LIST)) {
            return result;
        }

        ListTag list = undoTag.getList(PER_POS_BE_KEY, Tag.TAG_COMPOUND);

        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);

            BlockPos pos = new BlockPos(
                    entry.getInt(POS_X),
                    entry.getInt(POS_Y),
                    entry.getInt(POS_Z)
            );

            if (entry.contains(PER_POS_BE_ENTRY_KEY, Tag.TAG_COMPOUND)) {
                result.put(pos, entry.getCompound(PER_POS_BE_ENTRY_KEY).copy());
            }
        }

        return result;
    }

    public static void restoreBlocksWithPerPosStatesAndTags(
            ServerLevel level,
            List<ClonerUndoPlacedBlock> undoBlocks,
            CompoundTag fallbackStateNbt,
            Map<BlockPos, CompoundTag> perPosStateTags,
            Map<BlockPos, CompoundTag> perPosBeTags
    ) {
        BlockState fallbackState = readBlockStateOrFallback(
                fallbackStateNbt,
                Blocks.AIR.defaultBlockState()
        );

        for (ClonerUndoPlacedBlock undoBlock : undoBlocks) {
            level.removeBlockEntity(undoBlock.pos());
        }

        for (ClonerUndoPlacedBlock undoBlock : undoBlocks) {
            if (!level.getBlockState(undoBlock.pos()).isAir()) {
                level.setBlock(
                        undoBlock.pos(),
                        Blocks.AIR.defaultBlockState(),
                        UNDO_CLEAR_FLAGS,
                        0
                );
            }
        }

        for (ClonerUndoPlacedBlock undoBlock : undoBlocks) {
            BlockPos pos = undoBlock.pos();

            BlockState originalState = readBlockStateOrFallback(
                    perPosStateTags.get(pos),
                    fallbackState
            );

            CompoundTag beTag = perPosBeTags.get(pos);

            ClonerBlockPlacer.placeBlockAndLoadTag(
                    level,
                    pos,
                    originalState,
                    beTag
            );
        }
    }

    public static void restoreBlocks(
            ServerLevel level,
            List<ClonerUndoPlacedBlock> undoBlocks,
            CompoundTag originalStateNbt,
            @Nullable CompoundTag originalBeTag
    ) {
        BlockState originalState = readBlockStateOrFallback(
                originalStateNbt,
                Blocks.AIR.defaultBlockState()
        );

        for (ClonerUndoPlacedBlock undoBlock : undoBlocks) {
            level.removeBlockEntity(undoBlock.pos());
        }

        for (ClonerUndoPlacedBlock undoBlock : undoBlocks) {
            if (!level.getBlockState(undoBlock.pos()).isAir()) {
                level.setBlock(
                        undoBlock.pos(),
                        Blocks.AIR.defaultBlockState(),
                        UNDO_CLEAR_FLAGS,
                        0
                );
            }
        }

        for (ClonerUndoPlacedBlock undoBlock : undoBlocks) {
            ClonerBlockPlacer.placeBlockAndLoadTag(
                    level,
                    undoBlock.pos(),
                    originalState,
                    originalBeTag
            );
        }
    }

    private static BlockState readBlockStateOrFallback(
            @Nullable CompoundTag stateTag,
            BlockState fallback
    ) {
        if (stateTag == null || stateTag.isEmpty()) {
            return fallback;
        }

        try {
            return NbtUtils.readBlockState(
                    BuiltInRegistries.BLOCK.asLookup(),
                    stateTag
            );
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    public static @Nullable CompoundTag load(ServerLevel level, ItemStack toolStack) {
        CompoundTag tag = toolStack.getTag();

        if (tag == null || !tag.contains(UNDO_ID_KEY, Tag.TAG_STRING)) {
            return null;
        }

        String undoId = tag.getString(UNDO_ID_KEY);

        if (undoId.isBlank()) {
            return null;
        }

        try {
            return StructureToolStructureStore.load(level.getServer(), undoId);
        } catch (IOException ignored) {
            return null;
        }
    }

    public static void clear(ServerLevel level, ItemStack toolStack) {
        CompoundTag tag = toolStack.getTag();

        if (tag == null) {
            return;
        }

        if (tag.contains(UNDO_ID_KEY, Tag.TAG_STRING)) {
            String undoId = tag.getString(UNDO_ID_KEY);

            if (!undoId.isBlank()) {
                try {
                    StructureToolStructureStore.delete(level.getServer(), undoId);
                } catch (IOException ignored) {
                }
            }
        }

        clearFromStack(toolStack);
    }

    public static void clearFromStack(ItemStack toolStack) {
        CompoundTag tag = toolStack.getTag();

        if (tag == null) {
            return;
        }

        tag.remove(UNDO_ID_KEY);
        tag.remove(UNDO_DIMENSION_KEY);
        tag.remove(UNDO_BLOCKS_KEY);
    }

    public static String getDimension(CompoundTag undoTag) {
        return undoTag.getString(UNDO_DIMENSION_KEY);
    }

    public static boolean hasBlocks(CompoundTag undoTag) {
        return undoTag.contains(UNDO_BLOCKS_KEY, Tag.TAG_LIST);
    }

    private static ListTag writeRefundList(List<ItemStack> stacks) {
        ListTag out = new ListTag();

        for (ItemStack stack : stacks) {
            if (stack.isEmpty()) {
                continue;
            }

            CompoundTag entry = new CompoundTag();
            ItemStack single = stack.copy();
            int count = Math.max(1, stack.getCount());
            single.setCount(1);

            entry.put(REFUND_STACK_KEY, single.save(new CompoundTag()));
            entry.putInt(REFUND_COUNT_KEY, count);
            out.add(entry);
        }

        return out;
    }

    private static List<ItemStack> readRefundList(CompoundTag blockTag, String key) {
        if (!blockTag.contains(key, Tag.TAG_LIST)) {
            return List.of();
        }

        ListTag list = blockTag.getList(key, Tag.TAG_COMPOUND);
        List<ItemStack> out = new ArrayList<>();

        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);

            if (!entry.contains(REFUND_STACK_KEY, Tag.TAG_COMPOUND)) {
                continue;
            }

            ItemStack stack = ItemStack.of(entry.getCompound(REFUND_STACK_KEY));

            if (!stack.isEmpty()) {
                stack.setCount(Math.max(1, entry.getInt(REFUND_COUNT_KEY)));
                out.add(stack);
            }
        }

        return List.copyOf(out);
    }

    public static List<ClonerUndoPlacedBlock> readBlocks(CompoundTag undoTag) {
        if (!undoTag.contains(UNDO_BLOCKS_KEY, Tag.TAG_LIST)) {
            return List.of();
        }

        ListTag blocksTag = undoTag.getList(UNDO_BLOCKS_KEY, Tag.TAG_COMPOUND);
        List<ClonerUndoPlacedBlock> out = new ArrayList<>();

        for (int i = 0; i < blocksTag.size(); i++) {
            CompoundTag blockTag = blocksTag.getCompound(i);

            BlockPos pos = new BlockPos(
                    blockTag.getInt(POS_X),
                    blockTag.getInt(POS_Y),
                    blockTag.getInt(POS_Z)
            );

            out.add(new ClonerUndoPlacedBlock(
                    pos,
                    blockTag.getString(STATE_KEY),
                    readRefundList(blockTag, REFUNDS_KEY),
                    readRefundList(blockTag, UNDO_REFUNDS_KEY)
            ));
        }

        return out;
    }

    public static boolean areBlocksUnchanged(
            ServerLevel level,
            List<ClonerUndoPlacedBlock> undoBlocks
    ) {
        for (ClonerUndoPlacedBlock undoBlock : undoBlocks) {
            BlockState currentState = level.getBlockState(undoBlock.pos());

            if (!matchesStoredBlock(currentState, undoBlock.stateSignature())) {
                return false;
            }
        }

        return true;
    }

    private static boolean matchesStoredBlock(
            BlockState currentState,
            String storedSignature
    ) {
        if (currentState.isAir()) {
            return false;
        }

        String currentBlockId = getBlockId(currentState);
        String storedBlockId = extractBlockId(storedSignature);

        return !currentBlockId.isBlank()
                && !storedBlockId.isBlank()
                && currentBlockId.equals(storedBlockId);
    }

    private static String getBlockId(BlockState state) {
        ResourceLocation key = ForgeRegistries.BLOCKS.getKey(state.getBlock());

        if (key != null) {
            return key.toString();
        }

        return extractBlockId(state.toString());
    }

    private static String extractBlockId(String signature) {
        if (signature == null || signature.isBlank()) {
            return "";
        }

        int braceStart = signature.indexOf('{');
        int braceEnd = signature.indexOf('}', braceStart + 1);

        if (braceStart >= 0 && braceEnd > braceStart) {
            return signature.substring(braceStart + 1, braceEnd);
        }

        int bracketStart = signature.indexOf('[');

        if (bracketStart > 0) {
            return signature.substring(0, bracketStart);
        }

        return signature;
    }

    public static List<ItemStack> collectCurrentRefundStacks(
            ServerLevel level,
            List<ClonerUndoPlacedBlock> undoBlocks
    ) {
        List<ItemStack> out = new ArrayList<>();

        for (ClonerUndoPlacedBlock undoBlock : undoBlocks) {
            BlockPos pos = undoBlock.pos();
            BlockState state = level.getBlockState(pos);

            if (state.isAir()) {
                continue;
            }

            if (!undoBlock.undoRefundStacks().isEmpty()) {
                for (ItemStack stack : undoBlock.undoRefundStacks()) {
                    out.add(stack.copy());
                }

                continue;
            }

            BlockEntity be = level.getBlockEntity(pos);
            boolean handled = false;

            for (StructureCloneExtension extension : StructureToolExtensions.clonerExtensions()) {
                try {
                    if (extension.collectUndoRefunds(level, pos, state, be, out)) {
                        handled = true;
                        break;
                    }
                } catch (Throwable ignored) {
                }
            }

            if (handled) {
                continue;
            }

            ItemStack stack = ClonerBlockPlacer.getRequiredBlockItem(state);

            if (!stack.isEmpty()) {
                out.add(stack);
            }
        }

        return aggregateRefundStacks(out);
    }

    public static void removeBlocks(
            ServerLevel level,
            List<ClonerUndoPlacedBlock> undoBlocks
    ) {
        BlockState air = Blocks.AIR.defaultBlockState();

        for (ClonerUndoPlacedBlock undoBlock : undoBlocks) {
            level.removeBlockEntity(undoBlock.pos());
        }

        for (ClonerUndoPlacedBlock undoBlock : undoBlocks) {
            if (level.getBlockState(undoBlock.pos()).isAir()) {
                continue;
            }

            level.setBlock(
                    undoBlock.pos(),
                    air,
                    UNDO_CLEAR_FLAGS,
                    0
            );
        }
    }

    public static List<ItemStack> collectRefundStacks(
            List<ClonerUndoPlacedBlock> undoBlocks
    ) {
        List<ItemStack> out = new ArrayList<>();

        for (ClonerUndoPlacedBlock undoBlock : undoBlocks) {
            for (ItemStack stack : undoBlock.refundStacks()) {
                if (!stack.isEmpty()) {
                    out.add(stack.copy());
                }
            }
        }

        return aggregateRefundStacks(out);
    }

    public static List<ItemStack> aggregateRefundStacks(List<ItemStack> stacks) {
        List<ItemStack> out = new ArrayList<>();

        for (ItemStack stack : stacks) {
            if (stack.isEmpty()) {
                continue;
            }

            ItemStack copy = stack.copy();
            boolean merged = false;

            for (ItemStack existing : out) {
                if (ItemStack.isSameItemSameTags(existing, copy)) {
                    existing.grow(copy.getCount());
                    merged = true;
                    break;
                }
            }

            if (!merged) {
                out.add(copy);
            }
        }

        return out;
    }
}